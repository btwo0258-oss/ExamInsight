package com.example.llm.asset.processing.job;

import com.example.llm.auth.security.AuthCrypto;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class AssetJobRepository {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final AuthCrypto crypto;

    public AssetJobRepository(
            @Qualifier("v2JdbcTemplate") JdbcTemplate jdbc,
            @Qualifier("v2TransactionTemplate") TransactionTemplate transactions,
            AuthCrypto crypto) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.crypto = crypto;
    }

    public List<AssetJob> recoverExpiredLeases(LocalDateTime now) {
        return transactions.execute(status -> {
            List<AssetJob> exhausted = jdbc.query("""
                    SELECT id, external_id, job_type, aggregate_type, aggregate_external_id,
                           attempt_count, max_attempts
                      FROM async_job
                     WHERE status = 'RUNNING'
                       AND lease_expires_at <= ?
                       AND attempt_count >= max_attempts
                     FOR UPDATE
                    """, (rs, rowNum) -> mapJob(rs), now);
            jdbc.update("""
                    UPDATE async_job_attempt attempt
                    JOIN async_job job ON job.id = attempt.async_job_id
                       SET attempt.status = 'LEASE_EXPIRED',
                           attempt.finished_at = ?,
                           attempt.error_code = 'JOB_LEASE_EXPIRED',
                           attempt.row_version = attempt.row_version + 1
                     WHERE attempt.status = 'RUNNING'
                       AND job.status = 'RUNNING'
                       AND job.lease_expires_at <= ?
                    """, now, now);
            jdbc.update("""
                    UPDATE async_job
                       SET status = CASE WHEN attempt_count >= max_attempts THEN 'FAILED' ELSE 'RETRY_WAIT' END,
                           scheduled_at = CASE WHEN attempt_count >= max_attempts THEN scheduled_at ELSE ? END,
                           finished_at = CASE WHEN attempt_count >= max_attempts THEN ? ELSE NULL END,
                           error_code = 'JOB_LEASE_EXPIRED',
                           safe_error_message = '后台处理任务租约已过期。',
                           lease_owner = NULL,
                           lease_expires_at = NULL,
                           row_version = row_version + 1
                     WHERE status = 'RUNNING'
                       AND lease_expires_at <= ?
                    """, now, now, now);
            return exhausted;
        });
    }

    public Optional<AssetJob> claimNext(String workerId, LocalDateTime now, LocalDateTime leaseExpiresAt) {
        return transactions.execute(status -> {
            List<AssetJob> candidates = jdbc.query("""
                    SELECT id, external_id, job_type, aggregate_type, aggregate_external_id,
                           attempt_count, max_attempts
                      FROM async_job
                     WHERE job_type IN ('FILE_SECURITY_SCAN', 'FILE_PARSE', 'FILE_INDEX', 'ASSET_PURGE')
                       AND status IN ('QUEUED', 'RETRY_WAIT')
                       AND scheduled_at <= ?
                       AND attempt_count < max_attempts
                     ORDER BY priority ASC, scheduled_at ASC, id ASC
                     LIMIT 1
                     FOR UPDATE SKIP LOCKED
                    """, (rs, rowNum) -> mapJob(rs), now);
            if (candidates.isEmpty()) {
                return Optional.empty();
            }
            AssetJob candidate = candidates.get(0);
            int attemptNo = candidate.attemptCount() + 1;
            jdbc.update("""
                    UPDATE async_job
                       SET status = 'RUNNING',
                           started_at = COALESCE(started_at, ?),
                           heartbeat_at = ?,
                           finished_at = NULL,
                           error_code = NULL,
                           safe_error_message = NULL,
                           lease_owner = ?,
                           lease_expires_at = ?,
                           attempt_count = ?,
                           row_version = row_version + 1
                     WHERE id = ?
                    """, now, now, workerId, leaseExpiresAt, attemptNo, candidate.id());
            jdbc.update("""
                    INSERT INTO async_job_attempt (
                        external_id, async_job_id, attempt_no, worker_id, status,
                        started_at, heartbeat_at, finished_at, error_code, diagnostic_json
                    ) VALUES (?, ?, ?, ?, 'RUNNING', ?, ?, NULL, NULL, NULL)
                    """, crypto.newExternalId(), candidate.id(), attemptNo, workerId, now, now);
            return Optional.of(new AssetJob(
                    candidate.id(), candidate.externalId(), candidate.jobType(),
                    candidate.aggregateType(), candidate.aggregateExternalId(),
                    attemptNo, candidate.maxAttempts()));
        });
    }

    /**
     * Security scan jobs created before the scanner startup grace period was widened only had three
     * attempts. Reopen those legacy jobs once; max_attempts is raised at the same time, so this
     * migration repair cannot loop forever when the scanner remains unavailable.
     */
    public int extendLegacySecurityScanRetries(LocalDateTime now) {
        return jdbc.update("""
                UPDATE async_job job
                   SET job.status = 'RETRY_WAIT',
                       job.scheduled_at = ?,
                       job.finished_at = NULL,
                       job.max_attempts = 10,
                       job.error_code = NULL,
                       job.safe_error_message = NULL,
                       job.lease_owner = NULL,
                       job.lease_expires_at = NULL,
                       job.row_version = job.row_version + 1
                 WHERE job.job_type = 'FILE_SECURITY_SCAN'
                   AND job.status = 'FAILED'
                   AND job.max_attempts = 3
                   AND job.attempt_count >= job.max_attempts
                   AND job.error_code IN ('FILE_SCANNER_UNAVAILABLE', 'FILE_SCANNER_ERROR')
                   AND EXISTS (
                       SELECT 1
                         FROM storage_object storage
                        WHERE storage.external_id = job.aggregate_external_id
                          AND storage.status = 'QUARANTINED'
                   )
                """, now);
    }

    public void succeed(AssetJob job, String workerId, String resultJson, LocalDateTime now) {
        transactions.executeWithoutResult(status -> {
            int updated = jdbc.update("""
                    UPDATE async_job
                       SET status = 'SUCCEEDED', progress_current = COALESCE(progress_total, progress_current),
                           result_json = CAST(? AS JSON), heartbeat_at = ?, finished_at = ?,
                           error_code = NULL, safe_error_message = NULL,
                           lease_owner = NULL, lease_expires_at = NULL,
                           row_version = row_version + 1
                     WHERE id = ? AND status = 'RUNNING' AND lease_owner = ?
                    """, resultJson, now, now, job.id(), workerId);
            if (updated != 1) {
                throw new IllegalStateException("Asset job lease was lost before success");
            }
            jdbc.update("""
                    UPDATE async_job_attempt
                       SET status = 'SUCCEEDED', heartbeat_at = ?, finished_at = ?,
                           row_version = row_version + 1
                     WHERE async_job_id = ? AND attempt_no = ? AND status = 'RUNNING'
                    """, now, now, job.id(), job.attemptCount());
        });
    }

    public boolean fail(
            AssetJob job,
            String workerId,
            String errorCode,
            String safeMessage,
            boolean retryable,
            LocalDateTime now) {
        return Boolean.TRUE.equals(transactions.execute(status -> {
            List<AttemptState> states = jdbc.query("""
                    SELECT attempt_count, max_attempts
                      FROM async_job
                     WHERE id = ? AND status = 'RUNNING' AND lease_owner = ?
                     FOR UPDATE
                    """, (rs, rowNum) -> new AttemptState(rs.getInt(1), rs.getInt(2)), job.id(), workerId);
            if (states.isEmpty()) {
                return false;
            }
            AttemptState state = states.get(0);
            boolean terminal = !retryable || state.attemptCount() >= state.maxAttempts();
            LocalDateTime retryAt = now.plusSeconds(Math.min(300L, 1L << Math.min(state.attemptCount(), 8)));
            jdbc.update("""
                    UPDATE async_job
                       SET status = ?, scheduled_at = ?, finished_at = ?,
                           error_code = ?, safe_error_message = ?,
                           lease_owner = NULL, lease_expires_at = NULL,
                           row_version = row_version + 1
                     WHERE id = ?
                    """, terminal ? "FAILED" : "RETRY_WAIT",
                    terminal ? now : retryAt,
                    terminal ? now : null,
                    errorCode, truncate(safeMessage, 500), job.id());
            jdbc.update("""
                    UPDATE async_job_attempt
                       SET status = 'FAILED', heartbeat_at = ?, finished_at = ?, error_code = ?,
                           diagnostic_json = JSON_OBJECT('retryable', ?),
                           row_version = row_version + 1
                     WHERE async_job_id = ? AND attempt_no = ? AND status = 'RUNNING'
                    """, now, now, errorCode, retryable, job.id(), job.attemptCount());
            return terminal;
        }));
    }

    private AssetJob mapJob(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new AssetJob(
                rs.getLong("id"), rs.getString("external_id"), rs.getString("job_type"),
                rs.getString("aggregate_type"), rs.getString("aggregate_external_id"),
                rs.getInt("attempt_count"), rs.getInt("max_attempts"));
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return "后台处理暂时失败。";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private record AttemptState(int attemptCount, int maxAttempts) {
    }

    public record AssetJob(
            long id,
            String externalId,
            String jobType,
            String aggregateType,
            String aggregateExternalId,
            int attemptCount,
            int maxAttempts) {
    }
}
