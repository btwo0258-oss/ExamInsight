import type { EntityId } from "./common";
import type { ArtifactInlinePreview } from "./artifact";

export type DocumentProcessingStatus = "uploading" | "uploaded" | "parsing" | "ready" | "failed";

export type KnowledgeBaseDto = {
  id: EntityId;
  name: string;
  description?: string;
  icon?: string;
  color?: string;
  documentCount?: number;
  mindMapCount?: number;
  chunkCount?: number;
  knowledgePoints?: string[];
  examAnalysisId?: EntityId;
  availableForAi: boolean;
  createTime: string;
  updateTime: string;
};

export type KnowledgeBaseDocumentDto = {
  id: EntityId;
  knowledgeBaseId: EntityId;
  fileName: string;
  fileType: string;
  fileSize: number;
  chunkCount: number;
  status: DocumentProcessingStatus;
  errorCode?: string;
  errorMessage?: string;
  createTime: string;
};

export type ResourceSourceType = "uploaded" | "generated";
export type ResourceOrigin =
  | "resource-library"
  | "chat"
  | "learning"
  | "presentation"
  | "spreadsheet"
  | "mindmap";
export type ResourceFileType =
  | "image"
  | "document"
  | "spreadsheet"
  | "presentation"
  | "pdf"
  | "audio"
  | "archive"
  | "mindmap"
  | "other";
export type LibraryResourceProcessingStatus = "waiting" | "processing" | "ready" | "failed";
export type ResourcePreviewStatus = "processing" | "ready" | "failed" | "unsupported" | "too_large";
export type ResourcePreviewKind =
  | "text"
  | "image"
  | "pdf"
  | "word"
  | "presentation"
  | "spreadsheet"
  | "mindmap"
  | "audio"
  | "unsupported";

export type LibraryResourceDto = {
  resourceId: string;
  name: string;
  format: string;
  fileType: ResourceFileType;
  mimeType?: string;
  sizeBytes: number;
  status: LibraryResourceProcessingStatus;
  errorMessage?: string;
  updatedAt: string;
  sourceType: ResourceSourceType;
  origin: ResourceOrigin;
  projectId: number | null;
  knowledgeBaseId: number | null;
  externalKey?: string;
};

export type ResourceAssociations = {
  projectId: number | null;
  knowledgeBaseId: number | null;
};

export type ResourcePreviewDto = {
  resource: LibraryResourceDto;
  status: ResourcePreviewStatus;
  previewKind: ResourcePreviewKind;
  textContent?: string;
  previewUrl?: string;
  transcript?: string;
  presentationId?: string;
  spreadsheetId?: string;
  mindMapId?: number;
  previewData?: ArtifactInlinePreview;
  errorMessage?: string;
};

export const RESOURCE_PREVIEW_LIMITS = {
  text: 10 * 1024 * 1024,
  mindmap: 10 * 1024 * 1024,
  image: 20 * 1024 * 1024,
  document: 30 * 1024 * 1024,
  pdf: 30 * 1024 * 1024,
  presentation: 30 * 1024 * 1024,
  spreadsheet: 30 * 1024 * 1024,
  audio: 30 * 1024 * 1024,
} as const;
