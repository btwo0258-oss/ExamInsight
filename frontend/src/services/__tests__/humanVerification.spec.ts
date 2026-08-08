import { afterEach, describe, expect, it, vi } from 'vitest'

import { mountEmbeddedHumanVerification } from '@/services/humanVerification'

describe('embedded human verification', () => {
  afterEach(() => {
    vi.unstubAllEnvs()
    delete window.initAliyunCaptcha
    delete window.AliyunCaptchaConfig
    document.body.replaceChildren()
  })

  it('mounts Alibaba Cloud captcha in embed mode and returns its proof', async () => {
    vi.stubEnv('VITE_ALIYUN_CAPTCHA_PREFIX', 'test-prefix')
    vi.stubEnv('VITE_ALIYUN_CAPTCHA_SCENE_ID', 'test-scene')

    const element = document.createElement('div')
    const button = document.createElement('button')
    document.body.append(element, button)
    const destroyCaptcha = vi.fn()
    const onSuccess = vi.fn()
    let options: Parameters<NonNullable<typeof window.initAliyunCaptcha>>[0] | undefined

    window.initAliyunCaptcha = nextOptions => {
      options = nextOptions
      nextOptions.getInstance({ show: vi.fn(), hide: vi.fn(), destroyCaptcha })
    }

    const mounted = await mountEmbeddedHumanVerification({
      element,
      button,
      width: 420,
      onSuccess,
      onFailure: vi.fn(),
    })

    expect(options?.mode).toBe('embed')
    expect(options?.element).toBe(`#${element.id}`)
    expect(options?.button).toBe(`#${button.id}`)
    expect(options?.slideStyle).toEqual({ width: 420, height: 48 })

    options?.success('captcha-proof')
    expect(onSuccess).toHaveBeenCalledWith('captcha-proof')

    mounted.destroy()
    expect(destroyCaptcha).toHaveBeenCalledOnce()
    expect(element.id).toBe('')
    expect(button.id).toBe('')
  })
})
