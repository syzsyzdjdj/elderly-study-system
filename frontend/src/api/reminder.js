import request from '../utils/request'

export function listReminders() {
  return request.get('/reminder/list')
}

export function createReminder(data) {
  return request.post('/reminder', data)
}

export function updateReminder(data) {
  return request.put('/reminder', data)
}

export function deleteReminder(id) {
  return request.delete(`/reminder/${id}`)
}

export function toggleReminder(id) {
  return request.post(`/reminder/${id}/toggle`)
}

/** 获取到期提醒（前端 poll 用） */
export function getDueReminders() {
  return request.get('/reminder/due')
}

/** 标记已触发 */
export function markReminderTriggered(id) {
  return request.post(`/reminder/${id}/mark-triggered`)
}

// ============ 提醒模板（管理员配置，老人端一键选用） ============

/** 老人端：启用中的提醒模板 */
export function listEnabledTemplates() {
  return request.get('/reminder-template/enabled')
}

/** 老人端：基于模板创建自己的提醒 */
export function useReminderTemplate(id) {
  return request.post(`/reminder-template/use/${id}`)
}

/** 管理端：全部提醒模板（含停用） */
export function adminListTemplates() {
  return request.get('/reminder-template/admin/list')
}

/** 管理端：新增/修改提醒模板 */
export function adminSaveTemplate(data) {
  return request.post('/reminder-template/admin/save', data)
}

/** 管理端：删除提醒模板 */
export function adminDeleteTemplate(id) {
  return request.delete(`/reminder-template/admin/${id}`)
}
