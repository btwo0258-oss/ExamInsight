package com.example.llm.asset.service;

import com.example.llm.asset.api.AssetApiException;
import com.example.llm.asset.api.LibraryDtos;
import com.example.llm.asset.api.LibraryDtos.AssetDetail;
import com.example.llm.asset.api.LibraryDtos.AssetItem;
import com.example.llm.asset.api.LibraryDtos.AssetVersionView;
import com.example.llm.asset.api.LibraryDtos.KnowledgeBaseDetail;
import com.example.llm.asset.api.LibraryDtos.KnowledgeBaseItem;
import com.example.llm.asset.api.LibraryDtos.KnowledgeBaseReference;
import com.example.llm.asset.api.LibraryDtos.Page;
import com.example.llm.asset.api.LibraryDtos.PurgeJobView;
import com.example.llm.asset.repository.AssetLibraryRepository;
import com.example.llm.asset.repository.AssetLibraryRepository.AssetLifecycle;
import com.example.llm.asset.repository.AssetLibraryRepository.AssetRow;
import com.example.llm.asset.repository.AssetLibraryRepository.PurgeJobRow;
import com.example.llm.asset.repository.AssetLibraryRepository.VersionRow;
import com.example.llm.asset.repository.KnowledgeBaseLibraryRepository;
import com.example.llm.asset.repository.KnowledgeBaseLibraryRepository.KnowledgeBaseLifecycle;
import com.example.llm.asset.repository.KnowledgeBaseLibraryRepository.KnowledgeBaseRow;
import com.example.llm.asset.processing.index.EmbeddingRuntime;
import com.example.llm.auth.security.AuthCrypto;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
public class LibraryApplicationService {
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final AssetLibraryRepository assets;
    private final KnowledgeBaseLibraryRepository knowledgeBases;
    private final AuthCrypto crypto;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final EmbeddingRuntime embeddingRuntime;

    public LibraryApplicationService(
            AssetLibraryRepository assets,
            KnowledgeBaseLibraryRepository knowledgeBases,
            AuthCrypto crypto,
            @Qualifier("v2TransactionTemplate") TransactionTemplate transactions,
            EmbeddingRuntime embeddingRuntime,
            Clock clock) {
        this.assets = assets;
        this.knowledgeBases = knowledgeBases;
        this.crypto = crypto;
        this.transactions = transactions;
        this.embeddingRuntime = embeddingRuntime;
        this.clock = clock;
    }

    public Page<AssetItem> listAssets(long userId, String view, Integer limit, String cursor) {
        int pageSize = pageSize(limit);
        String status = statusForView(view);
        AssetLibraryRepository.PageCursor decoded = decodeAssetCursor(cursor);
        List<AssetRow> rows = assets.findPage(userId, status, decoded, pageSize + 1);
        return assetPage(rows, pageSize);
    }

    public AssetDetail getAsset(long userId, String assetExternalId) {
        AssetRow row = requireAsset(userId, assetExternalId);
        List<KnowledgeBaseReference> references = assets.findActiveKnowledgeBases(userId, row.id()).stream()
                .map(reference -> new KnowledgeBaseReference(
                        reference.externalId(), reference.name()))
                .toList();
        PurgeJobView purgeJob = assets.findLatestPurgeJob(userId, assetExternalId)
                .map(this::toPurgeJob)
                .orElse(null);
        return new AssetDetail(toAssetItem(row), references, purgeJob);
    }

    public AssetItem renameAsset(long userId, String assetExternalId, String requestedName) {
        String name = normalizeDisplayText(requestedName, 255, "资料名称");
        transactions.executeWithoutResult(status -> {
            AssetLifecycle asset = requireAssetForUpdate(userId, assetExternalId);
            assertNoActivePurge(userId, assetExternalId);
            assets.rename(asset.id(), name);
        });
        return toAssetItem(requireAsset(userId, assetExternalId));
    }

    public AssetItem moveAssetToTrash(long userId, String assetExternalId) {
        transactions.executeWithoutResult(status -> {
            AssetLifecycle asset = requireAssetForUpdate(userId, assetExternalId);
            if (asset.status().equals("TRASHED")) {
                return;
            }
            if (!asset.status().equals("ACTIVE") && !asset.status().equals("ARCHIVED")) {
                throw stateConflict("资料当前状态不能移入回收站。", asset.status());
            }
            assets.moveToTrash(asset.id(), asset.status(), now());
        });
        return toAssetItem(requireAsset(userId, assetExternalId));
    }

    public AssetItem restoreAsset(long userId, String assetExternalId) {
        transactions.executeWithoutResult(status -> {
            AssetLifecycle asset = requireAssetForUpdate(userId, assetExternalId);
            if (asset.status().equals("ACTIVE")) {
                return;
            }
            if (!asset.status().equals("TRASHED")) {
                throw stateConflict("资料当前状态不能恢复。", asset.status());
            }
            assertNoActivePurge(userId, assetExternalId);
            assets.restore(asset.id());
        });
        return toAssetItem(requireAsset(userId, assetExternalId));
    }

    public PurgeJobView requestAssetPurge(long userId, String assetExternalId) {
        PurgeJobRow job = Objects.requireNonNull(transactions.execute(status -> {
            AssetLifecycle asset = requireAssetForUpdate(userId, assetExternalId);
            if (!asset.status().equals("TRASHED")) {
                throw stateConflict("请先把资料移入回收站，再执行永久删除。", asset.status());
            }
            PurgeJobRow active = assets.findLatestPurgeJob(userId, assetExternalId)
                    .filter(candidate -> isActiveJob(candidate.status()))
                    .orElse(null);
            if (active != null) {
                return active;
            }
            String jobExternalId = crypto.newExternalId();
            String idempotencyKey = crypto.digest(
                    "asset-purge-job", assetExternalId + ":" + jobExternalId);
            return assets.enqueuePurge(
                    jobExternalId, userId, assetExternalId, idempotencyKey, now());
        }));
        return toPurgeJob(job);
    }

    public PurgeJobView getAssetPurgeJob(long userId, String assetExternalId) {
        return assets.findLatestPurgeJob(userId, assetExternalId)
                .map(this::toPurgeJob)
                .orElseThrow(() -> notFound("PURGE_JOB_NOT_FOUND", "未找到该资料的永久删除任务。"));
    }

    public Page<KnowledgeBaseItem> listKnowledgeBases(
            long userId,
            String view,
            Integer limit,
            String cursor) {
        int pageSize = pageSize(limit);
        String status = statusForView(view);
        KnowledgeBaseLibraryRepository.PageCursor decoded = decodeKnowledgeBaseCursor(cursor);
        List<KnowledgeBaseRow> rows = knowledgeBases.findPage(
                userId, status, decoded, pageSize + 1);
        return knowledgeBasePage(rows, pageSize);
    }

    public KnowledgeBaseDetail createKnowledgeBase(
            long userId,
            LibraryDtos.CreateKnowledgeBaseRequest request) {
        String name = normalizeDisplayText(request.name(), 160, "知识库名称");
        String normalizedName = normalizedName(name);
        String description = normalizeOptionalText(request.description(), 1000, "知识库描述");
        String externalId = crypto.newExternalId();
        try {
            transactions.executeWithoutResult(status -> {
                if (knowledgeBases.activeNameExists(userId, normalizedName, 0)) {
                    throw nameConflict();
                }
                knowledgeBases.insert(externalId, userId, name, normalizedName, description);
            });
        } catch (DuplicateKeyException exception) {
            throw nameConflict();
        }
        return new KnowledgeBaseDetail(toKnowledgeBaseItem(
                requireKnowledgeBase(userId, externalId)));
    }

    public KnowledgeBaseDetail getKnowledgeBase(long userId, String knowledgeBaseExternalId) {
        return new KnowledgeBaseDetail(toKnowledgeBaseItem(
                requireKnowledgeBase(userId, knowledgeBaseExternalId)));
    }

    public KnowledgeBaseDetail updateKnowledgeBase(
            long userId,
            String knowledgeBaseExternalId,
            LibraryDtos.UpdateKnowledgeBaseRequest request) {
        if (request.name() == null && request.description() == null) {
            throw badRequest("EMPTY_KNOWLEDGE_BASE_UPDATE", "请至少修改知识库名称或描述。", Map.of());
        }
        try {
            transactions.executeWithoutResult(status -> {
                KnowledgeBaseLifecycle current = requireKnowledgeBaseForUpdate(
                        userId, knowledgeBaseExternalId);
                String name = request.name() == null
                        ? current.name()
                        : normalizeDisplayText(request.name(), 160, "知识库名称");
                String normalizedName = normalizedName(name);
                String description = request.description() == null
                        ? current.description()
                        : normalizeOptionalText(request.description(), 1000, "知识库描述");
                if (current.status().equals("ACTIVE")
                        && knowledgeBases.activeNameExists(userId, normalizedName, current.id())) {
                    throw nameConflict();
                }
                knowledgeBases.update(current.id(), name, normalizedName, description);
            });
        } catch (DuplicateKeyException exception) {
            throw nameConflict();
        }
        return getKnowledgeBase(userId, knowledgeBaseExternalId);
    }

    public KnowledgeBaseDetail moveKnowledgeBaseToTrash(
            long userId,
            String knowledgeBaseExternalId) {
        transactions.executeWithoutResult(status -> {
            KnowledgeBaseLifecycle knowledgeBase = requireKnowledgeBaseForUpdate(
                    userId, knowledgeBaseExternalId);
            if (knowledgeBase.status().equals("TRASHED")) {
                return;
            }
            if (!knowledgeBase.status().equals("ACTIVE")
                    && !knowledgeBase.status().equals("ARCHIVED")) {
                throw stateConflict("知识库当前状态不能移入回收站。", knowledgeBase.status());
            }
            knowledgeBases.moveToTrash(knowledgeBase.id(), knowledgeBase.status(), now());
        });
        return getKnowledgeBase(userId, knowledgeBaseExternalId);
    }

    public KnowledgeBaseDetail restoreKnowledgeBase(
            long userId,
            String knowledgeBaseExternalId) {
        try {
            transactions.executeWithoutResult(status -> {
                KnowledgeBaseLifecycle knowledgeBase = requireKnowledgeBaseForUpdate(
                        userId, knowledgeBaseExternalId);
                if (knowledgeBase.status().equals("ACTIVE")) {
                    return;
                }
                if (!knowledgeBase.status().equals("TRASHED")) {
                    throw stateConflict("知识库当前状态不能恢复。", knowledgeBase.status());
                }
                if (knowledgeBases.activeNameExists(
                        userId, knowledgeBase.normalizedName(), knowledgeBase.id())) {
                    throw nameConflict();
                }
                knowledgeBases.restore(knowledgeBase.id());
            });
        } catch (DuplicateKeyException exception) {
            throw nameConflict();
        }
        return getKnowledgeBase(userId, knowledgeBaseExternalId);
    }

    public void purgeKnowledgeBase(long userId, String knowledgeBaseExternalId) {
        transactions.executeWithoutResult(status -> {
            KnowledgeBaseLifecycle knowledgeBase = requireKnowledgeBaseForUpdate(
                    userId, knowledgeBaseExternalId);
            if (!knowledgeBase.status().equals("TRASHED")) {
                throw stateConflict("请先把知识库移入回收站，再执行永久删除。", knowledgeBase.status());
            }
            knowledgeBases.purge(knowledgeBase.id());
        });
    }

    public void addAssetToKnowledgeBase(
            long userId,
            String knowledgeBaseExternalId,
            String assetExternalId) {
        transactions.executeWithoutResult(status -> {
            KnowledgeBaseLifecycle knowledgeBase = requireActiveKnowledgeBaseForUpdate(
                    userId, knowledgeBaseExternalId);
            var asset = knowledgeBases.findActiveAssetForUpdate(userId, assetExternalId)
                    .orElseThrow(() -> notFound(
                            "ASSET_NOT_FOUND", "资料不存在或当前不能加入知识库。"));
            knowledgeBases.addAsset(knowledgeBase.id(), asset.id(), userId);
        });
    }

    public void removeAssetFromKnowledgeBase(
            long userId,
            String knowledgeBaseExternalId,
            String assetExternalId) {
        transactions.executeWithoutResult(status -> {
            KnowledgeBaseLifecycle knowledgeBase = requireActiveKnowledgeBaseForUpdate(
                    userId, knowledgeBaseExternalId);
            var asset = knowledgeBases.findActiveAssetForUpdate(userId, assetExternalId)
                    .orElseThrow(() -> notFound(
                            "ASSET_NOT_FOUND", "资料不存在或当前不能从知识库移除。"));
            knowledgeBases.removeAsset(knowledgeBase.id(), asset.id(), userId);
        });
    }

    public Page<AssetItem> listKnowledgeBaseAssets(
            long userId,
            String knowledgeBaseExternalId,
            Integer limit,
            String cursor) {
        int pageSize = pageSize(limit);
        KnowledgeBaseRow knowledgeBase = requireKnowledgeBase(userId, knowledgeBaseExternalId);
        if (!knowledgeBase.status().equals("ACTIVE")) {
            throw stateConflict("回收站中的知识库不能浏览资料。", knowledgeBase.status());
        }
        List<AssetRow> rows = assets.findKnowledgeBaseAssets(
                userId,
                knowledgeBase.id(),
                decodeAssetCursor(cursor),
                pageSize + 1);
        return assetPage(rows, pageSize);
    }

    private Page<AssetItem> assetPage(List<AssetRow> rows, int pageSize) {
        boolean hasMore = rows.size() > pageSize;
        List<AssetRow> visible = hasMore ? rows.subList(0, pageSize) : rows;
        List<AssetItem> items = visible.stream().map(this::toAssetItem).toList();
        String nextCursor = hasMore && !visible.isEmpty()
                ? encodeCursor("A", visible.get(visible.size() - 1).updatedAt(),
                visible.get(visible.size() - 1).id())
                : null;
        return new Page<>(items, nextCursor);
    }

    private Page<KnowledgeBaseItem> knowledgeBasePage(
            List<KnowledgeBaseRow> rows,
            int pageSize) {
        boolean hasMore = rows.size() > pageSize;
        List<KnowledgeBaseRow> visible = hasMore ? rows.subList(0, pageSize) : rows;
        List<KnowledgeBaseItem> items = visible.stream().map(this::toKnowledgeBaseItem).toList();
        String nextCursor = hasMore && !visible.isEmpty()
                ? encodeCursor("K", visible.get(visible.size() - 1).updatedAt(),
                visible.get(visible.size() - 1).id())
                : null;
        return new Page<>(items, nextCursor);
    }

    private AssetItem toAssetItem(AssetRow row) {
        return new AssetItem(
                row.externalId(), row.name(), row.assetType(), row.sourceType(), row.status(),
                row.knowledgeBaseCount(), toVersion(row.version()), instant(row.trashStartedAt()),
                instant(row.createdAt()), instant(row.updatedAt()));
    }

    private AssetVersionView toVersion(VersionRow row) {
        if (row == null) {
            return null;
        }
        return new AssetVersionView(
                row.externalId(), row.versionNumber(), row.status(), row.mimeType(), row.sizeBytes(),
                row.chunkCount(), row.indexedChunkCount(), row.failedChunkCount(), indexStatus(row),
                instant(row.createdAt()));
    }

    private String indexStatus(VersionRow version) {
        if (!version.status().equals("READY")) {
            return switch (version.status()) {
                case "FAILED", "REJECTED", "WITHDRAWN" -> "UNAVAILABLE";
                default -> "WAITING_FOR_PARSE";
            };
        }
        if (version.chunkCount() == 0) {
            return "EMPTY";
        }
        if (!embeddingRuntime.isSemanticIndexAvailable()) {
            return "KEYWORD_ONLY";
        }
        if (version.indexedChunkCount() >= version.chunkCount()) {
            return "READY";
        }
        if (version.failedChunkCount() > 0) {
            return "DEGRADED";
        }
        return "PROCESSING";
    }

    private KnowledgeBaseItem toKnowledgeBaseItem(KnowledgeBaseRow row) {
        return new KnowledgeBaseItem(
                row.externalId(), row.name(), row.description(), row.status(), row.assetCount(),
                instant(row.trashStartedAt()), instant(row.createdAt()), instant(row.updatedAt()));
    }

    private PurgeJobView toPurgeJob(PurgeJobRow row) {
        return new PurgeJobView(
                row.externalId(), row.status(), row.errorCode(),
                instant(row.createdAt()), instant(row.finishedAt()));
    }

    private AssetRow requireAsset(long userId, String externalId) {
        return assets.findByExternalId(userId, externalId)
                .orElseThrow(() -> notFound("ASSET_NOT_FOUND", "资料不存在。"));
    }

    private AssetLifecycle requireAssetForUpdate(long userId, String externalId) {
        return assets.findForUpdate(userId, externalId)
                .orElseThrow(() -> notFound("ASSET_NOT_FOUND", "资料不存在。"));
    }

    private KnowledgeBaseRow requireKnowledgeBase(long userId, String externalId) {
        return knowledgeBases.findByExternalId(userId, externalId)
                .orElseThrow(() -> notFound("KNOWLEDGE_BASE_NOT_FOUND", "知识库不存在。"));
    }

    private KnowledgeBaseLifecycle requireKnowledgeBaseForUpdate(long userId, String externalId) {
        return knowledgeBases.findForUpdate(userId, externalId)
                .orElseThrow(() -> notFound("KNOWLEDGE_BASE_NOT_FOUND", "知识库不存在。"));
    }

    private KnowledgeBaseLifecycle requireActiveKnowledgeBaseForUpdate(long userId, String externalId) {
        KnowledgeBaseLifecycle knowledgeBase = requireKnowledgeBaseForUpdate(userId, externalId);
        if (!knowledgeBase.status().equals("ACTIVE")) {
            throw stateConflict("知识库当前状态不能修改资料。", knowledgeBase.status());
        }
        return knowledgeBase;
    }

    private void assertNoActivePurge(long userId, String assetExternalId) {
        if (assets.hasActivePurgeJob(userId, assetExternalId)) {
            throw new AssetApiException(
                    HttpStatus.CONFLICT,
                    "ASSET_PURGE_IN_PROGRESS",
                    "资料正在永久删除，当前不能执行该操作。",
                    Map.of());
        }
    }

    private int pageSize(Integer value) {
        int size = value == null ? DEFAULT_PAGE_SIZE : value;
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw badRequest(
                    "INVALID_PAGE_SIZE", "每页数量必须在 1 到 100 之间。", Map.of());
        }
        return size;
    }

    private String statusForView(String view) {
        if (view == null || view.isBlank() || view.equalsIgnoreCase("library")) {
            return "ACTIVE";
        }
        if (view.equalsIgnoreCase("trash")) {
            return "TRASHED";
        }
        throw badRequest(
                "INVALID_LIBRARY_VIEW", "view 只能是 library 或 trash。", Map.of());
    }

    private String normalizeDisplayText(String value, int maxLength, String fieldLabel) {
        if (value == null) {
            throw badRequest("INVALID_DISPLAY_TEXT", fieldLabel + "不能为空。", Map.of());
        }
        String normalized = Normalizer.normalize(value.trim(), Normalizer.Form.NFC);
        if (normalized.isEmpty() || normalized.length() > maxLength
                || normalized.codePoints().anyMatch(Character::isISOControl)) {
            throw badRequest("INVALID_DISPLAY_TEXT", fieldLabel + "格式不正确。", Map.of());
        }
        return normalized;
    }

    private String normalizeOptionalText(String value, int maxLength, String fieldLabel) {
        if (value == null) {
            return null;
        }
        String normalized = Normalizer.normalize(value.trim(), Normalizer.Form.NFC);
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > maxLength
                || normalized.codePoints().anyMatch(Character::isISOControl)) {
            throw badRequest("INVALID_DISPLAY_TEXT", fieldLabel + "格式不正确。", Map.of());
        }
        return normalized;
    }

    private String normalizedName(String name) {
        return Normalizer.normalize(name, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
    }

    private AssetLibraryRepository.PageCursor decodeAssetCursor(String cursor) {
        DecodedCursor decoded = decodeCursor(cursor, "A");
        return decoded == null ? null : new AssetLibraryRepository.PageCursor(
                decoded.updatedAt(), decoded.id());
    }

    private KnowledgeBaseLibraryRepository.PageCursor decodeKnowledgeBaseCursor(String cursor) {
        DecodedCursor decoded = decodeCursor(cursor, "K");
        return decoded == null ? null : new KnowledgeBaseLibraryRepository.PageCursor(
                decoded.updatedAt(), decoded.id());
    }

    private String encodeCursor(String type, LocalDateTime updatedAt, long id) {
        String value = type + ":" + updatedAt.toInstant(ZoneOffset.UTC).toEpochMilli() + ":" + id;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.US_ASCII));
    }

    private DecodedCursor decodeCursor(String cursor, String requiredType) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        if (cursor.length() > 256) {
            throw invalidCursor();
        }
        try {
            String value = new String(
                    Base64.getUrlDecoder().decode(cursor), StandardCharsets.US_ASCII);
            String[] parts = value.split(":", -1);
            if (parts.length != 3 || !parts[0].equals(requiredType)) {
                throw invalidCursor();
            }
            long epochMillis = Long.parseLong(parts[1]);
            long id = Long.parseLong(parts[2]);
            if (id <= 0) {
                throw invalidCursor();
            }
            return new DecodedCursor(
                    LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneOffset.UTC), id);
        } catch (AssetApiException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw invalidCursor();
        }
    }

    private AssetApiException invalidCursor() {
        return badRequest("INVALID_PAGE_CURSOR", "分页游标无效，请重新加载列表。", Map.of());
    }

    private boolean isActiveJob(String status) {
        return status.equals("QUEUED") || status.equals("RUNNING")
                || status.equals("RETRY_WAIT") || status.equals("CANCELLING");
    }

    private AssetApiException nameConflict() {
        return new AssetApiException(
                HttpStatus.CONFLICT,
                "KNOWLEDGE_BASE_NAME_CONFLICT",
                "已有同名知识库，请使用其他名称。",
                Map.of());
    }

    private AssetApiException stateConflict(String message, String currentStatus) {
        return new AssetApiException(
                HttpStatus.CONFLICT,
                "LIBRARY_STATE_CONFLICT",
                message,
                Map.of("status", currentStatus));
    }

    private AssetApiException notFound(String code, String message) {
        return new AssetApiException(HttpStatus.NOT_FOUND, code, message, Map.of());
    }

    private AssetApiException badRequest(String code, String message, Map<String, Object> details) {
        return new AssetApiException(HttpStatus.BAD_REQUEST, code, message, details);
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private Instant instant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    private record DecodedCursor(LocalDateTime updatedAt, long id) {
    }
}
