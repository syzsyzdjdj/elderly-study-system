import request from '../utils/request'

// 积分首页汇总
export function getPointSummary() {
  return request.get('/point/summary')
}

// 每日打卡
export function checkin() {
  return request.post('/point/checkin')
}

// 积分明细分页
export function getPointRecords(params) {
  return request.get('/point/records', { params })
}

// 积分规则
export function getPointRules() {
  return request.get('/point/rules')
}
