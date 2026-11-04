<template>
  <div class="home-page">
    <div class="home-header">
      <h2 class="page-title">老年数字素养学习系统</h2>
      <p class="welcome">您好，{{ nickname }}！今天也要开心学习哦 🌞</p>
    </div>

    <!-- 轮播 Banner（CSS 渐变占位） -->
    <van-swipe class="banner-swipe" :autoplay="4000" indicator-color="#ffffff">
      <van-swipe-item v-for="(b, i) in banners" :key="i" class="banner-item" :style="{ background: b.bg }">
        <div class="banner-inner">
          <div class="banner-title">{{ b.title }}</div>
          <div class="banner-sub">{{ b.sub }}</div>
        </div>
      </van-swipe-item>
    </van-swipe>

    <!-- 系统/政务公告滚动条（点击查看消息中心） -->
    <van-notice-bar
      v-if="notice"
      class="home-notice"
      left-icon="volume-o"
      :text="notice.title"
      scrollable
      @click="router.push('/messages')"
    />

    <!-- 常用功能宫格（彩色图标块） -->
    <div class="section">
      <h3 class="section-title">🧡 常用功能</h3>
      <div class="tile-grid">
        <button v-for="t in quickTiles" :key="t.text" type="button" class="tile" @click="router.push(t.to)">
          <div class="tile-icon" :style="{ background: t.bg }">{{ t.emoji }}</div>
          <span class="tile-text">{{ t.text }}</span>
          <van-badge v-if="t.badge" :content="unread" class="tile-badge" />
        </button>
      </div>
    </div>

    <!-- 五大分类宫格（彩色图标块） -->
    <div class="section">
      <h3 class="section-title">📚 学习分类</h3>
      <div class="tile-grid">
        <button v-for="c in topCategories" :key="c.id" type="button" class="tile" @click="goCategory(c)">
          <div class="tile-icon" :style="{ background: tileOf(c.name).bg }">{{ tileOf(c.name).emoji }}</div>
          <span class="tile-text">{{ c.name }}</span>
        </button>
      </div>
      <van-empty v-if="loaded && !topCategories.length" description="暂无可选分类" image-size="80" />
    </div>

    <!-- 混合推荐：每条课程显示实际来源与原因 -->
    <div class="section">
      <h3 class="section-title">⭐ 猜你喜欢</h3>
      <van-notice-bar v-if="courses.some(c => c.recommendationDataScope === 'mock')" text="模拟学员推荐展示，仅用于测试和演示" wrapable />
      <p v-if="courses.length" class="section-tip">参考已学课程与学习偏好推荐，候选不足时补充热门或最新课程</p>
      <van-empty v-if="loaded && !courses.length" :description="recommendationFailed ? '推荐加载失败，请稍后重试' : '暂无未学课程，去课程页看看吧'" image-size="80" />
      <div
        v-for="item in courses"
        :key="item.id"
        class="course-card"
        @click="router.push(`/course/${item.id}`)"
      >
        <CourseCover class="course-cover" :src="item.cover" :title="item.title" />
        <div class="course-info">
          <div class="course-title">{{ item.title }}</div>
          <div class="course-intro">{{ item.intro || '开启本课程的学习之旅吧' }}</div>
          <div class="course-meta">
            <van-tag v-for="source in item.recommendationSources || []" :key="source" plain type="primary" round>
              {{ recommendationLabel(source) }}
            </van-tag>
            <van-tag type="success" round>＋{{ item.pointsReward ?? item.points ?? 0 }} 积分</van-tag>
          </div>
          <p v-if="item.recommendationReason" class="recommendation-reason">{{ item.recommendationReason }}</p>
        </div>
      </div>
    </div>

    <div class="tabbar-holder"></div>

    <!-- 底部导航 -->
    <van-tabbar route>
      <van-tabbar-item replace to="/home" icon="home-o">首页</van-tabbar-item>
      <van-tabbar-item replace to="/courses" icon="bookmark-o">课程</van-tabbar-item>
      <van-tabbar-item replace to="/ai" icon="chat-o">AI问答</van-tabbar-item>
      <van-tabbar-item replace to="/mine" icon="user-o">我的</van-tabbar-item>
    </van-tabbar>
  </div>
</template>

<script setup>
import CourseCover from '../components/CourseCover.vue'
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getCategoryList } from '../api/category'
import { getRecommendCourses } from '../api/course'
import { getPublishedAnnouncements } from '../api/announcement'
import { getMessageSummary } from '../api/message'
import { useUserStore } from '../store/user'

const router = useRouter()
const store = useUserStore()

const nickname = computed(() => store.userInfo?.nickname || store.userInfo?.username || '老友')

const banners = [
  { title: '学手机，会生活', sub: '政务办事、就医挂号一学就会', bg: 'linear-gradient(135deg, #16a34a 0%, #4ade80 100%)' },
  { title: '反诈防骗，守好钱袋', sub: '擦亮眼睛，不听不信不转账', bg: 'linear-gradient(135deg, #0d9488 0%, #2dd4bf 100%)' },
  { title: '便民生活，乐享晚年', sub: '看视频、听语音，学习更轻松', bg: 'linear-gradient(135deg, #65a30d 0%, #a3e635 100%)' }
]

// 常用功能宫格（emoji + 彩底圆角块）
const quickTiles = [
  { emoji: '🏛️', text: '政务直达', to: '/gov', bg: '#e3f0fd' },
  { emoji: '🪙', text: '积分打卡', to: '/points', bg: '#fff4dd' },
  { emoji: '🎁', text: '福利兑换', to: '/prizes', bg: '#fde5ee' },
  { emoji: '🤖', text: 'AI陪伴', to: '/companion', bg: '#efe8fd' },
  { emoji: '📊', text: '学习周报', to: '/brief', bg: '#dcf3f0' },
  { emoji: '❤️', text: '我的收藏', to: '/favorites', bg: '#fde4df' },
  { emoji: '🔔', text: '消息通知', to: '/messages', bg: '#fff7d6', badge: true },
  { emoji: '⏰', text: '定时提醒', to: '/reminder', bg: '#e0f5e8' }
]

const topCategories = ref([])
const courses = ref([])
const recommendationFailed = ref(false)
const loaded = ref(false)
const recommendationLabel = (source) => ({
  content: '相似课程', als: '学习偏好', item_cf: '共学关联', popular: '热门补充', latest: '最新课程'
}[source] || '课程推荐')
const notice = ref(null)
const unread = ref(0)

// 分类名 → 彩色图标块
const tileOf = (name = '') => {
  const s = name || ''
  if (s.includes('手机') || s.includes('操作') || s.includes('智能')) return { emoji: '📱', bg: '#e3f0fd' }
  if (s.includes('生活') || s.includes('服务')) return { emoji: '🏠', bg: '#dcf3f0' }
  if (s.includes('反诈') || s.includes('诈骗') || s.includes('安全')) return { emoji: '🛡️', bg: '#fde4df' }
  if (s.includes('健康') || s.includes('养生')) return { emoji: '💚', bg: '#e0f5e8' }
  if (s.includes('休闲') || s.includes('娱乐')) return { emoji: '🎵', bg: '#efe8fd' }
  return { emoji: '📚', bg: '#eef2f7' }
}

const goCategory = (c) => {
  router.push({ path: '/courses', query: { cid: c.id } })
}

onMounted(async () => {
  // 最新一条已发布公告（顶部滚动条）
  try {
    const res = await getPublishedAnnouncements()
    const rows = res.data || []
    notice.value = Array.isArray(rows) && rows.length ? rows[0] : null
  } catch (e) {
    // 拦截器已提示
  }
  // 未读消息角标
  try {
    const res = await getMessageSummary()
    unread.value = res.data?.unread ?? 0
  } catch (e) {
    // 忽略
  }
  // 顶级分类
  try {
    const res = await getCategoryList()
    const data = res.data || []
    const list = Array.isArray(data) ? data : data.list || []
    topCategories.value = list.filter((c) => !c.parentId)
  } catch (e) {
    // 拦截器已提示
  }
  // 补位与已学过滤由推荐接口统一处理，前端不再用普通课程列表覆盖空结果。
  try {
    const res = await getRecommendCourses()
    courses.value = Array.isArray(res.data) ? res.data : res.data?.list || []
  } catch (e) {
    recommendationFailed.value = true
  }
  loaded.value = true
})
</script>

<style scoped>
.recommendation-reason {
  margin: 8px 0 0;
  color: var(--app-muted, #666);
  font-size: 15px;
  line-height: 1.6;
  overflow-wrap: anywhere;
}
.home-notice {
  margin: 10px 12px 0;
  border-radius: 10px;
}
</style>
