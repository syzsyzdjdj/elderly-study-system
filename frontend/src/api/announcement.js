import request from '../utils/request'

// 老人端：已发布公告
export function getPublishedAnnouncements(type) {
  return request.get('/announcement/list', { params: type ? { type } : {} })
}

// 管理端：公告分页
export function getAnnouncementPage(params) {
  return request.get('/announcement/admin/page', { params })
}

// 管理端：新增/修改公告
export function saveAnnouncement(data) {
  return request.post('/announcement/admin/save', data)
}

// 管理端：删除公告
export function deleteAnnouncement(id) {
  return request.delete(`/announcement/admin/delete/${id}`)
}
