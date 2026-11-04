<template>
  <div class="gov-page">
    <van-nav-bar title="政务服务直达" left-arrow @click-left="router.back()" />

    <!-- 绑定地区提示 -->
    <div class="region-bar">
      <van-icon name="location-o" size="20" />
      <span class="region-text">{{ data.region ? `当前地区：${data.region}` : '尚未绑定常住地区' }}</span>
      <van-button v-if="!data.region" size="mini" type="primary" @click="router.push('/display')">
        去绑定
      </van-button>
    </div>

    <!-- 全国通用办事入口 -->
    <div class="section">
      <h3 class="section-title">🏛 全国通用服务</h3>
      <van-grid :column-num="3" :border="false" gutter="8">
        <van-grid-item
          v-for="s in data.nationalServices"
          :key="s.id"
          :icon="iconOf(s.icon)"
          :text="s.name"
          @click="openUrl(s.serviceUrl, s.name)"
        />
      </van-grid>
    </div>

    <!-- 本地办事入口 -->
    <div class="section" v-if="data.region">
      <h3 class="section-title">📍 本地办事（{{ data.region }}）</h3>
      <van-empty v-if="!data.localServices?.length" description="暂无本地服务入口" image-size="80" />
      <van-grid v-else :column-num="3" :border="false" gutter="8">
        <van-grid-item
          v-for="s in data.localServices"
          :key="s.id"
          :icon="iconOf(s.icon)"
          :text="s.name"
          @click="openUrl(s.serviceUrl, s.name)"
        />
      </van-grid>
    </div>

    <!-- 全国政策 -->
    <div class="section">
      <h3 class="section-title">📰 国家政策</h3>
      <van-cell
        v-for="p in data.nationalPolicies"
        :key="p.id"
        :title="p.title"
        :label="policyLabel(p)"
        is-link
        @click="openPolicy(p)"
      />
      <van-empty v-if="!data.nationalPolicies?.length" description="暂无政策" image-size="80" />
    </div>

    <!-- 地方政策 -->
    <div class="section" v-if="data.region">
      <h3 class="section-title">🏘 本地政策（{{ data.region }}）</h3>
      <van-empty v-if="!data.localPolicies?.length" description="暂无本地政策" image-size="80" />
      <van-cell
        v-for="p in data.localPolicies"
        :key="p.id"
        :title="p.title"
        :label="policyLabel(p)"
        is-link
        @click="openPolicy(p)"
      />
    </div>

    <div class="tabbar-holder"></div>

    <!-- 政策详情 -->
    <van-popup v-model:show="policyVisible" position="bottom" round teleport="body" :style="{ maxHeight: '72%' }">
      <div class="policy-popup">
        <h2 class="policy-title">{{ current.title }}</h2>
        <div class="policy-meta">{{ policyLabel(current) }}</div>
        <van-tag v-if="current.policyLevel === 'NATIONAL'" type="primary">全国通用</van-tag>
        <van-tag v-else type="warning">{{ current.province || '本地政策' }}</van-tag>
        <div class="policy-content">{{ current.content || '暂无政策正文，请查看原文链接。' }}</div>
        <TtsPlayer :text="current.title + '。' + (current.content || '暂无政策正文')" />
        <van-button
          v-if="current.govUrl"
          block
          round
          type="primary"
          size="large"
          @click="openUrl(current.govUrl, current.title)"
        >🔗 查看政策原文</van-button>
      </div>
    </van-popup>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { getGovHome } from '../api/gov'
import TtsPlayer from '../components/TtsPlayer.vue'

const router = useRouter()
const data = reactive({
  region: '',
  nationalServices: [],
  localServices: [],
  nationalPolicies: [],
  localPolicies: []
})

// 后端存的是 emoji，Vant grid-item 的 icon 支持图片 URL，emoji 用插槽替代不了，
// 这里返回空图标名，名称前已由接口提供，直接展示 emoji 在前缀拼接
const iconOf = () => ''

const policyLabel = (p) => {
  const parts = []
  if (p.source) parts.push(p.source)
  if (p.publishTime) parts.push(String(p.publishTime).slice(0, 10))
  return parts.join(' · ')
}

const openUrl = (url, name) => {
  if (!url) {
    showToast('该服务暂未配置链接')
    return
  }
  // APP WebView / 浏览器均通过新窗口打开系统浏览器，不离开学习应用
  window.open(url, '_blank', 'noopener')
}

const policyVisible = ref(false)
const current = reactive({})

const openPolicy = (p) => {
  Object.keys(current).forEach((k) => delete current[k])
  Object.assign(current, p)
  policyVisible.value = true
}

onMounted(async () => {
  try {
    const res = await getGovHome()
    Object.assign(data, res.data || {})
    // emoji 图标直接拼到服务名前
    data.nationalServices = (data.nationalServices || []).map((s) => ({
      ...s,
      name: `${s.icon && !s.name.startsWith(s.icon) ? s.icon + ' ' : ''}${s.name}`
    }))
    data.localServices = (data.localServices || []).map((s) => ({
      ...s,
      name: `${s.icon && !s.name.startsWith(s.icon) ? s.icon + ' ' : ''}${s.name}`
    }))
  } catch (e) { /* 拦截器已提示 */ }
})
</script>

<style scoped>
.region-bar {
  margin: 12px 16px 0;
  padding: 12px 16px;
  background: #fff8f0;
  border: 1px solid #ffe0c2;
  border-radius: 12px;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  color: #8a5a20;
}
.region-text {
  flex: 1;
}
.gov-page .van-grid-item__text {
  font-size: 14px;
  line-height: 1.35;
}
.policy-popup {
  padding: 20px 18px 24px;
}
.policy-title {
  font-size: 20px;
  margin: 0 0 8px;
  color: #1a1a1a;
}
.policy-meta {
  font-size: 13px;
  color: #999;
  margin-bottom: 10px;
}
.policy-content {
  white-space: pre-wrap;
  font-size: 16px;
  line-height: 1.75;
  color: #333;
  background: #faf7f3;
  border-radius: 10px;
  padding: 14px;
  margin: 14px 0;
  max-height: 44vh;
  overflow-y: auto;
}
</style>
