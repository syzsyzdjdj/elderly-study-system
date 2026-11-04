import axios from 'axios'
import { API_BASE } from './api-base'

let refreshing = null
export function needsRefresh(token, now = Date.now()) {
  if (!token) return true
  try {
    const payload = JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')))
    return !Number.isFinite(payload.exp) || payload.exp * 1000 <= now + 30000
  } catch { return true }
}

/** The HttpOnly cookie stays outside JS. Coalesce renewal and ignore old-account responses. */
export function refreshSession(store) {
  if (refreshing) return refreshing
  const previous = store.token
  const revision = store.authRevision
  refreshing = axios.post(`${API_BASE}/session/refresh`, {}, {
    withCredentials: true, timeout: 15000, headers: { 'X-Session-Request': '1' }
  }).then(response => {
    if (store.token !== previous || store.authRevision !== revision) throw new Error('session changed')
    const result = response.data
    if (result?.code !== 200 || !result.data?.token) throw new Error('invalid session')
    store.setToken(result.data.token)
    store.setUserInfo(result.data.userInfo)
    store.setRoleKey(result.data.roleKey)
    return result.data
  }).catch(error => {
    if (error.response?.status === 401 && store.token === previous && store.authRevision === revision) store.clearUser()
    throw error
  }).finally(() => { refreshing = null })
  return refreshing
}

export async function restoreSession(store) {
  if (!needsRefresh(store.token)) return
  if (!store.remembered) { if (store.token) store.clearUser(); return }
  try { await refreshSession(store) } catch { /* Offline errors must not erase a valid saved account. */ }
}
