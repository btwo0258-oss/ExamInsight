import axios from 'axios'

import { request } from '@/api/request'

const DEVICE_ID_KEY = 'examinsight.device-id'

export type AuthSession = {
  userId: string
  email: string
  displayName: string | null
  authLevel: string
  idleExpiresAt: string
  absoluteExpiresAt: string
}

export type RegistrationChallenge = {
  challengeId: string
  expiresAt: string
  resendAfterSeconds: number
}

export type RegistrationProof = {
  registrationProof: string
  expiresAt: string
}

export type AuthErrorDetails = Record<string, unknown>

export class AuthApiError extends Error {
  readonly status?: number
  readonly code: string
  readonly requestId?: string
  readonly details: AuthErrorDetails

  constructor(options: {
    message: string
    code?: string
    status?: number
    requestId?: string
    details?: AuthErrorDetails
  }) {
    super(options.message)
    this.name = 'AuthApiError'
    this.status = options.status
    this.code = options.code ?? 'AUTH_REQUEST_FAILED'
    this.requestId = options.requestId
    this.details = options.details ?? {}
  }
}

function randomDeviceId(): string {
  if (typeof crypto.randomUUID === 'function') return crypto.randomUUID()
  const bytes = crypto.getRandomValues(new Uint8Array(24))
  return Array.from(bytes, value => value.toString(16).padStart(2, '0')).join('')
}

export function getDeviceId(): string {
  const current = localStorage.getItem(DEVICE_ID_KEY)
  if (current && /^[A-Za-z0-9._~-]{16,128}$/.test(current)) return current
  const next = randomDeviceId()
  localStorage.setItem(DEVICE_ID_KEY, next)
  return next
}

function mapAuthError(error: unknown, fallback: string): AuthApiError {
  if (error instanceof AuthApiError) return error
  if (!axios.isAxiosError(error)) return new AuthApiError({ message: fallback })

  const envelope = error.response?.data as {
    error?: {
      code?: string
      message?: string
      requestId?: string
      details?: AuthErrorDetails
    }
  } | undefined
  return new AuthApiError({
    status: error.response?.status,
    code: envelope?.error?.code,
    message: envelope?.error?.message || fallback,
    requestId: envelope?.error?.requestId,
    details: envelope?.error?.details,
  })
}

async function authCall<T>(operation: () => Promise<T>, fallback: string): Promise<T> {
  try {
    return await operation()
  } catch (error) {
    throw mapAuthError(error, fallback)
  }
}

export function getSession(): Promise<AuthSession> {
  return authCall(async () => (await request.get<AuthSession>('/api/v2/auth/session')).data,
    '无法确认登录状态，请检查网络后重试。')
}

export function createRegistrationChallenge(payload: {
  email: string
  humanVerificationToken: string
}): Promise<RegistrationChallenge> {
  return authCall(async () => (await request.post<RegistrationChallenge>(
    '/api/v2/auth/registration-challenges',
    { ...payload, deviceId: getDeviceId() },
  )).data, '验证码发送失败，请稍后重试。')
}

export function verifyRegistrationEmail(challengeId: string, code: string): Promise<RegistrationProof> {
  return authCall(async () => (await request.post<RegistrationProof>(
    `/api/v2/auth/registration-challenges/${encodeURIComponent(challengeId)}/verify-email`,
    { code },
  )).data, '邮箱验证码校验失败。')
}

export function register(payload: {
  email: string
  password: string
  displayName: string
  ageGateAcknowledged: boolean
  registrationProof: string
}): Promise<AuthSession> {
  return authCall(async () => (await request.post<AuthSession>('/api/v2/auth/register', {
    ...payload,
    deviceId: getDeviceId(),
  })).data, '注册失败，请稍后重试。')
}

export function login(payload: {
  email: string
  password: string
  humanVerificationToken?: string
}): Promise<AuthSession> {
  return authCall(async () => (await request.post<AuthSession>('/api/v2/auth/login', {
    ...payload,
    deviceId: getDeviceId(),
  })).data, '登录失败，请稍后重试。')
}

export function refreshCsrf(): Promise<{ token: string }> {
  return authCall(async () => (await request.get<{ token: string }>('/api/v2/auth/csrf')).data,
    '安全令牌刷新失败，请重新登录。')
}

export function logout(): Promise<void> {
  return authCall(async () => { await request.post('/api/v2/auth/logout') }, '退出登录失败，请稍后重试。')
}

export function logoutAll(): Promise<void> {
  return authCall(async () => { await request.post('/api/v2/auth/logout-all') }, '退出全部设备失败，请稍后重试。')
}
