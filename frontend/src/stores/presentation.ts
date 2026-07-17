import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { presentationRepository } from '@/repositories/presentation'
import type { AsyncJob } from '@/types/contracts/common'
import type {
  CreatePresentationRequest,
  PresentationDto,
  PresentationSlideOutline,
  PresentationTemplateDto,
  UpdatePresentationDraftRequest,
} from '@/types/contracts/presentation'

function clientRequestId() {
  return globalThis.crypto?.randomUUID?.() ?? `presentation-${Date.now()}-${Math.random().toString(36).slice(2)}`
}

function wait(ms: number) {
  return new Promise((resolve) => window.setTimeout(resolve, ms))
}

export const usePresentationStore = defineStore('presentation', () => {
  const templates = ref<PresentationTemplateDto[]>([])
  const current = ref<PresentationDto | null>(null)
  const activeJob = ref<AsyncJob<unknown> | null>(null)
  const isLoading = ref(false)
  const isSaving = ref(false)
  const errorMessage = ref('')

  const progress = computed(() => activeJob.value?.progress ?? (current.value?.status === 'ready' ? 100 : 0))

  async function loadTemplates() {
    if (templates.value.length) return templates.value
    templates.value = await presentationRepository.listTemplates()
    return templates.value
  }

  async function load(id: string) {
    isLoading.value = true
    errorMessage.value = ''
    try {
      current.value = await presentationRepository.get(id)
      return current.value
    } catch (error) {
      errorMessage.value = error instanceof Error ? error.message : '获取 PPT 失败'
      throw error
    } finally {
      isLoading.value = false
    }
  }

  async function pollJob<T>(job: AsyncJob<T>, delay = 450) {
    activeJob.value = job as AsyncJob<unknown>
    let next = job
    for (let attempt = 0; ['pending', 'running'].includes(next.status) && attempt < 120; attempt += 1) {
      await wait(delay)
      next = await presentationRepository.getJob<T>(next.jobId)
      activeJob.value = next as AsyncJob<unknown>
    }
    if (next.status !== 'succeeded') throw new Error(next.errorMessage || 'PPT 生成任务失败')
    return next
  }

  async function createDraft(input: Omit<CreatePresentationRequest, 'clientRequestId'>) {
    isSaving.value = true
    errorMessage.value = ''
    try {
      current.value = await presentationRepository.create({ ...input, clientRequestId: clientRequestId() })
      return current.value
    } catch (error) {
      errorMessage.value = error instanceof Error ? error.message : 'PPT 草稿创建失败'
      throw error
    } finally {
      isSaving.value = false
    }
  }

  async function updateDraft(input: Omit<UpdatePresentationDraftRequest, 'clientRequestId'>) {
    if (!current.value) throw new Error('PPT 草稿不存在')
    isSaving.value = true
    errorMessage.value = ''
    try {
      current.value = await presentationRepository.updateDraft(current.value.id, {
        ...input,
        clientRequestId: clientRequestId(),
      })
      return current.value
    } catch (error) {
      errorMessage.value = error instanceof Error ? error.message : 'PPT 草稿保存失败'
      throw error
    } finally {
      isSaving.value = false
    }
  }

  async function generateOutline() {
    if (!current.value) throw new Error('PPT 草稿不存在')
    isSaving.value = true
    errorMessage.value = ''
    try {
      const job = await presentationRepository.startOutlineGeneration(current.value.id, clientRequestId())
      await pollJob(job)
      current.value = await presentationRepository.get(current.value.id)
      return current.value
    } catch (error) {
      errorMessage.value = error instanceof Error ? error.message : 'PPT 大纲生成失败'
      throw error
    } finally {
      isSaving.value = false
    }
  }

  async function createAndGenerateOutline(input: Omit<CreatePresentationRequest, 'clientRequestId'>) {
    await createDraft(input)
    return generateOutline()
  }

  async function saveOutline(slides: PresentationSlideOutline[]) {
    if (!current.value) throw new Error('PPT 不存在')
    isSaving.value = true
    errorMessage.value = ''
    try {
      current.value = await presentationRepository.updateOutline(current.value.id, {
        slides,
        clientRequestId: clientRequestId(),
      })
      return current.value
    } finally {
      isSaving.value = false
    }
  }

  async function saveSlide(slide: PresentationSlideOutline) {
    if (!current.value) throw new Error('PPT 不存在')
    isSaving.value = true
    errorMessage.value = ''
    try {
      current.value = await presentationRepository.updateSlide(current.value.id, slide.id, {
        slide,
        clientRequestId: clientRequestId(),
      })
      return current.value
    } catch (error) {
      errorMessage.value = error instanceof Error ? error.message : 'PPT 页面保存失败'
      throw error
    } finally {
      isSaving.value = false
    }
  }

  async function generate() {
    if (!current.value) throw new Error('PPT 不存在')
    isSaving.value = true
    errorMessage.value = ''
    try {
      const job = await presentationRepository.startGeneration(current.value.id, { clientRequestId: clientRequestId() })
      current.value = await presentationRepository.get(current.value.id)
      await pollJob(job, 650)
      current.value = await presentationRepository.get(current.value.id)
      return current.value
    } catch (error) {
      errorMessage.value = error instanceof Error ? error.message : 'PPT 生成失败'
      if (current.value) {
        try {
          current.value = await presentationRepository.get(current.value.id)
        } catch {
          // Keep the current failure state when refresh also fails.
        }
      }
      throw error
    } finally {
      isSaving.value = false
    }
  }

  async function resumeActiveJob() {
    if (!current.value?.activeJobId) return current.value
    const status = current.value.status
    const job = await presentationRepository.getJob<unknown>(current.value.activeJobId)
    await pollJob(job, status === 'outlining' ? 450 : 650)
    current.value = await presentationRepository.get(current.value.id)
    return current.value
  }

  async function retry() {
    if (!current.value) throw new Error('PPT 不存在')
    isSaving.value = true
    errorMessage.value = ''
    try {
      const job = await presentationRepository.retryGeneration(current.value.id, { clientRequestId: clientRequestId() })
      await pollJob(job, 650)
      current.value = await presentationRepository.get(current.value.id)
      return current.value
    } catch (error) {
      errorMessage.value = error instanceof Error ? error.message : 'PPT 重试失败'
      throw error
    } finally {
      isSaving.value = false
    }
  }

  async function cancel() {
    if (!activeJob.value || !['pending', 'running'].includes(activeJob.value.status)) return
    await presentationRepository.cancelJob(activeJob.value.jobId)
    activeJob.value = { ...activeJob.value, status: 'cancelled' }
    if (current.value) current.value = await presentationRepository.get(current.value.id)
  }

  function download() {
    if (!current.value) throw new Error('PPT 不存在')
    return presentationRepository.download(current.value.id)
  }

  function clearError() {
    errorMessage.value = ''
  }

  return {
    templates,
    current,
    activeJob,
    isLoading,
    isSaving,
    errorMessage,
    progress,
    loadTemplates,
    load,
    createDraft,
    updateDraft,
    generateOutline,
    createAndGenerateOutline,
    saveOutline,
    saveSlide,
    generate,
    resumeActiveJob,
    retry,
    cancel,
    download,
    clearError,
  }
})
