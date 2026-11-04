import request from '../utils/request'

// 用户列表（分页/搜索）
export function getUserList(params) {
  return request.get('/user/list', { params })
}

// 新增用户
export function addUser(data) {
  return request.post('/user/add', data)
}

// 更新用户（状态、资料、角色等）
export function updateUser(data) {
  return request.put('/user/update', data)
}

// 删除用户
export function deleteUser(id) {
  return request.delete(`/user/delete/${id}`)
}

// 重置密码
export function resetUserPwd(data) {
  return request.put('/user/resetPwd', data)
}
