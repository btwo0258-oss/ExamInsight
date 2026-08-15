package com.example.llm.asset;

import com.example.llm.asset.api.AssetApiException;
import com.example.llm.asset.api.LibraryDtos.CreateKnowledgeBaseRequest;
import com.example.llm.asset.api.LibraryDtos.UpdateKnowledgeBaseRequest;
import com.example.llm.asset.config.AssetStorageProperties;
import com.example.llm.asset.processing.config.AssetProcessingProperties;
import com.example.llm.asset.processing.ProcessingFailure;
import com.example.llm.asset.processing.index.VectorIndexGateway;
import com.example.llm.asset.processing.index.EmbeddingRuntime;
import com.example.llm.asset.processing.job.AssetJobRepository;
import com.example.llm.asset.processing.job.AssetJobRepository.AssetJob;
import com.example.llm.asset.processing.parse.AssetContentExtractor;
import com.example.llm.asset.processing.parse.StructuredTextChunker;
import com.example.llm.asset.processing.repository.AssetIndexRepository;
import com.example.llm.asset.processing.repository.AssetProcessingRepository;
import com.example.llm.asset.processing.repository.AssetPurgeRepository;
import com.example.llm.asset.processing.security.FileSecurityScanner;
import com.example.llm.asset.processing.service.AssetProcessingCoordinator;
import com.example.llm.asset.repository.AssetLibraryRepository;
import com.example.llm.asset.repository.KnowledgeBaseLibraryRepository;
import com.example.llm.asset.service.LibraryApplicationService;
import com.example.llm.asset.storage.LocalObjectStorageGateway;
import com.example.llm.asset.storage.StorageObjectKeyCipher;
import com.example.llm.auth.config.AuthProperties;
import com.example.llm.auth.security.AuthCrypto;
import com.example.llm.integration.ai.AiCapabilityRouter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Testcontainers(disabledWithoutDocker = true)
class LibraryApplicationServiceIntegrationTest {
    private static final AtomicLong IDS = new AtomicLong(8000);

    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.0.45")
            .withDatabaseName("examinsight_v2_library_test")
            .withUsername("examinsight")
            .withPassword("examinsight-test-password");

    private static JdbcTemplate jdbc;

    @TempDir
    Path temporaryDirectory;

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
    void libraryKnowledgeBaseRecycleBinAndPurgeFollowTheFrozenLifecycle() throws Exception {
        Clock clock = Clock.systemUTC();
        AuthProperties authProperties = new AuthProperties();
        authProperties.setHashSecret("library-auth-secret-at-least-thirty-two-characters");
        AuthCrypto crypto = new AuthCrypto(authProperties);
        TransactionTemplate transactions = new TransactionTemplate(
                new DataSourceTransactionManager(jdbc.getDataSource()));
        AssetProcessingProperties processingProperties = new AssetProcessingProperties();
        AssetLibraryRepository assetRepository = new AssetLibraryRepository(
                jdbc, processingProperties);
        KnowledgeBaseLibraryRepository knowledgeBaseRepository =
                new KnowledgeBaseLibraryRepository(jdbc);
        EmbeddingRuntime embeddingRuntime = mock(EmbeddingRuntime.class);
        when(embeddingRuntime.isSemanticIndexAvailable()).thenReturn(true);
        LibraryApplicationService service = new LibraryApplicationService(
                assetRepository, knowledgeBaseRepository, crypto, transactions, embeddingRuntime, clock);

        long owner = insertUser("owner-library@example.com");
        long other = insertUser("other-library@example.com");
        AssetFixture calculus = insertAsset(owner, "高等数学讲义");
        AssetFixture physics = insertAsset(owner, "大学物理复习");
        AssetFixture foreign = insertAsset(other, "其他用户资料");

        var firstPage = service.listAssets(owner, "library", 1, null);
        assertThat(firstPage.items()).hasSize(1);
        assertThat(firstPage.nextCursor()).isNotBlank();
        var secondPage = service.listAssets(owner, "library", 1, firstPage.nextCursor());
        assertThat(secondPage.items()).hasSize(1);
        assertThat(secondPage.items().get(0).assetId())
                .isNotEqualTo(firstPage.items().get(0).assetId());
        assertThatThrownBy(() -> service.getAsset(owner, foreign.externalId()))
                .isInstanceOf(AssetApiException.class)
                .extracting(error -> ((AssetApiException) error).code())
                .isEqualTo("ASSET_NOT_FOUND");

        var knowledgeBase = service.createKnowledgeBase(
                owner, new CreateKnowledgeBaseRequest("期末冲刺", "考试范围内的个人资料"));
        String knowledgeBaseId = knowledgeBase.knowledgeBase().knowledgeBaseId();
        service.addAssetToKnowledgeBase(owner, knowledgeBaseId, calculus.externalId());
        service.addAssetToKnowledgeBase(owner, knowledgeBaseId, calculus.externalId());
        assertThat(service.listKnowledgeBaseAssets(owner, knowledgeBaseId, 20, null).items())
                .extracting(item -> item.assetId())
                .containsExactly(calculus.externalId());
        assertThatThrownBy(() -> service.addAssetToKnowledgeBase(
                owner, knowledgeBaseId, foreign.externalId()))
                .isInstanceOf(AssetApiException.class)
                .extracting(error -> ((AssetApiException) error).code())
                .isEqualTo("ASSET_NOT_FOUND");

        service.moveAssetToTrash(owner, calculus.externalId());
        assertThat(service.listAssets(owner, "trash", 20, null).items())
                .extracting(item -> item.assetId())
                .contains(calculus.externalId());
        assertThat(service.listKnowledgeBaseAssets(owner, knowledgeBaseId, 20, null).items())
                .isEmpty();
        service.restoreAsset(owner, calculus.externalId());
        assertThat(service.listKnowledgeBaseAssets(owner, knowledgeBaseId, 20, null).items())
                .extracting(item -> item.assetId())
                .containsExactly(calculus.externalId());

        service.moveKnowledgeBaseToTrash(owner, knowledgeBaseId);
        service.createKnowledgeBase(
                owner, new CreateKnowledgeBaseRequest("期末冲刺", "新的同名知识库"));
        assertThatThrownBy(() -> service.restoreKnowledgeBase(owner, knowledgeBaseId))
                .isInstanceOf(AssetApiException.class)
                .extracting(error -> ((AssetApiException) error).code())
                .isEqualTo("KNOWLEDGE_BASE_NAME_CONFLICT");
        service.updateKnowledgeBase(
                owner, knowledgeBaseId, new UpdateKnowledgeBaseRequest("期末冲刺旧版", null));
        service.restoreKnowledgeBase(owner, knowledgeBaseId);
        service.moveKnowledgeBaseToTrash(owner, knowledgeBaseId);
        service.purgeKnowledgeBase(owner, knowledgeBaseId);
        assertThatThrownBy(() -> service.getKnowledgeBase(owner, knowledgeBaseId))
                .isInstanceOf(AssetApiException.class);
        assertThat(service.getAsset(owner, calculus.externalId()).asset().status())
                .isEqualTo("ACTIVE");

        service.moveAssetToTrash(owner, physics.externalId());
        var firstPurge = service.requestAssetPurge(owner, physics.externalId());
        var repeatedPurge = service.requestAssetPurge(owner, physics.externalId());
        assertThat(repeatedPurge.jobId()).isEqualTo(firstPurge.jobId());

        AssetStorageProperties storageProperties = new AssetStorageProperties();
        storageProperties.setBucketKey("library-private");
        storageProperties.setKeySecret(
                "library-storage-secret-at-least-thirty-two-characters");
        storageProperties.getLocal().setRoot(temporaryDirectory.resolve("storage"));
        var storage = new LocalObjectStorageGateway(storageProperties);
        var cipher = new StorageObjectKeyCipher(storageProperties);
        AssetPurgeRepository purgeRepository = new AssetPurgeRepository(jdbc, transactions);
        CapturingVectorIndex vectorIndex = new CapturingVectorIndex();
        AssetJobRepository jobs = new AssetJobRepository(jdbc, transactions, crypto);
        AssetProcessingCoordinator coordinator = new AssetProcessingCoordinator(
                new AssetProcessingRepository(jdbc, transactions, crypto, processingProperties),
                purgeRepository,
                new AssetIndexRepository(jdbc, transactions, crypto, processingProperties, embeddingRuntime),
                cleanScanner(),
                emptyExtractor(),
                new StructuredTextChunker(processingProperties),
                storage,
                cipher,
                text -> Collections.nCopies(2560, 0.01f),
                vectorIndex,
                processingProperties,
                embeddingRuntime,
                mock(AiCapabilityRouter.class),
                clock);

        LocalDateTime now = LocalDateTime.now(clock);
        AssetJob purgeJob = jobs.claimNext("library-test-worker", now, now.plusMinutes(5))
                .orElseThrow();
        assertThat(purgeJob.jobType()).isEqualTo("ASSET_PURGE");
        Map<String, Object> result = coordinator.execute(purgeJob);
        jobs.succeed(
                purgeJob,
                "library-test-worker",
                new ObjectMapper().writeValueAsString(result),
                LocalDateTime.now(clock));

        assertThat(vectorIndex.deletedOwnerUserId).isEqualTo(owner);
        assertThat(vectorIndex.deletedAssetId).isEqualTo(physics.id());
        assertThat(jdbc.queryForObject(
                "SELECT status FROM asset WHERE id = ?", String.class, physics.id()))
                .isEqualTo("PURGED");
        assertThat(service.getAssetPurgeJob(owner, physics.externalId()).status())
                .isEqualTo("SUCCEEDED");
        assertThatThrownBy(() -> service.getAsset(owner, physics.externalId()))
                .isInstanceOf(AssetApiException.class)
                .extracting(error -> ((AssetApiException) error).code())
                .isEqualTo("ASSET_NOT_FOUND");

        AssetFixture referenced = insertReferencedTextAsset(owner, "已用于对话的资料");
        attachToConversation(owner, referenced.versionId());
        service.moveAssetToTrash(owner, referenced.externalId());
        service.requestAssetPurge(owner, referenced.externalId());
        AssetJob referencedPurge = jobs.claimNext(
                        "library-reference-worker", LocalDateTime.now(clock),
                        LocalDateTime.now(clock).plusMinutes(5))
                .orElseThrow();
        assertThatThrownBy(() -> coordinator.execute(referencedPurge))
                .isInstanceOf(ProcessingFailure.class)
                .extracting(error -> ((ProcessingFailure) error).code())
                .isEqualTo("ASSET_VERSION_IN_USE");
        jobs.fail(
                referencedPurge,
                "library-reference-worker",
                "ASSET_VERSION_IN_USE",
                "资料仍被引用。",
                false,
                LocalDateTime.now(clock));
        assertThat(service.getAssetPurgeJob(owner, referenced.externalId()).status())
                .isEqualTo("FAILED");
        assertThat(service.getAsset(owner, referenced.externalId()).asset().status())
                .isEqualTo("TRASHED");
    }

    private long insertUser(String email) {
        String externalId = externalId();
        jdbc.update("""
                INSERT INTO app_user (
                    external_id, normalized_email, email_display, status, email_verified_at
                ) VALUES (?, ?, ?, 'ACTIVE', CURRENT_TIMESTAMP(3))
                """, externalId, email, email);
        return jdbc.queryForObject(
                "SELECT id FROM app_user WHERE external_id = ?", Long.class, externalId);
    }

    private AssetFixture insertAsset(long userId, String name) {
        String externalId = externalId();
        jdbc.update("""
                INSERT INTO asset (
                    external_id, user_id, name, asset_type, source_type,
                    current_version_id, status
                ) VALUES (?, ?, ?, 'FILE', 'UPLOAD', NULL, 'ACTIVE')
                """, externalId, userId, name);
        long id = jdbc.queryForObject(
                "SELECT id FROM asset WHERE external_id = ?", Long.class, externalId);
        return new AssetFixture(id, externalId, null);
    }

    private AssetFixture insertReferencedTextAsset(long userId, String name) {
        String assetExternalId = externalId();
        String versionExternalId = externalId();
        jdbc.update("""
                INSERT INTO asset (
                    external_id, user_id, name, asset_type, source_type,
                    current_version_id, status
                ) VALUES (?, ?, ?, 'TEXT', 'USER_TEXT', NULL, 'ACTIVE')
                """, assetExternalId, userId, name);
        long assetId = jdbc.queryForObject(
                "SELECT id FROM asset WHERE external_id = ?", Long.class, assetExternalId);
        jdbc.update("""
                INSERT INTO asset_version (
                    external_id, asset_id, version_no, text_content, content_sha256,
                    mime_type, size_bytes, source_type, status, generated_by_ai,
                    created_by_user_id
                ) VALUES (?, ?, 1, 'x', ?, 'text/plain', 1,
                          'USER_TEXT', 'READY', FALSE, ?)
                """, versionExternalId, assetId, "a".repeat(64), userId);
        long versionId = jdbc.queryForObject(
                "SELECT id FROM asset_version WHERE external_id = ?", Long.class, versionExternalId);
        jdbc.update("UPDATE asset SET current_version_id = ? WHERE id = ?", versionId, assetId);
        return new AssetFixture(assetId, assetExternalId, versionId);
    }

    private void attachToConversation(long userId, Long versionId) {
        String conversationExternalId = externalId();
        jdbc.update("""
                INSERT INTO conversation (
                    external_id, user_id, conversation_type, title, status
                ) VALUES (?, ?, 'GENERAL', '引用测试', 'ACTIVE')
                """, conversationExternalId, userId);
        long conversationId = jdbc.queryForObject(
                "SELECT id FROM conversation WHERE external_id = ?",
                Long.class,
                conversationExternalId);
        String branchExternalId = externalId();
        jdbc.update("""
                INSERT INTO conversation_branch (
                    external_id, conversation_id, status, created_by_user_id
                ) VALUES (?, ?, 'ACTIVE', ?)
                """, branchExternalId, conversationId, userId);
        long branchId = jdbc.queryForObject(
                "SELECT id FROM conversation_branch WHERE external_id = ?",
                Long.class,
                branchExternalId);
        jdbc.update("UPDATE conversation SET active_branch_id = ? WHERE id = ?", branchId, conversationId);
        String messageExternalId = externalId();
        jdbc.update("""
                INSERT INTO message (
                    external_id, conversation_id, branch_id, role, status,
                    sequence_no, plain_text, generated_by_ai, finalized_at
                ) VALUES (?, ?, ?, 'USER', 'FINALIZED', 1, '使用这份资料', FALSE,
                          CURRENT_TIMESTAMP(3))
                """, messageExternalId, conversationId, branchId);
        long messageId = jdbc.queryForObject(
                "SELECT id FROM message WHERE external_id = ?", Long.class, messageExternalId);
        jdbc.update("""
                INSERT INTO message_attachment (
                    message_id, asset_version_id, attachment_role, display_name
                ) VALUES (?, ?, 'REFERENCE', '已引用资料')
                """, messageId, versionId);
    }

    private static FileSecurityScanner cleanScanner() {
        return new FileSecurityScanner() {
            @Override
            public String scannerKey() {
                return "library-test";
            }

            @Override
            public String scannerVersion() {
                return "1";
            }

            @Override
            public ScanResult scan(java.io.InputStream content) {
                return ScanResult.cleanFile();
            }
        };
    }

    private static AssetContentExtractor emptyExtractor() {
        return (content, mimeType, sizeBytes) ->
                new AssetContentExtractor.ExtractedContent("empty", null, "zh");
    }

    private static String externalId() {
        return String.format("%026d", IDS.getAndIncrement());
    }

    private static final class CapturingVectorIndex implements VectorIndexGateway {
        private long deletedOwnerUserId;
        private long deletedAssetId;

        @Override
        public void ensureIndex() {
        }

        @Override
        public void upsert(VectorDocument document) {
        }

        @Override
        public void deleteByAsset(long ownerUserId, long assetId) {
            deletedOwnerUserId = ownerUserId;
            deletedAssetId = assetId;
        }
    }

    private record AssetFixture(long id, String externalId, Long versionId) {
    }
}
