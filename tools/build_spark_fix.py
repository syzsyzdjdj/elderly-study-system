"""Create a small, hash-guarded update for the existing cloud deployment."""
from pathlib import Path
import hashlib
import json
import tarfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'work/spark-fix'
old = ROOT / 'work/cloud-build/backend/elderly-study-backend-1.0.0.jar'
new = OUT / 'backend/elderly-study-backend-1.0.0.jar'
payload = OUT / 'payload'
payload.mkdir(exist_ok=True)
replacements = ['BOOT-INF/classes/com/elderly/util/TaskRunner.class',
                'BOOT-INF/lib/spark-analysis-1.0.0.jar']
with zipfile.ZipFile(new) as jar:
    for i, name in enumerate(replacements):
        (payload / f'entry-{i}').write_bytes(jar.read(name))
manifest = {'base_sha256': hashlib.sha256(old.read_bytes()).hexdigest(),
            'entries': {n: f'entry-{i}' for i, n in enumerate(replacements)}}
(payload / 'manifest.json').write_text(json.dumps(manifest), encoding='utf-8')
apply_script = '''import hashlib, json, os, shutil, zipfile
from pathlib import Path
root = Path(__file__).resolve().parent
manifest = json.loads((root/'manifest.json').read_text())
jar = Path('/opt/elderly-study/backend.jar')
assert hashlib.sha256(jar.read_bytes()).hexdigest() == manifest['base_sha256'], 'Unexpected deployed JAR; aborting'
backup = jar.with_name('backend.before-spark-local.jar')
assert not backup.exists(), 'Backup exists; aborting'
shutil.copy2(jar, backup)
tmp = jar.with_name('backend.spark-local.tmp')
with zipfile.ZipFile(jar) as src, zipfile.ZipFile(tmp, 'w') as dst:
    for info in src.infolist():
        replacement = manifest['entries'].get(info.filename)
        dst.writestr(info, (root/replacement).read_bytes() if replacement else src.read(info.filename))
with zipfile.ZipFile(tmp) as check:
    assert check.testzip() is None
    for name, filename in manifest['entries'].items():
        assert check.read(name) == (root/filename).read_bytes()
        if name.startswith('BOOT-INF/lib/'):
            assert check.getinfo(name).compress_type == zipfile.ZIP_STORED
os.chmod(tmp, 0o644)
os.replace(tmp, jar)
web = Path('/var/www/elderly-study')
shutil.copy2(web/'index.html', root/'index.before.html')
for path in (root/'frontend/assets').iterdir():
    shutil.copy2(path, web/'assets'/path.name)
shutil.copy2(root/'frontend/index.html', web/'index.html')
print('PATCH_APPLIED', hashlib.sha256(jar.read_bytes()).hexdigest(), flush=True)
'''
(payload / 'apply.py').write_text(apply_script, encoding='utf-8')
with tarfile.open(OUT / 'spark-local-fix.tar.gz', 'w:gz') as archive:
    for path in sorted(payload.iterdir()):
        archive.add(path, arcname='spark-local-fix/' + path.name)
    for path in sorted((OUT / 'frontend/assets').iterdir()):
        archive.add(path, arcname='spark-local-fix/frontend/assets/' + path.name)
    archive.add(OUT/'frontend/index.html', arcname='spark-local-fix/frontend/index.html')
patch = OUT/'spark-local-fix.tar.gz'
print(json.dumps({'path':str(patch), 'bytes':patch.stat().st_size,
                  'sha256':hashlib.sha256(patch.read_bytes()).hexdigest()}))
