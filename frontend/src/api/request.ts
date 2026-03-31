import axios, { type AxiosInstance, type AxiosResponse } from 'axios'

const TOKEN_KEY = 'llm.token'
const USER_KEY = 'llm.user'

export function getStoredToken(): string | null {
  return sessionStorage.getItem(TOKEN_KEY) || localStorage.getItem(TOKEN_KEY)
}

export function clearStoredAuth() {
  localStorage.removeItem(TOKEN_KEY)
  sessionStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
  sessionStorage.removeItem(USER_KEY)
}

export const request: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '',
  timeout: 30_000,
  headers: { 'Content-Type': 'application/json' },
})

request.interceptors.request.use((config) => {
  const token = getStoredToken()
  if (token) {
    config.headers = config.headers ?? {}
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

request.interceptors.response.use(
  (res: AxiosResponse) => res,
  async (error: unknown) => {
    const status = (error as { response?: { status?: number } })?.response?.status
    if (status === 401) {
      clearStoredAuth()
      window.dispatchEvent(new CustomEvent('auth:logout'))
    }
    return Promise.reject(error)
  },
)

export { TOKEN_KEY, USER_KEY }
