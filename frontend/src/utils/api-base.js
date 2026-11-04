/**
 * API 与媒体资源基址配置
 * - Web 开发：API_ORIGIN 为空字符串，请求走相对路径 /api、/uploads（Vite 代理）
 * - APP（Capacitor 原生容器）：由构建环境指定真实服务器源地址
 */
import { Capacitor } from '@capacitor/core'
import { resolveApiOrigin } from './api-origin.mjs'

// 原生构建通过 VITE_API_BASE 指定服务器，Web 默认使用同源代理。

/** 是否运行在 APP 原生容器中 */
const isNative = Capacitor.isNativePlatform?.() ?? false

/** 构建时可用环境变量 VITE_API_BASE 覆盖（如 http://192.168.1.10:8081） */
const ENV_BASE = (import.meta.env.VITE_API_BASE || '').replace(/\/$/, '')

/** 后端源地址：Web 为空（相对路径走代理）；APP 为后端真实地址 */
export const API_ORIGIN = resolveApiOrigin(ENV_BASE, isNative)

/** axios baseURL */
export const API_BASE = `${API_ORIGIN}/api`

/**
 * 把后端返回的相对媒体路径（如 /uploads/videos/a.mp4）转成可访问地址。
 * 已是 http/data/blob/capacitor 等绝对地址的原样返回。
 */
export function mediaUrl(url) {
  if (!url) return ''
  if (/^(https?:|data:|blob:|capacitor:)/.test(url)) return url
  return API_ORIGIN + (url.startsWith('/') ? url : `/${url}`)
}
