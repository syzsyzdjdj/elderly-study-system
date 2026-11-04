"""Render a course-manifest JSON using a local ChatTTS HTTP service; resume safely.
Usage: python tools/generate_course_audio.py manifest.json --output work/course-audio
Only course sentences in the supplied manifest are sent to the configured service.
"""
import argparse
import hashlib
import json
from pathlib import Path
import urllib.request
import wave
import io
import tarfile

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('manifest')
    parser.add_argument('--url', default='http://127.0.0.1:5050/generate')
    parser.add_argument('--output', default='work/course-audio')
    args = parser.parse_args()
    items = json.loads(Path(args.manifest).read_text(encoding='utf-8-sig'))
    if isinstance(items, dict): items = items['data']
    output = Path(args.output)
    prepared = output / 'prepared'
    prepared.mkdir(parents=True, exist_ok=True)
    for index, item in enumerate(items):
        key = hashlib.sha256(item['text'].encode()).hexdigest()
        assert key == item['key'], 'Manifest text/hash mismatch'
        file = prepared / (key + '.wav')
        if file.exists():
            with wave.open(str(file)) as sound: assert sound.getnframes() > 0
            continue
        data = json.dumps({'text': item['text'], 'voice': 'default'}, ensure_ascii=False).encode()
        request = urllib.request.Request(args.url, data=data, headers={'Content-Type': 'application/json'})
        with urllib.request.urlopen(request, timeout=600) as response: audio = response.read()
        with wave.open(io.BytesIO(audio)) as sound:
            assert sound.getnframes() > 0 and sound.getframerate() > 0, 'Empty audio'
        temporary = file.with_suffix('.tmp')
        temporary.write_bytes(audio)
        temporary.replace(file)
        print(f'{index+1}/{len(items)} {item["title"]} {len(audio)} bytes', flush=True)
    (output/'manifest.json').write_text(json.dumps(items, ensure_ascii=False, indent=2), encoding='utf-8')
    with tarfile.open(output/'course-audio.tar.gz', 'w:gz') as archive:
        for item in items:
            file = prepared/(item['key']+'.wav')
            archive.add(file, arcname='prepared/'+file.name)
    print('DONE', len(items), output/'course-audio.tar.gz', flush=True)

if __name__ == '__main__': main()
