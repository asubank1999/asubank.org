"""Checks every material link students can open, and resolves Google Drive shortcuts.

Sources: assets/app/materials.json (subject banks) and the "latest uploads" Drive feed.
Writes:
  assets/app/drive-map.json  {shortcutId: [targetId, "folder"|"file"]}  used by the app and site
  assets/app/broken.json     {url: reason}  links that no longer work; the app hides them
  tools/link-report.json     every result, for maintainers

Run:  python tools/check_links.py            full check (slow on purpose: Google rate-limits)
      python tools/check_links.py --recheck  only links the last run couldn't decide, plus every dead one again
"""
import json
import random
import re
import sys
import time
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
APP = ROOT / 'assets' / 'app'
LATEST = ('https://script.google.com/macros/s/AKfycbyuzws_2LcDC8nb2KU-02swjehyRf1ivY14EbX1qcJC1chCUTLTOAAwtQ5JlXvNcLez/exec'
          '?t=1&sort=date&order=desc')
UA = {'User-Agent': 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 Safari/604.1',
      'Accept-Language': 'en'}


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *a, **k):
        return None


plain = urllib.request.build_opener()
noredir = urllib.request.build_opener(NoRedirect)


def get(url, follow=True, limit=400_000, method='GET', ua=None):
    """(status, final url, body text, location header)"""
    req = urllib.request.Request(url, headers={'User-Agent': ua} if ua else UA, method=method)
    try:
        with (plain if follow else noredir).open(req, timeout=40) as r:
            return r.status, r.geturl(), r.read(limit).decode('utf-8', 'replace'), r.headers.get('Location', '')
    except urllib.error.HTTPError as e:
        if e.code == 429:  # Google / Dropbox rate limit: wait and try again
            for wait in (20, 45, 90):
                time.sleep(wait + random.random() * 10)
                try:
                    with (plain if follow else noredir).open(req, timeout=40) as r:
                        return r.status, r.geturl(), r.read(limit).decode('utf-8', 'replace'), r.headers.get('Location', '')
                except urllib.error.HTTPError as e2:
                    if e2.code != 429:
                        return e2.code, url, '', e2.headers.get('Location', '') if e2.headers else ''
            return 429, url, '', ''
        return e.code, url, '', e.headers.get('Location', '') if e.headers else ''
    except Exception as e:  # timeouts, DNS
        return 0, url, str(e), ''


def drive_id(u):
    for p in (r'/(?:file|document|presentation|spreadsheets)/d/([\w-]{10,})', r'[?&]id=([\w-]{10,})', r'/folders/([\w-]{10,})'):
        m = re.search(p, u)
        if m:
            return m.group(1)
    return None


def check(u):
    """{'ok': bool, 'why': str, 'target': id?, 'kind': folder/file?}"""
    did = drive_id(u)
    if re.search(r'drive\.google\.com/(drive/(u/\d/)?folders|embeddedfolderview|folderview)', u) and did:
        s, final, body, _ = get(f'https://drive.google.com/embeddedfolderview?id={did}')
        if s == 404:
            return {'ok': False, 'why': 'folder deleted'}
        if 'accounts.google.com' in final or s in (401, 403):
            return {'ok': False, 'why': 'folder is private'}
        if s != 200:
            return {'ok': None, 'why': f'HTTP {s}'}
        n = len(re.findall(r'class="flip-entry"', body))
        return {'ok': True, 'why': 'empty folder' if n == 0 else f'{n} items', 'kind': 'folder', 'n': n}
    if re.search(r'drive\.google\.com/(file|open|uc)', u) and did:
        s, final, body, _ = get(f'https://drive.google.com/file/d/{did}/view')
        if s == 404:
            return {'ok': False, 'why': 'file deleted'}
        if 'accounts.google.com' in final or s in (401, 403):
            return {'ok': False, 'why': 'file is private'}
        if s != 200:
            return {'ok': None, 'why': f'HTTP {s}'}
        target = drive_id(final) or did
        r = {'ok': True, 'why': 'ok'}
        if target != did:
            r['target'] = target
            r['why'] = 'shortcut'
            if 'application/vnd.google-apps.folder' in body:
                r['kind'] = 'folder'
            else:
                r['kind'] = 'file'
        return r
    m = re.search(r'docs\.google\.com/(document|presentation|spreadsheets)/d/([\w-]+)', u)
    if m:
        s, final, _, _ = get(f'https://docs.google.com/{m.group(1)}/d/{m.group(2)}/preview')
        if s == 404:
            return {'ok': False, 'why': 'document deleted'}
        if 'accounts.google.com' in final or s in (401, 403):
            return {'ok': False, 'why': 'document is private'}
        return {'ok': s == 200 or None, 'why': 'ok' if s == 200 else f'HTTP {s}'}
    if 'dropbox.com' in u:
        dl = re.sub(r'([?&])dl=0', r'\1dl=1', u) if 'dl=0' in u else u + ('&' if '?' in u else '?') + 'dl=1'
        s, _, _, loc = get(dl, follow=False, limit=1, method='HEAD', ua='curl/8.4.0')
        if s in (301, 302, 303, 307) and 'dropbox' in loc and 'login' not in loc:
            return {'ok': True, 'why': 'ok'}
        if s == 404 or s == 200:
            time.sleep(3)
            s2, _, _, loc2 = get(dl, follow=False, limit=1, method='HEAD', ua='curl/8.4.0')
            if s2 in (301, 302, 303, 307) and 'dropbox' in loc2:
                return {'ok': True, 'why': 'ok'}
            return {'ok': False, 'why': 'Dropbox link removed'} if s2 in (200, 404) else {'ok': None, 'why': f'HTTP {s2}'}
        return {'ok': None, 'why': f'HTTP {s}'}
    if u.startswith('/') or 'asubank.org' in u or re.match(r'^(mailto|tel):', u):
        return {'ok': True, 'why': 'site page'}
    if not re.match(r'^https?://', u):
        return {'ok': False, 'why': 'not a link'}
    if re.search(r'chat\.whatsapp\.com|wa\.me|t\.me|instagram\.com|sharepoint\.com', u):
        return {'ok': True, 'why': 'not checked (app link / needs sign-in)'}
    s, _, _, _ = get(u, limit=2000)
    if s in (404, 410):
        return {'ok': False, 'why': f'HTTP {s}'}
    return {'ok': True if 200 <= s < 400 else None, 'why': f'HTTP {s}'}


def main():
    items = json.loads((APP / 'materials.json').read_text(encoding='utf-8'))['items']
    urls = {i['u']: i['t'] for i in items}
    try:
        s, _, body, _ = get(LATEST, limit=50_000_000)
        for f in json.loads(body).get('files', []):
            urls.setdefault(f['url'], f['name'])
    except Exception as e:
        print('latest feed failed:', e, file=sys.stderr)
    print(len(urls), 'links', flush=True)

    recheck = '--recheck' in sys.argv
    old = {}
    rp = ROOT / 'tools' / 'link-report.json'
    if recheck and rp.exists():
        old = json.loads(rp.read_text(encoding='utf-8'))
    results = {u: r for u, r in old.items() if u in urls and r.get('ok') is True}
    todo = [u for u in urls if u not in results]
    print(len(todo), 'to check', flush=True)

    def run(u):
        time.sleep(0.4)
        r = check(u)
        if r['ok'] is None:  # retry flaky ones once
            r = check(u)
        return u, r
    with ThreadPoolExecutor(3) as ex:
        for n, (u, r) in enumerate(ex.map(run, todo), 1):
            results[u] = {**r, 't': urls[u]}
            if n % 200 == 0:
                print(n, flush=True)

    dmap = {}
    for u, r in results.items():
        if r.get('target'):
            dmap[drive_id(u)] = [r['target'], r.get('kind', 'file')]
        elif r.get('kind') == 'folder' and '/folders/' not in u:
            dmap[drive_id(u)] = [drive_id(u), 'folder']
    broken = {u: r['why'] for u, r in results.items() if r['ok'] is False}
    (APP / 'drive-map.json').write_text(json.dumps(dmap, separators=(',', ':')), encoding='utf-8')
    (APP / 'broken.json').write_text(json.dumps(broken, ensure_ascii=False, indent=0), encoding='utf-8')
    (ROOT / 'tools' / 'link-report.json').write_text(json.dumps(results, ensure_ascii=False, indent=1), encoding='utf-8')
    from collections import Counter
    print(Counter(r['why'] if not r['why'].endswith('items') else 'folder ok' for r in results.values()).most_common())
    print('shortcuts resolved:', len(dmap), ' broken:', len(broken), ' unknown:', sum(r['ok'] is None for r in results.values()))


if __name__ == '__main__':
    main()
