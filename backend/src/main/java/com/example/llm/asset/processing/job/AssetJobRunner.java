package com.example.llm.asset.processing.job;

import com.example.llm.asset.processing.ProcessingFailure;
import com.example.llm.asset.processing.config.AssetProcessingProperties;
import com.example.llm.asset.processing.job.AssetJobRepository.AssetJob;
import com.example.llm.asset.processing.service.AssetProcessingCoordinator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
public class AssetJobRunner {
    private final AssetJobRepository jobs;
    private final AssetProcessingCoordinator coordinator;
    private final AssetProcessingProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final String workerId;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public AssetJobRunner(
            AssetJobRepository jobs,
            AssetProcessingCoordinator coordinator,
            AssetProcessingProperties properties,
            ObjectMapper objectMapper,
            Clock clock) {
        this.jobs = jobs;
        this.coordinator = coordinator;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.workerId = newWorkerId();
    }

    @Scheduled(fixedDelayString = "${app.v2.processing.poll-interval:PT2S}")
    public void poll() {
        if (!properties.isEnabled() || !running.compareAndSet(false, true)) {
            return;
        }
        try {
            try {
                int recovered = coordinator.reconcileRecoverableParseWork();
                if (recovered > 0) {
                    log.info("Requeued {} V2 image parse job(s) for the current OCR parser", recovered);
                }
            } catch (RuntimeException exception) {
                log.warn("Unable to reconcile recoverable V2 image parsing work", exception);
            }
            try {
                coordinator.reconcileIndexingWork();
            } catch (RuntimeException exception) {
                log.warn("Unable to reconcile V2 vector indexing work", exception);
            }
            LocalDateTime now = now();
            int reopened = jobs.extendLegacySecurityScanRetries(now);
            if (reopened > 0) {
                log.info("Reopened {} legacy V2 security scan job(s)", reopened);
            }
            for (AssetJob expired : jobs.recoverExpiredLeases(now)) {
                coordinator.onExhausted(
                        expired,
                        ProcessingFailure.retryable(
                                "JOB_LEASE_EXPIRED", "后台处理任务租约已过期。", null));
            }
            for (int index = 0; index < properties.getBatchSize(); index++) {
                AssetJob job = jobs.claimNext(
                                workerId, now(), now().plus(properties.getLeaseDuration()))
                        .orElse(null);
                if (job == null) {
                    break;
                }
                execute(job);
            }
        } catch (RuntimeException exception) {
            log.error("V2 asset job polling failed", exception);
        } finally {
            running.set(false);
        }
    }

    private void execute(AssetJob job) {
        try {
            Map<String, Object> result = coordinator.execute(job);
            jobs.succeed(job, workerId, json(result), now());
        } catch (ProcessingFailure failure) {
            boolean terminal = jobs.fail(
                    job, workerId, failure.code(), failure.getMessage(), failure.retryable(), now());
            if (terminal) {
                coordinator.onExhausted(job, failure);
            }
            if (failure.retryable()) {
                log.warn("V2 asset job {} will retry: {}", job.externalId(), failure.code());
            } else {
                log.info("V2 asset job {} failed safely: {}", job.externalId(), failure.code());
            }
        } catch (RuntimeException exception) {
            ProcessingFailure failure = ProcessingFailure.retryable(
                    "INTERNAL_PROCESSING_ERROR", "后台文件处理暂时失败。", exception);
            boolean terminal = jobs.fail(
                    job, workerId, failure.code(), failure.getMessage(), true, now());
            if (terminal) {
                coordinator.onExhausted(job, failure);
            }
            log.error("Unexpected V2 asset job failure: {}", job.externalId(), exception);
        }
    }

    private String json(Map<String, Object> result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize asset job result", exception);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    static String newWorkerId() {
        String value = "asset-worker-" + UUID.randomUUID();
        if (!StandardCharsets.US_ASCII.newEncoder().canEncode(value)) {
            throw new IllegalStateException("Asset worker ID must be ASCII");
        }
        return value;
    }
}
