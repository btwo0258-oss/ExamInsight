package com.example.llm.asset.service;

import com.example.llm.asset.api.AssetApiException;
import com.example.llm.asset.api.LibraryDtos;
import com.example.llm.asset.repository.AssetLibraryRepository;
import com.example.llm.asset.repository.AssetLibraryRepository.AssetRow;
import com.example.llm.asset.repository.AssetLibraryRepository.ReadableAssetContent;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

@Service
public class AssetPreviewService {
    private static final long MIB = 1024L * 1024L;
    private static final long TEXT_LIMIT = 10L * MIB;
    private static final long MINDMAP_LIMIT = 10L * MIB;
    private static final long IMAGE_LIMIT = 20L * MIB;
    private static final long DOCUMENT_LIMIT = 30L * MIB;
    private static final long AUDIO_LIMIT = 30L * MIB;

    private final AssetLibraryRepository assets;

    public AssetPreviewService(AssetLibraryRepository assets) {
        this.assets = assets;
    }

    public LibraryDtos.AssetPreview describe(long userId, String assetExternalId) {
        AssetRow asset = assets.findByExternalId(userId, assetExternalId)
                .orElseThrow(() -> new AssetApiException(
                        HttpStatus.NOT_FOUND,
                        "ASSET_NOT_FOUND",
                        "资料不存在或已被删除。",
                        Map.of()));
        if (!asset.status().equals("ACTIVE")) {
            throw new AssetApiException(
                    HttpStatus.CONFLICT,
                    "ASSET_NOT_ACTIVE",
                    "回收站中的资料不能预览或下载，请先恢复。",
                    Map.of("status", asset.status()));
        }

        String mimeType = asset.version() == null ? "application/octet-stream" : asset.version().mimeType();
        long sizeBytes = asset.version() == null ? 0 : asset.version().sizeBytes();
        String extension = extensionOf(asset.name());
        PreviewRule rule = resolveRule(mimeType, extension);
        ReadableAssetContent readable = assets.findReadableContent(userId, assetExternalId).orElse(null);

        String status;
        String reason = null;
        String contentUrl = null;
        if (readable == null) {
            status = asset.version() != null && "FAILED".equals(asset.version().status())
                    ? "failed"
                    : "processing";
            reason = status.equals("failed")
                    ? "文件原件暂时不可用，可稍后重试或重新上传。"
                    : "文件仍在上传或安全检查中，请稍后重试。";
        } else if (rule.renderer().equals("unsupported")) {
            status = "unsupported";
            reason = "当前文件格式暂不支持在线预览，可下载原文件查看。";
        } else if (sizeBytes > rule.maxBytes()) {
            status = "too_large";
            reason = "文件超过当前格式的在线预览上限，可下载原文件查看。";
        } else {
            status = "ready";
            contentUrl = contentUrl(assetExternalId, "inline");
        }

        return new LibraryDtos.AssetPreview(
                asset.externalId(),
                asset.version() == null ? null : asset.version().externalId(),
                asset.name(),
                asset.sourceType(),
                mimeType,
                sizeBytes,
                extension,
                rule.renderer(),
                status,
                contentUrl,
                contentUrl(assetExternalId, "attachment"),
                readable != null,
                reason,
                asset.updatedAt().toInstant(java.time.ZoneOffset.UTC));
    }

    private PreviewRule resolveRule(String rawMimeType, String extension) {
        String mimeType = rawMimeType == null
                ? ""
                : rawMimeType.toLowerCase(Locale.ROOT).split(";", 2)[0].trim();
        if (mimeType.equals("application/pdf") || trustedFallback(mimeType, extension, "pdf")) {
            return new PreviewRule("pdf", DOCUMENT_LIMIT);
        }
        if (mimeType.startsWith("image/") && !mimeType.equals("image/svg+xml")) {
            return new PreviewRule("image", IMAGE_LIMIT);
        }
        if (mimeType.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                || trustedFallback(mimeType, extension, "docx")) {
            return new PreviewRule("docx", DOCUMENT_LIMIT);
        }
        if (mimeType.equals("application/vnd.openxmlformats-officedocument.presentationml.presentation")
                || trustedFallback(mimeType, extension, "pptx")) {
            return new PreviewRule("pptx", DOCUMENT_LIMIT);
        }
        if (mimeType.equals("application/vnd.examinsight.mindmap+json")
                || trustedFallback(mimeType, extension, "mindmap.json")) {
            return new PreviewRule("mindmap", MINDMAP_LIMIT);
        }
        if (mimeType.equals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                || trustedFallback(mimeType, extension, "xlsx")) {
            return new PreviewRule("xlsx", DOCUMENT_LIMIT);
        }
        if (mimeType.equals("text/csv") || trustedFallback(mimeType, extension, "csv")) {
            return new PreviewRule("csv", DOCUMENT_LIMIT);
        }
        if (extension.equals("md") && (mimeType.startsWith("text/") || isGenericMime(mimeType))) {
            return new PreviewRule("markdown", TEXT_LIMIT);
        }
        if (mimeType.startsWith("text/") || trustedFallback(mimeType, extension, "txt")) {
            return new PreviewRule("text", TEXT_LIMIT);
        }
        if (mimeType.startsWith("audio/")) {
            return new PreviewRule("audio", AUDIO_LIMIT);
        }
        return new PreviewRule("unsupported", Long.MAX_VALUE);
    }

    private boolean trustedFallback(String mimeType, String extension, String expectedExtension) {
        return extension.equals(expectedExtension) && isGenericMime(mimeType);
    }

    private boolean isGenericMime(String mimeType) {
        return mimeType.isBlank() || mimeType.equals("application/octet-stream");
    }

    private String extensionOf(String name) {
        int separator = name.lastIndexOf('.');
        return separator < 0 || separator == name.length() - 1
                ? ""
                : name.substring(separator + 1).toLowerCase(Locale.ROOT);
    }

    private String contentUrl(String assetExternalId, String disposition) {
        return "/api/v2/assets/"
                + URLEncoder.encode(assetExternalId, StandardCharsets.UTF_8)
                + "/content?disposition="
                + disposition;
    }

    private record PreviewRule(String renderer, long maxBytes) {
    }
}
