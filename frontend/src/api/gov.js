import request from '../utils/request'

// 老人端：政务首页聚合（全国通用 + 本地专属）
export function getGovHome() {
  return request.get('/gov/home')
}

// 管理端：办事入口列表
export function getAdminGovServices(scope) {
  return request.get('/gov/admin/service/list', { params: scope ? { scope } : {} })
}

// 管理端：新增/修改办事入口
export function saveGovService(data) {
  return request.post('/gov/admin/service/save', data)
}

// 管理端：删除办事入口
export function deleteGovService(id) {
  return request.delete(`/gov/admin/service/${id}`)
}

// 管理端：政策列表
export function getAdminGovPolicies(params) {
  return request.get('/gov/admin/policy/list', { params })
}

// 管理端：新增/修改政策
export function saveGovPolicy(data) {
  return request.post('/gov/admin/policy/save', data)
}

// 管理端：删除政策
export function deleteGovPolicy(id) {
  return request.delete(`/gov/admin/policy/${id}`)
}
