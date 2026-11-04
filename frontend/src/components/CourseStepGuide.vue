<template>
  <section class="step-guide">
    <h2>跟着图片，一步一步学</h2>
    <p>{{ tutorial.goal }}</p>
    <p class="guide-note">准备：{{ tutorial.preparation }}</p>
    <p class="guide-note">{{ tutorial.scope }}</p>
    <article v-for="(step, index) in tutorial.steps" :key="step.title" class="step-card">
      <h3><span>{{ index + 1 }}</span>{{ step.title }}</h3>
      <p>{{ step.action }}</p>
      <figure v-if="step.image">
        <button v-if="!failedImages.has(step.image)" type="button" class="screenshot-button" :aria-label="`放大第${index + 1}步截图：${step.imageAlt}`" @click="preview(step)">
          <img :src="imageUrl(step.image)" :alt="step.imageAlt" loading="lazy" @error="failedImages.add(step.image)" />
          <span>点图片可放大，双指可缩放</span>
        </button>
        <p v-else class="image-error" role="status">截图暂时无法显示，请点下方来源查看原图，并参照本步文字操作。</p>
        <figcaption>
          <p>{{ step.imageCaption }}</p>
          <a :href="step.source.url" target="_blank" rel="noopener noreferrer">截图来源：{{ step.source.publisher }} · {{ step.source.title }}</a>
          <p>来源日期：{{ step.source.date || '原站未标注' }} · 核对日期：{{ step.source.checkedAt }}</p>
        </figcaption>
      </figure>
      <p class="step-check"><strong>做到这一步：</strong>{{ step.check }}</p>
    </article>
    <div class="guide-warning"><strong>请记住</strong><p>{{ tutorial.warning }}</p></div>
    <div class="guide-practice"><strong>自己试一试</strong><p>{{ tutorial.practice }}</p></div>
  </section>
</template>

<script setup>
import { reactive } from 'vue'
import { showImagePreview } from 'vant'
defineProps({ tutorial: { type: Object, required: true } })
const failedImages = reactive(new Set())
const imageUrl = (path) => `${import.meta.env.BASE_URL}${path.replace(/^\//, '')}`
const preview = (step) => showImagePreview({ images: [imageUrl(step.image)], closeable: true, showIndex: false, closeOnClickImage: false })
</script>

<style scoped>
.step-guide { color: #172b20; line-height: 1.8; }
.step-guide h2 { font-size: 22px; margin: 0 0 12px; }
.guide-note { font-size: 16px; color: #475569; }
.step-card { margin: 22px 0; padding: 16px; border: 1px solid #cbd5e1; border-radius: 14px; background: #fff; }
.step-card h3 { display: flex; gap: 10px; align-items: center; font-size: 20px; margin: 0; }
.step-card h3 span { background: #15803d; color: #fff; border-radius: 50%; min-width: 32px; text-align: center; }
figure { margin: 16px 0; }
.screenshot-button { display: block; width: 100%; padding: 12px; background: #f1f5f9; border: 0; border-radius: 12px; cursor: zoom-in; color: #475569; font: inherit; }
.screenshot-button img { display: block; width: auto; max-width: 100%; max-height: 680px; object-fit: contain; margin: auto; background: #fff; }
.screenshot-button span { display: block; margin-top: 10px; font-size: 15px; }
figcaption { font-size: 14px; color: #475569; overflow-wrap: anywhere; }
figcaption p { margin: 8px 0; }
figcaption a { color: #166534; text-decoration: underline; text-underline-offset: 3px; }
.image-error { padding: 16px; border: 1px solid #cbd5e1; border-radius: 12px; }
.step-check { background: #f0fdf4; padding: 12px; border-left: 4px solid #15803d; }
.guide-warning, .guide-practice { padding: 16px; border-radius: 12px; background: #fff7ed; margin: 16px 0; }
.guide-practice { background: #f0fdf4; }
</style>
