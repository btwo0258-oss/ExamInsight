import type { LibraryAsset } from "@/types/contracts/assetLibraryV2";
import {
  RESOURCE_PREVIEW_LIMITS,
  type ResourceFileType,
  type ResourcePreviewDto,
  type ResourcePreviewKind,
  type ResourcePreviewStatus,
} from "@/types/contracts/library";

export type PreviewLoadStrategy =
  | "text"
  | "docx"
  | "csv"
  | "xlsx"
  | "object-url"
  | "unsupported";

export type PreviewDescriptor = {
  extension: string;
  kind: ResourcePreviewKind;
  fileType: ResourceFileType;
  strategy: PreviewLoadStrategy;
  maxBytes: number | null;
  unsupportedMessage?: string;
};

type PreviewRule = {
  extensions: readonly string[];
  mimePrefixes?: readonly string[];
  kind: ResourcePreviewKind;
  fileType: ResourceFileType;
  strategy: PreviewLoadStrategy;
  maxBytes: number | null;
  unsupportedMessage?: string;
};

const PREVIEW_RULES: readonly PreviewRule[] = [
  {
    extensions: ["pdf"],
    mimePrefixes: ["application/pdf"],
    kind: "pdf",
    fileType: "pdf",
    strategy: "object-url",
    maxBytes: RESOURCE_PREVIEW_LIMITS.pdf,
  },
  {
    extensions: ["jpg", "jpeg", "png", "webp", "gif"],
    mimePrefixes: ["image/"],
    kind: "image",
    fileType: "image",
    strategy: "object-url",
    maxBytes: RESOURCE_PREVIEW_LIMITS.image,
  },
  {
    extensions: ["docx"],
    kind: "word",
    fileType: "document",
    strategy: "docx",
    maxBytes: RESOURCE_PREVIEW_LIMITS.document,
  },
  {
    extensions: ["xlsx"],
    kind: "spreadsheet",
    fileType: "spreadsheet",
    strategy: "xlsx",
    maxBytes: RESOURCE_PREVIEW_LIMITS.spreadsheet,
  },
  {
    extensions: ["csv"],
    mimePrefixes: ["text/csv"],
    kind: "spreadsheet",
    fileType: "spreadsheet",
    strategy: "csv",
    maxBytes: RESOURCE_PREVIEW_LIMITS.spreadsheet,
  },
  {
    extensions: ["txt", "md"],
    mimePrefixes: ["text/"],
    kind: "text",
    fileType: "document",
    strategy: "text",
    maxBytes: RESOURCE_PREVIEW_LIMITS.text,
  },
  {
    extensions: ["mp3", "wav", "m4a", "ogg"],
    mimePrefixes: ["audio/"],
    kind: "audio",
    fileType: "audio",
    strategy: "object-url",
    maxBytes: RESOURCE_PREVIEW_LIMITS.audio,
  },
  {
    extensions: ["pptx"],
    kind: "presentation",
    fileType: "presentation",
    strategy: "unsupported",
    maxBytes: RESOURCE_PREVIEW_LIMITS.presentation,
    unsupportedMessage: "演示文稿需要由后端转换为 PDF 或页面图片后才能在线预览，当前可先下载原文件查看。",
  },
] as const;

const ORIGINAL_FILE_READABLE_STATUSES = new Set(["PROCESSING", "READY", "FAILED"]);

export function fileExtension(name: string) {
  return name.split(".").pop()?.toLowerCase() ?? "";
}

function matchesRule(rule: PreviewRule, extension: string, mimeType: string) {
  return (
    rule.extensions.includes(extension) ||
    rule.mimePrefixes?.some((prefix) => mimeType.startsWith(prefix)) === true
  );
}

export function resolveAssetPreview(asset: LibraryAsset): PreviewDescriptor {
  const extension = fileExtension(asset.name);
  const mimeType = asset.version?.mimeType?.toLowerCase() ?? "";
  const matched = PREVIEW_RULES.find((rule) => matchesRule(rule, extension, mimeType));
  if (!matched) {
    return {
      extension,
      kind: "unsupported",
      fileType: "other",
      strategy: "unsupported",
      maxBytes: null,
      unsupportedMessage: "当前文件格式暂不支持在线预览，可下载原文件查看。",
    };
  }
  return {
    extension,
    kind: matched.kind,
    fileType: matched.fileType,
    strategy: matched.strategy,
    maxBytes: matched.maxBytes,
    unsupportedMessage: matched.unsupportedMessage,
  };
}

export function buildAssetPreviewDto(
  asset: LibraryAsset,
  descriptor: PreviewDescriptor,
): ResourcePreviewDto {
  const version = asset.version;
  const originalFileReadable = ORIGINAL_FILE_READABLE_STATUSES.has(version?.status ?? "");
  const tooLarge =
    descriptor.maxBytes !== null && (version?.sizeBytes ?? 0) > descriptor.maxBytes;

  let status: ResourcePreviewStatus = "processing";
  let errorMessage: string | undefined;
  if (originalFileReadable && descriptor.strategy === "unsupported") {
    status = "unsupported";
    errorMessage = descriptor.unsupportedMessage;
  } else if (originalFileReadable && tooLarge) {
    status = "too_large";
    errorMessage = "文件超过当前格式的浏览器预览上限，可下载原文件查看。";
  } else if (originalFileReadable) {
    status = "ready";
  }

  return {
    resource: {
      resourceId: asset.assetId,
      name: asset.name,
      format: descriptor.extension.toUpperCase() || "FILE",
      fileType: descriptor.fileType,
      mimeType: version?.mimeType,
      sizeBytes: version?.sizeBytes ?? 0,
      status: originalFileReadable ? "ready" : "processing",
      updatedAt: asset.updatedAt,
      sourceType: asset.sourceType === "AI_GENERATED" ? "generated" : "uploaded",
      origin: "resource-library",
      projectId: null,
      knowledgeBaseId: null,
    },
    status,
    previewKind: descriptor.kind,
    errorMessage,
  };
}

