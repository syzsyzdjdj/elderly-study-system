import request from '../utils/request'

// TTS 音频资源分页
export function getTtsAudioPage(params) {
  return request.get('/tts/audio/page', { params })
}

// 删除 TTS 音频
export function deleteTtsAudio(filename) {
  return request.delete(`/tts/audio/${filename}`)
}

// 上传音频素材（mp3/wav）
export function uploadTtsAudio(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/tts/audio/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}
