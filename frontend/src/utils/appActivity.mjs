let background = false
const listeners = new Set()
export const isAppActive = () => !background && (typeof document === 'undefined' || !document.hidden)
const notify = () => listeners.forEach(callback => callback(isAppActive()))
if (typeof window !== 'undefined') {
  window.addEventListener('elderly:background', () => { background = true; notify() })
  window.addEventListener('elderly:foreground', () => { background = false; notify() })
  document.addEventListener('visibilitychange', notify)
}
export function subscribeActivity(callback) {
  listeners.add(callback)
  callback(isAppActive())
  return () => listeners.delete(callback)
}
export function createActiveClock(now = () => performance.now()) {
  let accumulated = 0
  let started = now()
  let active = true
  return {
    setActive(value) {
      if (active === value) return
      if (active) accumulated += now() - started
      else started = now()
      active = value
    },
    elapsedMs() { return accumulated + (active ? now() - started : 0) }
  }
}
