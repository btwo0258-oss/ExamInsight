import { beforeEach, describe, expect, it } from 'vitest'

import { USER_KEY } from '@/api/request'
import { mockSession } from '@/mock/storage'
import { libraryResourceRepository } from '@/repositories/libraryResource'

describe('MockLibraryResourceRepository', () => {
  beforeEach(() => {
    sessionStorage.clear()
    localStorage.clear()
    sessionStorage.setItem(USER_KEY, JSON.stringify({ id: 101 }))
  })

  it('uses the same CRUD contract as the API repository', async () => {
    const file = new File(['hello'], 'notes.md', { type: 'text/markdown' })
    const uploaded = await libraryResourceRepository.upload(file, 1)
    expect(uploaded.status).toBe('waiting')
    expect(uploaded.libraryId).toBe(1)

    const renamed = await libraryResourceRepository.rename(uploaded.id, 'renamed.md')
    expect(renamed.name).toBe('renamed.md')

    const moved = await libraryResourceRepository.move(uploaded.id, null)
    expect(moved.libraryId).toBeNull()

    const resources = await libraryResourceRepository.list()
    const failed = resources.find((item) => item.id === uploaded.id)!
    failed.status = 'failed'
    failed.errorMessage = 'parse failed'
    mockSession.set('library-resources', resources)

    const retried = await libraryResourceRepository.retry(uploaded.id)
    expect(retried.status).toBe('waiting')
    expect(retried.errorMessage).toBeUndefined()

    const blob = await libraryResourceRepository.download(uploaded.id)
    expect(await blob.text()).toContain('renamed.md')

    await libraryResourceRepository.remove(uploaded.id)
    expect((await libraryResourceRepository.list()).some((item) => item.id === uploaded.id)).toBe(false)
  })
})
