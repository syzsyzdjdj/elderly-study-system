<template>
  <div class="study-page">
    <van-nav-bar title="学习课程" left-arrow @click-left="router.back()" />

    <!-- 顶部标题 + 学习进度条 -->
    <div class="study-header">
      <h3 class="page-title">{{ course.title || '课程学习' }}</h3>
      <div class="progress-row">
        <span class="progress-label">学习进度</span>
        <van-progress class="progress-bar" :percentage="Math.round(progress)" color="#16a34a" stroke-width="10" :show-pivot="false" />
        <span class="progress-num">{{ Math.round(progress) }}%</span>
      </div>
    </div>

    <!-- 视频（B站官方播放器内嵌 / 直链视频文件） -->
    <div v-if="course.videoUrl" class="video-wrap">
      <iframe
        v-if="isBiliUrl(course.videoUrl)"
        class="course-video bili-embed"
        title="课程视频播放器"
        :src="videoSuspended ? 'about:blank' : biliEmbedUrl(course.videoUrl)"
        scrolling="no"
        frameborder="no"
        allowfullscreen="true"
        allow="encrypted-media; fullscreen;"
      ></iframe>
      <video
        v-else
        ref="videoRef"
        class="course-video"
        :src="mediaUrl(course.videoUrl)"
        controls
        playsinline
        preload="metadata"
        @timeupdate="onTimeUpdate"
        @ended="onVideoEnd"
      ></video>
    </div>

    <!-- 图文内容（滚动学习；视频课也可继续阅读） -->
    <div class="content-wrap" ref="contentRef">
      <div class="tts-row">
        <TtsPlayer v-if="listenText" :text="listenText" />
      </div>
      <div class="reader-tools">
        <span>阅读字号</span>
        <van-button size="small" plain @click="changeReadingSize(-1)">A−</van-button>
        <strong>{{ readingSize }}px</strong>
        <van-button size="small" plain @click="changeReadingSize(1)">A+</van-button>
      </div>
      <CourseStepGuide v-if="tutorial" :tutorial="tutorial" :style="{ fontSize: readingSize + 'px' }" />
      <CourseArticle v-else :style="{ fontSize: readingSize + 'px' }" :html="course.content || '<p>本课程暂未提供图文内容。</p>'" />
      <div class="content-end">— 已到底部，点击下方按钮标记完成 —</div>
    </div>

    <div class="page-bottom-space"></div>
    <div class="fixed-bottom" ref="completionRef">
      <van-button type="primary" size="large" block :disabled="finished" @click="markDone">
        {{ finished ? '✅ 已完成学习' : '标记完成' }}
      </van-button>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast } from 'vant'
import { getCourseDetail } from '../api/course'
import { saveStudyRecord } from '../api/study'
import { mediaUrl } from '../utils/api-base'
import TtsPlayer from '../components/TtsPlayer.vue'
import CourseStepGuide from '../components/CourseStepGuide.vue'
import CourseArticle from '../components/CourseArticle.vue'
import { courseTutorials } from '../utils/course-tutorials'
import { createActiveClock, subscribeActivity } from '../utils/appActivity.mjs'

const route = useRoute()
const router = useRouter()
const id = route.params.id

const course = ref({})
const progress = ref(0)
const finished = ref(false)
const videoRef = ref(null)
const contentRef = ref(null)
const completionRef = ref(null)
const readingSize = ref(18)
const tutorial = computed(() => course.value.title ? courseTutorials[Number(id)] : null)

const savedAt = ref(0) // 已保存过的最高进度
const activityClock = createActiveClock()
const videoSuspended = ref(false)
let unsubscribeActivity
let savedMinutes = 0
let saveTimer = null
let saving = false // 保存请求在途标记，避免并发重复请求
let needResave = false // 在途期间进度又前进，完成后补存一次

// 听全文文字（去掉 HTML 标签）
const listenText = computed(() => {
  if (tutorial.value) {
    const t = tutorial.value
    return [t.goal, t.preparation, ...t.steps.map((s, i) => `第${i + 1}步，${s.title}。${s.action}。${s.check}`), t.warning, t.practice].join('。')
  }
  const c = course.value
  const text = (c.content || '')
    .replace(/<[^>]+>/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
  return text || c.intro || c.title || ''
})

// B站视频：videoUrl 存视频页链接，内嵌官方播放器播放
const isBiliUrl = (u) => /bilibili\.com\/video\/|b23\.tv\//.test(u || '')
const biliEmbedUrl = (u) => {
  const m = (u || '').match(/\/video\/(BV[\w]+)/i)
  return m ? `https://player.bilibili.com/player.html?bvid=${m[1]}&autoplay=0&danmaku=0&high_quality=1` : ''
}

/** 保存学习进度（自动去重，已完成提示积分；请求串行化防止并发重复插入） */
const saveProgress = (p) => {
  const target = Math.min(100, Math.max(p, progress.value))
  const elapsedMinutes = Math.floor(activityClock.elapsedMs() / 60000)
  if (savedAt.value >= target && elapsedMinutes <= savedMinutes) return
  progress.value = target
  doSave(target)
}

const doSave = async (target) => {
  if (saving) {
    needResave = true
    return
  }
  saving = true
  try {
    const seconds = Math.max(1, Math.round(activityClock.elapsedMs() / 1000))
    const elapsedMinutes = Math.floor(seconds / 60)
    await saveStudyRecord({
      courseId: Number(id),
      progress: Math.round(target),
      studyTime: Math.max(0, elapsedMinutes - savedMinutes)
    })
    savedMinutes = elapsedMinutes
    savedAt.value = Math.max(savedAt.value, target)
    if (target >= 100 && !finished.value) {
      finished.value = true
      const points = Number(course.value.points || 0)
      showToast(`完成学习 +${points} 积分`)
    }
  } catch (e) {
    // 拦截器已提示
  } finally {
    saving = false
    // 在途期间进度又前进了，按最新进度补存
    if (needResave) {
      needResave = false
      doSave(Math.round(progress.value))
    }
  }
}

// 视频：按播放时间 / 总时长计算进度
const onTimeUpdate = () => {
  const v = videoRef.value
  if (!v || !v.duration) return
  progress.value = Math.max(progress.value, Math.min(100, (v.currentTime / v.duration) * 100))
}

const onVideoEnd = () => saveProgress(100)

// 整页滚动：以正文移过可阅读视口的距离计算进度，扣除底部完成按钮。
const onContentScroll = () => {
  const el = contentRef.value
  if (!el) return
  const visibleHeight = window.innerHeight - (completionRef.value?.offsetHeight || 0)
  const max = el.scrollHeight - visibleHeight
  if (max <= 0) return
  const travelled = Math.max(0, -el.getBoundingClientRect().top)
  progress.value = Math.max(progress.value, Math.min(100, (travelled / max) * 100))
  if (progress.value >= 70) saveProgress(70) // 滚动到 70% 算 70
}

const markDone = () => {
  saveProgress(100)
}

const changeReadingSize = (delta) => {
  readingSize.value = Math.max(16, Math.min(26, readingSize.value + delta))
}

onMounted(async () => {
  window.addEventListener('scroll', onContentScroll, { passive: true })
  unsubscribeActivity = subscribeActivity(active => {
    activityClock.setActive(active)
    clearInterval(saveTimer)
    videoSuspended.value = !active
    if (active) {
      saveTimer = setInterval(() => {
        if (activityClock.elapsedMs() >= 30000 && progress.value > 0) saveProgress(progress.value)
      }, 15000)
    } else {
      videoRef.value?.pause()
      if (activityClock.elapsedMs() >= 30000 && progress.value > 0) saveProgress(progress.value)
    }
  })
  try {
    const res = await getCourseDetail(id)
    // 后端返回 { course: {...}, progress: 已有进度 }
    course.value = res.data.course || {}
    const prev = Number(res.data.progress) || 0
    progress.value = prev
    savedAt.value = prev
    finished.value = prev >= 100
  } catch (e) {
    // 拦截器已提示
  }
})

onBeforeUnmount(() => {
  unsubscribeActivity?.()
  activityClock.setActive(false)
  window.removeEventListener('scroll', onContentScroll)
  if (saveTimer) clearInterval(saveTimer)
  // 离开页面时兜底保存
  const elapsed = activityClock.elapsedMs() / 1000
  if (elapsed >= 30 && progress.value > 0) saveProgress(progress.value)
})
</script>
