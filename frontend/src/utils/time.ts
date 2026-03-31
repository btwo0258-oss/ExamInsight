export function formatTime(input: number | string | Date): string {
  const date = input instanceof Date ? input : new Date(input)
  if (Number.isNaN(date.getTime())) return ''

  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  const hh = String(date.getHours()).padStart(2, '0')
  const mm = String(date.getMinutes()).padStart(2, '0')
  return `${y}-${m}-${d} ${hh}:${mm}`
}

export function formatRelativeTime(input: number | string | Date): string {
  const date = input instanceof Date ? input : new Date(input)
  const ts = date.getTime()
  if (Number.isNaN(ts)) return ''

  const diff = Date.now() - ts
  const sec = Math.floor(diff / 1000)
  if (sec < 60) return '刚刚'
  const min = Math.floor(sec / 60)
  if (min < 60) return `${min}分钟前`
  const hour = Math.floor(min / 60)
  if (hour < 24) return `${hour}小时前`
  const day = Math.floor(hour / 24)
  if (day < 7) return `${day}天前`
  return formatTime(date)
}
