"""Read-only public HTTPS check for all recordings and the signed app download."""
from pathlib import Path
import concurrent.futures
import hashlib
import json
import urllib.request

import argparse

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--base', required=True, help='Explicit HTTPS deployment origin')
    args = parser.parse_args()
    base = args.base.rstrip('/')
    if not base.startswith('https://'): parser.error('--base must use HTTPS')
    manifest = json.loads(Path('work/tts-release/manifest.json').read_text(encoding='utf-8'))
    def check(item):
        path = '/uploads/tts/prepared/'+item['key']+'.mp3'
        with urllib.request.urlopen(base+path,timeout=30) as response:
            audio = response.read()
            expected = Path('work/tts-release/tts-update/prepared')/(item['key']+'.mp3')
            assert response.status == 200 and response.headers.get('Content-Type','').startswith('audio/')
            assert hashlib.sha256(audio).digest() == hashlib.sha256(expected.read_bytes()).digest()
        return len(audio)
    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as executor:
        sizes = list(executor.map(check,manifest))
    with urllib.request.urlopen(base+'/downloads/latest.json',timeout=30) as response: metadata=json.load(response)
    assert metadata['version']=='1.2.0'
    with urllib.request.urlopen(base+metadata['url'],timeout=30) as response:
        data=response.read()
        assert len(data)==metadata['size'] and hashlib.sha256(data).hexdigest()==metadata['sha256']
    result=dict(recordings=len(sizes),bytes=sum(sizes),apk=metadata,public_https=True)
    Path('work/tts-release/public-verification.json').write_text(json.dumps(result,indent=2),encoding='utf-8')
    print(json.dumps(result))

if __name__ == '__main__':
    main()
