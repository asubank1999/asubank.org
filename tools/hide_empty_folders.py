"""Hides empty Drive folders from "Latest uploads", and brings a folder back once it has files.

Writes:
  assets/app/broken.json     adds {url: EMPTY} for empty folders; the apps and site already hide these links
  assets/app/drive-map.json  remembers every shortcut it resolves

A folder counts as empty only when Google answers clearly, twice. A folder holding only empty
folders is empty too. If Google's answer is unclear the folder keeps its current state, and if
the results look wrong overall nothing is written.

Run:  python tools/hide_empty_folders.py       (also runs daily, .github/workflows/empty-folders.yml)
"""
import json
import re
import sys
import time
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from check_links import APP, EMPTY, LATEST, drive_id, get  # noqa: E402

SHORTCUT = 'application/vnd.google-apps.shortcut'
FOLDER = 'application/vnd.google-apps.folder'
MAX_DEPTH = 3
MAX_BAD_SHARE = 0.25  # more empty or unclear answers than this means Google's page changed or blocked us


def resolve(sid):
    """[target id, 'folder'|'file'] for a shortcut, or None when Google's answer is unclear."""
    time.sleep(0.3)
    s, final, body, _ = get(f'https://drive.google.com/file/d/{sid}/view')
    if s != 200 or 'accounts.google.com' in final:
        return None
    target = drive_id(final) or sid
    if target == sid:
        return None
    return [target, 'folder' if FOLDER in body else 'file']


def has_files(fid, depth=0):
    """True, False, or None when Google's answer is unclear."""
    time.sleep(0.3)
    s, final, body, _ = get(f'https://drive.google.com/embeddedfolderview?id={fid}')
    if s != 200 or 'accounts.google.com' in final or 'flip-entries' not in body:
        return None
    chunks = body.split('class="flip-entry" id="entry-')[1:]
    if not chunks:
        return False
    subfolders = []
    for c in chunks:
        href = re.search(r'href="([^"]+)"', c)
        if not href or '/folders/' not in href.group(1):
            return True
        subfolders.append(c.split('"')[0])
    if depth >= MAX_DEPTH:
        return True
    answers = [has_files(sub, depth + 1) for sub in subfolders]
    if any(a is True for a in answers):
        return True
    return None if any(a is None for a in answers) else False


def confirmed_has_files(fid):
    first = has_files(fid)
    return first if first is not False else has_files(fid)


def save(path, text):
    if path.read_text(encoding='utf-8') != text:
        path.write_bytes(text.encode("utf-8"))
        print('updated', path.name)


def main():
    s, _, body, _ = get(LATEST, limit=50_000_000)
    files = json.loads(body).get('files', []) if s == 200 else []
    if not files:
        print('::warning::Latest uploads feed gave nothing, changed nothing')
        return
    dmap = json.loads((APP / 'drive-map.json').read_text(encoding='utf-8'))
    broken = json.loads((APP / 'broken.json').read_text(encoding='utf-8'))

    shortcuts = [f for f in files if f.get('mimeType') == SHORTCUT]
    missing = [f['id'] for f in shortcuts if f['id'] not in dmap]
    print(len(files), 'items,', len(shortcuts), 'shortcuts,', len(missing), 'not resolved yet', flush=True)
    with ThreadPoolExecutor(4) as ex:
        for sid, t in zip(missing, ex.map(resolve, missing)):
            if t:
                dmap[sid] = t

    # url -> id of the folder it opens
    folders = {}
    for f in files:
        t = dmap.get(f['id'])
        if f.get('mimeType') == FOLDER:
            folders[f['url']] = f['id']
        elif f.get('mimeType') == SHORTCUT and t and t[1] == 'folder':
            folders[f['url']] = t[0]
    urls = list(folders)
    print(len(urls), 'folders to check', flush=True)
    with ThreadPoolExecutor(4) as ex:
        answers = dict(zip(urls, ex.map(lambda u: confirmed_has_files(folders[u]), urls)))

    empty = [u for u in urls if answers[u] is False]
    unclear = [u for u in urls if answers[u] is None]
    print(len(empty), 'empty,', len(unclear), 'unclear,', len(urls) - len(empty) - len(unclear), 'with files')
    if not urls or len(unclear) > len(urls) * MAX_BAD_SHARE or len(empty) > len(urls) * MAX_BAD_SHARE:
        print('::warning::Results look wrong, changed nothing')
        return

    feed_urls = {f['url'] for f in files}
    for u, why in list(broken.items()):
        # Back when it has files or has left the feed; unclear answers keep the current state
        if why == EMPTY and (u not in feed_urls or answers.get(u) is True):
            del broken[u]
    for u in empty:
        broken.setdefault(u, EMPTY)
    save(APP / 'broken.json', json.dumps(broken, ensure_ascii=False, indent=0))
    save(APP / 'drive-map.json', json.dumps(dmap, separators=(',', ':')))


if __name__ == '__main__':
    main()
