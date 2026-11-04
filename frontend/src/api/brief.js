import request from '../utils/request'

// 当前登录用户近七天的个人学习周报
export function getLatestBrief() {
  return request.get('/brief/latest')
}
