import { ref } from 'vue'
import { showToast } from 'vant'
import { ttsFailureMessage } from '../utils/ttsFailure.mjs'
import { ttsGenerate, ttsSegments } from '../api/tts'
import { createTtsQueue } from '../utils/ttsQueue.mjs'
import { useUserStore } from '../store/user'
import { mediaUrl } from '../utils/api-base'
import { nativeSpeech } from '../utils/nativeSpeech.mjs'

// 模块级单例状态，所有组件共享同一实例
const speaking = ref(false)
const loading = ref(false)
const currentText = ref('')
let audioEl = null
let activeUtterance = null
let queue = null
let planController = null
let reqId = 0  // 请求序号，用于丢弃过期的异步响应

function stopAll() {
  queue?.cancel()
  queue = null
  planController?.abort()
  reqId++  // 使所有在途的异步响应失效
  if (window.speechSynthesis) window.speechSynthesis.cancel()
  nativeSpeech.stop()
  if (audioEl) {
    audioEl.onplay = null
    audioEl.onended = null
    audioEl.onerror = null
    audioEl.pause()
    audioEl.currentTime = 0
    audioEl.src = ''
    audioEl = null
  }
  if (activeUtterance) {
    activeUtterance.onend = null
    activeUtterance.onerror = null
    activeUtterance = null
  }
  speaking.value = false
  loading.value = false
}

function browserSpeak(text, speed) {
  if (nativeSpeech.available() && text) {
    const id = reqId
    speaking.value = true
    return nativeSpeech.speak(text, speed,
      () => { if (id === reqId) speaking.value = false },
      () => { if (id === reqId) speaking.value = false })
  }
  if (!window.speechSynthesis || !text) return false
  if (window.speechSynthesis) window.speechSynthesis.cancel()
  const u = new SpeechSynthesisUtterance(text)
  u.lang = 'zh-CN'
  u.rate = speed
  u.onend = () => {
    speaking.value = false
    activeUtterance = null
  }
  u.onerror = () => {
    speaking.value = false
    activeUtterance = null
  }
  activeUtterance = u
  window.speechSynthesis.speak(u)
  speaking.value = true
  return true
}

/**
 * 朗读文字：同一文字再点一次=停止，不同文字=切换朗读
 */
async function speak(text) {
  if (!text || text.trim().length < 2) return
  const trimmed = text.trim()

  // 同一段文字正在朗读或生成中 → 停止
  if ((speaking.value || loading.value) && currentText.value === trimmed) {
    stopAll()
    return
  }

  stopAll()
  currentText.value = trimmed
  loading.value = true
  const myId = ++reqId

  const store = useUserStore()
  const voice = store.userInfo?.voice || 'default'
  const speed = 1

  try {
    planController = new AbortController()
    const plan = await ttsSegments(trimmed, { silent: true, signal: planController.signal })
    if (myId !== reqId) return
    const parts = plan.data.segments
    if (!parts.length) { loading.value = false; return }
    const currentQueue = createTtsQueue(parts, async (text, signal) => {
      const result = await ttsGenerate({ text, speed, voice }, { silent: true, signal })
      if (!result.data?.url) throw new Error('missing audio')
      return result.data.url
    })
    queue = currentQueue
    let index = 0
    const playPart = async () => {
      if (myId !== reqId) return
      loading.value = true
      try {
        const url = await currentQueue.get(index)
        if (myId !== reqId) return
        loading.value = false
        if (!audioEl) audioEl = new Audio()
        audioEl.src = mediaUrl(url)
        audioEl.onplay = () => {
          if (myId !== reqId) return
          speaking.value = true
          currentQueue.prefetch(index + 1)
        }
        audioEl.onended = () => {
          if (myId !== reqId) return
          speaking.value = false
          index++
          if (index < parts.length) playPart()
        }
        audioEl.onerror = fallback
        await audioEl.play()
      } catch (error) { fallback(error) }
    }
    const fallback = (error) => {
      if (myId !== reqId) return
      showToast({ message: ttsFailureMessage(error) + ' 将尝试设备默认朗读。', duration: 5000 })
      currentQueue.cancel()
      if (audioEl) { audioEl.onerror = null; audioEl.pause() }
      loading.value = false
      speaking.value = false
      browserSpeak(parts.slice(index).join(''), speed)
    }
    await playPart()
    return
  } catch (error) {
    if (myId !== reqId) return
    showToast({ message: ttsFailureMessage(error) + ' 将尝试设备默认朗读。', duration: 5000 })
    // 后端不可用，降级浏览器语音
  }
  if (myId !== reqId) return  // 已被新请求取代
  loading.value = false
  if (!browserSpeak(trimmed, speed)) {
    currentText.value = ''
  }
}

export function useClickToSpeak() {
  return { speaking, loading, currentText, speak, stop: stopAll }
}
