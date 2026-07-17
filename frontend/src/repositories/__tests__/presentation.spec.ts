import { beforeEach, describe, expect, it } from 'vitest'

import { USER_KEY } from '@/api/request'
import { presentationRepository } from '@/repositories/presentation'
import type { AsyncJob } from '@/types/contracts/common'

async function completeJob<T>(initial: AsyncJob<T>) {
  let job = initial
  for (let attempt = 0; ['pending', 'running'].includes(job.status) && attempt < 5; attempt += 1) {
    job = await presentationRepository.getJob<T>(job.jobId)
  }
  return job
}

describe('MockPresentationRepository', () => {
  beforeEach(() => {
    sessionStorage.clear()
    localStorage.clear()
    sessionStorage.setItem(USER_KEY, JSON.stringify({ id: 301 }))
  })

  it('creates an editable outline and generates a resumable presentation', async () => {
    const templates = await presentationRepository.listTemplates()
    const created = await presentationRepository.create({
      topic: 'Java 多态',
      title: 'Java 多态复习',
      pageCount: 6,
      templateId: templates[0]!.id,
      aspectRatio: '16:9',
      style: 'academic',
      audience: 'student',
      language: 'zh-CN',
      projectId: 21,
      learningResourceId: 8,
      knowledgeBaseId: 3,
      clientRequestId: 'create-presentation-1',
    })

    const outlineJob = await presentationRepository.startOutlineGeneration(created.id, 'outline-presentation-1')
    const completedOutline = await completeJob(outlineJob)
    expect(completedOutline.status).toBe('succeeded')

    const outlined = await presentationRepository.get(created.id)
    expect(outlined.status).toBe('outline_ready')
    expect(outlined.outline).toHaveLength(6)

    const editedSlides = structuredClone(outlined.outline)
    editedSlides[1]!.title = '动态绑定与运行时类型'
    await presentationRepository.updateOutline(created.id, {
      slides: editedSlides,
      clientRequestId: 'update-presentation-outline-1',
    })

    const generationJob = await presentationRepository.startGeneration(created.id, {
      clientRequestId: 'generate-presentation-1',
    })
    expect((await presentationRepository.get(created.id)).activeJobId).toBe(generationJob.jobId)
    expect((await completeJob(generationJob)).status).toBe('succeeded')

    const generated = await presentationRepository.get(created.id)
    expect(generated.status).toBe('ready')
    expect(generated.previewPages[1]!.title).toBe('动态绑定与运行时类型')
    expect(generated.fileName).toBe('Java 多态复习.pptx')
  })

  it('archives a ready PPT resource and builds the PPTX only when downloaded', async () => {
    const created = await presentationRepository.create({
      topic: '数据结构复习',
      title: '数据结构复习',
      pageCount: 3,
      templateId: 'ink-focus',
      aspectRatio: '4:3',
      style: 'academic',
      audience: 'student',
      language: 'zh-CN',
      clientRequestId: 'create-presentation-2',
    })
    await completeJob(await presentationRepository.startOutlineGeneration(created.id, 'outline-presentation-2'))
    await completeJob(await presentationRepository.startGeneration(created.id, { clientRequestId: 'generate-presentation-2' }))

    const beforeDownload = await presentationRepository.get(created.id)
    expect(beforeDownload.resourceId).toBe(`presentation:${created.id}`)
    expect(beforeDownload.fileSize).toBeUndefined()
    const blob = await presentationRepository.download(created.id)
    expect(blob.type).toBe('application/vnd.openxmlformats-officedocument.presentationml.presentation')
    expect(blob.size).toBeGreaterThan(0)
    expect((await presentationRepository.get(created.id)).fileSize).toBe(blob.size)
  })
})
