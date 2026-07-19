import { libraryResourceRepository } from "@/repositories/libraryResource";
import type {
  LibraryResourceDto,
  ResourceAssociations,
  ResourceOrigin,
} from "@/types/contracts/library";
import { request } from "@/api/request";

export function getLibraryResources(): LibraryResourceDto[] {
  return libraryResourceRepository.initial();
}

export function saveLibraryResources(resources: LibraryResourceDto[]) {
  libraryResourceRepository.saveMock(resources);
}

export async function listLibraryResources(
  knowledgeBaseId?: number,
): Promise<LibraryResourceDto[]> {
  return libraryResourceRepository.list(knowledgeBaseId);
}

export function uploadLibraryResource(
  file: File,
  origin: Extract<ResourceOrigin, "resource-library" | "chat" | "learning">,
  associations: ResourceAssociations,
) {
  return libraryResourceRepository.upload(file, origin, associations);
}

export function deleteLibraryResource(id: string) {
  return libraryResourceRepository.remove(id);
}

export function retryLibraryResource(id: string) {
  return libraryResourceRepository.retry(id);
}

export function renameLibraryResource(id: string, name: string) {
  return libraryResourceRepository.rename(id, name);
}

export function updateLibraryResourceAssociations(
  resourceId: string,
  associations: ResourceAssociations,
) {
  return libraryResourceRepository.updateAssociations(resourceId, associations);
}

export function previewLibraryResource(id: string) {
  return libraryResourceRepository.preview(id);
}

export function downloadLibraryResource(id: string) {
  return libraryResourceRepository.download(id);
}
