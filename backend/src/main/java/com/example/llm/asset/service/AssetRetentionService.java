package com.example.llm.asset.service;

import com.example.llm.asset.processing.config.AssetProcessingProperties;
import com.example.llm.asset.repository.AssetLibraryRepository;
import com.example.llm.asset.repository.AssetLibraryRepository.AssetLifecycle;
import com.example.llm.asset.repository.KnowledgeBaseLibraryRepository;
import com.example.llm.asset.repository.KnowledgeBaseLibraryRepository.KnowledgeBaseLifecycle;
import com.example.llm.auth.security.AuthCrypto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Moves expired recycle-bin entries into the same controlled deletion paths as
 * an explicit user action.  Nothing is physically deleted directly by the
 * scheduler: files go through ASSET_PURGE, and knowledge-base references are
 * detached before the container is marked PURGED.
 */
@Slf4j
@Service
public class AssetRetentionService {
    private final AssetLibraryRepository assets;
    private final KnowledgeBaseLibraryRepository knowledgeBases;
    private final AssetProcessingProperties properties;
    private final AuthCrypto crypto;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public AssetRetentionService(
            AssetLibraryRepository assets,
            KnowledgeBaseLibraryRepository knowledgeBases,
            AssetProcessingProperties properties,
            AuthCrypto crypto,
            @Qualifier("v2TransactionTemplate") TransactionTemplate transactions,
            Clock clock) {
        this.assets = assets;
        this.knowledgeBases = knowledgeBases;
        this.properties = properties;
        this.crypto = crypto;
        this.transactions = transactions;
        this.clock = clock;
    }

    public RetentionResult expire(LocalDateTime now) {
        LocalDateTime cutoff = now.minusDays(properties.getTrashRetentionDays());
        int assetJobs = scheduleAssetPurges(cutoff, now);
        int purgedKnowledgeBases = purgeKnowledgeBases(cutoff);
        return new RetentionResult(assetJobs, purgedKnowledgeBases);
    }

    private int scheduleAssetPurges(LocalDateTime cutoff, LocalDateTime now) {
        int scheduled = 0;
        for (AssetLibraryRepository.ExpiredTrashAsset candidate
                : assets.findExpiredTrash(cutoff, properties.getRetentionBatchSize())) {
            try {
                boolean created = Boolean.TRUE.equals(transactions.execute(status -> {
                    AssetLifecycle current = assets.findForUpdate(
                                    candidate.userId(), candidate.externalId())
                            .orElse(null);
                    if (current == null
                            || !"TRASHED".equals(current.status())
                            || current.trashStartedAt() == null
                            || current.trashStartedAt().isAfter(cutoff)
                            || assets.hasActivePurgeJob(candidate.userId(), candidate.externalId())) {
                        return false;
                    }
                    // A day-based key prevents duplicate work when multiple
                    // scheduler ticks race, while allowing a blocked item to
                    // be retried on the next retention day.
                    String day = cutoff.toLocalDate().toString();
                    String idempotencyKey = crypto.digest(
                            "asset-purge-retention",
                            candidate.userId() + ":" + candidate.externalId() + ":" + day);
                    assets.enqueuePurge(
                            crypto.newExternalId(), candidate.userId(), candidate.externalId(),
                            idempotencyKey, now);
                    return true;
                }));
                if (created) {
                    scheduled++;
                }
            } catch (DuplicateKeyException duplicate) {
                // Another worker won the idempotency race.  The next poll will
                // observe its active job and skip this asset.
                log.debug("Retention purge already scheduled for asset {}", candidate.externalId());
            } catch (RuntimeException exception) {
                log.warn("Unable to schedule retention purge for asset {}", candidate.externalId(), exception);
            }
        }
        return scheduled;
    }

    private int purgeKnowledgeBases(LocalDateTime cutoff) {
        int purged = 0;
        for (KnowledgeBaseLibraryRepository.ExpiredTrashKnowledgeBase candidate
                : knowledgeBases.findExpiredTrash(cutoff, properties.getRetentionBatchSize())) {
            try {
                boolean changed = Boolean.TRUE.equals(transactions.execute(status -> {
                    KnowledgeBaseLifecycle current = knowledgeBases.findForUpdate(
                                    candidate.userId(), candidate.externalId())
                            .orElse(null);
                    if (current == null
                            || !"TRASHED".equals(current.status())
                            || current.trashStartedAt() == null
                            || current.trashStartedAt().isAfter(cutoff)) {
                        return false;
                    }
                    knowledgeBases.purge(current.id());
                    return true;
                }));
                if (changed) {
                    purged++;
                }
            } catch (RuntimeException exception) {
                // A failed container purge must not stop other users' assets;
                // the next scheduled run will retry the same safe operation.
                log.warn("Unable to purge expired knowledge base {}", candidate.externalId(), exception);
            }
        }
        return purged;
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    public RetentionResult expireNow() {
        return expire(now());
    }

    public record RetentionResult(int assetPurgeJobsScheduled, int knowledgeBasesPurged) {
    }
}
