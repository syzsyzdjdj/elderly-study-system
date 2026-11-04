import request from '../utils/request'

// 我的资料
export function getProfile() {
  return request.get('/profile')
}

// 更新资料（昵称/手机/头像/常住地区/字号/高对比度/音色）
export function updateProfile(data) {
  return request.put('/profile', data)
}

// 修改密码（校验旧密码）
export function changePassword(data) {
  return request.put('/profile/password', data)
}
