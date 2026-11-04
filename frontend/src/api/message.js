import request from '../utils/request'

// 我的消息分页
export function getMessages(params) {
  return request.get('/message/list', { params })
}

// 未读数等汇总
export function getMessageSummary() {
  return request.get('/message/summary')
}

// 标记单条已读
export function markMessageRead(id) {
  return request.post(`/message/read/${id}`)
}

// 全部已读
export function markAllMessagesRead() {
  return request.post('/message/read-all')
}
