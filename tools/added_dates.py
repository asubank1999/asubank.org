"""When was each link first added to the site? Used to show NEW for 30 days only.

tools/added-dates.json maps link -> first date seen (YYYY-MM-DD). It was seeded from git history;
build_banks.py adds today's date for links it has not seen before, so no need to run this again
unless the file is lost:  python tools/added_dates.py
"""
import html
import json
import re
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
FILE = ROOT / 'tools' / 'added-dates.json'


def from_git():
    out = subprocess.run(['git', 'log', '--reverse', '--date=short', '--format=@@%ad', '-p', '--unified=0', '--', '*.html'],
                         cwd=ROOT, capture_output=True).stdout.decode('utf-8', 'ignore')
    seen, date = {}, None
    for line in out.splitlines():
        if line.startswith('@@') and len(line) == 12:
            date = line[2:]
        elif line.startswith('+') and not line.startswith('+++') and 'href=' in line:
            for u in re.findall(r'href="([^"]+)"', line):
                u = html.unescape(u)
                seen.setdefault(u, date)
    return seen


def load():
    if FILE.exists():
        return json.loads(FILE.read_text(encoding='utf-8'))
    d = from_git()
    save(d)
    return d


def save(d):
    FILE.write_text(json.dumps(dict(sorted(d.items())), ensure_ascii=False, indent=0), encoding='utf-8', newline='\n')


if __name__ == '__main__':
    d = from_git()
    save(d)
    print(len(d), 'links')
