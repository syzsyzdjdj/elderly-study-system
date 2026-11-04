import request from '../utils/request'

// 操作日志分页
export function getOpLogPage(params) {
  return request.get('/log/page', { params })
}
