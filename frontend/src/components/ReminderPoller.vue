<!--
全局提醒轮询组件：
  挂在老人端根布局上，每 60 秒轮询一次 /api/reminder/due。
  命中提醒后：
    1. 弹系统桌面通知（Notification API，需用户授权）
    2. 弹 Vant Dialog 提醒卡片
    3. 自动调 ChatTTS 合成语音并播放
    4. 记录已触发的 reminderId+日期 到 localStorage 防重复
  浏览器限制：页面没交互过会拦截自动播放音频。
  老人登录/点击任意处后调用 unlockAudio() 解锁 AudioContext。
-->
<template>
  <!-- 纯逻辑组件：全局轮询定时提醒，不渲染任何界面 -->
  <span v-if="false" aria-hidden="true"></span>
</template>

<script setup>
import { onMounted, onUnmounted } from 'vue'
import { showDialog, showConfirmDialog } from 'vant'
import { getDueReminders, markReminderTriggered } from '../api/reminder'
import { ttsGenerate } from '../api/tts'
import { useUserStore } from '../store/user'
import { isAppActive, subscribeActivity } from '../utils/appActivity.mjs'
import { mediaUrl } from '../utils/api-base'

const store = useUserStore()

let pollTimer = null
let pollRunning = false
let unsubscribe
let reminderAudio = null

/** localStorage key：已触发提醒集合，格式 reminderId_YYYY-MM-DD */
const DONE_KEY = 'reminder_done'

function todayStr() {
  const d = new Date()
  return `${d.getFullYear()}-${d.getMonth() + 1}-${d.getDate()}`
}

function loadDone() {
  try {
    return new Set(JSON.parse(localStorage.getItem(DONE_KEY) || '[]'))
  } catch (e) {
    return new Set()
  }
}

function saveDone(done) {
  localStorage.setItem(DONE_KEY, JSON.stringify([...done].slice(-50)))
}

/** 轮询到期提醒 */
async function pollDue() {
  if (!store.token || store.roleKey === 'admin' || !isAppActive()) return
  if (pollRunning) return
  pollRunning = true
  try {
    const res = await getDueReminders()
    const dueList = res.data || []
    const done = loadDone()
    for (const r of dueList) {
      if (!isAppActive()) break
      const key = `${r.id}_${todayStr()}`
      if (done.has(key)) continue
      await triggerReminder(r)
      done.add(key)
      saveDone(done)
      markReminderTriggered(r.id).catch(() => {})
    }
  } catch (e) {
    // 静默：token 过期或网络差不影响主流程
  } finally {
    pollRunning = false
  }
}

/** 触发提醒：弹窗 + 语音 + 桌面通知 */
async function triggerReminder(r) {
  // 1. 桌面通知（需要用户先点"允许通知"）
  try {
    if (Notification.permission === 'granted') {
      new Notification('⏰ ' + r.title, {
        body: r.content || '您有一条定时提醒',
        icon: '/favicon.ico'
      })
    }
  } catch (e) { /* Notification API 可能不存在 */ }

  // 2. 弹窗提醒
  showDialog({
    title: '⏰ ' + r.title,
    message: r.content || '您有一条定时提醒',
    confirmButtonText: '好的',
    showCancelButton: false
  }).catch(() => {})

  // 3. 语音播报
  try {
    const ttsRes = await ttsGenerate({
      text: r.content || '您有一条定时提醒',
      voice: store.userInfo?.voiceKey || 'default'
    })
    const url = ttsRes.data?.url
    if (url && isAppActive()) {
      reminderAudio?.pause()
      reminderAudio = new Audio(mediaUrl(url))
      reminderAudio.play().catch(() => { /* 浏览器可能拦截首次自动播放 */ })
    }
  } catch (e) {
    // 语音合成失败不影响弹窗
  }
}

onMounted(() => {
  unsubscribe = subscribeActivity(active => {
    clearInterval(pollTimer)
    if (active) { pollDue(); pollTimer = setInterval(pollDue, 60000) }
    else { reminderAudio?.pause(); reminderAudio = null }
  })
})

onUnmounted(() => {
  if (pollTimer) clearInterval(pollTimer)
  unsubscribe?.()
  reminderAudio?.pause()
})

/** 解锁 AudioContext：浏览器要求用户先交互才能自动播放。
 *  外部可调用 ReminderPoller 组件实例的 unlockAudio 方法。 */
function unlockAudio() {
  try {
    const dummy = document.createElement('audio')
    dummy.play().then(() => {
      dummy.pause()
      dummy.src = ''
    }).catch(() => {})
  } catch (e) {}
}
</script>
