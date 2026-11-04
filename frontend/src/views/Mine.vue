<template>
  <div class="mine-page">
    <!-- 用户信息卡 -->
    <div class="mine-user-card">
      <div class="mine-avatar">
        <img v-if="store.userInfo?.avatar" :src="mediaUrl(store.userInfo.avatar)" alt="头像" />
        <span v-else>👵</span>
      </div>
      <div class="mine-meta">
        <div class="mine-nick">{{ store.userInfo?.nickname || store.userInfo?.username || '老年学员' }}</div>
        <div class="mine-region" v-if="regionText">📍 {{ regionText }}</div>
        <div class="mine-points">🏅 积分：{{ points }}</div>
      </div>
      <van-button size="small" round plain class="edit-btn" @click="openEdit">编辑资料</van-button>
    </div>

    <!-- 我的服务 -->
    <div class="section">
      <h3 class="section-title">🧡 我的服务</h3>
      <van-grid :column-num="4" :border="false">
        <van-grid-item icon="like-o" text="我的收藏" @click="router.push('/favorites')" />
        <van-grid-item icon="cluster-o" text="政务服务" @click="router.push('/gov')" />
        <van-grid-item icon="gold-coin-o" text="积分中心" @click="router.push('/points')" />
        <van-grid-item icon="gift-o" text="福利兑换" @click="router.push('/prizes')" />
        <van-grid-item icon="newspaper-o" text="学习简报" @click="router.push('/brief')" />
        <van-grid-item icon="friends-o" text="AI陪伴" @click="router.push('/companion')" />
        <van-grid-item icon="bell" text="消息通知" :badge="unread || ''" @click="router.push('/messages')" />
        <van-grid-item icon="clock-o" text="定时提醒" @click="router.push('/reminder')" />
      </van-grid>
    </div>

    <!-- 学习记录 -->
    <div class="section">
      <h3 class="section-title">📖 我的学习记录</h3>
      <van-empty v-if="loaded && !records.length" description="还没有学习记录，快去学两课吧" image-size="80" />
      <van-cell
        v-for="r in records"
        :key="r.id || r.courseId"
        :title="r.courseName || r.courseTitle || `课程 ${r.courseId}`"
        :label="`学习 ${formatTime(r.studyTime)}`"
        is-link
        @click="router.push(`/course/${r.courseId}`)"
      >
        <template #value>
          <van-progress :percentage="Number(r.progress || 0)" stroke-width="8" :show-pivot="false" />
        </template>
      </van-cell>
    </div>

    <!-- 设置 -->
    <div class="section">
      <h3 class="section-title">⚙️ 设置</h3>
      <van-cell title="🔠 显示设置" label="字体大小、高对比度、常住地区、修改密码" is-link to="/display" />
      <van-cell title="下载 Android App" label="安装到手机，随时打开学堂" is-link url="/download.html" />
      <van-cell :title="`🔊 语音包（当前：${currentVoiceName}）`" is-link @click="openVoices" />
    </div>

    <!-- 退出登录 -->
    <div class="section">
      <van-button block size="large" type="danger" plain @click="onLogout">退出登录</van-button>
    </div>

    <!-- 语音包选择弹窗 -->
    <van-popup v-model:show="showVoices" position="bottom" round @closed="voicePreview.stop()">
      <div class="popup-body voice-popup">
        <h3 class="page-title">选择朗读音色</h3>
        <p v-if="previewMessage" role="status">{{ previewMessage }}</p>
        <div v-for="pack in voicePacks" :key="pack.name" class="voice-pack">
          <div class="voice-pack-title">{{ pack.name }}</div>
          <div
            v-for="v in pack.voices"
            :key="v.key"
            class="voice-row"
            :class="{ on: currentVoice === v.key }"
            @click="chooseVoice(v)"
          >
            <span class="voice-radio"><span v-if="currentVoice === v.key"></span></span>
            <span class="voice-name">{{ v.name }}</span>
            <van-button
              size="mini"
              round
              plain
              class="voice-preview-btn"
              :loading="previewing === v.key"
              @click.stop="previewVoice(v)"
            >试听</van-button>
          </div>
        </div>
        <div class="form-actions">
          <van-button block type="primary" size="large" @click="showVoices = false">完成</van-button>
        </div>
      </div>
    </van-popup>

    <!-- 编辑个人信息弹窗 -->
    <van-popup v-model:show="showEdit" position="bottom" round>
      <div class="popup-body">
        <h3 class="page-title">编辑个人信息</h3>
        <van-field v-model="editForm.nickname" label="昵称" placeholder="请输入昵称" />
        <van-field v-model="editForm.phone" label="手机号" type="tel" placeholder="请输入手机号" />
        <van-field v-model="editForm.avatar" label="头像地址" placeholder="请输入图片地址（可留空）" />
        <van-field
          v-model="editRegionText"
          is-link
          readonly
          label="常住地区"
          placeholder="点击选择省 / 市 / 区"
          @click="showArea = true"
        />
        <div class="form-actions">
          <van-button block type="primary" size="large" :loading="saving" @click="saveInfo">保存</van-button>
        </div>
      </div>
    </van-popup>

    <!-- 地区选择 -->
    <van-popup v-model:show="showArea" position="bottom" round teleport="body">
      <van-area
        :area-list="areaList"
        title="选择常住地区"
        @confirm="onAreaConfirm"
        @cancel="showArea = false"
      />
    </van-popup>

    <div class="tabbar-holder"></div>

    <van-tabbar route>
      <van-tabbar-item replace to="/home" icon="home-o">首页</van-tabbar-item>
      <van-tabbar-item replace to="/courses" icon="bookmark-o">课程</van-tabbar-item>
      <van-tabbar-item replace to="/ai" icon="chat-o">AI问答</van-tabbar-item>
      <van-tabbar-item replace to="/mine" icon="user-o">我的</van-tabbar-item>
    </van-tabbar>
  </div>
</template>

<script setup>
import { computed, onMounted, onBeforeUnmount, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showToast } from 'vant'
import { areaList } from '@vant/area-data'
import { logoutApi } from '../api/auth'
import { updateProfile } from '../api/profile'
import { getStudyRecords } from '../api/study'
import { getTtsVoices, updateTtsVoice } from '../api/tts'
import { getPointSummary } from '../api/point'
import { getMessageSummary } from '../api/message'
import { useUserStore } from '../store/user'
import { mediaUrl } from '../utils/api-base'
import { nativeSpeech } from '../utils/nativeSpeech.mjs'
import { createVoicePreview } from '../utils/voicePreview.mjs'

const router = useRouter()
const store = useUserStore()

const records = ref([])
const loaded = ref(false)
const saving = ref(false)
const points = ref(store.userInfo?.points || 0)
const unread = ref(0)
const showEdit = ref(false)
const showArea = ref(false)
const editForm = reactive({ nickname: '', phone: '', avatar: '' })
const editRegion = reactive({ province: '', city: '', district: '' })

const regionText = computed(() =>
  [store.userInfo?.province, store.userInfo?.city, store.userInfo?.district]
    .filter(Boolean)
    .join(' / ')
)
const editRegionText = computed(() =>
  [editRegion.province, editRegion.city, editRegion.district].filter(Boolean).join(' / ')
)

const onAreaConfirm = ({ selectedOptions }) => {
  const names = (selectedOptions || []).map((o) => o?.text).filter(Boolean)
  editRegion.province = names[0] || ''
  editRegion.city = names[1] || ''
  editRegion.district = names[2] || ''
  showArea.value = false
}

// ---- 语音包 ----
const voicePacks = ref([])
const showVoices = ref(false)
const previewing = ref('')
const previewAudio = new Audio()
const previewMessage = ref('')
const voiceUnavailableReason = ref('')
const voicePreview = createVoicePreview({
  audio: previewAudio,
  nativeSpeech,
  synthesis: window.speechSynthesis,
  Utterance: window.SpeechSynthesisUtterance,
  toUrl: mediaUrl,
  getUnavailableReason: () => voiceUnavailableReason.value,
  onState: key => { previewing.value = key },
  onMessage: message => { previewMessage.value = message }
})
onBeforeUnmount(() => voicePreview.stop())
const currentVoice = computed(() => store.userInfo?.voice || 'default')
const currentVoiceName = computed(() => {
  for (const pack of voicePacks.value) {
    const hit = pack.voices?.find((v) => v.key === currentVoice.value)
    if (hit) return hit.name
  }
  return '默认音色'
})

const formatTime = (s) => {
  const sec = Number(s || 0)
  if (sec < 60) return `${sec} 秒`
  const min = Math.floor(sec / 60)
  const left = sec % 60
  return left ? `${min} 分 ${left} 秒` : `${min} 分钟`
}

const openEdit = () => {
  const info = store.userInfo || {}
  editForm.nickname = info.nickname || ''
  editForm.phone = info.phone || ''
  editForm.avatar = info.avatar || ''
  editRegion.province = info.province || ''
  editRegion.city = info.city || ''
  editRegion.district = info.district || ''
  showEdit.value = true
}

// 使用老人自己的 /api/profile 更新（/api/user/update 仅管理员可用）
const saveInfo = async () => {
  saving.value = true
  try {
    const payload = { ...editForm }
    if (editRegion.province) Object.assign(payload, { ...editRegion })
    const res = await updateProfile(payload)
    const returned = res.data && typeof res.data === 'object' ? res.data : null
    store.setUserInfo(returned || { ...(store.userInfo || {}), ...payload })
    showToast('保存成功')
    showEdit.value = false
  } catch (e) {
    // 拦截器已提示
  } finally {
    saving.value = false
  }
}

const openVoices = () => {
  previewMessage.value = voiceUnavailableReason.value
  showVoices.value = true
}

/** 选择音色并保存到个人资料（全局生效，任何登录用户可改自己的音色） */
const chooseVoice = async (v) => {
  try {
    await updateTtsVoice({ voice: v.key })
    store.setUserInfo({ ...(store.userInfo || {}), voice: v.key })
    showToast(`已切换音色：${v.name}`)
  } catch (e) {
    // 拦截器已提示
  }
}

const previewVoice = (v) => {
  previewMessage.value = ''
  return voicePreview.play(v)
}

const onLogout = async () => {
  try {
    await showConfirmDialog({ title: '退出登录', message: '确定要退出登录吗？' })
  } catch (e) {
    return // 用户取消
  }
  try {
    await logoutApi()
  } catch (e) {
    // 即使接口失败也清除本地登录态
  }
  store.clearUser()
  router.replace('/login')
}

onMounted(async () => {
  try {
    const res = await getStudyRecords({ page: 1, pageSize: 50 })
    const data = res.data || {}
    records.value = data.list || data.records || (Array.isArray(data) ? data : [])
  } catch (e) {
    // 拦截器已提示
  }
  loaded.value = true
  // 积分与未读消息
  try {
    const pres = await getPointSummary()
    points.value = pres.data?.totalPoints ?? 0
  } catch (e) { /* 忽略 */ }
  try {
    const mres = await getMessageSummary()
    unread.value = mres.data?.unread ?? 0
  } catch (e) { /* 忽略 */ }
  // 语音包列表
  try {
    const vres = await getTtsVoices()
    voicePacks.value = (vres.data && vres.data.packs) || []
    voiceUnavailableReason.value = vres.data?.unavailableReason || ''
  } catch (e) {
    // 拦截器已提示
  }
})
</script>

<style scoped>
.edit-btn {
  flex-shrink: 0;
  background: rgba(255, 255, 255, 0.2);
  border-color: #fff;
  color: #fff;
}
.mine-region {
  font-size: 13px;
  opacity: 0.92;
  margin: 2px 0;
}

/* 语音包弹窗 */
.voice-popup {
  max-height: 70vh;
  overflow-y: auto;
}

.voice-pack-title {
  margin: 12px 0 6px;
  font-weight: bold;
  font-size: 16px;
  color: #666;
}

.voice-row {
  display: flex;
  align-items: center;
  padding: 12px 4px;
  font-size: 18px;
  border-bottom: 1px solid #f2f2f2;
  cursor: pointer;
}

.voice-row.on .voice-name {
  color: #16a34a;
  font-weight: bold;
}

.voice-radio {
  width: 20px;
  height: 20px;
  margin-right: 12px;
  border: 2px solid #c8c9cc;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.voice-row.on .voice-radio {
  border-color: #16a34a;
}

.voice-row.on .voice-radio span {
  width: 10px;
  height: 10px;
  background: #16a34a;
  border-radius: 50%;
}

.voice-name {
  flex: 1;
}

.voice-preview-btn {
  flex-shrink: 0;
}
</style>
