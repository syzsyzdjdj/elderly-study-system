import request from '../utils/request'

// 我的收藏课程
export function getFavorites() {
  return request.get('/favorite/list')
}

// 收藏课程
export function addFavorite(courseId) {
  return request.post(`/favorite/add/${courseId}`)
}

// 取消收藏
export function removeFavorite(courseId) {
  return request.delete(`/favorite/${courseId}`)
}

// 是否已收藏
export function checkFavorite(courseId) {
  return request.get(`/favorite/check/${courseId}`)
}
