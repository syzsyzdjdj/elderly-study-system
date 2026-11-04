import request from '../utils/request'

// 可兑换奖品列表
export function getPrizeList() {
  return request.get('/prize/list')
}

// 兑换奖品
export function exchangePrize(id) {
  return request.post(`/prize/exchange/${id}`)
}

// 我的兑换记录
export function getMyExchanges() {
  return request.get('/prize/my')
}

// 管理端：奖品列表
export function getAdminPrizeList() {
  return request.get('/prize/admin/list')
}

// 管理端：新增/修改奖品
export function savePrize(data) {
  return request.post('/prize/admin/save', data)
}

// 管理端：删除奖品
export function deletePrize(id) {
  return request.delete(`/prize/admin/delete/${id}`)
}

// 管理端：兑换明细分页
export function getExchangePage(params) {
  return request.get('/exchange/page', { params })
}

// 管理端：发放 / 取消兑换
export function handleExchange(data) {
  return request.post('/exchange/handle', data)
}
