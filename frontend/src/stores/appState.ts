import { ref, watch } from 'vue'
import { defineStore } from 'pinia'

type AppState = 'chat' | 'knowledge' | 'exam-analysis' | 'learning'

export const useAppState = defineStore('appState', () => {
  const currentMode = ref<AppState>('chat')
  const activeKnowledgeBaseId = ref<number | null>(null)
  const timeUnit = ref<'ms' | 's'>(localStorage.getItem('llm.timeUnit') === 's' ? 's' : 'ms')

  watch(timeUnit, (newVal) => {
    localStorage.setItem('llm.timeUnit', newVal)
  })

  function setMode(mode: AppState) {
    currentMode.value = mode
  }

  function setActiveKnowledgeBase(id: number | null) {
    activeKnowledgeBaseId.value = id
  }

  function reset() {
    currentMode.value = 'chat'
    activeKnowledgeBaseId.value = null
  }

  function toggleTimeUnit() {
    timeUnit.value = timeUnit.value === 'ms' ? 's' : 'ms'
  }

  return {
    currentMode,
    activeKnowledgeBaseId,
    timeUnit,
    setMode,
    setActiveKnowledgeBase,
    reset,
    toggleTimeUnit
  }
})
