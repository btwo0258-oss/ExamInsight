package com.example.llm.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
class PlatformFoundationMigrationTest {

    static final Set<String> FOUNDATION_TABLES = Set.of(
            "async_job",
            "async_job_attempt",
            "outbox_event",
            "idempotency_record",
            "domain_audit_event",
            "legacy_import_map"
    );

    static final Set<String> MODEL_REGISTRY_TABLES = Set.of(
            "model_provider",
            "model_definition",
            "model_policy_version",
            "model_policy_route",
            "prompt_template",
            "prompt_version"
    );

    static final Set<String> IDENTITY_TABLES = Set.of(
            "app_user",
            "user_credential",
            "user_profile",
            "user_setting",
            "user_device",
            "auth_session",
            "email_verification",
            "email_delivery",
            "password_reset_token",
            "security_event",
            "admin_user",
            "admin_mfa_credential",
            "admin_recovery_code",
            "admin_session"
    );

    static final Set<String> STORAGE_ASSET_TABLES = Set.of(
            "upload_session",
            "storage_object",
            "asset",
            "asset_version",
            "asset_parse_result",
            "document_chunk",
            "embedding_record"
    );

    static final Set<String> PRIVACY_ADMIN_TABLES = Set.of(
            "processing_purpose",
            "privacy_notice_version",
            "privacy_notice_acknowledgement",
            "user_consent",
            "processor",
            "processor_version",
            "privacy_request",
            "privacy_request_event",
            "data_export_job",
            "account_deletion_request",
            "retention_policy",
            "retention_run",
            "legal_hold",
            "deletion_job",
            "deletion_item",
            "data_tombstone",
            "admin_access_case",
            "admin_access_grant",
            "admin_access_audit"
    );

    static final Set<String> KNOWLEDGE_BASE_TABLES = Set.of(
            "knowledge_base",
            "knowledge_base_asset"
    );

    static final Set<String> CONVERSATION_AI_TABLES = Set.of(
            "capability_definition",
            "conversation",
            "conversation_branch",
            "message",
            "assistant_response_group",
            "message_part",
            "message_attachment",
            "message_citation",
            "ai_run",
            "ai_context_snapshot",
            "retrieval_run",
            "retrieval_result",
            "ai_tool_call",
            "pending_action"
    );

    private static final Set<String> FOUNDATION_SCHEMA_TABLES = schemaTables(FOUNDATION_TABLES);

    private static final Set<String> MODEL_REGISTRY_SCHEMA_TABLES = schemaTables(
            FOUNDATION_TABLES,
            MODEL_REGISTRY_TABLES
    );

    private static final Set<String> IDENTITY_SCHEMA_TABLES = schemaTables(
            FOUNDATION_TABLES,
            MODEL_REGISTRY_TABLES,
            IDENTITY_TABLES
    );

    private static final Set<String> STORAGE_ASSET_SCHEMA_TABLES = schemaTables(
            FOUNDATION_TABLES,
            MODEL_REGISTRY_TABLES,
            IDENTITY_TABLES,
            STORAGE_ASSET_TABLES
    );

    private static final Set<String> PRIVACY_ADMIN_SCHEMA_TABLES = schemaTables(
            FOUNDATION_TABLES,
            MODEL_REGISTRY_TABLES,
            IDENTITY_TABLES,
            STORAGE_ASSET_TABLES,
            PRIVACY_ADMIN_TABLES
    );

    private static final Set<String> KNOWLEDGE_BASE_SCHEMA_TABLES = schemaTables(
            FOUNDATION_TABLES,
            MODEL_REGISTRY_TABLES,
            IDENTITY_TABLES,
            STORAGE_ASSET_TABLES,
            PRIVACY_ADMIN_TABLES,
            KNOWLEDGE_BASE_TABLES
    );

    private static final Set<String> CONVERSATION_AI_SCHEMA_TABLES = schemaTables(
            FOUNDATION_TABLES,
            MODEL_REGISTRY_TABLES,
            IDENTITY_TABLES,
            STORAGE_ASSET_TABLES,
            PRIVACY_ADMIN_TABLES,
            KNOWLEDGE_BASE_TABLES,
            CONVERSATION_AI_TABLES
    );

    /** Tables introduced by the post-V007 runtime and product migrations. */
    private static final Set<String> FINAL_SCHEMA_TABLES = schemaTables(
            FOUNDATION_TABLES,
            MODEL_REGISTRY_TABLES,
            IDENTITY_TABLES,
            STORAGE_ASSET_TABLES,
            PRIVACY_ADMIN_TABLES,
            KNOWLEDGE_BASE_TABLES,
            CONVERSATION_AI_TABLES,
            Set.of(
                    "terms_document_version",
                    "terms_acceptance",
                    "model_invocation",
                    "asset_preview_derivative",
                    "ai_context_source",
                    "artifact_draft",
                    "artifact_revision",
                    "conversation_memory_checkpoint",
                    "smart_learning_project",
                    "smart_learning_job",
                    "smart_learning_task",
                    "smart_learning_resource",
                    "smart_learning_execution"
            )
    );

    @Container
    private static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.0.45")
            .withDatabaseName("examinsight_v2_test")
            .withUsername("examinsight_test")
            .withPassword("examinsight_test_password");

    @Test
    void upgradesOneVersionAtATimeAndEnforcesFrozenBoundaries() throws SQLException {
        Flyway foundationFlyway = configureFlyway("001");
        assertRunsOneMigrationThenBecomesIdempotent(foundationFlyway);

        try (Connection connection = MYSQL.createConnection("")) {
            assertThat(readTables(connection)).containsExactlyInAnyOrderElementsOf(FOUNDATION_SCHEMA_TABLES);
            assertInvalidJobStatusIsRejected(connection);
        }

        Flyway modelRegistryFlyway = configureFlyway("002");
        assertRunsOneMigrationThenBecomesIdempotent(modelRegistryFlyway);

        try (Connection connection = MYSQL.createConnection("")) {
            assertThat(readTables(connection)).containsExactlyInAnyOrderElementsOf(MODEL_REGISTRY_SCHEMA_TABLES);
            assertInvalidProviderStatusIsRejected(connection);
        }

        Flyway identityFlyway = configureFlyway("003");
        assertRunsOneMigrationThenBecomesIdempotent(identityFlyway);

        long userId;
        try (Connection connection = MYSQL.createConnection("")) {
            assertThat(readTables(connection)).containsExactlyInAnyOrderElementsOf(IDENTITY_SCHEMA_TABLES);
            assertInvalidUserStatusIsRejected(connection);
            userId = insertValidUser(connection);
            assertOnlyOneActivePasswordResetTokenIsAllowed(connection, userId);
            assertPlatformUserForeignKeyIsEnforced(connection);
        }

        Flyway storageAssetFlyway = configureFlyway("004");
        assertRunsOneMigrationThenBecomesIdempotent(storageAssetFlyway);

        try (Connection connection = MYSQL.createConnection("")) {
            assertThat(readTables(connection)).containsExactlyInAnyOrderElementsOf(STORAGE_ASSET_SCHEMA_TABLES);
            assertStorageAndAssetBoundaries(connection, userId);
        }

        Flyway privacyAdminFlyway = configureFlyway("005");
        assertRunsOneMigrationThenBecomesIdempotent(privacyAdminFlyway);

        try (Connection connection = MYSQL.createConnection("")) {
            assertThat(readTables(connection)).containsExactlyInAnyOrderElementsOf(PRIVACY_ADMIN_SCHEMA_TABLES);
            assertPrivacyDeletionAndAdminBoundaries(connection, userId);
        }

        Flyway knowledgeBaseFlyway = configureFlyway("006");
        assertRunsOneMigrationThenBecomesIdempotent(knowledgeBaseFlyway);

        try (Connection connection = MYSQL.createConnection("")) {
            assertThat(readTables(connection)).containsExactlyInAnyOrderElementsOf(KNOWLEDGE_BASE_SCHEMA_TABLES);
            assertKnowledgeBaseBoundaries(connection, userId);
        }

        Flyway conversationAiFlyway = configureFlyway("007");
        assertRunsOneMigrationThenBecomesIdempotent(conversationAiFlyway);

        try (Connection connection = MYSQL.createConnection("")) {
            assertThat(readTables(connection)).containsExactlyInAnyOrderElementsOf(CONVERSATION_AI_SCHEMA_TABLES);
        }

        Flyway remainingMigrationsFlyway = configureFlyway(null);
        MigrateResult remaining = remainingMigrationsFlyway.migrate();
        MigrateResult secondRun = remainingMigrationsFlyway.migrate();
        // V007.1 plus V008..V023 (including smart-learning execution and navigation state).
        assertThat(remaining.migrationsExecuted).isEqualTo(17);
        assertThat(secondRun.migrationsExecuted).isZero();
        assertThat(remainingMigrationsFlyway.validateWithResult().validationSuccessful).isTrue();

        try (Connection connection = MYSQL.createConnection("")) {
            assertThat(readTables(connection)).containsExactlyInAnyOrderElementsOf(FINAL_SCHEMA_TABLES);
            assertConversationAndAiRuntimeBoundaries(connection, userId);
        }
    }

    private Flyway configureFlyway(String targetVersion) {
        FluentConfiguration configuration = Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .cleanDisabled(true)
                .validateMigrationNaming(true);
        if (targetVersion != null) {
            configuration.target(MigrationVersion.fromVersion(targetVersion));
        }
        return configuration.load();
    }

    private void assertRunsOneMigrationThenBecomesIdempotent(Flyway flyway) {
        MigrateResult firstRun = flyway.migrate();
        MigrateResult secondRun = flyway.migrate();

        assertThat(firstRun.migrationsExecuted).isEqualTo(1);
        assertThat(secondRun.migrationsExecuted).isZero();
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
    }

    private Set<String> readTables(Connection connection) throws SQLException {
        Set<String> tableNames = new LinkedHashSet<>();
        String sql = """
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_type = 'BASE TABLE'
                """;

        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                tableNames.add(resultSet.getString(1).toLowerCase());
            }
        }
        return tableNames;
    }

    private void assertInvalidJobStatusIsRejected(Connection connection) throws SQLException {
        String sql = """
                INSERT INTO async_job (external_id, job_type, status, max_attempts)
                VALUES ('01J00000000000000000000000', 'MIGRATION_TEST', 'NOT_A_REAL_STATUS', 1)
                """;

        try (Statement statement = connection.createStatement()) {
            assertThatThrownBy(() -> statement.executeUpdate(sql))
                    .isInstanceOf(SQLException.class);
        }
    }

    private void assertInvalidProviderStatusIsRejected(Connection connection) throws SQLException {
        String sql = """
                INSERT INTO model_provider (
                    external_id,
                    provider_key,
                    display_name,
                    status,
                    credential_secret_ref,
                    timeout_ms
                ) VALUES (
                    '01J00000000000000000000001',
                    'migration-test-provider',
                    'Migration test provider',
                    'NOT_A_REAL_STATUS',
                    'secret://migration-test/provider',
                    30000
                )
                """;

        try (Statement statement = connection.createStatement()) {
            assertThatThrownBy(() -> statement.executeUpdate(sql))
                    .isInstanceOf(SQLException.class);
        }
    }

    private void assertInvalidUserStatusIsRejected(Connection connection) throws SQLException {
        String sql = """
                INSERT INTO app_user (
                    external_id,
                    normalized_email,
                    email_display,
                    status,
                    age_gate_acknowledged_at
                ) VALUES (
                    '01J00000000000000000000002',
                    'invalid-status@example.test',
                    'invalid-status@example.test',
                    'NOT_A_REAL_STATUS',
                    CURRENT_TIMESTAMP(3)
                )
                """;

        try (Statement statement = connection.createStatement()) {
            assertThatThrownBy(() -> statement.executeUpdate(sql))
                    .isInstanceOf(SQLException.class);
        }
    }

    private long insertValidUser(Connection connection) throws SQLException {
        String insert = """
                INSERT INTO app_user (
                    external_id,
                    normalized_email,
                    email_display,
                    status,
                    age_gate_acknowledged_at,
                    email_verified_at
                ) VALUES (
                    '01J00000000000000000000003',
                    'identity-test@example.test',
                    'identity-test@example.test',
                    'ACTIVE',
                    CURRENT_TIMESTAMP(3),
                    CURRENT_TIMESTAMP(3)
                )
                """;
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(insert);
        }

        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("""
                     SELECT id FROM app_user
                     WHERE normalized_email = 'identity-test@example.test'
                     """)) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getLong(1);
        }
    }

    private void assertOnlyOneActivePasswordResetTokenIsAllowed(Connection connection, long userId)
            throws SQLException {
        String firstInsert = """
                INSERT INTO password_reset_token (
                    external_id,
                    user_id,
                    token_hash,
                    status,
                    expires_at,
                    session_version_at_issue
                ) VALUES (
                    '01J00000000000000000000004',
                    %d,
                    REPEAT('a', 64),
                    'ACTIVE',
                    DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL 30 MINUTE),
                    1
                )
                """.formatted(userId);
        String duplicateActiveInsert = """
                INSERT INTO password_reset_token (
                    external_id,
                    user_id,
                    token_hash,
                    status,
                    expires_at,
                    session_version_at_issue
                ) VALUES (
                    '01J00000000000000000000005',
                    %d,
                    REPEAT('b', 64),
                    'ACTIVE',
                    DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL 30 MINUTE),
                    1
                )
                """.formatted(userId);

        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(firstInsert);
            assertThatThrownBy(() -> statement.executeUpdate(duplicateActiveInsert))
                    .isInstanceOf(SQLException.class);
        }
    }

    private void assertPlatformUserForeignKeyIsEnforced(Connection connection) throws SQLException {
        String sql = """
                INSERT INTO async_job (external_id, user_id, job_type, status, max_attempts)
                VALUES ('01J00000000000000000000006', 999999999, 'IDENTITY_TEST', 'QUEUED', 1)
                """;

        try (Statement statement = connection.createStatement()) {
            assertThatThrownBy(() -> statement.executeUpdate(sql))
                    .isInstanceOf(SQLException.class);
        }
    }

    private void assertStorageAndAssetBoundaries(Connection connection, long userId) throws SQLException {
        assertInvalidUploadStatusIsRejected(connection, userId);
        long uploadSessionId = insertCompletedUploadSession(connection, userId);
        assertAvailableStorageRequiresCompletedScan(connection, userId);
        long storageObjectId = insertAvailableStorageObject(connection, userId);
        long assetId = insertValidAsset(connection, userId);
        long assetVersionId = insertValidFileVersion(
                connection,
                userId,
                assetId,
                uploadSessionId,
                storageObjectId
        );
        assertUploadSessionProducesOnlyOneVersion(
                connection,
                userId,
                assetId,
                uploadSessionId,
                storageObjectId
        );
        assertVersionCannotStoreFileAndTextTogether(connection, userId, assetId, storageObjectId);
        long parseResultId = insertReadyParseResult(connection, userId, assetVersionId);
        assertOversizedChunkIsRejected(connection, parseResultId);
        long chunkId = insertValidChunk(connection, parseResultId);
        long embeddingModelId = insertEmbeddingModel(connection);
        assertInvalidEmbeddingStatusIsRejected(connection, chunkId, embeddingModelId);
    }

    private void assertInvalidUploadStatusIsRejected(Connection connection, long userId) throws SQLException {
        String sql = """
                INSERT INTO upload_session (
                    external_id,
                    user_id,
                    upload_key,
                    original_filename,
                    expected_size,
                    status,
                    expires_at
                ) VALUES (
                    '01J00000000000000000000007',
                    %d,
                    'invalid-status-upload',
                    'invalid.pdf',
                    4,
                    'NOT_A_REAL_STATUS',
                    DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL 1 HOUR)
                )
                """.formatted(userId);

        try (Statement statement = connection.createStatement()) {
            assertThatThrownBy(() -> statement.executeUpdate(sql))
                    .isInstanceOf(SQLException.class);
        }
    }

    private long insertCompletedUploadSession(Connection connection, long userId) throws SQLException {
        String sql = """
                INSERT INTO upload_session (
                    external_id,
                    user_id,
                    upload_key,
                    original_filename,
                    declared_mime,
                    expected_size,
                    expected_sha256,
                    multipart_upload_ref_ciphertext,
                    status,
                    uploaded_bytes,
                    expires_at,
                    completed_at
                ) VALUES (
                    '01J00000000000000000000008',
                    %d,
                    'completed-upload',
                    'lecture.pdf',
                    'application/pdf',
                    4,
                    REPEAT('a', 64),
                    X'01',
                    'COMPLETED',
                    4,
                    DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL 1 HOUR),
                    CURRENT_TIMESTAMP(3)
                )
                """.formatted(userId);
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
        return readId(connection, "upload_session", "01J00000000000000000000008");
    }

    private void assertAvailableStorageRequiresCompletedScan(Connection connection, long userId)
            throws SQLException {
        String sql = """
                INSERT INTO storage_object (
                    external_id,
                    owner_user_id,
                    bucket_key,
                    object_key_ciphertext,
                    object_key_hash,
                    sha256,
                    size_bytes,
                    mime_type,
                    status,
                    verified_at
                ) VALUES (
                    '01J00000000000000000000009',
                    %d,
                    'migration-test',
                    X'02',
                    REPEAT('b', 64),
                    REPEAT('c', 64),
                    4,
                    'application/pdf',
                    'AVAILABLE',
                    CURRENT_TIMESTAMP(3)
                )
                """.formatted(userId);

        try (Statement statement = connection.createStatement()) {
            assertThatThrownBy(() -> statement.executeUpdate(sql))
                    .isInstanceOf(SQLException.class);
        }
    }

    private long insertAvailableStorageObject(Connection connection, long userId) throws SQLException {
        String sql = """
                INSERT INTO storage_object (
                    external_id,
                    owner_user_id,
                    bucket_key,
                    object_key_ciphertext,
                    object_key_hash,
                    sha256,
                    size_bytes,
                    mime_type,
                    status,
                    verified_at,
                    scanner_key,
                    scanner_version,
                    scan_completed_at
                ) VALUES (
                    '01J00000000000000000000010',
                    %d,
                    'migration-test',
                    X'03',
                    REPEAT('d', 64),
                    REPEAT('e', 64),
                    4,
                    'application/pdf',
                    'AVAILABLE',
                    CURRENT_TIMESTAMP(3),
                    'migration-test-scanner',
                    '1',
                    CURRENT_TIMESTAMP(3)
                )
                """.formatted(userId);
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
        return readId(connection, "storage_object", "01J00000000000000000000010");
    }

    private long insertValidAsset(Connection connection, long userId) throws SQLException {
        String sql = """
                INSERT INTO asset (
                    external_id,
                    user_id,
                    name,
                    asset_type,
                    source_type,
                    status
                ) VALUES (
                    '01J00000000000000000000011',
                    %d,
                    'Lecture notes',
                    'FILE',
                    'UPLOAD',
                    'ACTIVE'
                )
                """.formatted(userId);
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
        return readId(connection, "asset", "01J00000000000000000000011");
    }

    private long insertValidFileVersion(
            Connection connection,
            long userId,
            long assetId,
            long uploadSessionId,
            long storageObjectId
    ) throws SQLException {
        String sql = """
                INSERT INTO asset_version (
                    external_id,
                    asset_id,
                    version_no,
                    upload_session_id,
                    storage_object_id,
                    content_sha256,
                    mime_type,
                    size_bytes,
                    source_type,
                    status,
                    created_by_user_id
                ) VALUES (
                    '01J00000000000000000000012',
                    %d,
                    1,
                    %d,
                    %d,
                    REPEAT('e', 64),
                    'application/pdf',
                    4,
                    'UPLOAD',
                    'READY',
                    %d
                )
                """.formatted(assetId, uploadSessionId, storageObjectId, userId);
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
        return readId(connection, "asset_version", "01J00000000000000000000012");
    }

    private void assertUploadSessionProducesOnlyOneVersion(
            Connection connection,
            long userId,
            long assetId,
            long uploadSessionId,
            long storageObjectId
    ) throws SQLException {
        String sql = """
                INSERT INTO asset_version (
                    external_id,
                    asset_id,
                    version_no,
                    upload_session_id,
                    storage_object_id,
                    content_sha256,
                    mime_type,
                    size_bytes,
                    source_type,
                    status,
                    created_by_user_id
                ) VALUES (
                    '01J00000000000000000000013',
                    %d,
                    2,
                    %d,
                    %d,
                    REPEAT('f', 64),
                    'application/pdf',
                    4,
                    'UPLOAD',
                    'READY',
                    %d
                )
                """.formatted(assetId, uploadSessionId, storageObjectId, userId);

        try (Statement statement = connection.createStatement()) {
            assertThatThrownBy(() -> statement.executeUpdate(sql))
                    .isInstanceOf(SQLException.class);
        }
    }

    private void assertVersionCannotStoreFileAndTextTogether(
            Connection connection,
            long userId,
            long assetId,
            long storageObjectId
    ) throws SQLException {
        String sql = """
                INSERT INTO asset_version (
                    external_id,
                    asset_id,
                    version_no,
                    storage_object_id,
                    text_content,
                    content_sha256,
                    mime_type,
                    size_bytes,
                    source_type,
                    status,
                    created_by_user_id
                ) VALUES (
                    '01J00000000000000000000014',
                    %d,
                    2,
                    %d,
                    'text',
                    REPEAT('1', 64),
                    'application/pdf',
                    4,
                    'LEGACY_IMPORT',
                    'READY',
                    %d
                )
                """.formatted(assetId, storageObjectId, userId);

        try (Statement statement = connection.createStatement()) {
            assertThatThrownBy(() -> statement.executeUpdate(sql))
                    .isInstanceOf(SQLException.class);
        }
    }

    private long insertReadyParseResult(Connection connection, long userId, long assetVersionId)
            throws SQLException {
        String jobSql = """
                INSERT INTO async_job (
                    external_id,
                    user_id,
                    job_type,
                    aggregate_type,
                    aggregate_external_id,
                    status,
                    max_attempts
                ) VALUES (
                    '01J00000000000000000000015',
                    %d,
                    'ASSET_PARSE',
                    'ASSET_VERSION',
                    '01J00000000000000000000012',
                    'QUEUED',
                    3
                )
                """.formatted(userId);
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(jobSql);
        }
        long jobId = readId(connection, "async_job", "01J00000000000000000000015");

        String invalidResult = """
                INSERT INTO asset_parse_result (
                    external_id,
                    asset_version_id,
                    parser_key,
                    parser_version,
                    async_job_id,
                    status,
                    chunk_count,
                    plain_text_sha256,
                    completed_at
                ) VALUES (
                    '01J00000000000000000000016',
                    %d,
                    'migration-parser',
                    'invalid-empty',
                    %d,
                    'READY',
                    0,
                    REPEAT('2', 64),
                    CURRENT_TIMESTAMP(3)
                )
                """.formatted(assetVersionId, jobId);
        try (Statement statement = connection.createStatement()) {
            assertThatThrownBy(() -> statement.executeUpdate(invalidResult))
                    .isInstanceOf(SQLException.class);
        }

        String validResult = """
                INSERT INTO asset_parse_result (
                    external_id,
                    asset_version_id,
                    parser_key,
                    parser_version,
                    async_job_id,
                    status,
                    language,
                    page_count,
                    chunk_count,
                    plain_text_sha256,
                    completed_at
                ) VALUES (
                    '01J00000000000000000000017',
                    %d,
                    'migration-parser',
                    '1',
                    %d,
                    'READY',
                    'en',
                    1,
                    1,
                    REPEAT('3', 64),
                    CURRENT_TIMESTAMP(3)
                )
                """.formatted(assetVersionId, jobId);
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(validResult);
        }
        return readId(connection, "asset_parse_result", "01J00000000000000000000017");
    }

    private void assertOversizedChunkIsRejected(Connection connection, long parseResultId) throws SQLException {
        String sql = """
                INSERT INTO document_chunk (
                    external_id,
                    parse_result_id,
                    sequence_no,
                    content,
                    content_sha256,
                    token_count
                ) VALUES (
                    '01J00000000000000000000018',
                    %d,
                    1,
                    'oversized token count',
                    REPEAT('4', 64),
                    2001
                )
                """.formatted(parseResultId);

        try (Statement statement = connection.createStatement()) {
            assertThatThrownBy(() -> statement.executeUpdate(sql))
                    .isInstanceOf(SQLException.class);
        }
    }

    private long insertValidChunk(Connection connection, long parseResultId) throws SQLException {
        String sql = """
                INSERT INTO document_chunk (
                    external_id,
                    parse_result_id,
                    sequence_no,
                    content,
                    content_sha256,
                    token_count,
                    page_from,
                    page_to
                ) VALUES (
                    '01J00000000000000000000019',
                    %d,
                    1,
                    'valid parsed content',
                    REPEAT('5', 64),
                    4,
                    1,
                    1
                )
                """.formatted(parseResultId);
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
        return readId(connection, "document_chunk", "01J00000000000000000000019");
    }

    private long insertEmbeddingModel(Connection connection) throws SQLException {
        String providerSql = """
                INSERT INTO model_provider (
                    external_id,
                    provider_key,
                    display_name,
                    status,
                    credential_secret_ref,
                    timeout_ms
                ) VALUES (
                    '01J00000000000000000000020',
                    'migration-embedding-provider',
                    'Migration embedding provider',
                    'ACTIVE',
                    'secret://migration-test/embedding',
                    30000
                )
                """;
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(providerSql);
        }
        long providerId = readId(connection, "model_provider", "01J00000000000000000000020");

        String modelSql = """
                INSERT INTO model_definition (
                    external_id,
                    provider_id,
                    model_key,
                    provider_model_name,
                    role,
                    status
                ) VALUES (
                    '01J00000000000000000000021',
                    %d,
                    'migration-embedding-model',
                    'migration-embedding-model',
                    'EMBEDDING',
                    'ACTIVE'
                )
                """.formatted(providerId);
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(modelSql);
        }
        return readId(connection, "model_definition", "01J00000000000000000000021");
    }

    private void assertInvalidEmbeddingStatusIsRejected(
            Connection connection,
            long chunkId,
            long modelDefinitionId
    ) throws SQLException {
        String sql = """
                INSERT INTO embedding_record (
                    external_id,
                    chunk_id,
                    model_definition_id,
                    embedding_version,
                    index_name,
                    index_document_id,
                    content_sha256,
                    status
                ) VALUES (
                    '01J00000000000000000000022',
                    %d,
                    %d,
                    '1',
                    'migration-index',
                    'migration-document',
                    REPEAT('5', 64),
                    'NOT_A_REAL_STATUS'
                )
                """.formatted(chunkId, modelDefinitionId);

        try (Statement statement = connection.createStatement()) {
            assertThatThrownBy(() -> statement.executeUpdate(sql))
                    .isInstanceOf(SQLException.class);
        }
    }

    private void assertPrivacyDeletionAndAdminBoundaries(Connection connection, long userId) throws SQLException {
        assertOnlyOneActivePrivacyRequestOfEachTypeIsAllowed(connection, userId);
        long requesterAdminId = insertAdmin(
                connection,
                "01J00000000000000000000025",
                "privacy-requester@example.test",
                "Privacy requester"
        );
        insertAdmin(
                connection,
                "01J00000000000000000000026",
                "privacy-approver@example.test",
                "Privacy approver"
        );
        assertAdminCannotApproveOwnAccessCase(connection, userId, requesterAdminId);
        long pendingCaseId = insertPendingAdminAccessCase(connection, userId, requesterAdminId);
        assertContentGrantRequiresSpecificObjectHash(connection, pendingCaseId);
        assertRetainedDeletionItemRequiresExactlyOneRetentionBasis(connection, userId);
        assertTombstoneRejectsRawOrMalformedObjectReference(connection);
    }

    private void assertOnlyOneActivePrivacyRequestOfEachTypeIsAllowed(Connection connection, long userId)
            throws SQLException {
        String firstInsert = """
                INSERT INTO privacy_request (
                    external_id,
                    user_id,
                    subject_hash,
                    request_type,
                    status,
                    due_at
                ) VALUES (
                    '01J00000000000000000000023',
                    %d,
                    REPEAT('a', 64),
                    'EXPORT',
                    'RECEIVED',
                    DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL 15 DAY)
                )
                """.formatted(userId);
        String duplicateActiveInsert = """
                INSERT INTO privacy_request (
                    external_id,
                    user_id,
                    subject_hash,
                    request_type,
                    status,
                    due_at
                ) VALUES (
                    '01J00000000000000000000024',
                    %d,
                    REPEAT('a', 64),
                    'EXPORT',
                    'VERIFYING',
                    DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL 15 DAY)
                )
                """.formatted(userId);

        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(firstInsert);
            assertThatThrownBy(() -> statement.executeUpdate(duplicateActiveInsert))
                    .isInstanceOf(SQLException.class);
        }
    }

    private long insertAdmin(
            Connection connection,
            String externalId,
            String normalizedEmail,
            String displayName
    ) throws SQLException {
        String sql = """
                INSERT INTO admin_user (external_id, normalized_email, display_name, status)
                VALUES ('%s', '%s', '%s', 'ACTIVE')
                """.formatted(externalId, normalizedEmail, displayName);
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
        return readId(connection, "admin_user", externalId);
    }

    private void assertAdminCannotApproveOwnAccessCase(
            Connection connection,
            long userId,
            long requesterAdminId
    ) throws SQLException {
        String sql = """
                INSERT INTO admin_access_case (
                    external_id,
                    requested_by_admin_id,
                    approved_by_admin_id,
                    user_id,
                    subject_hash,
                    purpose_key,
                    status,
                    requested_at,
                    decision_at,
                    approved_at,
                    expires_at,
                    reason
                ) VALUES (
                    '01J00000000000000000000027',
                    %d,
                    %d,
                    %d,
                    REPEAT('b', 64),
                    'SUPPORT_INVESTIGATION',
                    'ACTIVE',
                    CURRENT_TIMESTAMP(3),
                    CURRENT_TIMESTAMP(3),
                    CURRENT_TIMESTAMP(3),
                    DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL 30 MINUTE),
                    'Investigate a user reported content rendering issue'
                )
                """.formatted(requesterAdminId, requesterAdminId, userId);

        try (Statement statement = connection.createStatement()) {
            assertThatThrownBy(() -> statement.executeUpdate(sql))
                    .isInstanceOf(SQLException.class);
        }
    }

    private long insertPendingAdminAccessCase(
            Connection connection,
            long userId,
            long requesterAdminId
    ) throws SQLException {
        String sql = """
                INSERT INTO admin_access_case (
                    external_id,
                    requested_by_admin_id,
                    user_id,
                    subject_hash,
                    purpose_key,
                    status,
                    requested_at,
                    reason
                ) VALUES (
                    '01J00000000000000000000028',
                    %d,
                    %d,
                    REPEAT('b', 64),
                    'SUPPORT_INVESTIGATION',
                    'PENDING_APPROVAL',
                    CURRENT_TIMESTAMP(3),
                    'Investigate a user reported content rendering issue'
                )
                """.formatted(requesterAdminId, userId);
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
        return readId(connection, "admin_access_case", "01J00000000000000000000028");
    }

    private void assertContentGrantRequiresSpecificObjectHash(Connection connection, long caseId)
            throws SQLException {
        String sql = """
                INSERT INTO admin_access_grant (
                    external_id,
                    case_id,
                    object_type,
                    permission,
                    starts_at,
                    expires_at
                ) VALUES (
                    '01J00000000000000000000029',
                    %d,
                    'ASSET',
                    'READ_CONTENT',
                    CURRENT_TIMESTAMP(3),
                    DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL 30 MINUTE)
                )
                """.formatted(caseId);

        try (Statement statement = connection.createStatement()) {
            assertThatThrownBy(() -> statement.executeUpdate(sql))
                    .isInstanceOf(SQLException.class);
        }
    }

    private void assertRetainedDeletionItemRequiresExactlyOneRetentionBasis(Connection connection, long userId)
            throws SQLException {
        String jobSql = """
                INSERT INTO async_job (
                    external_id,
                    user_id,
                    job_type,
                    aggregate_type,
                    aggregate_external_id,
                    status,
                    max_attempts
                ) VALUES (
                    '01J00000000000000000000030',
                    %d,
                    'DATA_DELETION',
                    'ASSET',
                    '01J00000000000000000000011',
                    'QUEUED',
                    3
                )
                """.formatted(userId);
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(jobSql);
        }
        long asyncJobId = readId(connection, "async_job", "01J00000000000000000000030");

        String deletionJobSql = """
                INSERT INTO deletion_job (
                    external_id,
                    user_id,
                    subject_hash,
                    async_job_id,
                    trigger_type,
                    scope_type,
                    status
                ) VALUES (
                    '01J00000000000000000000031',
                    %d,
                    REPEAT('a', 64),
                    %d,
                    'USER_OBJECT_DELETE',
                    'ASSET',
                    'QUEUED'
                )
                """.formatted(userId, asyncJobId);
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(deletionJobSql);
        }
        long deletionJobId = readId(connection, "deletion_job", "01J00000000000000000000031");

        String invalidItemSql = """
                INSERT INTO deletion_item (
                    external_id,
                    deletion_job_id,
                    store_type,
                    object_type,
                    object_ref_hash,
                    status,
                    attempt_count,
                    completed_at
                ) VALUES (
                    '01J00000000000000000000032',
                    %d,
                    'MYSQL',
                    'asset',
                    REPEAT('c', 64),
                    'RETAINED',
                    1,
                    CURRENT_TIMESTAMP(3)
                )
                """.formatted(deletionJobId);

        try (Statement statement = connection.createStatement()) {
            assertThatThrownBy(() -> statement.executeUpdate(invalidItemSql))
                    .isInstanceOf(SQLException.class);
        }
    }

    private void assertTombstoneRejectsRawOrMalformedObjectReference(Connection connection) throws SQLException {
        String sql = """
                INSERT INTO data_tombstone (
                    external_id,
                    subject_hash,
                    object_type,
                    object_ref_hash,
                    purged_at,
                    reason_code
                ) VALUES (
                    '01J00000000000000000000033',
                    REPEAT('a', 64),
                    'asset',
                    'raw-object-id',
                    CURRENT_TIMESTAMP(3),
                    'USER_REQUEST'
                )
                """;

        try (Statement statement = connection.createStatement()) {
            assertThatThrownBy(() -> statement.executeUpdate(sql))
                    .isInstanceOf(SQLException.class);
        }
    }

    private void assertKnowledgeBaseBoundaries(Connection connection, long userId) throws SQLException {
        long ownedAssetId = readId(connection, "asset", "01J00000000000000000000011");
        long otherUserId = insertKnowledgeBaseBoundaryUser(connection);
        long otherUserAssetId = insertKnowledgeBaseBoundaryAsset(connection, otherUserId);
        long knowledgeBaseId = insertKnowledgeBase(connection, userId);

        assertDuplicateLiveNormalizedNameIsRejected(connection, userId);
        insertKnowledgeBaseAsset(connection, knowledgeBaseId, ownedAssetId, userId);
        assertDuplicateKnowledgeBaseMembershipIsRejected(connection, knowledgeBaseId, ownedAssetId, userId);
        assertCrossUserKnowledgeBaseMembershipIsRejected(
                connection,
                knowledgeBaseId,
                otherUserAssetId,
                userId
        );
        trashKnowledgeBase(connection, knowledgeBaseId);
        assertThat(countRows(
                connection,
                "knowledge_base_asset",
                "knowledge_base_id = " + knowledgeBaseId
        )).isEqualTo(1);
        insertReplacementKnowledgeBaseWithReleasedName(connection, userId);
        assertRestoreNameConflictIsRejected(connection, knowledgeBaseId);

        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    DELETE FROM knowledge_base_asset
                    WHERE knowledge_base_id = %d AND asset_id = %d
                    """.formatted(knowledgeBaseId, ownedAssetId));
        }
        assertThat(countRows(connection, "asset", "id = " + ownedAssetId)).isEqualTo(1);
    }

    private long insertKnowledgeBaseBoundaryUser(Connection connection) throws SQLException {
        String sql = """
                INSERT INTO app_user (
                    external_id,
                    normalized_email,
                    email_display,
                    status,
                    age_gate_acknowledged_at,
                    email_verified_at
                ) VALUES (
                    '01J00000000000000000000034',
                    'knowledge-owner-two@example.test',
                    'knowledge-owner-two@example.test',
                    'ACTIVE',
                    CURRENT_TIMESTAMP(3),
                    CURRENT_TIMESTAMP(3)
                )
                """;
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
        return readId(connection, "app_user", "01J00000000000000000000034");
    }

    private long insertKnowledgeBaseBoundaryAsset(Connection connection, long userId) throws SQLException {
        String sql = """
                INSERT INTO asset (
                    external_id,
                    user_id,
                    name,
                    asset_type,
                    source_type,
                    status
                ) VALUES (
                    '01J00000000000000000000035',
                    %d,
                    'Other user notes',
                    'TEXT',
                    'USER_TEXT',
                    'ACTIVE'
                )
                """.formatted(userId);
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
        return readId(connection, "asset", "01J00000000000000000000035");
    }

    private long insertKnowledgeBase(Connection connection, long userId) throws SQLException {
        String sql = """
                INSERT INTO knowledge_base (
                    external_id,
                    user_id,
                    name,
                    normalized_name,
                    description,
                    status
                ) VALUES (
                    '01J00000000000000000000036',
                    %d,
                    'Calculus',
                    'calculus',
                    'Course notes and practice material',
                    'ACTIVE'
                )
                """.formatted(userId);
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
        return readId(connection, "knowledge_base", "01J00000000000000000000036");
    }

    private void assertDuplicateLiveNormalizedNameIsRejected(Connection connection, long userId) throws SQLException {
        String sql = """
                INSERT INTO knowledge_base (
                    external_id,
                    user_id,
                    name,
                    normalized_name,
                    status
                ) VALUES (
                    '01J00000000000000000000037',
                    %d,
                    'CALCULUS',
                    'calculus',
                    'ACTIVE'
                )
                """.formatted(userId);
        try (Statement statement = connection.createStatement()) {
            assertThatThrownBy(() -> statement.executeUpdate(sql))
                    .isInstanceOf(SQLException.class);
        }
    }

    private void insertKnowledgeBaseAsset(
            Connection connection,
            long knowledgeBaseId,
            long assetId,
            long userId
    ) throws SQLException {
        String sql = """
                INSERT INTO knowledge_base_asset (
                    knowledge_base_id,
                    asset_id,
                    added_by_user_id,
                    sort_order
                ) VALUES (%d, %d, %d, 0)
                """.formatted(knowledgeBaseId, assetId, userId);
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private void assertDuplicateKnowledgeBaseMembershipIsRejected(
            Connection connection,
            long knowledgeBaseId,
            long assetId,
            long userId
    ) throws SQLException {
        String sql = """
                INSERT INTO knowledge_base_asset (
                    knowledge_base_id,
                    asset_id,
                    added_by_user_id,
                    sort_order
                ) VALUES (%d, %d, %d, 1)
                """.formatted(knowledgeBaseId, assetId, userId);
        try (Statement statement = connection.createStatement()) {
            assertThatThrownBy(() -> statement.executeUpdate(sql))
                    .isInstanceOf(SQLException.class);
        }
    }

    private void assertCrossUserKnowledgeBaseMembershipIsRejected(
            Connection connection,
            long knowledgeBaseId,
            long assetId,
            long knowledgeBaseOwnerId
    ) throws SQLException {
        String sql = """
                INSERT INTO knowledge_base_asset (
                    knowledge_base_id,
                    asset_id,
                    added_by_user_id,
                    sort_order
                ) VALUES (%d, %d, %d, 0)
                """.formatted(knowledgeBaseId, assetId, knowledgeBaseOwnerId);
        try (Statement statement = connection.createStatement()) {
            assertThatThrownBy(() -> statement.executeUpdate(sql))
                    .isInstanceOf(SQLException.class);
        }
    }

    private void trashKnowledgeBase(Connection connection, long knowledgeBaseId) throws SQLException {
        String sql = """
                UPDATE knowledge_base
                SET status = 'TRASHED',
                    previous_status = 'ACTIVE',
                    trash_started_at = CURRENT_TIMESTAMP(3),
                    deleted_at = CURRENT_TIMESTAMP(3),
                    row_version = row_version + 1
                WHERE id = %d
                """.formatted(knowledgeBaseId);
        try (Statement statement = connection.createStatement()) {
            assertThat(statement.executeUpdate(sql)).isEqualTo(1);
        }
    }

    private void insertReplacementKnowledgeBaseWithReleasedName(Connection connection, long userId)
            throws SQLException {
        String sql = """
                INSERT INTO knowledge_base (
                    external_id,
                    user_id,
                    name,
                    normalized_name,
                    status
                ) VALUES (
                    '01J00000000000000000000038',
                    %d,
                    'Calculus',
                    'calculus',
                    'ACTIVE'
                )
                """.formatted(userId);
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private void assertRestoreNameConflictIsRejected(Connection connection, long knowledgeBaseId)
            throws SQLException {
        String sql = """
                UPDATE knowledge_base
                SET status = previous_status,
                    previous_status = NULL,
                    trash_started_at = NULL,
                    deleted_at = NULL,
                    row_version = row_version + 1
                WHERE id = %d
                """.formatted(knowledgeBaseId);
        try (Statement statement = connection.createStatement()) {
            assertThatThrownBy(() -> statement.executeUpdate(sql))
                    .isInstanceOf(SQLException.class);
        }
    }

    private void assertConversationAndAiRuntimeBoundaries(Connection connection, long userId)
            throws SQLException {
        long knowledgeBaseId = readId(connection, "knowledge_base", "01J00000000000000000000038");
        long assetVersionId = readId(connection, "asset_version", "01J00000000000000000000012");
        long documentChunkId = readId(connection, "document_chunk", "01J00000000000000000000019");
        long capabilityId = insertCapabilityDefinition(connection);
        long policyVersionId = insertConversationModelPolicy(connection);
        long promptVersionId = insertConversationPromptVersion(connection);
        long conversationId = insertGeneralConversation(connection, userId, knowledgeBaseId);
        long branchId = insertConversationBranch(connection, conversationId, userId);
        long userMessageId = insertUserMessage(connection, conversationId, branchId);
        long responseGroupId = insertResponseGroup(connection, conversationId, branchId, userMessageId);
        long asyncJobId = insertAiRunJob(connection, userId);
        long aiRunId = insertAiRun(
                connection,
                userId,
                asyncJobId,
                conversationId,
                branchId,
                userMessageId,
                capabilityId,
                policyVersionId,
                promptVersionId
        );
        long contextSnapshotId = insertAiContextSnapshot(connection, aiRunId);
        long assistantMessageId = insertAssistantMessage(
                connection,
                conversationId,
                branchId,
                userMessageId,
                responseGroupId,
                aiRunId
        );

        connectConversationRuntimePointers(
                connection,
                conversationId,
                branchId,
                responseGroupId,
                aiRunId,
                assistantMessageId
        );
        insertMessagePart(connection, assistantMessageId);
        insertMessageAttachment(connection, userMessageId, assetVersionId);
        insertMessageCitation(connection, assistantMessageId, documentChunkId);
        long retrievalRunId = insertRetrievalRun(connection, aiRunId);
        insertRetrievalResult(connection, retrievalRunId, documentChunkId);
        insertSuccessfulToolCall(connection, aiRunId);
        insertProposedPendingAction(connection, userId, aiRunId);

        assertInvalidGeneralConversationBindingIsRejected(connection, userId);
        assertCrossConversationMessageIsRejected(connection, userId);
        assertAssistantWithoutGenerationMetadataIsRejected(
                connection,
                conversationId,
                branchId,
                userMessageId
        );
        assertMessagePartWithTwoPayloadsIsRejected(connection, assistantMessageId);
        assertCitationWithMultipleTargetsIsRejected(connection, assistantMessageId, documentChunkId);
        assertDuplicateAiRunJobIsRejected(
                connection,
                userId,
                asyncJobId,
                conversationId,
                branchId,
                userMessageId,
                capabilityId,
                policyVersionId,
                promptVersionId
        );
        assertNonObjectContextManifestIsRejected(connection, contextSnapshotId);
        assertInvalidToolLifecycleIsRejected(connection, aiRunId);
        assertInvalidPendingActionLifecycleIsRejected(connection, userId, aiRunId);
        assertCrossUserPendingActionIsRejected(connection, aiRunId);

        assertThat(countRows(connection, "message", "conversation_id = " + conversationId)).isEqualTo(2);
        assertThat(countRows(connection, "ai_context_snapshot", "ai_run_id = " + aiRunId)).isEqualTo(1);
        assertThat(countRows(connection, "retrieval_result", "retrieval_run_id = " + retrievalRunId)).isEqualTo(1);
        assertThat(countRows(connection, "pending_action", "ai_run_id = " + aiRunId)).isEqualTo(1);
    }

    private long insertCapabilityDefinition(Connection connection) throws SQLException {
        executeUpdate(connection, """
                INSERT INTO capability_definition (
                    external_id,
                    capability_key,
                    entry_mode,
                    status,
                    title_key,
                    description_key,
                    suggested_prompt_key,
                    required_permission,
                    quota_policy_key,
                    audience_rule_json,
                    sort_order
                ) VALUES (
                    '01J00000000000000000000040',
                    'chat.test',
                    'CHAT',
                    'HIDDEN',
                    'capability.chat.test.title',
                    'capability.chat.test.description',
                    'capability.chat.test.prompt',
                    'conversation.create',
                    'beta.default',
                    JSON_OBJECT('cohort', 'migration-test'),
                    10
                )
                """);
        return readId(connection, "capability_definition", "01J00000000000000000000040");
    }

    private long insertConversationModelPolicy(Connection connection) throws SQLException {
        executeUpdate(connection, """
                INSERT INTO model_policy_version (
                    external_id,
                    policy_key,
                    version_no,
                    status,
                    description,
                    effective_at,
                    fallback_mode,
                    content_hash
                ) VALUES (
                    '01J00000000000000000000041',
                    'general-chat-test',
                    1,
                    'ACTIVE',
                    'Migration boundary policy',
                    CURRENT_TIMESTAMP(3),
                    'ALLOW_DEGRADED',
                    REPEAT('a', 64)
                )
                """);
        return readId(connection, "model_policy_version", "01J00000000000000000000041");
    }

    private long insertConversationPromptVersion(Connection connection) throws SQLException {
        executeUpdate(connection, """
                INSERT INTO prompt_template (
                    external_id,
                    prompt_key,
                    display_name,
                    purpose,
                    status
                ) VALUES (
                    '01J00000000000000000000042',
                    'general-chat-test',
                    'General chat migration test',
                    'GENERAL_CHAT',
                    'ACTIVE'
                )
                """);
        long promptTemplateId = readId(connection, "prompt_template", "01J00000000000000000000042");
        executeUpdate(connection, """
                INSERT INTO prompt_version (
                    external_id,
                    prompt_template_id,
                    version_no,
                    system_template,
                    developer_template,
                    input_schema_json,
                    output_schema_json,
                    template_hash,
                    status,
                    published_at
                ) VALUES (
                    '01J00000000000000000000043',
                    %d,
                    1,
                    'Answer with cited evidence.',
                    NULL,
                    JSON_OBJECT('type', 'object'),
                    JSON_OBJECT('type', 'object'),
                    REPEAT('b', 64),
                    'PUBLISHED',
                    CURRENT_TIMESTAMP(3)
                )
                """.formatted(promptTemplateId));
        return readId(connection, "prompt_version", "01J00000000000000000000043");
    }

    private long insertGeneralConversation(Connection connection, long userId, long knowledgeBaseId)
            throws SQLException {
        executeUpdate(connection, """
                INSERT INTO conversation (
                    external_id,
                    user_id,
                    conversation_type,
                    knowledge_base_id,
                    title,
                    status
                ) VALUES (
                    '01J00000000000000000000044',
                    %d,
                    'GENERAL',
                    %d,
                    'Explain calculus limits',
                    'ACTIVE'
                )
                """.formatted(userId, knowledgeBaseId));
        return readId(connection, "conversation", "01J00000000000000000000044");
    }

    private long insertConversationBranch(Connection connection, long conversationId, long userId)
            throws SQLException {
        executeUpdate(connection, """
                INSERT INTO conversation_branch (
                    external_id,
                    conversation_id,
                    status,
                    created_by_user_id
                ) VALUES (
                    '01J00000000000000000000045',
                    %d,
                    'ACTIVE',
                    %d
                )
                """.formatted(conversationId, userId));
        return readId(connection, "conversation_branch", "01J00000000000000000000045");
    }

    private long insertUserMessage(Connection connection, long conversationId, long branchId)
            throws SQLException {
        executeUpdate(connection, """
                INSERT INTO message (
                    external_id,
                    conversation_id,
                    branch_id,
                    role,
                    status,
                    sequence_no,
                    plain_text,
                    generated_by_ai,
                    finalized_at
                ) VALUES (
                    '01J00000000000000000000046',
                    %d,
                    %d,
                    'USER',
                    'FINALIZED',
                    1,
                    'What is a limit?',
                    FALSE,
                    CURRENT_TIMESTAMP(3)
                )
                """.formatted(conversationId, branchId));
        return readId(connection, "message", "01J00000000000000000000046");
    }

    private long insertResponseGroup(
            Connection connection,
            long conversationId,
            long branchId,
            long userMessageId
    ) throws SQLException {
        executeUpdate(connection, """
                INSERT INTO assistant_response_group (
                    external_id,
                    conversation_id,
                    branch_id,
                    user_message_id
                ) VALUES (
                    '01J00000000000000000000047',
                    %d,
                    %d,
                    %d
                )
                """.formatted(conversationId, branchId, userMessageId));
        return readId(connection, "assistant_response_group", "01J00000000000000000000047");
    }

    private long insertAiRunJob(Connection connection, long userId) throws SQLException {
        executeUpdate(connection, """
                INSERT INTO async_job (
                    external_id,
                    user_id,
                    job_type,
                    aggregate_type,
                    aggregate_external_id,
                    status,
                    started_at,
                    heartbeat_at,
                    attempt_count,
                    max_attempts
                ) VALUES (
                    '01J00000000000000000000048',
                    %d,
                    'AI_ORCHESTRATION',
                    'CONVERSATION',
                    '01J00000000000000000000044',
                    'RUNNING',
                    CURRENT_TIMESTAMP(3),
                    CURRENT_TIMESTAMP(3),
                    1,
                    3
                )
                """.formatted(userId));
        return readId(connection, "async_job", "01J00000000000000000000048");
    }

    private long insertAiRun(
            Connection connection,
            long userId,
            long asyncJobId,
            long conversationId,
            long branchId,
            long userMessageId,
            long capabilityId,
            long policyVersionId,
            long promptVersionId
    ) throws SQLException {
        executeUpdate(connection, """
                INSERT INTO ai_run (
                    external_id,
                    user_id,
                    async_job_id,
                    conversation_id,
                    branch_id,
                    request_message_id,
                    capability_id,
                    model_policy_version_id,
                    prompt_version_id,
                    mode,
                    intent_key,
                    side_effect_level,
                    started_at
                ) VALUES (
                    '01J00000000000000000000049',
                    %d,
                    %d,
                    %d,
                    %d,
                    %d,
                    %d,
                    %d,
                    %d,
                    'GENERAL_CHAT',
                    'question.answer',
                    'NONE',
                    CURRENT_TIMESTAMP(3)
                )
                """.formatted(
                userId,
                asyncJobId,
                conversationId,
                branchId,
                userMessageId,
                capabilityId,
                policyVersionId,
                promptVersionId
        ));
        return readId(connection, "ai_run", "01J00000000000000000000049");
    }

    private long insertAiContextSnapshot(Connection connection, long aiRunId) throws SQLException {
        executeUpdate(connection, """
                INSERT INTO ai_context_snapshot (
                    external_id,
                    ai_run_id,
                    context_schema_version,
                    context_manifest_json,
                    context_hash
                ) VALUES (
                    '01J00000000000000000000050',
                    %d,
                    1,
                    JSON_OBJECT('conversationWindow', JSON_ARRAY('01J00000000000000000000046')),
                    REPEAT('c', 64)
                )
                """.formatted(aiRunId));
        return readId(connection, "ai_context_snapshot", "01J00000000000000000000050");
    }

    private long insertAssistantMessage(
            Connection connection,
            long conversationId,
            long branchId,
            long userMessageId,
            long responseGroupId,
            long aiRunId
    ) throws SQLException {
        executeUpdate(connection, """
                INSERT INTO message (
                    external_id,
                    conversation_id,
                    branch_id,
                    parent_message_id,
                    role,
                    status,
                    sequence_no,
                    plain_text,
                    response_group_id,
                    generated_by_ai,
                    ai_run_id,
                    generation_label,
                    finalized_at
                ) VALUES (
                    '01J00000000000000000000051',
                    %d,
                    %d,
                    %d,
                    'ASSISTANT',
                    'FINALIZED',
                    2,
                    'A limit describes the value a function approaches.',
                    %d,
                    TRUE,
                    %d,
                    'AI generated',
                    CURRENT_TIMESTAMP(3)
                )
                """.formatted(conversationId, branchId, userMessageId, responseGroupId, aiRunId));
        return readId(connection, "message", "01J00000000000000000000051");
    }

    private void connectConversationRuntimePointers(
            Connection connection,
            long conversationId,
            long branchId,
            long responseGroupId,
            long aiRunId,
            long assistantMessageId
    ) throws SQLException {
        executeUpdate(connection, """
                UPDATE conversation
                SET active_branch_id = %d,
                    last_message_at = CURRENT_TIMESTAMP(3),
                    row_version = row_version + 1
                WHERE id = %d
                """.formatted(branchId, conversationId));
        executeUpdate(connection, """
                UPDATE conversation_branch
                SET active_run_id = %d,
                    row_version = row_version + 1
                WHERE id = %d
                """.formatted(aiRunId, branchId));
        executeUpdate(connection, """
                UPDATE assistant_response_group
                SET selected_message_id = %d,
                    row_version = row_version + 1
                WHERE id = %d
                """.formatted(assistantMessageId, responseGroupId));
        executeUpdate(connection, """
                UPDATE ai_run
                SET response_message_id = %d,
                    completed_at = CURRENT_TIMESTAMP(3),
                    row_version = row_version + 1
                WHERE id = %d
                """.formatted(assistantMessageId, aiRunId));
    }

    private void insertMessagePart(Connection connection, long assistantMessageId) throws SQLException {
        executeUpdate(connection, """
                INSERT INTO message_part (
                    message_id,
                    part_no,
                    part_type,
                    text_content,
                    content_sha256
                ) VALUES (
                    %d,
                    1,
                    'MARKDOWN',
                    'A limit describes the value a function approaches.',
                    REPEAT('d', 64)
                )
                """.formatted(assistantMessageId));
    }

    private void insertMessageAttachment(Connection connection, long messageId, long assetVersionId)
            throws SQLException {
        executeUpdate(connection, """
                INSERT INTO message_attachment (
                    message_id,
                    asset_version_id,
                    attachment_role,
                    display_name
                ) VALUES (
                    %d,
                    %d,
                    'CONTEXT',
                    'calculus-notes.pdf'
                )
                """.formatted(messageId, assetVersionId));
    }

    private void insertMessageCitation(Connection connection, long messageId, long documentChunkId)
            throws SQLException {
        executeUpdate(connection, """
                INSERT INTO message_citation (
                    message_id,
                    citation_no,
                    citation_type,
                    document_chunk_id,
                    quoted_text,
                    locator_label,
                    support_score
                ) VALUES (
                    %d,
                    1,
                    'DOCUMENT_CHUNK',
                    %d,
                    'A short valid chunk',
                    'Page 1',
                    0.950000
                )
                """.formatted(messageId, documentChunkId));
    }

    private long insertRetrievalRun(Connection connection, long aiRunId) throws SQLException {
        executeUpdate(connection, """
                INSERT INTO retrieval_run (
                    external_id,
                    ai_run_id,
                    query_text,
                    query_hash,
                    retrieval_mode,
                    filters_json,
                    top_k,
                    started_at,
                    completed_at,
                    status
                ) VALUES (
                    '01J00000000000000000000052',
                    %d,
                    'calculus limit definition',
                    REPEAT('e', 64),
                    'HYBRID',
                    JSON_OBJECT('knowledgeBaseId', '01J00000000000000000000038'),
                    10,
                    CURRENT_TIMESTAMP(3),
                    CURRENT_TIMESTAMP(3),
                    'SUCCEEDED'
                )
                """.formatted(aiRunId));
        return readId(connection, "retrieval_run", "01J00000000000000000000052");
    }

    private void insertRetrievalResult(Connection connection, long retrievalRunId, long documentChunkId)
            throws SQLException {
        executeUpdate(connection, """
                INSERT INTO retrieval_result (
                    retrieval_run_id,
                    rank_no,
                    document_chunk_id,
                    semantic_score,
                    keyword_score,
                    rerank_score,
                    selected_for_context,
                    reason_code
                ) VALUES (
                    %d,
                    1,
                    %d,
                    0.900000,
                    0.800000,
                    0.950000,
                    TRUE,
                    'TOP_RERANKED'
                )
                """.formatted(retrievalRunId, documentChunkId));
    }

    private void insertSuccessfulToolCall(Connection connection, long aiRunId) throws SQLException {
        executeUpdate(connection, """
                INSERT INTO ai_tool_call (
                    external_id,
                    ai_run_id,
                    call_no,
                    tool_key,
                    tool_version,
                    side_effect_level,
                    status,
                    arguments_json,
                    arguments_hash,
                    result_summary_json,
                    started_at,
                    completed_at
                ) VALUES (
                    '01J00000000000000000000053',
                    %d,
                    1,
                    'knowledge.search',
                    '1.0.0',
                    'NONE',
                    'SUCCEEDED',
                    JSON_OBJECT('queryHash', REPEAT('e', 64)),
                    REPEAT('f', 64),
                    JSON_OBJECT('selectedChunks', 1),
                    CURRENT_TIMESTAMP(3),
                    CURRENT_TIMESTAMP(3)
                )
                """.formatted(aiRunId));
    }

    private void insertProposedPendingAction(Connection connection, long userId, long aiRunId)
            throws SQLException {
        executeUpdate(connection, """
                INSERT INTO pending_action (
                    external_id,
                    user_id,
                    ai_run_id,
                    action_type,
                    action_schema_version,
                    payload_json,
                    payload_hash,
                    base_aggregate_type,
                    base_aggregate_external_id,
                    base_row_version,
                    status,
                    quota_estimate,
                    expires_at
                ) VALUES (
                    '01J00000000000000000000054',
                    %d,
                    %d,
                    'CREATE_MIND_MAP',
                    1,
                    JSON_OBJECT('topic', 'calculus limits'),
                    REPEAT('1', 64),
                    'CONVERSATION',
                    '01J00000000000000000000044',
                    1,
                    'PROPOSED',
                    1.25000000,
                    DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL 10 MINUTE)
                )
                """.formatted(userId, aiRunId));
    }

    private void assertInvalidGeneralConversationBindingIsRejected(Connection connection, long userId)
            throws SQLException {
        assertSqlRejected(connection, """
                INSERT INTO conversation (
                    external_id,
                    user_id,
                    conversation_type,
                    learning_project_id,
                    title,
                    status
                ) VALUES (
                    '01J00000000000000000000055',
                    %d,
                    'GENERAL',
                    999,
                    'Invalid general binding',
                    'ACTIVE'
                )
                """.formatted(userId));
    }

    private void assertCrossConversationMessageIsRejected(Connection connection, long userId)
            throws SQLException {
        executeUpdate(connection, """
                INSERT INTO conversation (
                    external_id,
                    user_id,
                    conversation_type,
                    title,
                    status
                ) VALUES (
                    '01J00000000000000000000056',
                    %d,
                    'GENERAL',
                    'Second conversation',
                    'ACTIVE'
                )
                """.formatted(userId));
        long secondConversationId = readId(connection, "conversation", "01J00000000000000000000056");
        executeUpdate(connection, """
                INSERT INTO conversation_branch (
                    external_id,
                    conversation_id,
                    status,
                    created_by_user_id
                ) VALUES (
                    '01J00000000000000000000057',
                    %d,
                    'ACTIVE',
                    %d
                )
                """.formatted(secondConversationId, userId));
        long secondBranchId = readId(connection, "conversation_branch", "01J00000000000000000000057");
        long firstConversationId = readId(connection, "conversation", "01J00000000000000000000044");
        assertSqlRejected(connection, """
                INSERT INTO message (
                    external_id,
                    conversation_id,
                    branch_id,
                    role,
                    status,
                    sequence_no,
                    plain_text,
                    generated_by_ai,
                    finalized_at
                ) VALUES (
                    '01J00000000000000000000058',
                    %d,
                    %d,
                    'USER',
                    'FINALIZED',
                    1,
                    'Cross-conversation message',
                    FALSE,
                    CURRENT_TIMESTAMP(3)
                )
                """.formatted(firstConversationId, secondBranchId));

        executeUpdate(connection, """
                INSERT INTO message (
                    external_id,
                    conversation_id,
                    branch_id,
                    role,
                    status,
                    sequence_no,
                    plain_text,
                    generated_by_ai,
                    finalized_at
                ) VALUES (
                    '01J00000000000000000000064',
                    %d,
                    %d,
                    'USER',
                    'FINALIZED',
                    1,
                    'Root branch message',
                    FALSE,
                    CURRENT_TIMESTAMP(3)
                )
                """.formatted(secondConversationId, secondBranchId));
        long forkMessageId = readId(connection, "message", "01J00000000000000000000064");
        executeUpdate(connection, """
                INSERT INTO conversation_branch (
                    external_id,
                    conversation_id,
                    parent_branch_id,
                    forked_from_message_id,
                    status,
                    created_by_user_id
                ) VALUES (
                    '01J00000000000000000000065',
                    %d,
                    %d,
                    %d,
                    'ACTIVE',
                    %d
                )
                """.formatted(secondConversationId, secondBranchId, forkMessageId, userId));
        long childBranchId = readId(connection, "conversation_branch", "01J00000000000000000000065");
        executeUpdate(connection, """
                INSERT INTO message (
                    external_id,
                    conversation_id,
                    branch_id,
                    role,
                    status,
                    sequence_no,
                    plain_text,
                    generated_by_ai,
                    finalized_at
                ) VALUES (
                    '01J00000000000000000000066',
                    %d,
                    %d,
                    'USER',
                    'FINALIZED',
                    1,
                    'Child branch first message',
                    FALSE,
                    CURRENT_TIMESTAMP(3)
                )
                """.formatted(secondConversationId, childBranchId));
        long childMessageId = readId(connection, "message", "01J00000000000000000000066");
        executeUpdate(connection, """
                INSERT INTO message (
                    external_id,
                    conversation_id,
                    branch_id,
                    parent_message_id,
                    role,
                    status,
                    sequence_no,
                    plain_text,
                    generated_by_ai,
                    finalized_at
                ) VALUES (
                    '01J00000000000000000000067',
                    %d,
                    %d,
                    %d,
                    'USER',
                    'FINALIZED',
                    2,
                    'Child branch reply',
                    FALSE,
                    CURRENT_TIMESTAMP(3)
                )
                """.formatted(secondConversationId, childBranchId, childMessageId));

        executeUpdate(connection, "DELETE FROM conversation WHERE id = " + secondConversationId);
        assertThat(countRows(connection, "conversation_branch", "conversation_id = " + secondConversationId))
                .isZero();
        assertThat(countRows(connection, "message", "conversation_id = " + secondConversationId)).isZero();
    }

    private void assertAssistantWithoutGenerationMetadataIsRejected(
            Connection connection,
            long conversationId,
            long branchId,
            long parentMessageId
    ) throws SQLException {
        assertSqlRejected(connection, """
                INSERT INTO message (
                    external_id,
                    conversation_id,
                    branch_id,
                    parent_message_id,
                    role,
                    status,
                    sequence_no,
                    plain_text,
                    generated_by_ai,
                    finalized_at
                ) VALUES (
                    '01J00000000000000000000059',
                    %d,
                    %d,
                    %d,
                    'ASSISTANT',
                    'FINALIZED',
                    3,
                    'Unlabelled AI answer',
                    FALSE,
                    CURRENT_TIMESTAMP(3)
                )
                """.formatted(conversationId, branchId, parentMessageId));
    }

    private void assertMessagePartWithTwoPayloadsIsRejected(Connection connection, long messageId)
            throws SQLException {
        assertSqlRejected(connection, """
                INSERT INTO message_part (
                    message_id,
                    part_no,
                    part_type,
                    text_content,
                    display_json,
                    content_sha256
                ) VALUES (
                    %d,
                    2,
                    'TABLE',
                    'must not coexist',
                    JSON_OBJECT('columns', JSON_ARRAY('name')),
                    REPEAT('2', 64)
                )
                """.formatted(messageId));
    }

    private void assertCitationWithMultipleTargetsIsRejected(
            Connection connection,
            long messageId,
            long documentChunkId
    ) throws SQLException {
        assertSqlRejected(connection, """
                INSERT INTO message_citation (
                    message_id,
                    citation_no,
                    citation_type,
                    document_chunk_id,
                    question_version_id
                ) VALUES (
                    %d,
                    2,
                    'DOCUMENT_CHUNK',
                    %d,
                    1
                )
                """.formatted(messageId, documentChunkId));
    }

    private void assertDuplicateAiRunJobIsRejected(
            Connection connection,
            long userId,
            long asyncJobId,
            long conversationId,
            long branchId,
            long userMessageId,
            long capabilityId,
            long policyVersionId,
            long promptVersionId
    ) throws SQLException {
        assertSqlRejected(connection, """
                INSERT INTO ai_run (
                    external_id,
                    user_id,
                    async_job_id,
                    conversation_id,
                    branch_id,
                    request_message_id,
                    capability_id,
                    model_policy_version_id,
                    prompt_version_id,
                    mode,
                    intent_key,
                    side_effect_level
                ) VALUES (
                    '01J00000000000000000000060',
                    %d,
                    %d,
                    %d,
                    %d,
                    %d,
                    %d,
                    %d,
                    %d,
                    'GENERAL_CHAT',
                    'question.answer',
                    'NONE'
                )
                """.formatted(
                userId,
                asyncJobId,
                conversationId,
                branchId,
                userMessageId,
                capabilityId,
                policyVersionId,
                promptVersionId
        ));
    }

    private void assertNonObjectContextManifestIsRejected(Connection connection, long contextSnapshotId)
            throws SQLException {
        assertSqlRejected(connection, """
                UPDATE ai_context_snapshot
                SET context_manifest_json = JSON_ARRAY('invalid')
                WHERE id = %d
                """.formatted(contextSnapshotId));
    }

    private void assertInvalidToolLifecycleIsRejected(Connection connection, long aiRunId)
            throws SQLException {
        assertSqlRejected(connection, """
                INSERT INTO ai_tool_call (
                    external_id,
                    ai_run_id,
                    call_no,
                    tool_key,
                    tool_version,
                    side_effect_level,
                    status,
                    arguments_json,
                    arguments_hash
                ) VALUES (
                    '01J00000000000000000000061',
                    %d,
                    2,
                    'knowledge.search',
                    '1.0.0',
                    'NONE',
                    'SUCCEEDED',
                    JSON_OBJECT(),
                    REPEAT('3', 64)
                )
                """.formatted(aiRunId));
    }

    private void assertInvalidPendingActionLifecycleIsRejected(
            Connection connection,
            long userId,
            long aiRunId
    ) throws SQLException {
        assertSqlRejected(connection, """
                INSERT INTO pending_action (
                    external_id,
                    user_id,
                    ai_run_id,
                    action_type,
                    action_schema_version,
                    payload_json,
                    payload_hash,
                    base_aggregate_type,
                    base_aggregate_external_id,
                    base_row_version,
                    status,
                    expires_at
                ) VALUES (
                    '01J00000000000000000000062',
                    %d,
                    %d,
                    'CREATE_MIND_MAP',
                    1,
                    JSON_OBJECT('topic', 'invalid lifecycle'),
                    REPEAT('4', 64),
                    'CONVERSATION',
                    '01J00000000000000000000044',
                    1,
                    'SUCCEEDED',
                    DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL 10 MINUTE)
                )
                """.formatted(userId, aiRunId));
    }

    private void assertCrossUserPendingActionIsRejected(Connection connection, long aiRunId)
            throws SQLException {
        long otherUserId = readId(connection, "app_user", "01J00000000000000000000034");
        assertSqlRejected(connection, """
                INSERT INTO pending_action (
                    external_id,
                    user_id,
                    ai_run_id,
                    action_type,
                    action_schema_version,
                    payload_json,
                    payload_hash,
                    base_aggregate_type,
                    base_aggregate_external_id,
                    base_row_version,
                    status,
                    expires_at
                ) VALUES (
                    '01J00000000000000000000063',
                    %d,
                    %d,
                    'CREATE_MIND_MAP',
                    1,
                    JSON_OBJECT('topic', 'cross user'),
                    REPEAT('5', 64),
                    'CONVERSATION',
                    '01J00000000000000000000044',
                    1,
                    'PROPOSED',
                    DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL 10 MINUTE)
                )
                """.formatted(otherUserId, aiRunId));
    }

    private void executeUpdate(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private void assertSqlRejected(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            assertThatThrownBy(() -> statement.executeUpdate(sql))
                    .isInstanceOf(SQLException.class);
        }
    }

    private int countRows(Connection connection, String tableName, String predicate) throws SQLException {
        String sql = "SELECT COUNT(*) FROM %s WHERE %s".formatted(tableName, predicate);
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getInt(1);
        }
    }

    private long readId(Connection connection, String tableName, String externalId) throws SQLException {
        String sql = "SELECT id FROM %s WHERE external_id = '%s'".formatted(tableName, externalId);
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getLong(1);
        }
    }

    @SafeVarargs
    private static Set<String> schemaTables(Set<String>... migrationTableSets) {
        Set<String> tables = new LinkedHashSet<>();
        for (Set<String> migrationTables : migrationTableSets) {
            tables.addAll(migrationTables);
        }
        tables.add("flyway_schema_history");
        return Set.copyOf(tables);
    }
}

class MigrationContractTest {

    @Test
    void foundationMigrationIsSchemaNeutralAndCreatesOnlyItsFrozenTables() throws IOException {
        assertMigrationContract(
                "db/migration/V001__platform_foundation.sql",
                PlatformFoundationMigrationTest.FOUNDATION_TABLES
        );
    }

    @Test
    void modelRegistryMigrationIsSchemaNeutralCreatesOnlyItsFrozenTablesAndStoresNoCredential() throws IOException {
        String migration = assertMigrationContract(
                "db/migration/V002__model_registry_prompt_and_policy.sql",
                PlatformFoundationMigrationTest.MODEL_REGISTRY_TABLES
        );

        assertThat(migration).doesNotContainPattern(
                "(?i)\\b(?:api_key|secret_value|credential_value|credential_ciphertext)\\b"
        );
        assertThat(migration).contains("credential_secret_ref");
    }

    @Test
    void identityMigrationIsSchemaNeutralCreatesOnlyItsFrozenTablesAndStoresOnlySecretDerivatives()
            throws IOException {
        String migration = assertMigrationContract(
                "db/migration/V003__identity_and_auth.sql",
                PlatformFoundationMigrationTest.IDENTITY_TABLES
        );

        assertThat(migration).doesNotContainPattern("(?i)\\b(?:INSERT|REPLACE)\\b");
        assertThat(migration).doesNotContainPattern(
                "(?im)^\\s*(?:password|verification_code|session_token|reset_token|recovery_code|csrf_secret)\\s+"
        );
        assertThat(migration).contains(
                "password_hash",
                "code_hash",
                "verification_proof_hash",
                "token_hash",
                "csrf_secret_hash",
                "secret_ciphertext"
        );
    }

    @Test
    void storageAssetMigrationIsSchemaNeutralCreatesOnlyItsFrozenTablesAndStoresNoObjectOrVectorPayload()
            throws IOException {
        String migration = assertMigrationContract(
                "db/migration/V004__storage_and_assets.sql",
                PlatformFoundationMigrationTest.STORAGE_ASSET_TABLES
        );

        assertThat(migration).doesNotContainPattern("(?i)\\b(?:INSERT|REPLACE)\\b");
        assertThat(migration).doesNotContainPattern("(?im)^\\s*object_key\\s+");
        assertThat(migration).doesNotContainPattern("(?im)^\\s*(?:embedding|vector)\\s+");
        assertThat(migration).doesNotContainPattern("(?im)^CREATE TABLE\\s+knowledge_base\\b");
        assertThat(migration).contains(
                "multipart_upload_ref_ciphertext",
                "object_key_ciphertext",
                "upload_session_id",
                "scanner_key",
                "safe_rejection_code"
        );
    }

    @Test
    void privacyAdminMigrationCreatesOnlyFrozenTablesUsesHashesAndContainsNoSeedData() throws IOException {
        String migration = assertMigrationContract(
                "db/migration/V005__privacy_and_admin_access.sql",
                PlatformFoundationMigrationTest.PRIVACY_ADMIN_TABLES
        );

        assertThat(migration).doesNotContainPattern("(?i)\\b(?:INSERT|REPLACE)\\b");
        assertThat(migration).doesNotContainPattern(
                "(?im)^\\s*(?:subject_email|subject_external_id|raw_subject|object_ref|download_token)\\s+"
        );
        assertThat(migration).doesNotContainPattern(
                "(?im)^CREATE TABLE\\s+(?:knowledge_base|conversation|learning_project)\\b"
        );
        assertThat(migration).contains(
                "subject_hash",
                "object_ref_hash",
                "download_token_hash",
                "COMPLETED_WITH_RETENTION",
                "READ_METADATA",
                "READ_CONTENT"
        );
    }

    @Test
    void knowledgeBaseMigrationCreatesOnlyCollectionAndMembershipWithoutCopyingAssetContent()
            throws IOException {
        String migration = assertMigrationContract(
                "db/migration/V006__knowledge_bases.sql",
                PlatformFoundationMigrationTest.KNOWLEDGE_BASE_TABLES
        );

        assertThat(migration).doesNotContainPattern("(?i)\\b(?:INSERT|REPLACE)\\b");
        assertThat(migration).doesNotContainPattern(
                "(?im)^CREATE TABLE\\s+(?:asset_version|asset_parse_result|document_chunk|embedding_record|conversation|learning_project)\\b"
        );
        assertThat(migration).doesNotContainPattern(
                "(?im)^\\s*(?:text_content|storage_object_id|parse_result_id|embedding|vector)\\s+"
        );
        assertThat(migration).contains(
                "ALTER TABLE asset",
                "uq_asset__id_user_id",
                "REFERENCES knowledge_base (id, user_id) ON DELETE CASCADE",
                "REFERENCES asset (id, user_id) ON DELETE CASCADE",
                "uq_knowledge_base__user_active_name",
                "uq_knowledge_base_asset__base_asset"
        );
    }

    @Test
    void conversationAiMigrationCreatesOnlyRuntimeEvidenceAndContainsNoSeedOrHiddenReasoning()
            throws IOException {
        String migration = assertMigrationContract(
                "db/migration/V007__conversation_and_ai_runs.sql",
                PlatformFoundationMigrationTest.CONVERSATION_AI_TABLES
        );

        assertThat(migration).doesNotContainPattern("(?i)\\b(?:INSERT|REPLACE)\\b");
        assertThat(migration).doesNotContainPattern(
                "(?im)^CREATE TABLE\\s+(?:learning_project|scope_version|question|learning_plan|quota_account)\\b"
        );
        assertThat(migration).doesNotContainPattern(
                "(?im)^\\s*(?:chain_of_thought|hidden_reasoning|raw_prompt|raw_response|provider_response|credential_secret)\\s+"
        );
        assertThat(migration).contains(
                "uq_async_job__id_user_id",
                "ck_conversation__type_binding",
                "uq_message__branch_sequence",
                "ck_message__generation_metadata",
                "ck_message_citation__target",
                "uq_ai_run__async_job_id",
                "ck_ai_context_snapshot__manifest",
                "ck_ai_tool_call__lifecycle",
                "ck_pending_action__lifecycle"
        );
        assertThat(migration).doesNotContain(
                "FOREIGN KEY (learning_project_id)",
                "FOREIGN KEY (active_branch_id)",
                "FOREIGN KEY (forked_from_message_id)",
                "FOREIGN KEY (active_run_id)",
                "FOREIGN KEY (response_group_id)",
                "FOREIGN KEY (selected_message_id)"
        );
    }

    @Test
    void conversationCascadeCorrectionChangesOnlySelfReferencingDeleteActions() throws IOException {
        String migration = readMigration("db/migration/V007_1__conversation_cascade_fix.sql");

        assertThat(migration).doesNotContainPattern("(?im)^CREATE TABLE\\s+");
        assertThat(migration).doesNotContainPattern(
                "(?i)\\b(?:USE|TRUNCATE)\\b|\\bDROP\\s+(?:TABLE|SCHEMA|DATABASE)\\b"
        );
        assertThat(migration).doesNotContainPattern(
                "(?im)^\\s*(?:INSERT|REPLACE|UPDATE|DELETE)\\b"
        );
        assertThat(migration).contains(
                "DROP FOREIGN KEY fk_conversation_branch__parent_conversation",
                "DROP FOREIGN KEY fk_message__parent_branch",
                "DROP FOREIGN KEY fk_message__edited_conversation",
                "REFERENCES conversation_branch (id, conversation_id) ON DELETE CASCADE",
                "REFERENCES message (id, branch_id) ON DELETE CASCADE",
                "REFERENCES message (id, conversation_id) ON DELETE CASCADE"
        );
    }

    private String assertMigrationContract(String path, Set<String> expectedTables) throws IOException {
        String migration = readMigration(path);
        Pattern createTable = Pattern.compile("(?im)^CREATE TABLE\\s+([a-z0-9_]+)\\s*\\(");
        Matcher matcher = createTable.matcher(migration);
        Set<String> actualTables = new LinkedHashSet<>();
        while (matcher.find()) {
            actualTables.add(matcher.group(1));
        }

        assertThat(actualTables).containsExactlyInAnyOrderElementsOf(expectedTables);
        assertThat(migration).doesNotContainPattern("(?i)\\b(?:USE|DROP|TRUNCATE)\\b");
        assertThat(migration).doesNotContain("`LLM`", "`llm`");
        return migration;
    }

    private String readMigration(String path) throws IOException {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(path)) {
            assertThat(input).as("migration resource %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
