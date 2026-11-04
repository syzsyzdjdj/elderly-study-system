<template>
  <div class="companion-page">
    <van-nav-bar title="AI 数字陪伴" left-arrow @click-left="router.back()" />

    <div class="companion-hero">
      <div class="hero-avatar">👵</div>
      <div class="hero-text">
        <div class="hero-title">{{ tip.greeting || '您好呀，今天也要开心学习！' }}</div>
        <div class="hero-sub">陪您学手机、办事情，实用功能一键直达</div>
      </div>
    </div>

    <WeatherCard />

    <!-- 今日贴心推荐：基于个人学习数据动态生成 -->
    <div v-if="tip.tipText" class="daily-tip">
      <div class="tip-head">
        <span class="tip-badge">📌 今日贴心推荐</span>
        <van-tag v-if="tip.source" plain type="success" size="large">{{ tip.source }}</van-tag>
      </div>
      <div v-if="tip.statText" class="tip-stat">{{ tip.statText }}</div>
      <div class="tip-content">{{ tip.tipText }}</div>
      <div class="tip-actions">
        <TtsPlayer :text="tip.statText ? tip.statText + '。' + tip.tipText : tip.tipText" />
        <van-button
          v-if="tip.courseId"
          type="primary"
          size="small"
          icon="arrow"
          @click="router.push(`/course/${tip.courseId}`)"
        >去学习</van-button>
      </div>
    </div>

    <div class="quick-actions">
      <van-button type="primary" icon="chat-o" size="large" @click="router.push('/ai')">
        和 AI 助手聊聊天
      </van-button>
      <van-button plain type="primary" icon="clock-o" size="large" @click="router.push('/reminder')">
        定时提醒设置
      </van-button>
    </div>

    <section class="tools-section" aria-labelledby="tools-title">
      <h2 id="tools-title" class="tools-title">实用工具</h2>
      <p class="tools-description">学习、办事，点一下就能找到</p>
      <div class="tools-grid">
        <button v-for="tool in tools" :key="tool.path" type="button" class="tool-card" @click="router.push(tool.path)">
          <span class="tool-icon"><van-icon :name="tool.icon" /></span>
          <span class="tool-copy">
            <span class="tool-title">{{ tool.title }}</span>
            <span class="tool-description">{{ tool.description }}</span>
          </span>
          <van-icon name="arrow" class="tool-arrow" />
        </button>
      </div>
    </section>

    <div class="tabbar-holder"></div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import TtsPlayer from '../components/TtsPlayer.vue'
import WeatherCard from '../components/WeatherCard.vue'
import { getDailyTip } from '../api/companion'

const router = useRouter()

const tools = [
  { title: '找课程', description: '学手机操作和生活技能', icon: 'play-circle-o', path: '/courses' },
  { title: '我的收藏', description: '复习收藏的课程', icon: 'star-o', path: '/favorites' },
  { title: '便民服务', description: '查找办事入口和政策', icon: 'service-o', path: '/gov' },
  { title: '学习周报', description: '看看最近的学习收获', icon: 'chart-trending-o', path: '/brief' }
]
const tip = ref({})

onMounted(async () => {
  // 今日贴心推荐
  try {
    const res = await getDailyTip()
    tip.value = res.data || {}
  } catch (e) {
    // 推荐暂不可用时仍可使用下方工具
  }

})
</script>

<style scoped>
.companion-hero {
  display: flex;
  align-items: center;
  gap: 14px;
  margin: 12px 16px;
  padding: 18px;
  background: linear-gradient(135deg, #e6f7ec, #cdefda);
  border-radius: 16px;
}
.hero-avatar {
  font-size: 46px;
  width: 64px;
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fff;
  border-radius: 50%;
  flex-shrink: 0;
}
.hero-title {
  font-size: 19px;
  font-weight: 800;
  color: #7a4a12;
  margin-bottom: 4px;
}
.hero-sub {
  font-size: 14px;
  color: #a07240;
}
/* 今日贴心推荐卡 */
.daily-tip {
  margin: 0 16px 14px;
  padding: 16px;
  background: #fff;
  border-radius: 14px;
  border-left: 5px solid #16a34a;
  box-shadow: 0 4px 16px rgba(22, 163, 74, 0.12);
}
.tip-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.tip-badge {
  font-size: 17px;
  font-weight: 700;
  color: #15803d;
}
.tip-stat {
  font-size: 15px;
  color: #4a6b56;
  background: #eef8f1;
  border-radius: 8px;
  padding: 6px 10px;
  margin-bottom: 10px;
}
.tip-content {
  font-size: 16px;
  line-height: 1.8;
  color: #333;
  margin-bottom: 12px;
}
.tip-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.quick-actions {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin: 0 16px 14px;
}
.tools-section {
  margin: 20px 16px;
}
.tools-title { margin: 0 0 6px; font-size: 21px; color: var(--van-text-color); }
.tools-description { margin: 0 0 16px; font-size: 16px; color: var(--van-text-color-2); line-height: 1.6; }
.tools-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.tool-card {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 112px;
  padding: 18px 14px;
  border: 1px solid var(--van-border-color);
  border-radius: 18px;
  background: var(--van-background-2, #fff);
  color: var(--van-text-color);
  font: inherit;
  text-align: left;
  cursor: pointer;
  -webkit-tap-highlight-color: transparent;
}
.tool-card:active { background: var(--van-active-color); }
.tool-card:focus-visible { outline: 3px solid var(--van-primary-color); outline-offset: 3px; }
.tool-icon { display: grid; place-items: center; width: 44px; height: 44px; flex-shrink: 0; border-radius: 14px; background: #edf8ef; color: var(--van-primary-color); font-size: 26px; }
.tool-copy { display: flex; flex: 1; min-width: 0; flex-direction: column; gap: 6px; }
.tool-title { font-size: 18px; font-weight: 600; line-height: 1.5; }
.tool-description { font-size: 15px; color: var(--van-text-color-2); line-height: 1.6; }
.tool-arrow { color: var(--van-text-color-3); flex-shrink: 0; }
:global(html.high-contrast) .tool-card { border: 2px solid #000; }
@media (max-width: 420px) {
  .tools-grid { grid-template-columns: 1fr; }
  .tool-card { min-height: 92px; padding: 16px; }
}
</style>
