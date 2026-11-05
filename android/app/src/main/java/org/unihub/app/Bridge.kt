package org.unihub.app

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

/**
 * Talks natively to an UniHub Google Apps Script web app (course matcher, material requests).
 * Those scripts only answer their own page through google.script.run, so a hidden WebView loads the
 * page, a script injected into every frame finds the frame holding the form (by the `marker` element
 * id) and runs the queued calls there.
 */
class ScriptBridge(private val url: String, marker: String) {
    enum class Status { CONNECTING, READY, FAILED }

    val status = MutableStateFlow(Status.CONNECTING)

    private val queue = ConcurrentLinkedQueue<String>()
    private val waiting = ConcurrentHashMap<Int, CompletableDeferred<Result<JsonElement>>>()
    private val ids = AtomicInteger()
    private var web: WebView? = null

    private val probe = """
(function(){
  function ready(){ try { return window.google && google.script && google.script.run && document.getElementById('$marker') && window.ABBridge; } catch(e) { return false; } }
  var told = false;
  function loop(){
    if (!ready()) { setTimeout(loop, 250); return; }
    if (!told) { told = true; ABBridge.ready(); }
    var c = ABBridge.next();
    while (c) {
      (function(c){
        var r = google.script.run
          .withSuccessHandler(function(v){ ABBridge.done(c.id, JSON.stringify(v === undefined ? null : v), ''); })
          .withFailureHandler(function(e){ ABBridge.done(c.id, '', (e && e.message) ? e.message : String(e)); });
        r[c.fn].apply(r, c.args);
      })(JSON.parse(c));
      c = ABBridge.next();
    }
    setTimeout(loop, 120);
  }
  loop();
})();
"""

    @Suppress("unused")
    private inner class Js {
        @JavascriptInterface fun ready() { if (status.value != Status.READY) status.value = Status.READY }
        @JavascriptInterface fun next(): String? = queue.poll()
        @JavascriptInterface fun done(id: Int, result: String, error: String) {
            val d = waiting.remove(id) ?: return
            if (error.isNotEmpty()) d.complete(Result.failure(Exception(error)))
            else d.complete(Result.success(try { json.parseToJsonElement(result) } catch (e: Exception) { JsonNull }))
        }
    }

    val supported get() = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface", "RequiresFeature")
    fun create(ctx: android.content.Context): WebView = WebView(ctx).apply {
        layoutParams = ViewGroup.LayoutParams(4, 4)
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        webViewClient = object : WebViewClient() {
            override fun onReceivedError(view: WebView, request: android.webkit.WebResourceRequest, error: android.webkit.WebResourceError) {
                if (request.isForMainFrame && status.value == Status.CONNECTING) status.value = Status.FAILED
            }
        }
        addJavascriptInterface(Js(), "ABBridge")
        WebViewCompat.addDocumentStartJavaScript(this, probe, setOf("*"))
        web = this
    }

    fun release(w: WebView) { if (web === w) { web = null; status.value = Status.CONNECTING }; w.destroy() }

    suspend fun connect(force: Boolean = false) {
        if (!supported) { status.value = Status.FAILED; return }
        if (status.value == Status.READY && !force) return
        status.value = Status.CONNECTING
        web?.loadUrl(url)
        val ok = withTimeoutOrNull(40_000) { while (status.value != Status.READY) delay(100) } != null
        if (!ok && status.value == Status.CONNECTING) status.value = Status.FAILED
    }

    /** Runs a script function; reconnects and retries once when the page doesn't answer */
    suspend fun call(fn: String, args: List<JsonElement>, timeoutMs: Long = 30_000): Result<JsonElement> {
        if (status.value != Status.READY) connect()
        if (status.value != Status.READY) return Result.failure(Exception("Couldn't connect. Check your connection and try again."))
        val r = callOnce(fn, args, timeoutMs)
        if (r.exceptionOrNull() !is BridgeTimeout) return r
        connect(force = true)
        if (status.value != Status.READY) return r
        return callOnce(fn, args, timeoutMs)
    }

    private class BridgeTimeout : Exception("The server didn't answer. Try again.")

    private suspend fun callOnce(fn: String, args: List<JsonElement>, timeoutMs: Long): Result<JsonElement> {
        val id = ids.incrementAndGet()
        val d = CompletableDeferred<Result<JsonElement>>()
        waiting[id] = d
        queue.add(buildJsonObject {
            put("id", id); put("fn", fn)
            putJsonArray("args") { args.forEach { add(it) } }
        }.toString())
        return withTimeoutOrNull(timeoutMs) { d.await() } ?: run { waiting.remove(id); Result.failure(BridgeTimeout()) }
    }
}

/** The bridge's hidden page; put it in a screen that uses the bridge */
@Composable
fun BridgeHost(bridge: ScriptBridge) {
    AndroidView(factory = { bridge.create(it) }, onRelease = { bridge.release(it) }, modifier = Modifier.size(1.dp).alpha(0f))
    LaunchedEffect(bridge) { bridge.connect() }
}

fun bridgeError(e: Throwable?): String = (e?.message ?: "Something went wrong. Try again.").removePrefix("Error: ").removePrefix("Exception: ")
