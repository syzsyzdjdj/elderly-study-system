<template>
  <section class="weather-card" aria-labelledby="weather-title">
    <div class="weather-heading">
      <h2 id="weather-title"><van-icon name="smile-o" /> 天气陪伴</h2>
      <van-button size="small" plain round :loading="loading" @click="load">刷新</van-button>
    </div>
    <van-loading v-if="loading && !weather" size="22">正在获取当地天气…</van-loading>
    <template v-else-if="weather?.status === 'READY'">
      <p class="weather-region"><van-icon name="location-o" /> {{ weather.region }}</p>
      <div v-if="weather.weatherAvailable" class="weather-current">
        <strong>{{ Math.round(weather.temperature) }}<small>°C</small></strong>
        <span>{{ weather.condition }}</span>
      </div>
      <p v-else class="weather-note">当前天气暂不可用，仍可查看已获取的预警。</p>
      <p class="weather-note">获取时间：{{ time(weather.fetchedAt) }} · {{ weather.source || '和风天气' }}</p>
      <div v-for="alert in activeAlerts" :key="alert.id" class="weather-alert" :class="{ severe: ['extreme', 'severe'].includes(alert.severity) }">
        <h3><van-icon name="warning-o" /> {{ alert.title }}</h3>
        <p class="weather-note">{{ alert.sender }} · {{ time(alert.issuedTime) }}发布</p>
        <p>{{ alert.description }}</p>
        <p v-if="alert.instruction"><b>防护提示：</b>{{ alert.instruction }}</p>
        <p class="weather-note">有效至 {{ time(alert.expireTime) }}</p>
        <TtsPlayer :text="`${alert.sender}发布${alert.title}。${alert.description}。${alert.instruction}`" />
      </div>
      <div v-for="advice in weather.advisories || []" :key="advice.id" class="weather-alert">
        <h3>{{ advice.title }}</h3>
        <p>{{ advice.description }}</p>
        <span class="weather-note">根据当前天气生成 · 非官方预警</span>
      </div>
      <p v-if="!weather.warningsAvailable" class="weather-note">{{ weather.warningsMessage || '预警信息暂未获取成功，请关注当地气象部门发布的信息。' }}</p>
      <p v-else-if="!activeAlerts.length" class="weather-note">当前数据中暂无有效预警，出行前请关注最新天气。</p>
      <p class="weather-companion">{{ companionText }}</p>
      <TtsPlayer :text="companionText" />
      <details v-if="weather.attributions?.length" class="weather-attribution">
        <summary>数据来源与说明</summary>
        <p v-for="item in weather.attributions" :key="item">{{ item }}</p>
      </details>
    </template>
    <template v-else>
      <p class="weather-note">{{ weather?.message || '天气暂时获取失败，请稍后重试' }}</p>
      <van-button v-if="weather?.status === 'NEED_REGION'" type="primary" plain round @click="router.push('/display')">设置常住地区</van-button>
    </template>
  </section>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getWeather } from '../api/weather'
import TtsPlayer from './TtsPlayer.vue'

const router = useRouter()
const loading = ref(false)
const weather = ref(null)
const clock = ref(Date.now())
let timer
const activeAlerts = computed(() => (weather.value?.alerts || []).filter(a => Date.parse(a.expireTime) > clock.value))
const companionText = computed(() => {
  const w = weather.value
  if (!w || w.status !== 'READY') return ''
  if (activeAlerts.value.length) return `您好，${w.region}有${activeAlerts.value.length}条天气预警，请先查看防护提示，安排好出行。预警以气象部门最新发布为准。`
  if (w.advisories?.length) return `您好，${w.region}当前${w.condition}，气温约${Math.round(w.temperature)}摄氏度。${w.advisories.map(a => a.description).join('')}这是根据天气生成的生活提醒，不是官方气象预警。`
  if (!w.weatherAvailable) return '天气数据暂不完整，请出行前留意当地最新天气。'
  return `您好，${w.region}当前${w.condition}，气温约${Math.round(w.temperature)}摄氏度。外出前看看天气，合理安排今天的活动。`
})
function time(value) {
  const d = new Date(value)
  return Number.isNaN(d.getTime()) ? '时间未知' : d.toLocaleString('zh-CN', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' })
}
async function load() {
  if (loading.value) return
  loading.value = true
  try { weather.value = (await getWeather()).data; clock.value = Date.now() }
  catch { weather.value = { status: 'UNAVAILABLE', message: '天气暂时获取失败，请稍后重试' } }
  finally { loading.value = false }
}
onMounted(() => { load(); timer = setInterval(() => { clock.value = Date.now(); if (!document.hidden) load() }, 300000) })
onUnmounted(() => clearInterval(timer))
</script>

<style scoped>
.weather-card { margin: 16px; padding: 20px; border: 1px solid var(--app-border, #e3ece6); border-radius: 22px; background: var(--app-surface, #fff); box-shadow: var(--app-shadow); }
.weather-heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.weather-heading h2 { margin: 0; font-size: 21px; color: var(--app-ink); }
.weather-region { color: var(--app-muted); font-size: 16px; line-height: 1.7; }
.weather-current { display: flex; align-items: center; flex-wrap: wrap; gap: 18px; color: var(--app-ink); }
.weather-current strong { font-size: 48px; line-height: 1.2; font-variant-numeric: tabular-nums; }
.weather-current small { font-size: 24px; }
.weather-note { font-size: 15px; color: var(--app-muted); line-height: 1.7; }
.weather-alert { margin: 16px 0; padding: 16px; border-radius: 16px; background: #fff9eb; border: 1px solid #e9c974; color: #654a16; }
.weather-alert.severe { background: #fff2ef; border-color: #e9b1a8; color: #8b3028; }
.weather-alert h3 { margin: 0 0 8px; font-size: 18px; line-height: 1.6; }
.weather-alert p { line-height: 1.8; overflow-wrap: anywhere; }
.weather-companion { padding: 14px; background: var(--app-soft); border-radius: 14px; font-size: 17px; line-height: 1.8; }
.weather-warning { color: #8b3028; }
.weather-attribution { margin-top: 16px; color: var(--app-muted); font-size: 14px; overflow-wrap: anywhere; line-height: 1.7; }
:global(html.high-contrast) .weather-alert { background: #fff; color: #000; border: 2px solid #000; }
</style>
