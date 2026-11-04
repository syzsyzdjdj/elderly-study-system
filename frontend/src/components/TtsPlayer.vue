<template>
  <div class="tts-player">
    <van-button
      size="small"
      round
      type="primary"
      plain
      :loading="generating"
      loading-text="语音生成中…"
      @click="onMainClick"
    >
      {{ mainLabel }}
    </van-button>
    <van-button size="small" round plain @click="cycleSpeed">语速 {{ speed }}x</van-button>
    <van-button v-if="active" size="small" round plain @click="stopAll">停止</van-button>
    <van-tag v-if="usingBrowser" type="success" class="browser-tag">已使用系统朗读</van-tag>
    <p v-if="failureReason" class="tts-failure" role="status">{{ failureReason }} 正在尝试设备默认朗读，音色可能与所选音色不同。</p>
    <!-- 原生播放条：始终可手动点播放，不依赖任何自动播放策略 -->
    <audio
      v-if="audioUrl"
      ref="audioRef"
      class="tts-audio"
      :src="audioUrl"
      controls
      preload="auto"
      @play="onPlay"
      @pause="onPause"
      @ended="onEnded"
      @error="onAudioError"
    ></audio>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { showToast } from 'vant'
import { ttsGenerate, ttsSegments } from '../api/tts'
import { createTtsQueue } from '../utils/ttsQueue.mjs'
import { useUserStore } from '../store/user'
import { mediaUrl } from '../utils/api-base'
import { nativeSpeech } from '../utils/nativeSpeech.mjs'
import { subscribeActivity } from '../utils/appActivity.mjs'
import { ttsFailureMessage } from '../utils/ttsFailure.mjs'

const props = defineProps({
  text: { type: String, default: '' },
  autoPlay: { type: Boolean, default: false }
})
const store = useUserStore()
const voiceKey = computed(() => store.userInfo?.voice || 'default')
const SPEEDS = [0.8, 1, 1.3]
const speedIdx = ref(1)
const speed = computed(() => SPEEDS[speedIdx.value])
const generating = ref(false)
const playing = ref(false)
const paused = ref(false)
const needClick = ref(false)
const usingBrowser = ref(false)
const audioUrl = ref('')
const audioRef = ref(null)
const active = ref(false)
const failureReason = ref('')
let session = 0
let queue = null
let segments = []
let index = 0
let wantsPlay = false
let utterance = null
let planning = null
let unsubscribe

const mainLabel = computed(() => {
  if (generating.value) return '语音准备中…'
  if (needClick.value) return '▶ 点击播放语音'
  if (playing.value) return '⏸ 暂停'
  if (paused.value) return '▶ 继续播放'
  return '🔊 听全文'
})

const stopAll = () => {
  session++
  wantsPlay = false
  planning?.abort()
  planning = null
  queue?.cancel()
  queue = null
  if (utterance) { utterance.onend = null; utterance.onerror = null; utterance = null }
  if (usingBrowser.value) window.speechSynthesis?.cancel()
  nativeSpeech.stop()
  audioRef.value?.pause()
  audioUrl.value = ''
  active.value = generating.value = playing.value = paused.value = needClick.value = false
  usingBrowser.value = false
}

const browserSpeak = (id) => {
  if (id !== session) return
  if (nativeSpeech.available()) {
    queue?.cancel()
    audioRef.value?.pause()
    generating.value = false
    audioUrl.value = ''
    usingBrowser.value = true
    playing.value = true
    paused.value = false
    nativeSpeech.speak(segments.slice(index).join('') || props.text, speed.value,
      () => { if (id === session) { playing.value = paused.value = active.value = false; index = 0 } },
      () => { if (id === session) { playing.value = active.value = false; showToast('请在手机设置中启用中文文字转语音引擎') } })
    return
  }
  if (!window.speechSynthesis) {
    generating.value = playing.value = active.value = false
    showToast('语音暂时不可用，请稍后重试')
    return
  }
  queue?.cancel()
  audioRef.value?.pause()
  generating.value = false
  audioUrl.value = ''
  usingBrowser.value = true
  utterance = new SpeechSynthesisUtterance(segments.slice(index).join('') || props.text)
  utterance.lang = 'zh-CN'
  utterance.rate = speed.value
  utterance.onend = () => {
    if (id === session) { playing.value = paused.value = false; active.value = false; index = 0 }
  }
  utterance.onerror = () => {
    if (id === session) { playing.value = false; needClick.value = true }
  }
  window.speechSynthesis.speak(utterance)
  playing.value = true
  showToast('语音服务不可用，已使用浏览器语音')
}

const playNow = async () => {
  const id = session
  wantsPlay = true
  needClick.value = false
  if (usingBrowser.value) {
    if (window.speechSynthesis?.paused) window.speechSynthesis.resume()
    else browserSpeak(id)
    playing.value = true
    paused.value = false
    return
  }
  const audio = audioRef.value
  if (!audio || !audioUrl.value) return
  try {
    await audio.play()
    if (id !== session) return
    playing.value = true
    paused.value = false
  } catch {
    if (id !== session) return
    needClick.value = true
    wantsPlay = false
    showToast('语音已准备好，请点击播放')
  }
}

const loadSegment = async (id) => {
  generating.value = true
  try {
    const url = await queue.get(index)
    if (id !== session) return
    audioUrl.value = mediaUrl(url)
    generating.value = false
    await nextTick()
    if (id !== session) return
    // edge 的速率由服务端生成；ChatTTS 由播放器变速，避免重复变速。
    if (audioRef.value) audioRef.value.playbackRate = url.includes('/tts/prepared/') ? speed.value : (voiceKey.value.startsWith('edge:') ? 1 : speed.value)
    if (wantsPlay) await playNow()
  } catch (error) {
    if (id !== session) return
    generating.value = false
    failureReason.value = ttsFailureMessage(error)
    if (wantsPlay) browserSpeak(id)
    else { needClick.value = true; usingBrowser.value = true }
  }
}

const prepare = async (play) => {
  stopAll()
  failureReason.value = ''
  if (!props.text.trim()) return
  const id = session
  active.value = play
  wantsPlay = play
  generating.value = play
  const voice = voiceKey.value
  const rate = speed.value
  planning = new AbortController()
  try {
    const res = await ttsSegments(props.text, { silent: true, signal: planning.signal })
    if (id !== session) return
    segments = res.data.segments
    index = 0
    if (!segments.length) { generating.value = active.value = false; return }
    queue = createTtsQueue(segments, async (text, signal) => {
      const result = await ttsGenerate({ text, speed: rate, voice }, { silent: true, signal })
      const url = result.data?.url
      if (typeof url !== 'string' || !url) throw new Error('missing audio')
      return url
    })
    if (play) await loadSegment(id)
    else queue.prefetch(0)
  } catch (error) {
    if (id !== session) return
    failureReason.value = ttsFailureMessage(error)
    segments = [props.text]
    index = 0
    generating.value = false
    if (play) browserSpeak(id)
  }
}

const onMainClick = () => {
  if (generating.value) return
  if (playing.value) {
    wantsPlay = false
    if (usingBrowser.value) { if (nativeSpeech.available()) nativeSpeech.stop(); else window.speechSynthesis?.pause() }
    else audioRef.value?.pause()
    playing.value = false
    paused.value = true
  } else if (audioUrl.value || usingBrowser.value) {
    active.value = true
    playNow()
  } else if (queue) {
    active.value = wantsPlay = true
    loadSegment(session)
  } else prepare(true)
}
const onEnded = () => {
  if (!active.value) return
  playing.value = paused.value = false
  index++
  if (index < segments.length) loadSegment(session)
  else { active.value = false; wantsPlay = false; audioUrl.value = ''; index = 0 }
}
const onPlay = () => {
  wantsPlay = true
  active.value = playing.value = true
  paused.value = needClick.value = false
  // 仅播放期间预取一段，后台预热不会吞掉整篇推理资源。
  queue?.prefetch(index + 1)
}
const onPause = () => {
  if (usingBrowser.value || audioRef.value?.ended || !audioUrl.value) return
  wantsPlay = false
  playing.value = false
  paused.value = true
}
const onAudioError = () => {
  if (audioUrl.value && active.value) {
    failureReason.value = '服务器返回的音频无法加载或播放，请联系管理员检查音频文件。'
    browserSpeak(session)
  }
}
const cycleSpeed = () => {
  const restart = active.value && (playing.value || generating.value)
  speedIdx.value = (speedIdx.value + 1) % SPEEDS.length
  prepare(restart)
}
watch([() => props.text, voiceKey, () => props.autoPlay], () => prepare(props.autoPlay), { immediate: true })
onMounted(() => { unsubscribe = subscribeActivity(active => { if (!active) stopAll() }) })
onBeforeUnmount(() => { unsubscribe?.(); stopAll() })
</script>

<style scoped>
.tts-player {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.browser-tag {
  font-size: 14px;
}
.tts-failure { width: 100%; margin: 0; color: #8c3b00; line-height: 1.6; }

/* 原生播放条：宽满一行，方便老年人手动播放 */
.tts-audio {
  width: 100%;
  height: 44px;
  margin-top: 8px;
}
</style>
