import test from 'node:test'
import assert from 'node:assert/strict'
import { createActiveClock } from '../src/utils/appActivity.mjs'

test('background time is excluded and repeated foreground signals do not reset the clock', () => {
  let now = 0
  const clock = createActiveClock(() => now)
  now = 45000; clock.setActive(false)
  now += 30 * 60000
  assert.equal(clock.elapsedMs(), 45000)
  clock.setActive(true); now += 15000
  clock.setActive(true)
  assert.equal(clock.elapsedMs(), 60000)
  clock.setActive(false); now += 10000
  assert.equal(clock.elapsedMs(), 60000)
})
