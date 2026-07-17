import { request } from '@/api/request'
import { isMockDataSource } from '@/config/dataSource'
import { mockSession } from '@/mock/storage'
import { libraryResourceRepository } from '@/repositories/libraryResource'
import type { AsyncJob } from '@/types/contracts/common'
import type { LibraryResourceDto } from '@/types/contracts/library'
import type {
  CreateSpreadsheetRequest,
  SpreadsheetDto,
  SpreadsheetGenerationJob,
  SpreadsheetSheetDraft,
} from '@/types/contracts/spreadsheet'

export interface SpreadsheetRepository {
  list(): Promise<SpreadsheetDto[]>
  get(id: string): Promise<SpreadsheetDto>
  create(input: CreateSpreadsheetRequest): Promise<SpreadsheetDto>
  getJob<T>(jobId: string): Promise<AsyncJob<T>>
  cancelJob(jobId: string): Promise<void>
  retryGeneration(id: string, clientRequestId: string): Promise<SpreadsheetGenerationJob>
  download(id: string): Promise<Blob>
}

type StoredSpreadsheetJob = AsyncJob<{ spreadsheetId: string }> & {
  spreadsheetId: string
  pollCount: number
}

const SPREADSHEET_DOMAIN = 'spreadsheets.v2'
const JOB_DOMAIN = 'spreadsheet-jobs.v2'
const REQUEST_DOMAIN = 'spreadsheet-requests.v2'

function now() {
  return new Date().toISOString()
}

function uid(prefix: string) {
  return globalThis.crypto?.randomUUID?.() ?? `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2)}`
}

function readSpreadsheets() {
  return mockSession.get<SpreadsheetDto[]>(SPREADSHEET_DOMAIN, [])
}

function saveSpreadsheets(items: SpreadsheetDto[]) {
  mockSession.set(SPREADSHEET_DOMAIN, items)
}

function readJobs() {
  return mockSession.get<Record<string, StoredSpreadsheetJob>>(JOB_DOMAIN, {})
}

function saveJobs(jobs: Record<string, StoredSpreadsheetJob>) {
  mockSession.set(JOB_DOMAIN, jobs)
}

function readRequests() {
  return mockSession.get<Record<string, string>>(REQUEST_DOMAIN, {})
}

function getSpreadsheet(spreadsheetId: string) {
  const spreadsheet = readSpreadsheets().find((item) => item.id === spreadsheetId)
  if (!spreadsheet) throw new Error('电子表格不存在')
  return spreadsheet
}

function saveSpreadsheet(spreadsheet: SpreadsheetDto) {
  const items = readSpreadsheets()
  const index = items.findIndex((item) => item.id === spreadsheet.id)
  if (index === -1) items.unshift(spreadsheet)
  else items[index] = spreadsheet
  saveSpreadsheets(items)
  return spreadsheet
}

function userRequirement(prompt: string) {
  const marker = '[用户输入]'
  const markerIndex = prompt.lastIndexOf(marker)
  return (markerIndex === -1 ? prompt : prompt.slice(markerIndex + marker.length)).trim()
}

function inferTopic(prompt: string) {
  const requirement = userRequirement(prompt)
  const about = requirement.match(/(?:关于|主题(?:是|为)?)[：:\s]*([^，。！？\n]{2,80})/i)?.[1]
  if (about) return about.replace(/的?\s*(?:excel|xlsx|电子表格|数据表|表格).*$/i, '').trim()
  const simplified = requirement
    .replace(/(?:请|帮我|麻烦|根据|生成|制作|创建|整理|转换|转成|做一份|做个|一份|一个)/g, ' ')
    .replace(/(?:excel|xlsx|电子表格|数据表|表格)/gi, ' ')
    .replace(/[：:，。！？]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
  return simplified.slice(0, 60) || 'AI 生成电子表格'
}

function inferSheetCount(prompt: string) {
  const explicit = userRequirement(prompt).match(/([1-5])\s*(?:个)?工作表/)?.[1]
  if (explicit) return Number(explicit)
  return /(?:明细|详情)/.test(prompt) && /(?:汇总|统计)/.test(prompt) ? 2 : 1
}

function inferColumns(prompt: string) {
  const requirement = userRequirement(prompt)
  const raw = requirement.match(/(?:字段|列|包含|包括)(?:为|是)?[：:\s]*([^。；;\n]{2,160})/)?.[1]
  const columns = raw
    ?.split(/[，,、/|]/)
    .map((item) => item.replace(/(?:等|以及|并且).*$/, '').trim())
    .filter((item) => item.length > 0 && item.length <= 18)
    .slice(0, 12)
  return columns && columns.length >= 2 ? columns : ['项目', '说明', '数值', '状态']
}

function sampleValue(column: string, rowIndex: number, topic: string) {
  if (/(?:日期|时间)/.test(column)) return `2026-07-${String(rowIndex + 1).padStart(2, '0')}`
  if (/(?:金额|价格|费用|数值|数量|成绩|分数|进度|比例)/.test(column)) return (rowIndex + 1) * 10
  if (/(?:状态|结果)/.test(column)) return rowIndex % 3 === 0 ? '重点' : '正常'
  if (/(?:姓名|人员|负责人)/.test(column)) return `示例人员 ${rowIndex + 1}`
  if (/(?:编号|序号|ID)/i.test(column)) return rowIndex + 1
  if (/(?:说明|备注|描述)/.test(column)) return 'Mock 根据对话要求生成的预览数据'
  return `${topic} ${rowIndex + 1}`
}

function createSheet(topic: string, prompt: string, index: number): SpreadsheetSheetDraft {
  if (index > 0) {
    return {
      sheetId: `sheet-${index + 1}`,
      name: `${topic.slice(0, 24)}汇总`.slice(0, 31),
      columns: ['指标', '结果', '说明'],
      rows: [
        ['记录数', 8, '根据明细自动汇总'],
        ['数据来源', '对话上下文', '包含已选知识库、项目和附件'],
      ],
    }
  }
  const columns = inferColumns(prompt)
  return {
    sheetId: 'sheet-1',
    name: `${topic.slice(0, 24)}明细`.slice(0, 31),
    columns,
    rows: Array.from({ length: 8 }, (_, rowIndex) => (
      columns.map((column) => sampleValue(column, rowIndex, topic))
    )),
  }
}

function createWorkbook(spreadsheet: SpreadsheetDto) {
  return {
    sheets: Array.from(
      { length: spreadsheet.config.sheetCount },
      (_, index) => createSheet(spreadsheet.config.topic, spreadsheet.config.requirements || '', index),
    ),
  }
}

function createJob(spreadsheetId: string) {
  const jobs = readJobs()
  const job: StoredSpreadsheetJob = {
    jobId: uid('mock-spreadsheet-job'),
    spreadsheetId,
    pollCount: 0,
    status: 'pending',
    progress: 8,
  }
  jobs[job.jobId] = job
  saveJobs(jobs)
  return job
}

function publicJob<T>(job: StoredSpreadsheetJob) {
  return {
    jobId: job.jobId,
    status: job.status,
    progress: job.progress,
    result: job.result as T | undefined,
    errorCode: job.errorCode,
    errorMessage: job.errorMessage,
  } satisfies AsyncJob<T>
}

function archiveMockSpreadsheet(spreadsheet: SpreadsheetDto) {
  if (!spreadsheet.resourceId || spreadsheet.status !== 'ready') return
  const resources = libraryResourceRepository.initial()
  const externalKey = `spreadsheet:${spreadsheet.id}`
  const resource: LibraryResourceDto = {
    resourceId: spreadsheet.resourceId,
    externalKey,
    name: spreadsheet.fileName || `${spreadsheet.config.title}.xlsx`,
    format: 'Excel',
    fileType: 'spreadsheet',
    mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    sizeBytes: spreadsheet.fileSize ?? 0,
    status: 'ready',
    updatedAt: spreadsheet.updatedAt,
    sourceType: 'generated',
    origin: 'spreadsheet',
    projectId: spreadsheet.projectId == null ? null : Number(spreadsheet.projectId),
    knowledgeBaseId: spreadsheet.knowledgeBaseId == null ? null : Number(spreadsheet.knowledgeBaseId),
  }
  const index = resources.findIndex((item) => item.externalKey === externalKey || item.resourceId === resource.resourceId)
  if (index === -1) resources.unshift(resource)
  else resources[index] = resource
  libraryResourceRepository.saveMock(resources)
}

function completeGeneration(job: StoredSpreadsheetJob) {
  const spreadsheet = getSpreadsheet(job.spreadsheetId)
  spreadsheet.workbook = createWorkbook(spreadsheet)
  spreadsheet.status = 'ready'
  spreadsheet.activeJobId = undefined
  spreadsheet.fileName = `${spreadsheet.config.title.trim() || spreadsheet.config.topic.trim() || '电子表格'}.xlsx`
  spreadsheet.resourceId = `spreadsheet:${spreadsheet.id}`
  spreadsheet.errorCode = undefined
  spreadsheet.errorMessage = undefined
  spreadsheet.updatedAt = now()
  saveSpreadsheet(spreadsheet)
  archiveMockSpreadsheet(spreadsheet)
  job.status = 'succeeded'
  job.progress = 100
  job.result = { spreadsheetId: spreadsheet.id }
}

function advanceJob(jobId: string) {
  const jobs = readJobs()
  const job = jobs[jobId]
  if (!job) throw new Error('电子表格生成任务不存在')
  if (!['pending', 'running'].includes(job.status)) return job
  job.pollCount += 1
  job.status = 'running'
  job.progress = job.pollCount === 1 ? 48 : 100
  if (job.pollCount >= 2) completeGeneration(job)
  jobs[jobId] = job
  saveJobs(jobs)
  return job
}

async function buildXlsx(spreadsheet: SpreadsheetDto) {
  const { default: ExcelJS } = await import('exceljs')
  const workbook = new ExcelJS.Workbook()
  workbook.creator = 'ExamInsight'
  workbook.title = spreadsheet.config.title
  workbook.subject = spreadsheet.config.topic
  spreadsheet.workbook.sheets.forEach((sheetDraft) => {
    const sheet = workbook.addWorksheet(sheetDraft.name.slice(0, 31))
    sheet.addRow(sheetDraft.columns)
    sheetDraft.rows.forEach((row) => sheet.addRow(row))
    const header = sheet.getRow(1)
    header.font = { bold: true, color: { argb: 'FFFFFFFF' } }
    header.fill = { type: 'pattern', pattern: 'solid', fgColor: { argb: 'FF303238' } }
    header.alignment = { vertical: 'middle' }
    sheet.views = [{ state: 'frozen', ySplit: 1 }]
    sheet.columns.forEach((column, index) => {
      const values = [sheetDraft.columns[index], ...sheetDraft.rows.map((row) => row[index])]
      column.width = Math.min(36, Math.max(12, ...values.map((value) => String(value ?? '').length + 2)))
    })
  })
  const buffer = await workbook.xlsx.writeBuffer()
  return new Blob([buffer as BlobPart], {
    type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
  })
}

const mockSpreadsheetRepository: SpreadsheetRepository = {
  async list() {
    return structuredClone(readSpreadsheets())
  },
  async get(id) {
    return structuredClone(getSpreadsheet(id))
  },
  async create(input) {
    const requestMap = readRequests()
    const existingId = requestMap[input.clientRequestId]
    if (existingId) return structuredClone(getSpreadsheet(existingId))

    const timestamp = now()
    const topic = inferTopic(input.prompt)
    const spreadsheet: SpreadsheetDto = {
      id: uid('mock-spreadsheet'),
      status: 'generating',
      config: {
        topic,
        title: topic,
        sheetCount: inferSheetCount(input.prompt),
        language: 'zh-CN',
        requirements: input.prompt,
      },
      workbook: { sheets: [] },
      conversationId: input.conversationId ?? null,
      sourceMessageId: input.sourceMessageId ?? null,
      knowledgeBaseId: input.knowledgeBaseId ?? null,
      projectId: input.projectId ?? null,
      createdAt: timestamp,
      updatedAt: timestamp,
    }
    const job = createJob(spreadsheet.id)
    spreadsheet.activeJobId = job.jobId
    saveSpreadsheet(spreadsheet)
    requestMap[input.clientRequestId] = spreadsheet.id
    mockSession.set(REQUEST_DOMAIN, requestMap)
    return structuredClone(spreadsheet)
  },
  async getJob<T>(jobId: string) {
    return publicJob<T>(advanceJob(jobId))
  },
  async cancelJob(jobId) {
    const jobs = readJobs()
    const job = jobs[jobId]
    if (!job) return
    job.status = 'cancelled'
    jobs[jobId] = job
    saveJobs(jobs)
    const spreadsheet = getSpreadsheet(job.spreadsheetId)
    spreadsheet.status = 'cancelled'
    spreadsheet.activeJobId = undefined
    spreadsheet.updatedAt = now()
    saveSpreadsheet(spreadsheet)
  },
  async retryGeneration(id, clientRequestId) {
    const requestMap = readRequests()
    const requestKey = `retry:${id}:${clientRequestId}`
    const existingJobId = requestMap[requestKey]
    if (existingJobId) {
      const existingJob = readJobs()[existingJobId]
      if (existingJob) return publicJob(existingJob)
    }
    const spreadsheet = getSpreadsheet(id)
    const job = createJob(id)
    spreadsheet.status = 'generating'
    spreadsheet.activeJobId = job.jobId
    spreadsheet.errorCode = undefined
    spreadsheet.errorMessage = undefined
    spreadsheet.updatedAt = now()
    saveSpreadsheet(spreadsheet)
    requestMap[requestKey] = job.jobId
    mockSession.set(REQUEST_DOMAIN, requestMap)
    return publicJob(job)
  },
  async download(id) {
    const spreadsheet = getSpreadsheet(id)
    if (spreadsheet.status !== 'ready') throw new Error('电子表格尚未生成完成')
    const blob = await buildXlsx(spreadsheet)
    spreadsheet.fileSize = blob.size
    spreadsheet.updatedAt = now()
    saveSpreadsheet(spreadsheet)
    archiveMockSpreadsheet(spreadsheet)
    return blob
  },
}

function unwrap<T>(response: { data: unknown }): T {
  const payload = response.data as { data?: T }
  return (payload?.data ?? response.data) as T
}

const apiSpreadsheetRepository: SpreadsheetRepository = {
  async list() {
    return unwrap<SpreadsheetDto[]>(await request.get('/api/spreadsheets'))
  },
  async get(id) {
    return unwrap<SpreadsheetDto>(await request.get(`/api/spreadsheets/${id}`))
  },
  async create(input) {
    return unwrap<SpreadsheetDto>(await request.post('/api/spreadsheets/generation-jobs', input))
  },
  async getJob<T>(jobId: string) {
    return unwrap<AsyncJob<T>>(await request.get(`/api/spreadsheets/jobs/${jobId}`))
  },
  async cancelJob(jobId) {
    await request.post(`/api/spreadsheets/jobs/${jobId}/cancel`)
  },
  async retryGeneration(id, clientRequestId) {
    return unwrap<SpreadsheetGenerationJob>(await request.post(`/api/spreadsheets/${id}/generation-jobs`, { clientRequestId }))
  },
  async download(id) {
    const response = await request.get(`/api/spreadsheets/${id}/download`, { responseType: 'blob' })
    return response.data as Blob
  },
}

export const spreadsheetRepository = isMockDataSource ? mockSpreadsheetRepository : apiSpreadsheetRepository
