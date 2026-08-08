import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const push = vi.hoisted(() => vi.fn())
const authStore = vi.hoisted(() => ({
  user: {
    id: 'user-1',
    username: 'student@example.com',
    email: 'student@example.com',
    nickname: 'student',
    avatar: null,
  },
  updateProfile: vi.fn(),
  deleteAccount: vi.fn(),
}))

vi.mock('@/stores/auth', () => ({ useAuthStore: () => authStore }))
vi.mock('vue-router', () => ({ useRouter: () => ({ push }) }))

import UserProfileModal from '@/components/auth/UserProfileModal.vue'

describe('UserProfileModal', () => {
  beforeEach(() => {
    authStore.user.nickname = 'student'
    authStore.updateProfile.mockReset().mockResolvedValue({ displayName: '紫涵' })
    authStore.deleteAccount.mockReset().mockResolvedValue(true)
    push.mockReset()
  })

  afterEach(() => {
    document.body.replaceChildren()
  })

  it('only exposes the registered email, nickname and account deletion', async () => {
    const wrapper = mount(UserProfileModal, {
      props: { open: true },
      attachTo: document.body,
    })
    await flushPromises()

    const text = document.body.textContent ?? ''
    expect(text).toContain('个人资料')
    expect(text).toContain('昵称')
    expect(text).toContain('注销账号')
    expect(text).not.toContain('修改密码')
    expect(text).not.toContain('忘记密码')
    expect(text).not.toContain('登录设备')

    const email = document.body.querySelector<HTMLInputElement>('input[autocomplete="username"]')
    expect(email?.value).toBe('student@example.com')
    expect(email?.disabled).toBe(true)

    const nickname = document.body.querySelector<HTMLInputElement>('input[autocomplete="nickname"]')
    expect(nickname).not.toBeNull()
    expect(nickname?.maxLength).toBe(20)
    expect(nickname?.placeholder).toBe('最多可输入20个字')
    const profileFields = Array.from(document.body.querySelectorAll('.profile-form > label'))
    expect(profileFields[0]?.textContent).toContain('昵称')
    expect(profileFields[1]?.textContent).toContain('账号')
    expect(nickname?.closest('.nickname-control')?.querySelector('button')?.textContent)
      .toContain('确认昵称')
    await wrapper.unmount()
  })

  it('updates the nickname and requires two confirmations before deletion', async () => {
    const wrapper = mount(UserProfileModal, {
      props: { open: true },
      attachTo: document.body,
    })
    await flushPromises()

    const nickname = document.body.querySelector<HTMLInputElement>('input[autocomplete="nickname"]')!
    nickname.value = '紫涵'
    nickname.dispatchEvent(new Event('input'))
    document.body.querySelector<HTMLFormElement>('.profile-form')!
      .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }))
    await flushPromises()
    expect(authStore.updateProfile).toHaveBeenCalledWith('紫涵')

    const openDeletionButton = Array.from(document.body.querySelectorAll('button'))
      .find(button => button.textContent?.trim() === '注销账号')!
    openDeletionButton.click()
    await flushPromises()

    const deleteButton = Array.from(document.body.querySelectorAll('button'))
      .find(button => button.textContent?.trim() === '确认注销') as HTMLButtonElement
    expect(deleteButton.disabled).toBe(true)

    const password = document.body.querySelector<HTMLInputElement>('input[autocomplete="current-password"]')!
    const confirmation = document.body.querySelector<HTMLInputElement>('input[placeholder="注销账号"]')!
    password.value = 'StrongPass2026'
    password.dispatchEvent(new Event('input'))
    confirmation.value = '注销账号'
    confirmation.dispatchEvent(new Event('input'))
    await flushPromises()
    expect(deleteButton.disabled).toBe(false)

    document.body.querySelector<HTMLFormElement>('.deletion-form')!
      .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }))
    await flushPromises()
    expect(authStore.deleteAccount).toHaveBeenCalledWith('StrongPass2026', expect.any(Object))

    wrapper.unmount()
  })
})
