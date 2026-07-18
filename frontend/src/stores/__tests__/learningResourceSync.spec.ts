import { beforeEach, describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

import { USER_KEY } from '@/api/request'
import { useLearningStore } from '@/stores/learning'

describe('Mock project generated resource synchronization', () => {
  beforeEach(() => {
    sessionStorage.clear()
    localStorage.clear()
    sessionStorage.setItem(USER_KEY, JSON.stringify({ id: 301 }))
    setActivePinia(createPinia())
  })

  it('adds a generated chat file to the project package and upserts it idempotently', async () => {
    const store = useLearningStore()
    await store.fetchPlans()
    const project = store.plans[0]!
    const before = project.resources.length
    const input = {
      projectId: project.id,
      resourceId: 'resource-chat-doc-1',
      artifactId: 'document:chat-doc-1',
      title: '多态复习文档',
      fileName: '多态复习文档.docx',
      fileType: 'document' as const,
      preview: { kind: 'document' as const, text: '第一版' },
      source: 'ai-conversation' as const,
    }

    await store.attachGeneratedResourceToProject(input)
    await store.attachGeneratedResourceToProject({ ...input, preview: { kind: 'document', text: '第二版' } })

    expect(project.resources).toHaveLength(before + 1)
    expect(project.resources.find((item) => item.artifactId === input.artifactId)).toMatchObject({
      resourceId: input.resourceId,
      group: '文档',
      content: '第二版',
      source: 'ai-conversation',
    })
  })
})
