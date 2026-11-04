<template>
  <div class="article" v-html="articleHtml" @error.capture="imageError"></div>
</template>

<script setup>
import { computed } from 'vue'
import DOMPurify from 'dompurify'
import { mediaUrl } from '../utils/api-base'
import { resolveCourseImage } from '../utils/courseIllustrations.mjs'

const props = defineProps({ html: { type: String, default: '' } })
const articleHtml = computed(() => {
  const document = new DOMParser().parseFromString(DOMPurify.sanitize(props.html), 'text/html')
  for (const img of document.querySelectorAll('img[src]')) {
    img.setAttribute('src', resolveCourseImage(img.getAttribute('src'), mediaUrl))
  }
  return document.body.innerHTML
})
const imageError = event => {
  const img = event.target
  if (!(img instanceof HTMLImageElement) || img.dataset.failed) return
  img.dataset.failed = 'true'
  img.hidden = true
  const message = document.createElement('span')
  message.setAttribute('role', 'status')
  message.className = 'article-image-error'
  message.textContent = `图片暂时无法显示：${img.alt || '课程配图'}。请参照本段文字学习。`
  img.after(message)
}
</script>

<style scoped>
.article :deep(img) { max-width: 100%; height: auto; }
.article :deep(.article-image-error) { display: block; padding: 16px; border-radius: 12px; background: #f0f6f2; color: #385544; }
</style>
