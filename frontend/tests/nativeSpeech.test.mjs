import test from 'node:test'
import assert from 'node:assert/strict'
import { createNativeSpeech } from '../src/utils/nativeSpeech.mjs'

test('native fallback ignores stale completion after switching text or stopping', () => {
  const calls = []
  const bridge = { postMessage: data => calls.push(JSON.parse(data)) }
  const speech = createNativeSpeech({ ElderlySpeech: bridge })
  let ended = 0
  speech.speak('第一段', 1, () => ended++)
  speech.speak('第二段', 0.8, () => ended++)
  bridge.onmessage({ data: JSON.stringify({ id: calls[0].id, status: 'done' }) })
  assert.equal(ended, 0)
  bridge.onmessage({ data: JSON.stringify({ id: calls[1].id, status: 'done' }) })
  assert.equal(ended, 1)
  speech.speak('第三段', 1, () => ended++)
  const id = calls.at(-1).id
  speech.stop()
  bridge.onmessage({ data: JSON.stringify({ id, status: 'done' }) })
  assert.equal(ended, 1)
  assert.equal(createNativeSpeech({}).available(), false)
})
