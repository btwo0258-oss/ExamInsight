import { request } from '@/api/request'
import { isMockDataSource } from '@/config/dataSource'
import { mockSession } from '@/mock/storage'
import type { AsyncJob } from '@/types/contracts/common'
import type {
  CreatePresentationRequest,
  PresentationDto,
  PresentationGenerationJob,
  PresentationOutlineJob,
  PresentationPreviewPage,
  PresentationSlideOutline,
  PresentationTemplateDto,
  StartPresentationGenerationRequest,
  UpdatePresentationDraftRequest,
  UpdatePresentationOutlineRequest,
  UpdatePresentationSlideRequest,
} from '@/types/contracts/presentation'

export interface PresentationRepository {
  listTemplates(): Promise<PresentationTemplateDto[]>
  list(): Promise<PresentationDto[]>
  get(id: string): Promise<PresentationDto>
  create(input: CreatePresentationRequest): Promise<PresentationDto>
  updateDraft(id: string, input: UpdatePresentationDraftRequest): Promise<PresentationDto>
  startOutlineGeneration(id: string, clientRequestId: string): Promise<PresentationOutlineJob>
  updateOutline(id: string, input: UpdatePresentationOutlineRequest): Promise<PresentationDto>
  updateSlide(id: string, slideId: string, input: UpdatePresentationSlideRequest): Promise<PresentationDto>
  startGeneration(id: string, input: StartPresentationGenerationRequest): Promise<PresentationGenerationJob>
  getJob<T>(jobId: string): Promise<AsyncJob<T>>
  cancelJob(jobId: string): Promise<void>
  retryGeneration(id: string, input: StartPresentationGenerationRequest): Promise<PresentationGenerationJob>
  download(id: string): Promise<Blob>
}

type StoredJob = AsyncJob<unknown> & {
  kind: 'outline' | 'presentation'
  presentationId: string
  pollCount: number
}

const PRESENTATION_DOMAIN = 'presentations'
const JOB_DOMAIN = 'presentation-jobs'

const templates: PresentationTemplateDto[] = [
  {
    id: 'ink-focus',
    name: '清晰讲解',
    description: '白底、深色正文与蓝色重点，适合课程和知识分享。',
    style: 'academic',
    backgroundColor: '#F8FAFC',
    surfaceColor: '#FFFFFF',
    textColor: '#172033',
    accentColor: '#2563EB',
  },
  {
    id: 'classroom',
    name: '课堂板书',
    description: '深绿、米白和金色标记，适合复习与教学演示。',
    style: 'academic',
    backgroundColor: '#173F35',
    surfaceColor: '#F7F4EA',
    textColor: '#F7F4EA',
    accentColor: '#F2B84B',
  },
  {
    id: 'signal',
    name: '重点信号',
    description: '高对比深色页面，使用珊瑚色和青色强调重点。',
    style: 'vibrant',
    backgroundColor: '#1E2228',
    surfaceColor: '#292F37',
    textColor: '#F7F8FA',
    accentColor: '#FF6B5F',
  },
  {
    id: 'briefing',
    name: '专业汇报',
    description: '克制的灰白页面和红色标记，适合答辩与工作汇报。',
    style: 'professional',
    backgroundColor: '#F4F3F1',
    surfaceColor: '#FFFFFF',
    textColor: '#202326',
    accentColor: '#B4232D',
  },
]

function now() {
  return new Date().toISOString()
}

function id(prefix: string) {
  return globalThis.crypto?.randomUUID?.() ?? `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2)}`
}

function readPresentations() {
  return mockSession.get<PresentationDto[]>(PRESENTATION_DOMAIN, [])
}

function savePresentations(items: PresentationDto[]) {
  mockSession.set(PRESENTATION_DOMAIN, items)
}

function readJobs() {
  return mockSession.get<Record<string, StoredJob>>(JOB_DOMAIN, {})
}

function saveJobs(jobs: Record<string, StoredJob>) {
  mockSession.set(JOB_DOMAIN, jobs)
}

function getMockPresentation(presentationId: string) {
  const item = readPresentations().find((presentation) => presentation.id === presentationId)
  if (!item) throw new Error('PPT 不存在')
  return item
}

function updateMockPresentation(presentation: PresentationDto) {
  const items = readPresentations()
  const index = items.findIndex((item) => item.id === presentation.id)
  if (index === -1) items.unshift(presentation)
  else items[index] = presentation
  savePresentations(items)
  return presentation
}

function outlineFor(topic: string, pageCount: number): PresentationSlideOutline[] {
  const middleSections = ['背景与目标', '核心概念', '关键方法', '案例解析', '常见误区', '实践建议', '进阶方向']
  return Array.from({ length: pageCount }, (_, index) => {
    const order = index + 1
    if (index === 0) {
      return {
        id: `slide-${order}`,
        order,
        title: topic,
        points: ['围绕主题建立清晰、可讲解的内容结构'],
        speakerNotes: `介绍“${topic}”及本次分享目标。`,
        layout: 'cover',
      }
    }
    if (index === pageCount - 1) {
      return {
        id: `slide-${order}`,
        order,
        title: '总结与下一步',
        points: [`回顾${topic}的核心结论`, '给出可执行的复习或实践步骤', '保留提问与讨论时间'],
        speakerNotes: '收束重点，并引导听众把结论用于后续行动。',
        layout: 'summary',
      }
    }
    const section = middleSections[(index - 1) % middleSections.length]
    return {
      id: `slide-${order}`,
      order,
      title: `${section}：${topic}`,
      points: [`说明${section}与主题的关系`, '提炼 2 至 3 个需要记住的重点', '使用示例或对比降低理解成本'],
      speakerNotes: `围绕“${section}”展开讲解，避免只朗读页面文字。`,
      layout: index % 3 === 0 ? 'comparison' : 'content',
    }
  })
}

function previewFor(presentation: PresentationDto): PresentationPreviewPage[] {
  const template = templates.find((item) => item.id === presentation.config.templateId) ?? templates[0]!
  return presentation.outline.map((slide) => ({
    ...slide,
    backgroundColor: template.backgroundColor,
    surfaceColor: template.surfaceColor,
    textColor: template.textColor,
    accentColor: template.accentColor,
  }))
}

function publicJob<T>(job: StoredJob) {
  return {
    jobId: job.jobId,
    status: job.status,
    progress: job.progress,
    result: job.result as T | undefined,
    errorCode: job.errorCode,
    errorMessage: job.errorMessage,
  } satisfies AsyncJob<T>
}

function createJob(kind: StoredJob['kind'], presentationId: string) {
  const jobs = readJobs()
  const job: StoredJob = {
    jobId: id(`mock-presentation-${kind}`),
    kind,
    presentationId,
    pollCount: 0,
    status: 'pending',
    progress: 8,
  }
  jobs[job.jobId] = job
  saveJobs(jobs)
  return job
}

function completeOutlineJob(job: StoredJob) {
  const presentation = getMockPresentation(job.presentationId)
  presentation.outline = outlineFor(presentation.config.topic, presentation.config.pageCount)
  presentation.status = 'outline_ready'
  presentation.activeJobId = undefined
  presentation.updatedAt = now()
  updateMockPresentation(presentation)
  job.status = 'succeeded'
  job.progress = 100
  job.result = { presentationId: presentation.id, outline: presentation.outline }
}

function completePresentationJob(job: StoredJob) {
  const presentation = getMockPresentation(job.presentationId)
  presentation.previewPages = previewFor(presentation)
  presentation.status = 'ready'
  presentation.activeJobId = undefined
  presentation.fileName = `${presentation.config.title.trim() || presentation.config.topic.trim() || '演示文稿'}.pptx`
  presentation.resourceId = `presentation:${presentation.id}`
  presentation.errorCode = undefined
  presentation.errorMessage = undefined
  presentation.updatedAt = now()
  updateMockPresentation(presentation)
  job.status = 'succeeded'
  job.progress = 100
  job.result = { presentationId: presentation.id }
}

function advanceMockJob(jobId: string) {
  const jobs = readJobs()
  const job = jobs[jobId]
  if (!job) throw new Error('PPT 生成任务不存在')
  if (!['pending', 'running'].includes(job.status)) return job

  job.pollCount += 1
  job.status = 'running'
  if (job.kind === 'outline') {
    job.progress = job.pollCount === 1 ? 46 : 100
    if (job.pollCount >= 2) completeOutlineJob(job)
  } else {
    job.progress = job.pollCount === 1 ? 32 : job.pollCount === 2 ? 72 : 100
    if (job.pollCount >= 3) completePresentationJob(job)
  }
  jobs[jobId] = job
  saveJobs(jobs)
  return job
}

async function buildPptx(presentation: PresentationDto) {
  const { default: PptxGenJS } = await import('pptxgenjs')
  const pptx = new PptxGenJS()
  pptx.layout = presentation.config.aspectRatio === '4:3' ? 'LAYOUT_4x3' : 'LAYOUT_WIDE'
  pptx.author = 'ExamInsight'
  pptx.company = 'ExamInsight'
  pptx.subject = presentation.config.topic
  pptx.title = presentation.config.title
  pptx.theme = {
    headFontFace: 'Microsoft YaHei',
    bodyFontFace: 'Microsoft YaHei',
  }

  const width = presentation.config.aspectRatio === '4:3' ? 10 : 13.333
  const height = 7.5
  presentation.previewPages.forEach((page) => {
    const slide = pptx.addSlide()
    slide.background = { color: page.backgroundColor.replace('#', '') }
    slide.addShape(pptx.ShapeType.rect, {
      x: 0,
      y: 0,
      w: 0.18,
      h: height,
      line: { color: page.accentColor.replace('#', ''), transparency: 100 },
      fill: { color: page.accentColor.replace('#', '') },
    })
    if (page.layout === 'cover') {
      slide.addText(page.title, {
        x: 0.9,
        y: 2.15,
        w: width - 1.8,
        h: 1.4,
        fontFace: 'Microsoft YaHei',
        fontSize: 30,
        bold: true,
        align: 'center',
        color: page.textColor.replace('#', ''),
        margin: 0,
        breakLine: false,
      })
      slide.addText(page.points[0] ?? presentation.config.topic, {
        x: 1.4,
        y: 3.8,
        w: width - 2.8,
        h: 0.7,
        fontFace: 'Microsoft YaHei',
        fontSize: 15,
        align: 'center',
        color: page.accentColor.replace('#', ''),
        margin: 0,
      })
    } else {
      slide.addText(page.title, {
        x: 0.72,
        y: 0.55,
        w: width - 1.44,
        h: 0.72,
        fontFace: 'Microsoft YaHei',
        fontSize: 23,
        bold: true,
        color: page.textColor.replace('#', ''),
        margin: 0,
      })
      slide.addShape(pptx.ShapeType.line, {
        x: 0.72,
        y: 1.45,
        w: 1.2,
        h: 0,
        line: { color: page.accentColor.replace('#', ''), width: 3 },
      })
      page.points.slice(0, 6).forEach((point, index) => {
        slide.addText(`• ${point}`, {
          x: 1,
          y: 2 + index * 0.72,
          w: width - 2,
          h: 0.52,
          fontFace: 'Microsoft YaHei',
          fontSize: 16,
          color: page.textColor.replace('#', ''),
          margin: 0,
          breakLine: false,
        })
      })
    }
    if (page.speakerNotes) slide.addNotes(page.speakerNotes)
  })

  const output = await pptx.write({ outputType: 'blob', compression: true })
  return new Blob([output as BlobPart], {
    type: 'application/vnd.openxmlformats-officedocument.presentationml.presentation',
  })
}

const mockPresentationRepository: PresentationRepository = {
  async listTemplates() {
    return structuredClone(templates)
  },
  async list() {
    return structuredClone(readPresentations())
  },
  async get(presentationId) {
    return structuredClone(getMockPresentation(presentationId))
  },
  async create(input) {
    const timestamp = now()
    const presentation: PresentationDto = {
      id: id('mock-presentation'),
      status: 'draft',
      config: {
        topic: input.topic,
        title: input.title,
        pageCount: input.pageCount,
        templateId: input.templateId,
        aspectRatio: input.aspectRatio,
        style: input.style,
        audience: input.audience,
        language: input.language,
        sourceText: input.sourceText,
        sourceFileNames: input.sourceFileNames ? [...input.sourceFileNames] : undefined,
        mediaAssetIds: input.mediaAssetIds ? [...input.mediaAssetIds] : undefined,
      },
      outline: [],
      previewPages: [],
      conversationId: input.conversationId ?? null,
      sourceMessageId: input.sourceMessageId ?? null,
      knowledgeBaseId: input.knowledgeBaseId ?? null,
      projectId: input.projectId ?? null,
      learningResourceId: input.learningResourceId ?? null,
      createdAt: timestamp,
      updatedAt: timestamp,
    }
    updateMockPresentation(presentation)
    return structuredClone(presentation)
  },
  async updateDraft(presentationId, input) {
    const presentation = getMockPresentation(presentationId)
    if (!['draft', 'outline_ready', 'cancelled'].includes(presentation.status)) {
      throw new Error('当前 PPT 状态不能修改配置')
    }
    presentation.config = {
      ...input.config,
      sourceFileNames: input.config.sourceFileNames ? [...input.config.sourceFileNames] : undefined,
      mediaAssetIds: input.config.mediaAssetIds ? [...input.config.mediaAssetIds] : undefined,
    }
    presentation.conversationId = input.conversationId ?? presentation.conversationId ?? null
    presentation.sourceMessageId = input.sourceMessageId ?? presentation.sourceMessageId ?? null
    presentation.knowledgeBaseId = input.knowledgeBaseId ?? null
    presentation.projectId = input.projectId ?? null
    presentation.learningResourceId = input.learningResourceId ?? presentation.learningResourceId ?? null
    presentation.updatedAt = now()
    return structuredClone(updateMockPresentation(presentation))
  },
  async startOutlineGeneration(presentationId) {
    const presentation = getMockPresentation(presentationId)
    const job = createJob('outline', presentation.id)
    presentation.status = 'outlining'
    presentation.activeJobId = job.jobId
    presentation.updatedAt = now()
    updateMockPresentation(presentation)
    return publicJob<{ presentationId: string; outline: PresentationSlideOutline[] }>(job)
  },
  async updateOutline(presentationId, input) {
    const presentation = getMockPresentation(presentationId)
    presentation.outline = input.slides.map((slide, index) => ({
      ...slide,
      order: index + 1,
      points: [...slide.points],
    }))
    presentation.config.pageCount = presentation.outline.length
    presentation.status = 'outline_ready'
    presentation.updatedAt = now()
    return structuredClone(updateMockPresentation(presentation))
  },
  async updateSlide(presentationId, slideId, input) {
    const presentation = getMockPresentation(presentationId)
    if (presentation.status !== 'ready') throw new Error('PPT 尚未生成完成')
    const outlineIndex = presentation.outline.findIndex((slide) => slide.id === slideId)
    const previewIndex = presentation.previewPages.findIndex((slide) => slide.id === slideId)
    const previewPage = presentation.previewPages[previewIndex]
    if (outlineIndex === -1 || !previewPage) throw new Error('PPT 页面不存在')
    const slide = {
      ...input.slide,
      id: slideId,
      order: outlineIndex + 1,
      title: input.slide.title.trim(),
      points: input.slide.points.map((point) => point.trim()).filter(Boolean),
      speakerNotes: input.slide.speakerNotes?.trim() || undefined,
    }
    presentation.outline[outlineIndex] = slide
    presentation.previewPages[previewIndex] = {
      ...previewPage,
      ...slide,
    }
    presentation.updatedAt = now()
    return structuredClone(updateMockPresentation(presentation))
  },
  async startGeneration(presentationId) {
    const presentation = getMockPresentation(presentationId)
    if (!presentation.outline.length) throw new Error('请先生成并确认 PPT 大纲')
    const job = createJob('presentation', presentation.id)
    presentation.status = 'generating'
    presentation.activeJobId = job.jobId
    presentation.errorCode = undefined
    presentation.errorMessage = undefined
    presentation.updatedAt = now()
    updateMockPresentation(presentation)
    return publicJob<{ presentationId: string }>(job)
  },
  async getJob<T>(jobId: string) {
    return publicJob<T>(advanceMockJob(jobId))
  },
  async cancelJob(jobId) {
    const jobs = readJobs()
    const job = jobs[jobId]
    if (!job) return
    job.status = 'cancelled'
    jobs[jobId] = job
    saveJobs(jobs)
    const presentation = getMockPresentation(job.presentationId)
    presentation.status = 'cancelled'
    presentation.activeJobId = undefined
    presentation.updatedAt = now()
    updateMockPresentation(presentation)
  },
  async retryGeneration(presentationId) {
    return this.startGeneration(presentationId, { clientRequestId: id('retry') })
  },
  async download(presentationId) {
    const presentation = getMockPresentation(presentationId)
    if (presentation.status !== 'ready') throw new Error('PPT 尚未生成完成')
    const blob = await buildPptx(presentation)
    presentation.fileSize = blob.size
    presentation.updatedAt = now()
    updateMockPresentation(presentation)
    return blob
  },
}

function unwrap<T>(response: { data: unknown }): T {
  const payload = response.data as { data?: T }
  return (payload?.data ?? response.data) as T
}

const apiPresentationRepository: PresentationRepository = {
  async listTemplates() {
    return unwrap<PresentationTemplateDto[]>(await request.get('/api/presentations/templates'))
  },
  async list() {
    return unwrap<PresentationDto[]>(await request.get('/api/presentations'))
  },
  async get(id) {
    return unwrap<PresentationDto>(await request.get(`/api/presentations/${id}`))
  },
  async create(input) {
    return unwrap<PresentationDto>(await request.post('/api/presentations', input))
  },
  async updateDraft(id, input) {
    return unwrap<PresentationDto>(await request.put(`/api/presentations/${id}/draft`, input))
  },
  async startOutlineGeneration(id, clientRequestId) {
    return unwrap<PresentationOutlineJob>(await request.post(`/api/presentations/${id}/outline-jobs`, { clientRequestId }))
  },
  async updateOutline(id, input) {
    return unwrap<PresentationDto>(await request.put(`/api/presentations/${id}/outline`, input))
  },
  async updateSlide(id, slideId, input) {
    return unwrap<PresentationDto>(await request.put(`/api/presentations/${id}/slides/${slideId}`, input))
  },
  async startGeneration(id, input) {
    return unwrap<PresentationGenerationJob>(await request.post(`/api/presentations/${id}/generation-jobs`, input))
  },
  async getJob<T>(jobId: string) {
    return unwrap<AsyncJob<T>>(await request.get(`/api/presentations/jobs/${jobId}`))
  },
  async cancelJob(jobId) {
    await request.post(`/api/presentations/jobs/${jobId}/cancel`)
  },
  async retryGeneration(id, input) {
    return unwrap<PresentationGenerationJob>(await request.post(`/api/presentations/${id}/generation-jobs`, input))
  },
  async download(id) {
    const response = await request.get(`/api/presentations/${id}/download`, { responseType: 'blob' })
    return response.data as Blob
  },
}

export const presentationRepository = isMockDataSource ? mockPresentationRepository : apiPresentationRepository
