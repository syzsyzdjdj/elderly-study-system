"""Verify public HTTPS, release checksum and one explicitly disposable test account."""
import argparse
import hashlib
import json
from pathlib import Path
import secrets
import urllib.error
import urllib.request


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--base', required=True, help='Explicit HTTPS deployment origin')
    parser.add_argument('--exercise-account', action='store_true')
    args = parser.parse_args()
    base = args.base.rstrip('/')
    assert base.startswith('https://')
    results = []

    def request(path, data=None, token=None):
        headers = {}
        if token:
            headers['Authorization'] = 'Bearer ' + token
        if data is not None:
            headers['Content-Type'] = 'application/json'
            data = json.dumps(data, ensure_ascii=False).encode('utf-8')
        req = urllib.request.Request(base + path, data=data, headers=headers)
        return urllib.request.urlopen(req, timeout=60)

    def api(path, data=None, token=None):
        with request('/api' + path, data, token) as response:
            payload = json.load(response)
        assert payload['code'] == 200, (path, payload.get('msg'))
        return payload['data']

    for path, expected in [('/', b'<html'), ('/download.html', '下载'.encode())]:
        with request(path) as response:
            assert expected in response.read()
        results.append(path + ' HTTPS OK')
    with request('/downloads/latest.json') as response:
        metadata = json.load(response)
    with request(metadata['url']) as response:
        apk = response.read()
        assert response.headers.get_content_type() == 'application/vnd.android.package-archive'
        assert 'attachment' in response.headers.get('Content-Disposition', '')
    assert len(apk) == metadata['size']
    assert hashlib.sha256(apk).hexdigest() == metadata['sha256']
    assert apk.startswith(b'PK')
    results.append('APK bytes, MIME, attachment and SHA-256 OK')
    assert api('/course/list?size=50')['total'] == 21
    assert '长期有效' in api('/course/7')['course']['content']
    assert '风险降低约30%' not in api('/course/18')['course']['content']
    results.append('21 courses and V25 reviewed text OK')
    manifest = json.loads(Path('docs/course-screenshot-sources.json').read_text(encoding='utf-8'))
    for asset in manifest['assets']:
        path = '/' + str(Path(asset['path']).relative_to('frontend/public')).replace('\\', '/')
        with request(path) as response:
            assert hashlib.sha256(response.read()).hexdigest() == asset['sha256']
    results.append('12 step screenshot hashes OK')
    try:
        request('/api/study/record/list')
        raise AssertionError('Anonymous study records unexpectedly accessible')
    except urllib.error.HTTPError as e:
        assert e.code == 401
    results.append('Anonymous private API denied OK')
    if args.exercise_account:
        username = 'deploy_check_' + secrets.token_hex(4)
        password = secrets.token_urlsafe(20)
        api('/register', dict(username=username, password=password, nickname='部署验收临时账号'))
        login = api('/login', dict(username=username, password=password))
        assert login['roleKey'] == 'user'
        token = login['token']
        user_id = login['userInfo']['id']
        api('/study/record/save', dict(courseId=3, progress=25, studyTime=1), token)
        records = api('/study/record/list', token=token)
        assert any(r['courseId'] == 3 and r['progress'] == 25 for r in records)
        results.append('Registration, login and study progress round-trip OK')
        Path('work/cloud-build/test-account.json').write_text(json.dumps(dict(username=username, id=user_id)), encoding='utf-8')
    Path('work/cloud-build/verification.json').write_text(json.dumps(results, indent=2), encoding='utf-8')
    print(json.dumps(results))


if __name__ == '__main__':
    main()
