"""Copies the YouTube links students shared into assets/app/materials.json, so the apps' search has them with no app update.
Keeps tools/synced-links.json so a link deleted on the website also leaves the index. Checks each new link really exists on YouTube."""
import json
import pathlib
import urllib.error
import urllib.parse
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parent.parent
API = 'https://script.google.com/macros/s/AKfycbzXDE0Km2mxL_gAJFqspZeaADtugupeLN0YQlkEbXnrCkSTKBfImW_lLCKF8_SMI0i_/exec'
INDEX = ROOT / 'assets' / 'app' / 'materials.json'
SYNCED = ROOT / 'tools' / 'synced-links.json'


def exists(url):
    """404 or 400 means gone. Anything else, such as embedding being off, keeps the link."""
    q = 'https://www.youtube.com/oembed?format=json&url=' + urllib.parse.quote(url, safe='')
    try:
        urllib.request.urlopen(urllib.request.Request(q, headers={'User-Agent': 'UniHub link check'}), timeout=20).read()
        return True
    except urllib.error.HTTPError as e:
        return e.code not in (400, 404)
    except Exception:
        return True


req = urllib.request.Request(API, data=b'{"action":"linksList"}', headers={'Content-Type': 'text/plain;charset=utf-8'})
reply = json.loads(urllib.request.urlopen(req, timeout=120).read().decode('utf-8'))
if not reply.get('ok') or not isinstance(reply.get('links'), list):
    raise SystemExit('linksList failed: ' + str(reply)[:200])
data = json.loads(INDEX.read_text(encoding='utf-8'))
old = set(json.loads(SYNCED.read_text(encoding='utf-8'))) if SYNCED.exists() else set()
pages = {s['p'] for s in data['subjects']}
live = [l for l in reply['links'] if l.get('p') in pages and l.get('u', '').startswith('https://www.youtube.com/')]
live_urls = {l['u'] for l in live}
items = [i for i in data['items'] if not (i['u'] in old and i['u'] not in live_urls)]
have = {i['u'] for i in items}
added = []
for l in live:
    if l['u'] not in have and exists(l['u']):
        added.append({'u': l['u'], 't': l['t'], 's': l['s'], 'p': l['p'], 'c': l.get('c', ''), 'tag': '', 'd': l['d']})
synced = sorted((old & live_urls) | {a['u'] for a in added})
if added or len(items) != len(data['items']) or synced != sorted(old):
    data['items'] = added + items
    INDEX.write_text(json.dumps(data, ensure_ascii=False, separators=(',', ':')), encoding='utf-8')
    SYNCED.write_text(json.dumps(synced, indent=0) + '\n', encoding='utf-8')
print('links', len(live), 'added', len(added), 'index', len(data['items']))
