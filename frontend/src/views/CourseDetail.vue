<template>
  <div class="course-detail-page">
    <van-nav-bar title="课程详情" left-arrow @click-left="router.back()">
      <template #right>
        <span class="fav-entry" @click="toggleFavorite">
          {{ course.favorite ? '❤️ 已收藏' : '🤍 收藏' }}
        </span>
      </template>
    </van-nav-bar>

    <!-- 封面（cover 为空显示占位） -->
    <CourseCover class="detail-cover" :src="course.cover" :title="course.title">
      <div class="cover-shade"></div>
      <div class="cover-caption">
        <span>{{ categoryName || '数字学习' }}</span>
        <span>{{ course.videoUrl ? '▶ 视频课程' : '📄 图文课程' }}</span>
      </div>
    </CourseCover>

    <div class="detail-body">
      <h2 class="page-title">{{ course.title }}</h2>
      <p class="course-intro-text">{{ course.intro || '暂无简介' }}</p>
      <div class="detail-tags">
        <van-tag type="primary" size="medium">⏱ {{ course.duration || 0 }} 分钟</van-tag>
        <van-tag type="success" size="medium">+{{ course.points || 0 }} 积分</van-tag>
        <van-tag v-if="categoryName" type="warning" size="medium">{{ categoryName }}</van-tag>
      </div>
      <div class="detail-read">
        <TtsPlayer v-if="course.title" :text="listenText" />
        <span>点按可朗读课程简介</span>
      </div>
    </div>

    <div class="detail-section" v-if="progress > 0">
      <div class="section-head">
        <span>学习进度</span>
        <strong>{{ Math.round(progress) }}%</strong>
      </div>
      <van-progress :percentage="Math.round(progress)" color="#16a34a" stroke-width="10" :show-pivot="false" />
      <p>{{ progress >= 100 ? '您已完成本课程，随时可以再复习一次。' : '您上次学到这里，继续学习吧。' }}</p>
    </div>

    <div class="detail-section course-preview" v-if="contentSummary">
      <h3>本节内容</h3>
      <p>{{ contentSummary }}</p>
    </div>

    <div class="detail-section learn-tips">
      <h3>学习小提示</h3>
      <div><span>1</span> 跟着步骤慢慢操作，不明白可以多看一遍。</div>
      <div><span>2</span> 点击朗读按钮，可以边听边学。</div>
      <div><span>3</span> 完成后记得领取学习积分。</div>
    </div>

    <div class="page-bottom-space"></div>
    <div class="fixed-bottom">
      <van-button
        class="fav-bottom-btn"
        :type="course.favorite ? 'default' : 'primary'"
        plain
        size="large"
        @click="toggleFavorite"
      >
        {{ course.favorite ? '❤️ 已收藏' : '🤍 收藏' }}
      </van-button>
      <van-button type="primary" size="large" block @click="router.push(`/study/${id}`)">
        {{ progress > 0 && progress < 100 ? '继续学习' : progress >= 100 ? '再次学习' : '开始学习' }}
      </van-button>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast } from 'vant'
import { getCourseDetail } from '../api/course'
import { getCategoryList } from '../api/category'
import { addFavorite, removeFavorite } from '../api/favorite'
import TtsPlayer from '../components/TtsPlayer.vue'
import CourseCover from '../components/CourseCover.vue'

const route = useRoute()
const router = useRouter()
const id = route.params.id

const course = ref({})
const categories = ref([])
const favLoading = ref(false)
const progress = ref(0)

const toggleFavorite = async () => {
  if (favLoading.value) return
  favLoading.value = true
  try {
    if (course.value.favorite) {
      await removeFavorite(id)
      course.value.favorite = false
      showToast('已取消收藏')
    } else {
      await addFavorite(id)
      course.value.favorite = true
      showToast('收藏成功，可在"我的收藏"中查看')
    }
  } catch (e) {
    // 拦截器已提示
  } finally {
    favLoading.value = false
  }
}

const categoryName = computed(() => {
  // 后端详情接口已填充分类名，优先使用
  if (course.value.categoryName) return course.value.categoryName
  const c = categories.value.find((x) => x.id === course.value.categoryId)
  return c ? c.categoryName || c.name : ''
})

const contentSummary = computed(() => (course.value.content || '')
  .replace(/<[^>]+>/g, ' ')
  .replace(/\s+/g, ' ')
  .trim()
  .slice(0, 110))

const listenText = computed(() => {
  const summary = contentSummary.value
  return `${course.value.title || ''}。${course.value.intro || ''}${summary ? `。本节内容：${summary}` : ''}`
})

onMounted(async () => {
  try {
    const res = await getCourseDetail(id)
    // 后端返回 { course: {...}, progress: 已有进度 }
    course.value = res.data.course || {}
    progress.value = Number(res.data.progress) || 0
  } catch (e) {
    // 拦截器已提示
  }
  try {
    const res = await getCategoryList()
    const data = res.data || []
    categories.value = Array.isArray(data) ? data : data.list || []
  } catch (e) {
    // 拦截器已提示
  }
})
</script>

<style scoped>
.fav-entry {
  font-size: 16px;
  color: #16a34a;
}
.fixed-bottom {
  display: flex;
  gap: 10px;
}
.fav-bottom-btn {
  flex-shrink: 0;
  width: 130px;
}
.detail-cover { position: relative; overflow: hidden; }
.cover-shade { position: absolute; inset: 0; background: linear-gradient(180deg, transparent 42%, rgba(46, 28, 14, .55)); }
.cover-caption { position: absolute; bottom: 14px; left: 16px; right: 16px; display: flex; justify-content: space-between; color: #fff; font-size: 15px; font-weight: 600; }
.detail-read { display: flex; align-items: center; gap: 8px; margin-top: 16px; color: #8a5a20; font-size: 14px; }
.detail-section { margin: 12px 16px 0; padding: 16px; background: #fff; border-radius: 12px; }
.detail-section h3 { margin: 0 0 10px; font-size: 18px; color: #1a1a1a; }
.detail-section p { margin: 10px 0 0; color: #555; line-height: 1.7; font-size: 16px; }
.section-head { display: flex; justify-content: space-between; align-items: center; font-size: 17px; margin-bottom: 10px; color: #333; }
.section-head strong { color: #16a34a; font-size: 20px; }
.learn-tips div { display: flex; align-items: flex-start; gap: 10px; padding: 7px 0; color: #444; line-height: 1.55; font-size: 16px; }
.learn-tips span { width: 22px; height: 22px; flex: 0 0 22px; border-radius: 50%; background: #e0f5e8; color: #15803d; text-align: center; line-height: 22px; font-weight: 700; }
</style>
