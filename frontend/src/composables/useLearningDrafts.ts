import { computed, ref, watch, type Ref } from 'vue'
import { learningErrorMessage } from '@/utils/learningErrors'

type Json = Record<string, unknown>
type Pending = { value: Json; version: string }
type Options = {
  storageKey: string
  values: Ref<Record<string, Json>>
  version: (section: string) => string
  save: (section: string, value: Json) => Promise<unknown>
  delay?: number
}

/** Serial, coalesced writes. An old acknowledgement must never replace newer typing. */
export function useLearningDrafts(options: Options) {
  const pending = new Map<string, Pending>()
  const state = ref<'idle' | 'pending' | 'saving' | 'saved' | 'error'>('idle')
  const error = ref('')
  const backupError = ref('')
  let ready = false
  let muted = false
  let disposed = false
  let timer: ReturnType<typeof setTimeout> | undefined
  let flight: Promise<boolean> | undefined
  let baseline: Record<string, string> = {}
  const clone = <T,>(value: T): T => JSON.parse(JSON.stringify(value))
  const status = computed(() => error.value || backupError.value || ({ idle: '修改后自动保存', pending: '草稿待保存…', saving: '正在保存草稿…', saved: '草稿已保存', error: '草稿保存失败' }[state.value]))

  function backup() {
    try {
      if (pending.size) sessionStorage.setItem(options.storageKey, JSON.stringify(Object.fromEntries(pending)))
      else sessionStorage.removeItem(options.storageKey)
      backupError.value = ''
    } catch { backupError.value = '浏览器无法保留草稿，请等待保存成功后再离开。' }
  }

  function schedule() {
    clearTimeout(timer)
    if (!disposed) timer = setTimeout(() => { void flush() }, options.delay ?? 500)
  }

  function touch(section: string) {
    const value = options.values.value[section]
    if (!ready || !value) return
    pending.set(section, { value: clone(value), version: options.version(section) })
    baseline[section] = JSON.stringify(value)
    error.value = ''
    if (state.value !== 'saving') state.value = 'pending'
    backup()
    schedule()
  }

  watch(options.values, (values) => {
    if (!ready || muted) return
    for (const [section, value] of Object.entries(values)) {
      if (JSON.stringify(value) !== baseline[section]) touch(section)
    }
  }, { deep: true, flush: 'sync' })

  function replace(action: () => void, sections?: string[]) {
    muted = true
    try { action() } finally { muted = false }
    for (const [section, value] of Object.entries(options.values.value)) {
      if (sections && !sections.includes(section)) continue
      baseline[section] = JSON.stringify(value)
      pending.delete(section)
    }
    backup()
  }

  function initialize(action: () => void, restore: (section: string, value: Json) => void) {
    // Read before replace() clears the pending cache.
    let saved: Record<string, Pending> = {}
    try { saved = JSON.parse(sessionStorage.getItem(options.storageKey) || '{}') } catch { /* unusable cache is ignored */ }
    replace(action)
    for (const [section, item] of Object.entries(saved)) {
      if (!item || !options.values.value[section] || item.version !== options.version(section)) continue
      if (!item.value || typeof item.value !== 'object' || Array.isArray(item.value)) continue
      muted = true
      try { restore(section, item.value) } finally { muted = false }
      baseline[section] = JSON.stringify(options.values.value[section])
      pending.set(section, { value: clone(options.values.value[section]!), version: item.version })
    }
    ready = true
    if (pending.size) { state.value = 'pending'; backup(); schedule() }
  }

  function flush(): Promise<boolean> {
    clearTimeout(timer)
    if (flight) return flight
    if (!pending.size) return Promise.resolve(true)
    flight = (async () => {
      state.value = 'saving'
      error.value = ''
      while (pending.size) {
        const [section, item] = pending.entries().next().value!
        try {
          await options.save(section, clone(item.value))
          // Changes made while this request ran remain queued.
          if (pending.get(section) === item) pending.delete(section)
          backup()
        } catch (cause) {
          error.value = learningErrorMessage(cause, '草稿未保存到服务器，请重试。')
          state.value = 'error'
          backup()
          return false
        }
      }
      state.value = 'saved'
      return true
    })().finally(() => { flight = undefined })
    return flight
  }

  function dispose() { disposed = true; clearTimeout(timer); if (ready) backup() }
  return { state, status, error, backupError, initialize, replace, touch, flush, dispose }
}
