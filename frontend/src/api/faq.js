import request from '../utils/request'

// 管理端：审核问答库分页
export function getFaqPage(params) {
  return request.get('/faq/admin/page', { params })
}

// 管理端：新增/修改审核问答
export function saveFaq(data) {
  return request.post('/faq/admin/save', data)
}

// 管理端：删除审核问答
export function deleteFaq(id) {
  return request.delete(`/faq/admin/delete/${id}`)
}
