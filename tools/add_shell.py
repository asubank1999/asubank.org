"""Give every page the ASU Bank app shell (tab bar, More menu, page transitions, offline support).

Pages built on the design (<body class="ab">) already load it; older pages keep their own layout and
only get the shell added. Safe to run again: pages that already load app.js are skipped.
"""
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SKIP_DIRS = {'archive', '.git', 'tools', 'assets', 'icons'}
SKIP_FILES = {'guides/latest_updates.html', 'qrscan/scannow.html'}  # plain-text fragments, not full pages

TAGS = ('    <link rel="stylesheet" href="/assets/app/app.css?v=1">\n'
        '    <script src="/assets/app/app.js?v=1" defer></script>\n')


def main():
    changed = 0
    for p in sorted(ROOT.rglob('*.html')):
        rel = p.relative_to(ROOT).as_posix()
        if rel.split('/')[0] in SKIP_DIRS or rel in SKIP_FILES:
            continue
        raw = p.read_bytes()
        if raw[:2] in (b'\xff\xfe', b'\xfe\xff'):
            continue
        s = raw.decode('utf-8')
        if '/assets/app/app.js' in s or '</head>' not in s:
            continue
        add = TAGS
        if 'rel="manifest"' not in s:
            add = '    <link rel="manifest" href="/manifest.json">\n' + add
        if 'apple-touch-icon' not in s:
            add = '    <link rel="apple-touch-icon" href="/icons/apple-touch-icon.png">\n' + add
        s = s.replace('</head>', add + '</head>', 1)
        # let people zoom (accessibility)
        s = re.sub(r',\s*maximum-scale=1(\.0)?', '', s)
        s = re.sub(r',\s*user-scalable=(no|0)', '', s)
        p.write_bytes(s.encode('utf-8'))
        changed += 1
        print('shell:', rel)
    print(changed, 'pages updated')


if __name__ == '__main__':
    main()
