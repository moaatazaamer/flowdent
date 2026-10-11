"""Turns test results into GitHub annotations (visible on the commit's checks).

Usage: python3 .github/scripts/report.py backend|frontend
All failures are folded into a single annotation so none are lost to annotation limits.
"""
import glob
import json
import os
import re
import sys
import xml.etree.ElementTree as ET


def esc(text):
    """Escape a message for a GitHub workflow command."""
    return text.replace('%', '%25').replace('\r', '').replace('\n', '%0A')


def emit(level, title, message):
    message = message[:60000]
    print(f"::{level} title={esc(title)}::{esc(message)}")


def tail_errors(log_path, limit=60):
    if not os.path.exists(log_path):
        return []
    with open(log_path, encoding='utf-8', errors='replace') as f:
        lines = f.read().splitlines()
    picked = [l for l in lines if re.search(r'\[ERROR\]|ERROR|Error:|error |FAIL|✕|●|Failed to compile|SyntaxError|Cannot find module', l)]
    return picked[:limit]


def backend():
    total = failures = errors = skipped = 0
    details = []
    for path in sorted(glob.glob('backend-service/target/surefire-reports/TEST-*.xml')):
        root = ET.parse(path).getroot()
        total += int(root.get('tests', 0))
        failures += int(root.get('failures', 0))
        errors += int(root.get('errors', 0))
        skipped += int(root.get('skipped', 0))
        for case in root.iter('testcase'):
            for kind in ('failure', 'error'):
                node = case.find(kind)
                if node is not None:
                    body = (node.get('message') or '') + '\n' + (node.text or '')
                    body = '\n'.join(body.strip().splitlines()[:25])
                    details.append(f"[{kind}] {case.get('classname')}.{case.get('name')}\n{body}")
    if total == 0:
        logs = []
        for log in ('build-eureka.log', 'build-gateway.log', 'build-backend.log'):
            logs += [f'{log}: {l}' for l in tail_errors(log)]
        emit('error', 'Backend: no test results (build/compile failed?)', '\n'.join(logs) or 'no log output')
        return 1
    summary = f'{total} tests, {failures} failures, {errors} errors, {skipped} skipped'
    if details:
        emit('error', f'Backend tests failed: {summary}', '\n\n'.join(details))
        return 1
    emit('notice', 'Backend tests passed', summary)
    return 0


def frontend():
    path = 'jest-results.json'
    if not os.path.exists(path):
        logs = [f'install: {l}' for l in tail_errors('install-frontend.log')]
        logs += [f'test: {l}' for l in tail_errors('test-frontend.log')]
        emit('error', 'Frontend: no test results (install or test run failed?)', '\n'.join(logs) or 'no log output')
        return 1
    with open(path, encoding='utf-8') as f:
        data = json.load(f)
    details = []
    for suite in data.get('testResults', []):
        name = os.path.relpath(suite.get('name', ''), os.getcwd())
        if suite.get('status') == 'failed' and not suite.get('assertionResults'):
            details.append(f'{name}\n{suite.get("message", "")[:3000]}')
        for t in suite.get('assertionResults', []):
            if t.get('status') == 'failed':
                msg = '\n'.join(('\n'.join(t.get('failureMessages', []))).splitlines()[:25])
                details.append(f"{name} › {t.get('fullName')}\n{msg}")
    summary = (f"{data.get('numTotalTests')} tests, {data.get('numPassedTests')} passed, "
               f"{data.get('numFailedTests')} failed, {data.get('numFailedTestSuites')} failed files")
    build_errors = tail_errors('build-frontend.log')
    if details:
        emit('error', f'Frontend tests failed: {summary}', '\n\n'.join(details))
        return 1
    if any('Failed to compile' in l for l in build_errors):
        emit('error', 'Frontend build failed', '\n'.join(build_errors))
        return 1
    emit('notice', 'Frontend tests passed', summary)
    return 0


if __name__ == '__main__':
    sys.exit(backend() if sys.argv[1] == 'backend' else frontend())
