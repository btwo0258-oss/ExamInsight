package com.example.llm.asset;

import com.example.llm.asset.api.UploadDtos;
import com.example.llm.asset.config.AssetStorageProperties;
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

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class UploadApplicationServiceIntegrationTest {
    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.0.45")
            .withDatabaseName("examinsight_v2_upload_test")
            .withUsername("examinsight")
            .withPassword("examinsight-test-password");

    private static JdbcTemplate jdbc;

    @TempDir
    Path temporaryDirectory;

    private UploadApplicationService service;
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
                ) VALUES ('01ARZ3NDEKTSV4RRFFQ69G5FAV', 'upload@example.com',
                          'upload@example.com', 'ACTIVE', ?)
                """, now);
        userId = jdbc.queryForObject(
                "SELECT id FROM app_user WHERE normalized_email = 'upload@example.com'", Long.class);

        AssetStorageProperties storageProperties = new AssetStorageProperties();
        storageProperties.setBucketKey("integration-private");
        storageProperties.setKeySecret("integration-storage-secret-at-least-thirty-two-characters");
        storageProperties.getLocal().setRoot(temporaryDirectory.resolve("storage"));

        AuthProperties authProperties = new AuthProperties();
        authProperties.setHashSecret("integration-auth-secret-at-least-thirty-two-characters");
        AuthCrypto crypto = new AuthCrypto(authProperties);
        TransactionTemplate transactions = new TransactionTemplate(
                new DataSourceTransactionManager(jdbc.getDataSource()));
        service = new UploadApplicationService(
                new UploadRepository(jdbc),
                new LocalObjectStorageGateway(storageProperties),
                new StorageObjectKeyCipher(storageProperties),
                new FileTypePolicy(),
                storageProperties,
                crypto,
                new ObjectMapper(),
                transactions,
                Clock.systemUTC());
    }

    @Test
    void completionCreatesOneQuarantinedAssetGraphAndIsIdempotent() {
        byte[] content = "ExamInsight".getBytes(StandardCharsets.UTF_8);
        UploadDtos.UploadSessionResponse upload = service.createUpload(
                userId,
                new UploadDtos.CreateUploadRequest(
                        "upload-integration-001", "复习资料.txt", "text/plain",
                        content.length, null));
        service.uploadPart(
                userId, upload.uploadId(), 1, new ByteArrayInputStream(content));

        UploadDtos.UploadCompletionResponse first = service.completeUpload(userId, upload.uploadId());
        UploadDtos.UploadCompletionResponse second = service.completeUpload(userId, upload.uploadId());

        assertThat(second).isEqualTo(first);
        assertThat(first.status()).isEqualTo("COMPLETED");
        assertThat(first.asset().status()).isEqualTo("ACTIVE");
        assertThat(first.version().status()).isEqualTo("QUARANTINED");
        assertThat(first.asset().name()).isEqualTo("复习资料.txt");
        assertThat(first.securityScanJob().status()).isEqualTo("QUEUED");
        assertThat(first.securityScanJob().stage()).isEqualTo("SECURITY_SCAN");

        assertThat(count("storage_object")).isEqualTo(1);
        assertThat(count("asset")).isEqualTo(1);
        assertThat(count("asset_version")).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM async_job WHERE job_type = 'FILE_SECURITY_SCAN'", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT current_version_id FROM asset LIMIT 1", Long.class)).isNull();
        assertThat(jdbc.queryForObject(
                "SELECT OCTET_LENGTH(object_key_ciphertext) FROM storage_object LIMIT 1", Integer.class))
                .isGreaterThan(20);
        assertThat(new String(jdbc.queryForObject(
                "SELECT object_key_ciphertext FROM storage_object LIMIT 1", byte[].class),
                StandardCharsets.UTF_8)).doesNotContain("quarantine/");
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }
}
