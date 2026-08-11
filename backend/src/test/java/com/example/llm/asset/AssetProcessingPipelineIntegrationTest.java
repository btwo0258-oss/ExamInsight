package com.example.llm.asset;

import com.example.llm.asset.api.UploadDtos;
import com.example.llm.asset.config.AssetStorageProperties;
import com.example.llm.asset.processing.config.AssetProcessingProperties;
import com.example.llm.asset.processing.job.AssetJobRepository;
import com.example.llm.asset.processing.job.AssetJobRepository.AssetJob;
import com.example.llm.asset.processing.parse.AssetContentExtractor;
import com.example.llm.asset.processing.index.DocumentEmbeddingGateway;
import com.example.llm.asset.processing.index.EmbeddingRuntime;
import com.example.llm.asset.processing.index.VectorIndexGateway;
import com.example.llm.asset.processing.repository.AssetIndexRepository;
import com.example.llm.asset.processing.parse.StructuredTextChunker;
import com.example.llm.asset.processing.repository.AssetProcessingRepository;
import com.example.llm.asset.processing.repository.AssetPurgeRepository;
import com.example.llm.asset.processing.security.FileSecurityScanner;
import com.example.llm.asset.processing.service.AssetProcessingCoordinator;
import com.example.llm.asset.repository.UploadRepository;
import com.example.llm.asset.security.FileTypePolicy;
import com.example.llm.asset.service.UploadApplicationService;
import com.example.llm.asset.storage.LocalObjectStorageGateway;
import com.example.llm.asset.storage.StorageObjectKeyCipher;
import com.example.llm.auth.config.AuthProperties;
import com.example.llm.auth.security.AuthCrypto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Testcontainers(disabledWithoutDocker = true)
class AssetProcessingPipelineIntegrationTest {
    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.0.45")
            .withDatabaseName("examinsight_v2_processing_test")
            .withUsername("examinsight")
            .withPassword("examinsight-test-password");

    private static JdbcTemplate jdbc;

    @TempDir
    Path temporaryDirectory;

    private UploadApplicationService uploads;
    private AssetProcessingCoordinator coordinator;
    private AssetJobRepository jobs;
    private ObjectMapper objectMapper;
    private CapturingVectorIndex vectorIndex;
    private long userId;

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

    @BeforeEach
    void setUp() {
        LocalDateTime now = LocalDateTime.now(Clock.systemUTC());
        jdbc.update("""
                INSERT INTO app_user (
                    external_id, normalized_email, email_display, status, email_verified_at
                ) VALUES ('01ARZ3NDEKTSV4RRFFQ69G5FAW', 'pipeline@example.com',
                          'pipeline@example.com', 'ACTIVE', ?)
                """, now);
        userId = jdbc.queryForObject(
                "SELECT id FROM app_user WHERE normalized_email = 'pipeline@example.com'", Long.class);

        AssetStorageProperties storageProperties = new AssetStorageProperties();
        storageProperties.setBucketKey("pipeline-private");
        storageProperties.setKeySecret("pipeline-storage-secret-at-least-thirty-two-characters");
        storageProperties.getLocal().setRoot(temporaryDirectory.resolve("storage"));
        LocalObjectStorageGateway storage = new LocalObjectStorageGateway(storageProperties);
        StorageObjectKeyCipher objectKeyCipher = new StorageObjectKeyCipher(storageProperties);

        AuthProperties authProperties = new AuthProperties();
        authProperties.setHashSecret("pipeline-auth-secret-at-least-thirty-two-characters");
        AuthCrypto crypto = new AuthCrypto(authProperties);
        TransactionTemplate transactions = new TransactionTemplate(
                new DataSourceTransactionManager(jdbc.getDataSource()));
        objectMapper = new ObjectMapper();
        uploads = new UploadApplicationService(
                new UploadRepository(jdbc), storage, objectKeyCipher, new FileTypePolicy(),
                storageProperties, crypto, objectMapper, transactions, Clock.systemUTC());

        AssetProcessingProperties processingProperties = new AssetProcessingProperties();
        EmbeddingRuntime embeddingRuntime = mock(EmbeddingRuntime.class);
        when(embeddingRuntime.isSemanticIndexAvailable()).thenReturn(true);
        AssetProcessingRepository processingRepository = new AssetProcessingRepository(
                jdbc, transactions, crypto, processingProperties);
        AssetIndexRepository indexRepository = new AssetIndexRepository(
                jdbc, transactions, crypto, processingProperties, embeddingRuntime);
        jobs = new AssetJobRepository(jdbc, transactions, crypto);
        FileSecurityScanner cleanScanner = new FileSecurityScanner() {
            @Override
            public String scannerKey() {
                return "test-scanner";
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
        AssetContentExtractor extractor = (content, mimeType, sizeBytes) ->
                new AssetContentExtractor.ExtractedContent(
                        "第一章 极限与连续。\n\n函数极限用于描述函数在某一点附近的变化趋势。",
                        null,
                        "zh");
        DocumentEmbeddingGateway embeddings = text -> Collections.nCopies(2560, 0.01f);
        vectorIndex = new CapturingVectorIndex();
        coordinator = new AssetProcessingCoordinator(
                processingRepository, new AssetPurgeRepository(jdbc, transactions),
                indexRepository, cleanScanner, extractor,
                new StructuredTextChunker(processingProperties), storage,
                objectKeyCipher, embeddings, vectorIndex, processingProperties,
                embeddingRuntime,
                Clock.systemUTC());
    }

    @Test
    void cleanUploadBecomesReadyOnlyAfterAtomicParseAndChunkCommit() throws Exception {
        byte[] source = "source text".getBytes(StandardCharsets.UTF_8);
        UploadDtos.UploadSessionResponse upload = uploads.createUpload(
                userId,
                new UploadDtos.CreateUploadRequest(
                        "pipeline-upload-001", "期末复习资料.txt", "text/plain", source.length, null));
        uploads.uploadPart(userId, upload.uploadId(), 1, new ByteArrayInputStream(source));
        uploads.completeUpload(userId, upload.uploadId());

        String worker = "integration-worker";
        AssetJob scanJob = claim(worker);
        assertThat(scanJob.jobType()).isEqualTo("FILE_SECURITY_SCAN");
        Map<String, Object> scanResult = coordinator.execute(scanJob);
        jobs.succeed(scanJob, worker, objectMapper.writeValueAsString(scanResult), LocalDateTime.now(Clock.systemUTC()));

        // Replaying the domain operation after its commit does not create a second parse job.
        coordinator.execute(scanJob);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM async_job WHERE job_type = 'FILE_PARSE'", Integer.class)).isEqualTo(1);

        AssetJob parseJob = claim(worker);
        assertThat(parseJob.jobType()).isEqualTo("FILE_PARSE");
        Map<String, Object> parseResult = coordinator.execute(parseJob);
        jobs.succeed(parseJob, worker, objectMapper.writeValueAsString(parseResult), LocalDateTime.now(Clock.systemUTC()));

        AssetJob indexJob = claim(worker);
        assertThat(indexJob.jobType()).isEqualTo("FILE_INDEX");
        Map<String, Object> indexResult = coordinator.execute(indexJob);
        jobs.succeed(indexJob, worker, objectMapper.writeValueAsString(indexResult), LocalDateTime.now(Clock.systemUTC()));

        assertThat(jdbc.queryForObject("SELECT status FROM storage_object", String.class)).isEqualTo("AVAILABLE");
        assertThat(jdbc.queryForObject("SELECT status FROM asset_version", String.class)).isEqualTo("READY");
        assertThat(jdbc.queryForObject("SELECT status FROM asset_parse_result", String.class)).isEqualTo("READY");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM document_chunk", Integer.class)).isGreaterThan(0);
        assertThat(jdbc.queryForObject("SELECT current_version_id FROM asset", Long.class)).isNotNull();
        assertThat(jdbc.queryForObject("SELECT status FROM embedding_record", String.class)).isEqualTo("INDEXED");
        assertThat(vectorIndex.lastDocument).isNotNull();
        assertThat(vectorIndex.lastDocument.embedding()).hasSize(2560);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM async_job WHERE status = 'SUCCEEDED'", Integer.class)).isEqualTo(3);
    }

    private AssetJob claim(String worker) {
        LocalDateTime now = LocalDateTime.now(Clock.systemUTC());
        return jobs.claimNext(worker, now, now.plusMinutes(10)).orElseThrow();
    }

    private static final class CapturingVectorIndex implements VectorIndexGateway {
        private VectorDocument lastDocument;

        @Override
        public void ensureIndex() {
        }

        @Override
        public void upsert(VectorDocument document) {
            this.lastDocument = document;
        }

        @Override
        public void deleteByAsset(long ownerUserId, long assetId) {
        }
    }
}
