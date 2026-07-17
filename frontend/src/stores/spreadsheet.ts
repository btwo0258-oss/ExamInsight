import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { spreadsheetRepository } from '@/repositories/spreadsheet'
import type { AsyncJob } from '@/types/contracts/common'
import type { SpreadsheetDto } from '@/types/contracts/spreadsheet'

function clientRequestId() {
  return globalThis.crypto?.randomUUID?.() ?? `spreadsheet-${Date.now()}-${Math.random().toString(36).slice(2)}`
}

function wait(ms: number) {
  return new Promise((resolve) => window.setTimeout(resolve, ms))
}

export const useSpreadsheetStore = defineStore('spreadsheet', () => {
  const current = ref<SpreadsheetDto | null>(null)
  const activeJob = ref<AsyncJob<unknown> | null>(null)
  const isLoading = ref(false)
  const isSaving = ref(false)
  const errorMessage = ref('')

  const progress = computed(() => activeJob.value?.progress ?? (current.value?.status === 'ready' ? 100 : 0))

  async function load(id: string) {
    isLoading.value = true
    errorMessage.value = ''
    try {
      current.value = await spreadsheetRepository.get(id)
      return current.value
    } catch (error) {
      errorMessage.value = error instanceof Error ? error.message : '获取电子表格失败'
      throw error
    } finally {
      isLoading.value = false
    }
  }

  async function pollJob<T>(job: AsyncJob<T>, delay = 600) {
    activeJob.value = job as AsyncJob<unknown>
    let next = job
    for (let attempt = 0; ['pending', 'running'].includes(next.status) && attempt < 120; attempt += 1) {
      await wait(delay)
      next = await spreadsheetRepository.getJob<T>(next.jobId)
      activeJob.value = next as AsyncJob<unknown>
    }
    if (next.status !== 'succeeded') throw new Error(next.errorMessage || '电子表格任务失败')
    return next
  }

  async function retry() {
    if (!current.value) throw new Error('电子表格不存在')
    isSaving.value = true
    errorMessage.value = ''
    try {
      const job = await spreadsheetRepository.retryGeneration(current.value.id, clientRequestId())
      await pollJob(job)
      current.value = await spreadsheetRepository.get(current.value.id)
      return current.value
    } catch (error) {
      errorMessage.value = error instanceof Error ? error.message : '电子表格重试失败'
      throw error
    } finally {
      isSaving.value = false
    }
  }

  async function resumeActiveJob() {
    if (!current.value?.activeJobId) return current.value
    isSaving.value = true
    try {
      const job = await spreadsheetRepository.getJob<{ spreadsheetId: string }>(current.value.activeJobId)
      await pollJob(job)
      current.value = await spreadsheetRepository.get(current.value.id)
      return current.value
    } catch (error) {
      errorMessage.value = error instanceof Error ? error.message : '电子表格任务恢复失败'
      throw error
    } finally {
      isSaving.value = false
    }
  }

  async function cancel() {
    if (!activeJob.value || !['pending', 'running'].includes(activeJob.value.status)) return
    await spreadsheetRepository.cancelJob(activeJob.value.jobId)
    if (current.value) current.value = await spreadsheetRepository.get(current.value.id)
  }

  function download() {
    if (!current.value) throw new Error('电子表格不存在')
    return spreadsheetRepository.download(current.value.id)
  }

  function clearError() {
    errorMessage.value = ''
  }

  return {
    current,
    activeJob,
    isLoading,
    isSaving,
    errorMessage,
    progress,
    load,
    retry,
    resumeActiveJob,
    cancel,
    download,
    clearError,
  }
})
