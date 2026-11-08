"""Turn asubank.org pages into /assets/app/pages.json for the iOS app.

The app shows each page as native iOS screens (headings, text, link rows, tables, FAQs,
calendars) instead of a web view, so it always matches the website. Run after changing a page:
    python tools/build_app_pages.py
The "App pages" GitHub workflow also runs it on every push.

Pages that are really Google Apps Script forms or embeds (rating, matcher, booking...) are
marked "web": the app opens those in its in-app browser.
"""
import html
import json
import re
from html.parser import HTMLParser
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SKIP_DIRS = {'archive', 'tools', 'assets', 'icons', 'banks', 'ios', '.github', '.git', 'qrscan'}
SKIP_FILES = {'404.html', 'offline.html', 'guides/latest_updates.html', 'guides/search.html', 'guides/saved.html',
              'upload/uploadmethod.html', 'index.html',
              'guides/final-mark.html', 'guides/absences.html', 'guides/flashcards.html'}
DROP_TAGS = {'script', 'style', 'noscript', 'svg', 'button', 'nav', 'footer', 'head', 'template', 'form', 'select',
             'input', 'textarea', 'label', 'canvas'}
DROP_CLASS = re.compile(r'toggle|theme|back-?(btn|button|link)|scroll|fab|mode-|lang-btn|language-toggle|ab-tabbar|loading|spinner|sr-only|empty|no-?results|not-?found|ab-nav|ab-skip|web-only', re.I)
VOID = {'br', 'img', 'hr', 'meta', 'link', 'input', 'source', 'wbr', 'area', 'col', 'embed', 'param', 'track'}
BLOCK = {'p', 'div', 'section', 'article', 'main', 'aside', 'header', 'ul', 'ol', 'li', 'table', 'h1', 'h2', 'h3', 'h4',
         'h5', 'h6', 'details', 'summary', 'blockquote', 'figure', 'iframe', 'img', 'dl', 'dt', 'dd', 'tr', 'pre'}
EMOJI = re.compile(r'^\s*([\U0001F000-\U0001FAFF☀-➿⬀-⯿][️‍\U0001F000-\U0001FAFF☀-➿]*)\s*')

# Calendar pages fill their table from these scripts
CALENDARS = {
    'schedules/calendar27.html': 'assets/js/asu_date_script2026_27.js',
    'schedules/cal2026.html': 'assets/js/asu_date_script2025_2026.js',
    'schedules/asu_date_asu2025_2026.html': 'assets/js/asu_date_script2025_2026.js',
    'schedules/asu_date_asu.html': 'assets/js/asu_date_script.js',
}


class Node:
    __slots__ = ('tag', 'attrs', 'kids', 'parent')

    def __init__(self, tag, attrs=None, parent=None):
        self.tag, self.attrs, self.kids, self.parent = tag, dict(attrs or {}), [], parent

    def cls(self):
        return (self.attrs.get('class') or '') + ' ' + (self.attrs.get('id') or '')

    def text(self):
        out = []
        for k in self.kids:
            if isinstance(k, str):
                out.append(k)
            elif k.tag == 'br':
                out.append('\n')
            elif k.tag not in DROP_TAGS:
                out.append(k.text())
                if k.tag in BLOCK:
                    out.append('\n')
        return ''.join(out)

    def find(self, pred):
        for k in self.kids:
            if isinstance(k, Node):
                if pred(k):
                    return k
                r = k.find(pred)
                if r:
                    return r
        return None

    def has_block(self):
        return any(isinstance(k, Node) and (k.tag in BLOCK or k.tag == 'a' or k.has_block()) for k in self.kids)


class Tree(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.root = Node('root')
        self.cur = self.root

    def handle_starttag(self, tag, attrs):
        n = Node(tag, attrs, self.cur)
        self.cur.kids.append(n)
        if tag not in VOID:
            self.cur = n

    def handle_startendtag(self, tag, attrs):
        self.cur.kids.append(Node(tag, attrs, self.cur))

    def handle_endtag(self, tag):
        n = self.cur
        while n is not self.root and n.tag != tag:
            n = n.parent
        if n is not self.root:
            self.cur = n.parent

    def handle_data(self, data):
        self.cur.kids.append(data)


def clean(s):
    s = re.sub(r'[ \t\r\f\v ]+', ' ', s or '')
    s = re.sub(r' *\n[ \n]*', '\n', s)
    return s.strip()


def one_line(s):
    return re.sub(r'\s+', ' ', s or '').strip()


def norm_url(u):
    u = html.unescape((u or '').strip())
    u = re.sub(r'^https?://(www\.)?asubank\.org', '', u)
    if u.startswith('/') and u != '/':
        u = re.sub(r'\.html?(?=$|[?#])', '', u).rstrip('/')
    return u


def dropped(n):
    if n.tag in DROP_TAGS:
        return True
    if DROP_CLASS.search(n.cls()):
        return True
    if n.tag == 'a' and norm_url(n.attrs.get('href')) in ('/', '/index', '#', ''):
        return True
    return False


class Builder:
    def __init__(self):
        self.blocks = []
        self.title = None

    def add(self, b):
        # merge consecutive link rows and list items into one block
        if self.blocks and 'links' in b and 'links' in self.blocks[-1]:
            self.blocks[-1]['links'] += b['links']
        elif self.blocks and 'list' in b and 'list' in self.blocks[-1]:
            self.blocks[-1]['list'] += b['list']
        else:
            self.blocks.append(b)

    def link(self, a):
        href = a.attrs.get('href') or ''
        if href.startswith(('javascript:', '#')) or not href:
            text = clean(a.text())
            if text:
                self.add({'p': text})
            return
        title_n = a.find(lambda k: k.tag in ('h1', 'h2', 'h3', 'h4', 'strong', 'b') or re.search(r'title|name', k.cls(), re.I))
        sub_n = a.find(lambda k: k.tag == 'p' or re.search(r'desc|sub|meta', k.cls(), re.I))
        badge_n = a.find(lambda k: re.search(r'badge|tag\b|pill', k.cls(), re.I))
        icon_n = a.find(lambda k: re.search(r'icon|emoji', k.cls(), re.I))
        badge = one_line(badge_n.text()) if badge_n else ''
        title = one_line(title_n.text()) if title_n else ''
        full = one_line(a.text())
        if badge and title.endswith(badge):
            title = title[: -len(badge)].strip()
        if not title:
            title = full
            for n in (sub_n, badge_n):
                if n:
                    title = title.replace(one_line(n.text()), '').strip()
        sub = one_line(sub_n.text()) if sub_n else ''
        if sub == title:
            sub = ''
        emoji = ''
        m = EMOJI.match(one_line(icon_n.text()) if icon_n else title)
        if m:
            emoji = m.group(1)
            title = EMOJI.sub('', title, count=1) if not icon_n else title
        title = re.sub(r'^[\s·•|:\-–]+|[\s·•|:\-–]+$', '', title)
        if not title and not sub:
            return
        row = {'t': title or sub, 'u': norm_url(href)}
        if sub and title:
            row['s'] = sub[:220]
        if badge and len(badge) < 24:
            row['b'] = badge
        if emoji:
            row['e'] = emoji
        self.add({'links': [row]})

    def table(self, t):
        rows = []
        for tr in iter_tag(t, 'tr'):
            cells = [one_line(c.text()) for c in tr.kids if isinstance(c, Node) and c.tag in ('td', 'th')]
            if any(cells):
                rows.append(cells)
        if not rows:
            return
        head_n = t.find(lambda k: k.tag == 'thead')
        first_is_head = bool(head_n) or all(isinstance(c, Node) and c.tag == 'th' for tr in [next(iter_tag(t, 'tr'), None)] if tr for c in tr.kids if isinstance(c, Node))
        b = {'table': {'r': rows[1:] if first_is_head else rows}}
        if first_is_head:
            b['table']['h'] = rows[0]
        self.add(b)

    def walk(self, n):
        for k in n.kids:
            if isinstance(k, str):
                t = clean(k)
                if t and len(t) > 1:
                    self.add({'p': t})
                continue
            if dropped(k):
                continue
            tag = k.tag
            if 'ab-section-h' in k.cls():
                h = k.find(lambda x: x.tag in ('h2', 'h3'))
                ar = k.find(lambda x: 'ar' in (x.attrs.get('class') or '').split() or x.attrs.get('lang') == 'ar')
                if h:
                    b = {'h': one_line(h.text())}
                    if ar:
                        b['ar'] = one_line(ar.text())
                    self.add(b)
                continue
            if 'ab-head' in k.cls().split():
                eb = k.find(lambda x: 'ab-eyebrow' in x.cls())
                h1 = k.find(lambda x: x.tag == 'h1')
                if h1 and not self.title:
                    self.title = one_line(h1.text())
                head = {'head': {}}
                if eb:
                    head['head']['eyebrow'] = one_line(eb.text())
                for x in iter_tag(k, 'p'):
                    head['head']['ar' if x.attrs.get('lang') == 'ar' else 'sub'] = one_line(x.text())
                self.add(head)
                continue
            if tag != 'a' and 'ab-row' in (k.attrs.get('class') or '').split():
                t = k.find(lambda x: 'ab-row-title' in x.cls())
                m = k.find(lambda x: 'ab-row-meta' in x.cls())
                e = k.find(lambda x: 'emoji' in x.cls())
                r = {'t': one_line(t.text()) if t else one_line(k.text())}
                if m:
                    r['s'] = one_line(m.text())
                if e:
                    r['e'] = one_line(e.text())
                self.add({'links': [r]})
                continue
            if tag in ('h1', 'h2', 'h3', 'h4', 'h5', 'h6'):
                t = one_line(k.text())
                if not t:
                    continue
                if tag == 'h1' and not self.title:
                    self.title = t
                elif self.is_faq(k):
                    pass
                else:
                    self.add({'h': t})
                for a in iter_tag(k, 'a'):
                    self.link(a)
            elif tag == 'a':
                self.link(k)
            elif tag == 'table':
                self.table(k)
            elif tag == 'img':
                src = k.attrs.get('src') or ''
                if src and not src.startswith('data:') and not re.search(r'logo|icon|new\.gif|pin\.png', src, re.I):
                    self.add({'img': norm_url(src) if src.startswith('/') else src, 'alt': k.attrs.get('alt') or ''})
            elif tag == 'iframe':
                src = k.attrs.get('src') or ''
                if src.startswith('http'):
                    self.add({'links': [{'t': k.attrs.get('title') or 'Open', 'u': src, 'e': '🔗'}]})
            elif tag in ('ul', 'ol'):
                for li in k.kids:
                    if not isinstance(li, Node) or dropped(li):
                        continue
                    if 'ab-row' in (li.attrs.get('class') or '').split():
                        self.walk(Node('div', {}, None) if False else _wrap(li))
                    elif li.find(lambda x: x.tag == 'a') or li.has_block():
                        self.walk(li)
                    else:
                        t = one_line(li.text())
                        if t:
                            self.add({'list': [t]})
            elif tag == 'details':
                s = k.find(lambda x: x.tag == 'summary')
                q = one_line(s.text()) if s else ''
                a = clean(k.text())[len(q):].strip() if q else clean(k.text())
                self.add({'faq': [{'q': q, 'a': a}]})
            elif self.faq_item(k):
                pass
            elif tag in ('p', 'blockquote', 'pre', 'dd', 'dt') or not k.has_block():
                t = clean(k.text())
                if t:
                    self.add({'p': t})
                for a in iter_tag(k, 'a'):
                    self.link(a)
            else:
                self.walk(k)

    def is_faq(self, h):
        return bool(h.parent and re.search(r'faq|question|accordion', h.parent.cls(), re.I))

    def faq_item(self, k):
        """<div class="faq-item"><h3>Question</h3><p>Answer</p></div>"""
        if not re.search(r'faq|question|accordion', k.cls(), re.I):
            return False
        q = k.find(lambda x: x.tag in ('h2', 'h3', 'h4', 'button', 'summary') or re.search(r'question|q\b', x.cls(), re.I))
        if not q:
            return False
        qt = one_line(q.text())
        rest = clean(k.text())
        a = rest[len(qt):].strip() if rest.startswith(qt) else rest.replace(qt, '', 1).strip()
        if not qt or not a:
            return False
        if self.blocks and 'faq' in self.blocks[-1]:
            self.blocks[-1]['faq'].append({'q': qt, 'a': a})
        else:
            self.blocks.append({'faq': [{'q': qt, 'a': a}]})
        return True


def _wrap(n):
    w = Node('div')
    w.kids = [n]
    return w


def iter_tag(n, tag):
    for k in n.kids:
        if isinstance(k, Node):
            if k.tag == tag:
                yield k
            else:
                yield from iter_tag(k, tag)


def calendar(js_path):
    s = (ROOT / js_path).read_text(encoding='utf-8')
    out = []
    for m in re.finditer(r'\{\s*date:\s*"([^"]+)"\s*,\s*day:\s*"([^"]*)"\s*,\s*name:\s*"([^"]*)"(?:\s*,\s*week:\s*(\d+))?', s):
        out.append({'d': m.group(1), 'day': m.group(2), 'n': m.group(3), 'w': int(m.group(4) or 0)})
    return out


def faq_data(raw):
    """guides/aspu_qa keeps its questions in a `faqData` script array (English + Arabic)."""
    m = re.search(r'faqData\s*=\s*\[(.*?)\n\s*\];', raw, re.S)
    if not m:
        return []

    def field(obj, k):
        f = re.search(k + r':\s*"((?:[^"\\]|\\.)*)"', obj)
        return clean(html.unescape(re.sub(r'<[^>]+>', '', f.group(1).replace('\\"', '"')))) if f else ''
    blocks, cat = [], None
    for obj in re.findall(r'\{(.*?)\}', m.group(1), re.S):
        c = field(obj, 'cat_en')
        if c != cat:
            cat = c
            blocks.append({'h': c, 'ar': field(obj, 'cat_ar'), 'e': field(obj, 'icon')})
            blocks.append({'faq': []})
        blocks[-1]['faq'].append({'q': field(obj, 'q_en'), 'a': field(obj, 'a_en'), 'qa': field(obj, 'q_ar'), 'aa': field(obj, 'a_ar')})
    return blocks


def is_web(raw):
    body = raw[raw.find('<body'):]
    return bool(re.search(r'<iframe|script\.google\.com/macros', body)) or len(re.findall(r'<input(?![^>]*type="(?:checkbox|search|radio)")|<textarea|<select', body)) >= 2


def convert(path, rel):
    raw = path.read_text(encoding='utf-8', errors='ignore')
    t = Tree()
    t.feed(raw)
    title_tag = re.search(r'<title>(.*?)</title>', raw, re.S)
    body = next(iter_tag(t.root, 'body'), t.root)
    b = Builder()
    b.walk(body)
    title = b.title or (one_line(html.unescape(title_tag.group(1))) if title_tag else rel)
    title = re.sub(r'\s*[|–-]\s*UniHub.*$', '', title)
    page = {'t': title, 'b': b.blocks}
    faqs = faq_data(raw)
    if faqs:
        page['b'] = [x for x in page['b'] if 'p' in x][:2] + faqs
    if rel in CALENDARS:
        page['cal'] = calendar(CALENDARS[rel])
        page['b'] = []
    if is_web(raw):
        page['web'] = 1
    return page


def main():
    pages = {}
    for p in sorted(ROOT.rglob('*.html')):
        rel = p.relative_to(ROOT).as_posix()
        if rel.split('/')[0] in SKIP_DIRS or rel in SKIP_FILES:
            continue
        raw = p.read_bytes()
        if raw[:2] in (b'\xff\xfe', b'\xfe\xff'):
            continue
        key = '/' + rel[:-5]
        pages[key] = convert(p, rel)
    out = ROOT / 'assets' / 'app' / 'pages.json'
    out.write_text(json.dumps(pages, ensure_ascii=False, separators=(',', ':')), encoding='utf-8', newline='\n')
    print(f'{len(pages)} pages, {out.stat().st_size // 1024} KB, web: {sum(1 for x in pages.values() if x.get("web"))}')


if __name__ == '__main__':
    main()
