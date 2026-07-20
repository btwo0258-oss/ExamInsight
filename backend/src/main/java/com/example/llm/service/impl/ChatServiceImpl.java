package com.example.llm.service.impl;

import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.common.Message;
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
import com.example.llm.service.ChatService;
import com.example.llm.service.EmbeddingService;
import com.example.llm.service.EsService;
import com.example.llm.service.MindMapGenerateService;
import com.example.llm.service.SystemConfigService;
import com.example.llm.integration.xfyun.XfyunSparkClient;
import com.example.llm.integration.xfyun.XfyunImageClient;
import com.example.llm.integration.xfyun.XfyunVisionClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.reactivex.Flowable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
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
    
    @Autowired
    private com.example.llm.mapper.DocumentMapper documentMapper;

    @Autowired
    private EmbeddingService embeddingService;

    @Autowired
    private EsService esService;

    @Autowired
    private SystemConfigService systemConfigService;

    @Autowired
    private XfyunSparkClient xfyunSparkClient;

    @Autowired
    private XfyunImageClient xfyunImageClient;

    @Autowired
    private XfyunVisionClient xfyunVisionClient;

    @Autowired
    private MediaAssetMapper mediaAssetMapper;

    @Autowired
    private KnowledgeBaseMapper knowledgeBaseMapper;

    @Autowired
    private PresentationMapper presentationMapper;

    @Autowired
    private MindMapGenerateService mindMapGenerateService;

    @Value("${dashscope.api-key}")
    private String apiKey;

    @Value("${dashscope.chat.model:qwen-plus-2025-07-28}")
    private String dashScopeChatModel;

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

                String context = "";
                String referenceDocs = "[]";
                if (conversation.getKbId() != null && req.getQuestion() != null && !req.getQuestion().isEmpty()) {
                    int topK = systemConfigService.getIntConfig("rag.top_k", 3);
                    double minScore = systemConfigService.getDoubleConfig("rag.min_score", 0.5);

                    List<Double> vector = embeddingService.getQueryEmbedding(req.getQuestion());
                    List<Map<String, Object>> similarChunks = esService.searchSimilarChunks("knowledge_chunks", conversation.getKbId(), vector, topK, minScore);
                    
                    if (similarChunks != null && !similarChunks.isEmpty()) {
                        StringBuilder contextBuilder = new StringBuilder();
                        for (Map<String, Object> chunk : similarChunks) {
                            contextBuilder.append((String) chunk.get("content")).append("\n");
                            // Add docName to chunk by querying DocumentMapper
                            Object docIdObj = chunk.get("docId");
                            if (docIdObj != null) {
                                try {
                                    String docIdStr = String.valueOf(docIdObj);
                                    if (docIdStr.startsWith("[") && docIdStr.endsWith("]")) {
                                        docIdStr = docIdStr.substring(1, docIdStr.length() - 1);
                                    }
                                    docIdStr = docIdStr.replaceAll("[\"']", "").trim();
                                    if (docIdStr.contains(".")) {
                                        docIdStr = docIdStr.substring(0, docIdStr.indexOf('.'));
                                    }
                                    
                                    Long docId = Long.parseLong(docIdStr);
                                    com.example.llm.entity.Document doc = documentMapper.selectById(docId);
                                    if (doc != null && doc.getFileName() != null && !doc.getFileName().isEmpty()) {
                                        chunk.put("docName", doc.getFileName());
                                    } else {
                                        chunk.put("docName", "未知文档");
                                    }
                                } catch (Exception e) {
                                    log.warn("Failed to parse docId: {}", docIdObj, e);
                                    chunk.put("docName", "未知文档");
                                }
                            } else {
                                chunk.put("docName", "未知文档");
                            }
                        }
                        context = contextBuilder.toString();
                        referenceDocs = objectMapper.writeValueAsString(similarChunks);
                    }
                }

                // 3. 构建 Prompt
                String promptKey = conversation.getKbId() != null ? "prompt.system.rag" : "prompt.system.general";
                String defaultPrompt = conversation.getKbId() != null ? "你是一个智能助手。请根据提供的参考资料回答问题。" : "你是一个智能助手。";
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

                List<Message> messages = new ArrayList<>();
                Message systemMsg = Message.builder().role(Role.SYSTEM.getValue())
                        .content(systemPrompt)
                        .build();
                messages.add(systemMsg);

                // 携带多轮对话上下文 (溯源父节点)
                int maxRounds = systemConfigService.getIntConfig("chat.max_rounds", 5);
                int limit = maxRounds * 2;
                
                if (req.getHistory() != null && !req.getHistory().isEmpty()) {
                    // 使用前端传来的历史记录
                    for (ChatReq.MessageDto m : req.getHistory()) {
                        messages.add(Message.builder().role(m.getRole()).content(m.getContent()).build());
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
                        messages.add(Message.builder().role(hm.getRole()).content(hm.getContent()).build());
                    }
                }

                StringBuilder fullResponse = new StringBuilder();
                long startTime = System.currentTimeMillis();
                String selectedModel = resolveModel(req.getModel());

                if ("spark-x2".equals(selectedModel)) {
                    String sparkAnswer = xfyunSparkClient.stream(toSparkMessages(messages), userId, delta -> {
                        try {
                            emitter.send(SseEmitter.event().data(delta));
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
                    fullResponse.append(sparkAnswer);
                } else {
                    Generation gen = new Generation();
                    GenerationParam param = GenerationParam.builder()
                            .apiKey(apiKey)
                            .model(dashScopeChatModel)
                            .messages(messages)
                            .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                            .incrementalOutput(true)
                            .build();
                    Flowable<GenerationResult> resultFlowable = gen.streamCall(param);
                    resultFlowable.blockingForEach(message -> {
                        String delta = message.getOutput().getChoices().get(0).getMessage().getContent();
                        if (delta != null) {
                            fullResponse.append(delta);
                            emitter.send(SseEmitter.event().data(delta));
                        }
                    });
                }

                long responseTime = System.currentTimeMillis() - startTime;

                // 5. 保存 AI 回答
                com.example.llm.entity.Message assistantMsg = new com.example.llm.entity.Message();
                assistantMsg.setConversationId(conversation.getId());
                assistantMsg.setParentId(currentUserMsg != null ? currentUserMsg.getId() : null);
                assistantMsg.setRole(Role.ASSISTANT.getValue());
                assistantMsg.setContent(fullResponse.toString());
                assistantMsg.setSourceChunks(referenceDocs);
                assistantMsg.setModel(selectedModel);
                assistantMsg.setDurationMs((int) responseTime);
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
                        Generation titleGen = new Generation();
                        GenerationParam titleParam = GenerationParam.builder()
                                .apiKey(apiKey)
                                .model(dashScopeChatModel)
                                .messages(java.util.Collections.singletonList(Message.builder().role(Role.USER.getValue()).content(titlePrompt).build()))
                                .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                                .build();
                        GenerationResult titleResult = titleGen.call(titleParam);
                        String generatedTitle = titleResult.getOutput().getChoices().get(0).getMessage().getContent().trim();
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
                    emitter.send(SseEmitter.event().name("error").data(e.getMessage()));
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
            Generation titleGen = new Generation();
            GenerationParam titleParam = GenerationParam.builder()
                    .apiKey(apiKey)
                    .model(dashScopeChatModel)
                    .messages(java.util.Collections.singletonList(Message.builder().role(Role.USER.getValue()).content(titlePrompt).build()))
                    .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                    .build();
            GenerationResult titleResult = titleGen.call(titleParam);
            String generatedTitle = titleResult.getOutput().getChoices().get(0).getMessage().getContent().trim();
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

    private String resolveModel(String requestedModel) {
        if (requestedModel == null || requestedModel.isBlank()) return dashScopeChatModel;
        if ("spark-x2".equals(requestedModel) || "spark-x".equals(requestedModel)) return "spark-x2";
        if (requestedModel.equals(dashScopeChatModel) || "qwen-plus".equals(requestedModel)) {
            return dashScopeChatModel;
        }
        throw new IllegalArgumentException("不支持的对话模型: " + requestedModel);
    }

    private List<Map<String, String>> toSparkMessages(List<Message> messages) {
        List<Map<String, String>> result = new ArrayList<>();
        for (Message message : messages) {
            Map<String, String> item = new LinkedHashMap<>();
            item.put("role", message.getRole());
            item.put("content", String.valueOf(message.getContent()));
            result.add(item);
        }
        return result;
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
        long startTime = System.currentTimeMillis();
        String prompt = req.getEffectiveQuestion().trim();
        byte[] image = xfyunImageClient.generate(prompt, 1024, 1024);
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
        assistantMsg.setModel("xfyun-image-generation");
        assistantMsg.setDurationMs((int) (System.currentTimeMillis() - startTime));
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
                    recognized = xfyunVisionClient.understand(bytes,
                            "请准确识别这张图片的内容；如果包含题目、公式或文字，请完整保留。回答时使用中文。");
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
