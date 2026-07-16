import { request } from "@/api/request";
import { isMockDataSource } from "@/config/dataSource";
import { recentUploads } from "@/mock";
import { mockSession } from "@/mock/storage";
import type {
  LibraryResourceDto,
  LibraryResourceProcessingStatus,
} from "@/types/contracts/library";

export interface LibraryResourceRepository {
  initial(): LibraryResourceDto[];
  list(libraryId?: number): Promise<LibraryResourceDto[]>;
  saveMock(resources: LibraryResourceDto[]): void;
  upload(
    file: File,
    libraryId: number | null,
    projectId?: number | null,
  ): Promise<LibraryResourceDto>;
  remove(id: string): Promise<void>;
  retry(id: string): Promise<LibraryResourceDto>;
  rename(id: string, name: string): Promise<LibraryResourceDto>;
  move(id: string, libraryId: number | null): Promise<LibraryResourceDto>;
  download(id: string): Promise<Blob>;
}

const DOMAIN = "library-resources";

function normalizeStatus(status: unknown): LibraryResourceProcessingStatus {
  if (status === "ready" || status === "解析完成") return "ready";
  if (status === "processing" || status === "向量化中") return "processing";
  if (status === "failed" || status === "解析失败") return "failed";
  return "waiting";
}

function extractNumericId(id: unknown): string | number {
  if (typeof id === "string" && id.startsWith("document:")) {
    return id.split(":")[1];
  }
  return id as string | number;
}

function normalizeResource(item: LibraryResourceDto | Record<string, unknown>): LibraryResourceDto {
  return {
    ...item,
    id: extractNumericId(item.id),
    status: normalizeStatus(item.status),
  } as LibraryResourceDto;
}

function initialMockResources(): LibraryResourceDto[] {
  return recentUploads.map((file) => ({
    id: `mock-${file.id}`,
    name: file.name,
    type: file.type,
    size: "128 KB",
    status: normalizeStatus(file.status),
    updatedAt: file.updatedAt,
    category: "file",
    source: "资料库上传",
    projectId: null,
    libraryId: null,
  }));
}

const mockRepository: LibraryResourceRepository = {
  initial() {
    return mockSession.get(DOMAIN, initialMockResources());
  },
  async list(libraryId) {
    const resources = mockSession.get(DOMAIN, initialMockResources());
    return libraryId === undefined
      ? resources
      : resources.filter((item) => item.libraryId === libraryId);
  },
  saveMock(resources) {
    mockSession.set(DOMAIN, resources);
  },
  async upload(file, libraryId, projectId = null) {
    const resource: LibraryResourceDto = {
      id: `upload-${Date.now()}`,
      name: file.name,
      type: file.name.split(".").pop()?.toUpperCase() || "文件",
      size: `${Math.max(1, Math.round(file.size / 1024))} KB`,
      status: "waiting",
      updatedAt: "刚刚",
      category: file.type.startsWith("image/") ? "image" : "file",
      source: "资料库上传",
      projectId,
      libraryId,
    };
    const resources = mockSession.get(DOMAIN, initialMockResources());
    resources.unshift(resource);
    mockSession.set(DOMAIN, resources);
    return resource;
  },
  async remove(id) {
    mockSession.set(
      DOMAIN,
      mockSession.get(DOMAIN, initialMockResources()).filter((item) => item.id !== id),
    );
  },
  async retry(id) {
    const resources = mockSession.get(DOMAIN, initialMockResources());
    const resource = resources.find((item) => item.id === id);
    if (!resource) throw new Error("资料不存在");
    resource.status = "waiting";
    resource.errorMessage = undefined;
    resource.updatedAt = "刚刚";
    mockSession.set(DOMAIN, resources);
    return resource;
  },
  async rename(id, name) {
    const resources = mockSession.get(DOMAIN, initialMockResources());
    const resource = resources.find((item) => item.id === id);
    if (!resource) throw new Error("资料不存在");
    resource.name = name;
    resource.updatedAt = "刚刚";
    mockSession.set(DOMAIN, resources);
    return resource;
  },
  async move(id, libraryId) {
    const resources = mockSession.get(DOMAIN, initialMockResources());
    const resource = resources.find((item) => item.id === id);
    if (!resource) throw new Error("资料不存在");
    resource.libraryId = libraryId;
    resource.updatedAt = "刚刚";
    mockSession.set(DOMAIN, resources);
    return resource;
  },
  async download(id) {
    const resource = mockSession.get(DOMAIN, initialMockResources()).find((item) => item.id === id);
    if (!resource) throw new Error("资料不存在");
    return new Blob([`Mock 文件：${resource.name}\nMock 环境不保存真实上传文件内容。`], {
      type: "text/plain;charset=utf-8",
    });
  },
};

const apiRepository: LibraryResourceRepository = {
  initial() {
    return [];
  },
  async list(libraryId) {
    try {
      const response = await request.get("/api/library/resources", { params: { libraryId } });
      const data = (response.data?.data ?? response.data) as Record<string, unknown>[];
      if (!Array.isArray(data)) return [];
      return data.map(normalizeResource);
    } catch (error) {
      console.error("Failed to load library resources:", error);
      return [];
    }
  },
  saveMock() {},
  async upload(file, libraryId, projectId = null) {
    const formData = new FormData();
    formData.append("file", file);
    if (libraryId !== null) formData.append("libraryId", String(libraryId));
    if (projectId !== null) formData.append("projectId", String(projectId));
    const response = await request.post("/api/library/resources/upload", formData, {
      headers: { "Content-Type": "multipart/form-data" },
    });
    return normalizeResource((response.data?.data ?? response.data) as Record<string, unknown>);
  },
  async remove(id) {
    await request.delete(`/api/library/resources/${id}`);
  },
  async retry(id) {
    const response = await request.post(`/api/library/resources/${id}/retry`);
    return normalizeResource((response.data?.data ?? response.data) as Record<string, unknown>);
  },
  async rename(id, name) {
    const response = await request.patch(`/api/library/resources/${id}`, { name });
    return normalizeResource((response.data?.data ?? response.data) as Record<string, unknown>);
  },
  async move(id, libraryId) {
    const response = await request.post(`/api/library/resources/${id}/move`, { libraryId });
    return normalizeResource((response.data?.data ?? response.data) as Record<string, unknown>);
  },
  async download(id) {
    const response = await request.get(`/api/library/resources/${id}/download`, {
      responseType: "blob",
    });
    return response.data as Blob;
  },
};

export const libraryResourceRepository = isMockDataSource ? mockRepository : apiRepository;
