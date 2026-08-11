package com.example.llm.service.impl;

import com.alibaba.dashscope.common.Role;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.llm.dto.ChatReq;
import com.example.llm.dto.MindMapGenerateReq;
import com.example.llm.entity.Conversation;
import com.example.llm.entity.Document;
import com.example.llm.entity.KnowledgeBase;
import com.example.llm.entity.MediaAsset;
import com.example.llm.entity.Presentation;
import com.example.llm.mapper.ConversationMapper;
import com.example.llm.mapper.KnowledgeBaseMapper;
import com.example.llm.mapper.MediaAssetMapper;
import com.example.llm.mapper.MessageMapper;
import com.example.llm.mapper.PresentationMapper;
import com.example.llm.integration.ai.AiCallResult;
import com.example.llm.integration.ai.AiCapabilityRouter;
import com.example.llm.integration.ai.AiChatMessage;
import com.example.llm.integration.ai.AiOperationGuard;
import com.example.llm.integration.ai.ProviderCallException;
import com.example.llm.asset.retrieval.AssetRetrievalService;
import com.example.llm.asset.retrieval.RetrievalModels;
import com.example.llm.service.ChatService;
import com.example.llm.service.MindMapGenerateService;
import com.example.llm.service.SystemConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.time.Duration;
import java.io.IOException;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import java.util.concurrent.Executors;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;

@Slf4j
@Service
public class ChatServiceImpl implements ChatService {

    @Autowired
    private ConversationMapper conversationMapper;

    @Autowired
    private MessageMapper messageMapper;

    // Generated artifacts still use the existing document persistence path until
    // the V2 generated-asset writer is introduced.
    @Autowired
    private com.example.llm.mapper.DocumentMapper documentMapper;

    @Autowired
    private SystemConfigService systemConfigService;

    @Autowired
    private AssetRetrievalService assetRetrievalService;

    @Autowired
    private AiCapabilityRouter aiRouter;

    @Autowired
    private AiOperationGuard aiOperationGuard;

    @Autowired
    private MediaAssetMapper mediaAssetMapper;

    @Autowired
    private KnowledgeBaseMapper knowledgeBaseMapper;

    @Autowired
    private PresentationMapper presentationMapper;

    @Autowired
    private MindMapGenerateService mindMapGenerateService;

    @Value("${upload.path}")
    private String uploadPath;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    @Override
    public SseEmitter chat(Long userId, ChatReq req) {
        log.info("Chat request received: userId={}, conversationId={}", userId, req.getConversationId());
        if (userId == null) {
            throw new IllegalArgumentException("用户未登录");
        }

        // 兼容前端发送的 message 字段
        String question = req.getEffectiveQuestion();
        if (question == null || question.trim().isEmpty()) {
            // 如果是重新生成，允许 question 为空，这里给个默认值防止 NPE
            if (Boolean.TRUE.equals(req.getIsRegenerate())) {
                question = "";
                req.setQuestion(question);
            } else {
                throw new IllegalArgumentException("问题不能为空");
            }
        }
        req.setQuestion(question);

        Conversation conversation;

        // 如果 conversationId 为 null，创建新对话
        if (req.getConversationId() == null) {
            Conversation newConversation = new Conversation();
            newConversation.setUserId(userId);
            newConversation.setTitle(question.length() > 20 ? question.substring(0, 20) + "..." : question);
            newConversation.setMessageCount(0);
            newConversation.setCreateTime(LocalDateTime.now());
            newConversation.setUpdateTime(LocalDateTime.now());
            conversationMapper.insert(newConversation);
            req.setConversationId(newConversation.getId());
            conversation = newConversation;
        } else {
            conversation = conversationMapper.selectById(req.getConversationId());
            if (conversation == null) {
                throw new IllegalArgumentException("对话不存在");
            }
            if (!userId.equals(conversation.getUserId())) {
                throw new IllegalArgumentException("无权访问该对话");
            }
        }

        SseEmitter emitter = new SseEmitter(0L); // no timeout
        
        executorService.submit(() -> {
            try {
                log.info("Starting chat for userId: {}, conversationId: {}, isRegenerate: {}", userId, req.getConversationId(), req.getIsRegenerate());
                
                Long parentMsgId = req.getParentId();
                com.example.llm.entity.Message currentUserMsg = null;

                // 处理重新生成或编辑问题
                if (Boolean.TRUE.equals(req.getIsRegenerate())) {
                    if (parentMsgId != null) {
                        currentUserMsg = messageMapper.selectById(parentMsgId);
                    } else {
                        // 兼容旧逻辑，找最后一个用户消息
                        LambdaQueryWrapper<com.example.llm.entity.Message> lastUserMsgWrapper = new LambdaQueryWrapper<>();
                        lastUserMsgWrapper.eq(com.example.llm.entity.Message::getConversationId, conversation.getId())
                                      .eq(com.example.llm.entity.Message::getRole, Role.USER.getValue())
                                      .eq(com.example.llm.entity.Message::getStatus, 0)
                                      .orderByDesc(com.example.llm.entity.Message::getCreateTime)
                                      .last("limit 1");
                        currentUserMsg = messageMapper.selectOne(lastUserMsgWrapper);
                    }
                    if (currentUserMsg != null && (req.getQuestion() == null || req.getQuestion().isEmpty())) {
                        req.setQuestion(currentUserMsg.getContent());
                    }
                    if (currentUserMsg != null) {
                        if (req.getTurnId() == null) {
                            req.setTurnId(currentUserMsg.getTurnId());
                        }
                        if (req.getQVersion() == null) {
                            req.setQVersion(currentUserMsg.getQVersion());
                        }
                        if (req.getFiles() == null) {
                            req.setFiles(currentUserMsg.getFiles());
                        }
                    }
                } else if (req.getEditMsgId() != null && req.getEditMsgId() > 0) {
                    // 编辑问题，基于被编辑消息的父节点创建一个新的分支
                    com.example.llm.entity.Message editMsg = messageMapper.selectById(req.getEditMsgId());
                    Long newParentId = req.getParentId() != null ? req.getParentId() : (editMsg != null ? editMsg.getParentId() : null);
                    
                    currentUserMsg = new com.example.llm.entity.Message();
                    currentUserMsg.setConversationId(conversation.getId());
                    currentUserMsg.setParentId(newParentId);
                    currentUserMsg.setRole(Role.USER.getValue());
                    currentUserMsg.setContent(req.getQuestion());
                    currentUserMsg.setStatus(0);
                    currentUserMsg.setCreateTime(LocalDateTime.now());
                    
                    String targetTurnId = req.getTurnId() != null ? req.getTurnId() : (editMsg != null && editMsg.getTurnId() != null ? editMsg.getTurnId() : generateTurnId());
                    Integer targetQVersion = req.getQVersion() != null ? req.getQVersion() : (editMsg != null && editMsg.getQVersion() != null ? editMsg.getQVersion() + 1 : 0);
                    
                    currentUserMsg.setTurnId(targetTurnId);
                    currentUserMsg.setQVersion(targetQVersion);
                    currentUserMsg.setAVersion(req.getAVersion() != null ? req.getAVersion() : 0);
                    currentUserMsg.setFiles(req.getFiles());
                    
                    messageMapper.insert(currentUserMsg);
                } else {
                    // 1. 保存用户提问 (正常提问)
                    if (parentMsgId == null) {
                        // 兼容旧逻辑，找到最后一条AI消息作为父节点
                        LambdaQueryWrapper<com.example.llm.entity.Message> lastMsgWrapper = new LambdaQueryWrapper<>();
                        lastMsgWrapper.eq(com.example.llm.entity.Message::getConversationId, conversation.getId())
                                      .eq(com.example.llm.entity.Message::getRole, Role.ASSISTANT.getValue())
                                      .eq(com.example.llm.entity.Message::getStatus, 0)
                                      .orderByDesc(com.example.llm.entity.Message::getCreateTime)
                                      .last("limit 1");
                        com.example.llm.entity.Message lastMsg = messageMapper.selectOne(lastMsgWrapper);
                        if (lastMsg != null) {
                            parentMsgId = lastMsg.getId();
                        }
                    }
                    
                    currentUserMsg = new com.example.llm.entity.Message();
                    currentUserMsg.setConversationId(conversation.getId());
                    currentUserMsg.setParentId(parentMsgId);
                    currentUserMsg.setRole(Role.USER.getValue());
                    currentUserMsg.setContent(req.getQuestion());
                    currentUserMsg.setStatus(0);
                    currentUserMsg.setCreateTime(LocalDateTime.now());
                    
                    currentUserMsg.setTurnId(req.getTurnId() != null ? req.getTurnId() : generateTurnId());
                    currentUserMsg.setQVersion(req.getQVersion() != null ? req.getQVersion() : 0);
                    currentUserMsg.setAVersion(req.getAVersion() != null ? req.getAVersion() : 0);
                    currentUserMsg.setFiles(req.getFiles());
                    
                    messageMapper.insert(currentUserMsg);
                }

                // 2. RAG 检索
                if (isImageGeneration(req)) {
                    handleImageGeneration(userId, conversation, currentUserMsg, req, emitter);
                    return;
                }
                if (isPresentationGeneration(req)) {
                    handlePresentationGeneration(userId, conversation, currentUserMsg, req, emitter);
                    return;
                }
                if (isMindMapGeneration(req)) {
                    handleMindMapGeneration(userId, conversation, currentUserMsg, req, emitter);
                    return;
                }

                boolean hasSelectedSources = hasSelectedSources(req);
                String context = "";
                String referenceDocs = "[]";
                if (hasSelectedSources && req.getQuestion() != null && !req.getQuestion().isEmpty()) {
                    int topK = systemConfigService.getIntConfig("rag.top_k", 3);
                    RetrievalModels.Bundle retrieval = assetRetrievalService.retrieve(
                            userId,
                            new RetrievalModels.Request(
                                    req.getQuestion(),
                                    RetrievalModels.Scope.explicitSources(
                                            req.getKnowledgeBaseExternalId(),
                                            req.getSourceAssetExternalIds()),
                                    topK,
                                    null));
                    if (retrieval.status() == RetrievalModels.Status.DISABLED) {
                        throw new IllegalStateException("资料检索服务暂时不可用，请稍后重试");
                    }
                    if ("NO_RETRIEVABLE_CHUNKS".equals(retrieval.degradationCode())) {
                        throw new IllegalStateException("所选资料仍在解析或索引中，请处理完成后再提问");
                    }
                    if (!retrieval.sources().isEmpty()) {
                        context = retrieval.contextJson();
                        referenceDocs = objectMapper.writeValueAsString(retrieval.sources());
                    } else {
                        context = "所选资料中没有检索到能够支持本次问题的内容。"
                                + "可以使用通用知识回答，但必须明确说明本回答未引用所选资料，且不得伪造引用。";
                    }
                }

                // 3. 构建 Prompt
                String promptKey = hasSelectedSources ? "prompt.system.rag" : "prompt.system.general";
                String defaultPrompt = hasSelectedSources
                        ? "你是一个智能助手。优先根据用户明确选择的资料回答。"
                        + "只有确实使用了标有 S1、S2 等编号的来源时才能给出相应引用；不得编造来源。"
                        : "你是一个智能助手。";
                String systemPrompt = systemConfigService.getConfig(promptKey, defaultPrompt);

                if (!context.isEmpty()) {
                    String contextTemplate = systemConfigService.getConfig("prompt.context.template", "参考资料：\n{context}\n\n");
                    String contextStr = contextTemplate.replace("{context}", context);
                    systemPrompt = systemPrompt + "\n" + contextStr;
                }

                if (req.getFileContext() != null && !req.getFileContext().isEmpty()) {
                    systemPrompt = systemPrompt + "\n用户上传了以下文件内容作为参考：\n" + req.getFileContext();
                }

                String mediaContext = buildMediaContext(userId, conversation.getId(), req.getMediaAssetIds());
                if (!mediaContext.isBlank()) {
                    systemPrompt = systemPrompt + "\n用户上传媒体的识别结果：\n" + mediaContext;
                }

                List<AiChatMessage> messages = new ArrayList<>();
                messages.add(new AiChatMessage(Role.SYSTEM.getValue(), systemPrompt));

                // 携带多轮对话上下文 (溯源父节点)
                int maxRounds = systemConfigService.getIntConfig("chat.max_rounds", 5);
                int limit = maxRounds * 2;
                
                if (req.getHistory() != null && !req.getHistory().isEmpty()) {
                    // 使用前端传来的历史记录
                    for (ChatReq.MessageDto m : req.getHistory()) {
                        messages.add(new AiChatMessage(m.getRole(), m.getContent()));
                    }
                } else {
                    List<com.example.llm.entity.Message> historyMsg = new ArrayList<>();
                    if (currentUserMsg != null && currentUserMsg.getId() != null) {
                        try {
                            historyMsg = messageMapper.getMessageHistoryRecursive(currentUserMsg.getId(), limit);
                        } catch (Exception e) {
                            log.warn("Failed to execute recursive query for chat history, falling back to simple loop", e);
                            // 降级策略，万一不是 MySQL 8+ 或语法不支持
                            Long currId = currentUserMsg.getId();
                            while (currId != null && historyMsg.size() < limit) {
                                com.example.llm.entity.Message m = messageMapper.selectById(currId);
                                if (m == null || m.getStatus() != 0) break;
                                historyMsg.add(m);
                                currId = m.getParentId();
                            }
                        }
                    }
                    
                    java.util.Collections.reverse(historyMsg);
                    for (com.example.llm.entity.Message hm : historyMsg) {
                        messages.add(new AiChatMessage(hm.getRole(), hm.getContent()));
                    }
                }

                StringBuilder fullResponse = new StringBuilder();
                AiCallResult<String> generation = aiRouter.streamChat(messages, userId, delta -> {
                    try {
                        emitter.send(SseEmitter.event().data(delta));
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
                fullResponse.append(generation.value());

                // 5. 保存 AI 回答
                com.example.llm.entity.Message assistantMsg = new com.example.llm.entity.Message();
                assistantMsg.setConversationId(conversation.getId());
                assistantMsg.setParentId(currentUserMsg != null ? currentUserMsg.getId() : null);
                assistantMsg.setRole(Role.ASSISTANT.getValue());
                assistantMsg.setContent(fullResponse.toString());
                assistantMsg.setSourceChunks(referenceDocs);
                assistantMsg.setModel(generation.routeKey());
                assistantMsg.setDurationMs((int) Math.min(Integer.MAX_VALUE, generation.durationMs()));
                assistantMsg.setStatus(0);
                assistantMsg.setCreateTime(LocalDateTime.now());
                
                assistantMsg.setTurnId(req.getTurnId() != null ? req.getTurnId() : (currentUserMsg != null ? currentUserMsg.getTurnId() : generateTurnId()));
                assistantMsg.setQVersion(req.getQVersion() != null ? req.getQVersion() : 0);
                assistantMsg.setAVersion(req.getAVersion() != null ? req.getAVersion() : 0);
                
                messageMapper.insert(assistantMsg);

                // 更新 conversation 统计
                int currentCount = conversation.getMessageCount() != null ? conversation.getMessageCount() : 0;
                boolean isFirstMessage = currentCount == 0;
                // 如果是重新生成，或者编辑消息，消息总数不应该+2，如果是重新生成只+1（生成了一个新的assistantMsg），编辑+2（生成了一个userMsg和一个assistantMsg）
                if (Boolean.TRUE.equals(req.getIsRegenerate())) {
                    conversation.setMessageCount(currentCount + 1);
                } else {
                    conversation.setMessageCount(currentCount + 2);
                }
                conversation.setUpdateTime(LocalDateTime.now());
                
                if (isFirstMessage && (conversation.getTitle() == null || conversation.getTitle().trim().isEmpty() || conversation.getTitle().equals("新对话"))) {
                    try {
                        String titlePrompt = "请根据用户的这句话，生成一个简短的对话标题（不超过10个字），只返回标题内容，不要有任何标点符号和其他废话：" + req.getQuestion();
                        String generatedTitle = aiRouter.completeText(
                                List.of(new AiChatMessage(Role.USER.getValue(), titlePrompt)), userId)
                                .value().trim();
                        if (!generatedTitle.isEmpty()) {
                            conversation.setTitle(generatedTitle);
                        }
                    } catch (Exception e) {
                        log.warn("Failed to generate title for conversation {}", conversation.getId(), e);
                        String fallbackTitle = req.getQuestion().length() > 10 ? req.getQuestion().substring(0, 10) : req.getQuestion();
                        conversation.setTitle(fallbackTitle);
                    }
                }
                
                conversationMapper.updateById(conversation);

                emitter.send(SseEmitter.event().name("finish").data(referenceDocs));
                emitter.complete();

            } catch (Exception e) {
                log.error("Chat error", e);
                try {
                    emitter.send(SseEmitter.event().name("error").data(safeChatErrorMessage(e)));
                } catch (Exception ex) {
                    log.error("Failed to send SSE error", ex);
                }
                emitter.complete();
            }
        });

        return emitter;
    }

    @Override
    public String generateTitle(String text) {
        try {
            String titlePrompt = "请根据用户的这句话，生成一个简短的对话标题（不超过10个字），只返回标题内容，不要有任何标点符号和其他废话：" + text;
            String generatedTitle = aiRouter.completeText(
                    List.of(new AiChatMessage(Role.USER.getValue(), titlePrompt)), 0L)
                    .value().trim();
            if (!generatedTitle.isEmpty()) {
                return generatedTitle;
            }
        } catch (Exception e) {
            log.warn("Failed to generate title for text: {}", text, e);
        }
        return text.length() > 10 ? text.substring(0, 10) : text;
    }

    private String generateTurnId() {
        return System.currentTimeMillis() + "_" + java.util.UUID.randomUUID().toString().substring(0, 8);
    }

    private String safeChatErrorMessage(Exception exception) {
        if (exception instanceof IllegalArgumentException) {
            return exception.getMessage();
        }
        if (exception instanceof ProviderCallException providerException) {
            return switch (providerException.category()) {
                case RATE_LIMITED -> "当前使用人数较多，请稍后重试";
                case QUOTA_EXHAUSTED -> "当前能力额度已用完，请稍后再试";
                case CONTENT_SAFETY -> "请求未通过内容安全检查，请调整内容后重试";
                case BAD_REQUEST, UNSUPPORTED_INPUT -> providerException.getMessage();
                case TIMEOUT, UNAVAILABLE, INTERRUPTED, INVALID_RESPONSE, AUTHENTICATION ->
                        "AI 服务暂时不可用，请稍后重试";
            };
        }
        String message = exception.getMessage();
        if ("资料检索服务暂时不可用，请稍后重试".equals(message)
                || "所选资料仍在解析或索引中，请处理完成后再提问".equals(message)) {
            return message;
        }
        return "服务暂时不可用，请稍后重试";
    }

    private boolean isImageGeneration(ChatReq req) {
        if ("image.create".equals(req.getClientAction())) return true;
        String question = req.getEffectiveQuestion();
        return question != null && question.matches("(?is).*(生成|绘制|制作|创建).{0,20}(图片|插图|海报|配图).*");
    }

    private boolean isPresentationGeneration(ChatReq req) {
        if ("presentation.create".equals(req.getClientAction())) return true;
        String question = req.getEffectiveQuestion();
        return question != null && question.matches("(?is).*(生成|制作|创建|做).{0,20}(PPT|ppt|幻灯片|演示文稿).*");
    }

    private boolean isMindMapGeneration(ChatReq req) {
        if ("mindmap.create".equals(req.getClientAction())) return true;
        String question = req.getEffectiveQuestion();
        return question != null && question.matches("(?is).*(生成|制作|创建|画|整理).{0,20}(思维导图|脑图|知识图谱).*");
    }

    private void handlePresentationGeneration(
            Long userId,
            Conversation conversation,
            com.example.llm.entity.Message currentUserMsg,
            ChatReq req,
            SseEmitter emitter) throws Exception {
        String topic = extractTopic(req.getEffectiveQuestion(), "PPT");
        boolean topicPending = topic.startsWith("待确定");
        String configTopic = topicPending ? "" : topic;
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("topic", configTopic);
        config.put("title", configTopic);
        config.put("pageCount", 8);
        config.put("templateId", "ink-focus");
        config.put("aspectRatio", "16:9");
        config.put("style", "academic");
        config.put("audience", "student");
        config.put("language", "zh-CN");

        LocalDateTime now = LocalDateTime.now();
        Presentation presentation = new Presentation();
        presentation.setId(java.util.UUID.randomUUID().toString());
        presentation.setUserId(userId);
        presentation.setStatus("draft");
        presentation.setConfigJson(objectMapper.writeValueAsString(config));
        presentation.setOutlineJson("[]");
        presentation.setPreviewJson("[]");
        presentation.setConversationId(conversation.getId());
        presentation.setKnowledgeBaseId(conversation.getKbId());
        presentation.setProjectId(req.getProjectId());
        presentation.setClientRequestId("chat:" + conversation.getId() + ":" + currentUserMsg.getId());
        presentation.setCreateTime(now);
        presentation.setUpdateTime(now);
        presentationMapper.insert(presentation);

        String content = "我先确认演示主题。你可以直接生成大纲，也可以进入完整配置。";
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("cardType", "presentation");
        card.put("view", "proposal");
        card.put("status", "draft");
        card.put("presentationId", presentation.getId());
        card.put("conversationId", conversation.getId());
        card.put("knowledgeBaseId", conversation.getKbId());
        card.put("projectId", req.getProjectId());
        card.put("config", config);

        com.example.llm.entity.Message assistantMsg = createStructuredAssistantMessage(
                conversation, currentUserMsg, req, content, "presentation");
        assistantMsg.setModel("presentation-workflow");
        assistantMsg.setPresentationData(objectMapper.writeValueAsString(card));
        messageMapper.insert(assistantMsg);

        card.put("sourceMessageId", assistantMsg.getId());
        assistantMsg.setPresentationData(objectMapper.writeValueAsString(card));
        messageMapper.updateById(assistantMsg);
        presentation.setSourceMessageId(String.valueOf(assistantMsg.getId()));
        presentation.setUpdateTime(LocalDateTime.now());
        presentationMapper.updateById(presentation);

        finishStructuredResponse(conversation, req, topicPending ? "待确定PPT主题" : topic,
                content, "presentation-card", card, emitter);
    }

    private void handleMindMapGeneration(
            Long userId,
            Conversation conversation,
            com.example.llm.entity.Message currentUserMsg,
            ChatReq req,
            SseEmitter emitter) throws Exception {
        String topic = extractTopic(req.getEffectiveQuestion(), "思维导图");
        MindMapGenerateReq generateReq = new MindMapGenerateReq();
        generateReq.setTitle(topic);
        generateReq.setContent(req.getEffectiveQuestion());
        generateReq.setKbId(conversation.getKbId());
        Map<String, Object> generated = mindMapGenerateService.generateFromAiContent(generateReq, userId);
        Long mindMapId = Long.valueOf(String.valueOf(generated.get("id")));
        String title = String.valueOf(generated.getOrDefault("title", topic));

        Map<String, Object> preview = new LinkedHashMap<>();
        preview.put("kind", "mindmap");
        preview.put("mindMap", generated.get("treeData"));
        preview.put("mindMapConfig", Map.of("theme", "classic", "layout", "logicalStructure"));

        Map<String, Object> artifact = new LinkedHashMap<>();
        artifact.put("artifactId", "mindmap:" + mindMapId);
        artifact.put("resourceId", "mindmap-" + mindMapId);
        artifact.put("conversationId", conversation.getId());
        artifact.put("projectId", req.getProjectId());
        artifact.put("knowledgeBaseId", conversation.getKbId());
        artifact.put("title", title);
        artifact.put("fileName", title + ".mindmap");
        artifact.put("fileType", "mindmap");
        artifact.put("format", "思维导图");
        artifact.put("mimeType", "application/vnd.examinsight.mindmap+json");
        artifact.put("status", "ready");
        artifact.put("progress", 100);
        artifact.put("preview", preview);
        artifact.put("editorRoute", "/mindmap/" + mindMapId);
        artifact.put("editable", true);

        String content = "思维导图已经生成，并已自动保存到资料库。";
        com.example.llm.entity.Message assistantMsg = createStructuredAssistantMessage(
                conversation, currentUserMsg, req, content, null);
        assistantMsg.setModel("qwen-mindmap");
        assistantMsg.setArtifacts(objectMapper.writeValueAsString(List.of(artifact)));
        messageMapper.insert(assistantMsg);
        artifact.put("sourceMessageId", assistantMsg.getId());
        assistantMsg.setArtifacts(objectMapper.writeValueAsString(List.of(artifact)));
        messageMapper.updateById(assistantMsg);

        finishStructuredResponse(conversation, req, title, content, "artifact", artifact, emitter);
    }

    private com.example.llm.entity.Message createStructuredAssistantMessage(
            Conversation conversation,
            com.example.llm.entity.Message currentUserMsg,
            ChatReq req,
            String content,
            String kind) {
        com.example.llm.entity.Message message = new com.example.llm.entity.Message();
        message.setConversationId(conversation.getId());
        message.setParentId(currentUserMsg != null ? currentUserMsg.getId() : null);
        message.setRole(Role.ASSISTANT.getValue());
        message.setContent(content);
        message.setKind(kind);
        message.setDurationMs(0);
        message.setStatus(0);
        message.setCreateTime(LocalDateTime.now());
        message.setTurnId(req.getTurnId() != null ? req.getTurnId()
                : currentUserMsg != null ? currentUserMsg.getTurnId() : generateTurnId());
        message.setQVersion(req.getQVersion() != null ? req.getQVersion() : 0);
        message.setAVersion(req.getAVersion() != null ? req.getAVersion() : 0);
        return message;
    }

    private void finishStructuredResponse(
            Conversation conversation,
            ChatReq req,
            String title,
            String content,
            String eventName,
            Map<String, Object> eventData,
            SseEmitter emitter) throws Exception {
        int currentCount = conversation.getMessageCount() == null ? 0 : conversation.getMessageCount();
        conversation.setMessageCount(currentCount + (Boolean.TRUE.equals(req.getIsRegenerate()) ? 1 : 2));
        conversation.setUpdateTime(LocalDateTime.now());
        if (currentCount == 0 || isPlaceholderTitle(conversation.getTitle())) {
            conversation.setTitle(title);
        }
        conversationMapper.updateById(conversation);
        emitter.send(SseEmitter.event().data(content));
        emitter.send(SseEmitter.event().name(eventName).data(objectMapper.writeValueAsString(eventData)));
        emitter.send(SseEmitter.event().name("finish").data("[]"));
        emitter.complete();
    }

    private void handleImageGeneration(
            Long userId,
            Conversation conversation,
            com.example.llm.entity.Message currentUserMsg,
            ChatReq req,
            SseEmitter emitter) throws Exception {
        String prompt = req.getEffectiveQuestion().trim();
        com.example.llm.entity.Message existing = findExistingGeneratedImage(
                conversation.getId(), currentUserMsg, req);
        if (existing != null) {
            resendGeneratedImage(existing, emitter);
            return;
        }

        String operationKey = imageOperationKey(userId, conversation.getId(), currentUserMsg, req);
        try (AiOperationGuard.Lease ignored = aiOperationGuard.acquire(operationKey, Duration.ofMinutes(5))) {
            existing = findExistingGeneratedImage(conversation.getId(), currentUserMsg, req);
            if (existing != null) {
                resendGeneratedImage(existing, emitter);
                return;
            }
            generateAndPersistImage(userId, conversation, currentUserMsg, req, emitter, prompt);
        }
    }

    private boolean hasSelectedSources(ChatReq req) {
        boolean hasKnowledgeBase = req.getKnowledgeBaseExternalId() != null
                && !req.getKnowledgeBaseExternalId().isBlank();
        List<String> assetIds = req.getSourceAssetExternalIds();
        boolean hasAssets = assetIds != null && !assetIds.isEmpty();
        if (assetIds != null && assetIds.size() > 20) {
            throw new IllegalArgumentException("单个对话最多可额外关联20份资料");
        }
        return hasKnowledgeBase || hasAssets;
    }

    private void generateAndPersistImage(
            Long userId,
            Conversation conversation,
            com.example.llm.entity.Message currentUserMsg,
            ChatReq req,
            SseEmitter emitter,
            String prompt) throws Exception {
        AiCallResult<byte[]> imageGeneration = aiRouter.generateImage(prompt, 1024, 1024);
        byte[] image = imageGeneration.value();
        String artifactId = "image:" + java.util.UUID.randomUUID();
        File directory = new File(uploadPath, "generated-images" + File.separator + userId);
        Files.createDirectories(directory.toPath());
        File target = new File(directory, artifactId.substring("image:".length()) + ".png");
        Files.write(target.toPath(), image);

        String title = imageTitle(prompt);
        Document document = new Document();
        document.setUserId(userId);
        document.setKbId(conversation.getKbId());
        document.setFileName(title + ".png");
        document.setFileType("image/png");
        document.setFileSize((long) image.length);
        document.setFilePath(target.getAbsolutePath());
        document.setExternalKey(artifactId);
        document.setCharCount(0);
        document.setChunkCount(0);
        document.setStatus(1);
        document.setCreateTime(LocalDateTime.now());
        document.setUpdateTime(LocalDateTime.now());
        documentMapper.insert(document);
        incrementKnowledgeBaseDocumentCount(conversation.getKbId());

        String content = "图片已经生成，并已自动保存到资料库。";
        Map<String, Object> artifact = new LinkedHashMap<>();
        artifact.put("artifactId", artifactId);
        artifact.put("resourceId", "doc-" + document.getId());
        artifact.put("conversationId", conversation.getId());
        artifact.put("projectId", req.getProjectId());
        artifact.put("knowledgeBaseId", conversation.getKbId());
        artifact.put("title", title);
        artifact.put("fileName", document.getFileName());
        artifact.put("fileType", "image");
        artifact.put("format", "PNG");
        artifact.put("mimeType", "image/png");
        artifact.put("sizeBytes", image.length);
        artifact.put("status", "ready");
        artifact.put("progress", 100);
        artifact.put("preview", Map.of(
                "kind", "image",
                "imageUrl", "/api/resources/doc-" + document.getId() + "/preview-file"));

        com.example.llm.entity.Message assistantMsg = new com.example.llm.entity.Message();
        assistantMsg.setConversationId(conversation.getId());
        assistantMsg.setParentId(currentUserMsg != null ? currentUserMsg.getId() : null);
        assistantMsg.setRole(Role.ASSISTANT.getValue());
        assistantMsg.setContent(content);
        assistantMsg.setKind("image");
        assistantMsg.setModel(imageGeneration.routeKey());
        assistantMsg.setDurationMs((int) Math.min(Integer.MAX_VALUE, imageGeneration.durationMs()));
        assistantMsg.setStatus(0);
        assistantMsg.setCreateTime(LocalDateTime.now());
        assistantMsg.setTurnId(req.getTurnId() != null ? req.getTurnId()
                : currentUserMsg != null ? currentUserMsg.getTurnId() : generateTurnId());
        assistantMsg.setQVersion(req.getQVersion() != null ? req.getQVersion() : 0);
        assistantMsg.setAVersion(req.getAVersion() != null ? req.getAVersion() : 0);
        assistantMsg.setArtifacts(objectMapper.writeValueAsString(List.of(artifact)));
        messageMapper.insert(assistantMsg);
        artifact.put("sourceMessageId", assistantMsg.getId());
        assistantMsg.setArtifacts(objectMapper.writeValueAsString(List.of(artifact)));
        messageMapper.updateById(assistantMsg);

        int currentCount = conversation.getMessageCount() == null ? 0 : conversation.getMessageCount();
        conversation.setMessageCount(currentCount + (Boolean.TRUE.equals(req.getIsRegenerate()) ? 1 : 2));
        conversation.setUpdateTime(LocalDateTime.now());
        if (currentCount == 0 || isPlaceholderTitle(conversation.getTitle())) {
            conversation.setTitle(title);
        }
        conversationMapper.updateById(conversation);

        emitter.send(SseEmitter.event().data(content));
        emitter.send(SseEmitter.event().name("artifact").data(objectMapper.writeValueAsString(artifact)));
        emitter.send(SseEmitter.event().name("finish").data("[]"));
        emitter.complete();
    }

    private com.example.llm.entity.Message findExistingGeneratedImage(
            Long conversationId,
            com.example.llm.entity.Message currentUserMsg,
            ChatReq req) {
        String turnId = req.getTurnId() != null
                ? req.getTurnId() : currentUserMsg != null ? currentUserMsg.getTurnId() : null;
        if (turnId == null || turnId.isBlank()) return null;
        int qVersion = req.getQVersion() != null ? req.getQVersion()
                : currentUserMsg != null && currentUserMsg.getQVersion() != null
                ? currentUserMsg.getQVersion() : 0;
        int aVersion = req.getAVersion() != null ? req.getAVersion() : 0;
        return messageMapper.selectOne(new LambdaQueryWrapper<com.example.llm.entity.Message>()
                .eq(com.example.llm.entity.Message::getConversationId, conversationId)
                .eq(com.example.llm.entity.Message::getRole, Role.ASSISTANT.getValue())
                .eq(com.example.llm.entity.Message::getStatus, 0)
                .eq(com.example.llm.entity.Message::getTurnId, turnId)
                .eq(com.example.llm.entity.Message::getQVersion, qVersion)
                .eq(com.example.llm.entity.Message::getAVersion, aVersion)
                .eq(com.example.llm.entity.Message::getKind, "image")
                .isNotNull(com.example.llm.entity.Message::getArtifacts)
                .orderByDesc(com.example.llm.entity.Message::getCreateTime)
                .last("LIMIT 1"));
    }

    private String imageOperationKey(
            Long userId,
            Long conversationId,
            com.example.llm.entity.Message currentUserMsg,
            ChatReq req) {
        String turnId = req.getTurnId() != null
                ? req.getTurnId() : currentUserMsg != null ? currentUserMsg.getTurnId() : generateTurnId();
        int qVersion = req.getQVersion() != null ? req.getQVersion()
                : currentUserMsg != null && currentUserMsg.getQVersion() != null
                ? currentUserMsg.getQVersion() : 0;
        int aVersion = req.getAVersion() != null ? req.getAVersion() : 0;
        return "image:" + userId + ":" + conversationId + ":" + turnId + ":" + qVersion + ":" + aVersion;
    }

    private void resendGeneratedImage(
            com.example.llm.entity.Message existing,
            SseEmitter emitter) throws Exception {
        emitter.send(SseEmitter.event().data(existing.getContent()));
        List<?> artifacts = objectMapper.readValue(existing.getArtifacts(), List.class);
        if (!artifacts.isEmpty()) {
            emitter.send(SseEmitter.event().name("artifact")
                    .data(objectMapper.writeValueAsString(artifacts.get(0))));
        }
        emitter.send(SseEmitter.event().name("finish").data("[]"));
        emitter.complete();
    }

    private String buildMediaContext(Long userId, Long conversationId, List<String> assetIds) {
        if (assetIds == null || assetIds.isEmpty()) return "";
        StringBuilder context = new StringBuilder();
        assetIds.stream().distinct().limit(3).forEach(assetId -> {
            try {
                MediaAsset asset = mediaAssetMapper.selectById(assetId);
                if (asset == null || !userId.equals(asset.getUserId())) return;
                if (asset.getConversationId() != null && !conversationId.equals(asset.getConversationId())) return;
                String recognized = asset.getTranscript();
                if ((recognized == null || recognized.isBlank()) && "image".equals(asset.getKind())) {
                    byte[] bytes = Files.readAllBytes(new File(asset.getFilePath()).toPath());
                    recognized = aiRouter.understand(
                            bytes,
                            asset.getMimeType(),
                            "请准确识别这张图片的内容；如果包含题目、公式或文字，请完整保留。回答时使用中文。")
                            .value();
                    asset.setTranscript(recognized);
                    asset.setStatus("ready");
                    asset.setUpdateTime(LocalDateTime.now());
                    mediaAssetMapper.updateById(asset);
                }
                if (recognized != null && !recognized.isBlank()) {
                    context.append('[').append(asset.getFileName()).append("]\n")
                            .append(recognized).append("\n\n");
                }
            } catch (Exception e) {
                log.warn("Failed to understand media asset {}", assetId, e);
            }
        });
        return context.toString().trim();
    }

    private void incrementKnowledgeBaseDocumentCount(Long knowledgeBaseId) {
        if (knowledgeBaseId == null) return;
        KnowledgeBase kb = knowledgeBaseMapper.selectById(knowledgeBaseId);
        if (kb == null) return;
        kb.setDocCount((kb.getDocCount() == null ? 0 : kb.getDocCount()) + 1);
        kb.setUpdateTime(LocalDateTime.now());
        knowledgeBaseMapper.updateById(kb);
    }

    private String imageTitle(String prompt) {
        String title = extractTopic(prompt, "图片").replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        if (title.isBlank()) title = "AI生成图片";
        return title.substring(0, Math.min(40, title.length()));
    }

    private String extractTopic(String input, String kind) {
        String value = input == null ? "" : input.trim();
        value = value.replaceFirst("(?is)^.*?(主题是|主题为|主题[:：]|内容是|内容为)\\s*", "");
        value = value.replaceFirst("(?is)^(请|帮我|麻烦)?\\s*(生成|绘制|制作|创建|做|画|整理)(一张|一个|一份)?\\s*", "");
        value = value.replaceFirst("(?is)^(PPT|ppt|幻灯片|演示文稿|图片|插图|海报|配图|思维导图|脑图|知识图谱)[，,。:：\\s]*", "");
        value = value.replaceAll("(?is)[。.!！?？]+$", "").trim();
        if (value.isBlank() || value.equalsIgnoreCase("生成" + kind)) {
            value = "待确定" + kind + "主题";
        }
        return value.substring(0, Math.min(40, value.length()));
    }

    private boolean isPlaceholderTitle(String title) {
        if (title == null || title.isBlank()) return true;
        String value = title.trim();
        return value.equals("新对话") || value.equals("PPT 创作") || value.equals("PPT创作")
                || value.equals("图片生成") || value.equals("思维导图");
    }
}
