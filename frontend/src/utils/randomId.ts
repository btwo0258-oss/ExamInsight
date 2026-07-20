export function createRandomId(prefix = 'request') {
  return globalThis.crypto?.randomUUID?.()
    ?? `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 12)}`
}
