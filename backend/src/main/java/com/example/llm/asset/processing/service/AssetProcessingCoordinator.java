package com.example.llm.asset.processing.service;

import com.example.llm.asset.processing.ProcessingFailure;
import com.example.llm.asset.processing.job.AssetJobRepository.AssetJob;
import com.example.llm.asset.processing.config.AssetProcessingProperties;
import com.example.llm.asset.processing.index.DocumentEmbeddingGateway;
import com.example.llm.asset.processing.index.EmbeddingRuntime;
import com.example.llm.asset.processing.index.VectorIndexGateway;
import com.example.llm.asset.processing.index.VectorIndexGateway.VectorDocument;
import com.example.llm.integration.ai.AiCapabilityRouter;
import com.example.llm.asset.processing.parse.AssetContentExtractor;
import com.example.llm.asset.processing.parse.AssetContentExtractor.ExtractedContent;
import com.example.llm.asset.processing.parse.StructuredTextChunker;
import com.example.llm.asset.processing.parse.StructuredTextChunker.TextChunk;
import com.example.llm.asset.processing.repository.AssetProcessingRepository;
import com.example.llm.asset.processing.repository.AssetPurgeRepository;
import com.example.llm.asset.processing.repository.AssetPurgeRepository.PurgeTarget;
import com.example.llm.asset.processing.repository.AssetIndexRepository;
import com.example.llm.asset.processing.repository.AssetIndexRepository.IndexTarget;
import com.example.llm.asset.processing.repository.AssetProcessingRepository.ParseTarget;
import com.example.llm.asset.processing.repository.AssetProcessingRepository.StorageTarget;
import com.example.llm.asset.processing.security.FileSecurityScanner;
import com.example.llm.asset.processing.security.FileSecurityScanner.ScanResult;
import com.example.llm.asset.storage.ObjectStorageGateway;
import com.example.llm.asset.storage.StorageObjectKeyCipher;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

@Service
public class AssetProcessingCoordinator {
    private final AssetProcessingRepository repository;
    private final AssetPurgeRepository purgeRepository;
    private final AssetIndexRepository indexRepository;
    private final FileSecurityScanner scanner;
    private final AssetContentExtractor extractor;
    private final StructuredTextChunker chunker;
    private final ObjectStorageGateway storage;
    private final StorageObjectKeyCipher objectKeyCipher;
    private final DocumentEmbeddingGateway embeddingGateway;
    private final VectorIndexGateway vectorIndexGateway;
    private final AssetProcessingProperties properties;
    private final EmbeddingRuntime embeddingRuntime;
    private final AiCapabilityRouter aiCapabilities;
    private final Clock clock;

    public AssetProcessingCoordinator(
            AssetProcessingRepository repository,
            AssetPurgeRepository purgeRepository,
            AssetIndexRepository indexRepository,
            FileSecurityScanner scanner,
            AssetContentExtractor extractor,
            StructuredTextChunker chunker,
            ObjectStorageGateway storage,
            StorageObjectKeyCipher objectKeyCipher,
            DocumentEmbeddingGateway embeddingGateway,
            VectorIndexGateway vectorIndexGateway,
            AssetProcessingProperties properties,
            EmbeddingRuntime embeddingRuntime,
            AiCapabilityRouter aiCapabilities,
            Clock clock) {
        this.repository = repository;
        this.purgeRepository = purgeRepository;
        this.indexRepository = indexRepository;
        this.scanner = scanner;
        this.extractor = extractor;
        this.chunker = chunker;
        this.storage = storage;
        this.objectKeyCipher = objectKeyCipher;
        this.embeddingGateway = embeddingGateway;
        this.vectorIndexGateway = vectorIndexGateway;
        this.properties = properties;
        this.embeddingRuntime = embeddingRuntime;
        this.aiCapabilities = aiCapabilities;
        this.clock = clock;
    }

    public Map<String, Object> execute(AssetJob job) {
        return switch (job.jobType()) {
            case "FILE_SECURITY_SCAN" -> scan(job);
            case "FILE_PARSE" -> parse(job);
            case "FILE_INDEX" -> index(job);
            case "ASSET_PURGE" -> purge(job);
            default -> throw ProcessingFailure.terminal("UNSUPPORTED_JOB_TYPE", "不支持的文件处理任务类型。");
        };
    }

    public void onExhausted(AssetJob job, ProcessingFailure failure) {
        if (job.jobType().equals("FILE_SECURITY_SCAN")) {
            repository.failSecurityScan(job.aggregateExternalId());
        } else if (job.jobType().equals("FILE_PARSE")) {
            repository.failParse(job.id(), failure.code(), now());
        } else if (job.jobType().equals("FILE_INDEX")) {
            indexRepository.failIndex(job.aggregateExternalId());
        }
    }

    public int reconcileIndexingWork() {
        return indexRepository.reconcileMissingWork(now());
    }

    public int reconcileRecoverableParseWork() {
        if (!aiCapabilities.isRecognitionConfigured()) {
            return 0;
        }
        return repository.reconcileObsoleteOcrFailures(now());
    }

    private Map<String, Object> scan(AssetJob job) {
        StorageTarget target = repository.prepareSecurityScan(
                        job.aggregateExternalId(), scanner.scannerKey(), scanner.scannerVersion())
                .orElseThrow(() -> ProcessingFailure.terminal("STORAGE_OBJECT_NOT_FOUND", "待扫描文件不存在。"));
        if (target.status().equals("AVAILABLE")) {
            repository.completeCleanScan(target.externalId(), now());
            return Map.of("status", "CLEAN", "idempotent", true);
        }
        if (target.status().equals("REJECTED")) {
            return Map.of("status", "REJECTED", "idempotent", true);
        }

        String objectKey = objectKeyCipher.decrypt(target.objectKeyCiphertext());
        try (InputStream input = storage.open(objectKey)) {
            ScanResult result = scanner.scan(input);
            if (result.clean()) {
                List<String> versions = repository.completeCleanScan(target.externalId(), now());
                return Map.of("status", "CLEAN", "parseVersions", versions);
            }
            repository.completeRejectedScan(target.externalId(), now());
            return Map.of("status", "REJECTED", "reason", "MALWARE_DETECTED");
        } catch (ProcessingFailure failure) {
            repository.resetSecurityScan(target.externalId());
            throw failure;
        } catch (IOException exception) {
            repository.resetSecurityScan(target.externalId());
            throw ProcessingFailure.retryable("STORAGE_UNAVAILABLE", "文件存储暂时不可用。", exception);
        }
    }

    private Map<String, Object> parse(AssetJob job) {
        ParseTarget target = repository.prepareParse(job.id(), job.aggregateExternalId())
                .orElseThrow(() -> ProcessingFailure.terminal("PARSE_TARGET_NOT_FOUND", "待解析文件版本不存在。"));
        if (target.parseStatus().equals("READY")) {
            reconcileIndexingWork();
            return Map.of("status", "READY", "idempotent", true);
        }
        if (target.parseStatus().equals("FAILED")) {
            throw ProcessingFailure.terminal("PARSE_ALREADY_FAILED", "文件解析已经失败。" );
        }

        String objectKey = objectKeyCipher.decrypt(target.objectKeyCiphertext());
        try (InputStream input = storage.open(objectKey)) {
            ExtractedContent extracted = extractor.extract(input, target.mimeType(), target.sizeBytes());
            List<TextChunk> chunks = chunker.split(extracted.text());
            int count = repository.completeParse(
                    target.parseResultId(), target.versionId(), target.assetId(), extracted, chunks, now());
            reconcileIndexingWork();
            return Map.of("status", "READY", "chunkCount", count);
        } catch (ProcessingFailure failure) {
            if (!failure.retryable()) {
                repository.failParse(job.id(), failure.code(), now());
            }
            throw failure;
        } catch (IOException exception) {
            throw ProcessingFailure.retryable("STORAGE_UNAVAILABLE", "文件存储暂时不可用。", exception);
        }
    }

    private Map<String, Object> index(AssetJob job) {
        if (!embeddingRuntime.isSemanticIndexAvailable()) {
            throw ProcessingFailure.terminal(
                    "EMBEDDING_NOT_CONFIGURED", "语义索引服务尚未配置，关键词检索仍可使用。");
        }
        IndexTarget target = indexRepository.prepareIndex(job.aggregateExternalId())
                .orElseThrow(() -> ProcessingFailure.terminal(
                        "INDEX_TARGET_NOT_FOUND", "待索引的文本切片不存在。"));
        if (target.embeddingStatus().equals("INDEXED")) {
            return Map.of("status", "INDEXED", "idempotent", true);
        }
        try {
            java.util.List<Float> vector = embeddingGateway.embedDocument(target.content());
            vectorIndexGateway.ensureIndex();
            vectorIndexGateway.upsert(new VectorDocument(
                    target.indexDocumentId(), target.chunkExternalId(), target.ownerUserId(),
                    target.assetId(), target.assetVersionId(), target.parseResultId(),
                    target.sequenceNo(), target.contentSha256(),
                    properties.getIndexing().getEmbeddingVersion(), vector));
            indexRepository.completeIndex(target.embeddingRecordId(), now());
            return Map.of("status", "INDEXED", "dimensions", vector.size());
        } catch (ProcessingFailure failure) {
            indexRepository.failIndex(target.chunkExternalId());
            throw failure;
        } catch (RuntimeException exception) {
            indexRepository.failIndex(target.chunkExternalId());
            throw ProcessingFailure.retryable(
                    "VECTOR_INDEXING_FAILED", "语义索引暂时失败，后台稍后会自动重试。", exception);
        }
    }

    private Map<String, Object> purge(AssetJob job) {
        PurgeTarget target = purgeRepository.prepare(job.aggregateExternalId());
        if (target.alreadyPurged()) {
            return Map.of("status", "PURGED", "idempotent", true);
        }
        vectorIndexGateway.deleteByAsset(target.ownerUserId(), target.assetId());
        try {
            for (var storageObject : target.storageObjects()) {
                String objectKey = objectKeyCipher.decrypt(storageObject.objectKeyCiphertext());
                storage.deleteObject(objectKey);
            }
        } catch (IOException exception) {
            throw ProcessingFailure.retryable(
                    "STORAGE_PURGE_FAILED",
                    "资料文件暂时无法清理，后台稍后会自动重试。",
                    exception);
        }
        purgeRepository.complete(target, now());
        return Map.of(
                "status", "PURGED",
                "storageObjects", target.storageObjects().size());
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
