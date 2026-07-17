import { beforeEach, describe, expect, it } from 'vitest'

import { USER_KEY } from '@/api/request'
import { libraryResourceRepository } from '@/repositories/libraryResource'

describe('MockLibraryResourceRepository', () => {
  beforeEach(() => {
    sessionStorage.clear()
    localStorage.clear()
    sessionStorage.setItem(USER_KEY, JSON.stringify({ id: 101 }))
  })

  it('uses the same CRUD contract as the API repository', async () => {
    const file = new File(['hello'], 'notes.md', { type: 'text/markdown' })
    const uploaded = await libraryResourceRepository.upload(
      file,
      'resource-library',
      { projectId: null, knowledgeBaseId: 1 },
    )
    expect(uploaded.status).toBe('waiting')
    expect(uploaded.knowledgeBaseId).toBe(1)

    const renamed = await libraryResourceRepository.rename(uploaded.resourceId, 'renamed.md')
    expect(renamed.name).toBe('renamed.md')

    const moved = await libraryResourceRepository.updateAssociations(uploaded.resourceId, { projectId: null, knowledgeBaseId: null })
    expect(moved.knowledgeBaseId).toBeNull()

    const resources = await libraryResourceRepository.list()
    const failed = resources.find((item) => item.resourceId === uploaded.resourceId)!
    failed.status = 'failed'
    failed.errorMessage = 'parse failed'
    libraryResourceRepository.saveMock(resources)

    const retried = await libraryResourceRepository.retry(uploaded.resourceId)
    expect(retried.status).toBe('waiting')
    expect(retried.errorMessage).toBeUndefined()

    const blob = await libraryResourceRepository.download(uploaded.resourceId)
    expect(await blob.text()).toContain('renamed.md')

    await libraryResourceRepository.remove(uploaded.resourceId)
    expect((await libraryResourceRepository.list()).some((item) => item.resourceId === uploaded.resourceId)).toBe(false)
  })

  it('uses the unified preview contract and blocks oversized files before reading content', async () => {
    const uploaded = await libraryResourceRepository.upload(
      new File(['# Preview'], 'preview.md', { type: 'text/markdown' }),
      'resource-library',
      { projectId: null, knowledgeBaseId: null },
    )
    const resources = await libraryResourceRepository.list()
    const resource = resources.find((item) => item.resourceId === uploaded.resourceId)!
    resource.status = 'ready'
    libraryResourceRepository.saveMock(resources)

    const preview = await libraryResourceRepository.preview(uploaded.resourceId)
    expect(preview.status).toBe('ready')
    expect(preview.previewKind).toBe('text')
    expect(preview.textContent).toBe('# Preview')

    resource.sizeBytes = 11 * 1024 * 1024
    libraryResourceRepository.saveMock(resources)
    expect((await libraryResourceRepository.preview(uploaded.resourceId)).status).toBe('too_large')
  })
})
