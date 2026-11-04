import request from '../utils/request'

// 登录
export function loginApi(data) {
  return request.post('/login', data)
}

// 注册
export function registerApi(data) {
  return request.post('/register', data)
}

// 退出登录
export function logoutApi() {
  return request.post('/logout')
}
