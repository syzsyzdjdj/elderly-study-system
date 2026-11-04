<template>
  <div class="course-art">
    <img v-if="src && !failed" :src="mediaUrl(src)" :alt="`${title || '课程'}封面`" @error="failed = true" />
    <svg v-else viewBox="0 0 240 180" role="img" :aria-label="`${title || '课程'}主题插画`" preserveAspectRatio="xMidYMid slice">
      <rect width="240" height="180" :fill="theme.bg" />
      <circle cx="208" cy="20" r="72" fill="#fff" opacity=".4" />
      <circle cx="20" cy="172" r="65" fill="#fff" opacity=".3" />
      <rect x="72" y="23" width="96" height="139" rx="17" :fill="theme.ink" opacity=".12" />
      <rect x="67" y="18" width="96" height="139" rx="17" fill="#fff" :stroke="theme.ink" stroke-width="5" />
      <path d="M99 29h33" :stroke="theme.ink" stroke-width="5" stroke-linecap="round" />
      <rect x="79" y="45" width="72" height="80" rx="10" :fill="theme.bg" />
      <g :stroke="theme.ink" stroke-width="5" stroke-linecap="round" stroke-linejoin="round" fill="none">
        <g v-if="theme.kind === 'audio'">
          <path d="M94 90V78a21 21 0 0 1 42 0v12" />
          <rect x="90" y="82" width="11" height="23" rx="5" />
          <rect x="129" y="82" width="11" height="23" rx="5" />
        </g>
        <g v-else-if="theme.kind === 'video'">
          <rect x="89" y="63" width="53" height="43" rx="7" />
          <path d="m110 75 14 10-14 10z" :fill="theme.ink" stroke="none" />
        </g>
        <g v-else-if="theme.kind === 'health'">
          <path d="M115 106 94 85c-17-19 10-34 21-16 11-18 38-3 21 16z" :fill="theme.ink" stroke="none" />
          <path d="M101 83h9l5-10 5 20 5-10h7" stroke="#fff" stroke-width="3" />
        </g>
        <g v-else-if="theme.kind === 'safety'">
          <path d="m115 58 23 9v19c0 14-23 24-23 24S92 100 92 86V67z" />
          <path d="m104 83 8 8 15-17" />
        </g>
        <g v-else>
          <path d="M88 67q14-7 27 0 13-7 27 0v37q-14-7-27 0-13-7-27 0zM115 67v37" />
          <path d="M96 78h10m-10 10h10m18-10h10m-10 10h10" stroke-width="3" />
        </g>
      </g>
      <path d="M94 137h42" :stroke="theme.ink" stroke-width="5" stroke-linecap="round" opacity=".35" />
      <circle cx="183" cy="133" r="21" :fill="theme.ink" />
      <path d="m178 124 13 9-13 9z" fill="#fff" />
    </svg>
    <slot />
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { mediaUrl } from '../utils/api-base'

const props = defineProps({ src: { type: String, default: '' }, title: { type: String, default: '' } })
const failed = ref(false)
watch(() => props.src, () => { failed.value = false })
const theme = computed(() => {
  const title = props.title
  if (/听书|新闻|语音/.test(title)) return { kind: 'audio', bg: '#e0eef7', ink: '#327295' }
  if (/视频|戏曲|拍照/.test(title)) return { kind: 'video', bg: '#f6e9d9', ink: '#a56732' }
  if (/健康|营养|饮食|就医|医院|药|医保|锻炼|太极/.test(title)) return { kind: 'health', bg: '#f5e3e5', ink: '#a94e61' }
  if (/安全|诈骗|骗局|谣言/.test(title)) return { kind: 'safety', bg: '#e5e8f7', ink: '#586caa' }
  return { kind: 'book', bg: '#dcefe3', ink: '#307a55' }
})
</script>

<style scoped>
.course-art { position: relative; overflow: hidden; }
.course-art > img, .course-art > svg { position: absolute; inset: 0; width: 100%; height: 100%; display: block; object-fit: cover; }
</style>
