<template>
  <div class="points-page">
    <van-nav-bar title="积分中心" left-arrow @click-left="router.back()" />

    <!-- 积分概览 + 打卡 -->
    <div class="point-hero">
      <div class="point-hero-top">
        <div>
          <div class="point-big">{{ summary.totalPoints || 0 }}</div>
          <div class="point-label">我的积分</div>
        </div>
        <div class="point-hero-right">
          <div class="point-sub">今日已得 <b>{{ summary.todayEarned || 0 }}</b> 分</div>
          <div class="point-sub">连续打卡 <b>{{ summary.continuousDays || 0 }}</b> 天</div>
        </div>
      </div>
      <van-button
        class="checkin-btn"
        block
        round
        size="large"
        :type="summary.checkedIn ? 'default' : 'primary'"
        :disabled="summary.checkedIn"
        :loading="loading"
        @click="onCheckin"
      >
        {{ summary.checkedIn ? '✅ 今日已打卡' : '📅 每日打卡（+5 积分）' }}
      </van-button>
    </div>

    <!-- 积分规则 -->
    <div class="section">
      <h3 class="section-title">📜 积分获取规则</h3>
      <van-cell
        v-for="r in rules"
        :key="r.id"
        :title="r.ruleName"
        :label="dailyText(r)"
      >
        <template #value>
          <div class="rule-cell">
            <TtsPlayer :text="r.ruleName + '，' + dailyText(r) + '，' + r.points + '分'" />
            <van-tag type="warning" round>+{{ r.points }} 分</van-tag>
          </div>
        </template>
      </van-cell>
    </div>

    <!-- 积分明细 -->
    <div class="section">
      <h3 class="section-title">🧾 积分明细</h3>
      <van-tabs v-model:active="activeType" sticky @change="loadRecords">
        <van-tab title="全部" name="" />
        <van-tab title="打卡" name="CHECKIN" />
        <van-tab title="课程" name="COURSE" />
        <van-tab title="问答" name="QA" />
        <van-tab title="兑换" name="EXCHANGE" />
      </van-tabs>
      <van-list
        v-model:loading="listLoading"
        :finished="finished"
        finished-text="没有更多记录了"
        @load="onLoadMore"
      >
        <van-cell
          v-for="r in records"
          :key="r.id"
          :title="r.remark || typeName(r.changeType)"
          :label="fmtTime(r.createTime)"
        >
          <template #value>
            <span :class="r.changeAmount >= 0 ? 'plus' : 'minus'">
              {{ r.changeAmount >= 0 ? '+' : '' }}{{ r.changeAmount }}
            </span>
          </template>
        </van-cell>
      </van-list>
    </div>

    <div class="tabbar-holder"></div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { getPointSummary, checkin, getPointRecords, getPointRules } from '../api/point'
import TtsPlayer from '../components/TtsPlayer.vue'

const router = useRouter()

const summary = reactive({ totalPoints: 0, todayEarned: 0, checkedIn: false, continuousDays: 0 })
const rules = ref([])
const loading = ref(false)

const activeType = ref('')
const records = ref([])
const page = ref(1)
const pageSize = 20
const listLoading = ref(false)
const finished = ref(false)

const TYPE_NAMES = {
  CHECKIN: '每日打卡',
  COURSE: '课程学习',
  QA: '智能问答',
  EXCHANGE: '积分兑换',
  ADMIN: '管理员调整'
}
const typeName = (t) => TYPE_NAMES[t] || t || '积分变动'

const dailyText = (r) => {
  if (r.dailyLimit && r.dailyLimit > 0) return `每日限 ${r.dailyLimit} 次`
  return '不限次数'
}

const fmtTime = (t) => (t ? String(t).replace('T', ' ').slice(0, 16) : '')

const loadSummary = async () => {
  try {
    const res = await getPointSummary()
    Object.assign(summary, res.data || {})
  } catch (e) { /* 拦截器已提示 */ }
}

const onCheckin = async () => {
  loading.value = true
  try {
    const res = await checkin()
    const d = res.data || {}
    showToast(`打卡成功，获得 ${d.earned ?? 5} 积分！`)
    await loadSummary()
    page.value = 1
    records.value = []
    finished.value = false
    loadRecords()
  } catch (e) {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

const loadRecords = () => {
  page.value = 1
  records.value = []
  finished.value = false
  listLoading.value = true
  onLoadMore()
}

const onLoadMore = async () => {
  try {
    const res = await getPointRecords({
      page: page.value,
      size: pageSize,
      type: activeType.value || undefined
    })
    const data = res.data || {}
    const rows = data.records || []
    records.value = records.value.concat(rows)
    page.value += 1
    if (records.value.length >= Number(data.total || 0)) finished.value = true
  } catch (e) {
    finished.value = true
  } finally {
    listLoading.value = false
  }
}

onMounted(async () => {
  loadSummary()
  try {
    const res = await getPointRules()
    rules.value = res.data || []
  } catch (e) { /* 拦截器已提示 */ }
  loadRecords()
})
</script>

<style scoped>
.point-hero {
  margin: 12px 16px;
  padding: 22px 20px 18px;
  border-radius: 16px;
  background: linear-gradient(135deg, #22c55e, #16a34a);
  color: #fff;
  box-shadow: 0 6px 18px rgba(22, 163, 74, 0.25);
}
.point-hero-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 18px;
}
.point-big {
  font-size: 42px;
  font-weight: 800;
  line-height: 1.1;
}
.point-label {
  font-size: 16px;
  opacity: 0.92;
}
.point-sub {
  font-size: 15px;
  margin-top: 6px;
  opacity: 0.95;
}
.checkin-btn {
  height: 54px;
  font-size: 19px;
}
.plus {
  color: #e6a100;
  font-weight: 700;
  font-size: 18px;
}
.minus {
  color: #e64531;
  font-weight: 700;
  font-size: 18px;
}
.rule-cell {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
}
</style>
