<template>
  <Transition name="slide-up">
    <div v-if="speaking || loading" class="cts-bar">
      <div class="cts-info">
        <span v-if="loading" class="cts-loading-dot"></span>
        <van-icon v-else name="volume-o" class="cts-icon" />
        <span class="cts-text">{{ label }}</span>
      </div>
      <van-icon name="cross" class="cts-stop" @click.stop="stop" />
    </div>
  </Transition>
</template>

<script setup>
import { computed } from 'vue'
import { useClickToSpeak } from '../composables/useClickToSpeak'

const { speaking, loading, currentText, stop } = useClickToSpeak()

const label = computed(() => {
  if (loading.value) return '语音生成中…'
  const t = currentText.value
  return t.length > 24 ? `朗读：${t.slice(0, 24)}…` : `朗读：${t}`
})
</script>

<style scoped>
.cts-bar {
  position: fixed;
  left: 50%;
  transform: translateX(-50%);
  bottom: 70px;
  z-index: 2000;
  display: flex;
  align-items: center;
  gap: 12px;
  background: rgba(0, 0, 0, 0.82);
  color: #fff;
  border-radius: 22px;
  padding: 8px 16px;
  min-width: 200px;
  max-width: 88vw;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.2);
  backdrop-filter: blur(8px);
}

.cts-info {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  overflow: hidden;
}

.cts-icon {
  font-size: 18px;
  color: #4fc3f7;
}

.cts-text {
  font-size: 14px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.cts-stop {
  font-size: 20px;
  cursor: pointer;
  flex-shrink: 0;
  opacity: 0.8;
}

.cts-loading-dot {
  width: 14px;
  height: 14px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: #4fc3f7;
  border-radius: 50%;
  animation: cts-spin 0.7s linear infinite;
  flex-shrink: 0;
}

@keyframes cts-spin {
  to { transform: rotate(360deg); }
}

.slide-up-enter-active,
.slide-up-leave-active {
  transition: all 0.3s ease;
}

.slide-up-enter-from,
.slide-up-leave-to {
  opacity: 0;
  transform: translateX(-50%) translateY(20px);
}
</style>
