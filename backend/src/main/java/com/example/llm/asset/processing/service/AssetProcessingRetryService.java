package com.example.llm.asset.processing.service;

import com.example.llm.asset.api.AssetApiException;
import com.example.llm.asset.processing.repository.AssetProcessingRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;

@Service
public class AssetProcessingRetryService {
    private final AssetProcessingRepository processing;
    private final Clock clock;

    public AssetProcessingRetryService(AssetProcessingRepository processing, Clock clock) {
        this.processing = processing;
        this.clock = clock;
    }

    public void retry(long userId, String assetExternalId) {
        var outcome = processing.retryFailedWork(
                userId, assetExternalId, LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        switch (outcome) {
            case PARSE_REQUEUED, INDEX_REQUEUED, ALREADY_PROCESSING -> {
                return;
            }
            case NOT_FOUND -> throw error(
                    HttpStatus.NOT_FOUND, "ASSET_NOT_FOUND", "资料不存在。", Map.of());
            case ASSET_NOT_ACTIVE -> throw error(
                    HttpStatus.CONFLICT, "ASSET_NOT_ACTIVE", "只有资料库中的有效资料可以重试。", Map.of());
            case NOTHING_TO_RETRY -> throw error(
                    HttpStatus.CONFLICT, "ASSET_NOT_RETRYABLE", "当前资料没有可重试的失败任务。", Map.of());
        }
    }

    private AssetApiException error(
            HttpStatus status,
            String code,
            String message,
            Map<String, Object> details) {
        return new AssetApiException(status, code, message, details);
    }
}
