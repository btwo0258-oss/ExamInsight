import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

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
      commitMode(event.newValue, false)
    })
    isReady.value = true
  }

  // V2 has not exposed a user-setting API yet. Keep the theme local instead of
  // silently falling back to the legacy user/settings endpoints.
  async function syncFromServer() {}

  function setMode(next: ThemeMode) {
    if (next === mode.value && isReady.value) return
    commitMode(next)
  }

  function toggle() {
    setMode(isDark.value ? 'light' : 'dark')
  }

  return { mode, isDark, isReady, init, syncFromServer, setMode, toggle }
})
