/* ASU Bank accounts, chat and admin on the website.
   Same "ASU Bank Accounts" Apps Script as the iOS and Android apps (tools/apps-script/accounts-api.gs),
   so one account works everywhere. Pages: /account/, /chat/, /feed/, /admin/ (each has <div id="acc-app" data-view="...">). */
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
    function isBlocked(id) { return !!(user && user.blocked && user.blocked.indexOf(id) >= 0); }
    var REPORT_REASONS = [['spam', 'Spam or advertising'], ['harassment', 'Harassment or bullying'], ['hate', 'Hate speech'], ['sexual', 'Sexual content'],
        ['violence', 'Violence or threats'], ['privacy', "Shares someone's private information"], ['copyright', 'Copyright: I own this or represent the owner'],
        ['wrong', 'Wrong, broken or misleading file'], ['other', 'Something else']];
    function reportSheet(kind, targetId, name) {
        var s = sheet('Report');
        var pick = h('select', { 'aria-label': "What's wrong?" }, h('option', { value: '', text: "What's wrong?" }));
        REPORT_REASONS.forEach(function (r) { pick.appendChild(h('option', { value: r[0], text: r[1] })); });
        var details = h('textarea', { rows: '3', maxlength: '1000', placeholder: 'Details (optional)' });
        var err = h('p', { class: 'acc-err', role: 'alert' });
        var send = h('button', { type: 'submit', class: 'ab-btn', text: 'Send report' });
        s.box.appendChild(h('form', { class: 'acc-field', onsubmit: function (ev) {
            ev.preventDefault();
            if (!pick.value) { err.textContent = "Choose what's wrong first."; return; }
            send.disabled = true;
            call('report', { kind: kind, targetId: targetId, targetName: name, reason: pick.value, details: details.value }).then(function () {
                while (s.box.lastChild && s.box.children.length > 1) s.box.removeChild(s.box.lastChild);
                s.box.appendChild(h('p', { text: 'Thank you. Our team will review your report.' }));
            }).catch(function (e) { send.disabled = false; err.textContent = e.message; });
        } }, name ? h('p', { class: 'acc-muted acc-small', text: name }) : null, pick, details, err, send));
    }
    function blockPerson(id, name, done) {
        if (!window.confirm('Block ' + name + "? You won't see their messages. You can undo this from their profile.")) return;
        call('block', { userId: id, on: true }).then(function (r) { user.blocked = r.blocked; if (done) done(); }).catch(function (e) { window.alert(e.message); });
    }
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
    function safePhoto(u) { return /^https:\/\/drive\.google\.com\/thumbnail\?/.test(u || '') ? u : ''; }
    // photo (when set) over the coloured letter; online adds a green dot, false a grey one, null none
    function avatar(n, color, size, admin, photo, online) {
        size = size || 40;
        var el = h('span', { class: 'acc-av' + (admin ? ' admin' : ''), style: 'width:' + size + 'px;height:' + size + 'px;font-size:' + Math.round(size * .42) + 'px;background:' + safeColor(color), 'aria-hidden': 'true' }, (n || '?').charAt(0).toUpperCase());
        photo = safePhoto(photo);
        if (photo) el.appendChild(h('img', { src: photo, alt: '', loading: 'lazy', referrerpolicy: 'no-referrer', onerror: function (e) { e.currentTarget.remove(); } }));
        if (online != null) el.appendChild(h('i', { class: 'acc-dot' + (online ? ' on' : '') }));
        return el;
    }
    var BADGES = { founder: ['👑', 'Founder'], moderator: ['🛡️', 'Moderator'], support: ['🛟', 'Support'], helper: ['🤝', 'Helper'], donor: ['💛', 'Donor'],
        contributor: ['📚', 'Contributor'], tutor: ['🎓', 'Tutor'], verified: ['✅', 'Verified'], vip: ['⭐', 'VIP'], developer: ['💻', 'Developer'],
        team: ['🏦', 'ASU Bank team'], mentor: ['🧭', 'Mentor'], ambassador: ['📣', 'Ambassador'], top_student: ['🏅', 'Top student'], class_rep: ['🙋', 'Class rep'],
        translator: ['🌐', 'Translator'], designer: ['🎨', 'Designer'], researcher: ['🔬', 'Researcher'], organizer: ['🎉', 'Organizer'],
        early_supporter: ['🌱', 'Early supporter'], sponsor: ['💎', 'Sponsor'], legend: ['🔥', 'Legend'] };
    var COLLEGES = ['Information Technology', 'Engineering', 'Pharmacy', 'Dentistry', 'Nursing', 'Allied Medical Sciences', 'Business', 'Law', 'Arts & Sciences', 'Arts & Design', 'Other'];
    function badgeRow(list, compact) {
        list = (list || []).filter(function (b) { return BADGES[b]; });
        if (!list.length) return null;
        return h('span', { class: 'acc-badges' + (compact ? ' compact' : '') }, list.map(function (b) {
            return h('span', { class: 'acc-pill', title: BADGES[b][1], text: compact ? BADGES[b][0] : BADGES[b][0] + ' ' + BADGES[b][1] });
        }));
    }
    function lastSeenText(iso, online) {
        if (online) return 'Online';
        var d = new Date(iso); if (!iso || isNaN(d)) return 'Offline';
        var m = Math.round((Date.now() - d) / 6e4);
        return 'Last seen ' + (m < 60 ? Math.max(1, m) + ' min ago' : m < 1440 ? Math.round(m / 60) + ' h ago' : d.toLocaleDateString([], { dateStyle: 'medium' }));
    }
    // a simple dialog layer; returns {box, close}
    function sheet(title) {
        var back = h('div', { class: 'acc-modal', role: 'dialog', 'aria-modal': 'true', 'aria-label': title });
        var box = h('div', { class: 'acc-card acc-modal-box' });
        function close() { back.remove(); document.removeEventListener('keydown', esc); }
        function esc(e) { if (e.key === 'Escape') close(); }
        back.addEventListener('click', function (e) { if (e.target === back) close(); });
        document.addEventListener('keydown', esc);
        box.appendChild(h('div', { class: 'acc-modal-head' }, h('h2', { text: title }), h('button', { type: 'button', class: 'acc-x', 'aria-label': 'Close', text: '×', onclick: close })));
        back.appendChild(box); document.body.appendChild(back);
        return { box: box, close: close };
    }
    function showProfile(userId, fallback) {
        var s = sheet('Profile');
        var body = h('div', { class: 'acc-profile' }, h('p', { class: 'acc-muted', text: 'Loading…' }));
        s.box.appendChild(body);
        var signedIn = !!(token && user);
        call(signedIn ? 'profile' : 'publicProfile', { userId: userId }).then(function (r) {
            var u = r.user; while (body.firstChild) body.removeChild(body.firstChild);
            var cover = h('div', { class: 'acc-cover', style: 'background:' + safeColor(u.color) });
            if (safePhoto(u.cover)) cover.appendChild(h('img', { src: safePhoto(u.cover), alt: '', referrerpolicy: 'no-referrer', onerror: function (e) { e.currentTarget.remove(); } }));
            body.appendChild(cover);
            body.appendChild(h('div', { class: 'acc-cover-av' }, avatar(nameOf(u), u.color, 96, u.role === 'admin', u.avatar, signedIn ? !!u.online : null)));
            body.appendChild(h('h2', { text: nameOf(u) }));
            body.appendChild(h('p', { class: 'acc-muted', text: '@' + u.username + (u.role === 'admin' ? ' · Admin' : '') + (signedIn ? ' · ' + lastSeenText(u.lastSeen, u.online) : '') }));
            if (signedIn) body.appendChild(h('div', { class: 'acc-stats' }, [['Friends', r.friendCount], ['Posts', r.postCount], ['Messages', r.messageCount]].map(function (s) {
                return h('span', null, h('b', { text: String(s[1] || 0) }), h('small', { text: s[0] }));
            })));
            var b = badgeRow(u.badges); if (b) body.appendChild(b);
            if (u.bio) body.appendChild(h('p', { class: 'acc-bio', text: u.bio }));
            if (u.college || u.major) body.appendChild(h('p', { class: 'acc-muted', text: '🎓 ' + [u.college, u.major].filter(Boolean).join(' · ') }));
            if (u.phone) {
                var digits = u.phone.replace(/[^0-9+]/g, '');
                body.appendChild(h('div', { class: 'acc-actions acc-center-row' },
                    h('a', { class: 'ab-btn tinted', href: 'tel:' + digits, text: '📞 ' + u.phone }),
                    h('a', { class: 'ab-btn tinted', href: 'https://wa.me/' + digits.replace('+', ''), target: '_blank', rel: 'noopener', text: 'WhatsApp' })));
            }
            if (signedIn && u.id !== user.id) body.appendChild(h('div', { class: 'acc-actions acc-center-row' },
                h('button', { type: 'button', class: 'ab-btn gray', text: 'Report', onclick: function () { reportSheet('user', u.id, nameOf(u)); } }),
                u.role === 'admin' ? null : h('button', { type: 'button', class: 'ab-btn gray', text: isBlocked(u.id) ? 'Unblock' : 'Block', onclick: function () {
                    if (isBlocked(u.id)) call('block', { userId: u.id, on: false }).then(function (r) { user.blocked = r.blocked; s.close(); });
                    else blockPerson(u.id, nameOf(u), function () { s.close(); });
                } })));
            if (signedIn && isAdmin() && u.id !== user.id) body.appendChild(h('p', null, h('a', { href: '/admin/#' + encodeURIComponent(u.username), text: 'Manage this user in Admin ›' })));
            if (!signedIn) body.appendChild(h('p', { class: 'acc-muted acc-small' }, h('a', { href: '/account/', text: 'Sign in' }), ' to see the full profile.'));
            if (r.posts && r.posts.length) {
                body.appendChild(h('h3', { class: 'acc-left', text: signedIn ? 'Posts' : 'Public posts' }));
                r.posts.forEach(function (p) { p.user = p.user || u; body.appendChild(postCard(p, true)); });
            }
        }).catch(function (e) { body.textContent = (fallback ? fallback + ': ' : '') + e.message; });
    }
    // shrinks a picked photo to a 256 px square JPEG (base64, no data: prefix)
    function photoData(file) {
        return new Promise(function (ok, bad) {
            var img = new Image(), url = URL.createObjectURL(file);
            img.onload = function () {
                var c = document.createElement('canvas'); c.width = c.height = 256;
                var s = Math.min(img.width, img.height);
                c.getContext('2d').drawImage(img, (img.width - s) / 2, (img.height - s) / 2, s, s, 0, 0, 256, 256);
                URL.revokeObjectURL(url);
                ok(c.toDataURL('image/jpeg', .82).split(',')[1]);
            };
            img.onerror = function () { URL.revokeObjectURL(url); bad(new Error("That file isn't a photo we can read.")); };
            img.src = url;
        });
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
                p.then(function (r) {
                    adopt(r); after();
                    if (r.earlySupporter) {
                        var thanks = sheet('Thank you for joining early! 🌱');
                        thanks.box.appendChild(h('p', { text: "You joined ASU Bank in its early days, so your profile now has the Early supporter badge. Thank you for helping students at ASU." }));
                    }
                })
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
        var pick = h('input', { type: 'file', accept: 'image/*', hidden: true, onchange: function () {
            var f = pick.files && pick.files[0]; if (!f) return;
            photoBtn.disabled = true; photoBtn.textContent = 'Uploading…';
            photoData(f).then(function (d) { return call('setAvatar', { data: d, type: 'image/jpeg' }); })
                .then(function (r) { adopt(r); accountView(); })
                .catch(function (e) { showErr(err, e); photoBtn.disabled = false; photoBtn.textContent = 'Change photo'; });
        } });
        var photoBtn = h('button', { type: 'button', class: 'ab-btn tinted', text: u.avatar ? 'Change photo' : 'Add a photo', onclick: function () { pick.click(); } });
        root.appendChild(h('div', { class: 'acc-profile' }, avatar(nameOf(u), u.color, 96, isAdmin(), u.avatar, true),
            h('h1', { text: nameOf(u) }), h('p', { class: 'acc-muted', text: '@' + u.username + (isAdmin() ? ' · Admin' : '') }),
            badgeRow(u.badges), u.bio ? h('p', { class: 'acc-bio', text: u.bio }) : null,
            h('div', { class: 'acc-actions acc-center-row' }, photoBtn, pick,
                u.avatar ? h('button', { type: 'button', class: 'ab-btn gray', text: 'Remove photo', onclick: function () {
                    call('setAvatar', { remove: true }).then(function (r) { adopt(r); accountView(); }).catch(function (e) { showErr(err, e); });
                } }) : null)));
        var dn = field('Display name', 'text', u.displayName), em = field('University email (optional)', 'email', u.email), uid = field('University ID (optional)', 'text', u.uniId);
        var major = field('Your major', 'text', u.major);
        var college = h('select', null, h('option', { value: '', text: 'Not set' }), COLLEGES.map(function (c) { return h('option', { value: c, text: c }); }));
        college.value = u.college || '';
        var ph = field('Phone number (optional)', 'tel', u.phone, 'Like +962 79 123 4567');
        var showPh = h('input', { type: 'checkbox' }); showPh.checked = !!u.showPhone;
        var bio = h('textarea', { rows: 2, maxlength: 160, placeholder: 'A line about you: major, year, what you can help with' }); bio.value = u.bio || '';
        var save = h('button', { type: 'submit', class: 'ab-btn', text: 'Save profile' });
        root.appendChild(h('form', { class: 'acc-card', onsubmit: function (ev) {
            ev.preventDefault(); busy(save, true, 'Save profile');
            call('updateProfile', { displayName: dn.input.value.trim(), email: em.input.value.trim(), uniId: uid.input.value.trim(),
                phone: ph.input.value.trim(), showPhone: showPh.checked, bio: bio.value.trim(), major: major.input.value.trim(), college: college.value })
                .then(function (r) { adopt(r); showErr(err, null); save.textContent = 'Saved ✓'; save.disabled = false; })
                .catch(function (e) { showErr(err, e); busy(save, false, 'Save profile'); });
        } }, h('h2', { text: 'Profile' }), dn.el, h('label', { class: 'acc-field' }, h('span', { text: 'About you' }), bio), h('label', { class: 'acc-field' }, h('span', { text: 'College' }), college), major.el, em.el, uid.el, ph.el,
            h('label', { class: 'acc-check' }, showPh, ' Show my phone number on my profile'), save));

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
        var rulesOk = false;
        try { rulesOk = localStorage.getItem('ab.chat.rulesAccepted') === '1'; } catch (e) {}
        if (!rulesOk) {
            clear();
            root.appendChild(h('div', { class: 'acc-card' }, h('h2', { text: 'Community rules' }),
                h('p', { text: 'Be respectful. No harassment, threats, hate speech, sexual or violent content, spam or other people’s private information. We have zero tolerance for abuse: admins can remove content and ban accounts.' }),
                h('p', null, h('a', { href: '/legal/community', text: 'Read the full rules' })),
                h('button', { type: 'button', class: 'ab-btn', text: 'I agree', onclick: function () {
                    try { localStorage.setItem('ab.chat.rulesAccepted', '1'); } catch (e) {}
                    chatView();
                } })));
            return;
        }
        var room = decodeURIComponent((location.hash || '').replace('#', ''));
        if (room) return roomView(room);
        clear();
        root.appendChild(h('div', { class: 'acc-intro' }, h('h1', { class: 'ab-large', text: 'Chat' }),
            h('p', { class: 'ab-sub', text: 'Signed in as @' + user.username + '. Be kind and helpful; admins can remove messages and ban accounts.' })));
        var strip = h('div', { class: 'acc-online' });
        root.appendChild(strip);
        call('online').then(function (r) {
            var on = (r.users || []).filter(function (x) { return x.online; });
            strip.appendChild(h('button', { type: 'button', class: 'acc-online-n', onclick: showOnline }, h('i', { class: 'acc-dot on' }), ' ' + (r.onlineCount || 0) + ' online'));
            on.slice(0, 12).forEach(function (x) {
                strip.appendChild(h('button', { type: 'button', class: 'acc-online-u', title: x.displayName, onclick: function () { showProfile(x.id); } },
                    avatar(x.displayName, x.color, 34, x.role === 'admin', x.avatar, true)));
            });
        }).catch(function () {});
        var list = h('div', { class: 'acc-card acc-rooms' }, h('p', { class: 'acc-muted', text: 'Loading rooms…' }));
        root.appendChild(list);
        function drawRooms(rooms) {
            while (list.firstChild) list.removeChild(list.firstChild);
            rooms.forEach(function (rm) {
                list.appendChild(h('div', { class: 'acc-room-wrap' },
                    h('a', { class: 'acc-room' + (rm.archived ? ' archived' : ''), href: '#' + encodeURIComponent(rm.id) },
                        h('span', { class: 'acc-emoji', text: rm.emoji || '💬' }),
                        h('span', { class: 'acc-room-t' }, h('b', { text: (rm.locked ? '🔒 ' : '') + rm.name }), h('small', { text: rm.about || '' })),
                        rm.archived ? h('em', { text: 'Archived' }) : rm.locked ? h('em', { text: 'Locked' }) : rm.adminOnly ? h('em', { text: 'Admins post' }) : null),
                    isAdmin() ? h('button', { type: 'button', class: 'acc-room-edit', 'aria-label': 'Room settings', title: 'Room settings', text: '⚙︎', onclick: function () { roomEditor(rm, drawRooms); } }) : null));
            });
            if (isAdmin()) list.appendChild(h('button', { type: 'button', class: 'acc-room acc-room-add', onclick: function () { roomEditor(null, drawRooms); } },
                h('span', { class: 'acc-emoji', text: '＋' }), h('span', { class: 'acc-room-t' }, h('b', { text: 'New room' }), h('small', { text: 'Admins only' }))));
        }
        call('rooms').then(function (r) { drawRooms(r.rooms || []); }).catch(function (e) { list.textContent = e.message; });
    }

    function showOnline() {
        var s = sheet('Who’s online');
        var box = h('div', { class: 'acc-users' }, h('p', { class: 'acc-muted', text: 'Loading…' }));
        s.box.appendChild(box);
        call('online').then(function (r) {
            while (box.firstChild) box.removeChild(box.firstChild);
            var users = r.users || [];
            if (!users.length) box.appendChild(h('p', { class: 'acc-muted', text: 'Nobody has been around lately.' }));
            users.forEach(function (x) {
                box.appendChild(h('button', { type: 'button', class: 'acc-user-row', onclick: function () { s.close(); showProfile(x.id); } },
                    avatar(x.displayName, x.color, 36, x.role === 'admin', x.avatar, !!x.online),
                    h('span', { class: 'acc-room-t' }, h('b', null, x.displayName, ' ', badgeRow(x.badges, true)), h('small', { text: lastSeenText(x.lastSeen, x.online) }))));
            });
        }).catch(function (e) { box.textContent = e.message; });
    }

    // admins: add or change a room; done(rooms) gets the new list
    function roomEditor(rm, done) {
        var s = sheet(rm ? 'Room settings' : 'New room');
        var err = errBox();
        var name = field('Name', 'text', rm && rm.name), about = field('About (optional)', 'text', rm && rm.about);
        var emoji = field('Emoji', 'text', (rm && rm.emoji) || '💬');
        var emojis = h('div', { class: 'acc-emojis' }, '💬📚🧪🧮💻🩺⚖️🎨🏗️📐🎓📣🎮⚽🎵☕🆘🤝'.match(/\p{Extended_Pictographic}️?/gu).map(function (e) {
            return h('button', { type: 'button', text: e, onclick: function () { emoji.input.value = e; } });
        }));
        function check(label, on, hint) {
            var c = h('input', { type: 'checkbox' }); c.checked = !!on;
            return { el: h('label', { class: 'acc-check' }, c, ' ' + label, hint ? h('small', { text: hint }) : null), input: c };
        }
        var ao = check('Only admins post', rm && rm.adminOnly), lk = check('Locked', rm && rm.locked, 'Everyone can read; nobody can post.'),
            ar = check('Archived', rm && rm.archived, 'Hidden from students. Admins still see it.');
        var order = field('Position in the list (0 = top)', 'number', rm ? String(rm.order || 0) : '');
        var save = h('button', { type: 'submit', class: 'ab-btn', text: rm ? 'Save' : 'Create room' });
        function finish(r) { s.close(); if (done) done(r.rooms || []); }
        s.box.appendChild(h('form', { onsubmit: function (ev) {
            ev.preventDefault(); busy(save, true, save.textContent);
            var f = { name: name.input.value.trim(), about: about.input.value.trim(), emoji: emoji.input.value.trim(),
                adminOnly: ao.input.checked, locked: lk.input.checked, archived: ar.input.checked };
            if (rm) f.id = rm.id;
            if (order.input.value !== '') f.order = Number(order.input.value);
            call('adminRoomSave', f).then(finish).catch(function (e) { showErr(err, e); busy(save, false, rm ? 'Save' : 'Create room'); });
        } }, name.el, about.el, emoji.el, emojis, ao.el, lk.el, ar.el, order.el, err, save));
        if (rm) s.box.appendChild(h('div', { class: 'acc-actions acc-sep' },
            h('button', { type: 'button', class: 'ab-btn gray', text: 'Clear all messages', onclick: function () {
                if (!window.confirm('Remove every message in ' + rm.name + '? This can’t be undone.')) return;
                call('adminClearRoom', { room: rm.id }).then(function (r) { showErr(err, new Error(r.removed + ' messages removed.')); }).catch(function (e) { showErr(err, e); });
            } }),
            rm.id !== 'announcements' ? h('button', { type: 'button', class: 'ab-btn gray acc-danger', text: 'Delete room', onclick: function () {
                if (!window.confirm('Delete ' + rm.name + ' and hide its messages?')) return;
                call('adminRoomDelete', { id: rm.id }).then(finish).catch(function (e) { showErr(err, e); });
            } }) : null));
    }

    function roomView(roomId) {
        clear();
        var info = { id: roomId, name: roomId, emoji: '💬' };
        var msgs = [], byId = {}, people = {}, replyTo = null, editing = null, pinned = null, last = null;
        var title = h('h1', { class: 'acc-room-title', text: '💬 ' + roomId });
        var pinBar = h('div', { class: 'acc-pinned', hidden: true });
        var feed = h('div', { class: 'acc-feed', 'aria-live': 'polite' });
        var err = errBox();
        var input = h('textarea', { rows: 1, maxlength: 1000, placeholder: 'Message' });
        var replyBar = h('div', { class: 'acc-reply', hidden: true });
        var send = h('button', { type: 'submit', class: 'ab-btn', text: 'Send' });
        var composer = h('form', { class: 'acc-composer', onsubmit: function (ev) {
            ev.preventDefault();
            var t = input.value.trim(); if (!t) return;
            var label = editing ? 'Save' : 'Send';
            busy(send, true, label);
            var p = editing
                ? call('editMessage', { id: editing.id, text: t }).then(function () { var m = editing; m.text = t; m.edited = new Date().toISOString(); setReply(null); render(); })
                : call('send', { room: roomId, text: t, replyTo: replyTo ? replyTo.id : '' }).then(function (r) { setReply(null); if (r.message) add([r.message]); });
            p.then(function () { input.value = ''; showErr(err, null); })
                .catch(function (e) { showErr(err, e); })
                .then(function () { busy(send, false, editing ? 'Save' : 'Send'); input.focus(); });
        } }, replyBar, h('div', { class: 'acc-compose-row' }, input, send));
        input.addEventListener('keydown', function (e) {
            if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); composer.requestSubmit ? composer.requestSubmit() : send.click(); }
            if (e.key === 'Escape' && (editing || replyTo)) { setReply(null); input.value = ''; }
        });
        var readonly = h('p', { class: 'acc-muted acc-small acc-center', hidden: true, text: 'Only admins post here.' });
        var settings = h('button', { type: 'button', class: 'acc-room-edit', hidden: true, 'aria-label': 'Room settings', title: 'Room settings', text: '⚙︎', onclick: function () {
            roomEditor(info, function (rooms) {
                var rm = rooms.filter(function (x) { return x.id === roomId; })[0];
                if (rm) setInfo(rm); else location.hash = '';
            });
        } });
        root.appendChild(h('div', { class: 'acc-room-head' }, h('a', { href: '#', class: 'acc-back', text: '‹ Rooms' }), title, settings));
        root.appendChild(pinBar);
        root.appendChild(feed);
        root.appendChild(err);
        root.appendChild(composer);
        root.appendChild(readonly);

        function setInfo(rm) {
            info = rm; title.textContent = (rm.emoji || '💬') + ' ' + (rm.locked ? '🔒 ' : '') + rm.name;
            var canPost = isAdmin() || (!rm.adminOnly && !rm.locked && !rm.archived);
            composer.hidden = !canPost; readonly.hidden = canPost;
            readonly.textContent = rm.archived ? 'This room is archived.' : rm.locked ? 'This room is locked. You can still read it.' : 'Only admins post here.';
            settings.hidden = !isAdmin();
        }
        // replying (m) or editing (m, true); null clears both
        function setReply(m, edit) {
            replyTo = edit ? null : m; editing = edit ? m : null;
            while (replyBar.firstChild) replyBar.removeChild(replyBar.firstChild);
            replyBar.hidden = !m;
            send.textContent = editing ? 'Save' : 'Send';
            if (m) replyBar.appendChild(h('span', null, h('b', { text: (edit ? 'Editing your message' : 'Replying to ' + nameOf(m)) + ': ' }), m.text.slice(0, 80)),
                h('button', { type: 'button', class: 'acc-x', 'aria-label': 'Cancel', text: '×', onclick: function () { if (editing) input.value = ''; setReply(null); } }));
        }
        function drawPin() {
            while (pinBar.firstChild) pinBar.removeChild(pinBar.firstChild);
            pinBar.hidden = !pinned;
            if (!pinned) return;
            pinBar.appendChild(h('button', { type: 'button', class: 'acc-pinned-t', onclick: function () {
                var el = feed.querySelector('[data-id="' + pinned.id + '"]'); if (el) el.scrollIntoView({ block: 'center', behavior: 'smooth' });
            } }, h('b', { text: '📌 ' + nameOf(pinned) + ': ' }), pinned.text.slice(0, 140)));
            if (isAdmin()) pinBar.appendChild(h('button', { type: 'button', class: 'acc-x', 'aria-label': 'Unpin', title: 'Unpin', text: '×', onclick: function () { pin(null); } }));
        }
        function pin(m) {
            call('adminPin', { room: roomId, id: m ? m.id : '' }).then(function () { pinned = m; drawPin(); render(); }).catch(function (e) { showErr(err, e); });
        }

        function bubble(m, prev) {
            var mine = m.userId === user.id, admin = m.role === 'admin';
            var p = people[m.userId] || {};
            var grouped = prev && prev.userId === m.userId && new Date(prev.created).toDateString() === new Date(m.created).toDateString();
            var reply = m.replyTo && byId[m.replyTo];
            var who = function () { showProfile(m.userId, nameOf(m)); };
            var actions = h('span', { class: 'acc-msg-actions' },
                h('button', { type: 'button', text: 'Reply', onclick: function () { setReply(m); input.focus(); } }),
                mine ? h('button', { type: 'button', text: 'Edit', onclick: function () { setReply(m, true); input.value = m.text; input.focus(); } }) : null,
                isAdmin() ? h('button', { type: 'button', text: pinned && pinned.id === m.id ? 'Unpin' : 'Pin', onclick: function () { pin(pinned && pinned.id === m.id ? null : m); } }) : null,
                !mine ? h('button', { type: 'button', text: 'Report', onclick: function () { reportSheet('message', m.id, m.text.slice(0, 120)); } }) : null,
                (!mine && m.role !== 'admin') ? h('button', { type: 'button', text: 'Block', onclick: function () { blockPerson(m.userId, nameOf(m), function () { location.reload(); }); } }) : null,
                (mine || isAdmin()) ? h('button', { type: 'button', text: 'Delete', onclick: function () {
                    if (!window.confirm('Delete this message?')) return;
                    call('deleteMessage', { id: m.id }).then(function () { remove([m.id]); }).catch(function (e) { showErr(err, e); });
                } }) : null,
                (isAdmin() && !mine) ? h('button', { type: 'button', text: 'Ban', onclick: function () {
                    if (!window.confirm('Ban @' + m.username + '? They can’t sign in or chat until an admin unbans them.')) return;
                    call('adminBan', { userId: m.userId, banned: true, reason: 'Removed from the chat by an admin' }).then(function () { showErr(err, new Error('@' + m.username + ' is banned.')); }).catch(function (e) { showErr(err, e); });
                } }) : null);
            var av = !mine ? (grouped ? h('span', { class: 'acc-av-space' }) : h('button', { type: 'button', class: 'acc-av-btn', 'aria-label': 'Profile of ' + nameOf(m), onclick: who },
                avatar(nameOf(m), m.color, 30, admin, p.avatar, p.online ? true : null))) : null;
            return h('div', { class: 'acc-msg' + (mine ? ' mine' : '') + (grouped ? ' grouped' : ''), 'data-id': m.id },
                av,
                h('div', { class: 'acc-bubble' },
                    (!mine && !grouped) ? h('button', { type: 'button', class: 'acc-from', style: 'color:' + safeColor(m.color), onclick: who },
                        nameOf(m), admin ? h('span', { class: 'acc-badge', text: 'ADMIN' }) : null, badgeRow(p.badges, true)) : null,
                    reply ? h('div', { class: 'acc-quote' }, h('b', { text: nameOf(reply) }), reply.text.slice(0, 120)) : null,
                    h('div', { class: 'acc-text', text: m.text }),
                    h('div', { class: 'acc-time', text: (pinned && pinned.id === m.id ? '📌 ' : '') + (m.edited ? 'edited · ' : '') + fmtTime(m.created) }),
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
            list.filter(function (m) { return !isBlocked(m.userId); }).forEach(function (m) { if (!byId[m.id]) { byId[m.id] = m; msgs.push(m); changed = true; } });
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
        // returns true when something changed
        function applyEdits(list) {
            var changed = false;
            (list || []).forEach(function (e) { var m = byId[e.id]; if (m && m.edited !== e.edited) { m.text = e.text; m.edited = e.edited; changed = true; } });
            return changed;
        }
        function poll() {
            var f = { room: roomId }; if (last) f.after = last;
            call('chat', f).then(function (r) {
                if (r.room) setInfo(r.room);
                var changed = false;
                if (r.people) { if (JSON.stringify(r.people) !== JSON.stringify(people)) changed = true; people = r.people; }
                var pid = r.pinned ? r.pinned.id : null;
                if (pid !== (pinned && pinned.id) || (pinned && r.pinned && pinned.text !== r.pinned.text)) { pinned = r.pinned || null; drawPin(); changed = true; }
                remove(r.deleted);
                var n = msgs.length;
                add(r.messages || []);
                if (applyEdits(r.edited)) changed = true;
                if (changed && msgs.length === n) render();
                if (!msgs.length) render();
                showErr(err, null);
            }).catch(function (e) { showErr(err, e); })
              .then(function () { if (decodeURIComponent(location.hash.replace('#', '')) === roomId) pollTimer = setTimeout(poll, document.hidden ? 15000 : 3000); });
        }
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
        var labels = [['users', 'Students'], ['online', 'Online now'], ['activeToday', 'Active today'], ['activeWeek', 'Active this week'], ['newWeek', 'New this week'], ['messagesToday', 'Messages today'], ['rooms', 'Chat rooms'], ['banned', 'Banned']];
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

        var roomBox = h('div', { class: 'acc-rooms' }, h('p', { class: 'acc-muted', text: 'Loading…' }));
        function drawRooms(rooms) {
            while (roomBox.firstChild) roomBox.removeChild(roomBox.firstChild);
            rooms.forEach(function (rm) {
                roomBox.appendChild(h('button', { type: 'button', class: 'acc-room' + (rm.archived ? ' archived' : ''), onclick: function () { roomEditor(rm, drawRooms); } },
                    h('span', { class: 'acc-emoji', text: rm.emoji || '💬' }),
                    h('span', { class: 'acc-room-t' }, h('b', { text: rm.name }), h('small', { text: rm.about || '' })),
                    rm.archived ? h('em', { text: 'Archived' }) : rm.locked ? h('em', { text: 'Locked' }) : rm.adminOnly ? h('em', { text: 'Admins post' }) : null));
            });
        }
        root.appendChild(h('div', { class: 'acc-card' }, h('h2', { text: '💬 Chat rooms' }),
            h('p', { class: 'acc-muted acc-small', text: 'Add, rename, lock, archive or delete rooms. Locked rooms can be read but not posted in; archived rooms are hidden from students.' }),
            roomBox, h('button', { type: 'button', class: 'ab-btn tinted', text: '＋ New room', onclick: function () { roomEditor(null, drawRooms); } })));
        call('rooms').then(function (r) { drawRooms(r.rooms || []); }).catch(function (e) { roomBox.textContent = e.message; });

        var q = h('input', { type: 'search', placeholder: 'Search username, name, email, ID or phone' });
        q.value = decodeURIComponent((location.hash || '').replace('#', ''));
        var filter = 'all';
        var chips = h('div', { class: 'acc-seg' }, [['all', 'All'], ['admins', 'Admins'], ['banned', 'Banned']].map(function (f) {
            return h('button', { type: 'button', class: f[0] === 'all' ? 'on' : '', text: f[1], onclick: function (ev) {
                filter = f[0]; Array.prototype.forEach.call(chips.children, function (c) { c.className = ''; }); ev.currentTarget.className = 'on'; draw();
            } });
        }));
        var table = h('div', { class: 'acc-users' });
        var users = [], open = {};
        root.appendChild(h('div', { class: 'acc-card' }, h('h2', { text: 'Users' }), q, chips, table));
        function draw() {
            while (table.firstChild) table.removeChild(table.firstChild);
            var shown = users.filter(function (u) { return filter === 'all' || (filter === 'admins' ? u.role === 'admin' : u.banned === true); });
            if (!shown.length) table.appendChild(h('p', { class: 'acc-muted', text: 'No accounts.' }));
            shown.forEach(function (u) { table.appendChild(userRow(u)); });
        }
        function userRow(u) {
            var me = u.id === user.id;
            var detail = h('div', { class: 'acc-user-detail', hidden: !open[u.id] },
                h('p', { class: 'acc-small' }, 'Email: ' + (u.email || '–') + ' · Uni ID: ' + (u.uniId || '–')),
                h('p', { class: 'acc-small' }, 'Phone: ' + (u.phone || '–') + (u.phone ? (u.showPhone ? ' (shown on profile)' : ' (private)') : '')),
                h('p', { class: 'acc-small' }, 'Joined ' + fmtDate(u.created) + ' · ' + (u.online ? 'Online now' : 'Last seen ' + fmtDate(u.lastSeen))),
                u.bio ? h('p', { class: 'acc-small', text: 'About: ' + u.bio }) : null,
                (u.college || u.major) ? h('p', { class: 'acc-small', text: 'Studies: ' + [u.college, u.major].filter(Boolean).join(' · ') }) : null,
                u.banReason ? h('p', { class: 'acc-small', text: 'Ban reason: ' + u.banReason }) : null,
                h('p', { class: 'acc-small' }, h('b', { text: 'Badges' })),
                h('div', { class: 'acc-badge-pick' }, Object.keys(BADGES).map(function (k) {
                    var on = (u.badges || []).indexOf(k) >= 0;
                    return h('button', { type: 'button', class: 'acc-pill' + (on ? ' on' : ''), 'aria-pressed': String(on), text: BADGES[k][0] + ' ' + BADGES[k][1], onclick: function () {
                        var list = (u.badges || []).filter(function (x) { return x !== k; });
                        if (!on) list.push(k);
                        call('adminSetBadges', { userId: u.id, badges: list }).then(function (r) { u.badges = r.badges; open[u.id] = 1; draw(); }).catch(function (e) { showErr(err, e); });
                    } });
                })),
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
                h('button', { type: 'button', class: 'acc-user-row', onclick: function () { detail.hidden = !detail.hidden; if (detail.hidden) delete open[u.id]; else open[u.id] = 1; } },
                    avatar(nameOf(u), u.color, 36, u.role === 'admin', u.avatar, !!u.online),
                    h('span', { class: 'acc-room-t' }, h('b', null, nameOf(u), ' ', badgeRow(u.badges, true)), h('small', { text: '@' + u.username + (u.email ? ' · ' + u.email : '') })),
                    u.banned ? h('em', { class: 'acc-banned', text: 'BANNED' }) : null),
                detail);
        }
        function act(action, f) { call(action, f).then(load).catch(function (e) { showErr(err, e); }); }
        var t = null;
        function load() { return call('adminUsers', { q: q.value.trim() }).then(function (r) { users = r.users || []; if (users.length === 1) open[users[0].id] = 1; draw(); }).catch(function (e) { showErr(err, e); }); }
        q.addEventListener('input', function () { clearTimeout(t); t = setTimeout(load, 400); });
        load();

        // the upload id is the Drive file id in its link
        var link = h('input', { type: 'url', placeholder: 'https://drive.google.com/file/d/…' });
        var delBtn = h('button', { type: 'submit', class: 'ab-btn gray acc-danger', text: 'Delete upload' });
        root.appendChild(h('form', { class: 'acc-card', onsubmit: function (ev) {
            ev.preventDefault();
            var m = link.value.match(/(?:\/d\/|[?&]id=)([-\w]{20,})/);
            if (!m) { showErr(err, new Error('Paste a Google Drive file link.')); return; }
            if (!window.confirm('Delete this file from Drive and ASU Bank?')) return;
            busy(delBtn, true, 'Delete upload');
            call('adminDeleteUpload', { fileId: m[1] }).then(function () { link.value = ''; delBtn.textContent = 'Deleted ✓'; delBtn.disabled = false; })
                .catch(function (e) { showErr(err, e); busy(delBtn, false, 'Delete upload'); });
        } }, h('h2', { text: 'Delete a file' }), h('p', { class: 'acc-muted acc-small', text: 'It goes to the Drive trash and disappears from ASU Bank for everyone. A file owned by another Drive account is only removed from ASU Bank.' }), link, delBtn));

        var repBox = h('div', { class: 'acc-log' }, h('p', { class: 'acc-muted', text: 'Loading…' }));
        root.appendChild(h('div', { class: 'acc-card' }, h('h2', { text: 'Open reports' }), repBox));
        function loadReports() {
            call('adminReports').then(function (r) {
                while (repBox.firstChild) repBox.removeChild(repBox.firstChild);
                var list = r.reports || [];
                if (!list.length) repBox.appendChild(h('p', { class: 'acc-muted', text: 'Nothing to review.' }));
                list.forEach(function (x) {
                    function set(st) { return function () { call('adminReportSet', { id: x.id, status: st }).then(loadReports); }; }
                    repBox.appendChild(h('div', { class: 'acc-small acc-sep' },
                        h('b', { text: x.reason + ' · ' + x.kind + (x.targetUser ? ' · @' + x.targetUser : '') }),
                        h('p', { text: x.targetName || '' }), x.details ? h('p', { text: x.details }) : null,
                        h('p', { class: 'acc-muted', text: x.reporterName + (x.contact ? ' · ' + x.contact : '') + ' · ' + x.created }),
                        h('div', { class: 'acc-actions' },
                            h('button', { type: 'button', class: 'ab-btn gray', text: 'Done', onclick: set('done') }),
                            h('button', { type: 'button', class: 'ab-btn gray', text: 'Dismiss', onclick: set('dismissed') }),
                            x.kind === 'message' ? h('button', { type: 'button', class: 'ab-btn gray acc-danger', text: 'Delete message', onclick: function () {
                                call('deleteMessage', { id: x.targetId }).then(set('done'));
                            } }) : null)));
                });
            }).catch(function (e) { repBox.textContent = e.message; });
        }
        loadReports();

        var sugBox = h('div', { class: 'acc-log' }, h('p', { class: 'acc-muted', text: 'Loading…' }));
        root.appendChild(h('div', { class: 'acc-card' }, h('h2', { text: 'Suggestions' }), sugBox));
        function loadSuggestions() {
            call('adminSuggestions').then(function (r) {
                while (sugBox.firstChild) sugBox.removeChild(sugBox.firstChild);
                var list = r.suggestions || [];
                if (!list.length) sugBox.appendChild(h('p', { class: 'acc-muted', text: 'Nothing to review.' }));
                list.forEach(function (x) {
                    function set(st) { return function () { call('adminSuggestionSet', { id: x.id, status: st }).then(loadSuggestions); }; }
                    sugBox.appendChild(h('div', { class: 'acc-small acc-sep' },
                        h('p', { text: x.note || '' }),
                        h('p', { class: 'acc-muted', text: x.username + (x.contact ? ' · ' + x.contact : '') + ' · ' + x.created }),
                        h('div', { class: 'acc-actions' },
                            h('button', { type: 'button', class: 'ab-btn gray', text: 'Done', onclick: set('done') }),
                            h('button', { type: 'button', class: 'ab-btn gray', text: 'Dismiss', onclick: set('dismissed') }))));
                });
            }).catch(function (e) { sugBox.textContent = e.message; });
        }
        loadSuggestions();

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

    // ---- /feed/: reading is open to everyone, posting happens in the apps
    function ago(iso) {
        var s = (Date.now() - new Date(iso).getTime()) / 1000;
        if (!iso || isNaN(s)) return '';
        if (s < 60) return 'now';
        if (s < 3600) return Math.floor(s / 60) + 'm';
        if (s < 86400) return Math.floor(s / 3600) + 'h';
        if (s < 604800) return Math.floor(s / 86400) + 'd';
        return new Date(iso).toLocaleDateString();
    }
    var AUDIENCE = { 'public': ['🌐', 'Everyone'], students: ['🎓', 'ASU students'], friends: ['👥', 'Friends only'] };
    function bigPhoto(u) { return safePhoto(u).replace(/sz=w\d+/, 'sz=w1600'); }
    function postCard(p, flat) {
        var u = p.user || {}, a = AUDIENCE[p.audience] || AUDIENCE.students;
        var card = h('article', { class: 'acc-card acc-post' + (flat ? ' flat' : '') });
        card.appendChild(h('button', { type: 'button', class: 'acc-post-head', 'aria-label': 'Open the profile of ' + nameOf(u), onclick: flat ? null : function () { showProfile(u.id, nameOf(u)); } },
            avatar(nameOf(u), u.color, 40, u.role === 'admin', u.avatar),
            h('span', { class: 'acc-post-who' }, h('b', { text: nameOf(u) }), h('small', { title: a[1], text: ago(p.created) + ' · ' + a[0] }))));
        if (p.text) card.appendChild(h('p', { class: 'acc-post-text', text: p.text }));
        if (safePhoto(p.image)) card.appendChild(h('a', { href: bigPhoto(p.image), target: '_blank', rel: 'noopener noreferrer' },
            h('img', { class: 'acc-post-img', src: safePhoto(p.image), alt: 'Photo in the post', loading: 'lazy', referrerpolicy: 'no-referrer', onerror: function (e) { e.currentTarget.remove(); } })));
        card.appendChild(h('p', { class: 'acc-post-meta', text: '♥ ' + (p.likes || 0) + '   💬 ' + (p.comments || 0) }));
        return card;
    }
    function driveId(u) { var m = /[?&]id=([\w-]+)/.exec(u || ''); return m ? m[1] : ''; }
    function storyViewer(groups, start) {
        var g = start, s = 0, timer = null;
        var stage = h('div', { class: 'acc-story-stage' });
        var bars = h('div', { class: 'acc-story-bars' });
        var head = h('div', { class: 'acc-story-head' });
        var box = h('div', { class: 'acc-story', role: 'dialog', 'aria-modal': 'true', 'aria-label': 'Story' });
        function close() { clearTimeout(timer); document.removeEventListener('keydown', keys); box.remove(); }
        function step(d) {
            var list = groups[g].stories;
            if (s + d >= 0 && s + d < list.length) s += d;
            else if (d > 0 && g + 1 < groups.length) { g++; s = 0; }
            else if (d < 0 && g > 0) { g--; s = groups[g].stories.length - 1; }
            else if (d > 0) return close();
            draw();
        }
        function keys(e) { if (e.key === 'Escape') close(); else if (e.key === 'ArrowRight') step(1); else if (e.key === 'ArrowLeft') step(-1); }
        function draw() {
            clearTimeout(timer);
            var group = groups[g], item = group.stories[s], u = group.user;
            while (stage.firstChild) stage.removeChild(stage.firstChild);
            while (bars.firstChild) bars.removeChild(bars.firstChild);
            while (head.firstChild) head.removeChild(head.firstChild);
            group.stories.forEach(function (_, i) { bars.appendChild(h('i', { class: i <= s ? 'on' : '' })); });
            head.appendChild(avatar(nameOf(u), u.color, 36, u.role === 'admin', u.avatar));
            head.appendChild(h('span', { class: 'acc-post-who' }, h('b', { text: nameOf(u) }), h('small', { text: ago(item.created) })));
            head.appendChild(h('button', { type: 'button', class: 'acc-story-x', 'aria-label': 'Close', text: '×', onclick: close }));
            var vid = driveId(item.video);
            stage.style.background = (item.image || vid) ? '#000' : safeColor(item.bg);
            if (vid) stage.appendChild(h('iframe', { src: 'https://drive.google.com/file/d/' + vid + '/preview', title: 'Story video', allow: 'autoplay; fullscreen', referrerpolicy: 'no-referrer' }));
            else if (safePhoto(item.image)) stage.appendChild(h('img', { src: bigPhoto(item.image), alt: '', referrerpolicy: 'no-referrer' }));
            if (item.text) stage.appendChild(h('p', { class: 'acc-story-text', text: item.text }));
            if (token && user && !item.seen) { item.seen = true; call('storySeen', { id: item.id }).catch(function () {}); }
            // a video moves on when the person taps next: the Drive player doesn't say when it has ended
            if (!vid) timer = setTimeout(function () { step(1); }, 6000);
        }
        box.appendChild(bars); box.appendChild(head); box.appendChild(stage);
        box.appendChild(h('button', { type: 'button', class: 'acc-story-nav prev', 'aria-label': 'Previous story', text: '‹', onclick: function () { step(-1); } }));
        box.appendChild(h('button', { type: 'button', class: 'acc-story-nav next', 'aria-label': 'Next story', text: '›', onclick: function () { step(1); } }));
        document.addEventListener('keydown', keys);
        document.body.appendChild(box);
        draw();
    }
    function feedView() {
        var signedIn = !!(token && user);
        var scope = signedIn ? (get('ab.feed.scope') || 'friends') : 'guest';
        var offset = 0;
        var loadToken = 0;
        clear();
        root.appendChild(h('div', { class: 'acc-intro' }, h('h1', { class: 'ab-large', text: 'Feed' })));
        var stories = h('div', { class: 'acc-stories' });
        root.appendChild(stories);
        if (signedIn) {
            var seg = h('div', { class: 'acc-seg', role: 'tablist' });
            [['friends', 'Friends'], ['strangers', 'Strangers'], ['guest', 'Guest']].forEach(function (o) {
                seg.appendChild(h('button', { type: 'button', role: 'tab', 'aria-selected': o[0] === scope ? 'true' : 'false', class: o[0] === scope ? 'on' : '', text: o[1], onclick: function () {
                    scope = o[0]; set('ab.feed.scope', scope);
                    Array.prototype.forEach.call(seg.children, function (b) { var on = b === seg.children[['friends', 'strangers', 'guest'].indexOf(scope)]; b.className = on ? 'on' : ''; b.setAttribute('aria-selected', on ? 'true' : 'false'); });
                    posts(true);
                } }));
            });
            root.appendChild(seg);
            root.appendChild(h('p', { class: 'acc-muted acc-small', text: 'To post or add a story, use the ASU Bank app.' }));
        } else {
            root.appendChild(h('div', { class: 'acc-card' }, h('b', { text: "You're browsing as a guest" }),
                h('p', { class: 'acc-muted', text: 'You see what students share with everyone. Sign in for more.' }),
                h('a', { class: 'ab-btn', href: '/account/', text: 'Sign in or create account' })));
        }
        var list = h('div', { 'aria-live': 'polite' });
        var moreBtn = h('button', { type: 'button', class: 'ab-btn gray acc-more', text: 'Load more', hidden: true, onclick: function () { posts(false); } });
        root.appendChild(list); root.appendChild(moreBtn);

        function posts(fresh) {
            var mine = ++loadToken;
            if (fresh) {
                offset = 0;
                while (list.firstChild) list.removeChild(list.firstChild);
                list.appendChild(h('p', { class: 'acc-muted', text: 'Loading…' }));
                moreBtn.hidden = true;
            }
            call(signedIn ? 'feedAll' : 'publicFeed', { scope: scope, offset: offset }).then(function (r) {
                if (mine !== loadToken) return;
                if (fresh) while (list.firstChild) list.removeChild(list.firstChild);
                var got = r.posts || [];
                if (fresh && !got.length) list.appendChild(h('p', { class: 'acc-muted acc-center', text:
                    scope === 'friends' ? 'No posts from friends yet.' : scope === 'strangers' ? 'No posts from other students yet.' : 'No public posts yet.' }));
                got.forEach(function (p) { list.appendChild(postCard(p)); });
                offset += got.length;
                moreBtn.hidden = got.length < 25;
            }).catch(function (e) {
                if (mine !== loadToken) return;
                if (!token && signedIn) return feedView();
                if (fresh) { while (list.firstChild) list.removeChild(list.firstChild); }
                list.appendChild(h('p', { class: 'acc-err', role: 'alert', text: e.message }));
            });
        }
        call(signedIn ? 'stories' : 'publicStories').then(function (r) {
            (r.groups || []).forEach(function (g, i) {
                if (!g.stories || !g.stories.length) return;
                stories.appendChild(h('button', { type: 'button', class: 'acc-story-pick' + (g.seen ? ' seen' : ''), onclick: function () { storyViewer(r.groups, i); } },
                    h('span', { class: 'acc-story-ring' }, avatar(nameOf(g.user), g.user.color, 56, false, g.user.avatar)),
                    h('small', { text: nameOf(g.user) })));
            });
        }).catch(function () {});
        posts(true);
    }

    var views = { account: accountView, chat: chatView, admin: adminView, feed: feedView };
    (views[view] || accountView)();
    if (view === 'chat') window.addEventListener('hashchange', chatView);
})();
