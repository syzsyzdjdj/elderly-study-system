/**
 * 适老化显示参数：字号缩放 + 高对比度。
 * 字号通过 zoom 整体放大（Android WebView/Chromium 与现代浏览器均支持），
 * 高对比度通过 html.high-contrast 类名切换 CSS 变量与描边样式。
 */

/** 根据用户资料立即生效显示参数 */
export function applyDisplay(userInfo) {
  const scale = Number(userInfo?.fontScale) || 100
  // 管理端布局在全屏容器之外，不受老人端 zoom 影响
  const inAdmin = location.hash.startsWith('#/admin')
  const root = document.documentElement
  root.style.zoom = !inAdmin && scale !== 100 ? String(scale / 100) : ''
  if (Number(userInfo?.highContrast) === 1) {
    root.classList.add('high-contrast')
  } else {
    root.classList.remove('high-contrast')
  }
  // 字体加粗模式
  if (!inAdmin && Number(userInfo?.fontBold) === 1) {
    root.classList.add('bold-text')
  } else {
    root.classList.remove('bold-text')
  }
  // 行距（百分比 140/160/190 → 1.4/1.6/1.9）
  const lh = Number(userInfo?.lineHeight) || 160
  root.style.setProperty('--app-lh', !inAdmin && lh !== 160 ? (lh / 100).toFixed(1) : '')
}

/** 路由切换后重新评估（进入/离开管理后台） */
export function refreshDisplayScope(userInfo) {
  applyDisplay(userInfo)
}
