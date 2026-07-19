import { request } from "@/api/request";
import { isMockDataSource } from "@/config/dataSource";
import { recentUploads } from "@/mock";
import { mockSession } from "@/mock/storage";
import {
  deleteMockResourceFile,
  readMockResourceFile,
  saveMockResourceFile,
} from "@/mock/resourceFileStore";
import { RESOURCE_PREVIEW_LIMITS } from "@/types/contracts/library";
import type {
  LibraryResourceDto,
  LibraryResourceProcessingStatus,
  ResourceAssociations,
  ResourceFileType,
  ResourceOrigin,
  ResourcePreviewDto,
  ResourcePreviewKind,
} from "@/types/contracts/library";
import type { ArtifactInlinePreview } from "@/types/contracts/artifact";

export interface LibraryResourceRepository {
  initial(): LibraryResourceDto[];
  list(knowledgeBaseId?: number): Promise<LibraryResourceDto[]>;
  saveMock(resources: LibraryResourceDto[]): void;
  upload(
    file: File,
    origin: Extract<ResourceOrigin, "resource-library" | "chat" | "learning">,
    associations: ResourceAssociations,
  ): Promise<LibraryResourceDto>;
  remove(resourceId: string): Promise<void>;
  retry(resourceId: string): Promise<LibraryResourceDto>;
  rename(resourceId: string, name: string): Promise<LibraryResourceDto>;
  updateAssociations(
    resourceId: string,
    associations: ResourceAssociations,
  ): Promise<LibraryResourceDto>;
  preview(resourceId: string): Promise<ResourcePreviewDto>;
  download(resourceId: string): Promise<Blob>;
}

const DOMAIN = "resources";
const GENERATED_PREVIEW_DOMAIN = "resources.generated-previews";
const PROCESSING_DOMAIN = "resources.processing-jobs";

type MockResourceProcessingJob = {
  startedAt: number;
  durationMs: number;
  shouldFail: boolean;
};

function readProcessingJobs() {
  return mockSession.get<Record<string, MockResourceProcessingJob>>(PROCESSING_DOMAIN, {});
}

function startMockProcessing(resourceId: string, fileName: string, allowFailure = true) {
  const jobs = readProcessingJobs();
  jobs[resourceId] = {
    startedAt: Date.now(),
    durationMs: 900,
    shouldFail: allowFailure && /(?:^|[._-])(fail|broken)(?:[._-]|$)/i.test(fileName),
  };
  mockSession.set(PROCESSING_DOMAIN, jobs);
}

function advanceMockProcessing(resources: LibraryResourceDto[]) {
  const jobs = readProcessingJobs();
  let resourcesChanged = false;
  let jobsChanged = false;
  const now = Date.now();
  resources.forEach((resource) => {
    const job = jobs[resource.resourceId];
    if (!job) return;
    if (resource.status === "ready" || resource.status === "failed") {
      delete jobs[resource.resourceId];
      jobsChanged = true;
      return;
    }
    const elapsed = now - job.startedAt;
    const nextStatus: LibraryResourceProcessingStatus =
      elapsed < 250
        ? "waiting"
        : elapsed < job.durationMs
          ? "processing"
          : job.shouldFail
            ? "failed"
            : "ready";
    if (resource.status !== nextStatus) {
      resource.status = nextStatus;
      resource.updatedAt = "刚刚";
      resource.errorMessage = nextStatus === "failed" ? "Mock 文件解析失败，可重试解析" : undefined;
      resourcesChanged = true;
    }
    if (nextStatus === "ready" || nextStatus === "failed") {
      delete jobs[resource.resourceId];
      jobsChanged = true;
    }
  });
  if (resourcesChanged) mockSession.set(DOMAIN, resources);
  if (jobsChanged) mockSession.set(PROCESSING_DOMAIN, jobs);
  return resources;
}

function externalId(resource: LibraryResourceDto, prefix: string) {
  return resource.externalKey?.startsWith(prefix)
    ? resource.externalKey.slice(prefix.length)
    : undefined;
}

function extensionOf(name: string) {
  return name.split(".").pop()?.toLowerCase() ?? "";
}

function previewKind(resource: LibraryResourceDto): ResourcePreviewKind {
  const extension = extensionOf(resource.name);
  if (resource.fileType === "image") return "image";
  if (resource.fileType === "pdf") return "pdf";
  if (resource.fileType === "presentation") return "presentation";
  if (resource.fileType === "spreadsheet") return "spreadsheet";
  if (resource.fileType === "mindmap") return "mindmap";
  if (resource.fileType === "audio") return "audio";
  if (resource.fileType === "document" && ["doc", "docx"].includes(extension)) return "word";
  if (
    resource.fileType === "document" ||
    ["txt", "md", "json", "js", "ts", "java", "py", "css", "html"].includes(extension)
  )
    return "text";
  return "unsupported";
}

function previewLimit(resource: LibraryResourceDto, kind: ResourcePreviewKind) {
  if (kind === "text") return RESOURCE_PREVIEW_LIMITS.text;
  if (kind === "mindmap") return RESOURCE_PREVIEW_LIMITS.mindmap;
  if (kind === "image") return RESOURCE_PREVIEW_LIMITS.image;
  if (kind === "pdf") return RESOURCE_PREVIEW_LIMITS.pdf;
  if (kind === "presentation") return RESOURCE_PREVIEW_LIMITS.presentation;
  if (kind === "spreadsheet") return RESOURCE_PREVIEW_LIMITS.spreadsheet;
  if (kind === "audio") return RESOURCE_PREVIEW_LIMITS.audio;
  return RESOURCE_PREVIEW_LIMITS.document;
}

export async function rememberMockLibraryResourceFile(resourceId: string, file: File) {
  if (!isMockDataSource) return;
  await saveMockResourceFile(resourceId, file);
}

export function detachMockResourcesFromKnowledgeBase(knowledgeBaseId: number) {
  if (!isMockDataSource) return;
  const resources = mockSession.get(DOMAIN, initialMockResources());
  let changed = false;
  resources.forEach((resource) => {
    if (resource.knowledgeBaseId !== knowledgeBaseId) return;
    resource.knowledgeBaseId = null;
    resource.updatedAt = "刚刚";
    changed = true;
  });
  if (changed) mockSession.set(DOMAIN, resources);
}

export function rememberMockGeneratedResourcePreview(
  resourceId: string,
  preview: ArtifactInlinePreview,
) {
  if (!isMockDataSource) return;
  const previews = mockSession.get<Record<string, ArtifactInlinePreview>>(
    GENERATED_PREVIEW_DOMAIN,
    {},
  );
  previews[resourceId] = preview;
  mockSession.set(GENERATED_PREVIEW_DOMAIN, previews);
}

function normalizeStatus(status: unknown): LibraryResourceProcessingStatus {
  if (status === "ready" || status === "解析完成") return "ready";
  if (status === "processing" || status === "向量化中") return "processing";
  if (status === "failed" || status === "解析失败") return "failed";
  return "waiting";
}

function escapeXml(value: string) {
  return value.replace(
    /[<>&'\"]/g,
    (character) =>
      ({
        "<": "&lt;",
        ">": "&gt;",
        "&": "&amp;",
        "'": "&apos;",
        '"': "&quot;",
      })[character] ?? character,
  );
}

async function generatedDocx(name: string, content: string) {
  const { default: JSZip } = await import("jszip");
  const zip = new JSZip();
  const paragraphs = content
    .split(/\n+/)
    .filter(Boolean)
    .map((line) => `<w:p><w:r><w:t xml:space="preserve">${escapeXml(line)}</w:t></w:r></w:p>`)
    .join("");
  zip.file(
    "[Content_Types].xml",
    '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>',
  );
  zip
    .folder("_rels")
    ?.file(
      ".rels",
      '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>',
    );
  zip
    .folder("word")
    ?.file(
      "document.xml",
      `<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body><w:p><w:r><w:rPr><w:b/><w:sz w:val="32"/></w:rPr><w:t>${escapeXml(name)}</w:t></w:r></w:p>${paragraphs}<w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440"/></w:sectPr></w:body></w:document>`,
    );
  return zip.generateAsync({
    type: "blob",
    mimeType: "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
  });
}

async function generatedPdf(name: string, content: string) {
  const { PDFDocument, StandardFonts, rgb } = await import("pdf-lib");
  const pdf = await PDFDocument.create();
  const page = pdf.addPage([595, 842]);
  const font = await pdf.embedFont(StandardFonts.Helvetica);
  page.drawText("ExamInsight AI Generated Document", {
    x: 54,
    y: 770,
    size: 20,
    font,
    color: rgb(0.12, 0.14, 0.18),
  });
  page.drawText(name.replace(/[^\x20-\x7E]/g, " ").slice(0, 70) || "Generated file", {
    x: 54,
    y: 735,
    size: 12,
    font,
    color: rgb(0.3, 0.33, 0.4),
  });
  const lines = content
    .replace(/[^\x20-\x7E\n]/g, " ")
    .split("\n")
    .flatMap((line) => line.match(/.{1,78}/g) ?? [""])
    .slice(0, 34);
  lines.forEach((line, index) =>
    page.drawText(line, {
      x: 54,
      y: 700 - index * 17,
      size: 10,
      font,
      color: rgb(0.2, 0.22, 0.26),
    }),
  );
  const bytes = await pdf.save();
  const buffer = bytes.buffer.slice(
    bytes.byteOffset,
    bytes.byteOffset + bytes.byteLength,
  ) as ArrayBuffer;
  return new Blob([buffer], { type: "application/pdf" });
}

export function resourceFileType(name: string, mimeType = ""): ResourceFileType {
  const extension = name.split(".").pop()?.toLowerCase() ?? "";
  if (
    mimeType.startsWith("image/") ||
    ["jpg", "jpeg", "png", "webp", "heic", "heif"].includes(extension)
  )
    return "image";
  if (extension === "pdf" || mimeType === "application/pdf") return "pdf";
  if (["xls", "xlsx", "csv"].includes(extension)) return "spreadsheet";
  if (["ppt", "pptx"].includes(extension)) return "presentation";
  if (
    ["mp3", "wav", "m4a", "aac", "ogg", "flac"].includes(extension) ||
    mimeType.startsWith("audio/")
  )
    return "audio";
  if (["zip", "rar", "7z"].includes(extension)) return "archive";
  if (extension === "mindmap") return "mindmap";
  if (["doc", "docx", "txt", "md"].includes(extension)) return "document";
  return "other";
}

export function resourceFormat(name: string, fallback = "FILE") {
  const extension = name.split(".").pop()?.toUpperCase();
  if (!extension || extension === name.toUpperCase()) return fallback;
  if (extension === "DOC" || extension === "DOCX") return "Word";
  if (extension === "PPT" || extension === "PPTX") return "PPT";
  if (extension === "XLS" || extension === "XLSX") return "Excel";
  if (extension === "JPG" || extension === "JPEG" || extension === "PNG" || extension === "WEBP")
    return "图片";
  return extension;
}

function normalizeResource(item: LibraryResourceDto | Record<string, unknown>): LibraryResourceDto {
  return { ...item, status: normalizeStatus(item.status) } as LibraryResourceDto;
}

function initialMockResources(): LibraryResourceDto[] {
  return recentUploads.map((file) => ({
    resourceId: `mock-${file.id}`,
    name: file.name,
    format: resourceFormat(file.name, file.type),
    fileType: resourceFileType(file.name),
    sizeBytes: 128 * 1024,
    status: normalizeStatus(file.status),
    updatedAt: file.updatedAt,
    sourceType: "uploaded",
    origin: "resource-library",
    projectId: null,
    knowledgeBaseId: null,
  }));
}

const mockRepository: LibraryResourceRepository = {
  initial() {
    return advanceMockProcessing(mockSession.get(DOMAIN, initialMockResources()));
  },
  async list(knowledgeBaseId) {
    const resources = advanceMockProcessing(mockSession.get(DOMAIN, initialMockResources()));
    return knowledgeBaseId === undefined
      ? resources
      : resources.filter((item) => item.knowledgeBaseId === knowledgeBaseId);
  },
  saveMock(resources) {
    mockSession.set(DOMAIN, resources);
  },
  async upload(file, origin, associations) {
    const resource: LibraryResourceDto = {
      resourceId: `upload-${Date.now()}-${Math.random().toString(36).slice(2)}`,
      name: file.name,
      format: resourceFormat(file.name),
      fileType: resourceFileType(file.name, file.type),
      mimeType: file.type || undefined,
      sizeBytes: file.size,
      status: "waiting",
      updatedAt: "刚刚",
      sourceType: "uploaded",
      origin,
      ...associations,
    };
    const resources = mockSession.get(DOMAIN, initialMockResources());
    resources.unshift(resource);
    mockSession.set(DOMAIN, resources);
    startMockProcessing(resource.resourceId, file.name);
    await saveMockResourceFile(resource.resourceId, file);
    return resource;
  },
  async remove(resourceId) {
    mockSession.set(
      DOMAIN,
      mockSession
        .get(DOMAIN, initialMockResources())
        .filter((item) => item.resourceId !== resourceId),
    );
    const jobs = readProcessingJobs();
    delete jobs[resourceId];
    mockSession.set(PROCESSING_DOMAIN, jobs);
    await deleteMockResourceFile(resourceId);
  },
  async retry(resourceId) {
    const resources = mockSession.get(DOMAIN, initialMockResources());
    const resource = resources.find((item) => item.resourceId === resourceId);
    if (!resource) throw new Error("资料不存在");
    resource.status = "waiting";
    resource.errorMessage = undefined;
    resource.updatedAt = "刚刚";
    mockSession.set(DOMAIN, resources);
    startMockProcessing(resource.resourceId, resource.name, false);
    return resource;
  },
  async rename(resourceId, name) {
    const resources = mockSession.get(DOMAIN, initialMockResources());
    const resource = resources.find((item) => item.resourceId === resourceId);
    if (!resource) throw new Error("资料不存在");
    resource.name = name;
    resource.format = resourceFormat(name, resource.format);
    resource.fileType = resourceFileType(name, resource.mimeType);
    resource.updatedAt = "刚刚";
    mockSession.set(DOMAIN, resources);
    return resource;
  },
  async updateAssociations(resourceId, associations) {
    const resources = mockSession.get(DOMAIN, initialMockResources());
    const resource = resources.find((item) => item.resourceId === resourceId);
    if (!resource) throw new Error("资料不存在");
    resource.projectId = associations.projectId;
    resource.knowledgeBaseId = associations.knowledgeBaseId;
    resource.updatedAt = "刚刚";
    mockSession.set(DOMAIN, resources);
    return resource;
  },
  async preview(resourceId) {
    const resource = advanceMockProcessing(mockSession.get(DOMAIN, initialMockResources())).find(
      (item) => item.resourceId === resourceId,
    );
    if (!resource) throw new Error("资料不存在或已被删除");

    const kind = previewKind(resource);
    const base = { resource: structuredClone(resource), previewKind: kind } as const;
    if (resource.status === "waiting" || resource.status === "processing") {
      return { ...base, status: "processing", errorMessage: "文件仍在解析，请稍后重试" };
    }
    if (resource.status === "failed") {
      return { ...base, status: "failed", errorMessage: resource.errorMessage || "文件解析失败" };
    }
    if (kind === "unsupported") {
      return {
        ...base,
        status: "unsupported",
        errorMessage: "当前格式不支持在线预览，可下载后查看",
      };
    }
    if (resource.sizeBytes > previewLimit(resource, kind)) {
      return { ...base, status: "too_large", errorMessage: "文件超过该格式的在线预览大小限制" };
    }

    const presentationId = externalId(resource, "presentation:");
    const spreadsheetId = externalId(resource, "spreadsheet:");
    const mindMapId = Number(externalId(resource, "mindmap:")) || undefined;
    const generatedPreview = mockSession.get<Record<string, ArtifactInlinePreview>>(
      GENERATED_PREVIEW_DOMAIN,
      {},
    )[resourceId];
    if (
      presentationId ||
      spreadsheetId ||
      mindMapId ||
      resource.externalKey?.startsWith("learning:")
    ) {
      return {
        ...base,
        status: "ready",
        presentationId,
        spreadsheetId,
        mindMapId,
        previewData: generatedPreview,
      };
    }

    if (generatedPreview) {
      return {
        ...base,
        status: "ready",
        previewData: generatedPreview,
        textContent: generatedPreview.text,
        previewUrl: generatedPreview.imageUrl,
      };
    }

    const file = await readMockResourceFile(resourceId);
    if (!file) {
      return {
        ...base,
        status: "failed",
        errorMessage: "Mock 文件内容不存在或已被清理，请重新上传",
      };
    }

    if (kind === "text") {
      return { ...base, status: "ready", textContent: await file.text() };
    }
    return { ...base, status: "ready", previewUrl: URL.createObjectURL(file) };
  },
  async download(resourceId) {
    const resource = mockSession
      .get(DOMAIN, initialMockResources())
      .find((item) => item.resourceId === resourceId);
    if (!resource) throw new Error("资料不存在");
    const preview = mockSession.get<Record<string, ArtifactInlinePreview>>(
      GENERATED_PREVIEW_DOMAIN,
      {},
    )[resourceId];
    if (preview?.imageUrl) return fetch(preview.imageUrl).then((response) => response.blob());
    if (resource.name.toLowerCase().endsWith(".docx"))
      return generatedDocx(resource.name, preview?.text || resource.name);
    if (resource.name.toLowerCase().endsWith(".pdf"))
      return generatedPdf(resource.name, preview?.text || resource.name);
    if (resource.fileType === "mindmap" && preview?.mindMap) {
      return new Blob([JSON.stringify(preview.mindMap, null, 2)], {
        type: "application/vnd.examinsight.mindmap+json",
      });
    }
    const uploadedFile = await readMockResourceFile(resourceId);
    if (uploadedFile) return uploadedFile;
    return new Blob([`Mock 文件：${resource.name}\n文件内容不存在或已被清理。`], {
      type: "text/plain;charset=utf-8",
    });
  },
};

function unwrap<T>(response: { data: unknown }): T {
  const payload = response.data as { data?: T };
  return (payload?.data ?? response.data) as T;
}

const apiRepository: LibraryResourceRepository = {
  initial() {
    return [];
  },
  async list(knowledgeBaseId) {
    const data = unwrap<Record<string, unknown>[]>(
      await request.get("/api/resources", { params: { knowledgeBaseId } }),
    );
    return data.map(normalizeResource);
  },
  saveMock() {},
  async upload(file, origin, associations) {
    const formData = new FormData();
    formData.append("file", file);
    formData.append("origin", origin);
    if (associations.knowledgeBaseId != null)
      formData.append("knowledgeBaseId", String(associations.knowledgeBaseId));
    if (associations.projectId != null)
      formData.append("projectId", String(associations.projectId));
    return normalizeResource(
      unwrap<Record<string, unknown>>(
        await request.post("/api/resources/upload", formData, {
          headers: { "Content-Type": "multipart/form-data" },
        }),
      ),
    );
  },
  async remove(resourceId) {
    await request.delete(`/api/resources/${resourceId}`);
  },
  async retry(resourceId) {
    return normalizeResource(
      unwrap<Record<string, unknown>>(await request.post(`/api/resources/${resourceId}/retry`)),
    );
  },
  async rename(resourceId, name) {
    return normalizeResource(
      unwrap<Record<string, unknown>>(
        await request.patch(`/api/resources/${resourceId}`, { name }),
      ),
    );
  },
  async updateAssociations(resourceId, associations) {
    return normalizeResource(
      unwrap<Record<string, unknown>>(
        await request.put(`/api/resources/${resourceId}/associations`, associations),
      ),
    );
  },
  async preview(resourceId) {
    return unwrap<ResourcePreviewDto>(await request.get(`/api/resources/${resourceId}/preview`));
  },
  async download(resourceId) {
    const response = await request.get(`/api/resources/${resourceId}/download`, {
      responseType: "blob",
    });
    return response.data as Blob;
  },
};

export const libraryResourceRepository = isMockDataSource ? mockRepository : apiRepository;
