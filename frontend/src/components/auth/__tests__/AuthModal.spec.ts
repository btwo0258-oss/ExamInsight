import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'

const authStore = vi.hoisted(() => ({
  errorMessage: null as string | null,
  isSubmitting: false,
  login: vi.fn(),
  register: vi.fn(),
}))

vi.mock('@/stores/auth', () => ({ useAuthStore: () => authStore }))
vi.mock('@/stores/theme', () => ({ useThemeStore: () => ({ isDark: false }) }))
vi.mock('vue-router', () => ({
  useRouter: () => ({ replace: vi.fn() }),
  RouterLink: {
    props: ['to'],
    template: '<a><slot /></a>',
  },
}))
vi.mock('@/services/humanVerification', () => ({
  mountEmbeddedHumanVerification: vi.fn().mockResolvedValue({ destroy: vi.fn() }),
  preloadHumanVerification: vi.fn(),
  runHumanVerification: vi.fn(),
}))

import AuthModal from '@/components/auth/AuthModal.vue'

describe('AuthModal', () => {
  afterEach(() => {
    document.body.replaceChildren()
    vi.clearAllMocks()
  })

  it('shows the complete registration form on one page without profile or age fields', async () => {
    const wrapper = mount(AuthModal, { props: { open: true }, attachTo: document.body })
    await flushPromises()

    const registerLink = Array.from(document.body.querySelectorAll('button'))
      .find(button => button.textContent === '去注册')
    expect(registerLink).toBeDefined()
    registerLink!.click()
    await flushPromises()

    const text = document.body.textContent ?? ''
    expect(text).toContain('创建 ExamInsight 账户')
    expect(text).toContain('获取验证码')
    expect(text).toContain('确认密码')
    expect(text).toContain('用户协议')
    expect(text).toContain('隐私政策')
    expect(text).toContain('已有账号？')
    expect(text).not.toContain('昵称')
    expect(text).not.toContain('最低使用年龄')
    expect(document.body.querySelector('input[aria-label="6 位邮箱验证码"]')).not.toBeNull()
    expect(document.body.querySelectorAll('input[type="password"]')).toHaveLength(2)

    wrapper.unmount()
  })
})
