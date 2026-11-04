/** Android injects this bridge only into the app's trusted HTTPS origin. */
export function createNativeSpeech(scope) {
  let serial = 0
  let pending = null
  const available = () => typeof scope?.ElderlySpeech?.postMessage === 'function'
  const stop = () => {
    pending = null
    if (available()) scope.ElderlySpeech.postMessage(JSON.stringify({ action: 'stop' }))
  }
  const speak = (text, rate, onend, onerror) => {
    if (!available()) return false
    const bridge = scope.ElderlySpeech
    const id = String(++serial)
    pending = { id, onend, onerror }
    bridge.onmessage = event => {
      let result
      try { result = JSON.parse(event.data) } catch { return }
      if (pending?.id !== result.id) return
      const callbacks = pending
      pending = null
      if (result.status === 'done') callbacks.onend?.()
      else callbacks.onerror?.()
    }
    bridge.postMessage(JSON.stringify({ action: 'speak', id, text, rate }))
    return true
  }
  return { available, speak, stop }
}
export const nativeSpeech = createNativeSpeech(typeof window === 'undefined' ? {} : window)
