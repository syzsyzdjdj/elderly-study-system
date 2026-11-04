import request from '../utils/request'

// 课程列表（分页/分类筛选）
export function getCourseList(params) {
  return request.get('/course/list', { params })
}

// 混合推荐课程，附 recommendationSources 与 recommendationReason
export function getRecommendCourses(params) {
  return request.get('/course/recommend', { params })
}

// 课程详情
export function getCourseDetail(id) {
  return request.get(`/course/${id}`)
}

// 新增课程
export function addCourse(data) {
  return request.post('/course/add', data)
}

// 更新课程（含上下架）
export function updateCourse(data) {
  return request.put('/course/update', data)
}

// 删除课程；已有学习记录的课程由后端拒绝删除，可改为下架。
export function deleteCourse(id) {
  return request.delete(`/course/delete/${id}`)
}
