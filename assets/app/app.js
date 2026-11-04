/* =====================================================================
   ASU Bank app shell
   - tab bar on every page, "More" sheet, theme (keeps the old 'darkMode' key)
   - saved (bookmarks) + recent items in localStorage
   - material rows with preview sheet, share and save
   - instant prefetch of internal links + service worker
   Public API: window.AB
   ===================================================================== */
(function () {
    'use strict';
    var AB = window.AB = window.AB || {};
    var doc = document, root = doc.documentElement;

    /* ---------- tiny helpers ---------- */
    function load(k, d) { try { var v = JSON.parse(localStorage.getItem(k)); return v == null ? d : v; } catch (e) { return d; } }
    function store(k, v) { try { localStorage.setItem(k, JSON.stringify(v)); } catch (e) {} }
    function esc(s) { return String(s == null ? '' : s).replace(/[&<>"']/g, function (c) { return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]; }); }
    function el(html) { var t = doc.createElement('template'); t.innerHTML = html.trim(); return t.content.firstElementChild; }
    AB.esc = esc; AB.el = el; AB.load = load; AB.store = store;

    /* Arabic + Latin normalisation used by every search on the site */
    AB.norm = function (s) {
        return String(s || '').toLowerCase()
            .replace(/[ً-ْـ]/g, '')
            .replace(/[أإآ]/g, 'ا').replace(/ة/g, 'ه').replace(/ى/g, 'ي')
            .replace(/[✅✔️⭐🔥]/g, '')
            .replace(/[_\-–—.,:;()\[\]{}|/\\]+/g, ' ')
            .replace(/\s+/g, ' ').trim();
    };

    /* ---------- icons (SF-symbol-like strokes) ---------- */
    var P = {
        home: '<path d="M3 10.5 12 3l9 7.5"/><path d="M5 9.5V20a1 1 0 0 0 1 1h4v-6h4v6h4a1 1 0 0 0 1-1V9.5"/>',
        search: '<circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5"/>',
        upload: '<path d="M12 15V4"/><path d="m7 9 5-5 5 5"/><path d="M5 15v4a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-4"/>',
        bookmark: '<path d="M6 3h12a1 1 0 0 1 1 1v17l-7-4.5L5 21V4a1 1 0 0 1 1-1z"/>',
        more: '<circle cx="5" cy="12" r="1.6" fill="currentColor"/><circle cx="12" cy="12" r="1.6" fill="currentColor"/><circle cx="19" cy="12" r="1.6" fill="currentColor"/>',
        grid: '<rect x="3.5" y="3.5" width="7" height="7" rx="2"/><rect x="13.5" y="3.5" width="7" height="7" rx="2"/><rect x="3.5" y="13.5" width="7" height="7" rx="2"/><rect x="13.5" y="13.5" width="7" height="7" rx="2"/>',
        back: '<path d="m15 18-6-6 6-6"/>',
        chev: '<path d="m9 18 6-6-6-6"/>',
        star: '<path d="m12 3.5 2.6 5.3 5.9.9-4.3 4.1 1 5.8L12 16.9l-5.2 2.7 1-5.8-4.3-4.1 5.9-.9z"/>',
        starOn: '<path fill="currentColor" d="m12 3.5 2.6 5.3 5.9.9-4.3 4.1 1 5.8L12 16.9l-5.2 2.7 1-5.8-4.3-4.1 5.9-.9z"/>',
        share: '<path d="M12 15V3"/><path d="m8 7 4-4 4 4"/><path d="M6 11H5a1 1 0 0 0-1 1v8a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-8a1 1 0 0 0-1-1h-1"/>',
        eye: '<path d="M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7S2 12 2 12z"/><circle cx="12" cy="12" r="3"/>',
        x: '<path d="M18 6 6 18M6 6l12 12"/>',
        xc: '<circle cx="12" cy="12" r="10" fill="currentColor" stroke="none" opacity=".35"/><path d="m15 9-6 6m0-6 6 6" stroke="#fff"/>',
        folder: '<path d="M3 6.5A1.5 1.5 0 0 1 4.5 5H9l2 2h8.5A1.5 1.5 0 0 1 21 8.5v9a1.5 1.5 0 0 1-1.5 1.5h-15A1.5 1.5 0 0 1 3 17.5z"/>',
        file: '<path d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z"/><path d="M14 3v5h5"/>',
        pdf: '<path d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z"/><path d="M14 3v5h5"/><path d="M8.5 15.5h7M8.5 12h4"/>',
        img: '<rect x="3" y="4" width="18" height="16" rx="2"/><circle cx="9" cy="10" r="2"/><path d="m21 16-5-5-9 9"/>',
        video: '<rect x="3" y="5" width="13" height="14" rx="2"/><path d="m16 10 5-3v10l-5-3"/>',
        ppt: '<rect x="3" y="4" width="18" height="12" rx="2"/><path d="M12 16v4M8 20h8"/>',
        sheet: '<rect x="4" y="3" width="16" height="18" rx="2"/><path d="M4 9h16M4 15h16M10 3v18"/>',
        zip: '<path d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z"/><path d="M11 5h2M11 8h2M11 11h2M10.5 14h3v3h-3z"/>',
        link: '<path d="M10 14a4 4 0 0 0 5.7 0l3-3a4 4 0 0 0-5.7-5.7l-1 1"/><path d="M14 10a4 4 0 0 0-5.7 0l-3 3a4 4 0 0 0 5.7 5.7l1-1"/>',
        page: '<path d="M4 19.5V5a2 2 0 0 1 2-2h12v18H6a2 2 0 0 1-2-1.5z"/><path d="M8 7h6"/>',
        clock: '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>',
        moon: '<path d="M20 14.5A8 8 0 0 1 9.5 4a8 8 0 1 0 10.5 10.5z"/>',
        sun: '<circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4"/>',
        open: '<path d="M14 4h6v6"/><path d="M20 4 10 14"/><path d="M19 14v5a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1h5"/>',
        cal: '<rect x="3" y="5" width="18" height="16" rx="2"/><path d="M3 10h18M8 3v4M16 3v4"/>',
        chat: '<path d="M21 12a8 8 0 0 1-11.6 7.1L4 20l1-4.6A8 8 0 1 1 21 12z"/>',
        help: '<circle cx="12" cy="12" r="9"/><path d="M9.5 9.5a2.5 2.5 0 1 1 3.5 2.3c-.6.3-1 .9-1 1.6V14"/><circle cx="12" cy="17.2" r=".6" fill="currentColor"/>',
        inbox: '<path d="M3 13h5l1.5 3h5L16 13h5"/><path d="M5 5h14l2 8v6a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1v-6z"/>',
        sparkle: '<path d="M12 3v4M12 17v4M3 12h4M17 12h4M6 6l2.5 2.5M15.5 15.5 18 18M6 18l2.5-2.5M15.5 8.5 18 6"/>',
        install: '<rect x="6" y="2" width="12" height="20" rx="2.5"/><path d="M12 8v6m-3-3 3 3 3-3"/>',
        check: '<path d="m5 12.5 4.5 4.5L19 7.5"/>',
        trash: '<path d="M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3"/>',
        filter: '<path d="M4 6h16M7 12h10M10 18h4"/>',
        people: '<circle cx="9" cy="8" r="3.5"/><path d="M2.5 20a6.5 6.5 0 0 1 13 0"/><path d="M16 4.6a3.5 3.5 0 0 1 0 6.8M18 14a6.5 6.5 0 0 1 3.5 6"/>',
        doc: '<path d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z"/><path d="M14 3v5h5"/><path d="M9 13h6M9 17h6"/>'
    };
    AB.icon = function (name, cls) {
        return '<svg class="' + (cls || '') + '" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' + (P[name] || P.file) + '</svg>';
    };

    /* ---------- theme ---------- */
    AB.themePref = function () { try { var v = localStorage.getItem('darkMode'); return v === 'enabled' ? 'dark' : v === 'disabled' ? 'light' : 'auto'; } catch (e) { return 'auto'; } };
    AB.applyTheme = function () {
        if (doc.body && doc.body.classList.contains('ab-shell')) return;
        var p = AB.themePref();
        var dark = p === 'dark' || (p === 'auto' && matchMedia('(prefers-color-scheme: dark)').matches);
        root.classList.toggle('dark', dark);
        var m = doc.querySelector('meta[name="theme-color"]');
        if (m && doc.body && doc.body.classList.contains('ab')) m.content = dark ? '#000000' : '#f2f2f7';
        doc.dispatchEvent(new CustomEvent('ab:theme', { detail: { dark: dark } }));
    };
    AB.setTheme = function (p) {
        try { if (p === 'auto') localStorage.removeItem('darkMode'); else localStorage.setItem('darkMode', p === 'dark' ? 'enabled' : 'disabled'); } catch (e) {}
        AB.applyTheme();
    };
    try { matchMedia('(prefers-color-scheme: dark)').addEventListener('change', AB.applyTheme); } catch (e) {}

    /* ---------- material helpers ---------- */
    AB.driveId = function (u) {
        var m = /\/(?:file|document|presentation|spreadsheets)\/d\/([\w-]{10,})/.exec(u) || /[?&]id=([\w-]{10,})/.exec(u) || /\/folders\/([\w-]{10,})/.exec(u);
        return m ? m[1] : null;
    };
    AB.kind = function (u, t) {
        u = String(u || ''); var s = (u + ' ' + (t || '')).toLowerCase();
        if (/^\/(?!\/)/.test(u) || /^https?:\/\/(www\.)?asubank\.org/.test(u)) return 'page';
        if (/drive\.google\.com\/(drive\/(u\/\d\/)?folders|embeddedfolderview|folderview)/.test(u) || /dropbox\.com\/(scl\/fo|sh)\//.test(u) || /sharepoint\.com\/:f:/.test(u)) return 'folder';
        if (/sharepoint\.com\/:v:|youtube\.com\/(playlist|@)/.test(u)) return 'video';
        if (/docs\.google\.com\/document/.test(u)) return 'doc';
        if (/docs\.google\.com\/presentation/.test(u)) return 'ppt';
        if (/docs\.google\.com\/spreadsheets/.test(u)) return 'sheet';
        if (/\.pdf\b/.test(s)) return 'pdf';
        if (/\.(pptx?|key)\b/.test(s)) return 'ppt';
        if (/\.(docx?|rtf|odt)\b/.test(s)) return 'doc';
        if (/\.(xlsx?|csv)\b/.test(s)) return 'sheet';
        if (/\.(jpe?g|png|gif|webp|heic)\b/.test(s)) return 'img';
        if (/\.(mp4|mov|mkv)\b|youtube\.com|youtu\.be/.test(s)) return 'video';
        if (/\.(zip|rar|7z)\b/.test(s)) return 'zip';
        if (/drive\.google\.com\/(file|open|uc)/.test(u) || /dropbox\.com/.test(u)) return 'file';
        return 'link';
    };
    var KIND_ICON = { folder: 'folder', pdf: 'pdf', doc: 'doc', ppt: 'ppt', sheet: 'sheet', img: 'img', video: 'video', zip: 'zip', file: 'file', link: 'link', page: 'page' };
    var KIND_NAME = { folder: 'Folder', pdf: 'PDF', doc: 'Document', ppt: 'Slides', sheet: 'Sheet', img: 'Image', video: 'Video', zip: 'Archive', file: 'File', link: 'Link', page: 'Page' };
    AB.kindName = function (k) { return KIND_NAME[k] || 'File'; };
    AB.tile = function (k) { return '<span class="ab-tile ' + (k === 'file' ? 'link' : k) + '">' + AB.icon(KIND_ICON[k] || 'file') + '</span>'; };
    AB.previewUrl = function (u) {
        u = String(u || '');
        var id = AB.driveId(u);
        if (/drive\.google\.com\/(drive\/(u\/\d\/)?folders|embeddedfolderview)/.test(u) && id) return 'https://drive.google.com/embeddedfolderview?id=' + id + '#list';
        var m = /docs\.google\.com\/(document|presentation|spreadsheets)\/d\/([\w-]+)/.exec(u);
        if (m) return 'https://docs.google.com/' + m[1] + '/d/' + m[2] + '/preview';
        if (/drive\.google\.com\/(file|open|uc)/.test(u) && id) {
            // Drive shortcuts can't be previewed: use the real file or folder (tools/check_links.py -> drive-map.json)
            var t = AB.driveMap[id];
            if (t && t[1] === 'folder') return 'https://drive.google.com/embeddedfolderview?id=' + t[0] + '#list';
            return 'https://drive.google.com/file/d/' + (t ? t[0] : id) + '/preview';
        }
        var y = /(?:youtube\.com\/watch\?v=|youtu\.be\/)([\w-]{6,})/.exec(u);
        if (y) return 'https://www.youtube-nocookie.com/embed/' + y[1];
        return null;
    };
    AB.driveMap = {};
    AB.broken = {};
    var linksP = null;
    AB.links = function () {
        if (!linksP) linksP = Promise.all(['drive-map', 'broken'].map(function (n) {
            return fetch('/assets/app/' + n + '.json').then(function (r) { return r.ok ? r.json() : {}; }).catch(function () { return {}; });
        })).then(function (r) { AB.driveMap = r[0] || {}; AB.broken = r[1] || {}; });
        return linksP;
    };
    // Files admins removed (accounts API). Remembered for 10 minutes and never holds a page up for long.
    AB.deletedIds = {};
    var removedP = null;
    AB.removed = function () {
        if (removedP) return removedP;
        function use(ids) { AB.deletedIds = {}; ids.forEach(function (i) { AB.deletedIds[i] = 1; }); }
        var saved = null;
        try { saved = JSON.parse(localStorage.getItem('ab.removed')); } catch (e) { saved = null; }
        if (saved && saved.ids) use(saved.ids);
        if (saved && Date.now() - saved.at < 6e5) { removedP = Promise.resolve(); return removedP; }
        var live = fetch('https://script.google.com/macros/s/AKfycbzXDE0Km2mxL_gAJFqspZeaADtugupeLN0YQlkEbXnrCkSTKBfImW_lLCKF8_SMI0i_/exec', {
            method: 'POST', headers: { 'Content-Type': 'text/plain;charset=utf-8' }, body: '{"action":"deletedFiles"}'
        }).then(function (r) { return r.json(); }).then(function (o) {
            if (!o || !o.ids) return;
            use(o.ids);
            try { localStorage.setItem('ab.removed', JSON.stringify({ at: Date.now(), ids: o.ids })); } catch (e) {}
        }).catch(function () {});
        removedP = Promise.race([live, new Promise(function (ok) { setTimeout(ok, 1500); })]);
        return removedP;
    };
    AB.isRemoved = function (u) {
        var m = String(u || '').match(/(?:\/d\/|\/folders\/|[?&]id=)([-\w]{10,})/);
        if (!m) return false;
        if (AB.deletedIds[m[1]]) return true;
        var t = AB.driveMap[m[1]];
        return !!(t && AB.deletedIds[t[0]]);
    };
    AB.isExternal = function (u) { return /^https?:\/\//.test(u) && !/^https?:\/\/(www\.)?asubank\.org/.test(u) && u.indexOf(location.origin) !== 0; };
    // "2025", "2024/25" and semester codes like "20231" all count as a year
    // NEW badges last 30 days after a link is added (d = "YYYY-MM-DD"); older ones disappear by themselves
    AB.NEW_DAYS = 30;
    AB.isNew = function (d) { var t = d ? Date.parse(d) : NaN; return !isNaN(t) && Date.now() - t < AB.NEW_DAYS * 864e5; };
    AB.yearOf = function (t) { var m = /(?:^|\D)(20[12]\d)[123]?(?!\d)/.exec(t || ''); return m ? m[1] : ''; };

    /* ---------- saved + recent ---------- */
    function list(key, max) {
        return {
            all: function () { return load(key, []); },
            has: function (u) { return load(key, []).some(function (x) { return x.u === u; }); },
            add: function (it) {
                var a = load(key, []).filter(function (x) { return x.u !== it.u; });
                a.unshift({ u: it.u, t: it.t, k: it.k || AB.kind(it.u, it.t), s: it.s || '', ts: Date.now() });
                if (max) a = a.slice(0, max);
                store(key, a); doc.dispatchEvent(new CustomEvent('ab:' + key));
            },
            remove: function (u) { store(key, load(key, []).filter(function (x) { return x.u !== u; })); doc.dispatchEvent(new CustomEvent('ab:' + key)); },
            clear: function () { store(key, []); doc.dispatchEvent(new CustomEvent('ab:' + key)); }
        };
    }
    AB.saved = list('ab.saved', 0);
    AB.recent = list('ab.recent', 40);
    AB.searches = {
        all: function () { return load('ab.searches', []); },
        add: function (q) { q = String(q || '').trim(); if (q.length < 2) return; var a = load('ab.searches', []).filter(function (x) { return x !== q; }); a.unshift(q); store('ab.searches', a.slice(0, 12)); },
        clear: function () { store('ab.searches', []); }
    };
    AB.toggleSave = function (it) {
        if (AB.saved.has(it.u)) { AB.saved.remove(it.u); AB.toast('Removed from Saved'); return false; }
        AB.saved.add(it); AB.toast('Saved ★'); haptic(); return true;
    };

    /* ---------- toast + haptics ---------- */
    var toastEl, toastT;
    AB.toast = function (msg) {
        if (!toastEl) { toastEl = el('<div class="ab-toast" role="status" aria-live="polite"></div>'); doc.body.appendChild(toastEl); }
        toastEl.textContent = msg; toastEl.classList.add('show');
        clearTimeout(toastT); toastT = setTimeout(function () { toastEl.classList.remove('show'); }, 2000);
    };
    function haptic() { try { navigator.vibrate && navigator.vibrate(8); } catch (e) {} }

    /* ---------- share ---------- */
    AB.absUrl = function (u) { try { return new URL(u, location.origin).href; } catch (e) { return u; } };
    AB.share = function (it) {
        var url = AB.absUrl(it.u), title = it.t || doc.title;
        if (navigator.share) { navigator.share({ title: title, text: title + ' — ASU Bank', url: url }).catch(function () {}); return; }
        var done = function () { AB.toast('Link copied'); };
        if (navigator.clipboard) navigator.clipboard.writeText(url).then(done, function () { prompt('Copy this link', url); });
        else prompt('Copy this link', url);
    };

    /* ---------- sheets ---------- */
    var openSheets = [];
    AB.sheet = function (opts) {
        var back = el('<div class="ab-backdrop"></div>');
        var sh = el('<div class="ab-sheet' + (opts.full ? ' full' : '') + '" role="dialog" aria-modal="true" tabindex="-1">' +
            '<div class="ab-grabber"></div>' +
            '<div class="ab-sheet-h"><h2></h2><button class="ab-icon-btn" type="button" data-close aria-label="Close">' + AB.icon('xc') + '</button></div>' +
            '<div class="ab-sheet-b' + (opts.flush ? ' flush' : '') + '"></div></div>');
        sh.querySelector('h2').textContent = opts.title || '';
        sh.setAttribute('aria-label', opts.title || 'Dialog');
        var body = sh.querySelector('.ab-sheet-b');
        if (typeof opts.body === 'string') body.innerHTML = opts.body; else if (opts.body) body.appendChild(opts.body);
        if (opts.actions) sh.appendChild(opts.actions);
        doc.body.appendChild(back); doc.body.appendChild(sh);
        var prevFocus = doc.activeElement, prevOverflow = doc.body.style.overflow;
        doc.body.style.overflow = 'hidden';
        sh.getBoundingClientRect(); setTimeout(function () { back.classList.add('open'); sh.classList.add('open'); sh.focus({ preventScroll: true }); }, 16);
        var api = { el: sh, body: body, close: close };
        function close() {
            if (!sh.isConnected) return;
            back.classList.remove('open'); sh.classList.remove('open'); sh.style.transform = '';
            openSheets = openSheets.filter(function (x) { return x !== api; });
            if (!openSheets.length) doc.body.style.overflow = prevOverflow;
            setTimeout(function () { back.remove(); sh.remove(); }, 420);
            if (prevFocus && prevFocus.focus) prevFocus.focus({ preventScroll: true });
            opts.onClose && opts.onClose();
        }
        back.addEventListener('click', close);
        sh.querySelector('[data-close]').addEventListener('click', close);
        // swipe down to dismiss (mobile)
        var y0 = null, dy = 0, head = sh.querySelector('.ab-sheet-h'), grab = sh.querySelector('.ab-grabber');
        [head, grab].forEach(function (h) {
            h.addEventListener('touchstart', function (e) { y0 = e.touches[0].clientY; dy = 0; sh.style.transition = 'none'; }, { passive: true });
            h.addEventListener('touchmove', function (e) { if (y0 == null) return; dy = Math.max(0, e.touches[0].clientY - y0); sh.style.transform = 'translateY(' + dy + 'px)'; }, { passive: true });
            h.addEventListener('touchend', function () { sh.style.transition = ''; if (dy > 90) close(); else sh.style.transform = ''; y0 = null; });
        });
        openSheets.push(api);
        return api;
    };
    doc.addEventListener('keydown', function (e) { if (e.key === 'Escape' && openSheets.length) openSheets[openSheets.length - 1].close(); });

    /* ---------- preview ---------- */
    AB.preview = function (it) {
        var src = AB.previewUrl(it.u);
        if (!src) { window.open(it.u, '_blank', 'noopener'); return; }
        AB.recent.add(it);
        var body = el('<div style="position:relative;flex:1;display:flex;flex-direction:column"><div class="ab-empty" style="position:absolute;inset:0;display:grid;place-items:center"><span class="ab-spinner"></span></div></div>');
        var fr = doc.createElement('iframe');
        fr.src = src; fr.title = 'Preview: ' + it.t; fr.allow = 'autoplay; fullscreen'; fr.setAttribute('allowfullscreen', '');
        fr.style.position = 'relative'; fr.addEventListener('load', function () { body.firstElementChild.remove(); });
        body.appendChild(fr);
        var saved = AB.saved.has(it.u);
        var actions = el('<div class="ab-sheet-actions">' +
            '<a class="ab-btn" target="_blank" rel="noopener">' + AB.icon('open') + ' Open</a>' +
            '<button class="ab-btn tinted" type="button" data-save>' + AB.icon(saved ? 'starOn' : 'star') + '<span>' + (saved ? 'Saved' : 'Save') + '</span></button>' +
            '<button class="ab-btn tinted" type="button" data-share>' + AB.icon('share') + ' Share</button></div>');
        actions.querySelector('a').href = it.u;
        actions.querySelector('[data-save]').addEventListener('click', function () {
            var on = AB.toggleSave(it);
            this.innerHTML = AB.icon(on ? 'starOn' : 'star') + '<span>' + (on ? 'Saved' : 'Save') + '</span>';
            syncStars();
        });
        actions.querySelector('[data-share]').addEventListener('click', function () { AB.share(it); });
        AB.sheet({ title: it.t, body: body, flush: true, full: true, actions: actions });
    };

    /* ---------- material rows ---------- */
    // item: {u, t, k?, s? (source/subject), y? (year), d? (date added), tag? ('pin'|'warn')}
    AB.row = function (it, opts) {
        opts = opts || {};
        var k = it.k || AB.kind(it.u, it.t);
        var ext = AB.isExternal(it.u);
        var canPrev = !!AB.previewUrl(it.u);
        var meta = [];
        if (AB.isNew(it.d)) meta.push('<span class="ab-badge new">NEW</span>');
        else if (it.tag === 'pin') meta.push('<span class="ab-badge accent">ALL FILES</span>');
        else if (it.tag === 'warn') meta.push('<span class="ab-badge warn">CLOSED</span>');
        meta.push('<span>' + AB.kindName(k) + '</span>');
        if (it.s && opts.showSource !== false) meta.push('<span>· ' + esc(it.s) + '</span>');
        if (it.meta) meta.push('<span>· ' + esc(it.meta) + '</span>');
        var on = AB.saved.has(it.u);
        var li = el('<li><div class="ab-row">' +
            '<a class="ab-row-main" href="' + esc(it.u) + '"' + (ext ? ' target="_blank" rel="noopener"' : '') + '>' + AB.tile(k) +
            '<span class="ab-row-text"><span class="ab-row-title"></span><span class="ab-row-meta">' + meta.join('') + '</span></span></a>' +
            '<span class="ab-row-actions">' +
            (canPrev ? '<button class="ab-icon-btn" type="button" data-ab-act="preview" aria-label="Preview">' + AB.icon('eye') + '</button>'
                : '<button class="ab-icon-btn" type="button" data-ab-act="share" aria-label="Share">' + AB.icon('share') + '</button>') +
            '<button class="ab-icon-btn' + (on ? ' on' : '') + '" type="button" data-ab-act="save" aria-pressed="' + on + '" aria-label="Save">' + AB.icon(on ? 'starOn' : 'star') + '</button>' +
            '</span></div></li>');
        li.querySelector('.ab-row-title').textContent = it.t;
        li._item = { u: it.u, t: it.t, k: k, s: it.s || '' };
        return li;
    };
    function itemOf(node) { var li = node.closest('li'); return li && li._item; }
    function syncStars() {
        doc.querySelectorAll('[data-ab-act="save"]').forEach(function (b) {
            var it = itemOf(b); if (!it) return;
            var on = AB.saved.has(it.u);
            b.classList.toggle('on', on); b.setAttribute('aria-pressed', on); b.innerHTML = AB.icon(on ? 'starOn' : 'star');
        });
    }
    AB.syncStars = syncStars;
    doc.addEventListener('click', function (e) {
        var b = e.target.closest('[data-ab-act]');
        if (b) {
            var it = itemOf(b); if (!it) return;
            e.preventDefault();
            var a = b.getAttribute('data-ab-act');
            if (a === 'save') { AB.toggleSave(it); syncStars(); }
            else if (a === 'preview') AB.preview(it);
            else if (a === 'share') AB.share(it);
            else if (a === 'remove-recent') { AB.recent.remove(it.u); }
            return;
        }
        var m = e.target.closest('.ab-row-main');
        if (m) { var it2 = itemOf(m); if (it2) AB.recent.add(it2); }
    });

    /* Upgrade hand-written material links: <a class="ab-item" href data-tag> and legacy .subject-card blocks */
    AB.enhance = function (scope) {
        scope = scope || doc;
        var lists = scope.querySelectorAll('ul.ab-items');
        lists.forEach(function (ul) {
            var src = ul.getAttribute('data-source') || '';
            Array.prototype.slice.call(ul.querySelectorAll('a.ab-item')).forEach(function (a) {
                var it = { u: a.getAttribute('href'), t: a.textContent.replace(/\s+/g, ' ').trim(), tag: a.getAttribute('data-tag') || '', d: a.getAttribute('data-added') || '', s: a.getAttribute('data-source') || '' };
                var li = AB.row(it, { showSource: !!it.s });
                li.setAttribute('data-q', AB.norm(it.t + ' ' + src + ' ' + it.s));
                li.setAttribute('data-k', li._item.k);
                if (it.tag) li.setAttribute('data-tag', it.tag);
                if (AB.isNew(it.d)) li.setAttribute('data-new', '');
                a.closest('li').replaceWith(li);
            });
            ul.classList.add('ab-list');
        });
    };

    /* Legacy snippets pasted into a page: <div class="subject-card"><a href>..</a></div> -> rows */
    AB.adoptLegacy = function (ul) {
        doc.querySelectorAll('main .subject-card').forEach(function (card) {
            var a = card.matches('a') ? card : card.querySelector('a[href]'); if (!a) return;
            var t = a.textContent.replace(/\s+/g, ' ').trim(); if (!t) return;
            var tag = a.querySelector('img[src*="pin.png"]') ? 'pin' : a.querySelector('font[color]') ? 'warn' : '';
            var li = AB.row({ u: a.getAttribute('href'), t: t, tag: tag }, { showSource: false });
            li.setAttribute('data-q', AB.norm(t)); li.setAttribute('data-k', li._item.k); if (tag) li.setAttribute('data-tag', tag);
            ul.insertBefore(li, ul.firstChild); card.remove();
        });
    };

    /* Search box + chips over a list of rows (subject pages, Saved page) */
    AB.listFilter = function (ul) {
        var input = doc.getElementById(ul.getAttribute('data-ab-filter'));
        var chipsBox = doc.getElementById(ul.getAttribute('data-ab-chips'));
        var count = doc.getElementById(ul.getAttribute('data-ab-count'));
        var empty = doc.getElementById(ul.getAttribute('data-ab-empty'));
        var rows = function () { return Array.prototype.slice.call(ul.children); };
        var mode = 'all';
        if (chipsBox) {
            var all = rows(), years = {}, nNew = 0, nFold = 0;
            all.forEach(function (li) {
                var y = AB.yearOf(li._item && li._item.t); if (y) years[y] = (years[y] || 0) + 1;
                if (li.hasAttribute('data-new')) nNew++;
                if (li.getAttribute('data-k') === 'folder') nFold++;
            });
            var chips = [['all', 'All', all.length]];
            if (nFold && nFold < all.length) { chips.push(['folder', 'Folders', nFold]); chips.push(['file', 'Files', all.length - nFold]); }
            if (nNew) chips.push(['new', 'New', nNew]);
            chips.push(['saved', '★ Saved', null]);
            Object.keys(years).sort().reverse().forEach(function (y) { if (years[y] > 1) chips.push(['y' + y, y, years[y]]); });
            chipsBox.innerHTML = chips.map(function (c, i) {
                return '<button class="ab-chip" type="button" data-mode="' + c[0] + '" aria-pressed="' + (i === 0) + '">' + c[1] + (c[2] != null ? ' <span class="n">' + c[2] + '</span>' : '') + '</button>';
            }).join('');
            chipsBox.addEventListener('click', function (e) {
                var b = e.target.closest('[data-mode]'); if (!b) return;
                mode = b.getAttribute('data-mode');
                chipsBox.querySelectorAll('[data-mode]').forEach(function (x) { x.setAttribute('aria-pressed', x === b); });
                run();
            });
        }
        function run() {
            var q = input ? AB.norm(input.value) : '', toks = q ? q.split(' ') : [], n = 0;
            if (input) input.parentNode.classList.toggle('has-text', !!input.value);
            rows().forEach(function (li) {
                var s = li.getAttribute('data-q') || '', k = li.getAttribute('data-k'), it = li._item || {};
                var ok = toks.every(function (t) { return s.indexOf(t) > -1; });
                if (ok && mode !== 'all') {
                    if (mode === 'folder') ok = k === 'folder';
                    else if (mode === 'file') ok = k !== 'folder';
                    else if (mode === 'new') ok = li.hasAttribute('data-new');
                    else if (mode === 'saved') ok = AB.saved.has(it.u);
                    else if (mode.charAt(0) === 'y') ok = AB.yearOf(it.t) === mode.slice(1);
                }
                li.toggleAttribute('data-ab-hidden', !ok);
                if (ok) n++;
            });
            var total = rows().length;
            if (count) count.textContent = (q || mode !== 'all') ? n + ' of ' + total + ' shown' : total + ' item' + (total === 1 ? '' : 's');
            if (empty) empty.hidden = n > 0;
            ul.hidden = n === 0;
            var qs = doc.querySelectorAll('[data-ab-q]');
            qs.forEach(function (a) { a.href = a.getAttribute('data-ab-q') + encodeURIComponent(input ? input.value.trim() : ''); });
        }
        if (input) {
            input.addEventListener('input', run);
            input.addEventListener('keydown', function (e) {
                if (e.key !== 'Enter') return;
                var first = ul.querySelector('li:not([data-ab-hidden]) .ab-row-main');
                if (!first && input.value.trim()) location.href = '/guides/search?q=' + encodeURIComponent(input.value.trim());
                else if (first) first.focus();
            });
            var clr = input.parentNode.querySelector('.ab-s-clear');
            if (clr) clr.addEventListener('click', function () { input.value = ''; run(); input.focus(); });
            var q0 = new URLSearchParams(location.search).get('q');
            if (q0) input.value = q0;
        }
        doc.addEventListener('ab:ab.saved', function () { if (mode === 'saved') run(); });
        run();
        return { run: run };
    };
    doc.addEventListener('click', function (e) {
        if (e.target.closest('[data-share-page]')) { e.preventDefault(); AB.share({ u: location.href, t: doc.title.split('|')[0].trim() }); }
    });

    /* ---------- tab bar + More sheet ---------- */
    var TABS = [
        { id: 'home', href: '/', label: 'Home', icon: 'home', match: /^\/(index)?$/ },
        { id: 'search', href: '/guides/search', label: 'Search', icon: 'search', match: /^\/guides\/search$/ },
        { id: 'upload', href: '/upload/uploadmethod', label: 'Upload', icon: 'upload', match: /^\/upload\// },
        { id: 'saved', href: '/guides/saved', label: 'Saved', icon: 'bookmark', match: /^\/guides\/saved$/ }
    ];
    function cleanPath() { return location.pathname.replace(/\.html$/, '').replace(/\/index$/, '/').replace(/(.)\/$/, '$1'); }
    AB.path = cleanPath;
    function buildTabbar() {
        if (doc.querySelector('.ab-tabbar') || root.hasAttribute('data-ab-notab')) return;
        var p = cleanPath();
        var html = '<div class="ab-tabbar" role="navigation" aria-label="App"><ul>';
        TABS.forEach(function (t) {
            html += '<li><a href="' + t.href + '"' + (t.match.test(p) ? ' aria-current="page"' : '') + '>' + AB.icon(t.icon) + '<span>' + t.label + '</span></a></li>';
        });
        html += '<li><button type="button" data-ab-more aria-haspopup="dialog">' + AB.icon('grid') + '<span>More</span></button></li></ul></div>';
        doc.body.appendChild(el(html));
    }
    var MORE = [
        ['Study', [
            ['/guides/latest', 'Latest uploads', 'sparkle'], ['/banks/mostwanted', 'Most wanted files', 'star'],
            ['/requests/student_request_portal', 'Request a material', 'inbox'], ['/guides/tutorial', 'How to search', 'help']]],
        ['University', [
            ['/schedules/calendar27', 'Calendar 2026–2027', 'cal'], ['/guides/aspu_qa', 'New student guide', 'page'],
            ['/guides/asu', 'ASU shortcuts (MyASU, EduGate)', 'link'], ['/guides/halls', 'University halls', 'page'],
            ['/guides/grading-scale-asu', 'Grading scale', 'sheet']]],
        ['Community', [
            ['/guides/groups', 'Course WhatsApp groups', 'chat'], ['/requests/portal_to_rating', 'Rate professors', 'star'],
            ['/requests/matcher_confirm', 'Course matcher', 'people'], ['/guides/links', 'ASU resources', 'grid']]],
        ['Help & support', [
            ['/guides/contact', 'Contact us', 'chat'], ['/guides/faqs', 'FAQs', 'help'],
            ['/apps/donate', 'Donate (PayPal or CliQ)', 'star'], ['/legal/uni_tos', 'Terms of Service', 'page'], ['/legal/privacy', 'Privacy policy', 'page'], ['/legal/community', 'Community rules', 'page']]]
    ];
    var installEvt = null;
    window.addEventListener('beforeinstallprompt', function (e) { e.preventDefault(); installEvt = e; });
    AB.install = function () {
        if (installEvt) { installEvt.prompt(); installEvt.userChoice.finally(function () { installEvt = null; }); return; }
        var ios = /iphone|ipad|ipod/i.test(navigator.userAgent);
        AB.sheet({ title: 'Install ASU Bank', body: '<div class="ab-prose" style="margin-top:4px">' + (ios
            ? '<p>1. Tap the <strong>Share</strong> button in Safari.</p><p>2. Choose <strong>Add to Home Screen</strong>.</p><p>3. Open ASU Bank from your home screen — it runs full screen like an app and works offline for pages you opened.</p>'
            : '<p>Open your browser menu (⋮) and choose <strong>Install app</strong> or <strong>Add to Home screen</strong>.</p><p>ASU Bank then opens like an app and keeps working offline for pages you opened.</p>') + '</div>' });
    };
    function openMore() {
        var p = AB.themePref();
        var html = '<div style="padding-top:4px">';
        html += '<div class="ab-section-h" style="padding-top:4px"><h2 style="font-size:15px;color:var(--ab-label-2);font-weight:600">Appearance</h2></div>' +
            '<div class="ab-seg" role="group" aria-label="Appearance" style="width:100%;display:grid;grid-template-columns:repeat(3,1fr)">' +
            ['auto', 'light', 'dark'].map(function (v) { return '<button type="button" data-theme="' + v + '" aria-pressed="' + (p === v) + '">' + { auto: 'Automatic', light: 'Light', dark: 'Dark' }[v] + '</button>'; }).join('') + '</div>';
        MORE.forEach(function (g) {
            html += '<div class="ab-section" style="margin-top:18px"><div class="ab-section-h"><h2 style="font-size:15px;color:var(--ab-label-2);font-weight:600">' + g[0] + '</h2></div><ul class="ab-list">';
            g[1].forEach(function (l) {
                html += '<li><a class="ab-row" href="' + l[0] + '"><span class="ab-row-main"><span class="ab-tile page">' + AB.icon(l[2]) + '</span><span class="ab-row-text"><span class="ab-row-title">' + l[1] + '</span></span></span>' + AB.icon('chev', 'ab-chev') + '</a></li>';
            });
            html += '</ul></div>';
        });
        var standalone = matchMedia('(display-mode: standalone)').matches || navigator.standalone;
        html += '<div class="ab-btn-row" style="margin-top:18px">' +
            (standalone ? '' : '<button class="ab-btn tinted" type="button" data-install style="flex:1">' + AB.icon('install') + ' Install app</button>') +
            '<a class="ab-btn green" style="flex:1" href="https://chat.whatsapp.com/LS92JgjO26Y5SZK9SVJ9FJ" target="_blank" rel="noopener">' + AB.icon('chat') + ' WhatsApp</a></div></div>';
        var s = AB.sheet({ title: 'More', body: html });
        s.body.addEventListener('click', function (e) {
            var t = e.target.closest('[data-theme]');
            if (t) { AB.setTheme(t.getAttribute('data-theme')); s.body.querySelectorAll('[data-theme]').forEach(function (b) { b.setAttribute('aria-pressed', b === t); }); }
            if (e.target.closest('[data-install]')) { s.close(); AB.install(); }
        });
    }
    doc.addEventListener('click', function (e) { if (e.target.closest('[data-ab-more]')) { e.preventDefault(); openMore(); } });

    /* ---------- nav bar behaviour ---------- */
    function navBar() {
        var nav = doc.querySelector('.ab-nav'), big = doc.querySelector('.ab-large');
        if (nav && big && 'IntersectionObserver' in window) {
            new IntersectionObserver(function (en) { nav.classList.toggle('scrolled', !en[0].isIntersecting); }, { rootMargin: '-60px 0px 0px 0px' }).observe(big);
        }
        doc.querySelectorAll('.ab-back').forEach(function (b) {
            b.addEventListener('click', function (e) {
                var ref = doc.referrer;
                if (ref && ref.indexOf(location.origin) === 0 && history.length > 1) { e.preventDefault(); history.back(); }
            });
        });
        var p = cleanPath();
        doc.querySelectorAll('.ab-desk-links a').forEach(function (a) { if (a.getAttribute('href') === p) a.setAttribute('aria-current', 'page'); });
        // account chip (same account as the apps; see assets/app/accounts.js)
        var r = doc.querySelector('.ab-nav-r');
        if (r && !r.querySelector('.ab-acc-chip')) {
            var u = null; try { u = localStorage.getItem('ab.acc.token') && JSON.parse(localStorage.getItem('ab.acc.user') || 'null'); } catch (e) {}
            var c = doc.createElement('a'); c.className = 'ab-acc-chip';
            c.href = u ? '/chat/' : '/account/';
            c.textContent = u ? ((u.displayName || u.username || '?').charAt(0).toUpperCase()) : 'Sign in';
            c.setAttribute('aria-label', u ? 'Student chat (signed in as @' + u.username + ')' : 'Sign in to ASU Bank');
            c.setAttribute('style', u ? 'display:inline-grid;place-items:center;width:30px;height:30px;border-radius:50%;color:#fff;font-weight:700;text-decoration:none;background:' + (/^#[0-9a-fA-F]{3,8}$/.test(u.color || '') ? u.color : '#8C1D40')
                : 'font-weight:600;font-size:15px;text-decoration:none;color:var(--ab-accent);white-space:nowrap');
            r.insertBefore(c, r.firstChild);
        }
    }

    /* ---------- prefetch internal pages (instant navigation) ---------- */
    function prefetch() {
        if (navigator.connection && (navigator.connection.saveData || /2g/.test(navigator.connection.effectiveType || ''))) return;
        if (HTMLScriptElement.supports && HTMLScriptElement.supports('speculationrules')) {
            var s = doc.createElement('script'); s.type = 'speculationrules';
            s.textContent = JSON.stringify({ prefetch: [{ where: { and: [{ href_matches: '/*' }, { not: { href_matches: '/apps/adminpanel_subs*' } }] }, eagerness: 'moderate' }] });
            doc.head.appendChild(s); return;
        }
        var done = {};
        function go(e) {
            var a = e.target.closest && e.target.closest('a[href^="/"]'); if (!a) return;
            var h = a.getAttribute('href').split('#')[0]; if (!h || done[h]) return; done[h] = 1;
            var l = doc.createElement('link'); l.rel = 'prefetch'; l.href = h; doc.head.appendChild(l);
        }
        doc.addEventListener('pointerover', go, { passive: true });
        doc.addEventListener('touchstart', go, { passive: true });
    }

    /* ---------- copy protection kept from the old site (fields still work) ---------- */
    AB.protect = function () {
        var isField = function (n) { return n && n.closest && n.closest('input, textarea, select, [contenteditable]'); };
        doc.addEventListener('contextmenu', function (e) { if (!isField(e.target)) e.preventDefault(); });
        doc.addEventListener('copy', function (e) { if (!isField(e.target)) e.preventDefault(); });
        doc.addEventListener('keydown', function (e) { if (!isField(e.target) && e.ctrlKey && ['c', 'x', 'u'].indexOf(e.key) > -1) e.preventDefault(); });
    };

    /* ---------- materials index (built from the subject pages) ---------- */
    var idxP = null;
    AB.index = function () {
        if (!idxP) idxP = Promise.all([fetch('/assets/app/materials.json').then(function (r) { return r.json(); }), AB.links(), AB.removed()]).then(function (x) {
            var d = x[0];
            // links the checker found dead are left out of search
            d.items = d.items.filter(function (it) { return !AB.broken[it.u] && !AB.isRemoved(it.u); });
            d.items.forEach(function (it) { it.q = AB.norm(it.t + ' ' + it.s + ' ' + (it.c || '')); it.k = it.k || AB.kind(it.u, it.t); });
            return d;
        }).catch(function () { idxP = null; return { items: [], subjects: [] }; });
        return idxP;
    };
    // score items for query; all tokens must match
    AB.match = function (items, q, limit) {
        var nq = AB.norm(q); if (!nq) return [];
        var toks = nq.split(' ');
        var out = [];
        for (var i = 0; i < items.length; i++) {
            var it = items[i], s = it.q, ok = true, score = 0;
            for (var j = 0; j < toks.length; j++) {
                var p = s.indexOf(toks[j]);
                if (p < 0) { ok = false; break; }
                score += p === 0 ? 6 : (s.charAt(p - 1) === ' ' ? 4 : 1);
            }
            if (!ok) continue;
            var tn = AB.norm(it.t);
            if (tn.indexOf(nq) === 0) score += 8; else if (tn.indexOf(nq) > -1) score += 4;
            if (it.k === 'folder') score += 1;
            if (AB.isNew(it.d)) score += 1;
            if (it.arch) score -= 3;
            out.push([score, it]);
        }
        out.sort(function (a, b) { return b[0] - a[0]; });
        return out.slice(0, limit || 200).map(function (x) { return x[1]; });
    };

    /* ---------- boot ---------- */
    function boot() {
        // Pages built on this design follow the saved theme; older pages keep their own theme switch.
        if (doc.body.classList.contains('ab')) AB.applyTheme(); else doc.body.classList.add('ab-shell');
        // hand-written NEW badges carry data-added too
        doc.querySelectorAll('[data-added].new, [data-added].new-badge, .tag.new[data-added]').forEach(function (b) { if (!AB.isNew(b.getAttribute('data-added'))) b.remove(); });
        buildTabbar();
        navBar();
        AB.enhance();
        doc.querySelectorAll('ul[data-ab-filter]').forEach(function (ul) { if (ul.hasAttribute('data-ab-legacy')) AB.adoptLegacy(ul); AB.listFilter(ul); });
        prefetch();
        AB.links();
        // subject pages list their files in the page itself
        if (doc.querySelector('ul[data-ab-filter] a[href*="google.com"]')) Promise.all([AB.links(), AB.removed()]).then(function () {
            doc.querySelectorAll('ul[data-ab-filter] a[href*="google.com"]').forEach(function (a) {
                if (AB.isRemoved(a.href)) { var li = a.closest('li'); if (li) li.remove(); }
            });
        });
        var rt = doc.body.getAttribute('data-ab-recent');
        if (rt) AB.recent.add({ u: cleanPath(), t: rt, k: 'page', s: doc.body.getAttribute('data-ab-group') || '' });
        if (doc.body.hasAttribute('data-ab-protect')) AB.protect();
        if ('serviceWorker' in navigator && window.isSecureContext) navigator.serviceWorker.register('/sw.js').catch(function () {});
        // chat notifications, on every page, for people signed in
        try {
            if (localStorage.getItem('ab.acc.token')) {
                var notify = doc.createElement('script');
                notify.src = '/assets/app/chat-notify.js';
                notify.defer = true;
                doc.head.appendChild(notify);
            }
        } catch (e) {}
        doc.addEventListener('ab:ab.saved', syncStars);
    }
    if (doc.readyState === 'loading') doc.addEventListener('DOMContentLoaded', boot); else boot();
})();
