import request from '../utils/request'

// 老人端：今日贴心推荐（基于个人学习数据动态生成）
export function getDailyTip() {
  return request.get('/companion/daily-tip')
}

// 老人端：全部启用陪伴话术
export function getCompanionList() {
  return request.get('/companion/list')
}

// 老人端：按场景取话术
export function getCompanionByScene(scene) {
  return request.get('/companion/scene', { params: { scene } })
}

// 管理端：全部话术
export function getAdminCompanionList(scene) {
  return request.get('/companion/admin/list', { params: scene ? { scene } : {} })
}

// 管理端：新增/修改话术
export function saveCompanion(data) {
  return request.post('/companion/admin/save', data)
}

// 管理端：删除话术
export function deleteCompanion(id) {
  return request.delete(`/companion/admin/${id}`)
}
