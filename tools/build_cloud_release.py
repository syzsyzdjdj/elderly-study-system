"""Build an explicit deployment bundle without developer credentials or signing keys."""
import hashlib
import json
from pathlib import Path
import tarfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def main():
    build = ROOT / 'work/cloud-build'
    jar = build / 'backend/elderly-study-backend-1.0.0.jar'
    apk = ROOT / 'android-app/app/build/outputs/apk/release/app-release.apk'
    dist = ROOT / 'frontend/dist'
    with zipfile.ZipFile(jar) as z:
        assert 'BOOT-INF/classes/db/migration/V25__review_course_copy.sql' in z.namelist()
        assert not any('application-local.yml' in n for n in z.namelist())
    download = build / 'downloads'
    download.mkdir(parents=True, exist_ok=True)
    apk_name = 'elderly-study-1.1.0.apk'
    raw = apk.read_bytes()
    (download / apk_name).write_bytes(raw)
    metadata = dict(version='1.1.0', minAndroid='7.0', date='2026-11-03',
                    url='/downloads/' + apk_name, size=len(raw), sha256=hashlib.sha256(raw).hexdigest())
    (download / 'latest.json').write_text(json.dumps(metadata, ensure_ascii=False, indent=2), encoding='utf-8')
    sources = json.loads((ROOT / 'docs/course-screenshot-sources.json').read_text(encoding='utf-8'))
    screenshots = {str(Path(a['path']).relative_to('frontend/public')).replace('\\', '/') for a in sources['assets']}
    files = [(jar, 'backend.jar')]
    for p in sorted(dist.rglob('*')):
        if not p.is_file():
            continue
        relative = p.relative_to(dist).as_posix()
        if relative.startswith(('tutorials/', 'voice-previews/')) and relative not in screenshots:
            continue
        if p.suffix.lower() in {'.png', '.jpg', '.jpeg', '.svg', '.webp', '.gif', '.mp3', '.wav', '.mp4'} and relative not in screenshots:
            continue
        files.append((p, 'frontend/' + relative))
    for p in download.iterdir():
        files.append((p, 'frontend/downloads/' + p.name))
    for name in ['jvm.options', 'install-small-server.sh', 'nginx-ip.conf.example', 'mysql-users.sql.example',
                 'elderly-study.service', 'elderly-cert-renew.service', 'elderly-cert-renew.timer']:
        files.append((ROOT / 'deploy' / name, 'deploy/' + name))
    for name in ['course-screenshot-sources.json', 'course-source-review.md', 'media-rights.md']:
        files.append((ROOT / 'docs' / name, 'docs/' + name))
    output = build / 'cloud-release.tar.gz'
    with tarfile.open(output, 'w:gz') as archive:
        for p, name in files:
            assert p.is_file()
            assert not name.endswith(('.jks', '.properties', '.env'))
            archive.add(p, arcname='cloud-release/' + name, recursive=False)
    digest = hashlib.sha256(output.read_bytes()).hexdigest()
    output.with_suffix('.gz.sha256').write_text(digest + '  ' + output.name + '\n', encoding='ascii')
    print(json.dumps(dict(archive=str(output), sha256=digest, files=len(files), apk=metadata), ensure_ascii=False))


if __name__ == '__main__':
    main()
