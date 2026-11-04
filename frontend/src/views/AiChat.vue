<template>
  <div class="chat-page">
    <van-nav-bar title="🤖 AI 智能问答" />

    <!-- 聊天气泡列表 -->
    <div class="chat-list" ref="listRef">
      <div v-for="(m, i) in messages" :key="i" class="msg-row" :class="m.role">
        <div class="msg-avatar">{{ m.role === 'user' ? '🧓' : '🤖' }}</div>
        <div class="msg-bubble">
          <div v-if="m.role === 'ai' && replyLabel(m.source)" class="reply-source" :class="m.source">{{ replyLabel(m.source) }}</div>
          <p v-if="m.notice" class="reply-notice" role="status">{{ m.notice }}</p>
          <div class="msg-text">{{ m.content }}</div>
          <!-- 答案气泡旁：朗读按钮 -->
          <TtsPlayer v-if="m.role === 'ai' && !m.thinking" :text="m.content" />
        </div>
      </div>
    </div>

    <!-- 常用问题快捷按钮 -->
    <div class="quick-bar">
      <van-button
        v-for="q in quickQuestions"
        :key="q"
        size="small"
        round
        plain
        type="primary"
        @click="send(q)"
      >
        {{ q }}
      </van-button>
    </div>

    <!-- 语音输入提示 -->
    <van-overlay :show="listening" class="voice-overlay" @click="stopVoice">
      <div class="voice-box" @click.stop>
        <div class="voice-wave">🎤</div>
        <div class="voice-tip">正在听您说话，请说出问题…</div>
        <van-button round plain type="primary" @click="stopVoice">说完了</van-button>
      </div>
    </van-overlay>

    <!-- 输入框 + 语音 + 发送 -->
    <div class="chat-input-bar">
      <van-field
        v-model="input"
        placeholder="可以打字，也可以点话筒直接问"
        clearable
        :maxlength="MAX_QUESTION_LENGTH"
        @keyup.enter="send()"
      />
      <van-button
        class="voice-btn"
        :type="listening ? 'danger' : 'default'"
        size="large"
        icon="service-o"
        aria-label="语音输入"
        @click="toggleVoice"
      />
      <van-button class="send-btn" type="primary" size="large" :disabled="sending" @click="send()">{{ sending ? '回答中…' : '发送' }}</van-button>
    </div>

    <van-tabbar route :fixed="false" safe-area-inset-bottom>
      <van-tabbar-item replace to="/home" icon="home-o">首页</van-tabbar-item>
      <van-tabbar-item replace to="/courses" icon="bookmark-o">课程</van-tabbar-item>
      <van-tabbar-item replace to="/ai" icon="chat-o">AI问答</van-tabbar-item>
      <van-tabbar-item replace to="/mine" icon="user-o">我的</van-tabbar-item>
    </van-tabbar>
  </div>
</template>

<script setup>
import { nextTick, onBeforeUnmount, ref } from 'vue'
import { showToast } from 'vant'
import { aiChat } from '../api/ai'
import TtsPlayer from '../components/TtsPlayer.vue'
import { conversationContext, MAX_QUESTION_LENGTH, readChatReply, replyLabel } from '../utils/chatConversation.mjs'

const input = ref('')
const sending = ref(false)
const listRef = ref(null)
let chatRequest = null

// ---------- 语音输入（Web Speech API，Android WebView/Chrome 可用，不支持时降级提示） ----------
const listening = ref(false)
let recognition = null

const toggleVoice = () => {
  if (listening.value) {
    stopVoice()
    return
  }
  const SR = window.SpeechRecognition || window.webkitSpeechRecognition
  if (!SR) {
    showToast('当前设备不支持语音输入，请直接打字提问')
    return
  }
  recognition = new SR()
  recognition.lang = 'zh-CN'
  recognition.interimResults = true
  recognition.continuous = false
  recognition.maxAlternatives = 1

  const baseText = input.value
  recognition.onresult = (event) => {
    let text = ''
    for (let i = 0; i < event.results.length; i++) {
      text += event.results[i][0].transcript
    }
    input.value = (baseText ? baseText : '') + text
  }
  recognition.onerror = (e) => {
    listening.value = false
    if (e.error === 'not-allowed' || e.error === 'service-not-allowed') {
      showToast('请在系统设置中允许麦克风权限')
    } else if (e.error === 'no-speech') {
      showToast('没有听到声音，请再试一次')
    }
  }
  recognition.onend = () => {
    listening.value = false
  }
  try {
    recognition.start()
    listening.value = true
  } catch (e) {
    showToast('语音启动失败，请打字提问')
  }
}

const stopVoice = () => {
  if (recognition) {
    try {
      recognition.stop()
    } catch (e) { /* 忽略重复停止 */ }
  }
  listening.value = false
  // 识别完成且有内容时直接发送
  if (input.value.trim()) send()
}

onBeforeUnmount(() => {
  chatRequest?.abort()
  if (recognition) {
    try {
      recognition.abort()
    } catch (e) { /* 忽略 */ }
  }
})

const quickQuestions = [
  '怎么用手机挂号',
  '如何防范电信诈骗',
  '怎么用手机缴水电费',
  '手机字体怎么调大'
]

const messages = ref([
  { role: 'ai', content: '您好！我是您的智能学习助手，手机使用方面的问题都可以问我。', intro: true }
])

const scrollBottom = () => {
  nextTick(() => {
    const el = listRef.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

const send = async (text) => {
  const q = (text !== undefined ? text : input.value).trim()
  if (!q || sending.value) return
  if (q.length > MAX_QUESTION_LENGTH) { showToast('问题不能超过1000字，请分开提问'); return }
  const history = conversationContext(messages.value)
  sending.value = true
  input.value = ''
  messages.value.push({ role: 'user', content: q })
  messages.value.push({ role: 'ai', content: '正在思考…', thinking: true })
  const replyIndex = messages.value.length - 1
  const controller = new AbortController()
  chatRequest = controller
  scrollBottom()
  try {
    const res = await aiChat({ question: q, history }, { signal: controller.signal })
    if (controller.signal.aborted) return
    messages.value[replyIndex] = readChatReply(res.data)
  } catch (e) {
    if (controller.signal.aborted) return
    messages.value[replyIndex] = {
      role: 'ai',
      failed: true,
      content: '抱歉，AI 服务暂时不可用，请稍后再试。'
    }
  } finally {
    if (chatRequest === controller) chatRequest = null
    sending.value = false
    scrollBottom()
  }
}
</script>

<style scoped>
.chat-input-bar {
  display: flex;
  align-items: center;
  gap: 8px;
}
.voice-btn {
  flex-shrink: 0;
}
.send-btn {
  flex-shrink: 0;
}
.voice-overlay {
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2000;
}
.voice-box {
  background: #fff;
  border-radius: 20px;
  padding: 34px 42px;
  text-align: center;
}
.voice-wave {
  font-size: 64px;
  animation: pulse 1s ease-in-out infinite;
}
.voice-tip {
  font-size: 18px;
  color: #444;
  margin: 14px 0 22px;
}
@keyframes pulse {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.18); }
}
.reply-source {
  display: inline-block;
  font-size: 13px;
  font-weight: 600;
  color: #fff;
  background: linear-gradient(90deg, #2ea043, #22c55e);
  border-radius: 10px 10px 10px 2px;
  padding: 3px 10px;
  margin-bottom: 8px;
}
.reply-source.AI { background: #466d91; }
.reply-source.LOCAL { background: #65796a; }
.reply-notice { font-size: 15px; line-height: 1.6; color: #745320; margin: 0 0 8px; }
</style>
