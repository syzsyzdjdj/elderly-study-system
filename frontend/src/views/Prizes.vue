<template>
  <div class="prizes-page">
    <van-nav-bar title="积分兑换福利" left-arrow @click-left="router.back()" />

    <van-tabs v-model:active="activeTab" sticky @change="onTabChange">
      <van-tab title="可兑换福利" name="list" />
      <van-tab title="我的兑换" name="my" />
    </van-tabs>

    <!-- 可兑换 -->
    <div v-if="activeTab === 'list'" class="section">
      <div class="points-bar">
        <span>🏅 我的积分：<b>{{ points }}</b></span>
        <van-button size="small" type="primary" plain to="/points">去赚积分</van-button>
      </div>
      <van-empty v-if="loaded && !prizes.length" description="暂无可兑换的福利" image-size="90" />
      <div class="prize-grid">
        <div v-for="p in prizes" :key="p.id" class="prize-card">
          <div class="prize-cover">
            <img v-if="p.cover" :src="mediaUrl(p.cover)" alt="奖品" />
            <span v-else class="prize-emoji">🎁</span>
          </div>
          <div class="prize-name">{{ p.name }}</div>
          <div class="prize-desc">{{ p.description || '积分专享福利' }}</div>
          <TtsPlayer :text="p.name + '。' + (p.description || '积分专享福利')" class="prize-tts" />
          <div class="prize-foot">
            <span class="prize-cost">{{ p.pointsCost }} 分</span>
            <van-tag v-if="p.stock <= 0" type="danger">已兑完</van-tag>
            <van-tag v-else type="success">剩 {{ p.stock }} 份</van-tag>
          </div>
          <van-button
            block
            round
            type="primary"
            size="small"
            :disabled="p.stock <= 0 || points < p.pointsCost"
            @click="onExchange(p)"
          >
            {{ points < p.pointsCost ? '积分不足' : '立即兑换' }}
          </van-button>
        </div>
      </div>
    </div>

    <!-- 我的兑换 -->
    <div v-if="activeTab === 'my'" class="section">
      <van-empty v-if="loaded && !myList.length" description="还没有兑换记录" image-size="90" />
      <van-cell
        v-for="e in myList"
        :key="e.id"
        :title="e.prizeName"
        :label="`兑换时间：${fmtTime(e.createTime)}`"
      >
        <template #value>
          <van-tag :type="statusTag(e.status)" round>{{ statusName(e.status) }}</van-tag>
        </template>
      </van-cell>
    </div>

    <div class="tabbar-holder"></div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showToast } from 'vant'
import { getPrizeList, exchangePrize, getMyExchanges } from '../api/prize'
import { getPointSummary } from '../api/point'
import { mediaUrl } from '../utils/api-base'
import { useUserStore } from '../store/user'
import TtsPlayer from '../components/TtsPlayer.vue'

const router = useRouter()
const store = useUserStore()

const activeTab = ref('list')
const prizes = ref([])
const myList = ref([])
const points = ref(0)
const loaded = ref(false)

const STATUS = { PENDING: ['待发放', 'warning'], DONE: ['已发放', 'success'], CANCEL: ['已取消', 'danger'] }
const statusName = (s) => STATUS[s]?.[0] || s
const statusTag = (s) => STATUS[s]?.[1] || 'default'

const fmtTime = (t) => (t ? String(t).replace('T', ' ').slice(0, 16) : '')

const loadPrizes = async () => {
  try {
    const res = await getPrizeList()
    prizes.value = res.data || []
  } catch (e) { /* 拦截器已提示 */ }
}

const loadMy = async () => {
  try {
    const res = await getMyExchanges()
    myList.value = res.data || []
  } catch (e) { /* 拦截器已提示 */ }
}

const loadPoints = async () => {
  try {
    const res = await getPointSummary()
    points.value = res.data?.totalPoints || 0
    if (store.userInfo) store.setUserInfo({ ...store.userInfo, points: res.data?.totalPoints ?? 0 })
  } catch (e) { /* 拦截器已提示 */ }
}

const onExchange = async (p) => {
  try {
    await showConfirmDialog({
      title: '确认兑换',
      message: `用 ${p.pointsCost} 积分兑换「${p.name}」吗？兑换后由管理员线下联系发放。`
    })
  } catch {
    return
  }
  try {
    await exchangePrize(p.id)
    showToast('兑换成功，请等待工作人员发放')
    await Promise.all([loadPrizes(), loadPoints(), loadMy()])
  } catch (e) {
    // 拦截器已提示
  }
}

const onTabChange = (name) => {
  if (name === 'my') loadMy()
}

onMounted(async () => {
  await Promise.all([loadPrizes(), loadPoints()])
  loaded.value = true
})
</script>

<style scoped>
.points-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff8f0;
  border: 1px solid #ffe0c2;
  border-radius: 12px;
  padding: 12px 16px;
  margin-bottom: 14px;
  font-size: 17px;
}
.points-bar b {
  color: #16a34a;
  font-size: 20px;
}
.prize-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}
.prize-card {
  background: #fff;
  border-radius: 14px;
  padding: 12px;
  box-shadow: 0 2px 10px rgba(242, 122, 26, 0.08);
}
.prize-cover {
  height: 110px;
  border-radius: 10px;
  background: linear-gradient(135deg, #fff1e2, #ffe4c8);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  margin-bottom: 8px;
}
.prize-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.prize-emoji {
  font-size: 42px;
}
.prize-name {
  font-size: 17px;
  font-weight: 700;
  color: #1a1a1a;
  margin-bottom: 4px;
}
.prize-desc {
  font-size: 13px;
  color: #888;
  height: 36px;
  overflow: hidden;
}
.prize-tts {
  margin: 4px 0;
}
.prize-foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 8px 0;
}
.prize-cost {
  color: #16a34a;
  font-weight: 800;
  font-size: 18px;
}
</style>
