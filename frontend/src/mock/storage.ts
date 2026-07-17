import { USER_KEY } from '@/api/request'

const PREFIX = 'examinsight.mock.v2'

function currentUserId() {
  const raw = sessionStorage.getItem(USER_KEY) ?? localStorage.getItem(USER_KEY)
  if (!raw) return 'guest'
  try {
    const user = JSON.parse(raw) as { id?: string | number }
    return user.id === undefined || user.id === null ? 'guest' : String(user.id)
  } catch {
    return 'guest'
  }
}

function storageKey(domain: string) {
  return `${PREFIX}.${currentUserId()}.${domain}`
}

export const mockSession = {
  key: storageKey,

  get<T>(domain: string, fallback: T): T {
    const raw = sessionStorage.getItem(storageKey(domain))
    if (!raw) return fallback
    try {
      return JSON.parse(raw) as T
    } catch {
      return fallback
    }
  },

  set<T>(domain: string, value: T) {
    sessionStorage.setItem(storageKey(domain), JSON.stringify(value))
  },

  remove(domain: string) {
    sessionStorage.removeItem(storageKey(domain))
  },

  clearCurrentUser() {
    const prefix = `${PREFIX}.${currentUserId()}.`
    Object.keys(sessionStorage)
      .filter((key) => key.startsWith(prefix))
      .forEach((key) => sessionStorage.removeItem(key))
  },
}
