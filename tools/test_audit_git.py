import unittest
import tempfile
import subprocess
from pathlib import Path
from unittest.mock import patch
import audit_git
import json
import hashlib
from audit_git import credential_findings, scan_source_tree

class GitAuditTest(unittest.TestCase):
    def test_empty_env_value_does_not_consume_the_next_line(self):
        self.assertEqual([], credential_findings('backend.env.example', b'DB_PASSWORD=\nDB_USERNAME=example\nJWT_SECRET=\n'))

    def test_env_secret_and_private_key_are_reported_without_values(self):
        secret = b'synthetic-password-only'
        findings = credential_findings('.env', b'DB_PASSWORD=' + secret)
        self.assertEqual('env-secret-literal', findings[0]['rule'])
        self.assertNotIn(secret.decode(), repr(findings))
        marker = b'-----BEGIN OPENSSH ' + b'PRIVATE KEY-----'
        self.assertEqual('private-key-literal', credential_findings('key', marker)[0]['rule'])

    def test_source_export_excludes_private_config_builds_and_unverified_media(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            for name in ['README.md', 'frontend/src/main.js', 'backend/src/main/java/com/elderly/config/SecurityConfig.java', 'frontend/.env',
                         'frontend/node_modules/pkg/main.js', 'backend/config/application-local.yml',
                         'frontend/public/tutorials/image.png', 'docs/screenshots/login.jpg', 'docs/screenshots/unreviewed.jpg',
                         'backend/credentials.json', 'backend/release.properties', 'backend/server.log']:
                file = root / name; file.parent.mkdir(parents=True, exist_ok=True)
                file.write_text('synthetic', encoding='utf-8')
            (root / 'docs/public-media.json').write_text(json.dumps({'files': [{
                'path': 'docs/screenshots/login.jpg', 'sha256': hashlib.sha256(b'synthetic').hexdigest()}]}))
            names = {p.relative_to(root).as_posix() for p in audit_git.source_files(root)}
            self.assertEqual({'README.md', 'frontend/src/main.js', 'backend/src/main/java/com/elderly/config/SecurityConfig.java', 'docs/screenshots/login.jpg', 'docs/public-media.json'}, names)
            self.assertEqual([], scan_source_tree(root)['findings'])
            (root / 'docs/screenshots/login.jpg').write_text('replaced-with-unreviewed-content')
            self.assertEqual('approved-media-changed-requires-review', scan_source_tree(root)['findings'][0]['rule'])

    def test_deleted_historical_secret_is_detected(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            def git(*args):
                return subprocess.check_output(['git', *args], cwd=root, stderr=subprocess.DEVNULL)
            git('init')
            git('config', 'user.name', 'Audit Test'); git('config', 'user.email', 'audit@example.invalid')
            file = root / 'removed.env'
            file.write_text('JWT_SECRET=synthetic-deleted-secret\n', encoding='utf-8')
            git('add', '.'); git('commit', '-m', 'synthetic fixture')
            file.unlink(); git('add', '-u'); git('commit', '-m', 'remove fixture')
            with patch.object(audit_git, 'ROOT', root):
                findings = audit_git.history_findings()
            self.assertEqual('historical-env-secret-literal', findings[0]['rule'])
            self.assertNotIn('synthetic-deleted-secret', repr(findings))

    def test_placeholders_and_empty_example_values_are_allowed(self):
        data = b'jwt:\n  secret: ${JWT_SECRET:}\npassword: ""\napi-key: ${API_KEY:}\n'
        self.assertEqual([], credential_findings('application.yml', data))

    def test_plain_yaml_credentials_are_flagged_without_disclosing_values(self):
        value = 'synthetic-password-for-test'
        findings = credential_findings('application.yml', ('password: '+value).encode())
        self.assertEqual('yaml-secret-literal', findings[0]['rule'])
        self.assertNotIn(value, repr(findings))

    def test_literal_api_key_is_detected_in_code_and_not_printed(self):
        value = 'sk-' + 'a' * 32
        findings = credential_findings('example.java', value.encode())
        self.assertEqual('api-key-literal', findings[0]['rule'])
        self.assertNotIn(value, repr(findings))

    def test_shell_references_and_random_generation_are_not_literal_secrets(self):
        self.assertEqual([], credential_findings('install.sh', b'JWT_SECRET=$(openssl rand -hex 32)\nDB_PASSWORD=$DB_PASS\n'))
        self.assertTrue(credential_findings('install.sh', b'JWT_SECRET=$(echo synthetic-secret)\n'))

if __name__ == '__main__': unittest.main()
