import { recentUploads } from '@/mock'
import type { LibraryResource } from '@/stores/libraryResource'

const STORAGE_KEY = 'examinsight.library.resources'

function initialResources(): LibraryResource[] {
  return recentUploads.map((file) => ({
    id: `mock-${file.id}`,
    name: file.name,
    type: file.type,
    size: '128 KB',
    status: file.status,
    updatedAt: file.updatedAt,
    category: 'file',
    source: '资料库上传',
    projectId: null,
    libraryId: null,
  }))
}

export function getLibraryResources(): LibraryResource[] {
  const stored = sessionStorage.getItem(STORAGE_KEY)
  if (!stored) return initialResources()

  try {
    return JSON.parse(stored) as LibraryResource[]
  } catch {
    return initialResources()
  }
}

export function saveLibraryResources(resources: LibraryResource[]) {
  sessionStorage.setItem(STORAGE_KEY, JSON.stringify(resources))
}

