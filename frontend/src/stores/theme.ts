import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { getSettings, updateSettings } from '@/api/auth'
import { getStoredToken } from '@/api/request'

export type ThemeMode = 'light' | 'dark'

const THEME_KEY = 'llm.theme'

function isThemeMode(value: unknown): value is ThemeMode {
  return value === 'light' || value === 'dark'
}

function applyTheme(mode: ThemeMode) {
  document.documentElement.dataset.theme = mode
}

export const useThemeStore = defineStore('theme', () => {
  const mode = ref<ThemeMode>('light')
  const isReady = ref(false)
  const isDark = computed(() => mode.value === 'dark')

  let changeVersion = 0
  let syncPromise: Promise<void> | null = null
  let pendingMode: ThemeMode | null = null
  let persistPromise: Promise<void> | null = null

  function commitMode(next: ThemeMode, persist = true) {
    mode.value = next
    applyTheme(next)
    if (persist) localStorage.setItem(THEME_KEY, next)
  }

  function init() {
    if (isReady.value) return
    const saved = localStorage.getItem(THEME_KEY)
    commitMode(isThemeMode(saved) ? saved : 'light', false)
    window.addEventListener('storage', event => {
      if (event.key !== THEME_KEY || !isThemeMode(event.newValue)) return
      changeVersion += 1
      commitMode(event.newValue, false)
    })
    isReady.value = true
  }

  async function syncFromServer() {
    if (!getStoredToken()) return
    if (syncPromise) return syncPromise

    const versionAtStart = changeVersion
    syncPromise = (async () => {
      try {
        const settings = await getSettings()
        if (isThemeMode(settings?.theme) && versionAtStart === changeVersion) {
          commitMode(settings.theme)
        }
      } catch (err) {
        console.error('Failed to sync user theme settings:', err)
      } finally {
        syncPromise = null
      }
    })()

    return syncPromise
  }

  function persistToServer() {
    if (persistPromise || !getStoredToken()) return

    persistPromise = (async () => {
      while (pendingMode) {
        const next = pendingMode
        pendingMode = null
        try {
          await updateSettings({ theme: next })
        } catch (err) {
          console.error('Failed to update theme to server:', err)
        }
      }
    })().finally(() => {
      persistPromise = null
      if (pendingMode) persistToServer()
    })
  }

  function setMode(next: ThemeMode) {
    if (next === mode.value && isReady.value) return

    changeVersion += 1
    commitMode(next)

    if (!getStoredToken()) return
    pendingMode = next
    persistToServer()
  }

  function toggle() {
    setMode(isDark.value ? 'light' : 'dark')
  }

  return { mode, isDark, isReady, init, syncFromServer, setMode, toggle }
})
