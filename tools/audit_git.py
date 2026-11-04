"""Audit a Git snapshot without printing secret values. Read-only with respect to Git."""
import argparse
from collections import Counter
import io
import json
from pathlib import Path
import re
import subprocess
import sys
import os
import hashlib

ROOT = Path(__file__).resolve().parents[1]
TEXT = {'.java', '.js', '.mjs', '.py', '.ps1', '.sh', '.yml', '.yaml', '.json',
        '.xml', '.md', '.properties', '.gradle', '.example', '.sql', '.txt', '.html', '.css'}
KEY = re.compile(rb'\bsk-[A-Za-z0-9_-]{20,}\b')
YAML_SECRET = re.compile(rb'^[ \t]*(?:secret|api[-_]key|jdbc[-_]password|password):[ \t]*([^\r\n]*)$', re.M | re.I)
PRIVATE_KEY = re.compile(rb'-----BEGIN (?:RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----')
ENV_SECRET = re.compile(rb'^[ \t]*(?:DB_PASSWORD|DB_MIGRATION_PASSWORD|JWT_SECRET|DEEPSEEK_API_KEY|QWEATHER_API_KEY|BOOTSTRAP_ADMIN_PASSWORD)[ \t]*=[ \t]*([^\r\n]*)$', re.M)

# Exported-source scans do not need Git. Keep this allow-list shared with public packaging.
SOURCE_DIRS = ('backend', 'frontend', 'spark-analysis', 'android-app', 'shared', 'tools', 'deploy', 'docs', '.github')
SOURCE_FILES = ('.gitignore', '.gitattributes', 'README.md', 'LICENSE', 'NOTICE.md', 'SECURITY.md')
SKIP_PARTS = {'node_modules', 'target', 'dist', '__pycache__', '.git', '.gradle', '.idea', 'build', 'logs', 'uploads', 'spark-tmp'}
MEDIA_SUFFIXES = {'.png', '.jpg', '.jpeg', '.webp', '.gif', '.mp3', '.mp4', '.wav', '.svg', '.ico'}
PRIVATE_NAMES = {'local.properties', 'id_rsa', 'id_ed25519', 'credentials.json', 'test-account.json',
                 'release.properties', 'initial-admin.txt', 'known_hosts'}


def public_media(root):
    manifest = root / 'docs/public-media.json'
    if not manifest.is_file(): return {}
    rows = json.loads(manifest.read_text(encoding='utf-8'))['files']
    media = {}
    for row in rows:
        path = row['path']
        relative = Path(path)
        if relative.is_absolute() or '..' in relative.parts or not path.startswith('docs/screenshots/') \
                or relative.suffix.lower() not in MEDIA_SUFFIXES or not re.fullmatch('[a-f0-9]{64}', row['sha256']):
            raise ValueError('Invalid public media manifest entry')
        if path in media: raise ValueError('Duplicate public media manifest entry: ' + path)
        media[path] = row['sha256']
    return media

def source_files(root=ROOT):
    media = public_media(root)
    for name in SOURCE_FILES:
        if (root / name).is_file(): yield root / name
    for directory in SOURCE_DIRS:
      for current, directories, filenames in os.walk(root / directory, followlinks=False):
        directories[:] = sorted(d for d in directories if d not in SKIP_PARTS and not (Path(current) / d).is_symlink())
        # Only the external local-config directory is private; Java config packages are source.
        if Path(current) == root / 'backend' and 'config' in directories:
            directories.remove('config')
        for filename in sorted(filenames):
            file = Path(current) / filename
            relative = file.relative_to(root)
            if any(part in SKIP_PARTS for part in relative.parts) or not file.is_file(): continue
            if file.is_symlink() or not file.resolve().is_relative_to(root.resolve()):
                raise ValueError('Source path escapes root: ' + relative.as_posix())
            name = file.name
            if name.startswith('.env') and not name.endswith('.example'): continue
            if name in PRIVATE_NAMES: continue
            if file.suffix.lower() in {'.key', '.pem', '.cnf', '.env', '.jks', '.keystore', '.p12', '.pfx', '.pyc', '.docx', '.pptx', '.pdf', '.log', '.db', '.sqlite', '.sqlite3', '.ibd', '.sftp', '.bak', '.zip', '.gz', '.tar'}: continue
            if relative.parts[:2] == ('frontend', 'public') and relative.parts[2:3] in [('tutorials',), ('voice-previews',)]: continue
            if file.suffix.lower() in MEDIA_SUFFIXES:
                if relative.as_posix() not in media: continue
            yield file

def git(*args, data=None):
    return subprocess.check_output(['git', *args], input=data, cwd=ROOT)

def paths(*args):
    return [p.decode('utf-8') for p in git(*args, '-z').split(b'\0') if p]

def credential_findings(path, data):
    findings = []
    if KEY.search(data): findings.append({'path': path, 'rule': 'api-key-literal'})
    if PRIVATE_KEY.search(data): findings.append({'path': path, 'rule': 'private-key-literal'})
    for match in ENV_SECRET.finditer(data):
        value = match.group(1).strip().strip(b'\'"')
        shell_reference = re.fullmatch(rb'\$[A-Za-z_][A-Za-z0-9_]*', value)
        generated_secret = path.endswith('.sh') and re.fullmatch(rb'\$\(openssl rand -hex [0-9]+\)', value)
        if value and not value.startswith((b'${', b'#')) and not shell_reference and not generated_secret:
            findings.append({'path': path, 'rule': 'env-secret-literal'})
            break
    if path.lower().endswith(('.yml', '.yaml', '.yml.example', '.yaml.example')):
        for match in YAML_SECRET.finditer(data):
            value = match.group(1).strip().strip(b'\'"')
            if value and not value.startswith((b'${', b'#')):
                findings.append({'path': path, 'rule': 'yaml-secret-literal'})
                break
    return findings

def text_file(path):
    return Path(path).suffix.lower() in TEXT or Path(path).name in {'.gitignore', '.env.example'}

def scan_source_tree(root=ROOT):
    files = list(source_files(root))
    findings = []
    media = public_media(root)
    for name, expected in media.items():
        file = root / name
        if not file.is_file(): findings.append({'path': name, 'rule': 'approved-media-missing'})
        elif hashlib.sha256(file.read_bytes()).hexdigest() != expected:
            findings.append({'path': name, 'rule': 'approved-media-changed-requires-review'})
    for file in files:
        name = file.relative_to(root).as_posix()
        size = file.stat().st_size
        if size > 20 * 1024 * 1024: findings.append({'path': name, 'rule': 'large-file-over-20MiB'})
        if text_file(name) and size <= 2 * 1024 * 1024:
            findings.extend(credential_findings(name, file.read_bytes()))
    return {'scope': 'source-tree', 'files': len(files), 'findings': findings}

def history_findings():
    # Every reachable historical blob, including deleted files, rather than only application.yml.
    entries = git('rev-list', '--objects', '--all').decode('utf-8').splitlines()
    if not entries: return []
    names = dict(row.split(' ', 1) for row in entries if ' ' in row)
    metadata = git('cat-file', '--batch-check', data=('\n'.join(names) + '\n').encode()).splitlines()
    selected = []
    findings = []
    for row in metadata:
        oid, kind, size = row.decode().split()
        if kind != 'blob': continue
        if int(size) > 20 * 1024 * 1024:
            findings.append({'path': names[oid], 'rule': 'historical-large-file', 'blob': oid})
        if int(size) <= 2 * 1024 * 1024: selected.append(oid)
    if not selected: return findings
    # Stream one blob at a time so large histories do not accumulate in Python memory.
    proc = subprocess.Popen(['git', 'cat-file', '--batch'], cwd=ROOT, stdin=subprocess.PIPE, stdout=subprocess.PIPE)
    try:
        for oid in selected:
            proc.stdin.write((oid + '\n').encode()); proc.stdin.flush()
            header = proc.stdout.readline().split()
            data = proc.stdout.read(int(header[2])); proc.stdout.read(1)
            for finding in credential_findings(names[oid], data):
                findings.append({**finding, 'rule': 'historical-' + finding['rule'], 'blob': oid})
    finally:
        proc.stdin.close(); proc.stdout.close(); proc.wait()
    return findings

def audit(worktree=False, history=False):
    findings = []
    ignored = set(paths('ls-files', '-ci', '--exclude-standard'))
    if worktree:
        names = sorted(set(paths('ls-files', '--cached', '--others', '--exclude-standard')) - ignored)
        for name in names:
            file = ROOT / name
            if not file.is_file(): continue
            size = file.stat().st_size
            if size > 20 * 1024 * 1024: findings.append({'path': name, 'rule': 'large-file-over-20MiB'})
            if text_file(name) and size <= 2 * 1024 * 1024:
                findings.extend(credential_findings(name, file.read_bytes()))
    else:
        findings.extend({'path': p, 'rule': 'ignored-but-tracked'} for p in sorted(ignored))
        entries = []
        for record in git('ls-files', '--stage', '-z').split(b'\0'):
            if not record: continue
            metadata, name = record.split(b'\t', 1)
            mode, oid, stage = metadata.split()
            if stage != b'0':
                findings.append({'path': name.decode(), 'rule': 'unmerged-index'})
                continue
            entries.append((name.decode('utf-8'), oid.decode('ascii')))
        names = [name for name, _ in entries]
        ids = sorted({oid for _, oid in entries})
        sizes = {}
        if ids:
            output = git('cat-file', '--batch-check', data=('\n'.join(ids)+'\n').encode())
            sizes = {row.split()[0].decode(): int(row.split()[2]) for row in output.splitlines()}
        selected = {oid for name, oid in entries if text_file(name) and sizes[oid] <= 2*1024*1024}
        blobs = {}
        if selected:
            stream = io.BytesIO(git('cat-file', '--batch', data=('\n'.join(sorted(selected))+'\n').encode()))
            while True:
                header = stream.readline()
                if not header: break
                oid, kind, size = header.split()
                blobs[oid.decode()] = stream.read(int(size))
                stream.read(1)
        for name, oid in entries:
            if sizes[oid] > 20*1024*1024: findings.append({'path': name, 'rule': 'large-file-over-20MiB'})
            if oid in blobs: findings.extend(credential_findings(name, blobs[oid]))
    if history:
        findings.extend(history_findings())
    return {'scope': 'worktree' if worktree else 'index', 'files': len(names), 'findings': findings}

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--worktree', action='store_true')
    parser.add_argument('--source-tree', action='store_true', help='Scan public source allow-list without Git metadata')
    parser.add_argument('--history', action='store_true', help='Also inspect every blob reachable from local Git refs')
    parser.add_argument('--json-output', type=Path)
    args = parser.parse_args()
    if args.source_tree and (args.worktree or args.history):
        parser.error('--source-tree cannot be combined with Git modes')
    try:
        result = scan_source_tree() if args.source_tree else audit(args.worktree, args.history)
    except subprocess.CalledProcessError:
        print('Git audit unavailable; exported sources can use --source-tree. No audit passed.', file=sys.stderr)
        raise SystemExit(2)
    if args.json_output:
        args.json_output.parent.mkdir(parents=True, exist_ok=True)
        args.json_output.write_text(json.dumps(result, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    summary = dict(Counter(item['rule'] for item in result['findings']))
    print(json.dumps({'scope': result['scope'], 'files': result['files'], 'issues': len(result['findings']), 'rules': summary}, ensure_ascii=False))
    raise SystemExit(1 if result['findings'] else 0)
