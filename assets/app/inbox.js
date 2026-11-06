/* The round Messages button at the bottom right of every asubank.org page (signed-in students only).
   It opens a small inbox with private Messages and chat Rooms, and chats open right inside it:
   read, reply and start a new message without leaving the page. An open chat stays open on the next page.
   Loaded by app.js. Not shown on /chat/ itself or inside the apps. Same Accounts API as accounts.js
   (dmList, rooms, dmThread, dmSend, chat, send, friends). Voice, photos and files show as a label with a link to /chat/. */
(function () {
    'use strict';
    var API = 'https://script.google.com/macros/s/AKfycbzXDE0Km2mxL_gAJFqspZeaADtugupeLN0YQlkEbXnrCkSTKBfImW_lLCKF8_SMI0i_/exec';
    var FRESH = 60000; // the list asks the server at most once a minute, across pages
    var doc = document;

    function get(k) { try { return localStorage.getItem(k); } catch (e) { return null; } }
    function set(k, v) { try { localStorage.setItem(k, v); } catch (e) {} }
    function sget(k) { try { return JSON.parse(sessionStorage.getItem(k)); } catch (e) { return null; } }
    function sset(k, v) { try { if (v == null) sessionStorage.removeItem(k); else sessionStorage.setItem(k, JSON.stringify(v)); } catch (e) {} }
    function cacheGet(k) { try { return JSON.parse(get('ab.cache.' + k)); } catch (e) { return null; } }
    function cachePut(k, v) { set('ab.cache.' + k, JSON.stringify(v)); }

    var token = get('ab.acc.token');
    var me = null;
    try { me = JSON.parse(get('ab.acc.user') || 'null'); } catch (e) {}
    if (!token || !me || doc.getElementById('ab-inbox-btn')) return;
    if (/ASUBankApp/.test(navigator.userAgent) || (window.webkit && window.webkit.messageHandlers)) return;
    if (/^\/chat(\/|$)/.test(location.pathname)) return;

    function call(action, fields) {
        var body = Object.assign({ action: action, token: token }, fields || {});
        return fetch(API, { method: 'POST', headers: { 'Content-Type': 'text/plain;charset=utf-8' }, body: JSON.stringify(body) })
            .then(function (r) { return r.json(); }, function () { throw new Error("Couldn't reach UniHub. Check your connection."); })
            .then(function (o) {
                if (o && o.ok) return o;
                var m = (o && o.error) || 'Something went wrong.';
                if (/sign in again|banned|no longer exists/.test(m)) { btn.remove(); panel.remove(); stop(); }
                throw new Error(m);
            });
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
    function empty(el) { while (el.firstChild) el.removeChild(el.firstChild); }
    function color(c) { return /^#[0-9a-f]{3,8}$/i.test(c || '') ? c : '#8C1D40'; }
    function photo(u) { return /^https:\/\//.test(u || '') ? u : ''; }
    function nameOf(u) { return (u && (u.displayName || u.name || u.username)) || ''; }
    function avatar(u, size) {
        var n = nameOf(u);
        var el = h('span', { class: 'abx-av' + (size ? ' abx-av-s' : ''), style: 'background:' + color(u && u.color), 'aria-hidden': 'true' }, (n || '?').charAt(0).toUpperCase());
        var p = photo(u && u.avatar);
        if (p) el.appendChild(h('img', { src: p, alt: '', loading: 'lazy', referrerpolicy: 'no-referrer', onerror: function (e) { e.currentTarget.remove(); } }));
        if (u && u.online) el.appendChild(h('i', { class: 'abx-dot' }));
        return el;
    }
    function mediaLabel(m) {
        return m.fileType === 'voice' ? '🎤 Voice message' : m.fileType === 'video' ? '🎬 Video' : m.fileType === 'photo' ? '📷 Photo' : m.fileName ? '📄 ' + m.fileName : '';
    }
    function preview(m) { return m ? (m.text || mediaLabel(m)) : ''; }
    function ago(iso) {
        var s = (Date.now() - new Date(iso).getTime()) / 1000;
        if (!iso || isNaN(s)) return '';
        if (s < 60) return 'now';
        if (s < 3600) return Math.floor(s / 60) + 'm';
        if (s < 86400) return Math.floor(s / 3600) + 'h';
        if (s < 604800) return Math.floor(s / 86400) + 'd';
        return Math.floor(s / 604800) + 'w';
    }
    function clock(iso) {
        var d = new Date(iso);
        return isNaN(d) ? '' : d.toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' });
    }
    // ---- voice notes and photos inside the panel (same files the full chat plays)
    function sniff(bytes, name) {
        var b = bytes, ext = String(name || '').split('.').pop().toLowerCase();
        if (b.length > 8 && b[4] === 0x66 && b[5] === 0x74 && b[6] === 0x79 && b[7] === 0x70) return ext === 'mp4' || ext === 'mov' ? 'video/mp4' : 'audio/mp4';
        if (b[0] === 0x1A && b[1] === 0x45 && b[2] === 0xDF && b[3] === 0xA3) return 'audio/webm';
        if (b[0] === 0x4F && b[1] === 0x67 && b[2] === 0x67 && b[3] === 0x53) return 'audio/ogg';
        if (b[0] === 0x52 && b[1] === 0x49 && b[2] === 0x46 && b[3] === 0x46) return b[8] === 0x57 && b[9] === 0x45 ? 'image/webp' : 'audio/wav';
        if (b[0] === 0xFF && b[1] === 0xD8) return 'image/jpeg';
        if (b[0] === 0x89 && b[1] === 0x50) return 'image/png';
        if ((b[0] === 0x49 && b[1] === 0x44 && b[2] === 0x33) || (b[0] === 0xFF && (b[1] & 0xE0) === 0xE0)) return 'audio/mpeg';
        return { m4a: 'audio/mp4', weba: 'audio/webm', webm: 'audio/webm', ogg: 'audio/ogg', jpg: 'image/jpeg', jpeg: 'image/jpeg', png: 'image/png', webp: 'image/webp' }[ext] || 'application/octet-stream';
    }
    var fileUrls = {};
    function fileUrl(isDm, m) {
        var key = (isDm ? 'dm' : 'room') + m.id;
        if (!fileUrls[key]) fileUrls[key] = call(isDm ? 'dmFile' : 'mediaFile', isDm ? { id: m.id } : { scope: 'room', id: m.id }).then(function (r) {
            var bin = atob(r.data), bytes = new Uint8Array(bin.length);
            for (var i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
            return URL.createObjectURL(new Blob([bytes], { type: sniff(bytes, r.name || m.fileName) }));
        }).catch(function (e) { delete fileUrls[key]; throw e; });
        return fileUrls[key];
    }
    function voiceTime(s) { s = Math.round(s || 0); return Math.floor(s / 60) + ':' + ('0' + (s % 60)).slice(-2); }
    var audios = {}, audioNow = null;
    function voicePlayer(isDm, m) {
        var key = (isDm ? 'dm' : 'room') + m.id;
        var play = h('button', { type: 'button', class: 'abx-play', 'aria-label': 'Play voice message', text: '▶' });
        var bar = h('span', { class: 'abx-track' }, h('i'));
        var time = h('small', { class: 'abx-vtime', text: voiceTime(m.duration) });
        var box = h('span', { class: 'abx-voice' }, play, bar, time);
        function draw(a) {
            var on = a && !a.paused;
            play.textContent = on ? '❚❚' : '▶';
            play.setAttribute('aria-label', on ? 'Pause' : 'Play voice message');
            bar.firstChild.style.width = (a && a.duration && isFinite(a.duration) ? Math.min(100, a.currentTime / a.duration * 100) : 0) + '%';
            if (a && on) time.textContent = voiceTime(a.currentTime);
            else if (!a || !a.currentTime) time.textContent = voiceTime(m.duration || (a && isFinite(a.duration) ? a.duration : 0));
        }
        function bind(a) {
            a.ontimeupdate = a.onplay = a.onpause = function () { draw(a); };
            a.onended = function () { a.currentTime = 0; draw(a); };
            draw(a);
        }
        if (audios[key]) bind(audios[key]);
        play.addEventListener('click', function () {
            var a = audios[key];
            if (a) {
                if (a.paused) { if (audioNow && audioNow !== a) audioNow.pause(); audioNow = a; a.play().catch(function () {}); } else a.pause();
                return;
            }
            play.disabled = true; play.textContent = '…';
            fileUrl(isDm, m).then(function (u) {
                a = audios[key] = new Audio(u);
                a.onerror = function () { delete audios[key]; time.textContent = "Can't play here"; play.textContent = '▶'; };
                bind(a);
                if (audioNow) audioNow.pause();
                audioNow = a;
                return a.play();
            }).catch(function () { time.textContent = "Couldn't load"; play.textContent = '▶'; })
              .then(function () { play.disabled = false; });
        });
        return box;
    }
    function photoInPanel(isDm, m) {
        var img = h('img', { class: 'abx-photo', alt: 'Photo' });
        var tile = h('button', { type: 'button', class: 'abx-photo-btn', 'aria-label': 'View photo', onclick: function () { if (img.src) window.open(img.src, '_blank', 'noopener'); } }, h('span', { class: 'abx-photo-wait', text: '📷' }), img);
        fileUrl(isDm, m).then(function (u) { img.src = u; tile.classList.add('ready'); }).catch(function () { tile.firstChild.textContent = '📷 Photo'; });
        return tile;
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

    // ---- the panel: a list view and a chat view
    var panel = h('div', { class: 'abx-panel', role: 'dialog', 'aria-label': 'Messages', hidden: true });
    doc.body.appendChild(panel);
    doc.body.appendChild(btn);
    if (doc.querySelector('.ab-tabbar')) doc.body.classList.add('abx-has-tabbar');

    var tab = get('ab.inbox.tab') === 'rooms' ? 'rooms' : 'messages';
    var view = sget('ab.inbox.view'); // null = list, {k:'dm'|'room'|'new', id, name, color, avatar, emoji}
    var timer = null, viewSeq = 0;
    function stop() { if (timer) { clearTimeout(timer); timer = null; } viewSeq++; }

    function skeleton(n) {
        var box = h('div');
        for (var i = 0; i < (n || 5); i++) box.appendChild(h('div', { class: 'abx-row abx-skel' }, h('span', { class: 'abx-av' }), h('span', { class: 'abx-t' }, h('i'), h('i'))));
        return box;
    }
    function note(t, p) { return h('div', { class: 'abx-empty' }, h('b', { text: t }), p ? h('p', { text: p }) : null); }
    function closeBtn() { return h('button', { type: 'button', class: 'abx-x', 'aria-label': 'Close', text: '✕', onclick: close }); }
    function go(v) { stop(); view = v; sset('ab.inbox.view', v); draw(); }

    function draw() {
        stop();
        empty(panel);
        if (!view) return drawList();
        if (view.k === 'new') return drawNew();
        return drawChat();
    }

    // ---- list
    function drawList() {
        var body = h('div', { class: 'abx-body' });
        var tMsg = h('button', { type: 'button', role: 'tab', class: tab === 'messages' ? 'on' : '', 'aria-selected': String(tab === 'messages'), text: 'Messages', onclick: function () { pick('messages'); } });
        var tRooms = h('button', { type: 'button', role: 'tab', class: tab === 'rooms' ? 'on' : '', 'aria-selected': String(tab === 'rooms'), text: 'Rooms', onclick: function () { pick('rooms'); } });
        panel.appendChild(h('div', { class: 'abx-head' }, h('b', { class: 'abx-title', text: 'Messages' }), h('span', { class: 'abx-sp' }),
            h('button', { type: 'button', class: 'abx-x', 'aria-label': 'New message', title: 'New message', text: '✏️', onclick: function () { go({ k: 'new' }); } }),
            closeBtn()));
        panel.appendChild(h('div', { class: 'abx-tabs', role: 'tablist' }, tMsg, tRooms));
        panel.appendChild(body);
        panel.appendChild(h('a', { class: 'abx-all', href: '/chat/', text: 'Open full chat' }));
        function fill() {
            var saved = tab === 'messages' ? cacheGet('dms') : cacheGet('rooms');
            empty(body);
            body.appendChild(saved ? (tab === 'messages' ? listMessages(saved) : listRooms(saved)) : skeleton());
        }
        fill();
        var seq = viewSeq;
        refresh(tab, true).then(function () { if (seq === viewSeq) fill(); }, function (e) {
            if (seq === viewSeq && !(tab === 'messages' ? cacheGet('dms') : cacheGet('rooms'))) { empty(body); body.appendChild(note("Couldn't load", e.message)); }
        });
    }
    function pick(t) { tab = t; set('ab.inbox.tab', t); draw(); }
    function listMessages(r) {
        var list = (r && r.conversations) || [];
        if (!list.length) return note('No private messages yet', 'Tap ✏️ to message a friend. Only the two of you see the chat.');
        return h('div', null, list.map(function (c) {
            return h('button', { type: 'button', class: 'abx-row' + (c.unread ? ' unread' : ''), onclick: function () { go({ k: 'dm', id: c.user.id, name: nameOf(c.user), color: c.user.color, avatar: c.user.avatar }); } },
                avatar(c.user),
                h('span', { class: 'abx-t' },
                    h('span', { class: 'abx-top' }, h('b', { text: nameOf(c.user) }), h('small', { text: ago(c.last && c.last.created) })),
                    h('span', { class: 'abx-top' }, h('small', { class: 'abx-pre', text: preview(c.last) }), c.unread ? h('em', { text: String(c.unread) }) : null)));
        }));
    }
    function listRooms(rooms) {
        rooms = (rooms || []).filter(function (r) { return !r.archived; });
        if (!rooms.length) return note('No rooms yet', 'Rooms are group chats for every student.');
        return h('div', null, rooms.map(function (rm) {
            return h('button', { type: 'button', class: 'abx-row', onclick: function () { go({ k: 'room', id: rm.id, name: rm.name, emoji: rm.emoji || '💬' }); } },
                h('span', { class: 'abx-av abx-emoji', 'aria-hidden': 'true', text: rm.emoji || '💬' }),
                h('span', { class: 'abx-t' }, h('b', { text: (rm.locked ? '🔒 ' : '') + rm.name }), h('small', { class: 'abx-pre', text: rm.about || '' })));
        }));
    }
    function refresh(which, force) {
        var stamp = 'ab.inbox.at.' + which;
        if (!force && Date.now() - Number(get(stamp) || 0) < FRESH) return Promise.resolve();
        set(stamp, String(Date.now()));
        return which === 'messages'
            ? call('dmList').then(function (r) { cachePut('dms', r); showUnread(r); })
            : call('rooms').then(function (r) { cachePut('rooms', r.rooms || []); });
    }

    // ---- new message: pick a friend
    function drawNew() {
        var search = h('input', { type: 'search', class: 'abx-search', placeholder: 'Search friends', 'aria-label': 'Search friends' });
        var body = h('div', { class: 'abx-body' }, skeleton(4));
        panel.appendChild(h('div', { class: 'abx-head' }, back(), h('b', { class: 'abx-title', text: 'New message' }), h('span', { class: 'abx-sp' }), closeBtn()));
        panel.appendChild(h('div', { class: 'abx-pad' }, search));
        panel.appendChild(body);
        var friends = null, seq = viewSeq;
        function fill() {
            if (!friends) return;
            var q = search.value.trim().toLowerCase();
            var shown = friends.filter(function (u) { return !q || nameOf(u).toLowerCase().indexOf(q) >= 0; });
            empty(body);
            if (!friends.length) { body.appendChild(note('No friends yet', 'Add friends from the Feed page, then you can message them here.')); return; }
            if (!shown.length) { body.appendChild(note('Nobody with that name')); return; }
            shown.forEach(function (u) {
                body.appendChild(h('button', { type: 'button', class: 'abx-row', onclick: function () { go({ k: 'dm', id: u.id, name: nameOf(u), color: u.color, avatar: u.avatar }); } },
                    avatar(u), h('span', { class: 'abx-t' }, h('b', { text: nameOf(u) }), u.username ? h('small', { text: '@' + u.username }) : null)));
            });
        }
        search.addEventListener('input', fill);
        call('friends').then(function (r) { if (seq !== viewSeq) return; friends = r.friends || []; fill(); })
            .catch(function (e) { if (seq === viewSeq) { empty(body); body.appendChild(note("Couldn't load", e.message)); } });
        setTimeout(function () { search.focus(); }, 50);
    }
    function back() { return h('button', { type: 'button', class: 'abx-x abx-back', 'aria-label': 'Back', text: '‹', onclick: function () { go(null); } }); }

    // ---- a chat inside the panel
    function drawChat() {
        var v = view, isDm = v.k === 'dm';
        var full = isDm ? '/chat/#@dm/' + encodeURIComponent(v.id) : '/chat/#' + encodeURIComponent(v.id);
        var face = isDm ? avatar({ displayName: v.name, color: v.color, avatar: v.avatar }, true) : h('span', { class: 'abx-av abx-av-s abx-emoji', 'aria-hidden': 'true', text: v.emoji || '💬' });
        var name = h('b', { class: 'abx-cname', text: v.name || '' });
        var sub = h('small', { class: 'abx-csub' });
        var feed = h('div', { class: 'abx-feed', 'aria-live': 'polite' }, h('div', { class: 'abx-skel abx-bubbles' }, h('i'), h('i'), h('i')));
        var err = h('p', { class: 'abx-err', role: 'alert', hidden: true });
        var input = h('textarea', { rows: 1, maxlength: isDm ? 2000 : 1000, placeholder: 'Message…', 'aria-label': 'Message' });
        var sendBtn = h('button', { type: 'submit', class: 'abx-send', 'aria-label': 'Send', text: '↑', disabled: true });
        var form = h('form', { class: 'abx-composer', onsubmit: function (e) { e.preventDefault(); deliver(); } }, input, sendBtn);
        var closed = h('p', { class: 'abx-closed', hidden: true });
        panel.appendChild(h('div', { class: 'abx-head abx-chead' }, back(), face, h('span', { class: 'abx-t' }, name, sub), h('span', { class: 'abx-sp' }),
            h('a', { class: 'abx-x', href: full, 'aria-label': 'Open in full chat', title: 'Open in full chat', text: '⤢' }), closeBtn()));
        panel.appendChild(feed);
        panel.appendChild(err);
        panel.appendChild(closed);
        panel.appendChild(form);

        var msgs = [], byId = {}, last = null, people = {}, first = true, seq = viewSeq, seenUpTo = '';
        function mine(m) { return isDm ? m.from === me.id : m.userId === me.id; }
        function showErr(e) { err.hidden = !e; err.textContent = e ? e.message : ''; }
        function render() {
            var atBottom = feed.scrollHeight - feed.scrollTop - feed.clientHeight < 80;
            empty(feed);
            if (!msgs.length) { feed.appendChild(note(isDm ? 'Say hi 👋' : 'No messages yet', isDm ? 'Only the two of you see this chat.' : 'Be the first to write here.')); return; }
            var prev = null, seenId = null;
            if (isDm && seenUpTo) msgs.forEach(function (m) { if (mine(m) && !m.pending && m.created <= seenUpTo) seenId = m.id; });
            msgs.forEach(function (m) {
                var own = mine(m);
                var who = isDm ? null : (people[m.userId] || { displayName: m.displayName, color: m.color, avatar: m.avatar });
                var grouped = prev && (isDm ? prev.from === m.from : prev.userId === m.userId) && (new Date(m.created) - new Date(prev.created) < 300000);
                var label = mediaLabel(m);
                var bubble = h('div', { class: 'abx-bubble' + (m.pending ? ' pending' : '') },
                    !own && !isDm && !grouped ? h('b', { class: 'abx-from', style: 'color:' + color(who && who.color), text: nameOf(who) || m.username || '' }) : null,
                    m.text ? h('span', { class: 'abx-text', text: m.text }) : null,
                    m.fileType === 'voice' && !m.pending ? voicePlayer(isDm, m)
                        : m.fileType === 'photo' && !m.pending ? photoInPanel(isDm, m)
                        : m.hasFile || label ? h('a', { class: 'abx-file', href: full, text: (label || '📎 File') + ' · open' }) : null,
                    h('small', { class: 'abx-time', text: m.pending ? 'Sending…' : clock(m.created) + (m.edited ? ' · edited' : '') }));
                feed.appendChild(h('div', { class: 'abx-msg' + (own ? ' mine' : '') + (grouped ? ' grouped' : '') },
                    !own && !isDm ? (grouped ? h('span', { class: 'abx-av-gap' }) : avatar(who, true)) : null, bubble));
                if (seenId && m.id === seenId) feed.appendChild(h('small', { class: 'abx-seen', text: 'Seen' }));
                prev = m;
            });
            if (atBottom || first) feed.scrollTop = feed.scrollHeight;
        }
        function addAll(list) {
            var changed = false;
            (list || []).forEach(function (m) {
                if (!m || !m.id || byId[m.id]) return;
                byId[m.id] = m; msgs.push(m); changed = true;
            });
            if (changed) {
                msgs.sort(function (a, b) { return a.created < b.created ? -1 : a.created > b.created ? 1 : 0; });
                var real = msgs.filter(function (m) { return !m.pending; });
                last = real.length ? real[real.length - 1].id : last;
            }
            return changed;
        }
        function removeIds(ids) {
            if (!ids || !ids.length) return false;
            var gone = {}; ids.forEach(function (i) { gone[i] = 1; });
            var before = msgs.length;
            msgs = msgs.filter(function (m) { if (gone[m.id]) { delete byId[m.id]; return false; } return true; });
            return msgs.length !== before;
        }
        function applyEdits(list) {
            var changed = false;
            (list || []).forEach(function (e) { var m = byId[e.id]; if (m && m.text !== e.text) { m.text = e.text; m.edited = true; changed = true; } });
            return changed;
        }
        function poll() {
            if (seq !== viewSeq) return;
            var f = isDm ? { userId: v.id } : { room: v.id };
            if (last) f.after = last;
            call(isDm ? 'dmThread' : 'chat', f).then(function (r) {
                if (seq !== viewSeq) return;
                var changed = first;
                if (isDm && r.user) {
                    name.textContent = nameOf(r.user);
                    sub.textContent = r.user.online ? 'Online now' : '';
                    var nf = avatar(r.user, true); face.replaceWith(nf); face = nf;
                    if (r.canSend != null) { form.hidden = !r.canSend; closed.hidden = !!r.canSend; closed.textContent = r.canSend ? '' : 'You can message friends only.'; }
                    if ((r.seenUpTo || seenUpTo) !== seenUpTo) { seenUpTo = r.seenUpTo; changed = true; }
                }
                if (!isDm && r.room) {
                    name.textContent = r.room.name || v.name;
                    sub.textContent = r.room.about || '';
                    var locked = r.room.archived || r.room.locked || (r.room.adminOnly && me.role !== 'admin');
                    form.hidden = !!locked; closed.hidden = !locked;
                    closed.textContent = locked ? (r.room.adminOnly ? 'Only admins post in this room.' : 'This room is closed.') : '';
                }
                if (r.people) people = r.people;
                if (removeIds(r.deleted)) changed = true;
                if (addAll(r.messages)) changed = true;
                if (applyEdits(r.edited)) changed = true;
                if (changed) render();
                first = false;
                showErr(null);
            }).catch(function (e) { if (seq === viewSeq) { showErr(e); if (first) { first = false; render(); } } })
              .then(function () { if (seq === viewSeq && !panel.hidden) timer = setTimeout(poll, doc.hidden ? 15000 : 3000); });
        }
        function deliver() {
            var text = input.value.trim();
            if (!text) return;
            input.value = ''; tidy();
            var temp = { id: 'local-' + Date.now(), text: text, created: new Date().toISOString(), pending: true };
            if (isDm) { temp.from = me.id; temp.to = v.id; } else { temp.userId = me.id; temp.displayName = me.displayName; temp.color = me.color; }
            byId[temp.id] = temp; msgs.push(temp); render(); feed.scrollTop = feed.scrollHeight;
            call(isDm ? 'dmSend' : 'send', isDm ? { userId: v.id, text: text } : { room: v.id, text: text }).then(function (r) {
                delete byId[temp.id];
                msgs = msgs.filter(function (m) { return m !== temp; });
                if (r.message) addAll([r.message]);
                render(); feed.scrollTop = feed.scrollHeight;
                showErr(null);
                set('ab.inbox.at.messages', '0'); // the list shows the new last message next time
            }).catch(function (e) {
                delete byId[temp.id];
                msgs = msgs.filter(function (m) { return m !== temp; });
                render(); showErr(e);
                if (!input.value) { input.value = text; tidy(); }
            });
        }
        function tidy() {
            input.style.height = 'auto';
            input.style.height = Math.min(input.scrollHeight, 110) + 'px';
            sendBtn.disabled = !input.value.trim();
        }
        input.addEventListener('input', tidy);
        input.addEventListener('keydown', function (e) { if (e.key === 'Enter' && !e.shiftKey && !e.isComposing) { e.preventDefault(); deliver(); } });
        poll();
        setTimeout(function () { input.focus(); }, 50);
    }

    // ---- open and close
    function open() {
        panel.hidden = false;
        btn.setAttribute('aria-expanded', 'true');
        btn.classList.add('open');
        sset('ab.inbox.open', true);
        draw();
    }
    function close() {
        stop();
        panel.hidden = true;
        btn.setAttribute('aria-expanded', 'false');
        btn.classList.remove('open');
        sset('ab.inbox.open', null);
        btn.focus();
    }
    btn.addEventListener('click', function () { if (panel.hidden) open(); else close(); });
    doc.addEventListener('keydown', function (e) { if (e.key === 'Escape' && !panel.hidden) close(); });
    doc.addEventListener('visibilitychange', function () {
        // come back to the tab: catch up at once instead of waiting for the slow timer
        if (!doc.hidden && !panel.hidden && view && view.k !== 'new') draw();
    });

    showUnread(cacheGet('dms'));
    if (sget('ab.inbox.open')) open(); // a chat that was open stays open on the next page
    else refresh('messages', false).catch(function () {});
})();
