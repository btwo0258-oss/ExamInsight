import { beforeEach, describe, expect, it } from 'vitest'
import { USER_KEY } from '@/api/request'
import { mockSession } from '@/mock/storage'

describe('mockSession', () => {
  beforeEach(() => {
    sessionStorage.clear()
    localStorage.clear()
  })

  it('isolates Mock business data by current user', () => {
    sessionStorage.setItem(USER_KEY, JSON.stringify({ id: 101 }))
    mockSession.set('conversations', [{ id: 1 }])

    sessionStorage.setItem(USER_KEY, JSON.stringify({ id: 202 }))
    expect(mockSession.get('conversations', [])).toEqual([])
    mockSession.set('conversations', [{ id: 2 }])

    sessionStorage.setItem(USER_KEY, JSON.stringify({ id: 101 }))
    expect(mockSession.get('conversations', [])).toEqual([{ id: 1 }])
  })

  it('clears only the current user Mock domains', () => {
    sessionStorage.setItem(USER_KEY, JSON.stringify({ id: 101 }))
    mockSession.set('messages.1', [{ id: 1 }])

    sessionStorage.setItem(USER_KEY, JSON.stringify({ id: 202 }))
    mockSession.set('messages.1', [{ id: 2 }])
    mockSession.clearCurrentUser()
    expect(mockSession.get('messages.1', [])).toEqual([])

    sessionStorage.setItem(USER_KEY, JSON.stringify({ id: 101 }))
    expect(mockSession.get('messages.1', [])).toEqual([{ id: 1 }])
  })
})
