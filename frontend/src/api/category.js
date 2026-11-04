import request from '../utils/request'

// 分类列表（顶级 + 子级）
export function getCategoryList(params) {
  return request.get('/category/list', { params })
}

// 新增分类
export function addCategory(data) {
  return request.post('/category/add', data)
}

// 更新分类
export function updateCategory(data) {
  return request.put('/category/update', data)
}

// 删除分类
export function deleteCategory(id) {
  return request.delete(`/category/${id}`)
}

// 一键自动分类（基于课程标题/简介关键词匹配）
export function autoCategorize() {
  return request.post('/category/auto-categorize')
}
