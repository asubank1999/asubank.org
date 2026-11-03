/* ASU Bank accounts, chat and admin on the website.
   Same "ASU Bank Accounts" Apps Script as the iOS and Android apps (tools/apps-script/accounts-api.gs),
   so one account works everywhere. Pages: /account/, /chat/, /admin/ (each has <div id="acc-app" data-view="...">). */
(function () {
    'use strict';
    var API = 'https://script.google.com/macros/s/AKfycbzXDE0Km2mxL_gAJFqspZeaADtugupeLN0YQlkEbXnrCkSTKBfImW_lLCKF8_SMI0i_/exec';
    var root = document.getElementById('acc-app');
    if (!root) return;
    var view = root.getAttribute('data-view');

    // ---- storage (the session token stays in this browser only)
    function get(k) { try { return localStorage.getItem(k); } catch (e) { return null; } }
    function set(k, v) { try { if (v == null) localStorage.removeItem(k); else localStorage.setItem(k, v); } catch (e) {} }
    var token = get('ab.acc.token');
    var user = null;
    try { user = token ? JSON.parse(get('ab.acc.user') || 'null') : null; } catch (e) { user = null; }

    // ---- API: POST text/plain JSON (no CORS preflight)
    function call(action, fields) {
        var body = Object.assign({ action: action }, fields || {});
        if (token) body.token = token;
        return fetch(API, { method: 'POST', headers: { 'Content-Type': 'text/plain;charset=utf-8' }, body: JSON.stringify(body) })
            .then(function (r) { return r.json(); }, function () { throw new Error("Couldn't reach ASU Bank. Check your connection and try again."); })
            .then(function (o) {
                if (o && o.ok) return o;
                var m = (o && o.error) || 'Something went wrong.';
                if (/sign in again|banned|no longer exists/.test(m)) signOutLocally();
                var e = new Error(m); e.needCode = !!(o && o.needCode); throw e;
            });
    }
    function adopt(r) {
        if (r.token) { token = r.token; set('ab.acc.token', token); }
        if (r.user) { user = r.user; set('ab.acc.user', JSON.stringify(user)); }
    }
    function signOutLocally() { token = null; user = null; set('ab.acc.token', null); set('ab.acc.user', null); }
    function isAdmin() { return !!(user && user.role === 'admin'); }
    function nameOf(u) { return (u && (u.displayName || u.username)) || ''; }

    // ---- tiny DOM helper (text is always set with textContent, never HTML)
    function h(tag, attrs) {
        var el = document.createElement(tag);
        if (attrs) for (var k in attrs) {
            var v = attrs[k];
            if (v == null || v === false) continue;
            if (k === 'class') el.className = v;
            else if (k === 'text') el.textContent = v;
            else if (k.slice(0, 2) === 'on') el.addEventListener(k.slice(2), v);
            else if (k === 'style') el.setAttribute('style', v);
            else el.setAttribute(k, v === true ? '' : v);
        }
        for (var i = 2; i < arguments.length; i++) {
            var c = arguments[i];
            if (c == null || c === false) continue;
            if (Array.isArray(c)) c.forEach(function (x) { if (x) el.appendChild(typeof x === 'string' ? document.createTextNode(x) : x); });
            else el.appendChild(typeof c === 'string' ? document.createTextNode(c) : c);
        }
        return el;
    }
    function safeColor(c) { return /^#[0-9a-fA-F]{3,8}$/.test(c || '') ? c : '#8C1D40'; }
    function avatar(n, color, size, admin) {
        size = size || 40;
        return h('span', { class: 'acc-av' + (admin ? ' admin' : ''), style: 'width:' + size + 'px;height:' + size + 'px;font-size:' + Math.round(size * .42) + 'px;background:' + safeColor(color), 'aria-hidden': 'true' }, (n || '?').charAt(0).toUpperCase());
    }
    function field(label, type, value, hint) {
        var input = h('input', { type: type || 'text', value: value || '', autocomplete: type === 'password' ? 'current-password' : 'off', autocapitalize: 'off', spellcheck: 'false' });
        return { el: h('label', { class: 'acc-field' }, h('span', { text: label }), input, hint ? h('small', { text: hint }) : null), input: input };
    }
    function fmtTime(iso) { var d = new Date(iso); return isNaN(d) ? '' : d.toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' }); }
    function fmtDate(iso) { var d = new Date(iso); return isNaN(d) ? '–' : d.toLocaleString([], { dateStyle: 'medium', timeStyle: 'short' }); }
    function dayLabel(d) {
        var t = new Date(); t.setHours(0, 0, 0, 0);
        var x = new Date(d); x.setHours(0, 0, 0, 0);
        var diff = Math.round((t - x) / 864e5);
        return diff === 0 ? 'Today' : diff === 1 ? 'Yesterday' : x.toLocaleDateString([], { dateStyle: 'medium' });
    }
    function clear() { while (root.firstChild) root.removeChild(root.firstChild); }
    function errBox() { return h('p', { class: 'acc-err', role: 'alert' }); }
    function showErr(box, e) { box.textContent = e ? (e.message || String(e)) : ''; }
    function busy(btn, on, label) { btn.disabled = on; if (label) btn.textContent = on ? 'Please wait…' : label; }

    // ---- sign in / create account
    function signInForm(after) {
        var mode = 0;
        var wrap = h('div', { class: 'acc-card acc-auth' });
        function draw() {
            while (wrap.firstChild) wrap.removeChild(wrap.firstChild);
            var login = field(mode === 0 ? 'Username or email' : 'Username', 'text', '', mode === 1 ? '3–20 letters, numbers, . or _' : null);
            var pw = field('Password', 'password', '', mode === 1 ? 'At least 6 characters' : null);
            var pw2 = field('Confirm password', 'password');
            var dn = field('Display name (optional)');
            var em = field('University email (optional)', 'email');
            var uid = field('University ID (optional)', 'text');
            var code = field('6-digit code', 'text');
            var codeBox = h('div', { class: 'acc-code', hidden: true },
                h('b', { text: 'Admin verification' }),
                h('p', { class: 'acc-codemsg', text: "This name belongs to an ASU Bank admin. Send a code to the owner's email to prove it's you." }),
                code.el,
                h('button', { type: 'button', class: 'ab-btn tinted', text: 'Send the code', onclick: function (ev) {
                    var b = ev.currentTarget; busy(b, true, 'Send the code');
                    call('adminCode', { username: login.input.value.trim(), email: em.input.value.trim(), uniId: uid.input.value.trim() })
                        .then(function (r) { codeBox.querySelector('.acc-codemsg').textContent = 'We sent a 6-digit code to ' + (r.sentTo || "the owner's email") + '.'; })
                        .catch(function (e) { showErr(err, e); })
                        .then(function () { busy(b, false, 'Send again'); });
                } }));
            var err = errBox();
            var submit = h('button', { type: 'submit', class: 'ab-btn acc-wide', text: mode === 0 ? 'Sign in' : 'Create account' });
            var form = h('form', { onsubmit: function (ev) {
                ev.preventDefault(); showErr(err, null);
                if (mode === 1 && pw.input.value !== pw2.input.value) { showErr(err, new Error("The passwords don't match.")); return; }
                busy(submit, true, submit.textContent);
                var p = mode === 0
                    ? call('login', { login: login.input.value.trim(), password: pw.input.value, platform: 'web' })
                    : call('register', { username: login.input.value.trim(), password: pw.input.value, displayName: dn.input.value.trim(), email: em.input.value.trim(), uniId: uid.input.value.trim(), code: code.input.value.trim(), platform: 'web' });
                p.then(function (r) { adopt(r); after(); })
                    .catch(function (e) { showErr(err, e); if (e.needCode) codeBox.hidden = false; busy(submit, false, mode === 0 ? 'Sign in' : 'Create account'); });
            } },
                login.el, pw.el,
                mode === 1 ? [pw2.el, dn.el, em.el, uid.el, codeBox] : null,
                err, submit);
            if (mode === 1) pw.input.autocomplete = pw2.input.autocomplete = 'new-password';
            wrap.appendChild(h('img', { src: '/icons/icon-192x192.png', alt: '', width: 72, height: 72, class: 'acc-logo' }));
            wrap.appendChild(h('h2', { text: mode === 0 ? 'Welcome back' : 'Join ASU Bank' }));
            wrap.appendChild(h('p', { class: 'acc-muted', text: 'An account lets you chat with other students and ask for help. It works here and in the ASU Bank apps.' }));
            wrap.appendChild(h('div', { class: 'acc-seg', role: 'tablist' },
                ['Sign in', 'Create account'].map(function (l, i) {
                    return h('button', { type: 'button', role: 'tab', 'aria-selected': String(mode === i), class: mode === i ? 'on' : '', text: l, onclick: function () { mode = i; draw(); } });
                })));
            wrap.appendChild(form);
            if (mode === 0) wrap.appendChild(h('p', { class: 'acc-muted acc-small', text: 'Forgot your password? Ask an admin in the chat or on WhatsApp to reset it.' }));
        }
        draw();
        return wrap;
    }

    function needSignIn(title, text, after) {
        clear();
        root.appendChild(h('div', { class: 'acc-intro' }, h('h1', { class: 'ab-large', text: title }), h('p', { class: 'ab-sub', text: text })));
        root.appendChild(signInForm(after));
    }

    // ---- /account/
    function accountView() {
        if (!token || !user) return needSignIn('Account', 'Sign in to chat with other students. Your account works on the website and in the apps.', accountView);
        clear();
        var u = user;
        var err = errBox();
        root.appendChild(h('div', { class: 'acc-profile' }, avatar(nameOf(u), u.color, 84, isAdmin()),
            h('h1', { text: nameOf(u) }), h('p', { class: 'acc-muted', text: '@' + u.username + (isAdmin() ? ' · Admin' : '') })));
        var dn = field('Display name', 'text', u.displayName), em = field('University email (optional)', 'email', u.email), uid = field('University ID (optional)', 'text', u.uniId);
        var save = h('button', { type: 'submit', class: 'ab-btn', text: 'Save profile' });
        root.appendChild(h('form', { class: 'acc-card', onsubmit: function (ev) {
            ev.preventDefault(); busy(save, true, 'Save profile');
            call('updateProfile', { displayName: dn.input.value.trim(), email: em.input.value.trim(), uniId: uid.input.value.trim() })
                .then(function (r) { adopt(r); showErr(err, null); save.textContent = 'Saved ✓'; save.disabled = false; })
                .catch(function (e) { showErr(err, e); busy(save, false, 'Save profile'); });
        } }, h('h2', { text: 'Profile' }), dn.el, em.el, uid.el, save));

        var old = field('Current password', 'password'), nw = field('New password', 'password', '', 'At least 6 characters');
        nw.input.autocomplete = 'new-password';
        var change = h('button', { type: 'submit', class: 'ab-btn gray', text: 'Change password' });
        root.appendChild(h('form', { class: 'acc-card', onsubmit: function (ev) {
            ev.preventDefault(); busy(change, true, 'Change password');
            call('changePassword', { old: old.input.value, password: nw.input.value })
                .then(function () { old.input.value = nw.input.value = ''; showErr(err, null); change.textContent = 'Password changed ✓'; change.disabled = false; })
                .catch(function (e) { showErr(err, e); busy(change, false, 'Change password'); });
        } }, h('h2', { text: 'Password' }), old.el, nw.el, change));

        root.appendChild(err);
        root.appendChild(h('div', { class: 'acc-card acc-actions' },
            h('a', { class: 'ab-btn tinted', href: '/chat/', text: 'Open the chat' }),
            isAdmin() ? h('a', { class: 'ab-btn tinted', href: '/admin/', text: 'Admin' }) : null,
            h('button', { type: 'button', class: 'ab-btn gray', text: 'Sign out', onclick: function () {
                call('logout').catch(function () {}).then(function () { signOutLocally(); accountView(); });
            } }),
            h('button', { type: 'button', class: 'ab-btn gray acc-danger', text: 'Delete account', onclick: function () {
                var p = window.prompt('This removes your account and can’t be undone. Type your password to confirm:');
                if (!p) return;
                call('deleteAccount', { password: p }).then(function () { signOutLocally(); accountView(); }).catch(function (e) { showErr(err, e); });
            } })));
        call('me').then(function (r) { adopt(r); }).catch(function () {});
    }

    // ---- /chat/
    var pollTimer = null;
    function chatView() {
        if (pollTimer) { clearTimeout(pollTimer); pollTimer = null; }
        if (!token || !user) return needSignIn('Student chat', 'Talk with other ASU students, ask for help and share materials. Chat is only for signed-in students.', chatView);
        var room = decodeURIComponent((location.hash || '').replace('#', ''));
        if (room) return roomView(room);
        clear();
        root.appendChild(h('div', { class: 'acc-intro' }, h('h1', { class: 'ab-large', text: 'Chat' }),
            h('p', { class: 'ab-sub', text: 'Signed in as @' + user.username + '. Be kind and helpful; admins can remove messages and ban accounts.' })));
        var list = h('div', { class: 'acc-card acc-rooms' }, h('p', { class: 'acc-muted', text: 'Loading rooms…' }));
        root.appendChild(list);
        call('rooms').then(function (r) {
            while (list.firstChild) list.removeChild(list.firstChild);
            (r.rooms || []).forEach(function (rm) {
                list.appendChild(h('a', { class: 'acc-room', href: '#' + encodeURIComponent(rm.id) },
                    h('span', { class: 'acc-emoji', text: rm.emoji || '💬' }),
                    h('span', { class: 'acc-room-t' }, h('b', { text: rm.name }), h('small', { text: rm.about || '' })),
                    rm.adminOnly ? h('em', { text: 'Admins post' }) : null));
            });
        }).catch(function (e) { list.textContent = e.message; });
    }

    function roomView(roomId) {
        clear();
        var info = { id: roomId, name: roomId, emoji: '💬' };
        var msgs = [], byId = {}, replyTo = null, last = null;
        var title = h('h1', { class: 'acc-room-title', text: '💬 ' + roomId });
        var feed = h('div', { class: 'acc-feed', 'aria-live': 'polite' });
        var err = errBox();
        var input = h('textarea', { rows: 1, maxlength: 1000, placeholder: 'Message' });
        var replyBar = h('div', { class: 'acc-reply', hidden: true });
        var send = h('button', { type: 'submit', class: 'ab-btn', text: 'Send' });
        var composer = h('form', { class: 'acc-composer', onsubmit: function (ev) {
            ev.preventDefault();
            var t = input.value.trim(); if (!t) return;
            busy(send, true, 'Send');
            call('send', { room: roomId, text: t, replyTo: replyTo ? replyTo.id : '' })
                .then(function (r) { input.value = ''; setReply(null); if (r.message) add([r.message]); showErr(err, null); })
                .catch(function (e) { showErr(err, e); })
                .then(function () { busy(send, false, 'Send'); input.focus(); });
        } }, replyBar, h('div', { class: 'acc-compose-row' }, input, send));
        input.addEventListener('keydown', function (e) { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); composer.requestSubmit ? composer.requestSubmit() : send.click(); } });
        var readonly = h('p', { class: 'acc-muted acc-small acc-center', hidden: true, text: 'Only admins post here.' });
        root.appendChild(h('div', { class: 'acc-room-head' }, h('a', { href: '#', class: 'acc-back', text: '‹ Rooms' }), title));
        root.appendChild(feed);
        root.appendChild(err);
        root.appendChild(composer);
        root.appendChild(readonly);

        function setReply(m) {
            replyTo = m;
            while (replyBar.firstChild) replyBar.removeChild(replyBar.firstChild);
            replyBar.hidden = !m;
            if (m) replyBar.appendChild(h('span', null, h('b', { text: 'Replying to ' + nameOf(m) + ': ' }), m.text.slice(0, 80)),
                h('button', { type: 'button', class: 'acc-x', 'aria-label': 'Cancel reply', text: '×', onclick: function () { setReply(null); } }));
        }

        function bubble(m, prev) {
            var mine = m.userId === user.id, admin = m.role === 'admin';
            var grouped = prev && prev.userId === m.userId && new Date(prev.created).toDateString() === new Date(m.created).toDateString();
            var reply = m.replyTo && byId[m.replyTo];
            var actions = h('span', { class: 'acc-msg-actions' },
                h('button', { type: 'button', text: 'Reply', onclick: function () { setReply(m); input.focus(); } }),
                (mine || isAdmin()) ? h('button', { type: 'button', text: 'Delete', onclick: function () {
                    if (!window.confirm('Delete this message?')) return;
                    call('deleteMessage', { id: m.id }).then(function () { remove([m.id]); }).catch(function (e) { showErr(err, e); });
                } }) : null,
                (isAdmin() && !mine) ? h('button', { type: 'button', text: 'Ban', onclick: function () {
                    if (!window.confirm('Ban @' + m.username + '? They can’t sign in or chat until an admin unbans them.')) return;
                    call('adminBan', { userId: m.userId, banned: true, reason: 'Removed from the chat by an admin' }).then(function () { showErr(err, new Error('@' + m.username + ' is banned.')); }).catch(function (e) { showErr(err, e); });
                } }) : null);
            return h('div', { class: 'acc-msg' + (mine ? ' mine' : '') + (grouped ? ' grouped' : ''), 'data-id': m.id },
                !mine ? (grouped ? h('span', { class: 'acc-av-space' }) : avatar(nameOf(m), m.color, 30, admin)) : null,
                h('div', { class: 'acc-bubble' },
                    (!mine && !grouped) ? h('div', { class: 'acc-from', style: 'color:' + safeColor(m.color) }, nameOf(m), admin ? h('span', { class: 'acc-badge', text: 'ADMIN' }) : null) : null,
                    reply ? h('div', { class: 'acc-quote' }, h('b', { text: nameOf(reply) }), reply.text.slice(0, 120)) : null,
                    h('div', { class: 'acc-text', text: m.text }),
                    h('div', { class: 'acc-time', text: fmtTime(m.created) }),
                    actions));
        }

        function render() {
            var atBottom = feed.scrollHeight - feed.scrollTop - feed.clientHeight < 80;
            while (feed.firstChild) feed.removeChild(feed.firstChild);
            if (!msgs.length) feed.appendChild(h('p', { class: 'acc-muted acc-center', text: 'No messages yet. Say hi!' }));
            var day = null;
            msgs.forEach(function (m, i) {
                var d = new Date(m.created).toDateString();
                if (d !== day) { day = d; feed.appendChild(h('div', { class: 'acc-day', text: dayLabel(m.created) })); }
                feed.appendChild(bubble(m, i > 0 ? msgs[i - 1] : null));
            });
            if (atBottom || !last) feed.scrollTop = feed.scrollHeight;
        }
        function add(list) {
            var changed = false;
            list.forEach(function (m) { if (!byId[m.id]) { byId[m.id] = m; msgs.push(m); changed = true; } });
            if (msgs.length) last = msgs[msgs.length - 1].id;
            if (changed) render();
        }
        function remove(ids) {
            if (!ids || !ids.length) return;
            var gone = {}; ids.forEach(function (i) { gone[i] = 1; });
            var before = msgs.length;
            msgs = msgs.filter(function (m) { if (gone[m.id]) { delete byId[m.id]; return false; } return true; });
            if (msgs.length !== before) render();
        }
        function poll() {
            var f = { room: roomId }; if (last) f.after = last;
            call('chat', f).then(function (r) {
                remove(r.deleted); add(r.messages || []);
                if (!msgs.length) render();
                showErr(err, null);
            }).catch(function (e) { showErr(err, e); })
              .then(function () { if (decodeURIComponent(location.hash.replace('#', '')) === roomId) pollTimer = setTimeout(poll, document.hidden ? 15000 : 3000); });
        }
        call('rooms').then(function (r) {
            var rm = (r.rooms || []).filter(function (x) { return x.id === roomId; })[0];
            if (!rm) return;
            info = rm; title.textContent = (rm.emoji || '💬') + ' ' + rm.name;
            var canPost = !rm.adminOnly || isAdmin();
            composer.hidden = !canPost; readonly.hidden = canPost;
        }).catch(function () {});
        poll();
    }

    // ---- /admin/
    function adminView() {
        if (!token || !user) return needSignIn('Admin', 'Sign in with an admin account.', adminView);
        clear();
        if (!isAdmin()) { root.appendChild(h('div', { class: 'acc-intro' }, h('h1', { class: 'ab-large', text: 'Admins only' }), h('p', { class: 'ab-sub', text: 'Your account isn’t an admin.' }))); return; }
        var err = errBox();
        root.appendChild(h('div', { class: 'acc-intro' }, h('h1', { class: 'ab-large', text: 'Admin' }), h('p', { class: 'ab-sub', text: 'Manage students, the chat and announcements. The apps have the same tools.' })));
        var stats = h('div', { class: 'acc-stats' });
        root.appendChild(stats);
        root.appendChild(err);
        var labels = [['users', 'Students'], ['activeToday', 'Active today'], ['activeWeek', 'Active this week'], ['newWeek', 'New this week'], ['messagesToday', 'Messages today'], ['banned', 'Banned']];
        call('adminStats').then(function (r) {
            labels.forEach(function (l) { stats.appendChild(h('div', { class: 'acc-stat' }, h('b', { text: String((r.stats || {})[l[0]] != null ? r.stats[l[0]] : '–') }), h('small', { text: l[1] }))); });
        }).catch(function (e) { showErr(err, e); });

        var ann = h('textarea', { rows: 3, placeholder: 'Write an announcement for everyone…' });
        var annBtn = h('button', { type: 'submit', class: 'ab-btn', text: 'Send announcement' });
        root.appendChild(h('form', { class: 'acc-card', onsubmit: function (ev) {
            ev.preventDefault(); if (!ann.value.trim()) return;
            busy(annBtn, true, 'Send announcement');
            call('send', { room: 'announcements', text: ann.value.trim() }).then(function () { ann.value = ''; annBtn.textContent = 'Sent ✓'; annBtn.disabled = false; })
                .catch(function (e) { showErr(err, e); busy(annBtn, false, 'Send announcement'); });
        } }, h('h2', { text: '📣 Announcement' }), h('p', { class: 'acc-muted acc-small', text: 'Posted in Announcements; everyone with notifications on gets it.' }), ann, annBtn));

        var q = h('input', { type: 'search', placeholder: 'Search username, name, email or ID' });
        var filter = 'all';
        var chips = h('div', { class: 'acc-seg' }, [['all', 'All'], ['admins', 'Admins'], ['banned', 'Banned']].map(function (f) {
            return h('button', { type: 'button', class: f[0] === 'all' ? 'on' : '', text: f[1], onclick: function (ev) {
                filter = f[0]; Array.prototype.forEach.call(chips.children, function (c) { c.className = ''; }); ev.currentTarget.className = 'on'; draw();
            } });
        }));
        var table = h('div', { class: 'acc-users' });
        var users = [];
        root.appendChild(h('div', { class: 'acc-card' }, h('h2', { text: 'Users' }), q, chips, table));
        function draw() {
            while (table.firstChild) table.removeChild(table.firstChild);
            var shown = users.filter(function (u) { return filter === 'all' || (filter === 'admins' ? u.role === 'admin' : u.banned === true); });
            if (!shown.length) table.appendChild(h('p', { class: 'acc-muted', text: 'No accounts.' }));
            shown.forEach(function (u) { table.appendChild(userRow(u)); });
        }
        function userRow(u) {
            var me = u.id === user.id;
            var detail = h('div', { class: 'acc-user-detail', hidden: true },
                h('p', { class: 'acc-small' }, 'Email: ' + (u.email || '–') + ' · Uni ID: ' + (u.uniId || '–')),
                h('p', { class: 'acc-small' }, 'Joined ' + fmtDate(u.created) + ' · Last seen ' + fmtDate(u.lastSeen)),
                u.banReason ? h('p', { class: 'acc-small', text: 'Ban reason: ' + u.banReason }) : null,
                h('div', { class: 'acc-actions' },
                    !me ? h('button', { type: 'button', class: 'ab-btn gray', text: u.banned ? 'Unban' : 'Ban', onclick: function () {
                        var reason = '';
                        if (!u.banned) { reason = window.prompt('Ban @' + u.username + '? Reason (optional):', ''); if (reason === null) return; }
                        act('adminBan', { userId: u.id, banned: !u.banned, reason: reason });
                    } }) : null,
                    h('button', { type: 'button', class: 'ab-btn gray', text: 'Reset password', onclick: function () {
                        if (!window.confirm('Give @' + u.username + ' a temporary password?')) return;
                        call('adminResetPassword', { userId: u.id }).then(function (r) { window.prompt('Temporary password for @' + u.username + ' (copy it and send it to them):', r.password); })
                            .catch(function (e) { showErr(err, e); });
                    } }),
                    !me ? h('button', { type: 'button', class: 'ab-btn gray', text: u.role === 'admin' ? 'Remove admin' : 'Make admin', onclick: function () {
                        act('adminSetRole', { userId: u.id, role: u.role === 'admin' ? 'user' : 'admin' });
                    } }) : null,
                    !me ? h('button', { type: 'button', class: 'ab-btn gray acc-danger', text: 'Delete', onclick: function () {
                        if (window.confirm('Delete @' + u.username + '? This can’t be undone.')) act('adminDeleteUser', { userId: u.id });
                    } }) : null));
            return h('div', { class: 'acc-user' },
                h('button', { type: 'button', class: 'acc-user-row', onclick: function () { detail.hidden = !detail.hidden; } },
                    avatar(nameOf(u), u.color, 36, u.role === 'admin'),
                    h('span', { class: 'acc-room-t' }, h('b', { text: nameOf(u) }), h('small', { text: '@' + u.username + (u.email ? ' · ' + u.email : '') })),
                    u.banned ? h('em', { class: 'acc-banned', text: 'BANNED' }) : null),
                detail);
        }
        function act(action, f) { call(action, f).then(load).catch(function (e) { showErr(err, e); }); }
        var t = null;
        function load() { return call('adminUsers', { q: q.value.trim() }).then(function (r) { users = r.users || []; draw(); }).catch(function (e) { showErr(err, e); }); }
        q.addEventListener('input', function () { clearTimeout(t); t = setTimeout(load, 400); });
        load();

        // the upload id is the Drive file id in its link
        var link = h('input', { type: 'url', placeholder: 'https://drive.google.com/file/d/…' });
        var delBtn = h('button', { type: 'submit', class: 'ab-btn gray acc-danger', text: 'Delete upload' });
        root.appendChild(h('form', { class: 'acc-card', onsubmit: function (ev) {
            ev.preventDefault();
            var m = link.value.match(/(?:\/d\/|[?&]id=)([-\w]{20,})/);
            if (!m) { showErr(err, new Error('Paste a Google Drive file link.')); return; }
            if (!window.confirm('Move this upload to the Drive trash?')) return;
            busy(delBtn, true, 'Delete upload');
            call('adminDeleteUpload', { fileId: m[1] }).then(function () { link.value = ''; delBtn.textContent = 'Deleted ✓'; delBtn.disabled = false; })
                .catch(function (e) { showErr(err, e); busy(delBtn, false, 'Delete upload'); });
        } }, h('h2', { text: 'Delete an upload' }), h('p', { class: 'acc-muted acc-small', text: 'Only files in the ASU Bank upload folder can be deleted. They go to the Drive trash.' }), link, delBtn));

        var logBox = h('div', { class: 'acc-log' }, h('p', { class: 'acc-muted', text: 'Loading…' }));
        root.appendChild(h('div', { class: 'acc-card' }, h('h2', { text: 'Admin activity' }), logBox));
        call('adminLog').then(function (r) {
            while (logBox.firstChild) logBox.removeChild(logBox.firstChild);
            var log = r.log || [];
            if (!log.length) logBox.appendChild(h('p', { class: 'acc-muted', text: 'Nothing yet.' }));
            log.slice(0, 100).forEach(function (l) {
                logBox.appendChild(h('p', { class: 'acc-small' }, h('b', { text: (l.action || '') + ' · ' + (l.target || '') }), ' ' + (l.detail || '') + ' — ' + fmtDate(l.time)));
            });
        }).catch(function (e) { logBox.textContent = e.message; });
    }

    var views = { account: accountView, chat: chatView, admin: adminView };
    (views[view] || accountView)();
    if (view === 'chat') window.addEventListener('hashchange', chatView);
})();
