package com.example.llm.chatv2;

import com.example.llm.auth.config.AuthProperties;
import com.example.llm.auth.security.AuthCrypto;
import com.example.llm.chatv2.api.ChatV2ApiException;
import com.example.llm.chatv2.repository.ChatV2Repository;
import com.example.llm.chatv2.repository.ChatV2Repository.PreparedRun;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
class ChatV2BranchingIntegrationTest {
    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.0.45")
            .withDatabaseName("examinsight_v2_chat_branch_test")
            .withUsername("examinsight")
            .withPassword("examinsight-test-password");

    private static JdbcTemplate jdbc;
    private static ChatV2Repository repository;
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
        AuthProperties properties = new AuthProperties();
        properties.setHashSecret("chat-branch-test-secret-at-least-thirty-two-characters");
        crypto = new AuthCrypto(properties);
        repository = new ChatV2Repository(
                jdbc,
                new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource())),
                crypto,
                new ObjectMapper());
    }

    @Test
    void editRegenerateAndSwitchKeepPersistedMessageBranches() {
        long userId = insertUser();
        var conversation = repository.createConversation(userId, null, null, null);

        PreparedRun original = repository.prepareRun(
                userId, conversation.id(), "原始问题", List.of(), "original-turn");
        finalizeWithoutCallingModel(original, "原始回答");

        PreparedRun edited = repository.prepareEditedRun(
                userId, conversation.id(), original.requestMessageExternalId(),
                "编辑后的问题", "edited-turn");
        finalizeWithoutCallingModel(edited, "编辑后的回答");

        var editedDetail = repository.getConversation(userId, conversation.id());
        assertThat(editedDetail.messages()).extracting(message -> message.content())
                .containsExactly("编辑后的问题", "编辑后的回答");
        assertThat(editedDetail.versionGroups()).singleElement().satisfies(group -> {
            assertThat(group.role()).isEqualTo("USER");
            assertThat(group.versions()).hasSize(2);
        });

        repository.activateBranch(userId, conversation.id(), original.branchExternalId());
        var originalDetail = repository.getConversation(userId, conversation.id());
        assertThat(originalDetail.messages()).extracting(message -> message.content())
                .containsExactly("原始问题", "原始回答");

        PreparedRun regenerated = repository.prepareRegeneratedRun(
                userId, conversation.id(), original.responseMessageExternalId(), "regenerated-turn");
        finalizeWithoutCallingModel(regenerated, "重新生成的回答");

        var regeneratedDetail = repository.getConversation(userId, conversation.id());
        assertThat(regeneratedDetail.messages()).extracting(message -> message.content())
                .containsExactly("原始问题", "重新生成的回答");
        assertThat(regeneratedDetail.versionGroups()).extracting(group -> group.role())
                .containsExactlyInAnyOrder("USER", "ASSISTANT");
        assertThat(regeneratedDetail.versionGroups().stream()
                .filter(group -> "ASSISTANT".equals(group.role())).findFirst().orElseThrow().versions())
                .hasSize(2);
    }

    @Test
    void directAttachmentsRemainAvailableToLaterTurns() {
        long userId = insertUser();
        ReadyAsset source = insertReadyTextAsset(userId, "课程笔记");
        var conversation = repository.createConversation(userId, null, null, null);

        PreparedRun first = repository.prepareRun(
                userId, conversation.id(), "请总结附件", List.of(source.assetExternalId()), "attachment-turn-1");
        assertThat(first.sourceVersionExternalIds()).containsExactly(source.versionExternalId());
        finalizeWithoutCallingModel(first, "第一轮回答");

        PreparedRun followUp = repository.prepareRun(
                userId, conversation.id(), "继续解释第二点", List.of(), "attachment-turn-2");

        assertThat(followUp.sourceVersionExternalIds()).containsExactly(source.versionExternalId());
        Integer frozenSourceCount = jdbc.queryForObject("""
                SELECT COUNT(*)
                  FROM ai_context_source source
                  JOIN ai_context_snapshot snapshot ON snapshot.id = source.context_snapshot_id
                 WHERE snapshot.ai_run_id = ? AND source.source_kind = 'DIRECT_ASSET'
                """, Integer.class, followUp.runId());
        assertThat(frozenSourceCount).isEqualTo(1);
    }

    @Test
    void conversationRejectsMoreThanTwentyUniqueDirectAttachments() {
        long userId = insertUser();
        var conversation = repository.createConversation(userId, null, null, null);
        List<String> firstTwenty = java.util.stream.IntStream.rangeClosed(1, 20)
                .mapToObj(index -> insertReadyTextAsset(userId, "资料-" + index).assetExternalId())
                .toList();

        PreparedRun first = repository.prepareRun(
                userId, conversation.id(), "先看这些资料", firstTwenty, "attachment-limit-1");
        finalizeWithoutCallingModel(first, "第一轮回答");
        String twentyFirst = insertReadyTextAsset(userId, "资料-21").assetExternalId();

        assertThatThrownBy(() -> repository.prepareRun(
                userId, conversation.id(), "再增加一份", List.of(twentyFirst), "attachment-limit-2"))
                .isInstanceOf(ChatV2ApiException.class)
                .hasMessageContaining("最多额外关联 20 个资料");
    }

    private long insertUser() {
        String id = crypto.newExternalId();
        String email = id.toLowerCase() + "@example.com";
        jdbc.update("""
                INSERT INTO app_user (
                    external_id, normalized_email, email_display, status, email_verified_at
                ) VALUES (?, ?, ?, 'ACTIVE', CURRENT_TIMESTAMP(3))
                """, id, email, email);
        return jdbc.queryForObject("SELECT id FROM app_user WHERE external_id = ?", Long.class, id);
    }

    private ReadyAsset insertReadyTextAsset(long userId, String name) {
        String assetExternalId = crypto.newExternalId();
        jdbc.update("""
                INSERT INTO asset (external_id, user_id, name, asset_type, source_type, status)
                VALUES (?, ?, ?, 'TEXT', 'USER_TEXT', 'ACTIVE')
                """, assetExternalId, userId, name);
        long assetId = jdbc.queryForObject(
                "SELECT id FROM asset WHERE external_id = ?", Long.class, assetExternalId);

        String versionExternalId = crypto.newExternalId();
        String content = "可检索的课程资料：" + name;
        String contentHash = crypto.digest("chat-branch-test-content", content);
        jdbc.update("""
                INSERT INTO asset_version (
                    external_id, asset_id, version_no, text_content, content_sha256,
                    mime_type, size_bytes, source_type, status, created_by_user_id,
                    rag_policy, rag_status
                ) VALUES (?, ?, 1, ?, ?, 'text/plain', OCTET_LENGTH(?),
                          'USER_TEXT', 'READY', ?, 'AUTO', 'PENDING')
                """, versionExternalId, assetId, content, contentHash, content, userId);
        long versionId = jdbc.queryForObject(
                "SELECT id FROM asset_version WHERE external_id = ?", Long.class, versionExternalId);

        String jobExternalId = crypto.newExternalId();
        jdbc.update("""
                INSERT INTO async_job (
                    external_id, user_id, job_type, aggregate_type, aggregate_external_id,
                    status, stage_key, progress_current, progress_total, finished_at
                ) VALUES (?, ?, 'FILE_PARSE', 'ASSET_VERSION', ?,
                          'SUCCEEDED', 'TEXT_EXTRACTION', 1, 1, CURRENT_TIMESTAMP(3))
                """, jobExternalId, userId, versionExternalId);
        long jobId = jdbc.queryForObject(
                "SELECT id FROM async_job WHERE external_id = ?", Long.class, jobExternalId);

        String parseExternalId = crypto.newExternalId();
        jdbc.update("""
                INSERT INTO asset_parse_result (
                    external_id, asset_version_id, parser_key, parser_version, async_job_id,
                    status, language, page_count, chunk_count, plain_text_sha256, completed_at
                ) VALUES (?, ?, 'test-parser', '1', ?, 'READY', 'zh', 1, 1, ?, CURRENT_TIMESTAMP(3))
                """, parseExternalId, versionId, jobId, contentHash);
        long parseId = jdbc.queryForObject(
                "SELECT id FROM asset_parse_result WHERE external_id = ?", Long.class, parseExternalId);
        jdbc.update("UPDATE asset_version SET active_parse_result_id = ? WHERE id = ?", parseId, versionId);
        jdbc.update("UPDATE asset SET current_version_id = ? WHERE id = ?", versionId, assetId);
        return new ReadyAsset(assetExternalId, versionExternalId);
    }

    private void finalizeWithoutCallingModel(PreparedRun run, String answer) {
        jdbc.update("""
                UPDATE message SET status = 'FINALIZED', plain_text = ?,
                       finalized_at = CURRENT_TIMESTAMP(3), row_version = row_version + 1
                 WHERE id = ?
                """, answer, run.responseMessageId());
        jdbc.update("""
                UPDATE conversation_branch SET active_run_id = NULL, row_version = row_version + 1
                 WHERE id = ?
                """, run.branchId());
    }

    private record ReadyAsset(String assetExternalId, String versionExternalId) {
    }
}
