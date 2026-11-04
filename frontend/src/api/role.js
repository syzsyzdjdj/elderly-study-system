import request from '../utils/request'

export function getMyMenus() {
  return request.get('/menu/mine')
}

// 角色列表
export function getRoleList(params) {
  return request.get('/role/list', { params })
}

// 菜单列表（权限树）
export function getMenuList(params) {
  return request.get('/menu/list', { params })
}

// 给角色分配菜单
export function assignMenu(data) {
  return request.put('/role/assignMenu', data)
}
