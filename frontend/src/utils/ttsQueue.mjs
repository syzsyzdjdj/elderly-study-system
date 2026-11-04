/** 每次会话独立的分句缓存。最多预取一段，取消后不再发出请求。 */
export function createTtsQueue(segments, generate) {
  const controller = new AbortController()
  const pending = new Map()
  const get = (index) => {
    if (controller.signal.aborted) return Promise.reject(new Error('cancelled'))
    if (index < 0 || index >= segments.length) return Promise.reject(new Error('invalid segment'))
    if (!pending.has(index)) {
      const promise = Promise.resolve().then(() => {
        if (controller.signal.aborted) throw new Error('cancelled')
        return generate(segments[index], controller.signal)
      })
      pending.set(index, promise)
      promise.catch(() => { pending.delete(index) })
    }
    return pending.get(index)
  }
  return {
    get,
    prefetch(index) { if (index < segments.length) get(index).catch(() => {}) },
    cancel() { controller.abort(); pending.clear() }
  }
}
