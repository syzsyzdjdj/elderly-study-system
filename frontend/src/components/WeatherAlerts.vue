<template><span v-if="false" aria-hidden="true"></span></template>

<script setup>
import { onMounted, onUnmounted, watch } from 'vue'
import { showNotify, closeNotify } from 'vant'
import { useRouter } from 'vue-router'
import { useUserStore } from '../store/user'
import { getWeather } from '../api/weather'
import { isAppActive, subscribeActivity } from '../utils/appActivity.mjs'

const store = useUserStore()
const router = useRouter()
let timer
let unsubscribe
let busy = false
let lastPoll = 0
let sessionSeen = new Set()
function identity() { return `${store.token}:${store.userInfo?.province}:${store.userInfo?.city}:${store.userInfo?.district}` }
async function poll() {
  if (!store.token || store.roleKey === 'admin' || !isAppActive() || busy || Date.now() - lastPoll < 60000) return
  busy = true
  const owner = identity()
  const user = store.userInfo?.id
  lastPoll = Date.now()
  try {
    const w = (await getWeather()).data
    if (!isAppActive() || identity() !== owner || w?.status !== 'READY') return
    const key = `weather_seen_${user}_${w.locationId}`
    let seen = new Set(sessionSeen)
    try { seen = new Set([...seen, ...JSON.parse(localStorage.getItem(key) || '[]')]) } catch { /* Storage may be unavailable. */ }
    const fresh = (w.alerts || []).filter(a => Date.parse(a.expireTime) > Date.now() && !seen.has(`${a.id}:${a.issuedTime}`))
    const tips = (w.advisories || []).filter(a => !seen.has(a.id))
    if (!fresh.length && !tips.length) return
    const message = fresh.length ? `气象预警：${fresh.map(a => a.title).join('；')}` : `天气生活提醒（非官方预警）：${tips.map(a => a.title).join('；')}`
    showNotify({ type: 'warning', duration: 10000, message: `${w.region}，${message}。点击查看详情`, onClick: () => { closeNotify(); router.push('/companion') } })
    for (const alert of fresh) seen.add(`${alert.id}:${alert.issuedTime}`)
    for (const tip of tips) seen.add(tip.id)
    sessionSeen = seen
    try { localStorage.setItem(key, JSON.stringify([...seen].slice(-100))) } catch { /* In-memory deduplication remains active. */ }
  } catch { /* No fabricated alerts or repeated network toasts. */ }
  finally { busy = false }
}
watch(identity, () => { lastPoll = 0; sessionSeen = new Set(); closeNotify(); poll() })
onMounted(() => { unsubscribe = subscribeActivity(active => { clearInterval(timer); if (active) { poll(); timer = setInterval(poll, 300000) } else closeNotify() }) })
onUnmounted(() => { clearInterval(timer); unsubscribe?.() })
</script>
