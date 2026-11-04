<template>
  <div class="brief-page">
    <van-nav-bar title="我的学习周报" left-arrow @click-left="router.back()" />

    <van-loading v-if="loading" class="page-loading" type="spinner">数据统计中…</van-loading>

    <van-empty v-else-if="failed" description="周报暂时加载失败"><van-button type="primary" round @click="loadBrief">重新加载</van-button></van-empty>

    <template v-else>
      <p class="report-period">{{ brief.startDate }} 至 {{ brief.endDate }} · 仅展示您的学习数据</p>
      <!-- 语音收听 -->
      <div class="listen-card">
        <div class="listen-title">🔊 收听我的周报</div>
        <TtsPlayer :text="brief.text" />
      </div>

      <!-- 数据概览 -->
      <div class="stat-grid">
        <div class="stat-card">
          <div class="stat-num">{{ brief.studyDays || 0 }}</div>
          <div class="stat-label">近7天学习（天）</div>
        </div>
        <div class="stat-card">
          <div class="stat-num">{{ brief.courseCount || 0 }}</div>
          <div class="stat-label">近7天学过（门）</div>
        </div>
        <div class="stat-card">
          <div class="stat-num">{{ brief.weekDuration || 0 }}</div>
          <div class="stat-label">近7天时长（分钟）</div>
        </div>
        <div class="stat-card">
          <div class="stat-num">{{ brief.todayDuration || 0 }}</div>
          <div class="stat-label">今日时长（分钟）</div>
        </div>
      </div>

      <van-empty v-if="!brief.weekDuration" description="最近7天还没有学习记录">
        <van-button type="primary" round @click="router.push('/courses')">去选一门课程</van-button>
      </van-empty>

      <!-- 我的学习小结 -->
      <div class="section">
        <h3 class="section-title">📄 我的学习小结</h3>
        <div class="brief-text">{{ brief.text }}</div>
      </div>
    </template>

    <div class="tabbar-holder"></div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import TtsPlayer from '../components/TtsPlayer.vue'
import { getLatestBrief } from '../api/brief'

const router = useRouter()
const loading = ref(true)
const failed = ref(false)
const brief = reactive({
  text: '', startDate: '', endDate: '', studyDays: 0,
  courseCount: 0, weekDuration: 0, todayDuration: 0
})

async function loadBrief() {
  loading.value = true
  failed.value = false
  try {
    const res = await getLatestBrief()
    Object.assign(brief, res.data || {})
  } catch (e) {
    failed.value = true
  } finally {
    loading.value = false
  }
}
onMounted(loadBrief)
</script>

<style scoped>
.report-period { margin: 16px; color: var(--van-text-color-2); font-size: 15px; line-height: 1.6; }
.page-loading {
  text-align: center;
  padding-top: 80px;
}
.listen-card {
  margin: 12px 16px;
  padding: 16px;
  background: linear-gradient(135deg, #fff4e8, #ffe7cf);
  border-radius: 14px;
}
.listen-title {
  font-size: 18px;
  font-weight: 700;
  color: #8a5a20;
  margin-bottom: 10px;
}
.stat-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
  margin: 0 16px;
}
.stat-card {
  background: #fff;
  border-radius: 12px;
  padding: 16px;
  text-align: center;
  box-shadow: 0 2px 8px rgba(242, 122, 26, 0.07);
}
.stat-num {
  font-size: 28px;
  font-weight: 800;
  color: #16a34a;
}
.stat-label {
  font-size: 14px;
  color: #888;
  margin-top: 4px;
}
.brief-text {
  white-space: pre-wrap;
  font-size: 16px;
  line-height: 1.9;
  color: #333;
  background: #faf7f3;
  border-radius: 10px;
  padding: 16px;
}
</style>
