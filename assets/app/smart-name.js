/* UniHub smart file uploader: suggests a clean file name (and subject, type, year) from what is inside a file.
 * Everything runs in the browser; nothing is sent anywhere before the student presses Upload.
 *   PDF          pdf.js (cdnjs, loaded on first use): document title, biggest line on page 1, first pages' text
 *   Word / PPT   the .docx/.pptx zip itself: core.xml title, first paragraphs / first slide
 *   Photos       tesseract.js (jsdelivr, loaded on first use, English): biggest text line
 *   Anything     the file name, cleaned (IMG_2024..., WhatsApp Image..., underscores, (1) ...)
 * Used by upload-page.js: SmartName.analyze(file, items) -> Promise<{ name, heading, kind, year, chapter, bank }>. */
(function () {
    'use strict';
    var PDFJS = 'https://cdnjs.cloudflare.com/ajax/libs/pdf.js/3.11.174/';
    var TESS = 'https://cdn.jsdelivr.net/npm/tesseract.js@5.1.1/dist/tesseract.min.js';
    var loaded = {};
    function load(src) {
        if (!loaded[src]) loaded[src] = new Promise(function (res, rej) {
            var s = document.createElement('script'); s.src = src; s.async = true;
            s.onload = res; s.onerror = function () { delete loaded[src]; rej(new Error('load ' + src)); };
            document.head.appendChild(s);
        });
        return loaded[src];
    }
    function withTimeout(p, ms) {
        return Promise.race([p, new Promise(function (_, rej) { setTimeout(function () { rej(new Error('timeout')); }, ms); })]);
    }

    // ---------- reading the file ----------
    function readPdf(file) {
        return load(PDFJS + 'pdf.min.js').then(function () {
            var lib = window.pdfjsLib;
            lib.GlobalWorkerOptions.workerSrc = PDFJS + 'pdf.worker.min.js';
            return file.arrayBuffer().then(function (buf) { return lib.getDocument({ data: buf, isEvalSupported: false }).promise; });
        }).then(function (pdf) {
            var out = { title: '', heading: '', text: '' };
            var meta = pdf.getMetadata().then(function (m) { out.title = (m && m.info && m.info.Title) || ''; }, function () {});
            var pages = [];
            for (var i = 1; i <= Math.min(2, pdf.numPages); i++) pages.push(pdf.getPage(i).then(function (pg) { return pg.getTextContent(); }));
            return Promise.all([meta, Promise.all(pages)]).then(function (r) {
                r[1].forEach(function (tc, n) {
                    var lines = {}, order = [];
                    tc.items.forEach(function (it) {
                        if (!it.str || !it.str.trim()) return;
                        var h = Math.round(Math.abs(it.transform[3]) || it.height || 0), y = Math.round(it.transform[5]);
                        var key = y + ':' + h;
                        if (!lines[key]) { lines[key] = { h: h, s: '' }; order.push(key); }
                        lines[key].s += (lines[key].s ? ' ' : '') + it.str.trim();
                    });
                    var ls = order.map(function (k) { return lines[k]; });
                    out.text += ls.map(function (l) { return l.s; }).join('\n') + '\n';
                    if (n === 0) out.heading = biggest(ls);
                });
                pdf.destroy && pdf.destroy();
                return out;
            });
        });
    }
    function biggest(lines) {
        var good = lines.filter(function (l) { return usefulLine(l.s); });
        if (!good.length) return '';
        var max = Math.max.apply(null, good.map(function (l) { return l.h; }));
        // join neighbouring lines of the same (largest) size: titles often wrap
        var top = good.filter(function (l) { return l.h >= max * 0.92; }).slice(0, 2).map(function (l) { return l.s; });
        return top.join(' ');
    }
    function usefulLine(s) {
        s = s.trim();
        if (s.length < 4 || s.length > 90) return false;
        if (!/[A-Za-z؀-ۿ]{3}/.test(s)) return false;
        if (/^(page|slide)\s*\d+/i.test(s) || /^\d+\s*(of|\/)\s*\d+$/.test(s)) return false;
        if (/university|جامعة|faculty of|كلية|department of|قسم|copyright|all rights|www\.|https?:|@/i.test(s)) return false;
        return true;
    }

    // minimal zip reader for .docx / .pptx (stored or deflated entries, DecompressionStream)
    function zipEntries(buf) {
        var v = new DataView(buf), n = buf.byteLength, eocd = -1;
        for (var i = n - 22; i >= Math.max(0, n - 66000); i--) if (v.getUint32(i, true) === 0x06054b50) { eocd = i; break; }
        if (eocd < 0) return {};
        var count = v.getUint16(eocd + 10, true), p = v.getUint32(eocd + 16, true), map = {};
        for (var k = 0; k < count && p + 46 <= n; k++) {
            if (v.getUint32(p, true) !== 0x02014b50) break;
            var method = v.getUint16(p + 10, true), csize = v.getUint32(p + 20, true);
            var nl = v.getUint16(p + 28, true), el = v.getUint16(p + 30, true), cl = v.getUint16(p + 32, true), off = v.getUint32(p + 42, true);
            var name = new TextDecoder().decode(new Uint8Array(buf, p + 46, nl));
            map[name] = { method: method, csize: csize, off: off };
            p += 46 + nl + el + cl;
        }
        return map;
    }
    function zipText(buf, e) {
        if (!e) return Promise.resolve('');
        var v = new DataView(buf), start = e.off + 30 + v.getUint16(e.off + 26, true) + v.getUint16(e.off + 28, true);
        var data = new Uint8Array(buf, start, e.csize);
        if (e.method === 0) return Promise.resolve(new TextDecoder().decode(data));
        if (e.method !== 8 || typeof DecompressionStream === 'undefined') return Promise.resolve('');
        var stream = new Blob([data]).stream().pipeThrough(new DecompressionStream('deflate-raw'));
        return new Response(stream).text();
    }
    function xmlText(xml, tag) {
        var out = [], re = new RegExp('<' + tag + '(?:\\s[^>]*)?>([^<]*)</' + tag + '>', 'g'), m;
        while ((m = re.exec(xml))) out.push(m[1]);
        return out;
    }
    function unescapeXml(s) { return s.replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&quot;/g, '"').replace(/&apos;/g, "'").replace(/&amp;/g, '&'); }
    function readOffice(file) {
        return file.arrayBuffer().then(function (buf) {
            var z = zipEntries(buf), out = { title: '', heading: '', text: '' };
            var body = z['word/document.xml'] || z['ppt/slides/slide1.xml'];
            return Promise.all([zipText(buf, z['docProps/core.xml']), zipText(buf, body)]).then(function (r) {
                out.title = unescapeXml((xmlText(r[0], 'dc:title')[0] || '').trim());
                var paras = r[1].split(/<\/(?:w|a):p>/).map(function (p) { return unescapeXml(xmlText(p, '(?:w|a):t').join('')).trim(); }).filter(Boolean);
                out.text = paras.slice(0, 80).join('\n');
                out.heading = paras.filter(usefulLine)[0] || '';
                return out;
            });
        });
    }
    function readPhoto(file) {
        return load(TESS).then(function () {
            return window.Tesseract.recognize(file, 'eng');
        }).then(function (r) {
            var d = r.data || {}, lines = (d.lines || []).filter(function (l) { return l.confidence > 55; });
            var ls = lines.map(function (l) { return { h: l.bbox.y1 - l.bbox.y0, s: l.text.replace(/\s+/g, ' ').trim() }; });
            return { title: '', heading: biggest(ls), text: d.text || '' };
        });
    }

    // ---------- understanding it ----------
    var KINDS = [
        ['Test bank', /test\s*bank|question\s*bank|بنك\s*(ال)?اسئل/i],
        ['Final', /\bfinals?\b|final\s*exam|فاينل|نهائي/i],
        ['Midterm', /\bmid(-?term)?\b|first\s*exam|second\s*exam|ميد|نصفي|الامتحان\s*الاول|الامتحان\s*الثاني/i],
        ['Quiz', /\bquiz(zes)?\b|كويز/i],
        ['Assignment', /\bassignment|homework|\bhw\s*\d|واجب/i],
        ['Summary', /\bsummary|summarized|ملخص|تلخيص/i],
        ['Slides', /\bslides?\b|\blecture\s*\d|\bchapter\s*\d+\s*[:\-–]|محاضرة/i],
        ['Notes', /\bnotes\b|ملاحظات|دوسية|دوسيه/i]
    ];
    function findKind(s) { for (var i = 0; i < KINDS.length; i++) if (KINDS[i][1].test(s)) return KINDS[i][0]; return ''; }
    function findYear(s) {
        var now = new Date().getFullYear(), best = '', m, re = /(?:^|[^\d])(20[12]\d)(?:\s*[\/\-]\s*(20[12]\d))?(?!\d)/g;
        while ((m = re.exec(s))) {
            // 2024/2025: first-semester exams are in 2024, second-semester and summer ones in 2025
            var y = +(m[2] && /second|summer|الثاني|الصيفي/i.test(s.slice(Math.max(0, m.index - 40), m.index + 20)) ? m[2] : m[1]);
            if (y >= 2010 && y <= now + 1) { best = String(y); break; }
        }
        return best;
    }
    function findChapter(s) {
        var m = /\b(?:chapter|ch\.?|lecture|lec\.?|unit|week)\s*[-#:]?\s*(\d{1,2})\b/i.exec(s);
        if (!m) return '';
        var w = /lec/i.test(m[0]) ? 'Lecture' : /unit/i.test(m[0]) ? 'Unit' : /week/i.test(m[0]) ? 'Week' : 'Chapter';
        return w + ' ' + (+m[1]);
    }

    // which bank (subject) the text belongs to, learnt from the titles already in materials.json
    var GENERAL = /most wanted|summaries|presentations|unarranged|special request|elective|faculty course|lab materials/i;
    var STOP = /^(the|and|for|with|from|this|that|exam|final|finals|midterm|quiz|summary|notes|slides|chapter|lecture|first|second|third|part|file|files|doctor|university|semester|course|introduction|intro|principles|page|pdf|docx|pptx|بنك|امتحان|ملخص|مادة|الفصل|الاول|الثاني|نهائي|محاضرة)$/;
    var bankIndex = null;
    function words(s) {
        return (window.AB && AB.norm ? AB.norm(s) : String(s).toLowerCase()).split(/[^a-z0-9؀-ۿ]+/)
            .filter(function (w) { return w.length >= 4 && !/^\d+$/.test(w) && !STOP.test(w); });
    }
    function buildIndex(items) {
        var idx = { w: {}, banks: {} };
        (items || []).forEach(function (it) {
            if (!it.s || GENERAL.test(it.s) || it.arch) return;
            idx.banks[it.s] = 1;
            words(it.t).forEach(function (w) { var e = idx.w[w] || (idx.w[w] = {}); e[it.s] = (e[it.s] || 0) + 1; });
        });
        return idx;
    }
    function findBank(text, items) {
        if (!bankIndex && items) bankIndex = buildIndex(items);
        if (!bankIndex) return '';
        var score = {}, seen = {};
        words(text.slice(0, 4000)).forEach(function (w) {
            if (seen[w]) return; seen[w] = 1;
            var e = bankIndex.w[w]; if (!e) return;
            var tot = 0, b; for (b in e) tot += e[b];
            var nb = Object.keys(e).length; if (nb > 4) return; // too common across faculties
            for (b in e) score[b] = (score[b] || 0) + (e[b] / tot) * Math.min(3, 1 + Math.log(1 + e[b])) / nb;
        });
        var ranked = Object.keys(score).sort(function (a, b) { return score[b] - score[a]; });
        if (!ranked.length || score[ranked[0]] < 1.5) return '';
        if (ranked[1] && score[ranked[1]] > score[ranked[0]] * 0.7) return '';
        return ranked[0];
    }

    // ---------- the name ----------
    function cleanName(name) {
        var base = name.replace(/\.[^.]+$/, '');
        base = base.replace(/^(img|image|dsc|pxl|photo|scan|scanned|cam|screenshot|whatsapp image|whatsapp document|document|doc|file|new doc|microsoft word|cs|camscanner)[\s_\-]*/i, '')
            .replace(/\b(19|20)\d{6}[\s_\-]*\d{0,6}\b/g, ' ')            // 20241103_142233
            .replace(/\b\d{4}-\d{2}-\d{2}( at [\d.]+( [ap]m)?)?/gi, ' ')     // 2024-11-03 at 14.22.33
            .replace(/\(\d+\)|\bcopy( of)?\b|\bfinal_?final\b/gi, ' ')
            .replace(/[_+]+/g, ' ').replace(/\s*-\s*/g, ' - ').replace(/\s+/g, ' ').replace(/^[\s\-.]+|[\s\-.]+$/g, '');
        if (!/[A-Za-z؀-ۿ]{3}/.test(base)) return '';
        return tidy(base);
    }
    function tidy(s) {
        s = String(s).replace(/^microsoft (word|powerpoint) - /i, '').replace(/\.(docx?|pptx?|pdf)$/i, '')
            .replace(/[\\/:*?"<>|\u0000-\u001f]+/g, ' ').replace(/\s+/g, ' ').trim();
        if (s === s.toUpperCase() && /[A-Z]{4}/.test(s)) s = s.toLowerCase();       // SHOUTING -> Title Case
        if (s === s.toLowerCase()) s = s.replace(/(^|[\s(\-])([a-z])/g, function (m, a, b) { return a + b.toUpperCase(); });
        return s.length > 80 ? s.slice(0, 80).replace(/\s+\S*$/, '') : s;
    }
    function goodTitle(t) {
        t = tidy(t || '');
        if (!usefulLine(t)) return '';
        if (/^(untitled|slide ?\d*|presentation\d*|document\d*|powerpoint presentation|microsoft word|title)$/i.test(t)) return '';
        return t;
    }
    function compose(info, fallback) {
        var head = goodTitle(info.heading) || goodTitle(info.title) || fallback || '';
        var low = head.toLowerCase(), parts = [head];
        if (info.chapter && low.indexOf(info.chapter.toLowerCase()) < 0 && !/chapter|lecture|unit|week/i.test(low)) parts.push(info.chapter);
        if (info.kind && low.indexOf(info.kind.toLowerCase()) < 0 && !(info.kind === 'Midterm' && /\bmid/i.test(low))) parts.push(info.kind);
        if (info.year && low.indexOf(info.year) < 0) parts.push(info.year);
        return parts.filter(Boolean).join(' ').replace(/\s+/g, ' ').trim();
    }

    function analyze(file, items) {
        var ext = (file.name.match(/\.[^.]+$/) || [''])[0].toLowerCase();
        var reader = ext === '.pdf' ? readPdf
            : /\.(docx|pptx)$/.test(ext) ? readOffice
            : /^image\/(jpeg|png|webp)$/.test(file.type) || /\.(jpe?g|png|webp)$/.test(ext) ? readPhoto
            : null;
        var fallback = cleanName(file.name);
        var read = reader ? withTimeout(reader(file), reader === readPhoto ? 60000 : 25000).catch(function () { return { title: '', heading: '', text: '' }; })
            : Promise.resolve({ title: '', heading: '', text: '' });
        return read.then(function (r) {
            // the cleaned name: WhatsApp/camera dates in file names are upload dates, not exam years
            var head = [r.title, r.heading, fallback].join(' ');
            var all = [r.title, r.heading, fallback, r.text].join('\n');
            var info = {
                heading: r.heading, title: r.title,
                kind: findKind(head) || findKind(r.text.slice(0, 1500)),
                year: findYear(head) || findYear(r.text.slice(0, 2500)),
                chapter: findChapter(head) || findChapter(r.text.slice(0, 600)),
                bank: findBank(all, items)
            };
            info.name = compose(info, fallback);
            info.read = !!(r.heading || r.title || r.text);
            return info;
        });
    }
    window.SmartName = { analyze: analyze, cleanName: cleanName, tidy: tidy, _find: { kind: findKind, year: findYear, chapter: findChapter } };
})();
