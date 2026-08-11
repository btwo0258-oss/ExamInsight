package com.example.llm.chatv2.repository;

import com.example.llm.auth.security.AuthCrypto;
import com.example.llm.asset.retrieval.RetrievalModels;
import com.example.llm.asset.retrieval.RetrievalModels.Source;
import com.example.llm.chatv2.api.ChatV2ApiException;
import com.example.llm.chatv2.api.ChatV2Dtos.AiRunView;
import com.example.llm.chatv2.api.ChatV2Dtos.CitationView;
import com.example.llm.chatv2.api.ChatV2Dtos.ConversationDetail;
import com.example.llm.chatv2.api.ChatV2Dtos.ConversationPage;
import com.example.llm.chatv2.api.ChatV2Dtos.ConversationSummary;
import com.example.llm.chatv2.api.ChatV2Dtos.MessageView;
import com.example.llm.integration.ai.AiCallResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Repository
public class ChatV2Repository {
    private static final String DEFAULT_TITLE = "新对话";

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final AuthCrypto crypto;
    private final ObjectMapper objectMapper;

    public ChatV2Repository(
            @Qualifier("v2JdbcTemplate") JdbcTemplate jdbc,
            @Qualifier("v2TransactionTemplate") TransactionTemplate transactions,
            AuthCrypto crypto,
            ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.crypto = crypto;
        this.objectMapper = objectMapper;
    }

    public ConversationSummary createConversation(
            long userId, String requestedTitle, String knowledgeBaseExternalId) {
        return transactions.execute(status -> {
            Long knowledgeBaseId = resolveKnowledgeBaseId(userId, knowledgeBaseExternalId, false);
            String conversationExternalId = crypto.newExternalId();
            long conversationId = insertAndReturnId("""
                    INSERT INTO conversation (
                        external_id, user_id, conversation_type, knowledge_base_id,
                        title, status
                    ) VALUES (?, ?, 'GENERAL', ?, ?, 'ACTIVE')
                    """, conversationExternalId, userId, knowledgeBaseId, normalizedTitle(requestedTitle));

            String branchExternalId = crypto.newExternalId();
            long branchId = insertAndReturnId("""
                    INSERT INTO conversation_branch (
                        external_id, conversation_id, status, created_by_user_id
                    ) VALUES (?, ?, 'ACTIVE', ?)
                    """, branchExternalId, conversationId, userId);
            jdbc.update("UPDATE conversation SET active_branch_id = ? WHERE id = ?", branchId, conversationId);
            return requireConversationSummary(userId, conversationExternalId);
        });
    }

    public ConversationPage listConversations(long userId, String cursor, int requestedLimit) {
        int limit = Math.max(1, Math.min(100, requestedLimit));
        List<Object> args = new ArrayList<>();
        args.add(userId);
        String cursorPredicate = "";
        if (cursor != null && !cursor.isBlank()) {
            cursorPredicate = " AND c.external_id < ?";
            args.add(cursor.trim());
        }
        args.add(limit + 1);
        List<ConversationSummary> rows = jdbc.query("""
                SELECT c.external_id, c.title, c.conversation_type, c.status,
                       kb.external_id AS knowledge_base_external_id,
                       branch.external_id AS active_branch_external_id,
                       (SELECT COUNT(*) FROM message m WHERE m.conversation_id = c.id) AS message_count,
                       c.row_version, c.last_message_at, c.created_at, c.updated_at
                  FROM conversation c
                  LEFT JOIN knowledge_base kb ON kb.id = c.knowledge_base_id
                  LEFT JOIN conversation_branch branch ON branch.id = c.active_branch_id
                 WHERE c.user_id = ? AND c.conversation_type = 'GENERAL' AND c.status = 'ACTIVE'
                """ + cursorPredicate + " ORDER BY c.external_id DESC LIMIT ?",
                (rs, rowNum) -> mapConversationSummary(rs), args.toArray());
        boolean hasMore = rows.size() > limit;
        List<ConversationSummary> items = hasMore ? List.copyOf(rows.subList(0, limit)) : List.copyOf(rows);
        String nextCursor = hasMore && !items.isEmpty() ? items.get(items.size() - 1).id() : null;
        return new ConversationPage(items, nextCursor, hasMore);
    }

    public ConversationDetail getConversation(long userId, String conversationExternalId) {
        ConversationSummary summary = requireConversationSummary(userId, conversationExternalId);
        Map<String, List<CitationView>> citations = loadCitations(userId, conversationExternalId);
        List<MessageView> messages = jdbc.query("""
                SELECT m.external_id, b.external_id AS branch_external_id,
                       parent.external_id AS parent_external_id,
                       m.role, m.status, m.sequence_no, m.plain_text,
                       run.external_id AS run_external_id,
                       m.created_at, m.finalized_at
                  FROM message m
                  JOIN conversation c ON c.id = m.conversation_id
                  JOIN conversation_branch b ON b.id = m.branch_id
                  LEFT JOIN message parent ON parent.id = m.parent_message_id
                  LEFT JOIN ai_run run ON run.id = m.ai_run_id
                 WHERE c.external_id = ? AND c.user_id = ? AND m.branch_id = c.active_branch_id
                 ORDER BY m.sequence_no ASC
                """, (rs, rowNum) -> new MessageView(
                        rs.getString("external_id"),
                        rs.getString("branch_external_id"),
                        rs.getString("parent_external_id"),
                        rs.getString("role"),
                        rs.getString("status"),
                        rs.getLong("sequence_no"),
                        rs.getString("plain_text"),
                        rs.getString("run_external_id"),
                        citations.getOrDefault(rs.getString("external_id"), List.of()),
                        instant(rs.getTimestamp("created_at")),
                        instant(rs.getTimestamp("finalized_at"))),
                conversationExternalId, userId);
        return new ConversationDetail(summary, messages);
    }

    public ConversationSummary updateConversation(
            long userId,
            String conversationExternalId,
            String requestedTitle,
            String knowledgeBaseExternalId,
            boolean clearKnowledgeBase) {
        return transactions.execute(status -> {
            ConversationRow conversation = requireConversationForUpdate(userId, conversationExternalId);
            String title = requestedTitle == null ? conversation.title() : normalizedTitle(requestedTitle);
            Long knowledgeBaseId = conversation.knowledgeBaseId();
            if (clearKnowledgeBase) {
                knowledgeBaseId = null;
            } else if (knowledgeBaseExternalId != null) {
                knowledgeBaseId = resolveKnowledgeBaseId(userId, knowledgeBaseExternalId, false);
            }
            jdbc.update("""
                    UPDATE conversation
                       SET title = ?, knowledge_base_id = ?, row_version = row_version + 1
                     WHERE id = ?
                    """, title, knowledgeBaseId, conversation.id());
            return requireConversationSummary(userId, conversationExternalId);
        });
    }

    public void moveConversationToTrash(long userId, String conversationExternalId) {
        transactions.executeWithoutResult(status -> {
            ConversationRow conversation = requireConversationForUpdate(userId, conversationExternalId);
            if (conversation.activeRunId() != null) {
                throw conflict("RUN_ACTIVE", "当前回答生成完成或取消后才能删除对话。");
            }
            jdbc.update("""
                    UPDATE conversation
                       SET previous_status = status,
                           status = 'TRASHED', trash_started_at = CURRENT_TIMESTAMP(3),
                           deleted_at = CURRENT_TIMESTAMP(3), row_version = row_version + 1
                     WHERE id = ?
                    """, conversation.id());
        });
    }

    public PreparedRun prepareRun(
            long userId,
            String conversationExternalId,
            String content,
            List<String> requestedAssetIds,
            String requestedIdempotencyKey) {
        return transactions.execute(status -> {
            String idempotencyKey = normalizeIdempotencyKey(requestedIdempotencyKey);
            Optional<PreparedRun> replay = findIdempotentRun(userId, conversationExternalId, idempotencyKey);
            if (replay.isPresent()) {
                return replay.get();
            }

            ConversationRow conversation = requireConversationForUpdate(userId, conversationExternalId);
            if (!"ACTIVE".equals(conversation.status())) {
                throw notFound("CONVERSATION_NOT_FOUND", "对话不存在或不可用。");
            }
            if (conversation.activeRunId() != null) {
                throw conflict("RUN_ALREADY_ACTIVE", "当前对话仍在生成回答，请等待完成或先取消。");
            }

            List<LockedAssetVersion> directSources = resolveDirectSources(userId, requestedAssetIds);
            validateKnowledgeBaseReadiness(userId, conversation.knowledgeBaseId());

            BranchRow branch = requireActiveBranchForUpdate(conversation.id(), conversation.activeBranchId());
            MessageCursor cursor = lastMessageCursor(branch.id());
            String userMessageExternalId = crypto.newExternalId();
            long userMessageId = insertAndReturnId("""
                    INSERT INTO message (
                        external_id, conversation_id, branch_id, parent_message_id,
                        role, status, sequence_no, plain_text, generated_by_ai, finalized_at
                    ) VALUES (?, ?, ?, ?, 'USER', 'FINALIZED', ?, ?, FALSE, CURRENT_TIMESTAMP(3))
                    """, userMessageExternalId, conversation.id(), branch.id(), cursor.messageId(),
                    cursor.sequence() + 1, content.trim());

            long responseGroupId = insertAndReturnId("""
                    INSERT INTO assistant_response_group (
                        external_id, conversation_id, branch_id, user_message_id
                    ) VALUES (?, ?, ?, ?)
                    """, crypto.newExternalId(), conversation.id(), branch.id(), userMessageId);

            RuntimeDefinition runtime = requireRuntimeDefinition();
            String jobExternalId = crypto.newExternalId();
            String runExternalId = crypto.newExternalId();
            String payloadJson = json(Map.of(
                    "conversationId", conversationExternalId,
                    "requestMessageId", userMessageExternalId,
                    "sourceAssetIds", directSources.stream().map(LockedAssetVersion::assetExternalId).toList()));
            long jobId = insertAndReturnId("""
                    INSERT INTO async_job (
                        external_id, user_id, job_type, aggregate_type, aggregate_external_id,
                        status, stage_key, progress_current, progress_total,
                        idempotency_scope, idempotency_key, cancellable, payload_json, max_attempts
                    ) VALUES (?, ?, 'GENERAL_CHAT_RESPONSE', 'CONVERSATION', ?,
                              'QUEUED', 'queued', 0, 4, ?, ?, TRUE, CAST(? AS JSON), 1)
                    """, jobExternalId, userId, conversationExternalId,
                    idempotencyScope(conversationExternalId), idempotencyKey, payloadJson);

            long runId = insertAndReturnId("""
                    INSERT INTO ai_run (
                        external_id, user_id, async_job_id, conversation_id, branch_id,
                        request_message_id, capability_id, model_policy_version_id,
                        prompt_version_id, mode, intent_key, side_effect_level
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'GENERAL_CHAT', 'general.chat', 'NONE')
                    """, runExternalId, userId, jobId, conversation.id(), branch.id(), userMessageId,
                    runtime.capabilityId(), runtime.modelPolicyVersionId(), runtime.promptVersionId());

            String assistantMessageExternalId = crypto.newExternalId();
            long assistantMessageId = insertAndReturnId("""
                    INSERT INTO message (
                        external_id, conversation_id, branch_id, parent_message_id,
                        role, status, sequence_no, response_group_id, generated_by_ai,
                        ai_run_id, generation_label
                    ) VALUES (?, ?, ?, ?, 'ASSISTANT', 'STREAMING', ?, ?, TRUE, ?, 'general-chat-v1')
                    """, assistantMessageExternalId, conversation.id(), branch.id(), userMessageId,
                    cursor.sequence() + 2, responseGroupId, runId);
            jdbc.update("UPDATE ai_run SET response_message_id = ? WHERE id = ?", assistantMessageId, runId);
            jdbc.update("UPDATE assistant_response_group SET selected_message_id = ? WHERE id = ?",
                    assistantMessageId, responseGroupId);
            jdbc.update("UPDATE conversation_branch SET active_run_id = ?, row_version = row_version + 1 WHERE id = ?",
                    runId, branch.id());

            for (LockedAssetVersion source : directSources) {
                jdbc.update("""
                        INSERT INTO message_attachment (
                            message_id, asset_version_id, attachment_role, display_name
                        ) VALUES (?, ?, 'CONTEXT', ?)
                        """, userMessageId, source.assetVersionId(), source.assetName());
            }

            Map<String, Object> manifest = new LinkedHashMap<>();
            manifest.put("conversationId", conversationExternalId);
            manifest.put("knowledgeBaseId", conversation.knowledgeBaseExternalId());
            manifest.put("directAssetVersions", directSources.stream().map(source -> Map.of(
                    "assetId", source.assetExternalId(),
                    "versionId", source.versionExternalId(),
                    "name", source.assetName())).toList());
            manifest.put("messageSequence", cursor.sequence() + 1);
            String manifestJson = json(manifest);
            jdbc.update("""
                    INSERT INTO ai_context_snapshot (
                        external_id, ai_run_id, context_schema_version,
                        context_manifest_json, context_hash
                    ) VALUES (?, ?, 1, CAST(? AS JSON), ?)
                    """, crypto.newExternalId(), runId, manifestJson,
                    sha256(manifestJson.getBytes(StandardCharsets.UTF_8)));

            String title = conversation.title();
            if (DEFAULT_TITLE.equals(title)) {
                title = titleFromMessage(content);
            }
            jdbc.update("""
                    UPDATE conversation
                       SET title = ?, last_message_at = CURRENT_TIMESTAMP(3), row_version = row_version + 1
                     WHERE id = ?
                    """, title, conversation.id());

            return new PreparedRun(
                    runId, runExternalId, jobId, jobExternalId, userId,
                    conversation.id(), conversationExternalId, branch.id(), branch.externalId(),
                    userMessageId, userMessageExternalId,
                    assistantMessageId, assistantMessageExternalId,
                    conversation.knowledgeBaseExternalId(),
                    directSources.stream().map(LockedAssetVersion::versionExternalId).toList(),
                    runtime, false);
        });
    }

    public RunExecutionContext loadRunExecutionContext(long userId, String runExternalId) {
        List<RunExecutionContext> rows = jdbc.query("""
                SELECT run.id, run.external_id, run.async_job_id, job.external_id AS job_external_id,
                       run.conversation_id, c.external_id AS conversation_external_id,
                       run.branch_id, b.external_id AS branch_external_id,
                       run.request_message_id, request.external_id AS request_external_id,
                       request.plain_text AS request_text,
                       run.response_message_id, response.external_id AS response_external_id,
                       kb.external_id AS knowledge_base_external_id,
                       run.model_policy_version_id, run.prompt_version_id,
                       prompt.system_template, prompt.developer_template
                  FROM ai_run run
                  JOIN async_job job ON job.id = run.async_job_id
                  JOIN conversation c ON c.id = run.conversation_id
                  JOIN conversation_branch b ON b.id = run.branch_id
                  JOIN message request ON request.id = run.request_message_id
                  JOIN message response ON response.id = run.response_message_id
                  JOIN prompt_version prompt ON prompt.id = run.prompt_version_id
                  LEFT JOIN knowledge_base kb ON kb.id = c.knowledge_base_id
                 WHERE run.external_id = ? AND run.user_id = ?
                """, (rs, rowNum) -> new RunExecutionContext(
                        rs.getLong("id"), rs.getString("external_id"), userId,
                        rs.getLong("async_job_id"), rs.getString("job_external_id"),
                        rs.getLong("conversation_id"), rs.getString("conversation_external_id"),
                        rs.getLong("branch_id"), rs.getString("branch_external_id"),
                        rs.getLong("request_message_id"), rs.getString("request_external_id"),
                        rs.getString("request_text"), rs.getLong("response_message_id"),
                        rs.getString("response_external_id"), rs.getString("knowledge_base_external_id"),
                        loadDirectVersionExternalIds(rs.getLong("request_message_id")),
                        rs.getLong("model_policy_version_id"), rs.getLong("prompt_version_id"),
                        rs.getString("system_template"), rs.getString("developer_template"),
                        loadHistory(rs.getLong("branch_id"), rs.getLong("request_message_id"))),
                runExternalId, userId);
        if (rows.isEmpty()) {
            throw notFound("AI_RUN_NOT_FOUND", "生成任务不存在。");
        }
        return rows.get(0);
    }

    public boolean markRunStarted(RunExecutionContext context) {
        return Boolean.TRUE.equals(transactions.execute(status -> {
            int jobUpdated = jdbc.update("""
                    UPDATE async_job
                       SET status = 'RUNNING', stage_key = 'retrieving',
                           started_at = CURRENT_TIMESTAMP(3), heartbeat_at = CURRENT_TIMESTAMP(3),
                           attempt_count = attempt_count + 1, progress_current = 1
                     WHERE id = ? AND status = 'QUEUED'
                    """, context.jobId());
            if (jobUpdated == 0) {
                return false;
            }
            jdbc.update("UPDATE ai_run SET started_at = CURRENT_TIMESTAMP(3) WHERE id = ?", context.runId());
            insertAndReturnId("""
                    INSERT INTO async_job_attempt (
                        external_id, async_job_id, attempt_no, worker_id, status
                    ) VALUES (?, ?, 1, ?, 'RUNNING')
                    """, crypto.newExternalId(), context.jobId(), workerId());
            return true;
        }));
    }

    public void updateRunStage(long jobId, String stage, long progress) {
        jdbc.update("""
                UPDATE async_job
                   SET stage_key = ?, progress_current = ?, heartbeat_at = CURRENT_TIMESTAMP(3)
                 WHERE id = ? AND status = 'RUNNING'
                """, stage, progress, jobId);
    }

    public boolean cancellationRequested(long jobId) {
        List<String> status = jdbc.query("SELECT status FROM async_job WHERE id = ?",
                (rs, rowNum) -> rs.getString(1), jobId);
        return !status.isEmpty() && List.of("CANCELLING", "CANCELLED").contains(status.get(0));
    }

    public void completeRun(
            RunExecutionContext context,
            String answer,
            List<CitationSource> citations,
            AiCallResult<String> result,
            Instant invocationStartedAt) {
        transactions.executeWithoutResult(status -> {
            if (cancellationRequested(context.jobId())) {
                cancelRunInternal(context);
                return;
            }
            jdbc.update("""
                    UPDATE message
                       SET status = 'FINALIZED', plain_text = ?, finalized_at = CURRENT_TIMESTAMP(3),
                           row_version = row_version + 1
                     WHERE id = ? AND status = 'STREAMING'
                    """, answer, context.responseMessageId());
            jdbc.update("""
                    INSERT INTO message_part (
                        message_id, part_no, part_type, text_content, content_sha256
                    ) VALUES (?, 1, 'MARKDOWN', ?, ?)
                    """, context.responseMessageId(), answer,
                    sha256(answer.getBytes(StandardCharsets.UTF_8)));
            persistCitations(context.responseMessageId(), citations);
            String resultJson = json(Map.of(
                    "responseMessageId", context.responseMessageExternalId(),
                    "provider", result.provider(),
                    "model", result.model()));
            jdbc.update("""
                    UPDATE async_job
                       SET status = 'SUCCEEDED', stage_key = 'completed', progress_current = 4,
                           heartbeat_at = CURRENT_TIMESTAMP(3), finished_at = CURRENT_TIMESTAMP(3),
                           result_json = CAST(? AS JSON)
                     WHERE id = ?
                    """, resultJson, context.jobId());
            jdbc.update("""
                    UPDATE async_job_attempt
                       SET status = 'SUCCEEDED', finished_at = CURRENT_TIMESTAMP(3),
                           heartbeat_at = CURRENT_TIMESTAMP(3)
                     WHERE async_job_id = ? AND attempt_no = 1
                    """, context.jobId());
            jdbc.update("UPDATE ai_run SET completed_at = CURRENT_TIMESTAMP(3) WHERE id = ?", context.runId());
            jdbc.update("""
                    UPDATE conversation_branch
                       SET active_run_id = NULL, row_version = row_version + 1
                     WHERE id = ? AND active_run_id = ?
                    """, context.branchId(), context.runId());
            jdbc.update("""
                    UPDATE conversation
                       SET last_message_at = CURRENT_TIMESTAMP(3), row_version = row_version + 1
                     WHERE id = ?
                    """, context.conversationId());
            persistInvocation(context, result, invocationStartedAt, "SUCCEEDED", null);
        });
    }

    public void failRun(
            RunExecutionContext context,
            String errorCode,
            String safeMessage,
            AiCallResult<String> result,
            Instant invocationStartedAt) {
        transactions.executeWithoutResult(status -> {
            jdbc.update("""
                    UPDATE message
                       SET status = 'FAILED', finalized_at = CURRENT_TIMESTAMP(3),
                           row_version = row_version + 1
                     WHERE id = ? AND status = 'STREAMING'
                    """, context.responseMessageId());
            jdbc.update("""
                    UPDATE async_job
                       SET status = 'FAILED', stage_key = 'failed', finished_at = CURRENT_TIMESTAMP(3),
                           heartbeat_at = CURRENT_TIMESTAMP(3), error_code = ?, safe_error_message = ?
                     WHERE id = ?
                    """, safeCode(errorCode), safeMessage, context.jobId());
            jdbc.update("""
                    UPDATE async_job_attempt
                       SET status = 'FAILED', finished_at = CURRENT_TIMESTAMP(3),
                           heartbeat_at = CURRENT_TIMESTAMP(3), error_code = ?
                     WHERE async_job_id = ? AND attempt_no = 1
                    """, safeCode(errorCode), context.jobId());
            jdbc.update("UPDATE ai_run SET completed_at = CURRENT_TIMESTAMP(3) WHERE id = ?", context.runId());
            jdbc.update("""
                    UPDATE conversation_branch
                       SET active_run_id = NULL, row_version = row_version + 1
                     WHERE id = ? AND active_run_id = ?
                    """, context.branchId(), context.runId());
            if (result != null) {
                persistInvocation(context, result, invocationStartedAt, "FAILED", errorCode);
            }
        });
    }

    public void cancelRun(RunExecutionContext context) {
        transactions.executeWithoutResult(status -> cancelRunInternal(context));
    }

    public void recordRetrieval(
            RunExecutionContext context,
            List<Source> sources,
            List<String> degradationCodes,
            int topK) {
        transactions.executeWithoutResult(transaction -> {
            String retrievalMode = retrievalMode(sources);
            String status = sources.isEmpty() && !degradationCodes.isEmpty()
                    ? "FAILED"
                    : "SUCCEEDED";
            Map<String, Object> filters = new LinkedHashMap<>();
            filters.put("knowledgeBaseId", context.knowledgeBaseExternalId());
            filters.put("directVersionIds", context.directVersionExternalIds());
            filters.put("degradationCodes", degradationCodes);

            long retrievalRunId = insertAndReturnId("""
                    INSERT INTO retrieval_run (
                        external_id, ai_run_id, query_text, query_hash,
                        retrieval_mode, filters_json, top_k,
                        started_at, completed_at, status
                    ) VALUES (?, ?, ?, ?, ?, CAST(? AS JSON), ?,
                              CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), ?)
                    """, crypto.newExternalId(), context.runId(), context.requestText(),
                    sha256(context.requestText().getBytes(StandardCharsets.UTF_8)),
                    retrievalMode, json(filters), topK, status);

            for (Source source : sources) {
                List<Long> chunkIds = jdbc.query(
                        "SELECT id FROM document_chunk WHERE external_id = ?",
                        (rs, rowNum) -> rs.getLong(1), source.chunkExternalId());
                if (chunkIds.isEmpty() || source.mode() == RetrievalModels.Mode.NONE) {
                    continue;
                }
                Double semanticScore = source.mode() == RetrievalModels.Mode.SEMANTIC
                        ? normalizedScore(source.score()) : null;
                Double keywordScore = source.mode() == RetrievalModels.Mode.KEYWORD
                        ? normalizedScore(source.score()) : null;
                jdbc.update("""
                        INSERT INTO retrieval_result (
                            retrieval_run_id, rank_no, document_chunk_id,
                            semantic_score, keyword_score, selected_for_context, reason_code
                        ) VALUES (?, ?, ?, ?, ?, TRUE, 'SELECTED_FOR_CONTEXT')
                        """, retrievalRunId, source.citationNo(), chunkIds.get(0),
                        semanticScore, keywordScore);
            }
        });
    }

    public AiRunView getRun(long userId, String runExternalId) {
        List<AiRunView> rows = jdbc.query("""
                SELECT run.external_id, c.external_id AS conversation_external_id,
                       b.external_id AS branch_external_id,
                       request.external_id AS request_external_id,
                       response.external_id AS response_external_id,
                       job.status, job.stage_key, job.cancellable,
                       job.error_code, job.safe_error_message,
                       job.created_at, job.started_at, job.finished_at
                  FROM ai_run run
                  JOIN async_job job ON job.id = run.async_job_id
                  JOIN conversation c ON c.id = run.conversation_id
                  JOIN conversation_branch b ON b.id = run.branch_id
                  JOIN message request ON request.id = run.request_message_id
                  JOIN message response ON response.id = run.response_message_id
                 WHERE run.external_id = ? AND run.user_id = ?
                """, (rs, rowNum) -> new AiRunView(
                        rs.getString("external_id"), rs.getString("conversation_external_id"),
                        rs.getString("branch_external_id"), rs.getString("request_external_id"),
                        rs.getString("response_external_id"), rs.getString("status"),
                        rs.getString("stage_key"), rs.getBoolean("cancellable"),
                        rs.getString("error_code"), rs.getString("safe_error_message"),
                        instant(rs.getTimestamp("created_at")), instant(rs.getTimestamp("started_at")),
                        instant(rs.getTimestamp("finished_at"))),
                runExternalId, userId);
        if (rows.isEmpty()) {
            throw notFound("AI_RUN_NOT_FOUND", "生成任务不存在。");
        }
        return rows.get(0);
    }

    public boolean requestCancellation(long userId, String runExternalId) {
        int updated = jdbc.update("""
                UPDATE async_job job
                JOIN ai_run run ON run.async_job_id = job.id
                   SET job.status = 'CANCELLING', job.stage_key = 'cancelling',
                       job.heartbeat_at = CURRENT_TIMESTAMP(3)
                 WHERE run.external_id = ? AND run.user_id = ?
                   AND job.cancellable = TRUE AND job.status IN ('QUEUED', 'RUNNING')
                """, runExternalId, userId);
        if (updated > 0) {
            return true;
        }
        AiRunView run = getRun(userId, runExternalId);
        return run.terminal() || "CANCELLING".equals(run.status());
    }

    private Optional<PreparedRun> findIdempotentRun(
            long userId, String conversationExternalId, String idempotencyKey) {
        List<PreparedRun> rows = jdbc.query("""
                SELECT run.id, run.external_id, job.id AS job_id, job.external_id AS job_external_id,
                       run.conversation_id, c.external_id AS conversation_external_id,
                       run.branch_id, b.external_id AS branch_external_id,
                       run.request_message_id, request.external_id AS request_external_id,
                       run.response_message_id, response.external_id AS response_external_id,
                       kb.external_id AS knowledge_base_external_id,
                       run.capability_id, run.model_policy_version_id, run.prompt_version_id
                  FROM async_job job
                  JOIN ai_run run ON run.async_job_id = job.id
                  JOIN conversation c ON c.id = run.conversation_id
                  JOIN conversation_branch b ON b.id = run.branch_id
                  JOIN message request ON request.id = run.request_message_id
                  JOIN message response ON response.id = run.response_message_id
                  LEFT JOIN knowledge_base kb ON kb.id = c.knowledge_base_id
                 WHERE job.user_id = ? AND job.job_type = 'GENERAL_CHAT_RESPONSE'
                   AND job.idempotency_scope = ? AND job.idempotency_key = ?
                """, (rs, rowNum) -> {
                    long requestMessageId = rs.getLong("request_message_id");
                    RuntimeDefinition runtime = new RuntimeDefinition(
                            rs.getLong("capability_id"),
                            rs.getLong("model_policy_version_id"),
                            rs.getLong("prompt_version_id"));
                    return new PreparedRun(
                            rs.getLong("id"), rs.getString("external_id"),
                            rs.getLong("job_id"), rs.getString("job_external_id"), userId,
                            rs.getLong("conversation_id"), rs.getString("conversation_external_id"),
                            rs.getLong("branch_id"), rs.getString("branch_external_id"),
                            requestMessageId, rs.getString("request_external_id"),
                            rs.getLong("response_message_id"), rs.getString("response_external_id"),
                            rs.getString("knowledge_base_external_id"),
                            loadDirectVersionExternalIds(requestMessageId), runtime, true);
                }, userId, idempotencyScope(conversationExternalId), idempotencyKey);
        return rows.stream().findFirst();
    }

    private void cancelRunInternal(RunExecutionContext context) {
        jdbc.update("""
                UPDATE message
                   SET status = 'CANCELLED', finalized_at = CURRENT_TIMESTAMP(3),
                       row_version = row_version + 1
                 WHERE id = ? AND status = 'STREAMING'
                """, context.responseMessageId());
        jdbc.update("""
                UPDATE async_job
                   SET status = 'CANCELLED', stage_key = 'cancelled',
                       finished_at = CURRENT_TIMESTAMP(3), heartbeat_at = CURRENT_TIMESTAMP(3)
                 WHERE id = ? AND status IN ('QUEUED', 'RUNNING', 'CANCELLING')
                """, context.jobId());
        jdbc.update("""
                UPDATE async_job_attempt
                   SET status = 'CANCELLED', finished_at = CURRENT_TIMESTAMP(3),
                       heartbeat_at = CURRENT_TIMESTAMP(3)
                 WHERE async_job_id = ? AND attempt_no = 1 AND status = 'RUNNING'
                """, context.jobId());
        jdbc.update("UPDATE ai_run SET completed_at = CURRENT_TIMESTAMP(3) WHERE id = ?", context.runId());
        jdbc.update("""
                UPDATE conversation_branch
                   SET active_run_id = NULL, row_version = row_version + 1
                 WHERE id = ? AND active_run_id = ?
                """, context.branchId(), context.runId());
    }

    private void persistCitations(long messageId, List<CitationSource> citations) {
        for (CitationSource citation : citations) {
            List<Long> chunkIds = jdbc.query("SELECT id FROM document_chunk WHERE external_id = ?",
                    (rs, rowNum) -> rs.getLong(1), citation.chunkExternalId());
            if (chunkIds.isEmpty()) {
                continue;
            }
            jdbc.update("""
                    INSERT INTO message_citation (
                        message_id, citation_no, citation_type, document_chunk_id,
                        quoted_text, locator_label, support_score
                    ) VALUES (?, ?, 'DOCUMENT_CHUNK', ?, ?, ?, ?)
                    """, messageId, citation.number(), chunkIds.get(0),
                    truncate(citation.quotedText(), 2000), truncate(citation.locator(), 500), citation.score());
        }
    }

    private void persistInvocation(
            RunExecutionContext context,
            AiCallResult<String> result,
            Instant startedAt,
            String status,
            String errorCode) {
        Long modelId = resolveInvocationModelId(result.provider(), result.model());
        if (modelId == null) {
            return;
        }
        long inputTokens = usageLong(result.usage(), "prompt_tokens", "input_tokens");
        long outputTokens = usageLong(result.usage(), "completion_tokens", "output_tokens");
        String requestId = usageString(result.usage(), "request_id", "requestId");
        Instant completedAt = Instant.now();
        jdbc.update("""
                INSERT INTO model_invocation (
                    external_id, user_id, ai_run_id, async_job_id, model_definition_id,
                    model_policy_version_id, prompt_version_id, provider_request_id,
                    purpose, status, started_at, first_token_at, completed_at,
                    latency_ms, input_tokens, output_tokens, error_code
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'GENERAL_CHAT', ?, ?, ?, ?, ?, ?, ?, ?)
                """, crypto.newExternalId(), context.userId(), context.runId(), context.jobId(), modelId,
                context.modelPolicyVersionId(), context.promptVersionId(), requestId, status,
                Timestamp.from(startedAt), Timestamp.from(startedAt), Timestamp.from(completedAt),
                result.durationMs(), inputTokens, outputTokens, safeCode(errorCode));
    }

    private Long resolveInvocationModelId(String provider, String model) {
        List<Long> rows = jdbc.query("""
                SELECT definition.id
                  FROM model_definition definition
                  JOIN model_provider provider ON provider.id = definition.provider_id
                 WHERE provider.provider_key = ? AND definition.provider_model_name = ?
                 LIMIT 1
                """, (rs, rowNum) -> rs.getLong(1), provider, model);
        return rows.stream().findFirst().orElse(null);
    }

    private List<CitationView> mapCitationRows(String messageExternalId, long userId) {
        return jdbc.query("""
                SELECT citation.citation_no, a.external_id AS asset_external_id, a.name AS asset_name,
                       av.external_id AS version_external_id, chunk.external_id AS chunk_external_id,
                       citation.quoted_text, citation.locator_label, citation.support_score
                  FROM message_citation citation
                  JOIN message m ON m.id = citation.message_id
                  JOIN conversation c ON c.id = m.conversation_id
                  JOIN document_chunk chunk ON chunk.id = citation.document_chunk_id
                  JOIN asset_parse_result parse_result ON parse_result.id = chunk.parse_result_id
                  JOIN asset_version av ON av.id = parse_result.asset_version_id
                  JOIN asset a ON a.id = av.asset_id
                 WHERE m.external_id = ? AND c.user_id = ?
                 ORDER BY citation.citation_no ASC
                """, (rs, rowNum) -> new CitationView(
                        rs.getInt("citation_no"), rs.getString("asset_external_id"),
                        rs.getString("asset_name"), rs.getString("version_external_id"),
                        rs.getString("chunk_external_id"), rs.getString("quoted_text"),
                        rs.getString("locator_label"),
                        rs.getObject("support_score") == null ? null : rs.getDouble("support_score")),
                messageExternalId, userId);
    }

    private Map<String, List<CitationView>> loadCitations(long userId, String conversationExternalId) {
        List<String> messageIds = jdbc.query("""
                SELECT m.external_id
                  FROM message m
                  JOIN conversation c ON c.id = m.conversation_id
                 WHERE c.external_id = ? AND c.user_id = ? AND m.branch_id = c.active_branch_id
                """, (rs, rowNum) -> rs.getString(1), conversationExternalId, userId);
        Map<String, List<CitationView>> result = new LinkedHashMap<>();
        for (String messageId : messageIds) {
            List<CitationView> citations = mapCitationRows(messageId, userId);
            if (!citations.isEmpty()) {
                result.put(messageId, citations);
            }
        }
        return result;
    }

    private List<HistoryMessage> loadHistory(long branchId, long throughMessageId) {
        List<HistoryMessage> history = jdbc.query("""
                SELECT role, plain_text
                  FROM message
                 WHERE branch_id = ? AND id <= ? AND status = 'FINALIZED' AND plain_text IS NOT NULL
                 ORDER BY sequence_no DESC
                 LIMIT 20
                """, (rs, rowNum) -> new HistoryMessage(
                        rs.getString("role").toLowerCase(), rs.getString("plain_text")),
                branchId, throughMessageId);
        Collections.reverse(history);
        return List.copyOf(history);
    }

    private List<String> loadDirectVersionExternalIds(long requestMessageId) {
        return jdbc.query("""
                SELECT av.external_id
                  FROM message_attachment attachment
                  JOIN asset_version av ON av.id = attachment.asset_version_id
                 WHERE attachment.message_id = ? AND attachment.attachment_role = 'CONTEXT'
                 ORDER BY attachment.id ASC
                """, (rs, rowNum) -> rs.getString(1), requestMessageId);
    }

    private List<LockedAssetVersion> resolveDirectSources(long userId, List<String> requestedIds) {
        if (requestedIds == null || requestedIds.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String value : requestedIds) {
            if (value == null || value.isBlank()) {
                throw badRequest("INVALID_SOURCE_SELECTION", "资料选择无效。");
            }
            unique.add(value.trim());
        }
        if (unique.size() > 20) {
            throw badRequest("SOURCE_LIMIT_EXCEEDED", "单个对话最多额外关联 20 个资料。");
        }
        String placeholders = placeholders(unique.size());
        List<Object> args = new ArrayList<>();
        args.add(userId);
        args.addAll(unique);
        List<LockedAssetVersion> rows = jdbc.query("""
                SELECT a.external_id AS asset_external_id, a.name,
                       av.id AS version_id, av.external_id AS version_external_id
                  FROM asset a
                  JOIN asset_version av ON av.id = a.current_version_id
                  JOIN asset_parse_result parse_result ON parse_result.id = av.active_parse_result_id
                 WHERE a.user_id = ? AND a.status = 'ACTIVE'
                   AND av.status = 'READY' AND parse_result.status = 'READY'
                   AND a.external_id IN (%s)
                """.formatted(placeholders), (rs, rowNum) -> new LockedAssetVersion(
                        rs.getString("asset_external_id"), rs.getString("name"),
                        rs.getLong("version_id"), rs.getString("version_external_id")),
                args.toArray());
        if (rows.size() != unique.size()) {
            throw conflict("SOURCE_NOT_READY", "所选资料不存在、无权访问或尚未完成解析。");
        }
        Map<String, LockedAssetVersion> byExternalId = new LinkedHashMap<>();
        rows.forEach(row -> byExternalId.put(row.assetExternalId(), row));
        return unique.stream().map(byExternalId::get).toList();
    }

    private void validateKnowledgeBaseReadiness(long userId, Long knowledgeBaseId) {
        if (knowledgeBaseId == null) {
            return;
        }
        List<SourceReadiness> rows = jdbc.query("""
                SELECT COUNT(*) AS total,
                       SUM(CASE WHEN a.status = 'ACTIVE'
                                     AND av.status = 'READY'
                                     AND parse_result.status = 'READY'
                                THEN 1 ELSE 0 END) AS ready_count
                  FROM knowledge_base_asset membership
                  JOIN knowledge_base kb ON kb.id = membership.knowledge_base_id
                  JOIN asset a ON a.id = membership.asset_id
                  LEFT JOIN asset_version av ON av.id = a.current_version_id
                  LEFT JOIN asset_parse_result parse_result ON parse_result.id = av.active_parse_result_id
                 WHERE membership.knowledge_base_id = ? AND kb.user_id = ? AND kb.status = 'ACTIVE'
                """, (rs, rowNum) -> new SourceReadiness(rs.getLong("total"), rs.getLong("ready_count")),
                knowledgeBaseId, userId);
        SourceReadiness readiness = rows.get(0);
        if (readiness.total() == 0) {
            throw conflict("SOURCE_SET_EMPTY", "关联的知识库暂无可用资料，请先添加并完成解析。");
        }
        if (readiness.ready() != readiness.total()) {
            throw conflict("SOURCE_NOT_READY", "知识库中仍有资料未完成解析，请稍后再试或移除该资料。");
        }
    }

    private Long resolveKnowledgeBaseId(long userId, String externalId, boolean allowNull) {
        if (externalId == null || externalId.isBlank()) {
            if (allowNull) {
                return null;
            }
            return null;
        }
        List<Long> rows = jdbc.query("""
                SELECT id FROM knowledge_base
                 WHERE external_id = ? AND user_id = ? AND status = 'ACTIVE'
                """, (rs, rowNum) -> rs.getLong(1), externalId.trim(), userId);
        if (rows.isEmpty()) {
            throw notFound("KNOWLEDGE_BASE_NOT_FOUND", "知识库不存在或不可用。");
        }
        return rows.get(0);
    }

    private RuntimeDefinition requireRuntimeDefinition() {
        List<RuntimeDefinition> rows = jdbc.query("""
                SELECT capability.id AS capability_id,
                       policy.id AS policy_id, prompt.id AS prompt_id
                  FROM capability_definition capability
                  JOIN model_policy_version policy
                    ON policy.policy_key = 'general-chat' AND policy.status = 'ACTIVE'
                  JOIN prompt_template template
                    ON template.prompt_key = 'general-chat' AND template.status = 'ACTIVE'
                  JOIN prompt_version prompt
                    ON prompt.id = template.current_version_id AND prompt.status = 'PUBLISHED'
                 WHERE capability.capability_key = 'general.chat'
                   AND capability.status IN ('BETA', 'AVAILABLE')
                 ORDER BY policy.version_no DESC, prompt.version_no DESC
                 LIMIT 1
                """, (rs, rowNum) -> new RuntimeDefinition(
                        rs.getLong("capability_id"), rs.getLong("policy_id"), rs.getLong("prompt_id")));
        if (rows.isEmpty()) {
            throw new ChatV2ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "CHAT_RUNTIME_UNAVAILABLE", "对话能力暂不可用，请稍后再试。");
        }
        return rows.get(0);
    }

    private ConversationRow requireConversationForUpdate(long userId, String externalId) {
        List<ConversationRow> rows = jdbc.query("""
                SELECT c.id, c.external_id, c.title, c.status, c.knowledge_base_id,
                       kb.external_id AS knowledge_base_external_id,
                       c.active_branch_id, branch.active_run_id
                  FROM conversation c
                  LEFT JOIN knowledge_base kb ON kb.id = c.knowledge_base_id
                  LEFT JOIN conversation_branch branch ON branch.id = c.active_branch_id
                 WHERE c.external_id = ? AND c.user_id = ? AND c.conversation_type = 'GENERAL'
                 FOR UPDATE
                """, (rs, rowNum) -> new ConversationRow(
                        rs.getLong("id"), rs.getString("external_id"), rs.getString("title"),
                        rs.getString("status"), nullableLong(rs, "knowledge_base_id"),
                        rs.getString("knowledge_base_external_id"),
                        nullableLong(rs, "active_branch_id"), nullableLong(rs, "active_run_id")),
                externalId, userId);
        if (rows.isEmpty()) {
            throw notFound("CONVERSATION_NOT_FOUND", "对话不存在或不可用。");
        }
        return rows.get(0);
    }

    private BranchRow requireActiveBranchForUpdate(long conversationId, Long branchId) {
        if (branchId == null) {
            throw conflict("CONVERSATION_STATE_INVALID", "对话状态异常，请刷新后重试。");
        }
        List<BranchRow> rows = jdbc.query("""
                SELECT id, external_id
                  FROM conversation_branch
                 WHERE id = ? AND conversation_id = ? AND status = 'ACTIVE'
                 FOR UPDATE
                """, (rs, rowNum) -> new BranchRow(rs.getLong("id"), rs.getString("external_id")),
                branchId, conversationId);
        if (rows.isEmpty()) {
            throw conflict("CONVERSATION_STATE_INVALID", "对话状态异常，请刷新后重试。");
        }
        return rows.get(0);
    }

    private MessageCursor lastMessageCursor(long branchId) {
        List<MessageCursor> rows = jdbc.query("""
                SELECT id, sequence_no FROM message
                 WHERE branch_id = ? ORDER BY sequence_no DESC LIMIT 1
                """, (rs, rowNum) -> new MessageCursor(rs.getLong("id"), rs.getLong("sequence_no")), branchId);
        return rows.isEmpty() ? new MessageCursor(null, 0) : rows.get(0);
    }

    private ConversationSummary requireConversationSummary(long userId, String externalId) {
        List<ConversationSummary> rows = jdbc.query("""
                SELECT c.external_id, c.title, c.conversation_type, c.status,
                       kb.external_id AS knowledge_base_external_id,
                       branch.external_id AS active_branch_external_id,
                       (SELECT COUNT(*) FROM message m WHERE m.conversation_id = c.id) AS message_count,
                       c.row_version, c.last_message_at, c.created_at, c.updated_at
                  FROM conversation c
                  LEFT JOIN knowledge_base kb ON kb.id = c.knowledge_base_id
                  LEFT JOIN conversation_branch branch ON branch.id = c.active_branch_id
                 WHERE c.external_id = ? AND c.user_id = ?
                   AND c.conversation_type = 'GENERAL' AND c.status <> 'PURGED'
                """, (rs, rowNum) -> mapConversationSummary(rs), externalId, userId);
        if (rows.isEmpty()) {
            throw notFound("CONVERSATION_NOT_FOUND", "对话不存在或不可用。");
        }
        return rows.get(0);
    }

    private ConversationSummary mapConversationSummary(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ConversationSummary(
                rs.getString("external_id"), rs.getString("title"),
                rs.getString("conversation_type"), rs.getString("status"),
                rs.getString("knowledge_base_external_id"), rs.getString("active_branch_external_id"),
                rs.getLong("message_count"), rs.getLong("row_version"),
                instant(rs.getTimestamp("last_message_at")), instant(rs.getTimestamp("created_at")),
                instant(rs.getTimestamp("updated_at")));
    }

    private long insertAndReturnId(String sql, Object... args) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int index = 0; index < args.length; index++) {
                statement.setObject(index + 1, args[index]);
            }
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Database did not return a generated key");
        }
        return key.longValue();
    }

    private String normalizedTitle(String requestedTitle) {
        if (requestedTitle == null || requestedTitle.isBlank()) {
            return DEFAULT_TITLE;
        }
        String title = requestedTitle.trim();
        if (title.length() > 160 || title.chars().anyMatch(Character::isISOControl)) {
            throw badRequest("INVALID_TITLE", "对话标题格式不正确。");
        }
        return title;
    }

    private String titleFromMessage(String content) {
        String oneLine = content.trim().replaceAll("\\s+", " ");
        return oneLine.length() <= 40 ? oneLine : oneLine.substring(0, 40);
    }

    private String normalizeIdempotencyKey(String value) {
        if (value == null || value.isBlank()) {
            return crypto.newExternalId();
        }
        String key = value.trim();
        if (key.length() > 128 || key.chars().anyMatch(Character::isISOControl)) {
            throw badRequest("INVALID_IDEMPOTENCY_KEY", "幂等键格式不正确。");
        }
        return key;
    }

    private String idempotencyScope(String conversationExternalId) {
        return "chat:" + conversationExternalId;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize chat runtime state", exception);
        }
    }

    private String sha256(byte[] value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to calculate SHA-256", exception);
        }
    }

    private String retrievalMode(List<Source> sources) {
        boolean semantic = sources.stream().anyMatch(source -> source.mode() == RetrievalModels.Mode.SEMANTIC);
        boolean keyword = sources.stream().anyMatch(source -> source.mode() == RetrievalModels.Mode.KEYWORD);
        if (semantic && keyword) return "HYBRID";
        if (semantic) return "SEMANTIC";
        return "KEYWORD";
    }

    private double normalizedScore(double score) {
        return Math.max(0d, Math.min(1d, score));
    }

    private String placeholders(int size) {
        return String.join(",", Collections.nCopies(size, "?"));
    }

    private Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private Long nullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private String workerId() {
        return "chat-v2-" + Thread.currentThread().getId();
    }

    private long usageLong(Map<String, Object> usage, String... keys) {
        for (String key : keys) {
            Object value = usage.get(key);
            if (value instanceof Number number) {
                return Math.max(0, number.longValue());
            }
        }
        return 0;
    }

    private String usageString(Map<String, Object> usage, String... keys) {
        for (String key : keys) {
            Object value = usage.get(key);
            if (value != null && !value.toString().isBlank()) {
                return truncate(value.toString(), 255);
            }
        }
        return null;
    }

    private String safeCode(String code) {
        return code == null ? null : truncate(code.replaceAll("[^A-Za-z0-9_.-]", "_"), 64);
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private ChatV2ApiException badRequest(String code, String message) {
        return new ChatV2ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    private ChatV2ApiException notFound(String code, String message) {
        return new ChatV2ApiException(HttpStatus.NOT_FOUND, code, message);
    }

    private ChatV2ApiException conflict(String code, String message) {
        return new ChatV2ApiException(HttpStatus.CONFLICT, code, message);
    }

    public record PreparedRun(
            long runId,
            String runExternalId,
            long jobId,
            String jobExternalId,
            long userId,
            long conversationId,
            String conversationExternalId,
            long branchId,
            String branchExternalId,
            long requestMessageId,
            String requestMessageExternalId,
            long responseMessageId,
            String responseMessageExternalId,
            String knowledgeBaseExternalId,
            List<String> directVersionExternalIds,
            RuntimeDefinition runtime,
            boolean replayed) {
    }

    public record RuntimeDefinition(
            long capabilityId,
            long modelPolicyVersionId,
            long promptVersionId) {
    }

    public record RunExecutionContext(
            long runId,
            String runExternalId,
            long userId,
            long jobId,
            String jobExternalId,
            long conversationId,
            String conversationExternalId,
            long branchId,
            String branchExternalId,
            long requestMessageId,
            String requestMessageExternalId,
            String requestText,
            long responseMessageId,
            String responseMessageExternalId,
            String knowledgeBaseExternalId,
            List<String> directVersionExternalIds,
            long modelPolicyVersionId,
            long promptVersionId,
            String systemPrompt,
            String developerPrompt,
            List<HistoryMessage> history) {
    }

    public record HistoryMessage(String role, String content) {
    }

    public record CitationSource(
            int number,
            String chunkExternalId,
            String quotedText,
            String locator,
            double score) {
    }

    private record ConversationRow(
            long id,
            String externalId,
            String title,
            String status,
            Long knowledgeBaseId,
            String knowledgeBaseExternalId,
            Long activeBranchId,
            Long activeRunId) {
    }

    private record BranchRow(long id, String externalId) {
    }

    private record MessageCursor(Long messageId, long sequence) {
    }

    private record LockedAssetVersion(
            String assetExternalId,
            String assetName,
            long assetVersionId,
            String versionExternalId) {
    }

    private record SourceReadiness(long total, long ready) {
    }
}
