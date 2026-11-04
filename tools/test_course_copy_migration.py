"""Check course copy migration against historical seed bodies, without a live DB.

SQLite verifies REPLACE/UPDATE behavior only; this is not a MySQL/Flyway integration test.
"""
import re
import sqlite3
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MIGRATIONS = ROOT / 'backend/src/main/resources/db/migration'
COURSE_UPDATE = re.compile(
    r"UPDATE `course_info` SET `content` = (?:'|REPLACE\().*?WHERE `id` = (\d+);",
    re.S,
)


class CourseCopyMigrationTest(unittest.TestCase):
    def test_seed_corrections_preserve_images_and_other_edits(self):
        db = sqlite3.connect(':memory:')
        self.addCleanup(db.close)
        db.execute('CREATE TABLE course_info (id INTEGER PRIMARY KEY, content TEXT)')
        db.executemany('INSERT INTO course_info VALUES (?, ?)', [(i, '') for i in range(1, 22)])
        files = sorted(MIGRATIONS.glob('V*.sql'), key=lambda p: int(p.name.split('__')[0][1:]))
        target = MIGRATIONS / 'V25__review_course_copy.sql'
        for path in files:
            if path == target:
                break
            for match in COURSE_UPDATE.finditer(path.read_text(encoding='utf-8')):
                db.execute(match[0])
        before = dict(db.execute('SELECT id, content FROM course_info'))
        custom_note = '<p>管理员补充：请在服务台咨询。</p>'
        db.execute('UPDATE course_info SET content = content || ? WHERE id = 7', (custom_note,))
        statements = list(COURSE_UPDATE.finditer(target.read_text(encoding='utf-8')))
        self.assertGreater(len(statements), 100)
        for match in statements:
            course_id = int(match[1])
            old = db.execute('SELECT content FROM course_info WHERE id = ?', (course_id,)).fetchone()[0]
            db.execute(match[0])
            new = db.execute('SELECT content FROM course_info WHERE id = ?', (course_id,)).fetchone()[0]
            self.assertNotEqual(old, new, f'Correction did not match seeded course {course_id}')
        after = dict(db.execute('SELECT id, content FROM course_info'))
        for course_id in before:
            self.assertNotEqual(before[course_id], after[course_id])
            self.assertEqual(re.findall('<img[^>]*src="([^"]+)"', before[course_id]),
                             re.findall('<img[^>]*src="([^"]+)"', after[course_id]))
        self.assertIn(custom_note, after[7])
        self.assertIn('长期有效', after[7])
        self.assertNotIn('风险降低约30%', after[18])
        self.assertNotIn('每天练20-30次', after[18])
        self.assertNotIn('点"允许"即可正常浏览', after[20])
        for course_id in (5, 12):
            self.assertIn('虚构示例', after[course_id])
            self.assertNotIn('真实案例', after[course_id])
        for match in statements:
            db.execute(match[0])
        self.assertEqual(after, dict(db.execute('SELECT id, content FROM course_info')))


if __name__ == '__main__':
    unittest.main()
