/** Only expose useful, user-facing messages, never Axios/SQL/provider diagnostics. */
export function learningErrorMessage(error: unknown, fallback = '操作未完成，请稍后重试。'): string {
  const e = error as { response?: { status?: number; data?: { error?: { message?: string }; message?: string } }; code?: string; message?: string }
  const status = e?.response?.status
  if (status === 401) return '登录已过期，请重新登录后继续；当前输入会保留。'
  if (status === 403) return '没有权限使用这项资料或项目，请重新选择。'
  if (status === 404) return '项目或资料已不存在，请返回列表重新选择。'
  if (status === 429) return '操作有些频繁，请稍等片刻再试。'
  if (e?.code === 'ERR_NETWORK') return '网络连接中断，当前输入已保留，请连接网络后重试。'
  if (e?.code === 'ECONNABORTED' || e?.code === 'ETIMEDOUT') return '请求超时，当前输入已保留，请稍后重试。'
  if (status && status >= 500) return '服务暂时不可用，当前输入已保留，请稍后重试。'
  const message = e?.response?.data?.error?.message || e?.response?.data?.message || e?.message
  if (message && /[\u4e00-\u9fff]/.test(message) && !/Exception|SQL|jdbc|stack trace|https?:\/\//i.test(message)) return message.slice(0, 240)
  if (status === 400) return '填写的内容未通过检查，请检查必填项、日期和时间安排后再确认。'
  if (status === 409) return '项目状态已变化，请重新打开项目后继续。当前草稿已保留。'
  return fallback
}
