<template>
  <div class="admin-page">
    <h3 class="page-title">🔊 TTS 音频资源</h3>
    <p class="page-tip">课程朗读、简报收听生成的音频缓存文件（uploads/tts），可试听与删除。</p>
    <div class="prewarm-card">
      <div class="prewarm-title">课程预生成音频</div>
      <p class="prewarm-desc">先导出课程朗读清单，在电脑生成音频，再上传并关联对应分句。课程文字修改后需重新导出生成；旧音频不会用于新文字。预生成音频使用统一普通话音色，语速由播放器调整。</p>
      <van-button size="small" plain @click="exportManifest">导出课程朗读清单</van-button>
      <van-field v-model="preparedText" type="textarea" label="对应分句" placeholder="粘贴清单中的完整 text 文字" />
      <input type="file" accept=".wav,.mp3,.ogg" @change="uploadPrepared" :disabled="preparedUploading" />
      <p v-if="preparedResult">{{ preparedResult }}</p>
    </div>

    <!-- 语音预热：把老人端全部朗读内容提前合成，老人一点即播 -->
    <div class="prewarm-card">
      <div class="prewarm-title">🚀 老人端语音一键预热</div>
      <p v-if="progress.generationAvailable === false" class="prewarm-desc" role="status">
        {{ progress.unavailableReason }}
      </p>
      <p v-else class="prewarm-desc">
        提前合成老人界面所有“听全文”内容：全部课程正文与简介、政务政策、消息通知、陪伴话术、积分奖品。
        按所选音色预生成<b>慢速 / 标准 / 快速</b>三档的分段音频。预热成功且参数一致时可直接使用缓存；发布新课程或修改音色参数后请重新预热。
      </p>
      <van-radio-group v-if="progress.generationAvailable === true" v-model="voiceMode" direction="horizontal" :disabled="progress.running" class="prewarm-modes">
        <van-radio name="default">系统默认音色（推荐）</van-radio>
        <van-radio name="local">全部本地音色（6 种）</van-radio>
        <van-radio name="all">全部音色（在线含三档语速，耗时长）</van-radio>
      </van-radio-group>
      <div class="prewarm-actions">
        <van-button
          type="primary"
          size="small"
          :loading="progress.running"
          :disabled="progress.generationAvailable !== true"
          loading-text="预热进行中…"
          @click="startPrewarm"
        >{{ progress.generationAvailable === false ? '未启用语音合成' : progress.running ? '预热进行中…' : '开始预热' }}</van-button>
        <van-tag v-if="progress.running" type="warning" plain>
          {{ progress.done }}/{{ progress.total }}
          <template v-if="progress.failed">，失败 {{ progress.failed }}</template>
        </van-tag>
        <van-tag v-else-if="progress.finishedAt" :type="progress.failed ? 'danger' : 'success'" plain>
          上次已处理 {{ progress.done }} 项，成功 {{ Math.max(0, progress.done - progress.failed) }} 项
          <template v-if="progress.failed">，失败 {{ progress.failed }}</template>
        </van-tag>
      </div>
      <van-progress
        v-if="progress.total"
        :percentage="progress.total ? Math.round((progress.done / progress.total) * 100) : 0"
        color="#f27a1a"
        stroke-width="8"
        :show-pivot="true"
      />
      <div v-if="progress.running && progress.currentLabel" class="prewarm-current">
        正在合成：{{ progress.currentLabel }}
      </div>
      <div v-if="progress.errors && progress.errors.length" class="prewarm-errors">
        <div v-for="(e, i) in progress.errors.slice(0, 5)" :key="i" class="err-line">⚠️ {{ e }}</div>
      </div>
    </div>

    <div class="upload-bar">
      <van-button size="small" type="primary" icon="plus" :loading="uploading" loading-text="上传中…" @click="pickFile">上传音频素材</van-button>
      <span class="upload-tip">支持 mp3 / wav，≤20MB</span>
      <input ref="fileInput" type="file" accept=".mp3,.wav,audio/mpeg,audio/wav" style="display:none" @change="onFileChange" />
    </div>

    <div v-for="f in list" :key="f.filename" class="admin-course-card">
      <div class="course-line">
        <span class="c-title text-file">🎵 {{ f.filename }}</span>
      </div>
      <div class="c-line">大小：{{ formatSize(f.size) }}　生成时间：{{ fmt(f.modifiedTime) }}</div>
      <audio class="audio-preview" :src="mediaUrl(f.url)" controls preload="none"></audio>
      <div class="user-actions">
        <van-button size="small" type="danger" plain @click="remove(f)">删除音频</van-button>
      </div>
    </div>

    <van-empty v-if="loaded && !list.length" description="暂无音频文件" image-size="90" />

    <van-pagination
      v-model="page"
      :total-items="total"
      :items-per-page="20"
      @change="load"
    />
  </div>
</template>

<script setup>
import { onMounted, onBeforeUnmount, ref } from 'vue'
import { showConfirmDialog, showToast } from 'vant'
import { getTtsAudioPage, deleteTtsAudio, uploadTtsAudio } from '../../api/ttsAudio'
import { startTtsPrewarm, getTtsPrewarmProgress } from '../../api/tts'
import { mediaUrl } from '../../utils/api-base'
import request from '../../utils/request'

const preparedText = ref('')
const preparedUploading = ref(false)
const preparedResult = ref('')
const exportManifest = async () => {
  const res = await request.get('/tts/prewarm/course-manifest')
  const url = URL.createObjectURL(new Blob([JSON.stringify(res.data, null, 2)], { type: 'application/json' }))
  const link = document.createElement('a')
  link.href = url; link.download = 'course-audio-manifest.json'; link.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
const uploadPrepared = async event => {
  const file = event.target.files?.[0]
  if (!file) return
  if (!preparedText.value.trim()) { showToast('请先填写对应分句'); event.target.value = ''; return }
  preparedUploading.value = true
  preparedResult.value = ''
  try {
    const data = new FormData()
    data.append('text', preparedText.value); data.append('file', file)
    const res = await request.post('/tts/audio/prepared', data)
    preparedResult.value = '已关联，课程朗读将自动使用此音频。'
    showToast('音频关联成功')
  } finally { preparedUploading.value = false; event.target.value = '' }
}

const list = ref([])
const total = ref(0)
const page = ref(1)
const loaded = ref(false)

// ===== 语音预热 =====
const voiceMode = ref('default')
const progress = ref({ running: false, total: 0, done: 0, failed: 0, currentLabel: '', errors: [] })
let pollTimer = null
let observedRunning = false

const stopPolling = () => {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

const refreshProgress = async () => {
  try {
    const res = await getTtsPrewarmProgress()
    progress.value = res.data || progress.value
    if (progress.value.running) {
      observedRunning = true
      if (!pollTimer) pollTimer = setInterval(refreshProgress, 3000)
    } else {
      stopPolling()
      // 仅在本次打开页面观察到任务结束时提示，避免每次进页面都弹
      if (observedRunning && progress.value.finishedAt) {
        observedRunning = false
        load()
        showToast(progress.value.failed
          ? `已处理 ${progress.value.done} 项，失败 ${progress.value.failed} 项，请查看具体原因`
          : '所选范围预热完成，可播放与预热参数一致的缓存音频')
      }
    }
  } catch (e) { /* 拦截器已提示 */ }
}

const startPrewarm = async () => {
  if (progress.value.generationAvailable !== true) {
    showToast(progress.value.unavailableReason || '正在核对语音合成配置，请稍后重试')
    return
  }
  const tip = voiceMode.value === 'all'
    ? '将按全部已定义音色及慢速、标准、快速三档合成全部内容。在线音色需联网，可能需要数小时。确定开始？'
    : voiceMode.value === 'local'
      ? '将按 6 种本地普通话音色及慢速、标准、快速三档处理全部内容，服务可能复用相同音频，本地推理较慢。确定开始？'
      : '将按系统默认音色提前合成老人端全部朗读内容。确定开始？'
  try {
    await showConfirmDialog({ title: '开始语音预热', message: tip })
  } catch { return }
  try {
    await startTtsPrewarm(voiceMode.value)
    showToast('预热已开始，可离开本页，后台自动完成')
    refreshProgress()
  } catch (e) { /* 拦截器已提示 */ }
}

const fmt = (t) => (t ? String(t).replace('T', ' ').slice(0, 19) : '-')
const formatSize = (n) => {
  const size = Number(n || 0)
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(2)} MB`
}

const load = async () => {
  try {
    const res = await getTtsAudioPage({ page: page.value, size: 20 })
    list.value = res.data?.records || []
    total.value = Number(res.data?.total || 0)
  } catch (e) { /* 拦截器已提示 */ }
  loaded.value = true
}

const remove = async (f) => {
  try {
    await showConfirmDialog({ title: '删除音频', message: `确定删除 ${f.filename} 吗？删除后相关朗读需重新生成。` })
  } catch { return }
  try {
    await deleteTtsAudio(f.filename)
    showToast('删除成功')
    load()
  } catch (e) { /* 拦截器已提示 */ }
}

// ===== 上传音频素材 =====
const fileInput = ref(null)
const uploading = ref(false)

const pickFile = () => fileInput.value?.click()

const onFileChange = async (e) => {
  const file = e.target.files?.[0]
  e.target.value = ''
  if (!file) return
  if (!/\.(mp3|wav)$/i.test(file.name)) {
    showToast('仅支持 mp3 / wav 格式')
    return
  }
  uploading.value = true
  try {
    await uploadTtsAudio(file)
    showToast('上传成功')
    page.value = 1
    load()
  } catch (err) { /* 拦截器已提示 */ } finally {
    uploading.value = false
  }
}

onMounted(() => {
  load()
  refreshProgress()
})

onBeforeUnmount(stopPolling)
</script>

<style scoped>
.page-tip {
  color: #999;
  font-size: 14px;
  margin: 0 0 12px;
}
.prewarm-card {
  background: linear-gradient(135deg, #fff7ef, #ffedd9);
  border: 1px solid #ffd9ad;
  border-radius: 14px;
  padding: 14px 16px;
  margin-bottom: 14px;
}
.prewarm-title {
  font-size: 16px;
  font-weight: 700;
  color: #8a4b0d;
  margin-bottom: 6px;
}
.prewarm-desc {
  font-size: 13px;
  color: #9a6a33;
  line-height: 1.6;
  margin: 0 0 10px;
}
.prewarm-modes {
  margin-bottom: 10px;
  row-gap: 6px;
}
.prewarm-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}
.prewarm-current {
  margin-top: 8px;
  font-size: 12px;
  color: #b06a1c;
  word-break: break-all;
}
.prewarm-errors {
  margin-top: 8px;
}
.err-line {
  font-size: 12px;
  color: #c4561e;
  line-height: 1.6;
  word-break: break-all;
}
.text-file {
  font-size: 14px;
  word-break: break-all;
}
.upload-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 4px 0 14px;
}
.upload-tip {
  font-size: 12px;
  color: #999;
}
.audio-preview {
  width: 100%;
  height: 38px;
  margin: 8px 0;
}
</style>
