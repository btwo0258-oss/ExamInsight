package com.example.llm.chatv2.service;

import com.example.llm.asset.retrieval.RetrievalModels.Source;
import com.example.llm.chatv2.agent.ControlledChatAgent;
import com.example.llm.chatv2.agent.ControlledChatAgent.AgentActivity;
import com.example.llm.chatv2.agent.ControlledChatAgent.AgentResult;
import com.example.llm.chatv2.repository.ChatV2Repository;
import com.example.llm.chatv2.repository.ChatV2Repository.CitationSource;
import com.example.llm.chatv2.repository.ChatV2Repository.PreparedRun;
import com.example.llm.chatv2.repository.ChatV2Repository.RunExecutionContext;
import com.example.llm.chatv2.stream.AiRunEventBus;
import com.example.llm.integration.ai.AiCallResult;
import com.example.llm.integration.ai.ProviderCallException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class ChatRunExecutor {
    private static final int RETRIEVAL_TOP_K = 8;
    private static final Pattern CITATION_PATTERN = Pattern.compile("\\[S(\\d{1,3})]", Pattern.CASE_INSENSITIVE);

    private final ChatV2Repository repository;
    private final ControlledChatAgent agent;
    private final AiRunEventBus eventBus;

    public ChatRunExecutor(
            ChatV2Repository repository,
            ControlledChatAgent agent,
            AiRunEventBus eventBus) {
        this.repository = repository;
        this.agent = agent;
        this.eventBus = eventBus;
    }

    @Async("chatV2TaskExecutor")
    public void execute(PreparedRun prepared) {
        RunExecutionContext context;
        try {
            context = repository.loadRunExecutionContext(prepared.userId(), prepared.runExternalId());
        } catch (RuntimeException exception) {
            log.error("Unable to load V2 chat run: runId={}", prepared.runExternalId(), exception);
            eventBus.publish(prepared.runExternalId(), "run.failed", Map.of(
                    "runId", prepared.runExternalId(),
                    "code", "RUN_CONTEXT_UNAVAILABLE",
                    "message", "生成任务初始化失败，请稍后重试。"));
            return;
        }

        if (!repository.markRunStarted(context)) {
            if (repository.cancellationRequested(context.jobId())) {
                repository.cancelRun(context);
                publishCancelled(context);
            }
            return;
        }

        eventBus.publish(context.runExternalId(), "run.accepted", Map.of(
                "runId", context.runExternalId(),
                "conversationId", context.conversationExternalId(),
                "requestMessageId", context.requestMessageExternalId(),
                "responseMessageId", context.responseMessageExternalId()));
        stage(context, "agent-planning", 1);

        Instant invocationStartedAt = Instant.now();
        AiCallResult<String> callResult = null;
        try {
            AtomicBoolean generatingPublished = new AtomicBoolean(false);
            AgentResult result = agent.execute(
                    context,
                    delta -> {
                        if (repository.cancellationRequested(context.jobId())) {
                            throw new ControlledChatAgent.AgentCancelledException();
                        }
                        if (delta == null || delta.isEmpty()) {
                            return;
                        }
                        if (generatingPublished.compareAndSet(false, true)) {
                            stage(context, "generating", 3);
                        }
                        eventBus.publish(context.runExternalId(), "message.delta", Map.of(
                                "runId", context.runExternalId(),
                                "messageId", context.responseMessageExternalId(),
                                "delta", delta));
                    },
                    () -> repository.cancellationRequested(context.jobId()),
                    activity -> publishAgentActivity(context, activity));
            callResult = result.callResult();
            recordRetrievalSafely(context, result);

            if (repository.cancellationRequested(context.jobId())) {
                repository.cancelRun(context);
                publishCancelled(context);
                return;
            }

            String answer = callResult.value();
            if (answer == null || answer.isBlank()) {
                throw new IllegalStateException("Agent returned an empty chat response");
            }
            stage(context, "persisting", 4);
            List<CitationSource> citations = citedSources(answer, result.sources());
            repository.completeRun(context, answer, citations, callResult, invocationStartedAt);
            if ("CANCELLED".equals(repository.getRun(context.userId(), context.runExternalId()).status())) {
                publishCancelled(context);
                return;
            }
            eventBus.publish(context.runExternalId(), "usage", Map.of(
                    "runId", context.runExternalId(),
                    "provider", callResult.provider(),
                    "model", callResult.model(),
                    "usage", callResult.usage(),
                    "durationMs", callResult.durationMs()));
            eventBus.publish(context.runExternalId(), "run.completed", Map.of(
                    "runId", context.runExternalId(),
                    "conversationId", context.conversationExternalId(),
                    "messageId", context.responseMessageExternalId(),
                    "toolCalls", result.totalToolCalls(),
                    "citationCount", citations.size()));
        } catch (ControlledChatAgent.AgentCancelledException exception) {
            repository.cancelRun(context);
            publishCancelled(context);
        } catch (ProviderCallException exception) {
            if (repository.cancellationRequested(context.jobId())) {
                repository.cancelRun(context);
                publishCancelled(context);
                return;
            }
            String safeMessage = safeProviderMessage(exception);
            repository.failRun(context, exception.code(), safeMessage, callResult, invocationStartedAt);
            eventBus.publish(context.runExternalId(), "run.failed", Map.of(
                    "runId", context.runExternalId(),
                    "code", exception.code(),
                    "message", safeMessage,
                    "retryable", exception.retryable()));
        } catch (RuntimeException exception) {
            log.error("V2 general chat run failed: runId={}", context.runExternalId(), exception);
            String message = "回答生成失败，请稍后重试。";
            repository.failRun(context, "CHAT_RUN_FAILED", message, callResult, invocationStartedAt);
            eventBus.publish(context.runExternalId(), "run.failed", Map.of(
                    "runId", context.runExternalId(),
                    "code", "CHAT_RUN_FAILED",
                    "message", message,
                    "retryable", true));
        }
    }

    private void publishAgentActivity(RunExecutionContext context, AgentActivity activity) {
        Map<String, Object> payload = new LinkedHashMap<>(activity.details());
        payload.put("runId", context.runExternalId());
        payload.put("stage", activity.stage());
        if ("artifact-created".equals(activity.stage())) {
            eventBus.publish(context.runExternalId(), "artifact.created", Map.copyOf(payload));
            return;
        }
        if ("retrieving".equals(activity.stage())) {
            repository.updateRunStage(context.jobId(), activity.stage(), 2);
        }
        eventBus.publish(context.runExternalId(), "run.stage_changed", Map.copyOf(payload));
    }

    private void recordRetrievalSafely(RunExecutionContext context, AgentResult result) {
        if (result.searchCalls() == 0) {
            return;
        }
        try {
            repository.recordRetrieval(context, result.sources(), result.degradationCodes(), RETRIEVAL_TOP_K);
        } catch (RuntimeException exception) {
            log.warn("Unable to persist V2 retrieval audit: runId={}", context.runExternalId(), exception);
        }
    }

    static List<CitationSource> citedSources(String answer, List<Source> sources) {
        Set<Integer> citedNumbers = new LinkedHashSet<>();
        Matcher matcher = CITATION_PATTERN.matcher(answer);
        while (matcher.find()) {
            citedNumbers.add(Integer.parseInt(matcher.group(1)));
        }
        List<CitationSource> citations = new ArrayList<>();
        for (Source source : sources) {
            if (citedNumbers.contains(source.citationNo())) {
                citations.add(new CitationSource(
                        source.citationNo(),
                        source.chunkExternalId(),
                        source.content(),
                        locator(source),
                        source.score()));
            }
        }
        return List.copyOf(citations);
    }

    private static String locator(Source source) {
        if (source.pageFrom() != null) {
            return source.pageFrom().equals(source.pageTo())
                    ? "第 " + source.pageFrom() + " 页"
                    : "第 " + source.pageFrom() + "–" + source.pageTo() + " 页";
        }
        if (source.headingPath() != null && !source.headingPath().isBlank()) {
            return source.headingPath();
        }
        return "片段 " + source.sequenceNo();
    }

    private void stage(RunExecutionContext context, String stage, long progress) {
        repository.updateRunStage(context.jobId(), stage, progress);
        eventBus.publish(context.runExternalId(), "run.stage_changed", Map.of(
                "runId", context.runExternalId(),
                "stage", stage,
                "progress", progress,
                "total", 4));
    }

    private void publishCancelled(RunExecutionContext context) {
        eventBus.publish(context.runExternalId(), "run.cancelled", Map.of(
                "runId", context.runExternalId(),
                "messageId", context.responseMessageExternalId()));
    }

    private String safeProviderMessage(ProviderCallException exception) {
        return switch (exception.category()) {
            case QUOTA_EXHAUSTED -> "模型免费额度已用尽，当前无法继续生成。";
            case RATE_LIMITED -> "模型请求较多，请稍后重试。";
            case CONTENT_SAFETY -> "本次内容未通过安全检查，请调整问题后重试。";
            case AUTHENTICATION -> "模型服务配置异常，请联系管理员。";
            default -> "模型服务暂时不可用，请稍后重试。";
        };
    }
}
