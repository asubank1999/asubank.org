"""Rebuild the subject pages in /banks and the search index /assets/app/materials.json.

How to add a material to a subject page (no need to run anything):
    open banks/<subject>.html and copy one line inside <ul class="ab-items">:
        <li><a class="ab-item" href="DRIVE LINK" target="_blank" rel="noopener">Title</a></li>
    A NEW badge shows by itself for 30 days after a link is first added (dates live in
    tools/added-dates.json). Old <div class="subject-card"> snippets still work too.

Then run this script (python tools/build_banks.py) so the new item also shows in
site-wide search and the page gets tidied. It reads both the new and the old markup.
"""
import datetime
import html
import json
import re
from pathlib import Path

import added_dates

ROOT = Path(__file__).resolve().parent.parent
BANKS = ROOT / 'banks'

# slug: (title, arabic, college, emoji)
SUBJECTS = {
    'mostwanted': ('Most wanted files', 'الملفات الأكثر طلباً', 'Popular & general', '🔥'),
    'summaries': ('Summaries', 'الملخصات', 'Popular & general', '📚'),
    'presentations': ('Presentations', 'العروض التقديمية', 'Popular & general', '📂'),
    'others': ('Elective courses / other', 'المقررات الاختيارية / أخرى', 'Popular & general', '📜'),
    'unarranged': ('Unarranged – others', 'غير مصنف - أخرى', 'Popular & general', '📋'),
    'thaqafahseheya': ('Health culture', 'الثقافة الصحية', 'Popular & general', '🍎'),
    'diploma': ('Diploma courses', 'مواد الدبلوم', 'Popular & general', '🎓'),
    'medical': ('Health sciences', 'العلوم الصحية', 'Medical & health sciences', '🏥'),
    'pharmacy': ('Pharmacy', 'الصيدلة', 'Medical & health sciences', '💊'),
    'dentistry': ('Dentistry', 'طب الأسنان', 'Medical & health sciences', '🦷'),
    'nursing': ('Nursing', 'التمريض', 'Medical & health sciences', '🩺'),
    'biology': ('Biology', 'علم الأحياء', 'Medical & health sciences', '🧬'),
    'biochemistry': ('Biochemistry', 'الكيمياء الحيوية', 'Medical & health sciences', '🧪'),
    'physiology': ('Physiology', 'علم وظائف الأعضاء', 'Medical & health sciences', '🫀'),
    'lab_related': ('Lab materials', 'مواد المختبرات', 'Medical & health sciences', '⚗️'),
    'it': ('Information technology', 'تكنولوجيا المعلومات', 'Engineering & IT', '💻'),
    'engineering': ('Engineering', 'الهندسة', 'Engineering & IT', '⚙️'),
    'chemical': ('Chemical engineering', 'الهندسة الكيميائية', 'Engineering & IT', '🧫'),
    'electrical': ('Electrical engineering', 'الهندسة الكهربائية', 'Engineering & IT', '⚡'),
    'industrial': ('Industrial engineering', 'الهندسة الصناعية', 'Engineering & IT', '🏭'),
    'business': ('Business', 'الأعمال', 'Business, maths & statistics', '💼'),
    'mathematics': ('Mathematics & statistics', 'الرياضيات والإحصاء', 'Business, maths & statistics', '📐'),
    'calculus': ('All calculus', 'حساب التفاضل والتكامل', 'Business, maths & statistics', '🧮'),
    'demography': ('Demography', 'الديموغرافيا', 'Business, maths & statistics', '📊'),
    'english': ('English literature / translation', 'الأدب الإنجليزي / الترجمة', 'Arts, languages & Shariah', '📖'),
    'arabicliterature': ('Arabic literature', 'الأدب العربي', 'Arts, languages & Shariah', '📚'),
    'artshumanities': ('Arts & humanities', 'الفنون والعلوم الإنسانية', 'Arts, languages & Shariah', '🎨'),
    'hanafi': ('Shariah', 'الشريعة', 'Arts, languages & Shariah', '🕌'),
    'agricultural': ('Agricultural technology', 'التكنولوجيا الزراعية', 'Other faculties', '🌱'),
    'special': ('Special request', 'طلب خاص', 'Other faculties', '✨'),
    'law': ('Law', 'كلية الحقوق', 'Other faculties', '⚖️'),
    'facultiescourseslist': ('Faculty course lists', 'قوائم مواد الكليات', 'Other faculties', '🏛️'),
    'math': ('Math', 'الرياضيات', 'Business, maths & statistics', '➗'),
}
ARCHIVE = {
    'subjects_mobile': 'subjects_mobile',
    'subjects_android': 'subjects_android',
    'subjects_desktop_02': 'subjects_mobile',  # same files; desktop copy is UTF-16 with broken relative links
}
COLLEGE_ORDER = ['Popular & general', 'Medical & health sciences', 'Engineering & IT',
                 'Business, maths & statistics', 'Arts, languages & Shariah', 'Other faculties']

# The old math page only had dead relative links; point it at the real math pages.
MATH_ITEMS = [
    {'u': '/banks/mathematics', 't': 'Mathematics & statistics – all files', 'tag': 'pin'},
    {'u': '/banks/calculus', 't': 'All calculus – Calculus 1, 2, 3', 'tag': ''},
]


def read(p):
    b = p.read_bytes()
    if b[:2] in (b'\xff\xfe', b'\xfe\xff'):
        return b.decode('utf-16')
    return b.decode('utf-8', errors='replace')


def clean_text(s):
    s = re.sub(r'<[^>]+>', ' ', s)
    s = html.unescape(s)
    return re.sub(r'\s+', ' ', s).strip()


def parse_items(src):
    """Return [{u, t, tag}] from new (.ab-item) or old (.subject-card) markup, in page order."""
    items = []
    new = re.findall(r'<a\s+class="ab-item"([^>]*)>(.*?)</a>', src, re.S)
    if new:
        for attrs, body in new:
            u = re.search(r'href="([^"]*)"', attrs)
            tag = re.search(r'data-tag="([^"]*)"', attrs)
            items.append({'u': html.unescape(u.group(1)) if u else '', 't': clean_text(body), 'tag': tag.group(1) if tag else ''})
    # old cards: <div class="subject-card"><a href>..</a> or <a class="subject-card" href>..</a>
    for m in re.finditer(r'<div class="subject-card"[^>]*>\s*<a\s([^>]*)>(.*?)</a>|<a\s([^>]*class="subject-card"[^>]*)>(.*?)</a>', src, re.S):
        attrs = m.group(1) or m.group(3)
        body = m.group(2) or m.group(4)
        u = re.search(r'href="([^"]*)"', attrs)
        if not u:
            continue
        t = clean_text(body)
        if not t:
            continue
        tag = ''
        if 'pin.png' in body:
            tag = 'pin'
        elif 'new.gif' in body:
            tag = 'new'
        if re.search(r'root closed', t, re.I):
            tag = 'warn'
        items.append({'u': html.unescape(u.group(1)), 't': t, 'tag': tag})
    return items


def item_line(it):
    u = it['u']
    ext = u.startswith('http')
    attrs = ' target="_blank" rel="noopener"' if ext else ''
    if it.get('tag') in ('pin', 'warn'):
        attrs += f' data-tag="{it["tag"]}"'
    if it.get('d'):
        attrs += f' data-added="{it["d"]}"'
    return f'        <li><a class="ab-item" href="{html.escape(u, quote=True)}"{attrs}>{html.escape(it["t"], quote=False)}</a></li>'


HEAD = '''<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
    <title>{title_txt} | UniHub</title>
    <meta name="description" content="{desc}">
    <link rel="canonical" href="https://asubank.org/banks/{slug}">{robots}
    <meta name="theme-color" content="#f2f2f7">
    <link rel="manifest" href="/manifest.json">
    <link rel="icon" href="/assets/img/favicon2.ico" type="image/x-icon">
    <link rel="apple-touch-icon" href="/icons/apple-touch-icon.png">
    <meta name="apple-mobile-web-app-capable" content="yes">
    <meta name="mobile-web-app-capable" content="yes">
    <meta name="apple-mobile-web-app-status-bar-style" content="default">
    <meta name="apple-mobile-web-app-title" content="UniHub">
    <script>try{{var d=localStorage.getItem('darkMode');if(d==='enabled'||(d===null&&matchMedia('(prefers-color-scheme: dark)').matches))document.documentElement.classList.add('dark')}}catch(e){{}}</script>
    <link rel="stylesheet" href="/assets/app/app.css?v=3">
    <script src="/assets/app/app.js?v=2" defer></script>
</head>
'''

NAV = '''<body class="ab" data-ab-protect data-ab-recent="{title_attr}" data-ab-group="{college_attr}">
    <a class="ab-skip" href="#main">Skip to content</a>
    <header class="ab-nav">
        <div class="ab-wrap ab-nav-row">
            <div class="ab-nav-l"><a class="ab-back" href="{back}"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m15 18-6-6 6-6"/></svg>{back_label}</a></div>
            <div class="ab-nav-title" aria-hidden="true">{title}</div>
            <div class="ab-nav-r"><button class="ab-icon-btn" type="button" data-share-page aria-label="Share this page"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M12 15V3"/><path d="m8 7 4-4 4 4"/><path d="M6 11H5a1 1 0 0 0-1 1v8a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-8a1 1 0 0 0-1-1h-1"/></svg></button></div>
        </div>
    </header>
'''

SEARCH = '''        <div class="ab-search" role="search">
            <svg class="ab-s-ico" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" aria-hidden="true"><circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5"/></svg>
            <label class="ab-vh" for="q">Search this page</label>
            <input type="search" id="q" placeholder="{ph}" autocomplete="off" enterkeyhint="search">
            <button class="ab-s-clear" type="button" aria-label="Clear search"><svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="10" fill="currentColor"/><path d="m15 9-6 6m0-6 6 6" stroke="#fff" stroke-width="2" stroke-linecap="round"/></svg></button>
        </div>
        <div class="ab-chips" id="chips" role="group" aria-label="Filter"></div>
        <p class="ab-foot" id="count" role="status" aria-live="polite"></p>
'''

EMPTY = '''        <div class="ab-empty" id="empty" hidden>
            <h3>Nothing here matches</h3>
            <p>Search every file on UniHub, or ask for it and we will try to find it.</p>
            <div class="ab-btn-row" style="justify-content:center;margin-top:14px">
                <a class="ab-btn sm" href="/guides/search" data-ab-q="/guides/search?q=">Search all files</a>
                <a class="ab-btn sm tinted" href="/requests/student_request_portal">Request it</a>
            </div>
        </div>
'''

FOOT = '''        <section class="ab-section" aria-label="Help">
            <ul class="ab-list">
                <li><a class="ab-row" href="/upload/uploadmethod"><span class="ab-row-main"><span class="ab-tile emoji">📤</span><span class="ab-row-text"><span class="ab-row-title">Have a file that is not here?</span><span class="ab-row-meta">Upload it and help other students</span></span></span></a></li>
                <li><a class="ab-row" href="/requests/student_request_portal"><span class="ab-row-main"><span class="ab-tile emoji">📬</span><span class="ab-row-text"><span class="ab-row-title">Request a material</span><span class="ab-row-meta">We look for it or make a summary</span></span></span></a></li>
                <li><a class="ab-row" href="https://chat.whatsapp.com/LS92JgjO26Y5SZK9SVJ9FJ" target="_blank" rel="noopener"><span class="ab-row-main"><span class="ab-tile emoji">💬</span><span class="ab-row-text"><span class="ab-row-title">WhatsApp community</span><span class="ab-row-meta">Admin: necro144@yahoo.com</span></span></span></a></li>
            </ul>
            <p class="ab-foot">By using this website you agree to the <a href="/legal/uni_tos">Terms of Service</a>. The owner is not responsible for your exam marks. <span lang="ar" dir="rtl">باستخدام هذا الموقع فإنك توافق على <a href="/legal/uni_tos_ar">الشروط والأحكام</a>.</span></p>
        </section>
    </main>
</body>
</html>
'''


def page(slug, title, ar, college, emoji, items, archive=False):
    n = len(items)
    desc = (f'{title} at Applied Science Private University (ASU): {n} test banks, past exams, summaries and course folders, '
            f'searchable and free for students.')
    out = HEAD.format(title_txt=html.escape(f'{title} – test banks & past exams' if not archive else 'Archived materials'),
                      desc=html.escape(desc), slug=slug,
                      robots='\n    <meta name="robots" content="noindex, follow">' if archive and slug != 'subjects_mobile' else '')
    out += NAV.format(title_attr=html.escape(title), college_attr=html.escape(college), back='/#subjects',
                      back_label='Subjects', title=html.escape(title))
    out += '    <main id="main" class="ab-wrap">\n        <div class="ab-head">\n'
    out += f'            <div class="ab-eyebrow">{html.escape(college)}</div>\n'
    out += f'            <h1 class="ab-large">{html.escape(title)} <span aria-hidden="true">{emoji}</span></h1>\n'
    out += f'            <p class="ab-sub-ar" lang="ar" dir="rtl">{html.escape(ar)}</p>\n        </div>\n'
    if archive:
        out += ('        <div class="ab-notice" role="note"><span aria-hidden="true">🗄️</span><div><p><strong>Archive:</strong> this list is no longer updated. '
                'New files are in <a href="/guides/latest">Latest uploads</a> and <a href="/guides/search">Search</a>.</p>'
                '<p lang="ar" dir="rtl">هذه الصفحة لم تعد محدثة. للمواد الجديدة استخدم البحث أو آخر الملفات.</p></div></div>\n')
    out += SEARCH.format(ph=html.escape(f'Search {n} item{"s" if n != 1 else ""}'))
    out += ('        <!-- Add a material: copy one <li> line below and change the link and title. NEW shows for 30 days after a link is added. -->\n'
            f'        <ul class="ab-items" id="items" data-source="{html.escape(title)}" data-ab-filter="q" data-ab-chips="chips" data-ab-count="count" data-ab-empty="empty" data-ab-legacy>\n')
    out += '\n'.join(item_line(it) for it in items) + '\n        </ul>\n'
    out += EMPTY + FOOT
    return out


def directory_page(subjects):
    """banks/subjects: every subject page, grouped by college (was an orphan 404 copy)."""
    rows = []
    for col in COLLEGE_ORDER:
        group = [s for s in subjects if s['c'] == col]
        if not group:
            continue
        rows.append(f'        <section class="ab-section"><div class="ab-section-h"><h2>{html.escape(col)}</h2></div>\n        <ul class="ab-list">')
        for s in group:
            rows.append(f'            <li><a class="ab-row" href="{s["p"]}"><span class="ab-row-main"><span class="ab-tile emoji" aria-hidden="true">{s["e"]}</span>'
                        f'<span class="ab-row-text"><span class="ab-row-title">{html.escape(s["t"])}</span><span class="ab-row-meta">{s["n"]} items · <span lang="ar">{html.escape(s["ar"])}</span></span></span></span>'
                        '<svg class="ab-chev" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" aria-hidden="true"><path d="m9 18 6-6-6-6"/></svg></a></li>')
        rows.append('        </ul></section>')
    out = HEAD.format(title_txt='All subjects', desc='Every UniHub subject page: test banks, past exams and notes for Applied Science Private University, grouped by college.',
                      slug='subjects', robots='')
    out += NAV.format(title_attr='All subjects', college_attr='', back='/', back_label='Home', title='All subjects')
    out += ('    <main id="main" class="ab-wrap">\n        <div class="ab-head"><div class="ab-eyebrow">UniHub</div><h1 class="ab-large">All subjects</h1>'
            '<p class="ab-sub-ar" lang="ar" dir="rtl">جميع التخصصات</p></div>\n'
            '        <ul class="ab-list"><li><a class="ab-row" href="/banks/subjects_mobile"><span class="ab-row-main"><span class="ab-tile emoji">🗄️</span><span class="ab-row-text"><span class="ab-row-title">Archived materials</span><span class="ab-row-meta">Older list of 1,200+ files</span></span></span></a></li></ul>\n')
    out += '\n'.join(rows) + '\n'
    out += FOOT
    return out


def main():
    index_items, subjects, seen = [], [], set()
    dates, today = added_dates.load(), datetime.date.today().isoformat()

    def dated(items):
        for it in items:
            if it['u']:
                it['d'] = dates.setdefault(it['u'], today)
            if it.get('tag') == 'new':
                it['tag'] = ''
        return items
    for slug, (title, ar, college, emoji) in SUBJECTS.items():
        p = BANKS / f'{slug}.html'
        items = MATH_ITEMS if slug == 'math' else dated(parse_items(read(p)))
        p.write_text(page(slug, title, ar, college, emoji, items), encoding='utf-8', newline='\n')
        subjects.append({'p': f'/banks/{slug}', 't': title, 'ar': ar, 'c': college, 'e': emoji, 'n': len(items)})
        for it in items:
            if slug == 'math':
                continue
            key = (it['u'], slug)
            if key in seen or not it['u'] or it['u'] == '/':
                continue
            seen.add(key)
            index_items.append({'u': it['u'], 't': it['t'], 's': title, 'p': f'/banks/{slug}', 'c': college, 'tag': it['tag'], 'd': it.get('d', '')})
        print(f'{slug}: {len(items)}')

    archived_urls = {x['u'] for x in index_items}
    archive_cache = {}
    for slug, source in ARCHIVE.items():
        if source not in archive_cache:
            archive_cache[source] = dated(parse_items(read(BANKS / f'{source}.html')))
        items = archive_cache[source]
        (BANKS / f'{slug}.html').write_text(page(slug, 'Archived materials', 'المواد المؤرشفة', 'Archive', '🗄️', items, archive=True),
                                            encoding='utf-8', newline='\n')
        print(f'{slug}: {len(items)} (archive)')
    for source, items in archive_cache.items():
        for it in items:
            if it['u'] in archived_urls or not it['u'].startswith('http'):
                continue
            archived_urls.add(it['u'])
            index_items.append({'u': it['u'], 't': it['t'], 's': 'Archive', 'p': '/banks/subjects_mobile', 'c': 'Archive', 'tag': '', 'arch': 1, 'd': it.get('d', '')})

    (BANKS / 'subjects.html').write_text(directory_page(subjects), encoding='utf-8', newline='\n')
    data = {'subjects': subjects, 'items': index_items}
    out = ROOT / 'assets' / 'app' / 'materials.json'
    out.write_text(json.dumps(data, ensure_ascii=False, separators=(',', ':')), encoding='utf-8')
    added_dates.save(dates)
    print(f'index: {len(index_items)} items, {out.stat().st_size // 1024} KB')


if __name__ == '__main__':
    main()
