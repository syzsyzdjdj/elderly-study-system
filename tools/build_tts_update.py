"""Package tested TTS code, offline course audio, web assets and signed Android update."""
from pathlib import Path
import concurrent.futures
import hashlib
import io
import json
import subprocess
import tarfile
import wave
import zipfile

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT/'work/tts-release'
payload = OUT/'tts-update'
payload.mkdir(exist_ok=True)
prepared = payload/'prepared'
prepared.mkdir(exist_ok=True)
manifest = json.loads((OUT/'manifest.json').read_text(encoding='utf-8'))

def encode(item):
    key = item['key']
    source = OUT/'system-course-audio/prepared'/f'{key}.wav'
    with wave.open(str(source)) as sound:
        frames = sound.readframes(sound.getnframes())
        assert sound.getnframes() > 0 and any(frames), 'Empty/silent recording'
    target = prepared/f'{key}.mp3'
    if target.exists() and target.stat().st_size > 100: return str(target)
    subprocess.run(['E:/ffmpeg/bin/ffmpeg.exe','-hide_banner','-loglevel','error','-y',
                    '-i',str(source),'-ac','1','-codec:a','libmp3lame','-b:a','48k',str(target)],check=True)
    assert target.stat().st_size > 100
    return str(target)

with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
    list(pool.map(encode,manifest))
with zipfile.ZipFile(ROOT/'work/cloud-build/backend/elderly-study-backend-1.0.0.jar') as old, \
     zipfile.ZipFile(OUT/'backend/elderly-study-backend-1.0.0.jar') as new:
    changed = [n for n in new.namelist() if n.startswith('BOOT-INF/classes/') and not n.endswith('/')
               and (n not in old.namelist() or new.read(n)!=old.read(n))]
    for i, name in enumerate(changed): (payload/f'entry-{i}').write_bytes(new.read(name))
    entries = {n:f'entry-{i}' for i,n in enumerate(changed)}
apk = ROOT/'android-app/app/build/outputs/apk/release/app-release.apk'
apk_data = apk.read_bytes()
metadata = dict(version='1.2.0', minAndroid='7.0',date='2026-11-03',url='/downloads/elderly-study-1.2.0.apk',
                size=len(apk_data),sha256=hashlib.sha256(apk_data).hexdigest())
(payload/'latest.json').write_text(json.dumps(metadata,ensure_ascii=False,indent=2),encoding='utf-8')
(payload/'manifest.json').write_text(json.dumps(dict(
    base_sha256='973dada4c897f0db794426db4d9a1ba5c41e14f53505f8245dc59cee4e563757',entries=entries,
    audio={p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in prepared.iterdir()},
    sentences=manifest),ensure_ascii=False),encoding='utf-8')
with tarfile.open(OUT/'tts-update.tar.gz','w:gz') as archive:
    for file in sorted(payload.rglob('*')):
        if file.is_file(): archive.add(file,arcname='tts-update/'+file.relative_to(payload).as_posix())
    for file in sorted((OUT/'frontend/assets').iterdir()): archive.add(file,arcname='tts-update/frontend/assets/'+file.name)
    archive.add(OUT/'frontend/index.html',arcname='tts-update/frontend/index.html')
    archive.add(apk,arcname='tts-update/elderly-study-1.2.0.apk')
    archive.add(ROOT/'deploy/apply-tts-update.py',arcname='tts-update/apply.py')
patch = OUT/'tts-update.tar.gz'
print(json.dumps(dict(bytes=patch.stat().st_size,sha256=hashlib.sha256(patch.read_bytes()).hexdigest(),
                     audio=len(manifest),apk=metadata),ensure_ascii=False))
