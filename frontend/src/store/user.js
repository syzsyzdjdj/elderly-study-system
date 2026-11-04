import { defineStore } from 'pinia'

const TOKEN_KEY = 'elderly_token'
const USER_KEY = 'elderly_user'
const ROLE_KEY = 'elderly_role'

function readJSON(key) {
  try {
    return JSON.parse(localStorage.getItem(key) || 'null')
  } catch (e) {
    return null
  }
}

/**
 * 用户登录状态（localStorage 持久化）
 * 字段：token / userInfo / roleKey（admin | course_manager | user）
 */
export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY) || '',
    userInfo: readJSON(USER_KEY),
    roleKey: localStorage.getItem(ROLE_KEY) || '',
    remembered: localStorage.getItem('elderly_remembered') === '1',
    authRevision: 0,
    managementMenus: null
  }),
  getters: {
    nickname: (state) => state.userInfo?.nickname || state.userInfo?.username || ''
  },
  actions: {
    setRemembered(value) {
      this.remembered = !!value
      if (value) localStorage.setItem('elderly_remembered', '1')
      else localStorage.removeItem('elderly_remembered')
    },
    setToken(token) {
      this.managementMenus = null
      this.authRevision++
      this.token = token || ''
      localStorage.setItem(TOKEN_KEY, this.token)
    },
    setUserInfo(info) {
      this.userInfo = info || null
      localStorage.setItem(USER_KEY, JSON.stringify(this.userInfo))
    },
    setRoleKey(roleKey) {
      this.roleKey = roleKey || ''
      localStorage.setItem(ROLE_KEY, this.roleKey)
    },
    clearUser() {
      this.authRevision++
      this.token = ''
      this.userInfo = null
      this.roleKey = ''
      this.managementMenus = null
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(USER_KEY)
      localStorage.removeItem(ROLE_KEY)
      this.setRemembered(false)
    }
  }
})
