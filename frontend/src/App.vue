<template>
  <router-view />
  <!-- 全局定时提醒轮询：登录后生效，自动弹窗+语音播报 -->
  <ReminderPoller />
  <WeatherAlerts />
</template>

<script setup>
import { onBeforeUnmount, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Capacitor } from '@capacitor/core'
import { App as CapApp } from '@capacitor/app'
import { showToast } from 'vant'
import ReminderPoller from './components/ReminderPoller.vue'
import WeatherAlerts from './components/WeatherAlerts.vue'
import { useUserStore } from './store/user'
import { applyDisplay, refreshDisplayScope } from './utils/display'
import { subscribeActivity } from './utils/appActivity.mjs'
import { useClickToSpeak } from './composables/useClickToSpeak'

const store = useUserStore()
const route = useRoute()
const router = useRouter()

// APP 物理返回键：子页面返回上一页；主页面/登录页双击退出（防误触直接退出）
const MAIN_TABS = ['/home', '/courses', '/ai', '/mine']
let lastBackAt = 0
let unsubscribeActivity

if (Capacitor.isNativePlatform()) {
  CapApp.addListener('backButton', () => {
    const path = router.currentRoute.value.path
    if (MAIN_TABS.includes(path) || path === '/login') {
      const now = Date.now()
      if (now - lastBackAt < 2000) {
        CapApp.exitApp()
      } else {
        lastBackAt = now
        showToast('再按一次退出')
      }
      return
    }
    if (window.history.state?.back) {
      router.back()
    } else {
      router.replace('/home')
    }
  })
}

onMounted(() => {
  applyDisplay(store.userInfo)
  unsubscribeActivity = subscribeActivity(active => {
    if (active) return
    document.querySelectorAll('audio,video').forEach(media => media.pause())
    window.speechSynthesis?.cancel()
    useClickToSpeak().stop()
  })
})
onBeforeUnmount(() => unsubscribeActivity?.())

watch(
  () => [store.userInfo?.fontScale, store.userInfo?.highContrast],
  () => applyDisplay(store.userInfo)
)

watch(
  () => route.path,
  () => refreshDisplayScope(store.userInfo)
)
</script>
