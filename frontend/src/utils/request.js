/**
 * axios 统一封装
 * 约定：所有接口返回 {code, msg, data}，code===200 成功，code===500 业务失败（msg 中文提示）
 * HTTP 401=未登录、403=无权限
 */
import axios from 'axios'
import { showToast } from 'vant'
import router from '../router'
import { useUserStore } from '../store/user'
import { API_BASE } from './api-base'
import { refreshSession } from './session'

const request = axios.create({
  baseURL: API_BASE,
  timeout: 20000,
  withCredentials: true
})

// 请求拦截器：附加 token
request.interceptors.request.use((config) => {
  const store = useUserStore()
  if (store.token) {
    config.headers.Authorization = `Bearer ${store.token}`
  }
  return config
})

// 响应拦截器
request.interceptors.response.use(
  (response) => {
    const body = response.data
    // 成功
    if (body && body.code === 200) {
      return body
    }
    // 业务失败：统一 toast 中文提示（silent 请求静默失败，用于后台预生成等场景）
    if (body && body.code === 500) {
      if (!response.config?.silent) showToast(body.msg || '操作失败，请稍后再试')
      return Promise.reject(body)
    }
    // 其他情况（如文件流等）原样返回
    return body
  },
  async (error) => {
    if (axios.isCancel(error)) return Promise.reject(error)
    if (error.response) {
      const status = error.response.status
      if (status === 401) {
        const store = useUserStore()
        const config = error.config
        if (config && !config._sessionRetry && !/^\/(login|register|logout|session\/refresh)$/.test(config.url || '')) {
          config._sessionRetry = true
          try {
            const sent = config.headers?.Authorization
            if (!store.token || sent === `Bearer ${store.token}`) await refreshSession(store)
            config.headers.Authorization = `Bearer ${store.token}`
            return request(config)
          } catch (renewal) {
            if (!renewal.response && store.token) return Promise.reject(renewal)
          }
        }
        if (!store.token || config?._sessionRetry) {
          store.clearUser()
          if (!config?.silent) showToast('登录已过期，请重新登录')
          router.replace('/login')
        }
      } else if (status === 403) {
        showToast('没有权限操作')
      } else if (!error.config?.silent) {
        showToast('网络异常，请稍后再试')
      }
    } else if (!error.config?.silent) {
      showToast('网络异常，请检查网络连接')
    }
    return Promise.reject(error)
  }
)

export default request
