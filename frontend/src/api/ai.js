import request from '../utils/request'

// AI 智能问答
export function aiChat(data, config = {}) {
  // Backend allows 15s to connect and 60s to read; do not abandon it at the 20s global default.
  return request.post('/ai/chat', data, { timeout: 90000, ...config })
}
