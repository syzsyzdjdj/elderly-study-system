"""Create a server kit from an explicit allow-list; never bundles local configuration or user data."""
import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import tarfile
from datetime import datetime, timezone
import uuid
import zipfile
import io
import subprocess

ROOT = Path(__file__).resolve().parents[1]

def sha256_file(path):
    digest = hashlib.sha256()
    with path.open('rb') as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(chunk)
    return digest.hexdigest()


def source_identity():
    """Fingerprint source/config inputs, including uncommitted changes; never reads private config."""
    roots = ['frontend/src', 'backend/src/main', 'spark-analysis/src/main', 'android-app/app/src', 'shared', 'tools', '.github']
    suffixes = {'.java', '.js', '.mjs', '.vue', '.css', '.json', '.sql', '.xml', '.py', '.yml', '.yaml'}
    sources = [p for root in roots for p in (ROOT / root).rglob('*')
               if p.is_file() and p.suffix in suffixes and not p.is_symlink()
               and '__pycache__' not in p.parts
               and p.name not in {'application-local.yml', 'application-private.yml'}]
    for name in ['frontend/package.json', 'frontend/package-lock.json', 'frontend/vite.config.js',
                 'frontend/index.html', 'backend/pom.xml', 'spark-analysis/pom.xml',
                 'android-app/build.gradle', 'android-app/app/build.gradle', 'android-app/gradle.properties']:
        if (ROOT / name).is_file(): sources.append(ROOT / name)
    fingerprints = {p.relative_to(ROOT).as_posix(): sha256_file(p) for p in sorted(set(sources))}
    snapshot = hashlib.sha256(json.dumps(fingerprints, sort_keys=True).encode()).hexdigest()
    result = subprocess.run(['git', '-C', str(ROOT), 'rev-parse', 'HEAD'], capture_output=True, text=True)
    return {'revision': result.stdout.strip() if result.returncode == 0 else None,
            'sourceSha256': snapshot, 'sourceFiles': fingerprints}

def package(jar, frontend, output, include_verified_media=False):
    jar = jar.resolve(strict=True)
    frontend = frontend.resolve(strict=True)
    if not (frontend / 'index.html').is_file():
        raise ValueError('Frontend dist/index.html is missing')
    with zipfile.ZipFile(jar) as z:
        names = z.namelist()
        if 'BOOT-INF/classes/db/migration/V1__base_schema.sql' not in names:
            raise ValueError('Backend jar is outdated: V1 migration missing')
        if 'BOOT-INF/classes/com/elderly/config/InitialAdminBootstrap.class' not in names:
            raise ValueError('Backend jar is outdated: admin bootstrap missing')
        if any('application-local.yml' in name for name in names):
            raise ValueError('Backend jar contains private development configuration')
    files = [(jar, 'backend.jar')]
    for file in sorted(frontend.rglob('*')):
        if file.is_file():
            relative = file.relative_to(frontend)
            if not include_verified_media and (relative.parts[0] in ('tutorials', 'voice-previews')
                    or file.suffix.lower() in {'.png', '.jpg', '.jpeg', '.svg', '.webp', '.gif', '.mp3', '.mp4', '.wav', '.ico'}):
                continue
            if not file.resolve().is_relative_to(frontend):
                raise ValueError('Frontend asset escapes dist')
            files.append((file, 'frontend/' + file.relative_to(frontend).as_posix()))
    for file in sorted((ROOT / 'deploy').iterdir()):
        if file.is_file() and (file.name.endswith(('.example', '.service', '.timer', '.sh'))
                              or file.name in ('jvm.options', 'seed-assets.json', 'application-tts-http.yml')):
            files.append((file, 'deploy/' + file.name))
    for name in ['server-deployment.md', 'tts-deployment.md', 'first-deployment.md']:
        files.append((ROOT / 'docs' / name, 'docs/' + name))
    files.append((ROOT / 'docs/first-deployment.md', 'README.md'))
    assets = json.loads((ROOT / 'deploy/seed-assets.json').read_text(encoding='utf-8'))
    if not include_verified_media:
        assets = []
    for uri in assets:
        relative = PurePosixPath(uri.lstrip('/'))
        if '..' in relative.parts or not relative.is_relative_to('uploads/images'):
            raise ValueError('Invalid seed asset manifest entry')
        choices = [ROOT / 'assets/seed' / str(relative), ROOT / str(relative), ROOT / 'backend' / str(relative)]
        file = next((p for p in choices if p.is_file()), None)
        if file is None or not any(file.resolve().is_relative_to(root) for root in [ROOT/'assets/seed/uploads', ROOT/'uploads', ROOT/'backend/uploads']):
            raise ValueError('Seed asset is missing or escapes upload directory: ' + uri)
        files.append((file, relative.as_posix()))
    output.mkdir(parents=True, exist_ok=True)
    name = 'elderly-study-' + datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ') + '-' + uuid.uuid4().hex[:6] + '.tar.gz'
    archive = output / name
    partial = archive.with_name(name + '.partial')
    manifest = {'schemaVersion': 1, 'releaseMode': 'complete',
                'createdAt': datetime.now(timezone.utc).isoformat(),
                'source': source_identity(),
                'files': {destination: sha256_file(source) for source, destination in files},
                'migrations': sorted(n.rsplit('/', 1)[-1] for n in names
                                     if n.startswith('BOOT-INF/classes/db/migration/') and n.endswith(('.sql', '.class')))}
    metadata = json.dumps(manifest, ensure_ascii=False, indent=2).encode('utf-8')
    with tarfile.open(partial, 'w:gz') as tar:
        info = tarfile.TarInfo('release-manifest.json')
        info.size = len(metadata)
        tar.addfile(info, io.BytesIO(metadata))
        for source, destination in files:
            tar.add(source, arcname=destination, recursive=False)
    with tarfile.open(partial, 'r:gz') as check:
        for destination, expected in manifest['files'].items():
            digest = hashlib.sha256()
            with check.extractfile(destination) as stream:
                for chunk in iter(lambda: stream.read(1024 * 1024), b''):
                    digest.update(chunk)
            if digest.hexdigest() != expected:
                raise ValueError('File changed during packaging: ' + destination)
    partial.replace(archive)
    hasher = hashlib.sha256()
    with archive.open('rb') as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b''):
            hasher.update(chunk)
    digest = hasher.hexdigest()
    archive.with_name(name + '.sha256').write_text(digest + '  ' + name + '\n', encoding='utf-8')
    print(json.dumps({'archive': str(archive.resolve()), 'sha256': digest, 'files': len(files), 'seedAssets': len(assets)}, ensure_ascii=False))

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--backend-jar', type=Path, required=True)
    parser.add_argument('--frontend-dist', type=Path, default=ROOT/'frontend/dist')
    parser.add_argument('--output', type=Path, default=ROOT/'work/releases')
    parser.add_argument('--include-verified-media', action='store_true',
                        help='Opt in only after verifying rights for all bundled frontend and seed media')
    args = parser.parse_args()
    package(args.backend_jar, args.frontend_dist, args.output, args.include_verified_media)
