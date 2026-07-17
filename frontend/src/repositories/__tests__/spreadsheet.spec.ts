import { beforeEach, describe, expect, it } from 'vitest'
import { USER_KEY } from '@/api/request'
import { libraryResourceRepository } from '@/repositories/libraryResource'
import { spreadsheetRepository } from '@/repositories/spreadsheet'
import type { AsyncJob } from '@/types/contracts/common'

async function completeJob<T>(initial: AsyncJob<T>) {
  let job = initial
  for (let attempt = 0; ['pending', 'running'].includes(job.status) && attempt < 5; attempt += 1) {
    job = await spreadsheetRepository.getJob<T>(job.jobId)
  }
  return job
}

describe('MockSpreadsheetRepository', () => {
  beforeEach(() => {
    sessionStorage.clear()
    localStorage.clear()
    sessionStorage.setItem(USER_KEY, JSON.stringify({ id: 401 }))
  })

  it('generates a workbook directly from conversation context and archives the XLSX', async () => {
    const created = await spreadsheetRepository.create({
      prompt: '请根据课程资料生成课程进度电子表格，包括课程、负责人、进度、状态，并提供明细和汇总工作表。',
      conversationId: 12,
      knowledgeBaseId: 3,
      projectId: 8,
      mediaAssetIds: ['media-1'],
      clientRequestId: 'spreadsheet-create-1',
    })

    expect(created.status).toBe('generating')
    expect(created.activeJobId).toBeTruthy()
    await completeJob(await spreadsheetRepository.getJob(created.activeJobId!))

    const generated = await spreadsheetRepository.get(created.id)
    expect(generated.status).toBe('ready')
    expect(generated.resourceId).toBe(`spreadsheet:${created.id}`)
    expect(generated.workbook.sheets).toHaveLength(2)
    expect(generated.workbook.sheets[0]!.columns).toEqual(expect.arrayContaining(['课程', '负责人', '进度', '状态']))
    expect(libraryResourceRepository.initial().some((item) => item.resourceId === generated.resourceId)).toBe(true)

    const blob = await spreadsheetRepository.download(created.id)
    expect(blob.type).toBe('application/vnd.openxmlformats-officedocument.spreadsheetml.sheet')
    expect(blob.size).toBeGreaterThan(0)
  }, 30_000)
})
