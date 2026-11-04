import request from '../utils/request'

// 全部系统参数（仅管理员）
export function getConfigList() {
  return request.get('/config/list')
}

// 批量保存系统参数
export function saveConfigs(data) {
  return request.post('/config/save', data)
}
