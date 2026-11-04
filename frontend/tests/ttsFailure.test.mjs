import test from 'node:test'
import assert from 'node:assert/strict'
import { ttsFailureMessage } from '../src/utils/ttsFailure.mjs'

test('preserves a server TTS cause and avoids exposing raw network details', () => {
  const reason = '当前服务器未启用在线 TTS 合成服务，因此无法生成所选音色。'
  assert.equal(ttsFailureMessage({ code: 500, msg: reason }), reason)
  assert.equal(ttsFailureMessage({ response: { data: { msg: reason } } }), reason)
  const fallback = ttsFailureMessage(new Error('secret URL or transport details'))
  assert.match(fallback, /无法确认具体原因/)
  assert.doesNotMatch(fallback, /secret/)
})
