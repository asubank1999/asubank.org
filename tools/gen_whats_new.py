"""Builds the apps' What's New data from the release notes, so each release only writes its notes once.

  python tools/gen_whats_new.py

Reads ios/RELEASE_NOTES.md and android/RELEASE_NOTES.md, writes ios/ASUBank/whats-new.json and
android/app/src/main/assets/whats-new.json, newest version first. Fails if a platform's newest
notes aren't for the version it is about to build.
"""
import json
import os
import re

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
HEAD = re.compile(r"^(?:#+\s*What's new in\s+|\*\*New in\s+)(\d+(?:\.\d+)+)")
# anything else that starts a new part of the notes ends a version's list
END = re.compile(r'^(#+\s|\*\*[^*]+\*\*\s*:?)')


def parse(path):
    out, cur = [], None
    for line in open(path, encoding='utf-8'):
        line = line.rstrip()
        m = HEAD.match(line)
        if m:
            cur = {'version': m.group(1), 'changes': []}
            out.append(cur)
        elif cur is not None:
            if line.startswith('- '):
                text = re.sub(r'\[([^\]]+)\]\([^)]+\)', r'\1', line[2:]).replace('**', '').replace('`', '').strip()
                cur['changes'].append(text)
            elif (not line and cur['changes']) or END.match(line):
                cur = None  # general text after the list is not part of this version
    return [v for v in out if v['changes']]


def version_of(path, pattern):
    return re.search(pattern, open(path, encoding='utf-8').read(), re.M).group(1)


def write(src, dest, version):
    data = parse(os.path.join(ROOT, src))
    assert data and data[0]['version'] == version, f'{src}: newest notes are {data[0]["version"] if data else None}, the build is {version}'
    with open(os.path.join(ROOT, dest), 'w', encoding='utf-8', newline='\n') as f:
        json.dump(data, f, ensure_ascii=False, indent=1)
        f.write('\n')
    print(dest, len(data), 'versions')


write('ios/RELEASE_NOTES.md', 'ios/ASUBank/whats-new.json',
      version_of(os.path.join(ROOT, 'ios/project.yml'), r'MARKETING_VERSION:\s*"([\d.]+)"'))
write('android/RELEASE_NOTES.md', 'android/app/src/main/assets/whats-new.json',
      version_of(os.path.join(ROOT, 'android/app/build.gradle.kts'), r'versionName\s*=\s*"([\d.]+)"'))
