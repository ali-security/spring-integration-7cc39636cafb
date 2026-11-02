#!/usr/bin/env python3
"""Summarize JUnit XML results of one Gradle module and give the reason for every skipped test.

Usage: test-report.py <module-dir>
"""
import glob
import os
import re
import sys
import xml.etree.ElementTree as ET

module = sys.argv[1]
reports = sorted(glob.glob(os.path.join(module, 'build', 'test-results', 'test', 'TEST-*.xml')))
if not reports:
    print('No JUnit XML reports found under %s' % module)
    sys.exit(1)

src_roots = [os.path.join(module, 'src', 'test', d) for d in ('java', 'kotlin')]
annotation = re.compile(r'@(Ignore|Disabled)\b.*')


def source_lines(classname):
    rel = classname.split('$')[0].replace('.', os.sep)
    for root in src_roots:
        for ext in ('.java', '.kt'):
            path = os.path.join(root, rel + ext)
            if os.path.isfile(path):
                with open(path, encoding='utf-8', errors='replace') as f:
                    return f.read().splitlines()
    return []


def reason_for(classname, method):
    lines = source_lines(classname)
    if not lines:
        return 'no source found'
    name = method.split('(')[0].split('[')[0].strip()
    decl = re.compile(r'\b(void|fun)\s+`?' + re.escape(name) + r'`?\s*\(')
    for i, line in enumerate(lines):
        if decl.search(line):
            for j in range(i - 1, max(i - 8, -1), -1):
                m = annotation.search(lines[j])
                if m:
                    return 'method annotated %s' % m.group(0).strip()
            break
    for line in lines:
        m = annotation.search(line)
        if m and line.lstrip().startswith('@'):
            # class-level annotation precedes the class declaration
            idx = lines.index(line)
            if any(re.search(r'\bclass\b', l) for l in lines[idx + 1:idx + 4]):
                return 'class annotated %s' % m.group(0).strip()
    for line in lines:
        if 'LongRunningIntegrationTest' in line:
            return ('LongRunningIntegrationTest rule: assumption requires RUN_LONG_INTEGRATION_TESTS=true '
                    '(set only by the upstream testAll task, not by test/check)')
    return 'assumption not met at runtime'


tests = failures = errors = skipped = 0
skipped_rows = []
for path in reports:
    root = ET.parse(path).getroot()
    tests += int(root.get('tests', 0))
    failures += int(root.get('failures', 0))
    errors += int(root.get('errors', 0))
    skipped += int(root.get('skipped', 0))
    for case in root.iter('testcase'):
        sk = case.find('skipped')
        if sk is not None:
            cls = case.get('classname')
            name = case.get('name')
            msg = (sk.get('message') or '').strip()
            reason = msg if msg else reason_for(cls, name)
            skipped_rows.append('%s.%s -> %s' % (cls, name, reason))

print('=' * 70)
print('SKIPPED TESTS in %s (%d):' % (module, len(skipped_rows)))
for row in skipped_rows:
    print('  SKIPPED ' + row)
print('=' * 70)
passed = tests - failures - errors - skipped
print('JUNIT XML TOTALS %s: %d test classes, %d tests, %d passed, %d failed, %d errors, %d skipped'
      % (module, len(reports), tests, passed, failures, errors, skipped))
print('=' * 70)
sys.exit(1 if failures or errors else 0)
