/* UniHub accounts, chat and admin on the website.
   Same "UniHub Accounts" Apps Script as the iOS and Android apps (tools/apps-script/accounts-api.gs),
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
            .then(function (r) { return r.json(); }, function () { throw new Error("Couldn't reach UniHub. Check your connection and try again."); })
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
    function signOutLocally() { token = null; user = null; set('ab.acc.token', null); set('ab.acc.user', null); cacheClear(); }
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
        team: ['🏦', 'UniHub team'], mentor: ['🧭', 'Mentor'], ambassador: ['📣', 'Ambassador'], top_student: ['🏅', 'Top student'], class_rep: ['🙋', 'Class rep'],
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
        var body = h('div', { class: 'acc-profile' }, skel('row', 2));
        s.box.appendChild(body);
        var signedIn = !!(token && user);
        call(signedIn ? 'profile' : 'publicProfile', { userId: userId, social: true }).then(function (r) {
            var u = r.user; while (body.firstChild) body.removeChild(body.firstChild);
            var cover = h('div', { class: 'acc-cover', style: 'background:' + safeColor(u.color) });
            if (safePhoto(u.cover)) cover.appendChild(h('img', { src: safePhoto(u.cover), alt: '', referrerpolicy: 'no-referrer', onerror: function (e) { e.currentTarget.remove(); } }));
            body.appendChild(cover);
            body.appendChild(h('div', { class: 'acc-cover-av' }, avatar(nameOf(u), u.color, 96, u.role === 'admin', u.avatar, signedIn ? !!u.online : null)));
            body.appendChild(h('h2', { text: nameOf(u) }));
            body.appendChild(h('p', { class: 'acc-muted', text: '@' + u.username + (u.role === 'admin' ? ' · Admin' : '') + (signedIn ? ' · ' + lastSeenText(u.lastSeen, u.online) : '') }));
            if (signedIn) body.appendChild(h('div', { class: 'acc-pstats' }, [['Friends', r.friendCount], ['Posts', r.postCount], ['Messages', r.messageCount]].map(function (s) {
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
            if (signedIn && r.friendship && r.friendship !== 'self') body.appendChild(h('div', { class: 'acc-actions acc-center-row' }, friendControls(u, r.friendship)));
            if (signedIn && r.canMessage && u.id !== user.id) body.appendChild(h('div', { class: 'acc-actions acc-center-row' },
                h('a', { class: 'ab-btn', href: '/chat/#@dm/' + encodeURIComponent(u.id), onclick: function () { s.close(); }, text: 'Send a message' })));
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

    // ---- 1.14.0 loading effects
    function dots(label) { return h('div', { class: 'acc-dots', role: 'status', 'aria-label': label || 'Loading' }, h('i'), h('i'), h('i')); }
    // grey shapes in the form of what is coming, so the screen feels ready straight away
    function skel(kind, n) {
        var box = h('div', { class: 'acc-skel', role: 'status', 'aria-label': 'Loading' });
        if (kind === 'bubble') [[170, 0], [120, 0], [210, 1], [140, 0], [90, 1], [190, 0]].forEach(function (b) {
            box.appendChild(h('div', { class: 'acc-skel-b' + (b[1] ? ' mine' : '') }, h('i', { style: 'width:' + b[0] + 'px' })));
        });
        else for (var i = 0; i < (n || 3); i++) box.appendChild(kind === 'post'
            ? h('div', { class: 'acc-card acc-skel-post' }, h('div', { class: 'acc-skel-top' }, h('i', { class: 'av' }), h('span', null, h('i', { class: 'l1' }), h('i', { class: 'l2' }))), h('i', { class: 'l3' }), h('i', { class: 'pic' }))
            : h('div', { class: 'acc-skel-top row' }, h('i', { class: 'av' }), h('span', null, h('i', { class: 'l1' }), h('i', { class: 'l2' }))));
        return box;
    }
    // what a screen showed last time, so it opens at once. Cleared when signing out.
    function cacheGet(k) { try { return JSON.parse(get('ab.cache.' + k)); } catch (e) { return null; } }
    function cachePut(k, v) { set('ab.cache.' + k, JSON.stringify(v)); }
    function cacheClear() {
        try { Object.keys(localStorage).forEach(function (k) { if (k.indexOf('ab.cache.') === 0) localStorage.removeItem(k); }); } catch (e) {}
    }

    // ---- work that carries on in the background, with a strip at the top
    var jobsBar = null;
    function addJob(title, run, done) {
        if (!jobsBar) { jobsBar = h('div', { class: 'acc-jobs', 'aria-live': 'polite' }); document.body.appendChild(jobsBar); }
        var row = h('div', { class: 'acc-job' });
        function draw(state, message) {
            while (row.firstChild) row.removeChild(row.firstChild);
            row.appendChild(state === 'ok' ? h('b', { class: 'acc-job-ok', text: '✓' }) : state === 'fail' ? h('b', { class: 'acc-job-bad', text: '!' }) : dots());
            row.appendChild(h('span', { class: 'acc-job-t' }, h('b', { text: state === 'ok' ? 'Done' : title }), message ? h('small', { text: message }) : null));
            if (state === 'fail') {
                row.appendChild(h('button', { type: 'button', class: 'acc-job-btn', text: 'Try again', onclick: go }));
                row.appendChild(h('button', { type: 'button', class: 'acc-x', 'aria-label': 'Close', text: '×', onclick: function () { row.remove(); } }));
            }
        }
        function go() {
            draw('run');
            run().then(function () {
                draw('ok');
                if (done) done();
                setTimeout(function () { row.classList.add('out'); setTimeout(function () { row.remove(); }, 260); }, 1200);
            }).catch(function (e) { draw('fail', e.message); });
        }
        jobsBar.appendChild(row);
        go();
    }

    // ---- disappearing
    var DISAPPEAR = [[0, "Don't disappear"], [60, '1 minute'], [3600, '60 minutes'], [86400, '1 day'], [604800, '1 week']];
    function expired(iso) { return !!iso && new Date(iso).getTime() <= Date.now(); }
    function leftText(iso) {
        if (!iso) return '';
        var s = Math.max(0, Math.floor((new Date(iso).getTime() - Date.now()) / 1000));
        return '⏱ ' + (s < 60 ? Math.max(1, s) + 's' : s < 3600 ? Math.floor(s / 60) + 'm' : s < 86400 ? Math.floor(s / 3600) + 'h' : Math.floor(s / 86400) + 'd');
    }
    function disappearPick() {
        var pick = h('select', { class: 'acc-disappear', 'aria-label': 'Disappears after', title: 'Disappears after' });
        DISAPPEAR.forEach(function (d) { pick.appendChild(h('option', { value: String(d[0]), text: (d[0] ? '⏱ ' : '') + d[1] })); });
        pick.addEventListener('change', function () { pick.classList.toggle('on', pick.value !== '0'); });
        return pick;
    }

    // ---- files in chats and posts
    var MIME = { m4a: 'audio/mp4', weba: 'audio/webm', ogg: 'audio/ogg', mp4: 'video/mp4', mov: 'video/quicktime', webm: 'video/webm', jpg: 'image/jpeg', jpeg: 'image/jpeg', png: 'image/png', pdf: 'application/pdf' };
    function blobFrom(data, name) {
        var bin = atob(data), bytes = new Uint8Array(bin.length);
        for (var i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
        var ext = String(name || '').split('.').pop().toLowerCase();
        return URL.createObjectURL(new Blob([bytes], { type: MIME[ext] || 'application/octet-stream' }));
    }
    var mediaUrls = {};
    function mediaUrl(key, get) {
        if (!mediaUrls[key]) mediaUrls[key] = get().catch(function (e) { delete mediaUrls[key]; throw e; });
        return mediaUrls[key];
    }
    function roomOrPostMedia(scope, id, name) {
        return function () {
            return mediaUrl(scope + id, function () {
                return call('mediaFile', { scope: scope, id: id }).then(function (r) { return blobFrom(r.data, r.name || name); });
            });
        };
    }
    function fileBase64(f) {
        return new Promise(function (ok, bad) {
            var rd = new FileReader();
            rd.onload = function () { ok(String(rd.result).split(',')[1]); };
            rd.onerror = function () { bad(new Error("Couldn't open this file.")); };
            rd.readAsDataURL(f);
        });
    }
    function voiceTime(s) { s = Math.round(s || 0); return Math.floor(s / 60) + ':' + ('0' + (s % 60)).slice(-2); }
    var voices = {}, voiceNow = null;
    function voiceBubble(id, duration, fetchUrl) {
        var seed = 7; for (var c = 0; c < id.length; c++) seed = (seed * 31 + id.charCodeAt(c)) >>> 0;
        var wave = h('span', { class: 'acc-wave', 'aria-hidden': 'true' });
        for (var i = 0; i < 26; i++) {
            seed = (Math.imul(seed, 1664525) + 1013904223) >>> 0;
            wave.appendChild(h('i', { style: 'height:' + (6 + (seed >>> 28)) + 'px' }));
        }
        var btn = h('button', { type: 'button', class: 'acc-voice-btn', 'aria-label': 'Play voice message', text: '▶' });
        var time = h('small', { text: voiceTime(duration) });
        var box = h('div', { class: 'acc-voice' }, btn, wave, time);
        function draw(a) {
            var p = a && a.duration ? a.currentTime / a.duration : 0;
            Array.prototype.forEach.call(wave.children, function (b, i) { b.className = i / 26 <= p && p > 0 ? 'on' : ''; });
            var on = a && !a.paused;
            btn.textContent = on ? '❚❚' : '▶';
            btn.setAttribute('aria-label', on ? 'Pause' : 'Play voice message');
        }
        function bind(a) { a.ontimeupdate = function () { draw(a); }; a.onended = function () { a.currentTime = 0; draw(a); }; a.onpause = a.onplay = function () { draw(a); }; draw(a); }
        if (voices[id]) bind(voices[id]);
        btn.addEventListener('click', function (e) {
            e.stopPropagation();
            var a = voices[id];
            if (a) {
                if (a.paused) { if (voiceNow && voiceNow !== a) voiceNow.pause(); voiceNow = a; a.play(); } else a.pause();
                return;
            }
            btn.disabled = true; btn.textContent = '…';
            fetchUrl().then(function (u) {
                a = voices[id] = new Audio(u);
                a.onerror = function () { delete voices[id]; time.textContent = "This browser can't play it"; btn.textContent = '▶'; };
                bind(a);
                if (voiceNow) voiceNow.pause();
                voiceNow = a;
                return a.play();
            }).catch(function () { time.textContent = "Couldn't play it"; btn.textContent = '▶'; })
              .then(function () { btn.disabled = false; });
        });
        return box;
    }
    function photoTile(fetchUrl) {
        var img = h('img', { class: 'acc-dm-photo', alt: 'Photo' });
        var loader = dots();
        var tile = h('button', { type: 'button', class: 'acc-dm-photo-btn', 'aria-label': 'View photo', onclick: function (e) { e.stopPropagation(); if (img.src) window.open(img.src, '_blank', 'noopener'); } }, loader, img);
        fetchUrl().then(function (u) { img.src = u; loader.remove(); }).catch(function () { loader.remove(); });
        return tile;
    }
    // a video comes down only when it's tapped
    function videoTile(fetchUrl) {
        var tile = h('button', { type: 'button', class: 'acc-video-tile', 'aria-label': 'Play video' }, h('span', { text: '▶' }));
        tile.addEventListener('click', function (e) {
            e.stopPropagation();
            while (tile.firstChild) tile.removeChild(tile.firstChild);
            tile.appendChild(dots());
            tile.disabled = true;
            fetchUrl().then(function (u) {
                var v = h('video', { class: 'acc-video', src: u, controls: true, autoplay: true, playsinline: true });
                tile.replaceWith(v);
            }).catch(function () { while (tile.firstChild) tile.removeChild(tile.firstChild); tile.disabled = false; tile.appendChild(h('span', { text: '!' })); });
        });
        return tile;
    }
    function mediaOf(m, fetchUrl) {
        if (m.fileType === 'voice') return voiceBubble(m.id, m.duration, fetchUrl);
        if (m.fileType === 'video') return videoTile(fetchUrl);
        if (m.fileType === 'photo') return photoTile(fetchUrl);
        return null;
    }
    function previewOf(m) {
        return m.text || (m.fileType === 'voice' ? '🎤 Voice message' : m.fileType === 'video' ? '🎬 Video' : m.fileType === 'photo' ? '📷 Photo' : m.fileName ? '📄 ' + m.fileName : '');
    }
    var localSeq = 0;

    // the row in a chat: attach, timer, message box, and the microphone or send button
    function chatRow(opts) {
        var input = opts.input, send = opts.send;
        var file = h('input', { type: 'file', hidden: true, accept: 'image/jpeg,image/png,image/webp,video/mp4,video/quicktime,video/webm' + (opts.docs ? ',.pdf,.docx,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document' : '') });
        var attach = h('button', { type: 'button', class: 'acc-attach', 'aria-label': 'Attach a file', title: opts.docs ? 'Attach a photo, video, PDF or Word file' : 'Attach a photo or video', text: '+', onclick: function () { file.click(); } });
        var pick = disappearPick();
        var mic = h('button', { type: 'button', class: 'acc-send', 'aria-label': 'Record a voice message', title: 'Record a voice message', text: '🎤' });
        var row = h('div', { class: 'acc-compose-row' }, attach, pick, input, mic, send);
        var recBar = h('div', { class: 'acc-rec', hidden: true });
        function tidy() { var empty = !input.value.trim(); mic.hidden = !empty || !window.MediaRecorder; send.hidden = empty && !mic.hidden; }
        input.addEventListener('input', tidy);
        tidy();
        file.addEventListener('change', function () {
            var f = file.files[0]; file.value = '';
            if (!f) return;
            var isVideo = /^video\//.test(f.type), isPhoto = /^image\//.test(f.type);
            if (isVideo && f.size > 12000000) return opts.fail(new Error('That video is too big. The limit is 12 MB.'));
            if (!isVideo && !isPhoto && f.size > 8000000) return opts.fail(new Error('That file is too big. The limit is 8 MB.'));
            var local = URL.createObjectURL(f);
            if (isPhoto) opts.media('photo', 'photo.jpg', function () { return postPhotoData(f); }, 0, local, f.size);
            else if (isVideo) opts.media('video', /\.mov$/i.test(f.name) ? 'video.mov' : /\.webm$/i.test(f.name) ? 'video.webm' : 'video.mp4', function () { return fileBase64(f); }, 0, local, f.size);
            else opts.media(/\.pdf$/i.test(f.name) ? 'pdf' : 'docx', f.name, function () { return fileBase64(f); }, 0, null, f.size);
        });
        var rec = null, chunks = [], started = 0, tick = null, cancelled = false, streamNow = null;
        function stopAll() { clearInterval(tick); if (streamNow) streamNow.getTracks().forEach(function (t) { t.stop(); }); streamNow = null; recBar.hidden = true; row.hidden = false; }
        function drawRec() {
            while (recBar.firstChild) recBar.removeChild(recBar.firstChild);
            var clock = h('b', { text: '0:00' });
            recBar.appendChild(h('button', { type: 'button', class: 'acc-attach', 'aria-label': 'Cancel', text: '🗑', onclick: function () { cancelled = true; rec.stop(); } }));
            recBar.appendChild(h('i', { class: 'acc-rec-dot' }));
            recBar.appendChild(clock);
            recBar.appendChild(h('span', { class: 'acc-rec-t', text: 'Recording…' }));
            recBar.appendChild(h('button', { type: 'button', class: 'acc-send', 'aria-label': 'Send', text: '↑', onclick: function () { rec.stop(); } }));
            tick = setInterval(function () {
                var s = (Date.now() - started) / 1000;
                clock.textContent = voiceTime(s);
                if (s >= 180 && rec.state === 'recording') rec.stop();
            }, 250);
        }
        mic.addEventListener('click', function () {
            navigator.mediaDevices.getUserMedia({ audio: true }).then(function (stream) {
                streamNow = stream;
                // iPhones can't play webm, so mp4 audio is first choice wherever the browser records it
                var mp4 = MediaRecorder.isTypeSupported && MediaRecorder.isTypeSupported('audio/mp4');
                rec = new MediaRecorder(stream, mp4 ? { mimeType: 'audio/mp4', audioBitsPerSecond: 32000 } : { audioBitsPerSecond: 32000 });
                chunks = []; cancelled = false; started = Date.now();
                rec.ondataavailable = function (e) { if (e.data && e.data.size) chunks.push(e.data); };
                rec.onstop = function () {
                    var length = Math.max(1, Math.round((Date.now() - started) / 1000));
                    stopAll();
                    if (cancelled) return;
                    var blob = new Blob(chunks, { type: rec.mimeType || (mp4 ? 'audio/mp4' : 'audio/webm') });
                    if (blob.size > 8000000) return opts.fail(new Error('That file is too big. The limit is 8 MB.'));
                    opts.media('voice', mp4 ? 'voice.m4a' : 'voice.weba', function () { return fileBase64(blob); }, length, URL.createObjectURL(blob), blob.size);
                };
                row.hidden = true; recBar.hidden = false;
                drawRec();
                rec.start();
            }).catch(function () { opts.fail(new Error("UniHub can't use the microphone. You can allow it in your browser's settings.")); });
        });
        return { row: row, recBar: recBar, file: file, disappear: function () { return Number(pick.value) || 0; }, tidy: tidy };
    }

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
                h('p', { class: 'acc-codemsg', text: "This name belongs to an UniHub admin. Send a code to the owner's email to prove it's you." }),
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
                        thanks.box.appendChild(h('p', { text: "You joined UniHub in its early days, so your profile now has the Early supporter badge. Thank you for helping students at ASU." }));
                    }
                })
                    .catch(function (e) { showErr(err, e); if (e.needCode) codeBox.hidden = false; busy(submit, false, mode === 0 ? 'Sign in' : 'Create account'); });
            } },
                login.el, pw.el,
                mode === 1 ? [pw2.el, dn.el, em.el, uid.el, codeBox] : null,
                err, submit);
            if (mode === 1) pw.input.autocomplete = pw2.input.autocomplete = 'new-password';
            wrap.appendChild(h('img', { src: '/icons/icon-192x192.png', alt: '', width: 72, height: 72, class: 'acc-logo' }));
            wrap.appendChild(h('h2', { text: mode === 0 ? 'Welcome back' : 'Join UniHub' }));
            wrap.appendChild(h('p', { class: 'acc-muted', text: 'An account lets you chat with other students and ask for help. It works here and in the UniHub apps.' }));
            wrap.appendChild(h('div', { class: 'acc-seg', role: 'tablist' },
                ['Sign in', 'Create account'].map(function (l, i) {
                    return h('button', { type: 'button', role: 'tab', 'aria-selected': String(mode === i), class: mode === i ? 'on' : '', text: l, onclick: function () { mode = i; draw(); } });
                })));
            wrap.appendChild(form);
            if (mode === 0) wrap.appendChild(h('p', { class: 'acc-muted acc-small', text: 'Forgot your password? Ask an admin in the chat or email necro144@yahoo.com to reset it.' }));
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
        if (room.indexOf('@dm/') === 0) return dmThread(room.slice(4));
        if (room && room !== '@messages') return roomView(room);
        clear();
        root.appendChild(h('div', { class: 'acc-intro' }, h('h1', { class: 'ab-large', text: 'Chat' })));
        var tabs = h('div', { class: 'acc-pills', role: 'tablist' });
        var msgTab = h('button', { type: 'button', role: 'tab', class: room ? 'on' : '', 'aria-selected': room ? 'true' : 'false', text: 'Messages', onclick: function () { location.hash = '@messages'; } });
        tabs.appendChild(h('button', { type: 'button', role: 'tab', class: room ? '' : 'on', 'aria-selected': room ? 'false' : 'true', text: 'Rooms', onclick: function () { location.hash = ''; } }));
        tabs.appendChild(msgTab);
        root.appendChild(tabs);
        call('dmList').then(function (r) { cachePut('dms', r); if (r.unread) msgTab.textContent = 'Messages (' + r.unread + ')'; if (room) drawDms(r); })
            .catch(function (e) { if (room) { dmBox.textContent = e.message; } });
        var dmBox = h('div', null, skel('row', 5));
        function drawDms(r) {
            while (dmBox.firstChild) dmBox.removeChild(dmBox.firstChild);
            dmBox.appendChild(h('div', { class: 'acc-card acc-rooms' }, h('button', { type: 'button', class: 'acc-room', onclick: newMessage },
                h('span', { class: 'acc-emoji', text: '✏️' }), h('span', { class: 'acc-room-t' }, h('b', { text: 'New message' })))));
            var list = r.conversations || [];
            if (!list.length) {
                dmBox.appendChild(h('div', { class: 'acc-empty' }, h('b', { text: 'No private messages yet' }),
                    h('p', { class: 'acc-muted', text: 'You can message your friends. Only the two of you see the chat.' })));
                return;
            }
            dmBox.appendChild(h('div', { class: 'acc-card acc-rooms' }, list.map(function (c) {
                var preview = previewOf(c.last);
                return h('a', { class: 'acc-room' + (c.unread ? ' unread' : ''), href: '#@dm/' + encodeURIComponent(c.user.id) },
                    avatar(nameOf(c.user), c.user.color, 50, c.user.role === 'admin', c.user.avatar, c.user.online == null ? null : !!c.user.online),
                    h('span', { class: 'acc-room-t' },
                        h('span', { class: 'acc-dm-top' }, h('b', { text: nameOf(c.user) }), h('small', { text: ago(c.last.created) })),
                        h('span', { class: 'acc-dm-top' }, h('small', { class: 'acc-dm-preview', text: preview }), c.unread ? h('i', { class: 'acc-count', text: String(c.unread) }) : null)));
            })));
        }
        if (room) { var savedDms = cacheGet('dms'); if (savedDms) drawDms(savedDms); root.appendChild(dmBox); return; }
        var strip = h('div', { class: 'acc-card acc-rooms', hidden: true });
        root.appendChild(strip);
        call('online').then(function (r) {
            var on = (r.users || []).filter(function (x) { return x.online; });
            if (!(r.users || []).length) return;
            strip.hidden = false;
            strip.appendChild(h('button', { type: 'button', class: 'acc-room', onclick: showOnline },
                h('span', { class: 'acc-stack' }, on.slice(0, 6).map(function (x) { return avatar(x.displayName, x.color, 32, false, x.avatar, true); })),
                h('span', { class: 'acc-room-t' }, h('b', { text: (r.onlineCount || 0) + ' online now' }), h('small', { text: (r.users || []).length + ' students' })),
                h('span', { class: 'acc-chev', text: '›' })));
        }).catch(function () {});
        var list = h('div', { class: 'acc-card acc-rooms' }, skel('row', 5));
        root.appendChild(list);
        root.appendChild(h('p', { class: 'acc-muted acc-small acc-foot', text: "Be kind and helpful. Don't share exam answers during an exam, private info or anything illegal. Admins can remove messages and ban accounts." }));
        root.appendChild(h('p', { class: 'acc-muted acc-small acc-center', text: 'Signed in as @' + user.username }));
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
        var savedRooms = cacheGet('rooms');
        if (savedRooms && savedRooms.length) drawRooms(savedRooms);
        call('rooms').then(function (r) { cachePut('rooms', r.rooms || []); drawRooms(r.rooms || []); }).catch(function (e) { if (!savedRooms) list.textContent = e.message; });
    }

    function showOnline() {
        var s = sheet('Who’s online');
        var box = h('div', { class: 'acc-users' }, skel('row', 4));
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
        var msgs = [], byId = {}, people = {}, replyTo = null, editing = null, pinned = null, last = null, seenIds = {}, forceBottom = false;
        var title = h('h1', { class: 'acc-room-title', text: roomId });
        var titleEmoji = h('span', { class: 'acc-emoji', text: '💬' });
        var titleAbout = h('small', { class: 'acc-muted' });
        var pinBar = h('div', { class: 'acc-pinned', hidden: true });
        var feed = h('div', { class: 'acc-feed', 'aria-live': 'polite' });
        var err = errBox();
        var input = h('textarea', { rows: 1, maxlength: 1000, placeholder: 'Message' });
        var replyBar = h('div', { class: 'acc-reply', hidden: true });
        var send = h('button', { type: 'submit', class: 'acc-send', 'aria-label': 'Send', text: '↑' });
        // the message shows at once and is replaced by the real one when the server has it
        function deliver(text, media) {
            var reply = replyTo ? replyTo.id : '';
            setReply(null);
            var temp = { id: 'local-' + (++localSeq), room: roomId, userId: user.id, username: user.username, displayName: user.displayName, role: user.role, color: user.color,
                text: text, created: new Date().toISOString(), replyTo: reply, pending: true };
            if (media) { temp.hasFile = true; temp.fileType = media.type; temp.fileName = media.name; temp.duration = media.duration; temp.local = media.local; }
            byId[temp.id] = temp; msgs.push(temp); forceBottom = true; render();
            var gone = bar.disappear();
            (media ? media.load() : Promise.resolve(null)).then(function (data) {
                var f = { room: roomId, text: text, replyTo: reply, expiresIn: gone };
                if (data) { f.file = data; f.fileName = media.name; f.duration = media.duration; }
                return call('send', f);
            }).then(function (r) {
                delete byId[temp.id];
                msgs = msgs.filter(function (m) { return m !== temp; });
                if (r.message && !byId[r.message.id]) {
                    if (media && media.local) { r.message.local = media.local; mediaUrls['room' + r.message.id] = Promise.resolve(media.local); }
                    seenIds[r.message.id] = 1;
                    add([r.message]);
                }
                render();
                showErr(err, null);
            }).catch(function (e) {
                delete byId[temp.id];
                msgs = msgs.filter(function (m) { return m !== temp; });
                render();
                showErr(err, e);
                if (text && !input.value) { input.value = text; bar.tidy(); }
            });
        }
        var bar = chatRow({ input: input, send: send, fail: function (e) { showErr(err, e); }, media: function (type, name, load, duration, local) {
            deliver('', { type: type, name: name, load: load, duration: duration, local: local });
        } });
        var composer = h('form', { class: 'acc-composer', onsubmit: function (ev) {
            ev.preventDefault();
            var t = input.value.trim(); if (!t) return;
            input.value = ''; bar.tidy(); showErr(err, null);
            if (editing) {
                var m = editing, before = [m.text, m.edited];
                m.text = t; m.edited = new Date().toISOString();
                setReply(null); render();
                call('editMessage', { id: m.id, text: t }).catch(function (e) { m.text = before[0]; m.edited = before[1]; render(); showErr(err, e); });
            } else deliver(t);
            input.focus();
        } }, replyBar, bar.row, bar.recBar, bar.file);
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
        root.appendChild(h('div', { class: 'acc-room-head' }, h('a', { href: '#', class: 'acc-back', 'aria-label': 'Back to rooms', text: '‹' }), titleEmoji,
            h('span', { class: 'acc-room-t' }, title, titleAbout), settings));
        root.appendChild(pinBar);
        root.appendChild(feed);
        root.appendChild(err);
        root.appendChild(composer);
        root.appendChild(readonly);

        function setInfo(rm) {
            info = rm; title.textContent = (rm.locked ? '🔒 ' : '') + rm.name;
            titleEmoji.textContent = rm.emoji || '💬'; titleAbout.textContent = rm.about || '';
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
            send.textContent = editing ? '✓' : '↑';
            if (m) replyBar.appendChild(h('span', null, h('b', { text: (edit ? 'Editing your message' : 'Replying to ' + nameOf(m)) + ': ' }), previewOf(m).slice(0, 80)),
                h('button', { type: 'button', class: 'acc-x', 'aria-label': 'Cancel', text: '×', onclick: function () { if (editing) input.value = ''; setReply(null); } }));
        }
        function drawPin() {
            while (pinBar.firstChild) pinBar.removeChild(pinBar.firstChild);
            pinBar.hidden = !pinned;
            if (!pinned) return;
            pinBar.appendChild(h('button', { type: 'button', class: 'acc-pinned-t', onclick: function () {
                var el = feed.querySelector('[data-id="' + pinned.id + '"]'); if (el) el.scrollIntoView({ block: 'center', behavior: 'smooth' });
            } }, h('b', { text: '📌 ' + nameOf(pinned) + ': ' }), previewOf(pinned).slice(0, 140)));
            if (isAdmin()) pinBar.appendChild(h('button', { type: 'button', class: 'acc-x', 'aria-label': 'Unpin', title: 'Unpin', text: '×', onclick: function () { pin(null); } }));
        }
        function pin(m) {
            call('adminPin', { room: roomId, id: m ? m.id : '' }).then(function () { pinned = m; drawPin(); render(); }).catch(function (e) { showErr(err, e); });
        }

        function bubble(m, prev, next) {
            var mine = m.userId === user.id, admin = m.role === 'admin';
            var below = next && next.userId === m.userId && new Date(next.created).toDateString() === new Date(m.created).toDateString();
            var p = people[m.userId] || {};
            var grouped = prev && prev.userId === m.userId && new Date(prev.created).toDateString() === new Date(m.created).toDateString();
            var reply = m.replyTo && byId[m.replyTo];
            var who = function () { showProfile(m.userId, nameOf(m)); };
            var actions = m.pending ? null : h('span', { class: 'acc-msg-actions' },
                h('button', { type: 'button', text: 'Reply', onclick: function () { setReply(m); input.focus(); } }),
                mine && m.text ? h('button', { type: 'button', text: 'Edit', onclick: function () { setReply(m, true); input.value = m.text; bar.tidy(); input.focus(); } }) : null,
                isAdmin() ? h('button', { type: 'button', text: pinned && pinned.id === m.id ? 'Unpin' : 'Pin', onclick: function () { pin(pinned && pinned.id === m.id ? null : m); } }) : null,
                !mine ? h('button', { type: 'button', text: 'Report', onclick: function () { reportSheet('message', m.id, previewOf(m).slice(0, 120)); } }) : null,
                (!mine && m.role !== 'admin') ? h('button', { type: 'button', text: 'Block', onclick: function () { blockPerson(m.userId, nameOf(m), function () { location.reload(); }); } }) : null,
                (mine || isAdmin()) ? h('button', { type: 'button', text: 'Delete for everyone', onclick: function () {
                    if (!window.confirm('Delete this message for everyone?')) return;
                    // gone at once here; everyone else's screen removes it on its next check
                    remove([m.id]);
                    call('deleteMessage', { id: m.id }).catch(function (e) { byId[m.id] = m; msgs.push(m); msgs.sort(function (a, b) { return a.created < b.created ? -1 : 1; }); render(); showErr(err, e); });
                } }) : null,
                (isAdmin() && !mine) ? h('button', { type: 'button', text: 'Ban', onclick: function () {
                    if (!window.confirm('Ban @' + m.username + '? They can’t sign in or chat until an admin unbans them.')) return;
                    call('adminBan', { userId: m.userId, banned: true, reason: 'Removed from the chat by an admin' }).then(function () { showErr(err, new Error('@' + m.username + ' is banned.')); }).catch(function (e) { showErr(err, e); });
                } }) : null);
            var av = !mine ? (below ? h('span', { class: 'acc-av-space' }) : h('button', { type: 'button', class: 'acc-av-btn', 'aria-label': 'Profile of ' + nameOf(m), onclick: who },
                avatar(nameOf(m), m.color, 30, admin, p.avatar, p.online ? true : null))) : null;
            return h('div', { class: 'acc-msg' + (mine ? ' mine' : '') + (grouped ? ' grouped' : '') + (below ? ' below' : '') + (m.pending ? ' pending' : ''), 'data-id': m.id },
                av,
                h('div', { class: 'acc-bubble', onclick: toggleActions },
                    (!mine && !grouped) ? h('button', { type: 'button', class: 'acc-from', style: '--from:' + safeColor(m.color), onclick: who },
                        nameOf(m), admin ? h('span', { class: 'acc-badge', text: 'ADMIN' }) : null, badgeRow(p.badges, true)) : null,
                    reply ? h('div', { class: 'acc-quote' }, h('b', { text: nameOf(reply) }), previewOf(reply).slice(0, 120)) : null,
                    m.hasFile ? mediaOf(m, m.local ? function () { return Promise.resolve(m.local); } : roomOrPostMedia('room', m.id, m.fileName)) : null,
                    m.text ? h('div', { class: 'acc-text', text: m.text }) : null,
                    h('div', { class: 'acc-time', text: (m.pending ? '🕓 ' : '') + (m.expires ? leftText(m.expires) + ' · ' : '') + (pinned && pinned.id === m.id ? '📌 ' : '') + (m.edited ? 'edited · ' : '') + fmtTime(m.created) }),
                    actions));
        }

        function render() {
            var atBottom = forceBottom || feed.scrollHeight - feed.scrollTop - feed.clientHeight < 80;
            forceBottom = false;
            msgs = msgs.filter(function (m) { if (expired(m.expires)) { delete byId[m.id]; return false; } return true; });
            var firstDraw = !Object.keys(seenIds).length;
            while (feed.firstChild) feed.removeChild(feed.firstChild);
            if (!msgs.length) feed.appendChild(h('p', { class: 'acc-muted acc-center', text: 'No messages yet. Say hi!' }));
            var day = null;
            msgs.forEach(function (m, i) {
                var d = new Date(m.created).toDateString();
                if (d !== day) { day = d; feed.appendChild(h('div', { class: 'acc-day' }, h('span', { text: dayLabel(m.created) }))); }
                var b = bubble(m, i > 0 ? msgs[i - 1] : null, msgs[i + 1]);
                if (!seenIds[m.id]) { seenIds[m.id] = 1; if (!firstDraw) b.classList.add('enter'); }
                feed.appendChild(b);
            });
            if (atBottom || !last) feed.scrollTop = feed.scrollHeight;
        }
        function add(list) {
            var changed = false;
            var firstPending = function () { for (var i = 0; i < msgs.length; i++) if (msgs[i].pending) return i; return msgs.length; };
            list.filter(function (m) { return !isBlocked(m.userId) && !expired(m.expires); }).forEach(function (m) {
                if (!byId[m.id]) { byId[m.id] = m; msgs.splice(firstPending(), 0, m); changed = true; }
            });
            var real = msgs.filter(function (m) { return !m.pending; });
            if (real.length) last = real[real.length - 1].id;
            if (changed) render();
        }
        function remove(ids) {
            if (!ids || !ids.length) return;
            var gone = {}; ids.forEach(function (i) { gone[i] = 1; });
            var before = msgs.length;
            msgs = msgs.filter(function (m) { if (gone[m.id]) { delete byId[m.id]; return false; } return true; });
            var real = msgs.filter(function (m) { return !m.pending; });
            last = real.length ? real[real.length - 1].id : null;
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
            }).catch(function (e) { if (!msgs.length) render(); showErr(err, e); })
              .then(function () { if (decodeURIComponent(location.hash.replace('#', '')) === roomId) pollTimer = setTimeout(poll, document.hidden ? 15000 : 3000); });
        }
        feed.appendChild(skel('bubble'));
        poll();
    }

    // a tap shows a message's actions on phones; a mouse shows them on hover
    function toggleActions(e) {
        if (e.target.closest('button, a')) return;
        e.currentTarget.classList.toggle('show');
    }

    // ---- private messages. Friends only, admins can write to anyone. Files come through the server, to the two people in the chat only.
    function newMessage() {
        var s = sheet('New message');
        var search = h('input', { type: 'search', placeholder: 'Search people', 'aria-label': 'Search people' });
        var box = h('div', { class: 'acc-users' }, skel('row', 4));
        s.box.appendChild(h('div', { class: 'acc-field' }, search));
        s.box.appendChild(box);
        var friends = [], found = [], timer = null;
        function row(u) {
            return h('a', { class: 'acc-user-row', href: '#@dm/' + encodeURIComponent(u.id), onclick: function () { s.close(); } },
                avatar(nameOf(u), u.color, 40, u.role === 'admin', u.avatar), h('span', { class: 'acc-room-t' }, h('b', { text: nameOf(u) })));
        }
        function draw() {
            var q = search.value.trim().toLowerCase();
            var shown = friends.filter(function (u) { return !q || nameOf(u).toLowerCase().indexOf(q) >= 0; });
            while (box.firstChild) box.removeChild(box.firstChild);
            if (!shown.length && !found.length) box.appendChild(h('p', { class: 'acc-muted', text: 'No friends yet. Add friends from the Feed page, then you can message them here.' }));
            shown.forEach(function (u) { box.appendChild(row(u)); });
            if (found.length) box.appendChild(h('h3', { text: 'Everyone' }));
            found.forEach(function (u) { box.appendChild(row(u)); });
        }
        search.addEventListener('input', function () {
            draw();
            clearTimeout(timer);
            found = [];
            if (!isAdmin() || search.value.trim().length < 2) return;
            timer = setTimeout(function () {
                call('searchUsers', { q: search.value.trim() }).then(function (r) { found = r.people || []; draw(); }).catch(function () {});
            }, 400);
        });
        call('friends').then(function (r) { friends = r.friends || []; draw(); }).catch(function (e) { box.textContent = e.message; });
    }

    function dmThread(userId) {
        clear();
        var msgs = [], byId = {}, last = null, seenUpTo = '', other = null, urls = {}, seenIds = {}, forceBottom = false;
        var hash = '#@dm/' + encodeURIComponent(userId);
        var title = h('h1', { class: 'acc-room-title', text: '' });
        var status = h('small', { class: 'acc-muted' });
        var face = h('button', { type: 'button', class: 'acc-av-btn', 'aria-label': 'Open the profile', onclick: function () { showProfile(userId); } });
        var feed = h('div', { class: 'acc-feed', 'aria-live': 'polite' });
        var err = errBox();
        var input = h('textarea', { rows: 1, maxlength: 2000, placeholder: 'Message' });
        var send = h('button', { type: 'submit', class: 'acc-send', 'aria-label': 'Send', text: '↑' });
        // the message shows at once and is replaced by the real one when the server has it
        function deliver(text, media) {
            var temp = { id: 'local-' + (++localSeq), from: user.id, to: userId, text: text, created: new Date().toISOString(), pending: true };
            if (media) { temp.hasFile = true; temp.fileType = media.type; temp.fileName = media.name; temp.fileSize = media.size; temp.duration = media.duration; temp.local = media.local; }
            byId[temp.id] = temp; msgs.push(temp); forceBottom = true; render();
            var gone = bar.disappear();
            (media ? media.load() : Promise.resolve(null)).then(function (data) {
                var f = { userId: userId, text: text, expiresIn: gone };
                if (data) { f.file = data; f.fileName = media.name; f.duration = media.duration; }
                return call('dmSend', f);
            }).then(function (r) {
                delete byId[temp.id];
                msgs = msgs.filter(function (m) { return m !== temp; });
                if (r.message && !byId[r.message.id]) {
                    if (media && media.local) { r.message.local = media.local; urls[r.message.id] = media.local; }
                    seenIds[r.message.id] = 1;
                    add([r.message]);
                }
                render();
                showErr(err, null);
            }).catch(function (e) {
                delete byId[temp.id];
                msgs = msgs.filter(function (m) { return m !== temp; });
                render();
                showErr(err, e);
                if (text && !input.value) { input.value = text; bar.tidy(); }
            });
        }
        var bar = chatRow({ input: input, send: send, docs: true, fail: function (e) { showErr(err, e); }, media: function (type, name, load, duration, local, size) {
            deliver('', { type: type, name: name, load: load, duration: duration, local: local, size: size });
        } });
        var composer = h('form', { class: 'acc-composer', onsubmit: function (ev) {
            ev.preventDefault();
            var t = input.value.trim(); if (!t) return;
            input.value = ''; bar.tidy(); showErr(err, null);
            deliver(t);
            input.focus();
        } }, bar.row, bar.recBar, bar.file);
        var closed = h('p', { class: 'acc-muted acc-small acc-center', hidden: true, text: '🔒 You can only send private messages to friends.' });
        input.addEventListener('keydown', function (e) {
            if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); composer.requestSubmit ? composer.requestSubmit() : send.click(); }
        });
        root.appendChild(h('div', { class: 'acc-room-head' }, h('a', { href: '#@messages', class: 'acc-back', 'aria-label': 'Back to messages', text: '‹' }), face,
            h('span', { class: 'acc-room-t' }, title, status)));
        root.appendChild(feed); root.appendChild(err); root.appendChild(composer); root.appendChild(closed);

        function fetchFile(m) {
            if (urls[m.id]) return Promise.resolve(urls[m.id]);
            return call('dmFile', { id: m.id }).then(function (r) {
                return (urls[m.id] = blobFrom(r.data, r.name || m.fileName));
            });
        }
        function attachment(m) {
            var media = mediaOf(m, m.local ? function () { return Promise.resolve(m.local); } : function () { return fetchFile(m); });
            if (media) return media;
            var size = m.fileSize >= 1000000 ? (m.fileSize / 1000000).toFixed(1) + ' MB' : Math.max(1, Math.round(m.fileSize / 1000)) + ' KB';
            return h('button', { type: 'button', class: 'acc-dm-file', onclick: function (ev) {
                var b = ev.currentTarget; b.disabled = true;
                fetchFile(m).then(function (u) {
                    var a = h('a', { href: u, download: m.fileName }); document.body.appendChild(a); a.click(); a.remove();
                }).catch(function (e) { showErr(err, e); }).then(function () { b.disabled = false; });
            } }, h('span', { class: 'acc-emoji', text: m.fileType === 'pdf' ? '📕' : '📘' }),
                h('span', { class: 'acc-room-t' }, h('b', { text: m.fileName }), h('small', { text: size })));
        }
        function bubble(m, prev, next) {
            var mine = m.from === user.id;
            var same = function (o) { return o && o.from === m.from && new Date(o.created).toDateString() === new Date(m.created).toDateString(); };
            var actions = m.pending ? null : h('span', { class: 'acc-msg-actions' },
                mine ? h('button', { type: 'button', text: 'Delete for everyone', onclick: function () {
                    if (!window.confirm('Delete this message for everyone?')) return;
                    // gone at once for me; the other person's screen removes it on its next check
                    remove([m.id]);
                    call('dmDelete', { id: m.id }).catch(function (e) { byId[m.id] = m; msgs.push(m); msgs.sort(function (a, b) { return a.created < b.created ? -1 : 1; }); render(); showErr(err, e); });
                } }) : h('button', { type: 'button', text: 'Report', onclick: function () { reportSheet('dm', m.id, (m.text || m.fileName || '').slice(0, 120)); } }),
                (!mine && other && other.role !== 'admin') ? h('button', { type: 'button', text: 'Block', onclick: function () { blockPerson(userId, nameOf(other), function () { location.hash = '@messages'; }); } }) : null);
            return h('div', { class: 'acc-msg' + (mine ? ' mine' : '') + (same(prev) ? ' grouped' : '') + (same(next) ? ' below' : '') + (m.pending ? ' pending' : ''), 'data-id': m.id },
                h('div', { class: 'acc-bubble', onclick: toggleActions },
                    m.hasFile ? attachment(m) : null,
                    m.text ? h('div', { class: 'acc-text', text: m.text }) : null,
                    h('div', { class: 'acc-time', text: (m.pending ? '🕓 ' : '') + (m.expires ? leftText(m.expires) + ' · ' : '') + fmtTime(m.created) }),
                    actions));
        }
        function render() {
            var atBottom = forceBottom || feed.scrollHeight - feed.scrollTop - feed.clientHeight < 80;
            forceBottom = false;
            msgs = msgs.filter(function (m) { if (expired(m.expires)) { delete byId[m.id]; return false; } return true; });
            var firstDraw = !Object.keys(seenIds).length;
            while (feed.firstChild) feed.removeChild(feed.firstChild);
            if (!msgs.length) feed.appendChild(h('div', { class: 'acc-empty' }, other ? avatar(nameOf(other), other.color, 72, false, other.avatar) : null,
                h('b', { text: other ? nameOf(other) : '' }), h('p', { class: 'acc-muted', text: 'Say hello. Only the two of you see this chat.' })));
            var day = null, seen = null;
            if (seenUpTo) msgs.forEach(function (m) { if (m.from === user.id && m.created <= seenUpTo) seen = m.id; });
            msgs.forEach(function (m, i) {
                var d = new Date(m.created).toDateString();
                if (d !== day) { day = d; feed.appendChild(h('div', { class: 'acc-day' }, h('span', { text: dayLabel(m.created) }))); }
                var b = bubble(m, msgs[i - 1], msgs[i + 1]);
                if (!seenIds[m.id]) { seenIds[m.id] = 1; if (!firstDraw) b.classList.add('enter'); }
                feed.appendChild(b);
                if (m.id === seen) feed.appendChild(h('div', { class: 'acc-seen', text: 'Seen' }));
            });
            if (atBottom || !last) feed.scrollTop = feed.scrollHeight;
        }
        function add(list) {
            var changed = false;
            var firstPending = function () { for (var i = 0; i < msgs.length; i++) if (msgs[i].pending) return i; return msgs.length; };
            list.filter(function (m) { return !expired(m.expires); }).forEach(function (m) { if (!byId[m.id]) { byId[m.id] = m; msgs.splice(firstPending(), 0, m); changed = true; } });
            var real = msgs.filter(function (m) { return !m.pending; });
            if (real.length) last = real[real.length - 1].id;
            if (changed) render();
            return changed;
        }
        function remove(ids) {
            if (!ids || !ids.length) return;
            var gone = {}; ids.forEach(function (i) { gone[i] = 1; });
            var before = msgs.length;
            msgs = msgs.filter(function (m) { if (gone[m.id]) { delete byId[m.id]; return false; } return true; });
            var real = msgs.filter(function (m) { return !m.pending; });
            last = real.length ? real[real.length - 1].id : null;
            if (msgs.length !== before) render();
        }
        var first = true;
        feed.appendChild(skel('bubble'));
        function poll() {
            var f = { userId: userId }; if (last) f.after = last;
            call('dmThread', f).then(function (r) {
                if (r.user) {
                    other = r.user;
                    title.textContent = nameOf(other);
                    status.textContent = lastSeenText(other.lastSeen, other.online);
                    while (face.firstChild) face.removeChild(face.firstChild);
                    face.appendChild(avatar(nameOf(other), other.color, 36, other.role === 'admin', other.avatar, other.online == null ? null : !!other.online));
                }
                if (r.canSend != null) { composer.hidden = !r.canSend; closed.hidden = !!r.canSend; }
                var seenChanged = (r.seenUpTo || seenUpTo) !== seenUpTo;
                seenUpTo = r.seenUpTo || seenUpTo;
                remove(r.deleted);
                var added = add(r.messages || []);
                if ((seenChanged && !added) || first) render();
                first = false;
                showErr(err, null);
            }).catch(function (e) { showErr(err, e); })
              .then(function () {
                  if (location.hash === hash) pollTimer = setTimeout(poll, document.hidden ? 15000 : 3000);
              });
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

        var roomBox = h('div', { class: 'acc-rooms' }, skel('row', 3));
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
            if (!window.confirm('Delete this file from Drive and UniHub?')) return;
            busy(delBtn, true, 'Delete upload');
            call('adminDeleteUpload', { fileId: m[1] }).then(function () { link.value = ''; delBtn.textContent = 'Deleted ✓'; delBtn.disabled = false; })
                .catch(function (e) { showErr(err, e); busy(delBtn, false, 'Delete upload'); });
        } }, h('h2', { text: 'Delete a file' }), h('p', { class: 'acc-muted acc-small', text: 'It goes to the Drive trash and disappears from UniHub for everyone. A file owned by another Drive account is only removed from UniHub.' }), link, delBtn));

        var repBox = h('div', { class: 'acc-log' }, skel('row', 3));
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

        var sugBox = h('div', { class: 'acc-log' }, skel('row', 3));
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

        var logBox = h('div', { class: 'acc-log' }, skel('row', 3));
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
            h('span', { class: 'acc-post-who' }, h('b', { text: nameOf(u) }), h('small', { title: a[1], text: ago(p.created) + ' · ' + a[0] + (p.expires ? ' · ' + leftText(p.expires) : '') }))));
        if (p.text) card.appendChild(h('p', { class: 'acc-post-text', text: p.text }));
        if (safePhoto(p.image)) card.appendChild(h('a', { href: bigPhoto(p.image), target: '_blank', rel: 'noopener noreferrer' },
            h('img', { class: 'acc-post-img', src: safePhoto(p.image), alt: 'Photo in the post', loading: 'lazy', referrerpolicy: 'no-referrer', onerror: function (e) { e.currentTarget.remove(); } })));
        var postVid = driveId(p.video);
        if (postVid) {
            var vt = h('button', { type: 'button', class: 'acc-video-tile wide', 'aria-label': 'Play video' }, h('span', { text: '▶' }));
            vt.addEventListener('click', function () { vt.replaceWith(h('iframe', { class: 'acc-post-video', src: 'https://drive.google.com/file/d/' + postVid + '/preview', title: 'Video', allow: 'autoplay; fullscreen', referrerpolicy: 'no-referrer' })); });
            card.appendChild(vt);
        }
        if (p.hasVoice) card.appendChild(voiceBubble(p.id, p.duration, roomOrPostMedia('post', p.id, 'voice.m4a')));
        if (!(token && user)) {
            card.appendChild(h('p', { class: 'acc-post-meta', text: '♥ ' + (p.likes || 0) + '   💬 ' + (p.comments || 0) }));
            return card;
        }
        var likeBusy = false;
        var like = h('button', { type: 'button', class: 'acc-post-act', onclick: function () {
            if (likeBusy) return;
            likeBusy = true;
            var was = [p.liked, p.likes];
            p.liked = !p.liked; p.likes = Math.max(0, (p.likes || 0) + (p.liked ? 1 : -1));
            drawLike();
            call('postLike', { id: p.id, on: p.liked }).then(function (r) { p.likes = r.likes; p.liked = r.liked; })
                .catch(function () { p.liked = was[0]; p.likes = was[1]; })
                .then(function () { likeBusy = false; drawLike(); });
        } });
        function drawLike() {
            like.textContent = (p.liked ? '♥ ' : '♡ ') + (p.likes || 0);
            like.className = 'acc-post-act' + (p.liked ? ' on' : '');
            like.setAttribute('aria-pressed', p.liked ? 'true' : 'false');
            like.setAttribute('aria-label', (p.liked ? 'Remove like. ' : 'Like. ') + (p.likes || 0) + ' likes');
        }
        drawLike();
        var talk = h('button', { type: 'button', class: 'acc-post-act', 'aria-label': (p.comments || 0) + ' comments', text: '💬 ' + (p.comments || 0), onclick: function () {
            commentsSheet(p, function (n) { p.comments = n; talk.textContent = '💬 ' + n; talk.setAttribute('aria-label', n + ' comments'); });
        } });
        var row = h('div', { class: 'acc-post-acts' }, like, talk, h('span', { class: 'acc-spacer' }));
        if (u.id !== user.id) row.appendChild(h('button', { type: 'button', class: 'acc-post-act small', text: 'Report', onclick: function () { reportSheet('post', p.id, nameOf(u)); } }));
        if (u.id === user.id || isAdmin()) row.appendChild(h('button', { type: 'button', class: 'acc-post-act small', text: 'Delete', onclick: function (e) {
            if (!window.confirm('Delete this post?')) return;
            var b = e.currentTarget, parent = card.parentNode, after = card.nextSibling;
            card.remove();
            call('postDelete', { id: p.id }).catch(function (x) { if (parent) parent.insertBefore(card, after); b.textContent = x.message; });
        } }));
        card.appendChild(row);
        return card;
    }
    function commentsSheet(p, changed) {
        var s = sheet('Comments');
        var list = h('div', { class: 'acc-comments' }, skel('row', 3));
        var input = h('textarea', { rows: '1', maxlength: '500', placeholder: 'Write a comment', 'aria-label': 'Write a comment' });
        var send = h('button', { type: 'submit', class: 'acc-send', 'aria-label': 'Send', text: '↑' });
        var err = errBox();
        var count = p.comments || 0;
        function line(c) {
            var u = c.user || {};
            var mine = !!user && u.id === user.id;
            var acts = h('span', { class: 'acc-comment-acts' });
            if (mine || isAdmin() || (p.user && p.user.id === user.id)) acts.appendChild(h('button', { type: 'button', text: 'Delete', onclick: function (e) {
                if (!window.confirm('Delete this comment?')) return;
                row.remove();
                count = Math.max(0, count - 1); changed(count);
                call('commentDelete', { id: c.id }).then(load).catch(function (x) { showErr(err, x); load(); });
            } }));
            if (!mine) acts.appendChild(h('button', { type: 'button', text: 'Report', onclick: function () { reportSheet('comment', c.id, nameOf(u)); } }));
            var row = h('div', { class: 'acc-comment' + (c.pending ? ' pending' : '') },
                h('button', { type: 'button', class: 'acc-comment-av', 'aria-label': 'Open the profile of ' + nameOf(u), onclick: function () { showProfile(u.id, nameOf(u)); } }, avatar(nameOf(u), u.color, 34, u.role === 'admin', u.avatar)),
                h('div', { class: 'acc-comment-t' },
                    h('div', { class: 'acc-comment-top' }, h('b', { text: nameOf(u) }), h('small', { text: ago(c.created) })),
                    h('p', { text: c.text }), c.pending ? null : acts));
            return row;
        }
        function load() {
            return call('comments', { postId: p.id }).then(function (r) {
                var got = r.comments || [];
                while (list.firstChild) list.removeChild(list.firstChild);
                if (!got.length) list.appendChild(h('p', { class: 'acc-muted acc-center', text: 'No comments yet. Be the first.' }));
                got.forEach(function (c) { list.appendChild(line(c)); });
                count = got.length; changed(count);
                list.scrollTop = list.scrollHeight;
            }).catch(function (e) { list.textContent = e.message; });
        }
        var form = h('form', { class: 'acc-compose-row acc-comment-form', onsubmit: function (e) {
            e.preventDefault();
            var text = input.value.trim();
            if (!text) return;
            showErr(err);
            // the comment shows at once; only a failure brings the words back
            input.value = '';
            var empty = list.querySelector('.acc-center');
            if (empty) empty.remove();
            var temp = line({ id: 'local', text: text, created: new Date().toISOString(), user: user, pending: true });
            temp.classList.add('enter');
            list.appendChild(temp);
            list.scrollTop = list.scrollHeight;
            count++; changed(count);
            call('commentAdd', { postId: p.id, text: text }).then(load).catch(function (x) {
                temp.remove(); count--; changed(count);
                showErr(err, x);
                if (!input.value) input.value = text;
            });
            input.focus();
        } }, input, send);
        input.addEventListener('keydown', function (e) { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); form.requestSubmit(); } });
        s.box.appendChild(list); s.box.appendChild(err); s.box.appendChild(form);
        load();
    }
    // a picked photo as a JPEG of at most 1280 px wide (base64, no data: prefix), the same size the apps send
    function postPhotoData(file) {
        return new Promise(function (ok, bad) {
            var img = new Image(), url = URL.createObjectURL(file);
            img.onload = function () {
                var k = Math.min(1, 1280 / img.width), c = document.createElement('canvas');
                c.width = Math.round(img.width * k); c.height = Math.round(img.height * k);
                c.getContext('2d').drawImage(img, 0, 0, c.width, c.height);
                URL.revokeObjectURL(url);
                var d = c.toDataURL('image/jpeg', .82).split(',')[1];
                if (d.length > 2400000) d = c.toDataURL('image/jpeg', .6).split(',')[1];
                ok(d);
            };
            img.onerror = function () { URL.revokeObjectURL(url); bad(new Error("That file isn't a photo we can read.")); };
            img.src = url;
        });
    }
    function composeSheet(done) {
        var s = sheet('New post');
        var text = h('textarea', { rows: '4', maxlength: '1000', placeholder: "What's on your mind?", 'aria-label': 'Post text' });
        var pickInput = h('input', { type: 'file', accept: 'image/jpeg,image/png,image/webp,video/mp4,video/quicktime,video/webm', hidden: true });
        var preview = h('img', { class: 'acc-post-img', alt: 'Photo you picked', hidden: true });
        var previewVideo = h('video', { class: 'acc-video', controls: true, playsinline: true, hidden: true });
        var photo = null, video = null, previewUrl = null, voice = null, rec = null, streamNow = null, started = 0, cancelled = false;
        var pickBtn = h('button', { type: 'button', class: 'ab-btn gray', text: 'Add a photo or video', onclick: function () { pickInput.click(); } });
        var dropBtn = h('button', { type: 'button', class: 'ab-btn gray', text: 'Remove', hidden: true, onclick: function () { setMedia(null); } });
        var voiceBtn = h('button', { type: 'button', class: 'ab-btn gray', text: '🎤 Record a voice message', onclick: toggleVoice });
        var voiceBox = h('div', { hidden: true });
        var who = h('select', { 'aria-label': 'Who can see this' });
        ['public', 'students', 'friends'].forEach(function (k) { who.appendChild(h('option', { value: k, text: AUDIENCE[k][0] + ' ' + AUDIENCE[k][1] })); });
        who.value = AUDIENCE[get('ab.audience')] ? get('ab.audience') : 'public';
        var gone = disappearPick();
        var err = errBox();
        var post = h('button', { type: 'submit', class: 'ab-btn', text: 'Post' });
        function setMedia(f) {
            if (previewUrl) URL.revokeObjectURL(previewUrl);
            var isVideo = !!f && /^video\//.test(f.type);
            photo = f && !isVideo ? f : null; video = isVideo ? f : null;
            previewUrl = f ? URL.createObjectURL(f) : null;
            if (photo) preview.src = previewUrl; else preview.removeAttribute('src');
            if (video) previewVideo.src = previewUrl; else previewVideo.removeAttribute('src');
            preview.hidden = !photo; previewVideo.hidden = !video; dropBtn.hidden = !f;
            pickBtn.textContent = f ? 'Change' : 'Add a photo or video';
        }
        function finishRec() { if (streamNow) streamNow.getTracks().forEach(function (t) { t.stop(); }); streamNow = null; voiceBtn.textContent = '🎤 Record a voice message'; }
        function toggleVoice() {
            if (rec && rec.state === 'recording') { rec.stop(); return; }
            if (voice) { voice = null; while (voiceBox.firstChild) voiceBox.removeChild(voiceBox.firstChild); voiceBox.hidden = true; voiceBtn.textContent = '🎤 Record a voice message'; return; }
            if (!window.MediaRecorder) { showErr(err, new Error("This browser can't record voice messages.")); return; }
            navigator.mediaDevices.getUserMedia({ audio: true }).then(function (stream) {
                streamNow = stream;
                var mp4 = MediaRecorder.isTypeSupported && MediaRecorder.isTypeSupported('audio/mp4');
                rec = new MediaRecorder(stream, mp4 ? { mimeType: 'audio/mp4', audioBitsPerSecond: 32000 } : { audioBitsPerSecond: 32000 });
                var chunks = [];
                started = Date.now(); cancelled = false;
                rec.ondataavailable = function (e) { if (e.data && e.data.size) chunks.push(e.data); };
                rec.onstop = function () {
                    finishRec();
                    if (cancelled) return;
                    var blob = new Blob(chunks, { type: rec.mimeType || (mp4 ? 'audio/mp4' : 'audio/webm') });
                    var length = Math.max(1, Math.round((Date.now() - started) / 1000));
                    voice = { blob: blob, name: mp4 ? 'voice.m4a' : 'voice.weba', duration: length };
                    voiceBox.hidden = false;
                    voiceBox.appendChild(voiceBubble('new' + Date.now(), length, function () { return Promise.resolve(URL.createObjectURL(blob)); }));
                    voiceBtn.textContent = 'Remove voice message';
                };
                rec.start();
                voiceBtn.textContent = '⏹ Stop recording';
                setTimeout(function () { if (rec && rec.state === 'recording') rec.stop(); }, 180000);
            }).catch(function () { showErr(err, new Error("UniHub can't use the microphone. You can allow it in your browser's settings.")); });
        }
        pickInput.addEventListener('change', function () {
            var f = pickInput.files[0]; pickInput.value = '';
            if (!f) return;
            if (/^video\//.test(f.type) && f.size > 12000000) { showErr(err, new Error('That video is too big. The limit is 12 MB.')); return; }
            showErr(err); setMedia(f);
        });
        s.box.appendChild(h('form', { class: 'acc-compose-form', onsubmit: function (e) {
            e.preventDefault();
            var words = text.value.trim();
            if (rec && rec.state === 'recording') { showErr(err, new Error('Stop the recording first.')); return; }
            if (!words && !photo && !video && !voice) { showErr(err, new Error('Write something first.')); return; }
            set('ab.audience', who.value);
            // the sheet closes straight away; the upload carries on in the background
            var f = { text: words, audience: who.value, expiresIn: Number(gone.value) || 0 };
            var pic = photo, clip = video, sound = voice;
            addJob('Publishing your post…', function () {
                return (pic ? postPhotoData(pic) : Promise.resolve(null)).then(function (d) {
                    if (d) f.image = d;
                    return clip ? fileBase64(clip) : null;
                }).then(function (d) {
                    if (d) f.video = d;
                    return sound ? fileBase64(sound.blob) : null;
                }).then(function (d) {
                    if (d) { f.file = d; f.fileName = sound.name; f.duration = sound.duration; }
                    return call('postCreate', f);
                });
            }, done);
            s.close();
        } }, text, preview, previewVideo, pickInput,
            h('div', { class: 'acc-actions' }, pickBtn, dropBtn, voiceBtn), voiceBox,
            h('label', { class: 'acc-field' }, h('span', { text: 'Who can see this' }), who),
            h('label', { class: 'acc-field' }, h('span', { text: 'Disappears after' }), gone),
            err, post));
        text.focus();
    }
    // the add / cancel / accept / unfriend buttons for one person
    function friendControls(u, state, changed) {
        var box = h('span', { class: 'acc-fr-btns' });
        function go(action, fields) {
            // the button changes straight away with a little bounce; only a failure puts it back
            var before = state;
            state = action === 'friendRequest' ? 'outgoing' : action === 'friendRespond' ? (fields && fields.accept ? 'friends' : 'none') : 'none';
            draw(true);
            call(action, Object.assign({ userId: u.id }, fields || {})).then(function (r) {
                var now = r.friendship || state;
                if (now !== state) { state = now; draw(); }
                if (changed) changed(state);
            }).catch(function (e) {
                state = before;
                draw();
                box.appendChild(h('small', { class: 'acc-err', role: 'alert', text: e.message }));
            });
        }
        var popNext = false;
        function btn(label, cls, fn) { box.appendChild(h('button', { type: 'button', class: 'ab-btn ' + cls + (popNext ? ' acc-pop' : ''), text: label, onclick: fn })); }
        function draw(pop) {
            popNext = !!pop;
            while (box.firstChild) box.removeChild(box.firstChild);
            if (state === 'none') btn('Add friend', '', function () { go('friendRequest'); });
            else if (state === 'outgoing') btn('Cancel request', 'gray', function () { go('friendRemove'); });
            else if (state === 'incoming') {
                btn('Accept', '', function () { go('friendRespond', { accept: true }); });
                btn('Decline', 'gray', function () { go('friendRespond', { accept: false }); });
            } else if (state === 'friends') btn('Unfriend', 'gray', function () {
                if (window.confirm('Remove ' + nameOf(u) + ' from your friends?')) go('friendRemove');
            });
        }
        draw();
        return box;
    }
    function friendsSheet(changed) {
        var s = sheet('Friends');
        var search = h('input', { type: 'search', placeholder: 'Search students', 'aria-label': 'Search students' });
        var box = h('div', { class: 'acc-users' }, skel('row', 4));
        s.box.appendChild(h('div', { class: 'acc-field' }, search));
        s.box.appendChild(box);
        var data = { friends: [], incoming: [], outgoing: [] }, tips = [], found = null, timer = null;
        function row(u, state) {
            return h('div', { class: 'acc-user-row acc-fr-row' },
                h('button', { type: 'button', class: 'acc-fr-who', 'aria-label': 'Open the profile of ' + nameOf(u), onclick: function () { showProfile(u.id, nameOf(u)); } },
                    avatar(nameOf(u), u.color, 40, u.role === 'admin', u.avatar, state === 'friends' ? !!u.online : null),
                    h('span', { class: 'acc-room-t' }, h('b', { text: nameOf(u) }), u.major || u.college ? h('small', { text: u.major || u.college }) : null)),
                friendControls(u, state, load));
        }
        function part(title, people, state) {
            if (!people.length) return;
            box.appendChild(h('h3', { text: title }));
            people.forEach(function (u) { box.appendChild(row(u, state || u.friendship || 'none')); });
        }
        function draw() {
            while (box.firstChild) box.removeChild(box.firstChild);
            if (found) {
                if (!found.length) box.appendChild(h('p', { class: 'acc-muted acc-center', text: 'Nobody found.' }));
                part('Results', found);
                return;
            }
            part('Friend requests', data.incoming, 'incoming');
            part('Your friends', data.friends, 'friends');
            part('Requests you sent', data.outgoing, 'outgoing');
            part('People you may know', tips);
            if (!box.firstChild) box.appendChild(h('p', { class: 'acc-muted acc-center', text: 'No friends yet. Search for students above.' }));
        }
        function runSearch() {
            var q = search.value.trim();
            if (q.length < 2) { found = null; draw(); return Promise.resolve(); }
            return call('searchUsers', { q: q }).then(function (r) { if (search.value.trim() === q) { found = r.people || []; draw(); } }).catch(function () {});
        }
        function load() {
            if (changed) changed();
            Promise.all([call('friends'), call('searchUsers', { q: '' }).catch(function () { return {}; })]).then(function (a) {
                data = a[0]; tips = a[1].people || [];
                return found ? runSearch() : draw();
            }).catch(function (e) { box.textContent = e.message; });
        }
        search.addEventListener('input', function () {
            clearTimeout(timer);
            timer = setTimeout(runSearch, 400);
        });
        load();
    }
    function driveId(u) { var m = /[?&]id=([\w-]+)/.exec(u || ''); return m ? m[1] : ''; }
    // the text of a story with its font, colour, effect and motion
    function storyText(item) {
        var st = {};
        try { st = JSON.parse(item.style || '{}') || {}; } catch (e) {}
        var cls = ['acc-story-text', 'f-' + String(st.font || 'classic').replace(/\W/g, ''), 'e-' + String(st.effect || 'shadow').replace(/\W/g, ''), 'a-' + String(st.anim || 'none').replace(/\W/g, '')];
        var c = safeColor(st.color || '#FFFFFF');
        var p = h('p', { class: cls.join(' '), style: '--c:' + c + ';text-align:' + (st.align === 'left' || st.align === 'right' ? st.align : 'center') });
        p.appendChild(h('span', { text: item.text }));
        return p;
    }
    function storyBackground(item) {
        var st = {};
        try { st = JSON.parse(item.style || '{}') || {}; } catch (e) {}
        var a = safeColor(item.bg);
        return st.bg2 && /^#[0-9a-fA-F]{3,8}$/.test(st.bg2) ? 'linear-gradient(135deg,' + a + ',' + st.bg2 + ')' : a;
    }
    // who saw a story, with the exact date and time
    function storyViewers(id) {
        var s = sheet('Viewers');
        var list = h('div', { class: 'acc-users' }, skel('row', 4));
        s.box.appendChild(list);
        call('storyViewers', { id: id }).then(function (r) {
            while (list.firstChild) list.removeChild(list.firstChild);
            var got = r.viewers || [];
            if (!got.length) list.appendChild(h('p', { class: 'acc-muted acc-center', text: 'No views yet.' }));
            got.forEach(function (v) {
                var d = new Date(v.at);
                list.appendChild(h('button', { type: 'button', class: 'acc-room', onclick: function () { s.close(); showProfile(v.user.id, nameOf(v.user)); } },
                    avatar(nameOf(v.user), v.user.color, 40, v.user.role === 'admin', v.user.avatar),
                    h('span', { class: 'acc-room-t' }, h('b', { text: nameOf(v.user) }), h('small', { text: isNaN(d) ? '' : d.toLocaleString([], { dateStyle: 'long', timeStyle: 'medium' }) }))));
            });
        }).catch(function (e) { list.textContent = e.message; });
    }
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
            stage.style.background = (item.image || vid) ? '#000' : storyBackground(item);
            stage.classList.remove('fade'); void stage.offsetWidth; stage.classList.add('fade');
            if (vid) stage.appendChild(h('iframe', { src: 'https://drive.google.com/file/d/' + vid + '/preview', title: 'Story video', allow: 'autoplay; fullscreen', referrerpolicy: 'no-referrer' }));
            else if (safePhoto(item.image)) stage.appendChild(h('img', { src: bigPhoto(item.image), alt: '', referrerpolicy: 'no-referrer' }));
            if (item.text) stage.appendChild(storyText(item));
            if (item.views != null && user && u.id === user.id) stage.appendChild(h('button', { type: 'button', class: 'acc-story-views', text: '👁 ' + item.views + ' views', onclick: function () { clearTimeout(timer); storyViewers(item.id); } }));
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
            var frBadge = h('span', { class: 'acc-fr-badge', hidden: true });
            var frCount = function () {
                call('friends').then(function (r) {
                    var n = (r.incoming || []).length;
                    frBadge.textContent = String(n); frBadge.hidden = !n;
                }).catch(function () {});
            };
            root.appendChild(h('div', { class: 'acc-card acc-post-box' },
                avatar(nameOf(user), user.color, 40, user.role === 'admin', user.avatar),
                h('button', { type: 'button', class: 'acc-post-open', text: "What's on your mind?", onclick: function () {
                    composeSheet(function () { pickScope('friends'); });
                } }),
                h('button', { type: 'button', class: 'ab-btn gray acc-fr-open', onclick: function () { friendsSheet(frCount); } }, 'Friends', frBadge)));
            frCount();
            var seg = h('div', { class: 'acc-seg', role: 'tablist' });
            [['friends', 'Friends'], ['strangers', 'Strangers'], ['guest', 'Guest']].forEach(function (o) {
                seg.appendChild(h('button', { type: 'button', role: 'tab', 'aria-selected': o[0] === scope ? 'true' : 'false', class: o[0] === scope ? 'on' : '', text: o[1], onclick: function () { pickScope(o[0]); } }));
            });
            root.appendChild(seg);
            root.appendChild(h('p', { class: 'acc-muted acc-small', text: 'To add a story, use the UniHub app.' }));
        } else {
            root.appendChild(h('div', { class: 'acc-card' }, h('b', { text: "You're browsing as a guest" }),
                h('p', { class: 'acc-muted', text: 'You see what students share with everyone. Sign in for more.' }),
                h('a', { class: 'ab-btn', href: '/account/', text: 'Sign in or create account' })));
        }
        var list = h('div', { 'aria-live': 'polite' });
        var moreBtn = h('button', { type: 'button', class: 'ab-btn gray acc-more', text: 'Load more', hidden: true, onclick: function () { posts(false); } });
        root.appendChild(list); root.appendChild(moreBtn);

        function pickScope(next) {
            scope = next; set('ab.feed.scope', scope);
            Array.prototype.forEach.call(seg.children, function (b) { var on = b === seg.children[['friends', 'strangers', 'guest'].indexOf(scope)]; b.className = on ? 'on' : ''; b.setAttribute('aria-selected', on ? 'true' : 'false'); });
            posts(true);
        }
        function posts(fresh) {
            var mine = ++loadToken;
            if (fresh) {
                offset = 0;
                while (list.firstChild) list.removeChild(list.firstChild);
                var saved = cacheGet('feed.' + scope);
                if (saved && saved.length) saved.forEach(function (p) { list.appendChild(postCard(p)); });
                else list.appendChild(skel('post', 3));
                moreBtn.hidden = true;
            }
            call(signedIn ? 'feedAll' : 'publicFeed', { scope: scope, offset: offset }).then(function (r) {
                if (mine !== loadToken) return;
                if (fresh) while (list.firstChild) list.removeChild(list.firstChild);
                var got = r.posts || [];
                if (fresh) cachePut('feed.' + scope, got.slice(0, 12));
                if (fresh && !got.length) list.appendChild(h('p', { class: 'acc-muted acc-center', text:
                    scope === 'friends' ? 'No posts from friends yet.' : scope === 'strangers' ? 'No posts from other students yet.' : 'No public posts yet.' }));
                got.forEach(function (p) { list.appendChild(postCard(p)); });
                offset += got.length;
                moreBtn.hidden = got.length < 25;
            }).catch(function (e) {
                if (mine !== loadToken) return;
                if (!token && signedIn) return feedView();
                if (fresh && !cacheGet('feed.' + scope)) { while (list.firstChild) list.removeChild(list.firstChild); }
                list.insertBefore(h('p', { class: 'acc-err', role: 'alert', text: e.message }), list.firstChild);
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

    // subject swap: the list is public; contact details load only for a signed-in student who opens a listing
    function swapView() {
        root.innerHTML = '';
        var swaps = [], listBox = h('div', {}, skel('row', 4));
        var search = h('input', { type: 'search', placeholder: 'Search subjects', 'aria-label': 'Search subjects' });
        search.addEventListener('input', draw);
        root.appendChild(h('div', { class: 'acc-card' },
            h('h2', { text: 'Subject swap' }),
            h('p', { class: 'acc-muted', text: 'Trade a registered subject with another student. Say what you have and what you want.' }),
            h('button', { type: 'button', class: 'ab-btn acc-wide', text: 'Post a swap', onclick: function () {
                if (!token || !user) { location.href = '/account/'; return; }
                addSwap();
            } }),
            search));
        root.appendChild(listBox);
        function same(a, b) { return a.toLowerCase() === b.toLowerCase(); }
        function match(s, openMine) { return openMine.some(function (m) { return same(m.have, s.want) && same(m.want, s.have); }); }
        function row(s, isMatch) {
            return h('button', { type: 'button', class: 'acc-card acc-swap', onclick: function () { detail(s); } },
                isMatch ? h('small', { class: 'acc-swap-match', text: 'Matches your swap' }) : null,
                s.status === 'done' ? h('small', { class: 'acc-muted', text: 'Done' }) : null,
                h('strong', { text: s.have + '  ⇄  ' + s.want }),
                h('small', { class: 'acc-muted', text: [s.name, s.major, s.section].filter(Boolean).join(' · ') }));
        }
        function draw() {
            var q = search.value.trim().toLowerCase();
            var mine = swaps.filter(function (s) { return s.mine; });
            var openMine = mine.filter(function (s) { return s.status === 'open'; });
            var others = swaps.filter(function (s) { return !s.mine && (!q || (s.have + ' ' + s.want + ' ' + s.major).toLowerCase().indexOf(q) >= 0); });
            others.sort(function (a, b) { return match(b, openMine) - match(a, openMine); });
            listBox.innerHTML = '';
            if (mine.length) {
                listBox.appendChild(h('h3', { class: 'acc-swap-head', text: 'Your swaps' }));
                mine.forEach(function (s) { listBox.appendChild(row(s, false)); });
            }
            listBox.appendChild(h('h3', { class: 'acc-swap-head', text: 'Students offering' }));
            if (!others.length) listBox.appendChild(h('p', { class: 'acc-muted', text: 'No swaps here yet.' }));
            others.forEach(function (s) { listBox.appendChild(row(s, match(s, openMine))); });
        }
        function detail(s) {
            var d = sheet('Subject swap'), err = h('p', { class: 'acc-err', role: 'alert' });
            d.box.appendChild(h('p', {}, h('strong', { text: 'Has: ' }), s.have));
            d.box.appendChild(h('p', {}, h('strong', { text: 'Wants: ' }), s.want));
            if (s.section) d.box.appendChild(h('p', {}, h('strong', { text: 'Section or time: ' }), s.section));
            if (s.note) d.box.appendChild(h('p', { text: s.note }));
            if (!token || !user) {
                d.box.appendChild(h('p', { class: 'acc-muted' }, h('a', { href: '/account/', text: 'Sign in' }), ' to see this student\'s contact details.'));
            } else {
                var c = h('div', {}, dots());
                d.box.appendChild(c);
                call('swapContact', { id: s.id }).then(function (r) {
                    var k = r.contact || {};
                    c.innerHTML = '';
                    c.appendChild(h('p', {}, h('strong', { text: 'Name: ' }), k.fullName || ''));
                    if (k.studentId) c.appendChild(h('p', {}, h('strong', { text: 'University ID: ' }), k.studentId));
                    if (k.major) c.appendChild(h('p', {}, h('strong', { text: 'Major: ' }), k.major));
                    if (k.phone) c.appendChild(h('p', {}, h('strong', { text: 'Phone: ' }), h('a', { href: 'tel:' + k.phone.replace(/[^0-9+]/g, ''), text: k.phone })));
                    if (k.email) c.appendChild(h('p', {}, h('strong', { text: 'Email: ' }), h('a', { href: 'mailto:' + encodeURIComponent(k.email).replace('%40', '@'), text: k.email })));
                }).catch(function (e) { c.innerHTML = ''; showErr(err, e); });
            }
            d.box.appendChild(err);
            if (s.mine || (user && user.role === 'admin')) {
                function set(status) {
                    if (status === 'deleted') swaps = swaps.filter(function (x) { return x.id !== s.id; }); else s.status = status;
                    d.close(); draw();
                    call('swapSet', { id: s.id, status: status }).catch(function () {});
                }
                d.box.appendChild(h('div', { class: 'acc-actions' },
                    h('button', { type: 'button', class: 'ab-btn', text: s.status === 'open' ? 'Mark as done' : 'Open again', onclick: function () { set(s.status === 'open' ? 'done' : 'open'); } }),
                    h('button', { type: 'button', class: 'ab-btn acc-danger', text: 'Delete', onclick: function () { set('deleted'); } })));
            }
        }
        function addSwap() {
            var d = sheet('Post a swap'), err = h('p', { class: 'acc-err', role: 'alert' });
            var f = {
                have: field('Subject you have'), want: field('Subject you want'), section: field('Section or time (optional)'), note: field('Note (optional)'),
                fullName: field('Full name'), studentId: field('University ID'), major: field('Major (optional)'), phone: field('Phone number', 'tel'), email: field('University or personal email', 'email')
            };
            var form = h('form', {});
            Object.keys(f).forEach(function (k) {
                if (k === 'fullName') form.appendChild(h('h3', { class: 'acc-swap-head', text: 'Your details' }));
                form.appendChild(f[k].el);
            });
            form.appendChild(h('p', { class: 'acc-muted acc-small', text: 'Signed-in students can see these when they open your swap. Add a phone number or an email.' }));
            form.appendChild(err);
            var submit = h('button', { type: 'submit', class: 'ab-btn acc-wide', text: 'Post' });
            form.appendChild(submit);
            form.addEventListener('submit', function (e) {
                e.preventDefault();
                var body = {};
                Object.keys(f).forEach(function (k) { body[k] = f[k].input.value.trim(); });
                busy(submit, true, 'Post'); showErr(err, null);
                call('swapAdd', body).then(function (r) {
                    if (r.swap) swaps.unshift(r.swap);
                    d.close(); draw();
                }).catch(function (x) { showErr(err, x); busy(submit, false, 'Post'); });
            });
            d.box.appendChild(form);
        }
        call('swapList').then(function (r) { swaps = r.swaps || []; draw(); })
            .catch(function (e) { listBox.innerHTML = ''; listBox.appendChild(h('p', { class: 'acc-err', role: 'alert', text: e.message || String(e) })); });
    }

    var views = { account: accountView, chat: chatView, admin: adminView, feed: feedView, swap: swapView };
    (views[view] || accountView)();
    if (view === 'chat') window.addEventListener('hashchange', chatView);
})();
