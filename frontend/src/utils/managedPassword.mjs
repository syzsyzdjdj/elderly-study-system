export function managedPasswordError(password) {
  if (typeof password !== 'string' || !password.trim() || password.length < 12) {
    return '请输入至少12字符的新密码'
  }
  if (new TextEncoder().encode(password).length > 72) return '密码的UTF-8编码不能超过72字节'
  return ''
}
