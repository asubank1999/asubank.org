/* UniHub study tools on the website: /guides/final-mark, /guides/absences, /guides/flashcards.
 * Everything stays in this browser (localStorage). The apps have the same tools natively (StudyTools.swift, StudyTools.kt). */
document.addEventListener('DOMContentLoaded', function () {
    'use strict';
    var tool = document.body.getAttribute('data-tool');
    var $ = function (id) { return document.getElementById(id); };
    function load(k, d) { try { var v = localStorage.getItem(k); return v ? JSON.parse(v) : d; } catch (e) { return d; } }
    function save(k, v) { try { localStorage.setItem(k, JSON.stringify(v)); } catch (e) {} }
    function esc(s) { return String(s).replace(/[&<>"]/g, function (c) { return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]; }); }
    function uid() { return Date.now().toString(36) + Math.random().toString(36).slice(2, 7); }
    function fmt(n) { return n % 1 === 0 ? String(n) : n.toFixed(1); }

    // ---------- final mark calculator ----------
    if (tool === 'final') {
        var f = load('ab.final', { have: '', outOf: '50', weight: '50', target: '50' });
        ['have', 'outOf', 'weight', 'target'].forEach(function (k) {
            var el = $(k); el.value = f[k];
            el.addEventListener('input', function () { f[k] = el.value; save('ab.final', f); calc(); });
        });
        function calc() {
            var h = parseFloat(f.have), o = parseFloat(f.outOf), w = parseFloat(f.weight), t = parseFloat(f.target);
            var out = $('result'), msg = $('msg');
            out.innerHTML = ''; msg.textContent = '';
            if (isNaN(h) || isNaN(o) || isNaN(w) || o <= 0 || w <= 0) { out.hidden = true; return; }
            if (h > o) { out.hidden = true; msg.textContent = "Your marks so far can't be more than the total."; return; }
            var total = o + w, goals = [50, 60, 70, 80, 90].map(function (p) { return p * total / 100; });
            if (!isNaN(t) && goals.indexOf(t) < 0) goals.push(t);
            goals.sort(function (a, b) { return a - b; });
            goals.forEach(function (g) {
                var n = g - h, txt, cls = '';
                if (n <= 0) { txt = 'Already reached'; cls = 'ok'; }
                else if (n > w) { txt = 'Not possible'; cls = 'bad'; }
                else { txt = fmt(Math.ceil(n * 2) / 2) + ' / ' + fmt(w); if (n / w > 0.8) cls = 'warn'; }
                out.insertAdjacentHTML('beforeend', '<li class="ab-row"><span class="ab-row-text"><span class="ab-row-title">To get ' + fmt(g) + (g === t ? ' ⭐' : '') +
                    '</span></span><strong class="res ' + cls + '">' + txt + '</strong></li>');
            });
            out.hidden = false;
            $('totalNote').textContent = 'The total is out of ' + fmt(total) + '.';
        }
        calc();
    }

    // ---------- absence tracker ----------
    if (tool === 'absences') {
        var list = load('ab.absences', []);
        var listEl = $('list');
        function draw() {
            listEl.innerHTML = '';
            $('empty').hidden = list.length > 0;
            list.forEach(function (s, i) {
                var left = s.limit - s.missed;
                var cls = left <= 0 ? 'bad' : left === 1 ? 'warn' : 'ok';
                var st = left < 0 ? (-left) + ' over the limit' : left === 0 ? "At the limit: don't miss another lecture" : left === 1 ? '1 absence left' : left + ' absences left';
                var pct = Math.min(100, s.missed / Math.max(1, s.limit) * 100);
                listEl.insertAdjacentHTML('beforeend',
                    '<li class="abs"><div class="ab-row"><span class="ab-row-text"><span class="ab-row-title">' + esc(s.name) + '</span>' +
                    '<span class="ab-row-meta res ' + cls + '">' + st + '</span></span>' +
                    '<button class="ab-icon-btn" data-a="-1" data-i="' + i + '" aria-label="Remove one"' + (s.missed ? '' : ' disabled') + '>−</button>' +
                    '<strong class="count">' + s.missed + '/' + s.limit + '</strong>' +
                    '<button class="ab-icon-btn" data-a="1" data-i="' + i + '" aria-label="Add one">+</button>' +
                    '<button class="ab-icon-btn del" data-a="x" data-i="' + i + '" aria-label="Delete">' + AB.icon('x') + '</button></div>' +
                    '<div class="bar ' + cls + '"><i style="width:' + pct + '%"></i></div></li>');
            });
        }
        listEl.addEventListener('click', function (e) {
            var b = e.target.closest('[data-a]'); if (!b) return;
            var i = +b.getAttribute('data-i'), a = b.getAttribute('data-a');
            if (a === 'x') { if (!confirm('Delete ' + list[i].name + '?')) return; list.splice(i, 1); }
            else list[i].missed = Math.max(0, list[i].missed + (+a));
            save('ab.absences', list); draw();
        });
        $('addForm').addEventListener('submit', function (e) {
            e.preventDefault();
            var n = $('aName').value.trim(), l = parseInt($('aLimit').value, 10);
            if (!n || !(l > 0)) return;
            list.push({ id: uid(), name: n, limit: l, missed: 0 });
            save('ab.absences', list); $('aName').value = ''; draw();
        });
        draw();
    }

    // ---------- flashcards ----------
    if (tool === 'flash') {
        var decks = load('ab.flashcards', []), cur = null, queue = [], again = [], known = 0, round = 1;
        function deck() { return decks.filter(function (d) { return d.id === cur; })[0]; }
        function persist() { save('ab.flashcards', decks); }
        function show(v) { ['vDecks', 'vDeck', 'vStudy'].forEach(function (id) { $(id).hidden = id !== v; }); window.scrollTo(0, 0); }
        function drawDecks() {
            var el = $('decks'); el.innerHTML = '';
            $('noDecks').hidden = decks.length > 0;
            decks.forEach(function (d) {
                el.insertAdjacentHTML('beforeend', '<li><button class="ab-row" type="button" data-deck="' + d.id + '"><span class="ab-row-main"><span class="ab-tile emoji">🃏</span>' +
                    '<span class="ab-row-text"><span class="ab-row-title">' + esc(d.name) + '</span><span class="ab-row-meta">' + d.cards.length + ' cards</span></span></span>' + AB.icon('chev') + '</button></li>');
            });
            show('vDecks');
        }
        function drawDeck() {
            var d = deck(); if (!d) return drawDecks();
            $('deckName').textContent = d.name;
            $('studyBtn').disabled = !d.cards.length;
            var el = $('cards'); el.innerHTML = '';
            $('noCards').hidden = d.cards.length > 0;
            d.cards.forEach(function (c, i) {
                el.insertAdjacentHTML('beforeend', '<li class="ab-row"><span class="ab-row-text"><span class="ab-row-title">' + esc(c.front) + '</span><span class="ab-row-meta">' + esc(c.back) +
                    '</span></span><button class="ab-icon-btn" type="button" data-rm="' + i + '" aria-label="Delete card">' + AB.icon('x') + '</button></li>');
            });
            show('vDeck');
        }
        $('decks').addEventListener('click', function (e) { var b = e.target.closest('[data-deck]'); if (b) { cur = b.getAttribute('data-deck'); drawDeck(); } });
        $('newDeck').addEventListener('submit', function (e) {
            e.preventDefault(); var n = $('dName').value.trim(); if (!n) return;
            var d = { id: uid(), name: n, cards: [] }; decks.push(d); persist(); $('dName').value = ''; cur = d.id; drawDeck(); $('cFront').focus();
        });
        $('newCard').addEventListener('submit', function (e) {
            e.preventDefault(); var fr = $('cFront').value.trim(), bk = $('cBack').value.trim(); if (!fr || !bk) return;
            deck().cards.push({ id: uid(), front: fr, back: bk }); persist(); $('cFront').value = ''; $('cBack').value = ''; drawDeck(); $('cFront').focus();
        });
        $('cards').addEventListener('click', function (e) { var b = e.target.closest('[data-rm]'); if (b) { deck().cards.splice(+b.getAttribute('data-rm'), 1); persist(); drawDeck(); } });
        $('backDecks').addEventListener('click', drawDecks);
        $('delDeck').addEventListener('click', function () {
            if (!confirm('Delete this deck?')) return;
            decks = decks.filter(function (d) { return d.id !== cur; }); persist(); drawDecks();
        });
        function shuffle(a) { a = a.slice(); for (var i = a.length - 1; i > 0; i--) { var j = Math.floor(Math.random() * (i + 1)), t = a[i]; a[i] = a[j]; a[j] = t; } return a; }
        function startStudy() { queue = shuffle(deck().cards); again = []; known = 0; round = 1; drawCard(); show('vStudy'); }
        function drawCard() {
            var card = $('card'), c = queue[0];
            card.classList.remove('flip');
            $('finished').hidden = !!c; $('studyArea').hidden = !c;
            if (!c) { $('finText').textContent = deck().cards.length + ' cards in ' + round + (round > 1 ? ' rounds' : ' round'); return; }
            $('qText').textContent = c.front; $('aText').textContent = c.back;
            $('progress').textContent = 'Round ' + round + ' · ' + (queue.length + again.length) + ' left · ' + known + ' known';
            $('answerBtns').hidden = true; $('tapHint').hidden = false;
        }
        $('card').addEventListener('click', function () {
            var card = $('card'); card.classList.toggle('flip');
            var on = card.classList.contains('flip'); $('answerBtns').hidden = !on; $('tapHint').hidden = on;
        });
        function next(knew) {
            var c = queue.shift();
            if (knew) known++; else again.push(c);
            if (!queue.length && again.length) { queue = shuffle(again); again = []; round++; }
            drawCard();
        }
        $('againBtn').addEventListener('click', function () { next(false); });
        $('knewBtn').addEventListener('click', function () { next(true); });
        $('studyBtn').addEventListener('click', startStudy);
        $('restart').addEventListener('click', startStudy);
        $('stopStudy').addEventListener('click', drawDeck);
        $('doneStudy').addEventListener('click', drawDeck);
        document.addEventListener('keydown', function (e) {
            if ($('vStudy').hidden || /INPUT|TEXTAREA/.test(e.target.tagName)) return;
            if (e.key === ' ') { e.preventDefault(); $('card').click(); }
            if (!$('answerBtns').hidden && (e.key === '1' || e.key === 'ArrowLeft')) next(false);
            if (!$('answerBtns').hidden && (e.key === '2' || e.key === 'ArrowRight')) next(true);
        });
        drawDecks();
    }
});
