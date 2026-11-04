import request from '../utils/request'

// 文字转语音（返回音频 URL）
// 语音生成较慢（本地 ChatTTS 冷启动约 40 秒），单独放宽超时
// config 可传 { silent: true } 静默调用（不弹错误提示，用于页面加载时预生成）
export function ttsGenerate(data, config) {
  return request.post('/tts/generate', data, { timeout: 300000, ...config })
}

// 只规划分句，不启动推理；播放端按需生成并提前准备下一段。
export function ttsSegments(text, config) {
  return request.post('/tts/segments', { text }, config)
}

// 获取语音包列表（普通话 + 方言）
export function getTtsVoices() {
  return request.get('/tts/voices')
}

// 设置当前用户的朗读音色（个人中心语音包切换，任何登录用户可用）
export function updateTtsVoice(data) {
  return request.put('/tts/voice', data)
}

// 【管理端】一键预热老人端全部朗读语音（课程/政策/消息/话术/奖品）
// mode: default=系统默认音色 local=全部本地音色 all=全部音色(含在线方言)
export function startTtsPrewarm(mode = 'default') {
  return request.post('/tts/prewarm', { mode }, { timeout: 15000 })
}

// 【管理端】查询预热进度
export function getTtsPrewarmProgress() {
  return request.get('/tts/prewarm/progress')
}
