export function resolveApiOrigin(value, native) {
  const origin = (value || '').trim().replace(/\/+$/, '')
  if (!origin) {
    if (native) throw new Error('原生应用构建需要配置 VITE_API_BASE 为服务器地址')
    return ''
  }
  const url = new URL(origin)
  if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password || url.pathname !== '/' || url.search || url.hash) {
    throw new Error('VITE_API_BASE 必须是 http(s) 源地址，不含 /api、账号或查询参数')
  }
  return url.origin
}
