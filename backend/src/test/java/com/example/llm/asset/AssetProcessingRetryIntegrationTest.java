package com.example.llm.asset;

import com.example.llm.asset.processing.config.AssetProcessingProperties;
import com.example.llm.asset.processing.job.AssetJobRepository;
import com.example.llm.asset.processing.repository.AssetProcessingRepository;
import com.example.llm.asset.processing.repository.AssetProcessingRepository.RetryOutcome;
import com.example.llm.auth.config.AuthProperties;
import com.example.llm.auth.security.AuthCrypto;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class AssetProcessingRetryIntegrationTest {
    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.0.45")
            .withDatabaseName("examinsight_v2_processing_retry_test")
            .withUsername("examinsight")
            .withPassword("examinsight-test-password");

    private static JdbcTemplate jdbc;
    private static AssetProcessingRepository processing;
    private static AssetJobRepository jobs;
    private static AuthCrypto crypto;

    @BeforeAll
    static void prepareDatabase() {
        Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .cleanDisabled(true)
                .validateMigrationNaming(true)
                .load()
                .migrate();
        jdbc = new JdbcTemplate(new DriverManagerDataSource(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()));
        TransactionTemplate transactions = new TransactionTemplate(
                new DataSourceTransactionManager(jdbc.getDataSource()));
        AuthProperties auth = new AuthProperties();
        auth.setHashSecret("processing-retry-test-secret-at-least-thirty-two-characters");
        crypto = new AuthCrypto(auth);
        processing = new AssetProcessingRepository(
                jdbc, transactions, crypto, new AssetProcessingProperties());
        jobs = new AssetJobRepository(jdbc, transactions, crypto);
    }

    @Test
    void failedParseRetryReusesJobAndPreservesAttemptSequence() {
        long userId = insertUser();
        String assetExternalId = crypto.newExternalId();
        String versionExternalId = crypto.newExternalId();
        String content = "需要重新解析的资料";
        String hash = crypto.digest("processing-retry-content", content);

        jdbc.update("""
                INSERT INTO asset (external_id, user_id, name, asset_type, source_type, status)
                VALUES (?, ?, '失败资料.txt', 'TEXT', 'USER_TEXT', 'ACTIVE')
                """, assetExternalId, userId);
        long assetId = jdbc.queryForObject(
                "SELECT id FROM asset WHERE external_id = ?", Long.class, assetExternalId);
        jdbc.update("""
                INSERT INTO asset_version (
                    external_id, asset_id, version_no, text_content, content_sha256,
                    mime_type, size_bytes, source_type, status, created_by_user_id,
                    rag_policy, rag_status, rag_error_code
                ) VALUES (?, ?, 1, ?, ?, 'text/plain', OCTET_LENGTH(?),
                          'USER_TEXT', 'FAILED', ?, 'AUTO', 'FAILED', 'PARSE_FAILED')
                """, versionExternalId, assetId, content, hash, content, userId);
        long versionId = jdbc.queryForObject(
                "SELECT id FROM asset_version WHERE external_id = ?", Long.class, versionExternalId);

        String jobExternalId = crypto.newExternalId();
        jdbc.update("""
                INSERT INTO async_job (
                    external_id, user_id, job_type, aggregate_type, aggregate_external_id,
                    status, stage_key, progress_current, progress_total, finished_at,
                    error_code, safe_error_message, attempt_count, max_attempts
                ) VALUES (?, ?, 'FILE_PARSE', 'ASSET_VERSION', ?,
                          'FAILED', 'TEXT_EXTRACTION', 0, 1, CURRENT_TIMESTAMP(3),
                          'PARSE_FAILED', '解析失败', 3, 3)
                """, jobExternalId, userId, versionExternalId);
        long jobId = jdbc.queryForObject(
                "SELECT id FROM async_job WHERE external_id = ?", Long.class, jobExternalId);
        jdbc.update("""
                INSERT INTO async_job_attempt (
                    external_id, async_job_id, attempt_no, worker_id, status,
                    started_at, finished_at, error_code
                ) VALUES (?, ?, 3, 'old-worker', 'FAILED',
                          CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 'PARSE_FAILED')
                """, crypto.newExternalId(), jobId);
        jdbc.update("""
                INSERT INTO asset_parse_result (
                    external_id, asset_version_id, parser_key, parser_version, async_job_id,
                    status, chunk_count, safe_error_code, completed_at
                ) VALUES (?, ?, 'tika-ocr', '2026-08-10.2', ?,
                          'FAILED', 0, 'PARSE_FAILED', CURRENT_TIMESTAMP(3))
                """, crypto.newExternalId(), versionId, jobId);

        LocalDateTime now = LocalDateTime.now();
        assertThat(processing.retryFailedWork(userId, assetExternalId, now))
                .isEqualTo(RetryOutcome.PARSE_REQUEUED);
        assertThat(processing.retryFailedWork(userId, assetExternalId, now.plusSeconds(1)))
                .isEqualTo(RetryOutcome.ALREADY_PROCESSING);

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM async_job WHERE aggregate_external_id = ?", Integer.class,
                versionExternalId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT status FROM asset_parse_result WHERE async_job_id = ?", String.class, jobId))
                .isEqualTo("QUEUED");
        assertThat(jdbc.queryForObject(
                "SELECT status FROM asset_version WHERE id = ?", String.class, versionId))
                .isEqualTo("PROCESSING");

        var claimed = jobs.claimNext("retry-worker", now.plusSeconds(2), now.plusMinutes(5)).orElseThrow();
        assertThat(claimed.id()).isEqualTo(jobId);
        assertThat(claimed.attemptCount()).isEqualTo(4);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM async_job_attempt WHERE async_job_id = ?", Integer.class, jobId))
                .isEqualTo(2);
    }

    @Test
    void failedVectorIndexRetryReusesChunkJobAndDoesNotDuplicateWork() {
        long userId = insertUser();
        String assetExternalId = crypto.newExternalId();
        String versionExternalId = crypto.newExternalId();
        String content = "函数极限与连续性";
        String hash = crypto.digest("processing-retry-index-content", content);
        jdbc.update("""
                INSERT INTO asset (external_id, user_id, name, asset_type, source_type, status)
                VALUES (?, ?, '索引失败资料.txt', 'TEXT', 'USER_TEXT', 'ACTIVE')
                """, assetExternalId, userId);
        long assetId = jdbc.queryForObject(
                "SELECT id FROM asset WHERE external_id = ?", Long.class, assetExternalId);
        jdbc.update("""
                INSERT INTO asset_version (
                    external_id, asset_id, version_no, text_content, content_sha256,
                    mime_type, size_bytes, source_type, status, created_by_user_id,
                    rag_policy, rag_status, rag_error_code
                ) VALUES (?, ?, 1, ?, ?, 'text/plain', OCTET_LENGTH(?),
                          'USER_TEXT', 'READY', ?, 'AUTO', 'FAILED', 'VECTOR_INDEXING_FAILED')
                """, versionExternalId, assetId, content, hash, content, userId);
        long versionId = jdbc.queryForObject(
                "SELECT id FROM asset_version WHERE external_id = ?", Long.class, versionExternalId);
        String parseJobExternalId = crypto.newExternalId();
        jdbc.update("""
                INSERT INTO async_job (
                    external_id, user_id, job_type, aggregate_type, aggregate_external_id,
                    status, stage_key, progress_current, progress_total, finished_at
                ) VALUES (?, ?, 'FILE_PARSE', 'ASSET_VERSION', ?,
                          'SUCCEEDED', 'TEXT_EXTRACTION', 1, 1, CURRENT_TIMESTAMP(3))
                """, parseJobExternalId, userId, versionExternalId);
        long parseJobId = jdbc.queryForObject(
                "SELECT id FROM async_job WHERE external_id = ?", Long.class, parseJobExternalId);
        String parseExternalId = crypto.newExternalId();
        jdbc.update("""
                INSERT INTO asset_parse_result (
                    external_id, asset_version_id, parser_key, parser_version, async_job_id,
                    status, language, page_count, chunk_count, plain_text_sha256, completed_at
                ) VALUES (?, ?, 'tika-ocr', '2026-08-10.2', ?,
                          'READY', 'zh', 1, 1, ?, CURRENT_TIMESTAMP(3))
                """, parseExternalId, versionId, parseJobId, hash);
        long parseId = jdbc.queryForObject(
                "SELECT id FROM asset_parse_result WHERE external_id = ?", Long.class, parseExternalId);
        String chunkExternalId = crypto.newExternalId();
        jdbc.update("""
                INSERT INTO document_chunk (
                    external_id, parse_result_id, sequence_no, content, content_sha256,
                    token_count, page_from, page_to, locator_json, heading_path
                ) VALUES (?, ?, 1, ?, ?, 20, 1, 1, JSON_OBJECT('page', 1), '第一章')
                """, chunkExternalId, parseId, content, hash);
        long chunkId = jdbc.queryForObject(
                "SELECT id FROM document_chunk WHERE external_id = ?", Long.class, chunkExternalId);
        long modelId = jdbc.queryForObject("""
                SELECT id FROM model_definition
                 WHERE model_key = 'dashscope-qwen3.7-text-embedding-1024'
                """, Long.class);
        jdbc.update("""
                INSERT INTO embedding_record (
                    external_id, chunk_id, model_definition_id, embedding_version,
                    index_name, index_document_id, content_sha256, status
                ) VALUES (?, ?, ?, 'dashscope-qwen3.7-text-embedding-1024-v1',
                          'examinsight-v2-chunks-qwen3.7-embedding-1024-v1', ?, ?, 'FAILED')
                """, crypto.newExternalId(), chunkId, modelId, crypto.newExternalId(), hash);
        long embeddingId = jdbc.queryForObject(
                "SELECT id FROM embedding_record WHERE chunk_id = ?", Long.class, chunkId);
        String indexJobExternalId = crypto.newExternalId();
        jdbc.update("""
                INSERT INTO async_job (
                    external_id, user_id, job_type, aggregate_type, aggregate_external_id,
                    status, stage_key, progress_current, progress_total, finished_at,
                    error_code, attempt_count, max_attempts
                ) VALUES (?, ?, 'FILE_INDEX', 'DOCUMENT_CHUNK', ?,
                          'FAILED', 'VECTOR_INDEXING', 0, 1, CURRENT_TIMESTAMP(3),
                          'VECTOR_INDEXING_FAILED', 3, 3)
                """, indexJobExternalId, userId, chunkExternalId);
        jdbc.update("UPDATE asset_version SET active_parse_result_id = ? WHERE id = ?", parseId, versionId);
        jdbc.update("UPDATE asset SET current_version_id = ? WHERE id = ?", versionId, assetId);

        LocalDateTime now = LocalDateTime.now();
        assertThat(processing.retryFailedWork(userId, assetExternalId, now))
                .isEqualTo(RetryOutcome.INDEX_REQUEUED);
        assertThat(processing.retryFailedWork(userId, assetExternalId, now.plusSeconds(1)))
                .isEqualTo(RetryOutcome.ALREADY_PROCESSING);

        assertThat(jdbc.queryForObject(
                "SELECT status FROM embedding_record WHERE id = ?", String.class, embeddingId))
                .isEqualTo("PENDING");
        assertThat(jdbc.queryForObject(
                "SELECT rag_status FROM asset_version WHERE id = ?", String.class, versionId))
                .isEqualTo("INDEXING");
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM async_job
                 WHERE job_type = 'FILE_INDEX' AND aggregate_external_id = ?
                """, Integer.class, chunkExternalId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT max_attempts FROM async_job
                 WHERE job_type = 'FILE_INDEX' AND aggregate_external_id = ?
                """, Integer.class, chunkExternalId)).isEqualTo(6);
    }

    private long insertUser() {
        String externalId = crypto.newExternalId();
        String email = externalId.toLowerCase() + "@example.com";
        jdbc.update("""
                INSERT INTO app_user (
                    external_id, normalized_email, email_display, status, email_verified_at
                ) VALUES (?, ?, ?, 'ACTIVE', CURRENT_TIMESTAMP(3))
                """, externalId, email, email);
        return jdbc.queryForObject(
                "SELECT id FROM app_user WHERE external_id = ?", Long.class, externalId);
    }
}
