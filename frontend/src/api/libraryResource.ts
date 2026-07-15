import { libraryResourceRepository } from '@/repositories/libraryResource'
import type { LibraryResourceDto } from '@/types/contracts/library'

export function getLibraryResources(): LibraryResourceDto[] {
  return libraryResourceRepository.initial()
}

export function saveLibraryResources(resources: LibraryResourceDto[]) {
  libraryResourceRepository.saveMock(resources)
}

export function listLibraryResources(libraryId?: number): Promise<LibraryResourceDto[]> {
  return libraryResourceRepository.list(libraryId)
}

export function uploadLibraryResource(file: File, libraryId: number | null, projectId?: number | null) {
  return libraryResourceRepository.upload(file, libraryId, projectId)
}

export function deleteLibraryResource(id: string) {
  return libraryResourceRepository.remove(id)
}

export function retryLibraryResource(id: string) {
  return libraryResourceRepository.retry(id)
}

export function renameLibraryResource(id: string, name: string) {
  return libraryResourceRepository.rename(id, name)
}

export function moveLibraryResource(id: string, libraryId: number | null) {
  return libraryResourceRepository.move(id, libraryId)
}

export function downloadLibraryResource(id: string) {
  return libraryResourceRepository.download(id)
}
