package com.example.llm.asset.retrieval;

import com.example.llm.asset.processing.config.AssetProcessingProperties;
import com.example.llm.asset.retrieval.RetrievalModels.Bundle;
import com.example.llm.asset.retrieval.RetrievalModels.Mode;
import com.example.llm.asset.retrieval.RetrievalModels.Request;
import com.example.llm.asset.retrieval.RetrievalModels.Scope;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
class AssetRetrievalServiceIntegrationTest {
    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.0.45")
            .withDatabaseName("examinsight_v2_retrieval_test")
            .withUsername("examinsight")
            .withPassword("examinsight-test-password");

    private static final AtomicInteger IDS = new AtomicInteger(1);
    private static JdbcTemplate jdbc;

    @BeforeAll
    static void migrateSchema() {
        Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .cleanDisabled(true)
                .validateMigrationNaming(true)
                .load()
                .migrate();
        jdbc = new JdbcTemplate(new DriverManagerDataSource(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()));
    }

    @Test
    void retrievalEnforcesOwnershipScopeAndFallsBackToChineseKeywordSearch() {
        long owner = insertUser("owner@example.com");
        long otherUser = insertUser("other@example.com");
        AssetFixture math = insertReadyAsset(
                owner,
                "高等数学复习资料",
                "函数极限描述函数在某一点附近的变化趋势，连续性可以通过左右极限进行判断。",
                true);
        AssetFixture duplicateMath = insertReadyAsset(
                owner,
                "高等数学重复笔记",
                "函数极限描述函数在某一点附近的变化趋势，连续性可以通过左右极限进行判断。",
                true);
        AssetFixture physics = insertReadyAsset(
                owner,
                "大学物理复习资料",
                "牛顿第二定律说明物体加速度与合外力成正比，与质量成反比。",
                true);
        AssetFixture foreign = insertReadyAsset(
                otherUser,
                "他人的私密资料",
                "这段内容属于另一个用户，任何检索范围都不能返回它。",
                true);
        String knowledgeBaseExternalId = insertKnowledgeBase(owner, "高等数学", math.assetId());

        AssetProcessingProperties processing = new AssetProcessingProperties();
        AssetRetrievalProperties retrieval = new AssetRetrievalProperties();
        AssetRetrievalRepository repository = new AssetRetrievalRepository(
                jdbc, processing, retrieval);
        CapturingVectorSearch vectors = new CapturingVectorSearch(List.of(
                new VectorSearchGateway.VectorHit(foreign.chunkExternalId(), 0.99),
                new VectorSearchGateway.VectorHit(physics.chunkExternalId(), 0.95),
                new VectorSearchGateway.VectorHit(math.chunkExternalId(), 0.91),
                new VectorSearchGateway.VectorHit(duplicateMath.chunkExternalId(), 0.90)));
        QueryEmbeddingGateway embeddings = query -> java.util.Collections.nCopies(1024, 0.01f);
        AssetRetrievalService service = new AssetRetrievalService(
                repository, embeddings, vectors, retrieval, new ObjectMapper());

        Bundle personal = service.retrieve(
                owner,
                new Request("解释函数极限和牛顿第二定律", Scope.personalLibrary(), 6, 3200));
        assertThat(personal.mode()).isEqualTo(Mode.HYBRID);
        assertThat(personal.sources())
                .extracting(RetrievalModels.Source::assetExternalId)
                .contains(math.assetExternalId(), physics.assetExternalId())
                .doesNotContain(foreign.assetExternalId());
        assertThat(personal.sources())
                .filteredOn(source -> source.content().contains("函数极限"))
                .hasSize(1);
        assertThat(personal.contextTokens()).isLessThanOrEqualTo(3200);
        assertThat(personal.contextJson()).contains("untrusted reference data");

        Bundle knowledgeBase = service.retrieve(
                owner,
                new Request("解释函数极限", Scope.knowledgeBase(knowledgeBaseExternalId), 6, 3200));
        assertThat(knowledgeBase.mode()).isEqualTo(Mode.HYBRID);
        assertThat(knowledgeBase.sources())
                .extracting(RetrievalModels.Source::assetExternalId)
                .containsExactly(math.assetExternalId());
        assertThat(vectors.lastAllowedAssetIds).containsExactly(math.assetId());

        Bundle lockedVersion = service.retrieve(
                owner,
                new Request(
                        "explain the locked source",
                        Scope.versions(List.of(math.versionExternalId())),
                        6,
                        3200));
        assertThat(lockedVersion.sources())
                .extracting(RetrievalModels.Source::assetVersionExternalId)
                .containsExactly(math.versionExternalId());
        assertThat(vectors.lastAllowedVersionIds).containsExactly(math.versionId());

        assertThatThrownBy(() -> service.retrieve(
                owner,
                new Request("读取资料", Scope.assets(List.of(foreign.assetExternalId())), 6, 3200)))
                .isInstanceOf(RetrievalException.class)
                .extracting(exception -> ((RetrievalException) exception).code())
                .isEqualTo("RETRIEVAL_SCOPE_NOT_FOUND");

        QueryEmbeddingGateway unavailableEmbedding = query -> {
            throw new RetrievalException("QUERY_EMBEDDING_UNAVAILABLE", "provider unavailable");
        };
        AssetRetrievalService fallbackService = new AssetRetrievalService(
                repository, unavailableEmbedding, vectors, retrieval, new ObjectMapper());
        Bundle fallback = fallbackService.retrieve(
                owner,
                new Request("函数极限", Scope.personalLibrary(), 6, 3200));
        assertThat(fallback.status()).isEqualTo(RetrievalModels.Status.DEGRADED);
        assertThat(fallback.mode()).isEqualTo(Mode.KEYWORD);
        assertThat(fallback.degradationCode()).isEqualTo("QUERY_EMBEDDING_UNAVAILABLE");
        assertThat(fallback.sources())
                .allMatch(source -> !source.assetExternalId().equals(foreign.assetExternalId()))
                .anyMatch(source -> source.content().contains("函数极限"));
    }

    private long insertUser(String email) {
        jdbc.update("""
                INSERT INTO app_user (
                    external_id, normalized_email, email_display, status, email_verified_at
                ) VALUES (?, ?, ?, 'ACTIVE', CURRENT_TIMESTAMP(3))
                """, externalId(), email, email);
        return jdbc.queryForObject(
                "SELECT id FROM app_user WHERE normalized_email = ?", Long.class, email);
    }

    private AssetFixture insertReadyAsset(long userId, String name, String content, boolean indexed) {
        String assetExternalId = externalId();
        String versionExternalId = externalId();
        String chunkExternalId = externalId();
        String contentHash = sha256(content);
        jdbc.update("""
                INSERT INTO asset (
                    external_id, user_id, name, asset_type, source_type,
                    current_version_id, status
                ) VALUES (?, ?, ?, 'TEXT', 'USER_TEXT', NULL, 'ACTIVE')
                """, assetExternalId, userId, name);
        long assetId = jdbc.queryForObject(
                "SELECT id FROM asset WHERE external_id = ?", Long.class, assetExternalId);

        String jobExternalId = externalId();
        jdbc.update("""
                INSERT INTO async_job (
                    external_id, user_id, job_type, aggregate_type, aggregate_external_id,
                    status, stage_key, progress_current, progress_total
                ) VALUES (?, ?, 'FILE_PARSE', 'ASSET_VERSION', ?,
                          'SUCCEEDED', 'TEXT_EXTRACTION', 1, 1)
                """, jobExternalId, userId, versionExternalId);
        long jobId = jdbc.queryForObject(
                "SELECT id FROM async_job WHERE external_id = ?", Long.class, jobExternalId);

        byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
        jdbc.update("""
                INSERT INTO asset_version (
                    external_id, asset_id, version_no, upload_session_id, storage_object_id,
                    text_content, content_sha256, mime_type, size_bytes, source_type,
                    status, active_parse_result_id, generated_by_ai, created_by_user_id
                ) VALUES (?, ?, 1, NULL, NULL, ?, ?, 'text/plain', ?, 'USER_TEXT',
                          'READY', NULL, FALSE, ?)
                """, versionExternalId, assetId, content, contentHash, contentBytes.length, userId);
        long versionId = jdbc.queryForObject(
                "SELECT id FROM asset_version WHERE external_id = ?", Long.class, versionExternalId);

        String parseExternalId = externalId();
        jdbc.update("""
                INSERT INTO asset_parse_result (
                    external_id, asset_version_id, parser_key, parser_version, async_job_id,
                    status, language, page_count, chunk_count, plain_text_sha256,
                    safe_error_code, completed_at
                ) VALUES (?, ?, 'tika-ocr', '2026-08-09.1', ?,
                          'READY', 'zh', 1, 1, ?, NULL, CURRENT_TIMESTAMP(3))
                """, parseExternalId, versionId, jobId, contentHash);
        long parseId = jdbc.queryForObject(
                "SELECT id FROM asset_parse_result WHERE external_id = ?", Long.class, parseExternalId);

        jdbc.update("""
                INSERT INTO document_chunk (
                    external_id, parse_result_id, sequence_no, content,
                    content_sha256, token_count, page_from, page_to,
                    locator_json, heading_path
                ) VALUES (?, ?, 1, ?, ?, 80, 1, 1,
                          JSON_OBJECT('page', 1), '第一章')
                """, chunkExternalId, parseId, content, contentHash);
        long chunkId = jdbc.queryForObject(
                "SELECT id FROM document_chunk WHERE external_id = ?", Long.class, chunkExternalId);
        jdbc.update("UPDATE asset_version SET active_parse_result_id = ? WHERE id = ?", parseId, versionId);
        jdbc.update("UPDATE asset SET current_version_id = ? WHERE id = ?", versionId, assetId);

        if (indexed) {
            long modelId = jdbc.queryForObject(
                    "SELECT id FROM model_definition WHERE model_key = 'dashscope-qwen3.7-text-embedding-1024'",
                    Long.class);
            jdbc.update("""
                    INSERT INTO embedding_record (
                        external_id, chunk_id, model_definition_id, embedding_version,
                        index_name, index_document_id, content_sha256, status, indexed_at
                    ) VALUES (?, ?, ?, 'dashscope-qwen3.7-text-embedding-1024-v1',
                              'examinsight-v2-chunks-qwen3.7-embedding-1024-v1', ?, ?,
                              'INDEXED', CURRENT_TIMESTAMP(3))
                    """, externalId(), chunkId, modelId, externalId(), contentHash);
        }
        return new AssetFixture(assetId, assetExternalId, versionId, versionExternalId, chunkExternalId);
    }

    private String insertKnowledgeBase(long userId, String name, long assetId) {
        String externalId = externalId();
        jdbc.update("""
                INSERT INTO knowledge_base (
                    external_id, user_id, name, normalized_name, status
                ) VALUES (?, ?, ?, ?, 'ACTIVE')
                """, externalId, userId, name, name.toLowerCase(java.util.Locale.ROOT));
        long knowledgeBaseId = jdbc.queryForObject(
                "SELECT id FROM knowledge_base WHERE external_id = ?", Long.class, externalId);
        jdbc.update("""
                INSERT INTO knowledge_base_asset (
                    knowledge_base_id, asset_id, added_by_user_id, sort_order
                ) VALUES (?, ?, ?, 0)
                """, knowledgeBaseId, assetId, userId);
        return externalId;
    }

    private static String externalId() {
        return String.format("%026d", IDS.getAndIncrement());
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static final class CapturingVectorSearch implements VectorSearchGateway {
        private final List<VectorHit> hits;
        private List<Long> lastAllowedAssetIds;
        private List<Long> lastAllowedVersionIds;

        private CapturingVectorSearch(List<VectorHit> hits) {
            this.hits = hits;
        }

        @Override
        public List<VectorHit> search(
                List<Float> queryVector,
                long ownerUserId,
                List<Long> allowedAssetIds,
                List<Long> allowedVersionIds,
                int topK,
                int numCandidates,
                double minScore) {
            lastAllowedAssetIds = allowedAssetIds;
            lastAllowedVersionIds = allowedVersionIds;
            return hits;
        }
    }

    private record AssetFixture(
            long assetId,
            String assetExternalId,
            long versionId,
            String versionExternalId,
            String chunkExternalId) {
    }
}
