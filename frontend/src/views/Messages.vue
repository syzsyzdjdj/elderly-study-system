<template>
  <div class="msg-page">
    <van-nav-bar title="消息通知" left-arrow @click-left="router.back()">
      <template #right>
        <span v-if="unreadCount > 0" class="read-all" @click="onReadAll">全部已读</span>
      </template>
    </van-nav-bar>

    <van-list
      v-model:loading="loading"
      :finished="finished"
      finished-text="没有更多消息了"
      @load="onLoadMore"
    >
      <van-cell-group v-for="m in list" :key="m.id" inset class="msg-group">
        <van-cell
          :class="{ unread: m.isRead === 0 }"
          center
          @click="onClick(m)"
        >
          <template #icon>
            <div class="msg-icon" :class="m.msgType?.toLowerCase()">{{ iconOf(m.msgType) }}</div>
          </template>
          <template #title>
            <div class="msg-title-row">
              <span class="msg-title">{{ m.title }}</span>
              <span v-if="m.isRead === 0" class="dot"></span>
            </div>
            <div class="msg-content">{{ m.content }}</div>
            <TtsPlayer :text="m.title + '。' + m.content" class="msg-tts" />
            <div class="msg-time">{{ fmtTime(m.createTime) }}</div>
          </template>
        </van-cell>
      </van-cell-group>
    </van-list>

    <van-empty v-if="finished && !list.length" description="暂无消息" image-size="100" />
    <div class="tabbar-holder"></div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { getMessages, getMessageSummary, markMessageRead, markAllMessagesRead } from '../api/message'
import TtsPlayer from '../components/TtsPlayer.vue'

const router = useRouter()

const list = ref([])
const page = ref(1)
const pageSize = 20
const loading = ref(false)
const finished = ref(false)
const unreadCount = ref(0)

const ICONS = { SYSTEM: '📢', REMINDER: '⏰', GOV: '🏛', EXCHANGE: '🎁' }
const iconOf = (t) => ICONS[t] || '🔔'

const fmtTime = (t) => (t ? String(t).replace('T', ' ').slice(0, 16) : '')

const onLoadMore = async () => {
  try {
    const res = await getMessages({ page: page.value, size: pageSize })
    const data = res.data || {}
    const rows = data.records || []
    list.value = list.value.concat(rows)
    page.value += 1
    if (list.value.length >= Number(data.total || 0)) finished.value = true
  } catch (e) {
    finished.value = true
  } finally {
    loading.value = false
  }
}

const onClick = async (m) => {
  if (m.isRead === 0) {
    try {
      await markMessageRead(m.id)
      m.isRead = 1
      unreadCount.value = Math.max(0, unreadCount.value - 1)
    } catch (e) { /* 拦截器已提示 */ }
  }
  // 消息正文较长时弹出完整内容
  showToast({ message: m.content || m.title, position: 'middle', duration: 2000 })
}

const onReadAll = async () => {
  try {
    await markAllMessagesRead()
    list.value.forEach((m) => (m.isRead = 1))
    unreadCount.value = 0
    showToast('已全部标记为已读')
  } catch (e) { /* 拦截器已提示 */ }
}

onMounted(async () => {
  try {
    const res = await getMessageSummary()
    unreadCount.value = res.data?.unreadCount ?? res.data?.unread ?? 0
  } catch (e) { /* 忽略汇总失败 */ }
})
</script>

<style scoped>
.read-all {
  font-size: 15px;
  color: #16a34a;
}
.msg-group {
  margin-top: 10px;
}
.msg-icon {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: #fff1e2;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  margin-right: 12px;
  flex-shrink: 0;
}
.msg-icon.system { background: #e8f0ff; }
.msg-icon.reminder { background: #fff1e2; }
.msg-icon.gov { background: #eaf8ee; }
.msg-icon.exchange { background: #fdeef3; }
.msg-title-row {
  display: flex;
  align-items: center;
  gap: 6px;
}
.msg-title {
  font-size: 17px;
  font-weight: 700;
  color: #1a1a1a;
}
.unread .msg-title {
  color: #16a34a;
}
.dot {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: #e64531;
  display: inline-block;
}
.msg-content {
  font-size: 15px;
  color: #666;
  margin: 4px 0;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.msg-tts {
  margin: 2px 0;
}
.msg-time {
  font-size: 13px;
  color: #aaa;
}
</style>
