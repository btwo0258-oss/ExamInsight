import { ref } from 'vue'
import { defineStore } from 'pinia'
import { getSettings, updateSettings } from '@/api/auth'
import { getStoredToken } from '@/api/request'

export type ThemeMode = 'light' | 'dark'

const THEME_KEY = 'llm.theme'

function applyTheme(mode: ThemeMode) {
  document.documentElement.setAttribute('data-theme', mode)
}

export const useThemeStore = defineStore('theme', () => {
  const mode = ref<ThemeMode>('light')
  const isReady = ref(false)

  async function syncFromServer() {
    if (getStoredToken()) {
      try {
        const settings = await getSettings()
        if (settings && (settings.theme === 'light' || settings.theme === 'dark')) {
          mode.value = settings.theme
          localStorage.setItem(THEME_KEY, settings.theme)
          applyTheme(settings.theme)
        }
      } catch (err) {
        console.error('Failed to sync user theme settings:', err)
      }
    }
  }

  async function init() {
    if (isReady.value) return
    const saved = localStorage.getItem(THEME_KEY)
    if (saved === 'light' || saved === 'dark') {
      mode.value = saved
    }
    applyTheme(mode.value)
    isReady.value = true
    
    await syncFromServer()
  }

  function setMode(next: ThemeMode) {
    mode.value = next
    localStorage.setItem(THEME_KEY, next)
    applyTheme(next)
    if (getStoredToken()) {
      updateSettings({ theme: next }).catch(err => {
        console.error('Failed to update theme to server:', err)
      })
    }
  }

  function toggle() {
    setMode(mode.value === 'dark' ? 'light' : 'dark')
  }

  return { mode, isReady, init, syncFromServer, setMode, toggle }
})
