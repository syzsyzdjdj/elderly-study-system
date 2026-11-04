const TEXT = '欢迎使用老年数字素养学习系统，祝您学习愉快！'

/** Missing preview media must not silently fail or masquerade as the selected voice. */
export function createVoicePreview({ audio, nativeSpeech, synthesis, Utterance, toUrl, onState, onMessage, getUnavailableReason }) {
  let generation = 0
  let cancelPending = () => {}
  let active = ''

  function stop() {
    generation++
    cancelPending()
    cancelPending = () => {}
    audio.pause()
    audio.removeAttribute('src')
    audio.load()
    nativeSpeech.stop()
    synthesis?.cancel()
    active = ''
    onState('')
  }

  function waitForPlayback(start) {
    return new Promise(resolve => {
      let settled = false
      let timer
      const finish = result => {
        if (settled) return
        settled = true
        clearTimeout(timer)
        audio.onended = audio.onerror = audio.onplaying = null
        resolve(result)
      }
      cancelPending = () => finish(false)
      // Covers missing media, a hung network request, and a silent speech engine.
      timer = setTimeout(() => finish(false), 15000)
      try { start(() => finish(true), () => finish(false)) } catch { finish(false) }
    })
  }

  async function play(voice) {
    const wasActive = active === voice.key
    stop()
    if (wasActive) return
    const current = generation
    active = voice.key
    onState(voice.key)
    let played = false
    if (voice.preview) {
      played = await waitForPlayback((done, fail) => {
        audio.onended = done
        audio.onerror = fail
        audio.src = toUrl(voice.preview)
        Promise.resolve(audio.play()).catch(fail)
      })
    }
    if (current !== generation) return
    if (!played) {
      audio.pause()
      onMessage((getUnavailableReason?.() || '该音色试听音频缺失或无法播放。') + ' 尝试设备默认音色（与所选音色不同）')
      played = await waitForPlayback((done, fail) => {
        if (nativeSpeech.available()) {
          if (!nativeSpeech.speak(TEXT, 1, done, fail)) fail()
          return
        }
        if (!synthesis || !Utterance) { fail(); return }
        const utterance = new Utterance(TEXT)
        utterance.lang = 'zh-CN'
        utterance.onend = done
        utterance.onerror = fail
        synthesis.speak(utterance)
      })
    }
    if (current !== generation) return
    stop()
    if (!played) onMessage((getUnavailableReason?.() || '当前音色的服务器试听尚未配置。') + ' 设备试听播放失败，请检查媒体音量及设备中文语音服务。')
  }

  return { play, stop }
}
