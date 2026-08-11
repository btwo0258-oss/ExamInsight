package com.example.llm.asset.service;

import com.example.llm.asset.api.AssetApiException;
import com.example.llm.asset.api.UploadDtos;
import com.example.llm.asset.config.AssetStorageProperties;
import com.example.llm.asset.repository.UploadRepository;
import com.example.llm.asset.repository.UploadRepository.CompletionRecord;
import com.example.llm.asset.repository.UploadRepository.UploadSession;
import com.example.llm.asset.security.FileTypePolicy;
import com.example.llm.asset.security.FileTypePolicy.DeclaredFile;
import com.example.llm.asset.security.FileTypePolicy.InspectedFile;
import com.example.llm.asset.storage.ObjectStorageGateway;
import com.example.llm.asset.storage.ObjectStorageGateway.StoredObject;
import com.example.llm.asset.storage.StorageObjectKeyCipher;
import com.example.llm.auth.security.AuthCrypto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Service
public class UploadApplicationService {
    private static final int LOCK_STRIPES = 64;

    private final UploadRepository repository;
    private final ObjectStorageGateway storage;
    private final StorageObjectKeyCipher objectKeyCipher;
    private final FileTypePolicy fileTypePolicy;
    private final AssetStorageProperties properties;
    private final AuthCrypto crypto;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final ReentrantLock[] uploadLocks = new ReentrantLock[LOCK_STRIPES];

    public UploadApplicationService(
            UploadRepository repository,
            ObjectStorageGateway storage,
            StorageObjectKeyCipher objectKeyCipher,
            FileTypePolicy fileTypePolicy,
            AssetStorageProperties properties,
            AuthCrypto crypto,
            ObjectMapper objectMapper,
            @Qualifier("v2TransactionTemplate") TransactionTemplate transactions,
            Clock clock) {
        this.repository = repository;
        this.storage = storage;
        this.objectKeyCipher = objectKeyCipher;
        this.fileTypePolicy = fileTypePolicy;
        this.properties = properties;
        this.crypto = crypto;
        this.objectMapper = objectMapper;
        this.transactions = transactions;
        this.clock = clock;
        for (int index = 0; index < uploadLocks.length; index++) {
            uploadLocks[index] = new ReentrantLock();
        }
    }

    public UploadDtos.UploadSessionResponse createUpload(
            long userId,
            UploadDtos.CreateUploadRequest request) {
        DeclaredFile declaration = fileTypePolicy.validateDeclaration(
                request.originalFilename(), request.declaredMime(), request.expectedSize());
        String expectedSha256 = normalizeSha256(request.expectedSha256());
        LocalDateTime now = now();

        UploadSession session = Objects.requireNonNull(transactions.execute(status -> {
            String userStatus = repository.lockUserStatus(userId)
                    .orElseThrow(() -> notFound("USER_NOT_FOUND", "用户不存在。"));
            if (!userStatus.equals("ACTIVE") && !userStatus.equals("LIMITED")) {
                throw new AssetApiException(
                        HttpStatus.FORBIDDEN,
                        "ACCOUNT_NOT_AVAILABLE",
                        "当前账号状态不能上传资料。");
            }

            repository.expireStaleUploads(userId, now);
            var existing = repository.findByUserAndUploadKey(userId, request.uploadKey());
            if (existing.isPresent()) {
                assertIdempotentCreate(existing.get(), declaration, request.expectedSize(), expectedSha256);
                return existing.get();
            }
            if (repository.countConcurrentUploads(userId, now) >= properties.getMaxConcurrentUploads()) {
                throw new AssetApiException(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "TOO_MANY_ACTIVE_UPLOADS",
                        "同时上传的文件过多，请等待当前文件完成后再试。",
                        Map.of("maximumConcurrentUploads", properties.getMaxConcurrentUploads()));
            }

            String externalId = crypto.newExternalId();
            LocalDateTime expiresAt = now.plus(properties.getUploadSessionTtl());
            long id = repository.insertUploadSession(
                    externalId,
                    userId,
                    request.uploadKey(),
                    declaration.originalFilename(),
                    declaration.declaredMime(),
                    request.expectedSize(),
                    expectedSha256,
                    properties.getPartSize(),
                    expiresAt);
            return new UploadSession(
                    id, externalId, userId, request.uploadKey(), declaration.originalFilename(),
                    declaration.declaredMime(), request.expectedSize(), expectedSha256,
                    "INITIATED", properties.getPartSize(), 0, expiresAt, null, 0);
        }));
        return toSessionResponse(session);
    }

    public UploadDtos.UploadPartResponse uploadPart(
            long userId,
            String uploadExternalId,
            int partNumber,
            InputStream content) {
        ReentrantLock lock = lockFor(uploadExternalId);
        lock.lock();
        try {
            UploadSession session = loadSessionForActiveOperation(userId, uploadExternalId);
            int partCount = expectedPartCount(session.expectedSize(), session.partSize());
            if (partNumber < 1 || partNumber > partCount) {
                throw new AssetApiException(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_PART_NUMBER",
                        "分片序号超出该文件的有效范围。",
                        Map.of("expectedPartCount", partCount));
            }
            long expectedPartSize = expectedPartSize(session, partNumber, partCount);
            try {
                storage.putPart(uploadExternalId, partNumber, expectedPartSize, content);
                long uploadedBytes = storage.uploadedBytes(uploadExternalId);
                ProgressUpdate progress = Objects.requireNonNull(transactions.execute(status -> {
                    UploadSession current = findForUpdate(userId, uploadExternalId);
                    if (isExpired(current)) {
                        repository.markTerminal(current.id(), "EXPIRED");
                        return new ProgressUpdate(current, true);
                    }
                    if (!isWritable(current.status())) {
                        throw invalidState(current.status());
                    }
                    if (uploadedBytes > current.expectedSize()) {
                        throw new AssetApiException(
                                HttpStatus.CONFLICT,
                                "UPLOAD_SIZE_EXCEEDED",
                                "已上传内容超过预期文件大小。");
                    }
                    repository.updateProgress(current.id(), uploadedBytes);
                    return new ProgressUpdate(withProgress(current, uploadedBytes), false);
                }));
                if (progress.expired()) {
                    safeAbort(uploadExternalId);
                    throw expiredUpload();
                }
                UploadSession updated = progress.session();
                return new UploadDtos.UploadPartResponse(
                        updated.externalId(), updated.status(), partNumber,
                        updated.uploadedBytes(), updated.expectedSize());
            } catch (AssetApiException exception) {
                throw exception;
            } catch (IOException exception) {
                if (exception.getMessage() != null
                        && exception.getMessage().contains("length does not match")) {
                    throw new AssetApiException(
                            HttpStatus.BAD_REQUEST,
                            "PART_SIZE_MISMATCH",
                            "分片大小与该分片的预期大小不一致。",
                            Map.of("expectedBytes", expectedPartSize));
                }
                throw storageUnavailable(exception);
            }
        } finally {
            lock.unlock();
        }
    }

    public UploadDtos.UploadCompletionResponse completeUpload(long userId, String uploadExternalId) {
        ReentrantLock lock = lockFor(uploadExternalId);
        lock.lock();
        try {
            CompletionPreparation preparation = Objects.requireNonNull(transactions.execute(status -> {
                UploadSession session = findForUpdate(userId, uploadExternalId);
                if (session.status().equals("COMPLETED")) {
                    return new CompletionPreparation(session, true, false);
                }
                if (isExpired(session)) {
                    repository.markTerminal(session.id(), "EXPIRED");
                    return new CompletionPreparation(session, false, true);
                }
                if (session.status().equals("COMPLETING")) {
                    throw new AssetApiException(
                            HttpStatus.CONFLICT,
                            "UPLOAD_COMPLETION_IN_PROGRESS",
                            "文件正在完成处理中，请稍后重试。",
                            Map.of("status", session.status()));
                }
                if (!isWritable(session.status())) {
                    throw invalidState(session.status());
                }
                if (session.uploadedBytes() != session.expectedSize()) {
                    throw new AssetApiException(
                            HttpStatus.CONFLICT,
                            "UPLOAD_INCOMPLETE",
                            "文件分片尚未全部上传。",
                            Map.of("uploadedBytes", session.uploadedBytes(),
                                    "expectedSize", session.expectedSize()));
                }
                repository.markCompleting(session.id());
                return new CompletionPreparation(session, false, false);
            }));

            if (preparation.alreadyCompleted()) {
                return completionResponse(userId, uploadExternalId);
            }
            if (preparation.expired()) {
                safeAbort(uploadExternalId);
                throw expiredUpload();
            }

            StoredObject storedObject = null;
            try {
                UploadSession session = preparation.session();
                int partCount = expectedPartCount(session.expectedSize(), session.partSize());
                storedObject = storage.complete(uploadExternalId, session.expectedSize(), partCount);
                if (session.expectedSha256() != null
                        && !session.expectedSha256().equals(storedObject.sha256())) {
                    throw new AssetApiException(
                            HttpStatus.UNPROCESSABLE_ENTITY,
                            "FILE_HASH_MISMATCH",
                            "文件完整性校验失败，请重新上传。",
                            Map.of());
                }

                DeclaredFile declaration = fileTypePolicy.validateDeclaration(
                        session.originalFilename(), session.declaredMime(), storedObject.sizeBytes());
                StoredObject objectForInspection = storedObject;
                InspectedFile inspected = fileTypePolicy.inspect(
                        declaration,
                        storedObject.sizeBytes(),
                        () -> storage.open(objectForInspection.objectKey()));

                LocalDateTime completedAt = now();
                if (completedAt.isAfter(session.expiresAt())) {
                    markTerminal(userId, uploadExternalId, "EXPIRED");
                    safeDelete(storedObject.objectKey());
                    throw expiredUpload();
                }

                StoredObject objectToPersist = storedObject;
                Objects.requireNonNull(transactions.execute(status -> {
                    UploadSession current = findForUpdate(userId, uploadExternalId);
                    if (current.status().equals("COMPLETED")) {
                        return Boolean.TRUE;
                    }
                    if (!current.status().equals("COMPLETING")) {
                        throw invalidState(current.status());
                    }
                    persistCompletion(current, objectToPersist, inspected, completedAt);
                    return Boolean.TRUE;
                }));
                return completionResponse(userId, uploadExternalId);
            } catch (AssetApiException exception) {
                if (storedObject != null) {
                    safeDelete(storedObject.objectKey());
                }
                if (!exception.code().equals("UPLOAD_EXPIRED")) {
                    markFailedUnlessFinal(userId, uploadExternalId);
                }
                throw exception;
            } catch (IOException exception) {
                if (storedObject != null) {
                    safeDelete(storedObject.objectKey());
                }
                markFailedUnlessFinal(userId, uploadExternalId);
                throw storageUnavailable(exception);
            } catch (RuntimeException exception) {
                if (storedObject != null) {
                    safeDelete(storedObject.objectKey());
                }
                markFailedUnlessFinal(userId, uploadExternalId);
                throw exception;
            }
        } finally {
            lock.unlock();
        }
    }

    public void abortUpload(long userId, String uploadExternalId) {
        ReentrantLock lock = lockFor(uploadExternalId);
        lock.lock();
        try {
            boolean shouldDeleteParts = Boolean.TRUE.equals(transactions.execute(status -> {
                UploadSession session = findForUpdate(userId, uploadExternalId);
                if (session.status().equals("COMPLETED")) {
                    throw invalidState(session.status());
                }
                if (session.status().equals("ABORTED") || session.status().equals("EXPIRED")) {
                    return false;
                }
                repository.markTerminal(session.id(), "ABORTED");
                return true;
            }));
            if (shouldDeleteParts) {
                try {
                    storage.abortUpload(uploadExternalId);
                } catch (IOException exception) {
                    throw storageUnavailable(exception);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    private void persistCompletion(
            UploadSession session,
            StoredObject storedObject,
            InspectedFile inspected,
            LocalDateTime completedAt) {
        String storageObjectExternalId = crypto.newExternalId();
        byte[] encryptedObjectKey = objectKeyCipher.encrypt(storedObject.objectKey());
        String objectKeyHash = crypto.digest("storage-object-key", storedObject.objectKey());
        long storageObjectId = repository.insertStorageObject(
                storageObjectExternalId,
                session.userId(),
                storedObject.bucketKey(),
                encryptedObjectKey,
                objectKeyHash,
                storedObject.sha256(),
                storedObject.sizeBytes(),
                inspected.canonicalMime());

        String assetExternalId = crypto.newExternalId();
        long assetId = repository.insertAsset(
                assetExternalId, session.userId(), session.originalFilename());
        repository.insertAssetVersion(
                crypto.newExternalId(),
                assetId,
                session.id(),
                storageObjectId,
                storedObject.sha256(),
                inspected.canonicalMime(),
                storedObject.sizeBytes(),
                session.userId());
        repository.insertSecurityScanJob(
                crypto.newExternalId(),
                session.userId(),
                storageObjectExternalId,
                securityScanPayload(storageObjectExternalId, assetExternalId),
                completedAt);
        repository.markCompleted(session.id(), completedAt);
    }

    private String securityScanPayload(String storageObjectExternalId, String assetExternalId) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "storageObjectId", storageObjectExternalId,
                    "assetId", assetExternalId));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize security scan job", exception);
        }
    }

    private UploadDtos.UploadCompletionResponse completionResponse(long userId, String uploadExternalId) {
        CompletionRecord record = repository.findCompletion(userId, uploadExternalId)
                .orElseThrow(() -> new IllegalStateException("Completed upload has no persisted asset graph"));
        return new UploadDtos.UploadCompletionResponse(
                record.uploadExternalId(),
                record.uploadStatus(),
                new UploadDtos.AssetSummary(
                        record.assetExternalId(), record.assetName(), record.assetStatus()),
                new UploadDtos.VersionSummary(
                        record.versionExternalId(), record.versionNumber(), record.versionStatus(),
                        record.mimeType(), record.sizeBytes(), record.sha256()),
                new UploadDtos.JobSummary(
                        record.jobExternalId(), record.jobStatus(), record.stageKey()));
    }

    private UploadSession loadSessionForActiveOperation(long userId, String uploadExternalId) {
        OperationPreparation preparation = Objects.requireNonNull(transactions.execute(status -> {
            UploadSession session = findForUpdate(userId, uploadExternalId);
            if (isExpired(session)) {
                repository.markTerminal(session.id(), "EXPIRED");
                return new OperationPreparation(session, true);
            }
            if (!isWritable(session.status())) {
                throw invalidState(session.status());
            }
            return new OperationPreparation(session, false);
        }));
        if (preparation.expired()) {
            safeAbort(uploadExternalId);
            throw expiredUpload();
        }
        return preparation.session();
    }

    private UploadSession findForUpdate(long userId, String uploadExternalId) {
        return repository.findByExternalIdForUpdate(userId, uploadExternalId)
                .orElseThrow(() -> notFound("UPLOAD_NOT_FOUND", "上传任务不存在。"));
    }

    private void assertIdempotentCreate(
            UploadSession existing,
            DeclaredFile declaration,
            long expectedSize,
            String expectedSha256) {
        if (!existing.originalFilename().equals(declaration.originalFilename())
                || !Objects.equals(existing.declaredMime(), declaration.declaredMime())
                || existing.expectedSize() != expectedSize
                || !Objects.equals(existing.expectedSha256(), expectedSha256)) {
            throw new AssetApiException(
                    HttpStatus.CONFLICT,
                    "UPLOAD_KEY_CONFLICT",
                    "该 uploadKey 已用于另一份文件，请生成新的 uploadKey。",
                    Map.of());
        }
    }

    private boolean isExpired(UploadSession session) {
        return !session.status().equals("COMPLETED") && !now().isBefore(session.expiresAt());
    }

    private void markTerminal(long userId, String uploadExternalId, String terminalStatus) {
        transactions.executeWithoutResult(status -> {
            UploadSession session = findForUpdate(userId, uploadExternalId);
            if (!session.status().equals("COMPLETED")) {
                repository.markTerminal(session.id(), terminalStatus);
            }
        });
    }

    private void markFailedUnlessFinal(long userId, String uploadExternalId) {
        try {
            transactions.executeWithoutResult(status -> repository
                    .findByExternalIdForUpdate(userId, uploadExternalId)
                    .filter(session -> !session.status().equals("COMPLETED"))
                    .ifPresent(session -> repository.markTerminal(session.id(), "FAILED")));
        } catch (RuntimeException exception) {
            log.error("Unable to mark upload {} as failed", uploadExternalId, exception);
        }
    }

    private void safeAbort(String uploadExternalId) {
        try {
            storage.abortUpload(uploadExternalId);
        } catch (IOException exception) {
            log.warn("Unable to clean expired upload parts for {}", uploadExternalId, exception);
        }
    }

    private void safeDelete(String objectKey) {
        try {
            storage.deleteObject(objectKey);
        } catch (IOException exception) {
            log.error("Unable to clean private object after failed completion", exception);
        }
    }

    private UploadSession withProgress(UploadSession session, long uploadedBytes) {
        return new UploadSession(
                session.id(), session.externalId(), session.userId(), session.uploadKey(),
                session.originalFilename(), session.declaredMime(), session.expectedSize(),
                session.expectedSha256(), "UPLOADING", session.partSize(), uploadedBytes,
                session.expiresAt(), session.completedAt(), session.rowVersion() + 1);
    }

    private UploadDtos.UploadSessionResponse toSessionResponse(UploadSession session) {
        return new UploadDtos.UploadSessionResponse(
                session.externalId(), session.originalFilename(), session.status(),
                session.expectedSize(), session.uploadedBytes(), session.partSize(),
                expectedPartCount(session.expectedSize(), session.partSize()),
                session.expiresAt().toInstant(ZoneOffset.UTC));
    }

    private int expectedPartCount(long expectedSize, int partSize) {
        return Math.toIntExact((expectedSize + partSize - 1) / partSize);
    }

    private long expectedPartSize(UploadSession session, int partNumber, int partCount) {
        if (partNumber < partCount) {
            return session.partSize();
        }
        return session.expectedSize() - (long) session.partSize() * (partCount - 1);
    }

    private String normalizeSha256(String sha256) {
        return sha256 == null ? null : sha256.toLowerCase(Locale.ROOT);
    }

    private boolean isWritable(String status) {
        return status.equals("INITIATED") || status.equals("UPLOADING");
    }

    private ReentrantLock lockFor(String uploadExternalId) {
        int index = Math.floorMod(Objects.requireNonNullElse(uploadExternalId, "").hashCode(), LOCK_STRIPES);
        return uploadLocks[index];
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private AssetApiException expiredUpload() {
        return new AssetApiException(
                HttpStatus.GONE,
                "UPLOAD_EXPIRED",
                "上传任务已过期，请重新选择文件上传。",
                Map.of());
    }

    private AssetApiException invalidState(String status) {
        return new AssetApiException(
                HttpStatus.CONFLICT,
                "UPLOAD_STATE_CONFLICT",
                "当前上传状态不允许执行该操作。",
                Map.of("status", status));
    }

    private AssetApiException notFound(String code, String message) {
        return new AssetApiException(HttpStatus.NOT_FOUND, code, message, Map.of());
    }

    private AssetApiException storageUnavailable(IOException cause) {
        log.error("V2 private storage operation failed", cause);
        return new AssetApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "STORAGE_UNAVAILABLE",
                "文件存储暂时不可用，请稍后重试。",
                Map.of());
    }

    private record CompletionPreparation(
            UploadSession session,
            boolean alreadyCompleted,
            boolean expired) {
    }

    private record OperationPreparation(UploadSession session, boolean expired) {
    }

    private record ProgressUpdate(UploadSession session, boolean expired) {
    }
}
