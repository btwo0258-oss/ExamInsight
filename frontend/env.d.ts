/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL?: string
  readonly VITE_DATA_SOURCE?: 'mock' | 'api'
  readonly VITE_ALIYUN_CAPTCHA_REGION?: 'cn' | 'sgp'
  readonly VITE_ALIYUN_CAPTCHA_PREFIX?: string
  readonly VITE_ALIYUN_CAPTCHA_SCENE_ID?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
