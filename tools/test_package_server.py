"""Public deployment bundles must exclude unverified media by default."""
from contextlib import redirect_stdout
import io
import json
from pathlib import Path
import tarfile
import tempfile
import unittest
from unittest.mock import patch
import zipfile

import package_server


class PublicServerPackageTest(unittest.TestCase):
    def test_default_bundle_contains_code_but_neither_seed_nor_frontend_media(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / 'deploy').mkdir(); (root / 'docs').mkdir()
            # Referenced seed file deliberately absent: default packaging must not require it.
            (root / 'deploy/seed-assets.json').write_text(json.dumps(['/uploads/images/unverified.svg']))
            for name in ['first-deployment.md', 'server-deployment.md', 'tts-deployment.md']:
                (root / 'docs' / name).write_text('synthetic documentation')
            jar = root / 'backend.jar'
            with zipfile.ZipFile(jar, 'w') as archive:
                archive.writestr('BOOT-INF/classes/db/migration/V1__base_schema.sql', '-- synthetic')
                archive.writestr('BOOT-INF/classes/com/elderly/config/InitialAdminBootstrap.class', b'synthetic')
            dist = root / 'dist'
            for name in ['index.html', 'assets/app.js', 'assets/app.css', 'voice-previews/sample.mp3', 'tutorials/sample.jpg', 'icon.png']:
                file = dist / name; file.parent.mkdir(parents=True, exist_ok=True); file.write_bytes(b'synthetic')
            output = root / 'releases'
            with patch.object(package_server, 'ROOT', root), redirect_stdout(io.StringIO()):
                package_server.package(jar, dist, output)
            with tarfile.open(next(output.glob('*.tar.gz'))) as archive:
                names = set(archive.getnames())
                metadata = json.load(archive.extractfile('release-manifest.json'))
                self.assertEqual('complete', metadata['releaseMode'])
                self.assertIn('backend.jar', metadata['files'])
                self.assertEqual(64, len(metadata['source']['sourceSha256']))
                self.assertIn('V1__base_schema.sql', metadata['migrations'])
                import hashlib
                for name, digest in metadata['files'].items():
                    self.assertEqual(digest, hashlib.sha256(archive.extractfile(name).read()).hexdigest())
            self.assertIn('frontend/assets/app.js', names)
            self.assertIn('frontend/assets/app.css', names)
            self.assertFalse(any(name.endswith(('.png', '.jpg', '.mp3', '.svg')) for name in names))
            self.assertFalse(any(name.startswith('uploads/') for name in names))


if __name__ == '__main__': unittest.main()
