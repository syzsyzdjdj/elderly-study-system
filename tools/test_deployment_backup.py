"""Backup script smoke tests using a fake dump command; never connects to MySQL."""
import gzip
import os
from pathlib import Path
import shutil
import subprocess
import tarfile
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
BASH = shutil.which('bash') or ('C:/Program Files/Git/bin/bash.exe' if os.name == 'nt' else None)

def shell_path(path):
    value = str(path.resolve()).replace('\\', '/')
    return '/' + value[0].lower() + value[2:] if len(value) > 1 and value[1] == ':' else value

@unittest.skipUnless(BASH and Path(BASH).exists(), 'bash unavailable')
class BackupTest(unittest.TestCase):
    def run_backup(self, fail=False):
        work = ROOT / 'work'
        work.mkdir(exist_ok=True)
        tmp = tempfile.TemporaryDirectory(dir=work)
        self.addCleanup(tmp.cleanup)
        root = Path(tmp.name)
        uploads = root / 'uploads'; uploads.mkdir()
        (uploads / 'sample.txt').write_text('sample-media', encoding='utf-8')
        mocks = root / 'bin'; mocks.mkdir()
        dump = mocks / 'mysqldump'
        dump.write_text('#!/bin/sh\n' + ('exit 7\n' if fail else "printf 'CREATE TABLE smoke (id INT);\\n'\n"), encoding='utf-8')
        dump.chmod(0o700)
        config = root / 'backup.cnf'; config.write_text('[client]\nuser=synthetic\n', encoding='utf-8')
        backup = root / 'backups'
        env = dict(os.environ, MOCK_BIN=shell_path(mocks), BACKUP_DIR=shell_path(backup),
                   UPLOAD_DIR=shell_path(uploads), MYSQL_BACKUP_CONFIG=shell_path(config))
        result = subprocess.run([BASH, '--noprofile', '--norc', '-c',
                                 'export PATH="$MOCK_BIN:$PATH"; bash deploy/backup.sh'],
                                cwd=ROOT, env=env, capture_output=True, text=True, timeout=30)
        return backup, result

    def test_success_archives_database_and_media(self):
        backup, result = self.run_backup()
        self.assertEqual(0, result.returncode, result.stderr)
        dumps = list(backup.glob('*.sql.gz')); self.assertEqual(1, len(dumps))
        with gzip.open(dumps[0], 'rt') as stream: self.assertIn('CREATE TABLE smoke', stream.read())
        archives = list(backup.glob('*.tar.gz')); self.assertEqual(1, len(archives))
        with tarfile.open(archives[0]) as archive:
            self.assertEqual(b'sample-media', archive.extractfile('./sample.txt').read())
        self.assertFalse(list(backup.glob('*.partial')))

    def test_dump_failure_does_not_publish_a_complete_backup(self):
        backup, result = self.run_backup(fail=True)
        self.assertNotEqual(0, result.returncode)
        self.assertFalse(list(backup.glob('*.sql.gz')))
        self.assertFalse(list(backup.glob('*.tar.gz')))

if __name__ == '__main__': unittest.main()
