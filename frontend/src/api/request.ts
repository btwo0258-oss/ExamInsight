import axios, { type AxiosInstance, type AxiosResponse } from 'axios'

// USER_KEY is retained only for the isolated mock repositories. Production
// authentication never persists a user or bearer token in browser storage.
const USER_KEY = 'llm.user'
const LEGACY_TOKEN_KEY = 'llm.token'

/** @deprecated V2 uses an HttpOnly session cookie. */
export function getStoredToken(): null {
  return null
}

export function clearStoredAuth() {
  localStorage.removeItem(LEGACY_TOKEN_KEY)
  sessionStorage.removeItem(LEGACY_TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
  sessionStorage.removeItem(USER_KEY)
}

function csrfToken(): string | null {
  const match = document.cookie.split('; ')
    .find(cookie => cookie.startsWith('XSRF-TOKEN='))
  return match ? decodeURIComponent(match.slice('XSRF-TOKEN='.length)) : null
}

export function sessionFetch(input: RequestInfo | URL, init: RequestInit = {}) {
  const headers = new Headers(init.headers)
  const method = (init.method ?? 'GET').toUpperCase()
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    const token = csrfToken()
    if (token) headers.set('X-CSRF-Token', token)
  }
  return fetch(input, { ...init, headers, credentials: 'include' })
}

export const request: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '',
  timeout: 30_000,
  withCredentials: true,
  xsrfCookieName: 'XSRF-TOKEN',
  xsrfHeaderName: 'X-CSRF-Token',
  headers: { 'Content-Type': 'application/json' },
})

request.interceptors.response.use(
  (res: AxiosResponse) => res,
  async (error: unknown) => {
    const status = (error as { response?: { status?: number } })?.response?.status
    if (status === 401) window.dispatchEvent(new CustomEvent('auth:session-expired'))
    return Promise.reject(error)
  },
)

export { USER_KEY }
