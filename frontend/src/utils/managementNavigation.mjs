import definitions from '../../../shared/admin-navigation.json' with { type: 'json' }

export function isManager(role) {
  return role === 'admin' || role === 'course_manager'
}

export function managementMenus(role, serverMenus) {
  if (!isManager(role) || !Array.isArray(serverMenus)) return []
  const granted = new Set(serverMenus.map(menu => menu.path))
  return definitions.filter(menu => menu.roles.includes(role) && granted.has(menu.path))
}

export function managementDestination(path, menus) {
  return menus.some(menu => menu.path === path) ? path : menus[0]?.path || '/home'
}

export function assignableMenus(role) {
  return role === 'course_manager'
    ? definitions.filter(menu => menu.roles.includes(role) && menu.path !== '/admin/profile') : []
}

export async function managementGuard(to, store, fetchMenus) {
  // 登录/注册页放行
  if (to.path === '/login' || to.path === '/register') return true

  // 未登录 → 登录页
  if (!store.token) return { path: '/login' }

  if (to.path.startsWith('/admin') && !isManager(store.roleKey)) {
    return { path: '/home' }
  }

  if (isManager(store.roleKey) && (to.path.startsWith('/admin') || to.path === '/home')) {
    const userId = store.userInfo?.id
    const role = store.roleKey
    let response
    try { response = await fetchMenus() } catch { return false }
    if (!store.token || store.userInfo?.id !== userId || store.roleKey !== role) return { path: '/login' }
    store.managementMenus = managementMenus(store.roleKey, response.data)
    const target = to.path === '/admin/profile' && to.redirectedFrom?.path === '/admin'
      ? store.managementMenus[0]?.path || '/admin/profile'
      : managementDestination(to.path, store.managementMenus)
    if (target !== to.path) return { path: target }
  }

  return true
}
