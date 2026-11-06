/* The round Messages button at the bottom right of every asubank.org page (signed-in students only).
   It opens a small inbox with private Messages and chat Rooms; a conversation opens in /chat/.
   Loaded by app.js. Not shown on /chat/ itself or inside the apps. Same Accounts API as accounts.js. */
(function () {
    'use strict';
    var API = 'https://script.google.com/macros/s/AKfycbzXDE0Km2mxL_gAJFqspZeaADtugupeLN0YQlkEbXnrCkSTKBfImW_lLCKF8_SMI0i_/exec';
    var FRESH = 60000; // ask the server at most once a minute, across pages
    var doc = document;

    function get(k) { try { return localStorage.getItem(k); } catch (e) { return null; } }
    function set(k, v) { try { localStorage.setItem(k, v); } catch (e) {} }
    function cacheGet(k) { try { return JSON.parse(get('ab.cache.' + k)); } catch (e) { return null; } }
    function cachePut(k, v) { set('ab.cache.' + k, JSON.stringify(v)); }

    var token = get('ab.acc.token');
    if (!token || doc.getElementById('ab-inbox-btn')) return;
    if (/ASUBankApp/.test(navigator.userAgent) || (window.webkit && window.webkit.messageHandlers)) return;
    if (/^\/chat(\/|$)/.test(location.pathname)) return;

    function call(action, fields) {
        var body = Object.assign({ action: action, token: token }, fields || {});
        return fetch(API, { method: 'POST', headers: { 'Content-Type': 'text/plain;charset=utf-8' }, body: JSON.stringify(body) })
            .then(function (r) { return r.json(); })
            .then(function (o) { if (o && o.ok) return o; throw new Error((o && o.error) || 'Something went wrong.'); });
    }

    // tiny DOM helper: text always goes in with textContent
    function h(tag, attrs) {
        var el = doc.createElement(tag);
        if (attrs) for (var k in attrs) {
            var v = attrs[k];
            if (v == null || v === false) continue;
            if (k === 'text') el.textContent = v;
            else if (k.indexOf('on') === 0) el.addEventListener(k.slice(2), v);
            else el.setAttribute(k, v === true ? '' : v);
        }
        for (var i = 2; i < arguments.length; i++) add(el, arguments[i]);
        return el;
    }
    function add(el, c) {
        if (c == null || c === false) return;
        if (Array.isArray(c)) { c.forEach(function (x) { add(el, x); }); return; }
        el.appendChild(typeof c === 'string' ? doc.createTextNode(c) : c);
    }
    function color(c) { return /^#[0-9a-f]{3,8}$/i.test(c || '') ? c : '#8C1D40'; }
    function photo(u) { return /^https:\/\//.test(u || '') ? u : ''; }
    function nameOf(u) { return (u && (u.displayName || u.username)) || ''; }
    function avatar(u) {
        var n = nameOf(u);
        var el = h('span', { class: 'abx-av', style: 'background:' + color(u && u.color), 'aria-hidden': 'true' }, (n || '?').charAt(0).toUpperCase());
        var p = photo(u && u.avatar);
        if (p) el.appendChild(h('img', { src: p, alt: '', loading: 'lazy', referrerpolicy: 'no-referrer', onerror: function (e) { e.currentTarget.remove(); } }));
        if (u && u.online) el.appendChild(h('i', { class: 'abx-dot' }));
        return el;
    }
    function preview(m) {
        if (!m) return '';
        return m.text || (m.fileType === 'voice' ? '🎤 Voice message' : m.fileType === 'video' ? '🎬 Video' : m.fileType === 'photo' ? '📷 Photo' : m.fileName ? '📄 ' + m.fileName : '');
    }
    function ago(iso) {
        var s = (Date.now() - new Date(iso).getTime()) / 1000;
        if (!iso || isNaN(s)) return '';
        if (s < 60) return 'now';
        if (s < 3600) return Math.floor(s / 60) + 'm';
        if (s < 86400) return Math.floor(s / 3600) + 'h';
        if (s < 604800) return Math.floor(s / 86400) + 'd';
        return Math.floor(s / 604800) + 'w';
    }
    var ICON = '<svg viewBox="0 0 24 24" width="26" height="26" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M21 12a8 8 0 0 1-11.6 7.1L4 20l1-4.6A8 8 0 1 1 21 12z"/></svg>';

    // ---- the button
    var btn = h('button', { type: 'button', id: 'ab-inbox-btn', class: 'abx-btn', 'aria-label': 'Messages', 'aria-haspopup': 'dialog', 'aria-expanded': 'false' });
    btn.innerHTML = ICON; // fixed markup, no user text
    var badge = h('span', { class: 'abx-badge', hidden: true });
    btn.appendChild(badge);
    function showUnread(r) {
        var n = (r && r.unread) || 0;
        badge.hidden = !n;
        badge.textContent = n > 99 ? '99+' : String(n);
        btn.setAttribute('aria-label', n ? 'Messages, ' + n + ' unread' : 'Messages');
    }

    // ---- the inbox panel
    var tab = get('ab.inbox.tab') === 'rooms' ? 'rooms' : 'messages';
    var panel = h('div', { class: 'abx-panel', role: 'dialog', 'aria-label': 'Messages', hidden: true });
    var title = h('b', { text: 'Messages' });
    var closeBtn = h('button', { type: 'button', class: 'abx-x', 'aria-label': 'Close', text: '✕', onclick: close });
    var newBtn = h('a', { class: 'abx-new', href: '/chat/#@messages', 'aria-label': 'New message', title: 'New message', text: '✏️' });
    var tMsg = h('button', { type: 'button', role: 'tab', text: 'Messages', onclick: function () { pick('messages'); } });
    var tRooms = h('button', { type: 'button', role: 'tab', text: 'Rooms', onclick: function () { pick('rooms'); } });
    var body = h('div', { class: 'abx-body' });
    panel.appendChild(h('div', { class: 'abx-head' }, title, h('span', { class: 'abx-sp' }), newBtn, closeBtn));
    panel.appendChild(h('div', { class: 'abx-tabs', role: 'tablist' }, tMsg, tRooms));
    panel.appendChild(body);
    panel.appendChild(h('a', { class: 'abx-all', href: '/chat/', text: 'Open full chat' }));

    function skeleton() {
        var box = h('div');
        for (var i = 0; i < 5; i++) box.appendChild(h('div', { class: 'abx-row abx-skel' }, h('span', { class: 'abx-av' }), h('span', { class: 'abx-t' }, h('i'), h('i'))));
        return box;
    }
    function empty(t, p) { return h('div', { class: 'abx-empty' }, h('b', { text: t }), h('p', { text: p })); }

    function drawMessages(r) {
        var list = (r && r.conversations) || [];
        if (!list.length) return empty('No private messages yet', 'You can message your friends. Only the two of you see the chat.');
        return h('div', null, list.map(function (c) {
            return h('a', { class: 'abx-row' + (c.unread ? ' unread' : ''), href: '/chat/#@dm/' + encodeURIComponent(c.user.id) },
                avatar(c.user),
                h('span', { class: 'abx-t' },
                    h('span', { class: 'abx-top' }, h('b', { text: nameOf(c.user) }), h('small', { text: ago(c.last && c.last.created) })),
                    h('span', { class: 'abx-top' }, h('small', { class: 'abx-pre', text: preview(c.last) }), c.unread ? h('em', { text: String(c.unread) }) : null)));
        }));
    }
    function drawRooms(rooms) {
        rooms = (rooms || []).filter(function (r) { return !r.archived; });
        if (!rooms.length) return empty('No rooms yet', 'Rooms are group chats for every student.');
        return h('div', null, rooms.map(function (rm) {
            return h('a', { class: 'abx-row', href: '/chat/#' + encodeURIComponent(rm.id) },
                h('span', { class: 'abx-av abx-emoji', 'aria-hidden': 'true', text: rm.emoji || '💬' }),
                h('span', { class: 'abx-t' }, h('b', { text: (rm.locked ? '🔒 ' : '') + rm.name }), h('small', { class: 'abx-pre', text: rm.about || '' })));
        }));
    }
    function render() {
        tMsg.setAttribute('aria-selected', String(tab === 'messages'));
        tRooms.setAttribute('aria-selected', String(tab === 'rooms'));
        tMsg.className = tab === 'messages' ? 'on' : '';
        tRooms.className = tab === 'rooms' ? 'on' : '';
        var saved = tab === 'messages' ? cacheGet('dms') : cacheGet('rooms');
        while (body.firstChild) body.removeChild(body.firstChild);
        body.appendChild(saved ? (tab === 'messages' ? drawMessages(saved) : drawRooms(saved)) : skeleton());
    }
    function refresh(which, force) {
        var stamp = 'ab.inbox.at.' + which;
        if (!force && Date.now() - Number(get(stamp) || 0) < FRESH) return Promise.resolve();
        set(stamp, String(Date.now()));
        var p = which === 'messages'
            ? call('dmList').then(function (r) { cachePut('dms', r); showUnread(r); })
            : call('rooms').then(function (r) { cachePut('rooms', r.rooms || []); });
        return p.then(function () { if (!panel.hidden && tab === which) render(); }).catch(function (e) {
            if (/sign in again|banned|no longer exists/.test(e.message)) { btn.remove(); panel.remove(); return; }
            if (!panel.hidden && tab === which && !(which === 'messages' ? cacheGet('dms') : cacheGet('rooms'))) {
                while (body.firstChild) body.removeChild(body.firstChild);
                body.appendChild(empty("Couldn't load", e.message));
            }
        });
    }
    function pick(t) { tab = t; set('ab.inbox.tab', t); render(); refresh(t, true); }
    function open() {
        panel.hidden = false;
        btn.setAttribute('aria-expanded', 'true');
        btn.classList.add('open');
        render();
        refresh(tab, true);
        closeBtn.focus();
    }
    function close() {
        panel.hidden = true;
        btn.setAttribute('aria-expanded', 'false');
        btn.classList.remove('open');
    }
    btn.addEventListener('click', function () { if (panel.hidden) open(); else close(); });
    doc.addEventListener('keydown', function (e) { if (e.key === 'Escape' && !panel.hidden) { close(); btn.focus(); } });
    doc.addEventListener('click', function (e) { if (!panel.hidden && !panel.contains(e.target) && !btn.contains(e.target)) close(); });

    doc.body.appendChild(panel);
    doc.body.appendChild(btn);
    if (doc.querySelector('.ab-tabbar')) doc.body.classList.add('abx-has-tabbar');
    showUnread(cacheGet('dms'));
    refresh('messages', false);
})();
