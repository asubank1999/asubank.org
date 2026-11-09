/* UniHub uploader for /upload/gupload (quick upload) and /upload/dupload (structured submission).
 * Files go to the UniHub Upload API (tools/apps-script/upload-api.gs) as one simple text/plain POST each:
 * Apps Script answers no CORS preflight, so no custom headers and no upload progress listeners.
 * The apps have the same two modes natively (UploadView.swift, Uploads.kt) and open these links there. */
document.addEventListener('DOMContentLoaded', function () {
    'use strict';
    var UPLOAD_API = 'https://script.google.com/macros/s/AKfycbygD_bFuQN7k9zLKogIip_zEaXmsq9Vw-z6QPfMte1AAuLdq4Vf4-kGIgvy6hgVcOo/exec';
    var MAX = 25 * 1024 * 1024;
    var structured = document.body.getAttribute('data-mode') === 'structured';
    var OK_EXT = structured
        ? /\.(pdf|docx?|pptx?|jpe?g|png|heic)$/i
        : /\.(pdf|docx?|pptx?|xlsx?|txt|jpe?g|png|heic|webp|zip|rar|7z|csv|mp4|mov|m4v|webm|mp3|m4a)$/i;
    var $ = function (id) { return document.getElementById(id); };
    var agree = $('agree'), work = $('work'), drop = $('drop'), input = $('fileInput'), queue = $('queue');
    var subject = $('mSubject'), kind = $('mKind'), year = $('mYear'), title = $('mTitle'), note = $('mNote');
    var startBtn = $('startBtn'), clearBtn = $('clearBtn'), problem = $('problem'), done = $('done');
    var files = [], busy = false, uploaded = [], items = null, touched = {};

    // ---------- remembered choices ----------
    function get(k) { try { return localStorage.getItem(k) || ''; } catch (e) { return ''; } }
    function set(k, v) { try { localStorage.setItem(k, v); } catch (e) {} }
    try { agree.checked = sessionStorage.getItem('ab.uploadAgree') === '1'; } catch (e) {}
    // ---------- smart uploader bar ----------
    var smartOn = get('ab.up.smart') !== '0';
    var bar = AB.el('<div class="smart"><span aria-hidden="true">✨</span><div class="grow"><b>Smart uploader</b><br>Suggests a clear name, subject, type and year from what is inside your file.</div>' +
        '<input type="checkbox" id="smartOn" aria-label="Smart uploader"></div>');
    var tip = AB.el('<div class="smart-tip" hidden><span aria-hidden="true">💡</span><div class="grow"></div><button type="button">Use</button></div>');
    queue.parentNode.insertBefore(bar, queue);
    queue.parentNode.insertBefore(tip, queue.nextSibling);
    bar.querySelector('input').checked = smartOn;
    bar.querySelector('input').addEventListener('change', function () {
        smartOn = this.checked; set('ab.up.smart', smartOn ? '1' : '0');
        if (smartOn) files.forEach(function (x) { if (x.state === 'ready' && !x.smart) x.smart = 'wait'; });
        else tip.hidden = true;
        render(); runSmart();
    });
    var wanted = new URLSearchParams(location.search).get('subject') || get('ab.up.subject');

    AB.index().then(function (d) {
        items = d.items;
        d.subjects.forEach(function (s) { if (s.c !== 'Archive') subject.appendChild(new Option(s.t, s.t)); });
        if (wanted && Array.prototype.some.call(subject.options, function (o) { return o.value === wanted; })) subject.value = wanted;
        update();
    });
    for (var y = new Date().getFullYear() + 1; y >= 2018; y--) year.appendChild(new Option(y, y));
    if (get('ab.up.kind')) kind.value = get('ab.up.kind');
    if (get('ab.up.year')) year.value = get('ab.up.year');

    // ---------- the queue ----------
    function fmt(b) { return b >= 1048576 ? (b / 1048576).toFixed(1) + ' MB' : Math.max(1, Math.round(b / 1024)) + ' KB'; }
    function check(f) {
        if (!OK_EXT.test(f.name)) return structured ? 'Structured submissions take PDF, Word, PowerPoint and photos only' : 'This file type is not accepted';
        if (f.size > MAX) return 'Too big for the website (max 25 MB). The UniHub app takes any size.';
        if (f.size === 0) return 'Empty file';
        return '';
    }
    var batchNo = 0;
    function add(list) {
        if (!agree.checked) { AB.toast('Tick “I agree” first'); agree.focus(); return; }
        Array.prototype.forEach.call(list, function (f) {
            if (files.some(function (x) { return x.f.name === f.name && x.f.size === f.size && x.state !== 'ok'; })) return;
            var err = check(f);
            files.push({ f: f, state: err ? 'bad' : 'ready', msg: err, pct: 0, name: '', smart: !err && smartOn ? 'wait' : '', batch: batchNo });
        });
        batchNo++;
        runSmart();
        done.hidden = true;
        render(); update();
    }
    function render() {
        queue.hidden = !files.length;
        clearBtn.hidden = !files.length || busy;
        var fe = document.activeElement, fi = fe && fe.getAttribute ? fe.getAttribute('data-nm') : null, sel = fi !== null ? [fe.selectionStart, fe.selectionEnd] : null;
        queue.innerHTML = '';
        files.forEach(function (x, i) {
            var k = AB.kind('', x.f.name);
            var st = x.state === 'ready' ? 'Ready · ' + fmt(x.f.size)
                : x.state === 'up' ? 'Uploading… ' + x.pct + '%'
                : x.state === 'ok' ? '✓ Uploaded' : x.msg;
            var cls = x.state === 'ok' ? 'ok' : (x.state === 'err' || x.state === 'bad') ? 'err' : '';
            var bar = x.state === 'ok' || x.state === 'err' || x.state === 'bad' ? 100 : x.pct;
            var btn = '';
            if (x.state === 'err') btn = '<button class="ab-icon-btn" type="button" data-retry="' + i + '" aria-label="Try again">↻</button>';
            if (x.state === 'ready' || x.state === 'err' || x.state === 'bad') btn += '<button class="ab-icon-btn" type="button" data-rm="' + i + '" aria-label="Remove">' + AB.icon('x') + '</button>';
            if (x.state === 'ok' && x.url) btn = '<a class="ab-icon-btn" href="' + AB.esc(x.url) + '" target="_blank" rel="noopener" aria-label="Open">' + AB.icon('chev') + '</a>';
            var li = AB.el('<li><div class="ab-row">' + AB.tile(k === 'link' ? 'file' : k) +
                '<div class="ab-row-text"><div class="ab-row-title"></div><div class="st ' + cls + '">' + AB.esc(st) + '</div>' +
                '<div class="ab-progress' + (cls === 'ok' ? ' done' : cls === 'err' ? ' err' : '') + '"><i style="width:' + bar + '%"></i></div></div>' + btn + '</div></li>');
            var t = li.querySelector('.ab-row-title');
            if (x.state === 'ready' || x.state === 'err') {
                t.innerHTML = '<input class="nm" dir="auto" maxlength="120" aria-label="File name" data-nm="' + i + '">';
                var inp = t.firstChild;
                inp.value = x.name || baseName(x.f.name);
                inp.placeholder = baseName(x.f.name);
                var was = AB.el('<div class="was"></div>');
                was.textContent = x.smart === 'busy' || x.smart === 'wait' ? '✨ Reading the file to suggest a name…'
                    : x.smart === 'done' && x.name && x.name !== baseName(x.f.name) && !x.edited ? '✨ Suggested name · was ' + x.f.name
                    : x.edited ? 'Was ' + x.f.name : '';
                if (was.textContent) t.appendChild(was);
            } else t.textContent = outName(x);
            queue.appendChild(li);
        });
        if (fi !== null) { var back = queue.querySelector('[data-nm="' + fi + '"]'); if (back) { back.focus(); try { back.setSelectionRange(sel[0], sel[1]); } catch (e) {} } }
        var total = files.filter(function (x) { return x.state === 'ready'; }).reduce(function (a, x) { return a + x.f.size; }, 0);
        $('qInfo').textContent = total ? fmt(total) + ' to upload' : '';
    }

    // ---------- smart names ----------
    function ext(n) { return (n.match(/\.[^.]+$/) || [''])[0]; }
    function baseName(n) { return n.replace(/\.[^.]+$/, ''); }
    function outName(x) {
        var n = (x.name || '').replace(/[\\/:*?"<>|]+/g, ' ').replace(/\s+/g, ' ').trim();
        return n ? n + ext(x.f.name).toLowerCase() : x.f.name;
    }
    function isPhoto(x) { return /\.(jpe?g|png|webp|heic)$/i.test(x.f.name); }
    var running = false;
    function runSmart() {
        if (running || !smartOn || !window.SmartName) return;
        var x = files.filter(function (f) { return f.smart === 'wait' && f.state === 'ready'; })[0];
        if (!x) return;
        // photos added together are pages of one thing: read the first one, number the rest
        var group = isPhoto(x) ? files.filter(function (f) { return f.batch === x.batch && isPhoto(f) && f.state === 'ready' && f.smart === 'wait'; }) : [x];
        running = true;
        group.forEach(function (f) { f.smart = 'busy'; });
        render();
        SmartName.analyze(x.f, items).then(function (info) {
            fillFields(info);
            group.forEach(function (f, n) {
                f.smart = 'done';
                if (f.edited) return;
                var nm = info.name || (isPhoto(f) ? [subject.value && subject.value.length < 30 ? subject.value : '', kind.value, year.value].filter(Boolean).join(' ') : '');
                if (nm && group.length > 1) nm += ' - page ' + (n + 1);
                if (nm) f.name = nm;
            });
        }, function () { group.forEach(function (f) { f.smart = 'done'; }); }).then(function () {
            running = false; render(); update(); runSmart();
        });
    }
    function hasOption(sel, v) { return v && Array.prototype.some.call(sel.options, function (o) { return o.value === v; }); }
    function fillFields(info) {
        var differs = [];
        [[subject, info.bank, 'subject'], [kind, info.kind, 'type'], [year, info.year, 'year']].forEach(function (f) {
            var sel = f[0], v = f[1];
            if (!hasOption(sel, v) || sel.value === v) return;
            if (!sel.value && !touched[sel.id]) { sel.value = v; flash(sel); }
            else differs.push([sel, v, f[2]]);
        });
        if (title && !title.value.trim() && info.name && !touched.mTitle) { title.value = info.name; flash(title); }
        if (differs.length) {
            tip.querySelector('.grow').textContent = 'This file looks like ' + differs.map(function (d) { return d[2] + ' “' + d[1] + '”'; }).join(', ') + '.';
            tip.hidden = false;
            tip.querySelector('button').onclick = function () {
                differs.forEach(function (d) { d[0].value = d[1]; set('ab.up.' + d[0].id.slice(1).toLowerCase(), d[1]); flash(d[0]); });
                tip.hidden = true; update();
            };
        }
    }
    function flash(el) {
        var row = el.closest('.ab-field') || el;
        row.style.transition = 'background-color .6s'; row.style.backgroundColor = 'rgba(0,122,255,.14)';
        setTimeout(function () { row.style.backgroundColor = ''; }, 1400);
    }
    queue.addEventListener('input', function (e) {
        var i = e.target.getAttribute && e.target.getAttribute('data-nm');
        if (i === null || !files[+i]) return;
        files[+i].name = e.target.value; files[+i].edited = true;
    });

    // what still stops the upload, shown under the button
    function blocker() {
        var ready = files.filter(function (x) { return x.state === 'ready'; }).length;
        if (!agree.checked) return 'Tick “I agree” to upload.';
        if (!ready) return files.length ? '' : 'Add the files you want to share.';
        if (!structured) return '';
        if (files.some(function (x) { return x.state === 'bad'; })) return 'Remove the files that can’t be sent first.';
        if (!subject.value) return 'Choose a subject.';
        if (!kind.value) return 'Choose what kind of file it is.';
        if (!year.value) return 'Choose the year.';
        if (title.value.trim().length < 3) return 'Give it a title.';
        return '';
    }
    function update() {
        var n = files.filter(function (x) { return x.state === 'ready'; }).length;
        var why = busy ? '' : blocker();
        work.classList.toggle('locked', !agree.checked);
        startBtn.disabled = busy || !n || !!why;
        startBtn.textContent = busy ? 'Uploading…' : n > 1 ? 'Upload ' + n + ' files' : structured ? 'Submit' : 'Upload';
        problem.textContent = why;
    }

    agree.addEventListener('change', function () {
        try { sessionStorage.setItem('ab.uploadAgree', agree.checked ? '1' : '0'); } catch (e) {}
        update();
    });
    [subject, kind, year].forEach(function (s) { s.addEventListener('change', function () { touched[s.id] = 1; set('ab.up.' + s.id.slice(1).toLowerCase(), s.value); update(); }); });
    if (title) title.addEventListener('input', function () { touched.mTitle = 1; update(); });
    queue.addEventListener('click', function (e) {
        var rm = e.target.closest('[data-rm]'), rt = e.target.closest('[data-retry]');
        if (busy) return;
        if (rm) files.splice(+rm.getAttribute('data-rm'), 1);
        if (rt) { var x = files[+rt.getAttribute('data-retry')]; x.state = 'ready'; x.msg = ''; x.pct = 0; }
        if (rm || rt) { render(); update(); }
    });
    input.addEventListener('change', function () { add(input.files); input.value = ''; });
    drop.addEventListener('click', function (e) { if (!agree.checked) { e.preventDefault(); AB.toast('Tick “I agree” first'); } });
    ['dragenter', 'dragover'].forEach(function (t) { drop.addEventListener(t, function (e) { e.preventDefault(); drop.classList.add('over'); }); });
    ['dragleave', 'drop'].forEach(function (t) { drop.addEventListener(t, function (e) { e.preventDefault(); drop.classList.remove('over'); }); });
    drop.addEventListener('drop', function (e) { e.stopPropagation(); add(e.dataTransfer.files); });
    // dropping anywhere on the page works too, and so does pasting a screenshot
    document.addEventListener('dragover', function (e) { e.preventDefault(); });
    document.addEventListener('drop', function (e) { e.preventDefault(); if (e.dataTransfer.files.length) add(e.dataTransfer.files); });
    document.addEventListener('paste', function (e) {
        var list = e.clipboardData && e.clipboardData.files;
        if (list && list.length && !/^(INPUT|TEXTAREA)$/.test((e.target || {}).tagName || '')) { e.preventDefault(); add(list); }
    });
    clearBtn.addEventListener('click', function () { if (!busy) { files = []; tip.hidden = true; render(); update(); } });

    // ---------- sending ----------
    function toBase64(file) {
        return new Promise(function (res, rej) {
            var r = new FileReader();
            r.onload = function () { res(String(r.result).split(',')[1] || ''); };
            r.onerror = function () { rej(new Error('Could not read the file')); };
            r.readAsDataURL(file);
        });
    }
    function noteText() {
        var n = note.value.trim();
        return structured ? 'Structured submission: ' + title.value.trim() + (n ? ' · ' + n : '') : n;
    }
    function send(x) {
        x.state = 'up'; x.pct = 0; render();
        function setPct(p) {
            x.pct = p;
            var li = queue.children[files.indexOf(x)];
            if (li) { li.querySelector('.ab-progress i').style.width = p + '%'; li.querySelector('.st').textContent = 'Uploading… ' + p + '%'; }
        }
        // progress is estimated (about 300 KB/s, stops at 95%) because a simple request can't report it
        var t0 = Date.now(), tick = setInterval(function () {
            setPct(Math.min(95, Math.round((1 - Math.exp(-(Date.now() - t0) / 1000 * 300e3 / Math.max(x.f.size, 50e3))) * 100)));
        }, 250);
        return toBase64(x.f).then(function (b64) {
            return fetch(UPLOAD_API, {
                method: 'POST',
                headers: { 'Content-Type': 'text/plain;charset=utf-8' },
                body: JSON.stringify({ name: outName(x), mimeType: x.f.type, data: b64, subject: subject.value, kind: kind.value, year: year.value, note: noteText() })
            }).then(function (r) {
                return r.json().catch(function () { return { error: 'Upload failed (' + r.status + ')' }; });
            }, function () { throw new Error('Network error. Check your connection and try again.'); });
        }).then(function (d) {
            if (d && d.success) { x.state = 'ok'; x.url = d.url; uploaded.push({ name: d.name || outName(x), url: d.url }); }
            else { x.state = 'err'; x.msg = (d && d.error) || 'Upload failed'; }
        }).catch(function (err) { x.state = 'err'; x.msg = err.message; }).then(function () { clearInterval(tick); render(); });
    }
    startBtn.addEventListener('click', function () {
        if (busy || startBtn.disabled) return;
        busy = true; update(); render();
        var todo = files.filter(function (x) { return x.state === 'ready'; });
        todo.reduce(function (p, x) { return p.then(function () { return send(x); }); }, Promise.resolve()).then(function () {
            busy = false;
            var ok = todo.filter(function (x) { return x.state === 'ok'; }).length;
            if (ok) {
                $('doneText').textContent = ok + ' file' + (ok > 1 ? 's' : '') + (structured ? ' submitted' : ' uploaded') + '. Thank you! ' + (ok > 1 ? 'They show' : 'It shows') + ' in Latest uploads now.';
                done.hidden = false;
                done.scrollIntoView({ behavior: 'smooth', block: 'center' });
                if (title) title.value = '';
                try { navigator.vibrate && navigator.vibrate(30); } catch (e) {}
            }
            render(); update();
        });
    });
    $('moreBtn').addEventListener('click', function () {
        files = files.filter(function (x) { return x.state !== 'ok'; });
        done.hidden = true; render(); update();
        input.click();
    });
    window.addEventListener('beforeunload', function (e) { if (busy) { e.preventDefault(); e.returnValue = ''; } });
    render(); update();
});
