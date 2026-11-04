import test from 'node:test'
import assert from 'node:assert/strict'
import { createVoicePreview } from '../src/utils/voicePreview.mjs'

function fixture() {
  const messages = [], states = [], calls = []
  const audio = {
    pause() {}, removeAttribute() {}, load() {},
    play() { return Promise.reject(new Error('HTML is not audio')) }
  }
  const nativeSpeech = {
    available: () => true, stop() {},
    speak(text, rate, done, fail) { calls.push({ text, done, fail }); return true }
  }
  const preview = createVoicePreview({
    audio, nativeSpeech, toUrl: path => 'https://example.test' + path,
    onState: key => states.push(key), onMessage: text => messages.push(text)
  })
  return { preview, audio, nativeSpeech, messages, states, calls }
}

test('missing or HTML preview falls back with an honest default-voice notice', async () => {
  const f = fixture()
  const task = f.preview.play({ key: 'male', preview: '/male.mp3' })
  await new Promise(resolve => setImmediate(resolve))
  assert.equal(f.audio.src, 'https://example.test/male.mp3')
  assert.match(f.messages[0], /与所选音色不同/)
  assert.equal(f.calls.length, 1)
  f.calls[0].done()
  await task
  assert.equal(f.states.at(-1), '')
})

test('switching previews or closing never starts a stale fallback', async () => {
  const f = fixture()
  f.audio.play = () => new Promise(() => {})
  const first = f.preview.play({ key: 'male', preview: '/male.mp3' })
  const second = f.preview.play({ key: 'female', preview: '/female.mp3' })
  f.preview.stop()
  await Promise.all([first, second])
  assert.equal(f.calls.length, 0)
  assert.equal(f.messages.length, 0)
})

test('device speech failure is visible instead of silently swallowed', async () => {
  const f = fixture()
  f.nativeSpeech.available = () => false
  await f.preview.play({ key: 'male' })
  assert.match(f.messages.at(-1), /试听播放失败/)
  assert.equal(f.states.at(-1), '')
})

test('preview includes the server configuration cause when generation is unavailable', async () => {
  const messages = []
  const preview = createVoicePreview({
    audio: { pause() {}, removeAttribute() {}, load() {} },
    nativeSpeech: { available: () => false, stop() {} },
    onState() {}, onMessage: text => messages.push(text),
    getUnavailableReason: () => '当前服务器未启用外部 TTS 合成服务。'
  })
  await preview.play({ key: 'edge:xx-female' })
  assert.match(messages[0], /当前服务器未启用外部 TTS/)
  assert.match(messages.at(-1), /设备试听播放失败/)
})
