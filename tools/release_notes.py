"""Prints what is new in one version, for its GitHub Release, so a release never repeats the older ones.

  python tools/release_notes.py ios 1.14.0
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
HEAD = re.compile(r"^(?:#+\s*What's new in\s+|\*\*New in\s+)(\d+(?:\.\d+)+)")
END = re.compile(r'^(#+\s|\*\*[^*]+\*\*\s*:?)')


def section(platform, version):
    keep, lines = False, []
    for line in open(os.path.join(ROOT, platform, 'RELEASE_NOTES.md'), encoding='utf-8'):
        line = line.rstrip()
        m = HEAD.match(line)
        if m:
            if keep:
                break
            keep = m.group(1) == version
        elif keep:
            if line.startswith('- '):
                lines.append(line)
            elif (not line and lines) or END.match(line):
                break
    return lines


if __name__ == '__main__':
    got = section(sys.argv[1], sys.argv[2])
    print("## What's new in " + sys.argv[2] + '\n')
    print('\n'.join(got))
