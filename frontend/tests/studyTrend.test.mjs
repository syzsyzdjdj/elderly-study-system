import test from 'node:test'
import assert from 'node:assert/strict'
import { recentStudyTrend } from '../src/utils/studyTrend.mjs'

test('uses 30 calendar days, filters old rows and fills gaps only in analyzed window', () => {
  const rows = recentStudyTrend([
    { studyDate: '2026-08-24', studyCount: 99, studyDurationMin: 999 },
    { studyDate: '2026-10-14', studyCount: 1, studyDurationMin: 58 }
  ], '2026-10-21', '2026-07-24', '2026-10-15')
  assert.equal(rows.length, 30)
  assert.equal(rows[0].studyDate, '2026-09-22')
  assert.deepEqual(rows.find(r => r.studyDate === '2026-10-14'), { studyDate: '2026-10-14', studyCount: 1, studyDurationMin: 58 })
  assert.equal(rows.find(r => r.studyDate === '2026-10-15').studyCount, 0)
  assert.equal(rows.at(-1).studyDate, '2026-10-21')
  assert.equal(rows.at(-1).studyCount, null)
})

test('handles empty successful snapshot, unknown coverage, and leap year boundary', () => {
  const rows = recentStudyTrend([], '2024-03-01', '2024-02-20', '2024-03-01')
  assert.equal(rows.length, 30)
  assert.equal(rows.at(-2).studyDate, '2024-02-29')
  assert.equal(rows.at(-1).studyCount, 0)
  assert.equal(rows[0].studyCount, null)
  assert.ok(recentStudyTrend([], '2026-10-21').every(r => r.studyCount === null))
})
