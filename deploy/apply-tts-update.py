"""Run with sudo from the unpacked tts-update directory; retains rollback copies."""
from pathlib import Path
import hashlib
import json
import os
import shutil
import subprocess
import zipfile

root = Path(__file__).resolve().parent
manifest = json.loads((root/'manifest.json').read_text())
jar = Path('/opt/elderly-study/backend.jar')
assert hashlib.sha256(jar.read_bytes()).hexdigest() == manifest['base_sha256'], 'Unexpected backend; abort'
backup = jar.with_name('backend.before-course-tts.jar')
assert not backup.exists(), 'Backup exists; abort'
for name,digest in manifest['audio'].items():
    assert '/' not in name and '..' not in name
    assert hashlib.sha256((root/'prepared'/name).read_bytes()).hexdigest() == digest
metadata = json.loads((root/'latest.json').read_text())
assert hashlib.sha256((root/'elderly-study-1.2.0.apk').read_bytes()).hexdigest() == metadata['sha256']
shutil.copy2(jar, backup)
temporary = jar.with_name('backend.course-tts.tmp')
with zipfile.ZipFile(jar) as src, zipfile.ZipFile(temporary,'w') as dst:
    seen = set()
    for info in src.infolist():
        replacement = manifest['entries'].get(info.filename)
        dst.writestr(info, (root/replacement).read_bytes() if replacement else src.read(info.filename))
        seen.add(info.filename)
    for name,file in manifest['entries'].items():
        if name not in seen: dst.writestr(name,(root/file).read_bytes(),compress_type=zipfile.ZIP_DEFLATED)
with zipfile.ZipFile(temporary) as checked: assert checked.testzip() is None
os.chmod(temporary,0o644)
web = Path('/var/www/elderly-study')
shutil.copy2(web/'index.html',root/'index.before.html')
shutil.copy2(web/'downloads/latest.json',root/'latest.before.json')
for path in (root/'frontend/assets').iterdir(): shutil.copy2(path,web/'assets'/path.name)
prepared = Path('/var/lib/elderly-study/uploads/tts/prepared')
prepared.mkdir(parents=True,exist_ok=True)
for path in (root/'prepared').iterdir():
    shutil.copy2(path,prepared/path.name)
    os.chmod(prepared/path.name,0o644)
subprocess.run(['chown','-R','elderly:elderly',str(prepared)],check=True)
shutil.copy2(root/'elderly-study-1.2.0.apk',web/'downloads/elderly-study-1.2.0.apk')
config = Path('/etc/elderly-study/backend.env')
shutil.copy2(config,root/'backend.env.before')
os.chmod(root/'backend.env.before',0o600)
lines = [line for line in config.read_text().splitlines() if not line.startswith('TTS_MODE=')]
config.write_text('\n'.join(lines+['TTS_MODE=system'])+'\n')
os.chmod(config,0o600)
os.replace(temporary,jar)
shutil.copy2(root/'frontend/index.html',web/'index.html')
shutil.copy2(root/'latest.json',web/'downloads/latest.json')
subprocess.run(['systemctl','restart','elderly-study'],check=True)
print('TTS_UPDATE_APPLIED',len(manifest['audio']),'recordings; model disabled on server',flush=True)
