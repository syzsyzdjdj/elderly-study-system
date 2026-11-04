export function ttsFailureMessage(error) {
  const message = error?.msg || error?.response?.data?.msg
  return typeof message === 'string' && message.trim()
    ? message
    : '服务器语音请求未成功，暂时无法确认具体原因，请检查网络连接或联系管理员。'
}
