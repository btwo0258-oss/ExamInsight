export function validateNewPassword(password: string, confirmation?: string): string | null {
  const normalized = password.normalize('NFC')
  const length = Array.from(normalized).length
  if (length < 8 || length > 16) return '密码需要包含 8–16 个字符'
  if (!/^[!-~]+$/.test(normalized)) return '密码只能使用不含空格的英文字母、数字和可见符号'
  if (!/[A-Za-z]/.test(normalized) || !/\d/.test(normalized)) return '密码必须同时包含英文字母和数字'
  if (confirmation !== undefined && confirmation !== password) return '两次输入的密码不一致'
  return null
}
