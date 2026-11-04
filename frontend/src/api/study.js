import request from '../utils/request'

// 保存学习进度记录
export function saveStudyRecord(data) {
  return request.post('/study/record/save', data)
}

// 学习记录列表
export function getStudyRecords(params) {
  return request.get('/study/record/list', { params })
}

// 管理员查看指定用户学过哪些课程（课程汇总）
export function getUserStudyRecords(userId) {
  return request.get(`/study/record/admin/user/${userId}`)
}

// 管理员查看指定用户的学习行为明细（每次学习会话）
export function getUserStudyLogs(userId) {
  return request.get(`/study/record/admin/logs/${userId}`)
}

// 管理员查看指定课程被哪些用户学过（按用户聚合）
export function getCourseLearners(courseId) {
  return request.get(`/study/record/admin/course/${courseId}`)
}
