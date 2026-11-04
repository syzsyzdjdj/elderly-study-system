import test from 'node:test'
import assert from 'node:assert/strict'
import { createTtsQueue } from '../src/utils/ttsQueue.mjs'

test('first segment does not wait for the rest; lookahead and reads share a request', async () => {
  const calls = []
  let finishSecond
  const queue = createTtsQueue(['第一句', '第二句', '第三句'], async text => {
    calls.push(text)
    if (text === '第二句') return new Promise(resolve => { finishSecond = resolve })
    return text + '.wav'
  })
  assert.equal(await queue.get(0), '第一句.wav')
  assert.deepEqual(calls, ['第一句'])
  queue.prefetch(1)
  const second = queue.get(1)
  await Promise.resolve()
  assert.deepEqual(calls, ['第一句', '第二句'])
  finishSecond('第二句.wav')
  assert.equal(await second, '第二句.wav')
  assert.equal(await queue.get(0), '第一句.wav')
  queue.cancel()
})

test('cancellation aborts in-flight work and prevents more generation', async () => {
  let signal
  const queue = createTtsQueue(['一', '二'], async (_, s) => { signal = s; return 'one.wav' })
  await queue.get(0)
  queue.cancel()
  assert.equal(signal.aborted, true)
  await assert.rejects(queue.get(1), /cancelled/)
})

test('failed prefetch can be retried without unhandled rejection', async () => {
  let attempts = 0
  const queue = createTtsQueue(['一'], async () => {
    if (++attempts === 1) throw new Error('offline')
    return 'one.wav'
  })
  queue.prefetch(0)
  await new Promise(resolve => setImmediate(resolve))
  assert.equal(await queue.get(0), 'one.wav')
})
