/* Chat notifications on the website, for people signed in. Same `events` action the apps poll.
   Works while any asubank.org tab is open, including a tab in the background. A closed browser would need a push server, which this site doesn't have.
   Only one tab asks the server at a time, so several open tabs don't repeat a notification. */
(function () {
    'use strict';
    var API = 'https://script.google.com/macros/s/AKfycbzXDE0Km2mxL_gAJFqspZeaADtugupeLN0YQlkEbXnrCkSTKBfImW_lLCKF8_SMI0i_/exec';
    var EVERY = 30000;
    var KEYS = { off: 'ab.notify.off', since: 'ab.notify.since', turn: 'ab.notify.turn' };
    if (!('Notification' in window)) return;

    function get(k) { try { return localStorage.getItem(k); } catch (e) { return null; } }
    function set(k, v) { try { localStorage.setItem(k, v); } catch (e) {} }
    function me() { try { return JSON.parse(get('ab.acc.user') || 'null'); } catch (e) { return null; } }
    function enabled() { return Notification.permission === 'granted' && get(KEYS.off) !== '1'; }

    var baseTitle = document.title;
    var unread = 0;
    function showUnread() { document.title = unread ? '(' + unread + ') ' + baseTitle : baseTitle; }
    document.addEventListener('visibilitychange', function () { if (!document.hidden) { unread = 0; showUnread(); } });

    function wanted(e, user) {
        if (e.userId && e.userId === user.id) return false;
        if (e.userId && user.blocked && user.blocked.indexOf(e.userId) >= 0) return false;
        if (e.type === 'dm') {
            var inThread = /^\/chat/.test(location.pathname) && decodeURIComponent(location.hash.replace('#', '')) === '@dm/' + e.userId;
            return !(inThread && !document.hidden);
        }
        if (e.type === 'upload' || !e.room) return false;
        // already looking at this room
        var open = /^\/chat/.test(location.pathname) && decodeURIComponent(location.hash.replace('#', '')) === e.room;
        return !(open && !document.hidden);
    }

    function show(e, user) {
        var who = e.displayName || 'Someone';
        var title = e.type === 'dm' ? who : e.type === 'announcement' ? '📣 ' + who : e.replyToUserId === user.id ? who + ' replied to you' : 'New message from ' + who;
        var options = {
            body: (e.roomName ? e.roomName + ': ' : '') + String(e.text || '').slice(0, 160),
            icon: '/icons/icon-192x192.png',
            tag: 'chat-' + e.id,
            data: { url: e.type === 'dm' ? '/chat/#@dm/' + encodeURIComponent(e.userId) : '/chat/#' + encodeURIComponent(e.room) }
        };
        // phones only allow notifications through the service worker
        if (navigator.serviceWorker && navigator.serviceWorker.controller) {
            navigator.serviceWorker.ready.then(function (reg) { return reg.showNotification(title, options); }).catch(function () {});
            return;
        }
        try {
            var n = new Notification(title, options);
            n.onclick = function () { window.focus(); location.href = options.data.url; n.close(); };
        } catch (err) {}
    }

    function check() {
        var token = get('ab.acc.token');
        var user = me();
        if (!token || !user || !enabled()) return;
        var now = Date.now();
        if (now - Number(get(KEYS.turn) || 0) < EVERY - 2000) return; // another tab just asked
        set(KEYS.turn, String(now));
        var since = get(KEYS.since);
        fetch(API, { method: 'POST', headers: { 'Content-Type': 'text/plain;charset=utf-8' }, body: JSON.stringify({ action: 'events', token: token, since: since || '', social3: true }) })
            .then(function (r) { return r.json(); })
            .then(function (o) {
                if (!o || !o.ok) return;
                var events = o.events || [];
                var newest = events.reduce(function (m, e) { return e.created > m ? e.created : m; }, since || '');
                // the first check only remembers where we are, so old messages don't pop up
                if (!since) { set(KEYS.since, newest > (o.now || '') ? newest : (o.now || '')); return; }
                set(KEYS.since, newest);
                var fresh = events.filter(function (e) { return e.created > since && wanted(e, user); });
                fresh.forEach(function (e) { show(e, user); });
                if (fresh.length && document.hidden) { unread += fresh.length; showUnread(); }
            })
            .catch(function () {});
    }

    // ---- the switch at the top of the chat page. Browsers only ask for permission after a click.
    function drawSwitch() {
        var root = document.getElementById('acc-app');
        if (!root || root.getAttribute('data-view') !== 'chat' || !me()) return;
        var bar = document.getElementById('acc-notify-bar');
        if (!bar) {
            bar = document.createElement('div');
            bar.id = 'acc-notify-bar';
            bar.style.cssText = 'display:flex;align-items:center;justify-content:space-between;gap:10px;padding:8px 12px;margin:8px auto;border-radius:12px;font-size:14px;background:var(--ab-accent-soft,#f7dce5)';
            root.parentNode.insertBefore(bar, root);
        }
        // only asks for attention when notifications are off; while they're on it's one quiet line, and not inside a chat
        var open = /^#?(@messages)?$/.test(location.hash);
        bar.style.display = enabled() && !open ? 'none' : 'flex';
        bar.style.background = enabled() ? 'transparent' : '';
        bar.style.padding = enabled() ? '0 4px' : '';
        bar.textContent = '';
        var label = document.createElement('span');
        var perm = Notification.permission;
        if (perm === 'denied') label.textContent = '🔕 Notifications are blocked. Allow them for asubank.org in your browser settings.';
        else label.textContent = enabled() ? '🔔 Notifications are on' : '🔕 Get notified about new chat messages.';
        bar.appendChild(label);
        if (perm === 'denied') return;
        var b = document.createElement('button');
        b.type = 'button';
        b.className = 'ab-btn gray';
        b.style.cssText = 'flex-shrink:0;padding:6px 12px;font-size:13px';
        b.textContent = enabled() ? 'Turn off' : 'Turn on';
        b.onclick = function () {
            if (enabled()) { set(KEYS.off, '1'); drawSwitch(); return; }
            Notification.requestPermission().then(function () {
                set(KEYS.off, '0');
                drawSwitch();
                check();
            });
        };
        bar.appendChild(b);
    }

    drawSwitch();
    // the chat page signs in after this script loads
    window.addEventListener('storage', drawSwitch);
    window.addEventListener('hashchange', drawSwitch);
    check();
    setInterval(check, EVERY);
})();
