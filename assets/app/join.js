/* UniHub: a friendly "join for free" card for website visitors who aren't signed in.
   Shows once, a few seconds after a page opens; "Maybe later" hides it for 3 days. Never inside the apps. */
(function () {
    'use strict';
    var doc = document;
    if (/ASUBankApp/.test(navigator.userAgent) || (window.webkit && window.webkit.messageHandlers)) return;
    function get(k) { try { return localStorage.getItem(k); } catch (e) { return null; } }
    function set(k, v) { try { localStorage.setItem(k, v); } catch (e) {} }
    if (get('ab.acc.token')) return;
    if (/^\/(account|admin|legal|apps)(\/|$)/.test(location.pathname)) return;
    var later = Number(get('ab.join.later') || 0);
    if (later && Date.now() - later < 3 * 864e5) return;
    try { if (sessionStorage.getItem('ab.join.shown')) return; } catch (e) {}

    var PERKS = [
        ['💬', 'Chat with students', 'Rooms for every college, plus private messages with friends.'],
        ['📸', 'Feed and stories', 'Post, comment and share stories with ASU students.'],
        ['⭐', 'Save and sync', 'Keep your materials and settings on the website and in the apps.'],
        ['🎓', 'Student tools', 'Rate your teachers, find classmates and swap subjects.']
    ];

    function el(tag, cls, text) { var e = doc.createElement(tag); if (cls) e.className = cls; if (text) e.textContent = text; return e; }

    function show() {
        if (get('ab.acc.token') || doc.querySelector('.abj-back') || doc.querySelector('.acc-modal')) return;
        try { sessionStorage.setItem('ab.join.shown', '1'); } catch (e) {}
        var css = el('style');
        css.textContent =
            '.abj-back{position:fixed;inset:0;z-index:2147483647;background:rgba(0,0,0,.45);display:flex;align-items:flex-end;justify-content:center;animation:abj-f .25s ease}' +
            '@media(min-width:700px){.abj-back{align-items:center}}' +
            '.abj{width:100%;max-width:420px;max-height:92vh;overflow:auto;background:var(--ab-surface,#fff);color:var(--ab-label,#111);border-radius:24px 24px 0 0;padding:22px 20px calc(18px + var(--ab-safe-b,0px));box-shadow:0 -10px 50px rgba(0,0,0,.3);animation:abj-up .32s cubic-bezier(.2,.9,.3,1);text-align:start;font-family:var(--ab-font,inherit)}' +
            '@media(min-width:700px){.abj{border-radius:24px;padding-bottom:20px}}' +
            '.abj h2{margin:4px 0 2px;font-size:22px;text-align:center}.abj .abj-sub{margin:0 0 4px;text-align:center;opacity:.72;font-size:14px}' +
            '.abj .abj-ar{margin-bottom:14px}.abj-logo{display:block;width:64px;height:64px;border-radius:16px;margin:0 auto 6px}' +
            '.abj ul{list-style:none;margin:0 0 16px;padding:0;display:grid;gap:10px}.abj li{display:flex;gap:12px;align-items:flex-start}' +
            '.abj li i{font-style:normal;font-size:20px;width:38px;height:38px;flex:none;display:grid;place-items:center;border-radius:12px;background:var(--ab-accent-soft,rgba(140,29,64,.12))}' +
            '.abj li b{display:block;font-size:15px}.abj li small{display:block;opacity:.7;font-size:13px;line-height:1.35}' +
            '.abj a,.abj button{display:block;width:100%;box-sizing:border-box;text-align:center;border-radius:14px;padding:13px;font-size:16px;font-weight:600;text-decoration:none;border:0;cursor:pointer;margin-top:8px;font-family:inherit}' +
            '.abj .abj-go{background:var(--ab-accent,#8C1D40);color:#fff}.abj .abj-app{background:var(--ab-accent-soft,rgba(140,29,64,.12));color:var(--ab-accent,#8C1D40)}' +
            '.abj .abj-no{background:transparent;color:inherit;opacity:.6;font-weight:500}' +
            '@keyframes abj-up{from{transform:translateY(40px);opacity:0}}@keyframes abj-f{from{opacity:0}}';
        doc.head.appendChild(css);
        var back = el('div', 'abj-back');
        var box = el('div', 'abj');
        box.setAttribute('role', 'dialog'); box.setAttribute('aria-modal', 'true'); box.setAttribute('aria-label', 'Join UniHub');
        var logo = el('img', 'abj-logo'); logo.src = '/icons/icon-192x192.png'; logo.alt = '';
        box.appendChild(logo);
        box.appendChild(el('h2', null, 'Join UniHub, it’s free'));
        box.appendChild(el('p', 'abj-sub', 'One account for the website and the apps'));
        var ar = el('p', 'abj-sub abj-ar', 'انضم إلى UniHub مجاناً'); ar.dir = 'rtl'; box.appendChild(ar);
        var ul = el('ul');
        PERKS.forEach(function (p) {
            var li = el('li'); li.appendChild(el('i', null, p[0]));
            var t = el('span'); t.appendChild(el('b', null, p[1])); t.appendChild(el('small', null, p[2])); li.appendChild(t);
            ul.appendChild(li);
        });
        box.appendChild(ul);
        var go = el('a', 'abj-go', 'Create a free account'); go.href = '/account/';
        var app = el('a', 'abj-app', '📲 Get the UniHub app'); app.href = '/apps/download.html';
        var no = el('button', 'abj-no', 'Maybe later'); no.type = 'button';
        box.appendChild(go); box.appendChild(app); box.appendChild(no);
        back.appendChild(box);
        function close() { set('ab.join.later', String(Date.now())); back.remove(); doc.removeEventListener('keydown', key); }
        function key(e) { if (e.key === 'Escape') close(); }
        no.addEventListener('click', close);
        back.addEventListener('click', function (e) { if (e.target === back) close(); });
        doc.addEventListener('keydown', key);
        doc.body.appendChild(back);
        go.focus({ preventScroll: true });
    }
    setTimeout(show, 7000);
})();
