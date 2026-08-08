const ALIYUN_CAPTCHA_SCRIPT = 'https://o.alicdn.com/captcha-frontend/aliyunCaptcha/AliyunCaptcha.js'

type AliyunCaptchaInstance = {
  show(): void
  hide(): void
  destroyCaptcha(): void
}

type AliyunCaptchaError = { code?: string; msg?: string }

type AliyunCaptchaOptions = {
  SceneId: string
  mode: 'popup' | 'embed'
  element: string
  button: string
  language: 'cn'
  timeout: number
  slideStyle: { width: number; height: number }
  success(token: string): void
  fail(result: unknown): void
  getInstance(instance: AliyunCaptchaInstance): void
  onError(error: AliyunCaptchaError): void
  onClose(reason: string): void
}

declare global {
  interface Window {
    AliyunCaptchaConfig?: { region: 'cn' | 'sgp'; prefix: string }
    initAliyunCaptcha?: (options: AliyunCaptchaOptions) => void
  }
}

export class HumanVerificationError extends Error {
  constructor(
    readonly code: 'NOT_CONFIGURED' | 'LOAD_FAILED' | 'INITIALIZE_FAILED' | 'CANCELLED',
    message: string,
  ) {
    super(message)
    this.name = 'HumanVerificationError'
  }
}

type PendingExecution = {
  resolve(token: string): void
  reject(error: HumanVerificationError): void
}

type PreparedCaptcha = {
  execute(): Promise<string>
  destroy(): void
}

export type EmbeddedHumanVerification = {
  destroy(): void
}

type EmbeddedHumanVerificationOptions = {
  element: HTMLElement
  button: HTMLButtonElement
  width: number
  onSuccess(token: string): void
  onFailure(): void
}

let scriptPromise: Promise<void> | null = null
let preparedPromise: Promise<PreparedCaptcha> | null = null
let sequence = 0

function configuration() {
  return {
    region: import.meta.env.VITE_ALIYUN_CAPTCHA_REGION ?? 'cn',
    prefix: import.meta.env.VITE_ALIYUN_CAPTCHA_PREFIX?.trim() ?? '',
    sceneId: import.meta.env.VITE_ALIYUN_CAPTCHA_SCENE_ID?.trim() ?? '',
  } as const
}

function ensureConfigured() {
  const config = configuration()
  if (!config.prefix || !config.sceneId) {
    throw new HumanVerificationError(
      'NOT_CONFIGURED',
      '人机验证尚未配置，暂时不能发送验证码或完成受保护登录。',
    )
  }
  return config
}

function loadAliyunCaptchaScript(): Promise<void> {
  if (typeof window.initAliyunCaptcha === 'function') return Promise.resolve()
  if (scriptPromise) return scriptPromise

  scriptPromise = new Promise<void>((resolve, reject) => {
    const existing = document.querySelector<HTMLScriptElement>(`script[src="${ALIYUN_CAPTCHA_SCRIPT}"]`)
    const script = existing ?? document.createElement('script')
    const onLoad = () => {
      if (typeof window.initAliyunCaptcha === 'function') resolve()
      else {
        script.remove()
        reject(new HumanVerificationError('LOAD_FAILED', '人机验证组件加载失败，请刷新页面重试。'))
      }
    }
    const onError = () => {
      script.remove()
      reject(new HumanVerificationError('LOAD_FAILED', '人机验证组件加载失败，请检查网络后重试。'))
    }

    script.addEventListener('load', onLoad, { once: true })
    script.addEventListener('error', onError, { once: true })
    if (!existing) {
      script.src = ALIYUN_CAPTCHA_SCRIPT
      script.async = true
      document.head.appendChild(script)
    }
  }).catch(error => {
    scriptPromise = null
    throw error
  })

  return scriptPromise
}

async function prepare(): Promise<PreparedCaptcha> {
  const config = ensureConfigured()
  window.AliyunCaptchaConfig = { region: config.region, prefix: config.prefix }
  const preparationStartedAt = Date.now()
  await loadAliyunCaptchaScript()

  const id = ++sequence
  const root = document.createElement('div')
  root.dataset.examInsightCaptcha = String(id)
  root.style.position = 'fixed'
  root.style.inset = 'auto'
  root.style.width = '1px'
  root.style.height = '1px'
  root.style.overflow = 'hidden'

  const element = document.createElement('div')
  element.id = `examinsight-captcha-element-${id}`
  const button = document.createElement('button')
  button.id = `examinsight-captcha-trigger-${id}`
  button.type = 'button'
  button.tabIndex = -1
  root.append(element, button)
  document.body.appendChild(root)

  let instance: AliyunCaptchaInstance | null = null
  let pending: PendingExecution | null = null
  let destroyed = false

  const destroy = () => {
    if (destroyed) return
    destroyed = true
    try { instance?.destroyCaptcha() } catch {}
    root.remove()
    if (pending) {
      pending.reject(new HumanVerificationError('CANCELLED', '人机验证已取消。'))
      pending = null
    }
  }

  const ready = new Promise<void>((resolve, reject) => {
    const timer = window.setTimeout(() => {
      reject(new HumanVerificationError('INITIALIZE_FAILED', '人机验证初始化超时，请稍后重试。'))
    }, 10_000)

    window.initAliyunCaptcha?.({
      SceneId: config.sceneId,
      mode: 'popup',
      element: `#${element.id}`,
      button: `#${button.id}`,
      language: 'cn',
      timeout: 5_000,
      slideStyle: {
        width: Math.max(320, Math.min(360, window.innerWidth - 32)),
        height: 40,
      },
      success(token) {
        if (!pending || !token) return
        const execution = pending
        pending = null
        execution.resolve(token)
      },
      fail() {
        // The provider refreshes the challenge automatically while it remains valid.
      },
      getInstance(nextInstance) {
        window.clearTimeout(timer)
        instance = nextInstance
        resolve()
      },
      onError(error) {
        window.clearTimeout(timer)
        reject(new HumanVerificationError(
          'INITIALIZE_FAILED',
          error.msg || '人机验证初始化失败，请稍后重试。',
        ))
      },
      onClose(reason) {
        if (reason !== 'userDismiss' || !pending) return
        const execution = pending
        pending = null
        execution.reject(new HumanVerificationError('CANCELLED', '你已取消人机验证。'))
      },
    })
  })

  await ready.catch(error => {
    destroy()
    throw error
  })

  return {
    async execute() {
      if (destroyed || !instance) {
        throw new HumanVerificationError('INITIALIZE_FAILED', '人机验证实例已失效，请重试。')
      }
      if (pending) return Promise.reject(
        new HumanVerificationError('INITIALIZE_FAILED', '人机验证正在进行，请完成当前验证。'),
      )

      // Alibaba Cloud recommends at least two seconds between initialization and verification.
      const remaining = 2_100 - (Date.now() - preparationStartedAt)
      if (remaining > 0) await new Promise(resolve => window.setTimeout(resolve, remaining))

      return new Promise<string>((resolve, reject) => {
        pending = { resolve, reject }
        instance?.show()
      })
    },
    destroy,
  }
}

function prepared(): Promise<PreparedCaptcha> {
  if (!preparedPromise) {
    preparedPromise = prepare().catch(error => {
      preparedPromise = null
      throw error
    })
  }
  return preparedPromise
}

export function preloadHumanVerification() {
  void prepared().catch(() => undefined)
}

export async function runHumanVerification(): Promise<string> {
  const captcha = await prepared()
  try {
    return await captcha.execute()
  } finally {
    captcha.destroy()
    preparedPromise = null
  }
}

export async function mountEmbeddedHumanVerification({
  element,
  button,
  width,
  onSuccess,
  onFailure,
}: EmbeddedHumanVerificationOptions): Promise<EmbeddedHumanVerification> {
  const config = ensureConfigured()
  window.AliyunCaptchaConfig = { region: config.region, prefix: config.prefix }
  await loadAliyunCaptchaScript()

  const id = ++sequence
  element.id = `examinsight-captcha-element-${id}`
  button.id = `examinsight-captcha-trigger-${id}`

  let instance: AliyunCaptchaInstance | null = null
  let destroyed = false

  const destroy = () => {
    if (destroyed) return
    destroyed = true
    try { instance?.destroyCaptcha() } catch {}
    element.replaceChildren()
    element.removeAttribute('id')
    button.removeAttribute('id')
  }

  const ready = new Promise<void>((resolve, reject) => {
    const timer = window.setTimeout(() => {
      reject(new HumanVerificationError('INITIALIZE_FAILED', '人机验证初始化超时，请稍后重试。'))
    }, 10_000)

    window.initAliyunCaptcha?.({
      SceneId: config.sceneId,
      mode: 'embed',
      element: `#${element.id}`,
      button: `#${button.id}`,
      language: 'cn',
      timeout: 5_000,
      slideStyle: {
        width: Math.max(280, Math.floor(width)),
        height: 48,
      },
      success(token) {
        if (!destroyed && token) onSuccess(token)
      },
      fail() {
        if (!destroyed) onFailure()
      },
      getInstance(nextInstance) {
        window.clearTimeout(timer)
        instance = nextInstance
        resolve()
      },
      onError(error) {
        window.clearTimeout(timer)
        reject(new HumanVerificationError(
          'INITIALIZE_FAILED',
          error.msg || '人机验证初始化失败，请稍后重试。',
        ))
      },
      onClose() {},
    })
  })

  await ready.catch(error => {
    destroy()
    throw error
  })

  return { destroy }
}
