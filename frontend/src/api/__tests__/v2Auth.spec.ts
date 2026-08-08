import { AxiosError, type AxiosResponse } from 'axios'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const requestMock = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  patch: vi.fn(),
}))

vi.mock('@/api/request', () => ({ request: requestMock }))

import {
  AuthApiError,
  createRegistrationChallenge,
  getDeviceId,
  login,
  logout,
  register,
  requestAccountDeletion,
  updateAccountProfile,
  verifyRegistrationEmail,
} from '@/api/v2Auth'

describe('V2 auth API', () => {
  beforeEach(() => {
    requestMock.get.mockReset()
    requestMock.post.mockReset()
    requestMock.patch.mockReset()
    localStorage.clear()
    sessionStorage.clear()
  })

  it('creates one stable anonymous device id without storing an auth token', () => {
    const first = getDeviceId()
    const second = getDeviceId()

    expect(first).toBe(second)
    expect(first).toMatch(/^[A-Za-z0-9._~-]{16,128}$/)
    expect(localStorage.getItem('llm.token')).toBeNull()
    expect(sessionStorage.getItem('llm.token')).toBeNull()
  })

  it('sends the captcha proof and stable device id when requesting an email code', async () => {
    requestMock.post.mockResolvedValue({
      data: { challengeId: 'challenge-1', expiresAt: '2026-08-04T00:10:00Z', resendAfterSeconds: 60 },
    })

    await createRegistrationChallenge({
      email: 'student@example.com',
      humanVerificationToken: 'captcha-proof',
    })

    expect(requestMock.post).toHaveBeenCalledWith('/api/v2/auth/registration-challenges', {
      email: 'student@example.com',
      humanVerificationToken: 'captcha-proof',
      deviceId: getDeviceId(),
    })
  })

  it('does not trim or rewrite a password before login', async () => {
    requestMock.post.mockResolvedValue({ data: { userId: 'user-1' } })

    await login({ email: 'student@example.com', password: '  a long passphrase  ' })

    expect(requestMock.post).toHaveBeenCalledWith('/api/v2/auth/login', expect.objectContaining({
      password: '  a long passphrase  ',
    }))
  })

  it('registers with the verified email proof and no client-authored profile fields', async () => {
    requestMock.post.mockResolvedValue({ data: { userId: 'user-1' } })

    await register({
      email: 'student@example.com',
      password: 'StrongPass2026',
      registrationProof: 'registration-proof',
      termsVersion: '2026-08-08-beta.1',
      privacyVersion: '2026-08-08-beta.1',
      legalDocumentsAccepted: true,
    })

    expect(requestMock.post).toHaveBeenCalledWith('/api/v2/auth/register', {
      email: 'student@example.com',
      password: 'StrongPass2026',
      registrationProof: 'registration-proof',
      termsVersion: '2026-08-08-beta.1',
      privacyVersion: '2026-08-08-beta.1',
      legalDocumentsAccepted: true,
      deviceId: getDeviceId(),
    })
  })

  it('maps the structured backend error without losing its recovery code', async () => {
    const response = {
      status: 409,
      data: {
        error: {
          code: 'VERIFICATION_EXPIRED',
          message: '验证码已过期，请重新获取。',
          requestId: 'request-1',
          details: { retryable: true },
        },
      },
    } as AxiosResponse
    requestMock.post.mockRejectedValue(new AxiosError('conflict', '409', undefined, undefined, response))

    const failure = verifyRegistrationEmail('challenge-1', '123456')

    await expect(failure).rejects.toMatchObject<AuthApiError>({
      code: 'VERIFICATION_EXPIRED',
      status: 409,
      requestId: 'request-1',
      details: { retryable: true },
    })
  })

  it('logs out through the protected V2 endpoint', async () => {
    requestMock.post.mockResolvedValue({ data: undefined })
    await logout()
    expect(requestMock.post).toHaveBeenCalledWith('/api/v2/auth/logout')
  })

  it('uses the protected account endpoints without client-side identity fields', async () => {
    requestMock.patch.mockResolvedValue({
      data: { userId: 'user-1', email: 'student@example.com', displayName: '紫涵' },
    })
    requestMock.post.mockResolvedValue({ data: undefined })

    await updateAccountProfile('紫涵')
    await requestAccountDeletion('StrongPass2026')

    expect(requestMock.patch).toHaveBeenCalledWith('/api/v2/account/profile', { displayName: '紫涵' })
    expect(requestMock.post).toHaveBeenCalledWith('/api/v2/account/deletion-requests', {
      currentPassword: 'StrongPass2026',
      confirmation: 'DELETE_MY_ACCOUNT',
    })
  })
})
