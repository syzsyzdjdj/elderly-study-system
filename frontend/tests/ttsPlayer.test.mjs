import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import vm from 'node:vm'
import { createRequire } from 'node:module'
import { createTtsQueue } from '../src/utils/ttsQueue.mjs'
const require = createRequire(import.meta.url)
const vue = require('vue')
const { parse, compileScript } = require('@vue/compiler-sfc')
const file = new URL('../src/components/TtsPlayer.vue', import.meta.url)
const { descriptor } = parse(fs.readFileSync(file, 'utf8'))
const source = compileScript(descriptor, { id: 'tts-test' }).content
  .replace(/^import .* from .*$/gm, '').replace('export default', 'globalThis.component =')
const tick = () => new Promise(resolve => setImmediate(resolve))
const deferred = () => { let resolve; const promise = new Promise(r => { resolve = r }); return { promise, resolve } }

function mount(generate) {
  const calls = []
  const sandbox = {
    ...vue, AbortController, createTtsQueue, nativeSpeech: { available: () => false, stop() {} },
    subscribeActivity: callback => { callback(true); return () => {} },
    showToast() {}, useUserStore: () => vue.reactive({ userInfo: { voice: 'female' } }),
    mediaUrl: url => url,
    ttsSegments: async () => ({ data: { segments: ['第一句话。', '第二句话。'] } }),
    ttsGenerate: async (data, config) => { calls.push(data.text); return generate(data, config) },
    window: {},
  }
  vm.runInNewContext(source, sandbox)
  const renderer = vue.createRenderer({
    createElement: () => ({}), createComment: () => ({}), createText: () => ({}),
    insert() {}, remove() {}, setText() {}, setElementText() {}, patchProp() {},
    parentNode: () => null, nextSibling: () => null
  })
  const component = sandbox.component
  component.render = () => null
  const app = renderer.createApp(component, { text: '第一句话。第二句话。' })
  const instance = app.mount({})
  const state = instance.$.setupState
  let plays = 0
  const audio = { ended: false, playbackRate: 1, async play() { plays++; state.onPlay() }, pause() {} }
  state.audioRef = audio
  return { state, calls, audio, app, plays: () => plays, setText: text => { instance.$.props.text = text } }
}

test('first segment starts before second resolves; pause and resume keep the current segment', async () => {
  const second = deferred()
  const player = mount(data => data.text.startsWith('第二') ? second.promise : { data: { url: '/first.wav' } })
  try {
    await tick()
    player.state.onMainClick()
    await tick()
    assert.equal(player.plays(), 1)
    assert.equal(player.state.playing, true)
    assert.deepEqual(player.calls, ['第一句话。', '第二句话。'])
    player.state.onMainClick()
    assert.equal(player.state.paused, true)
    assert.equal(player.state.audioUrl, '/first.wav')
    player.state.onMainClick()
    await tick()
    assert.equal(player.plays(), 2)
    second.resolve({ data: { url: '/second.wav' } })
    player.state.onEnded()
    await tick()
    assert.equal(player.state.audioUrl, '/second.wav')
    assert.equal(player.plays(), 3)
  } finally { player.app.unmount() }
})

test('stop discards a delayed first response and never restarts playback', async () => {
  const first = deferred()
  const player = mount(() => first.promise)
  try {
    await tick()
    player.state.onMainClick()
    player.state.stopAll()
    first.resolve({ data: { url: '/late.wav' } })
    await tick()
    assert.equal(player.plays(), 0)
    assert.equal(player.state.audioUrl, '')
    assert.equal(player.state.generating, false)
    assert.equal(player.state.active, false)
  } finally { player.app.unmount() }
})

test('switching text while generating discards the old session', async () => {
  const first = deferred()
  const player = mount(() => first.promise)
  try {
    await tick()
    player.state.onMainClick()
    player.setText('新内容')
    await tick()
    first.resolve({ data: { url: '/old.wav' } })
    await tick()
    assert.equal(player.plays(), 0)
    assert.equal(player.state.audioUrl, '')
    assert.equal(player.state.generating, false)
  } finally { player.app.unmount() }
})
