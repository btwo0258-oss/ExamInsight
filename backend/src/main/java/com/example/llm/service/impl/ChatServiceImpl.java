package com.example.llm.service.impl;

import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.Role;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.llm.dto.ChatReq;
import com.example.llm.entity.Conversation;
import com.example.llm.mapper.ConversationMapper;
import com.example.llm.mapper.MessageMapper;
import com.example.llm.service.ChatService;
import com.example.llm.service.EmbeddingService;
import com.example.llm.service.EsService;
import com.example.llm.service.SystemConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.reactivex.Flowable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import java.util.concurrent.Executors;

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

    @Value("${dashscope.api-key}")
    private String apiKey;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    @Override
    public SseEmitter chat(Long userId, ChatReq req) {
        log.info("Chat request received: userId={}, conversationId={}", userId, req.getConversationId());
        if (userId == null) {
            throw new IllegalArgumentException("用户未登录");
        }
        if (req.getConversationId() == null) {
            throw new IllegalArgumentException("对话ID不能为空");
        }
        if (req.getQuestion() == null || req.getQuestion().trim().isEmpty()) {
            // 如果是重新生成，允许 question 为空，这里给个默认值防止 NPE
            if (Boolean.TRUE.equals(req.getIsRegenerate())) {
                req.setQuestion(""); 
            } else {
                throw new IllegalArgumentException("问题不能为空");
            }
        }

        Conversation conversation = conversationMapper.selectById(req.getConversationId());
        if (conversation == null) {
            throw new IllegalArgumentException("对话不存在");
        }
        if (!userId.equals(conversation.getUserId())) {
            throw new IllegalArgumentException("无权访问该对话");
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
                                      .eq(com.example.llm.entity.Message::getStatus, 1)
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
                    currentUserMsg.setStatus(1);
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
                                      .eq(com.example.llm.entity.Message::getStatus, 1)
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
                    currentUserMsg.setStatus(1);
                    currentUserMsg.setCreateTime(LocalDateTime.now());
                    
                    currentUserMsg.setTurnId(req.getTurnId() != null ? req.getTurnId() : generateTurnId());
                    currentUserMsg.setQVersion(req.getQVersion() != null ? req.getQVersion() : 0);
                    currentUserMsg.setAVersion(req.getAVersion() != null ? req.getAVersion() : 0);
                    currentUserMsg.setFiles(req.getFiles());
                    
                    messageMapper.insert(currentUserMsg);
                }

                // 2. RAG 检索
                String context = "";
                String referenceDocs = "[]";
                if (conversation.getKbId() != null && req.getQuestion() != null && !req.getQuestion().isEmpty()) {
                    int topK = systemConfigService.getIntConfig("rag.top_k", 3);
                    double minScore = systemConfigService.getDoubleConfig("rag.min_score", 0.5);

                    List<Double> vector = embeddingService.getEmbedding(req.getQuestion());
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
                                if (m == null || m.getStatus() != 1) break;
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

                // 4. 调用 DashScope 大模型 (qwen-plus)
                Generation gen = new Generation();
                GenerationParam param = GenerationParam.builder()
                        .apiKey(apiKey)
                        .model("qwen-plus")
                        .messages(messages)
                        .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                        .incrementalOutput(true) // 流式输出
                        .build();

                Flowable<GenerationResult> resultFlowable = gen.streamCall(param);
                
                StringBuilder fullResponse = new StringBuilder();
                long startTime = System.currentTimeMillis();

                resultFlowable.blockingForEach(message -> {
                    String delta = message.getOutput().getChoices().get(0).getMessage().getContent();
                    if (delta != null) {
                        fullResponse.append(delta);
                        emitter.send(SseEmitter.event().data(delta));
                    }
                });

                long responseTime = System.currentTimeMillis() - startTime;

                // 5. 保存 AI 回答
                com.example.llm.entity.Message assistantMsg = new com.example.llm.entity.Message();
                assistantMsg.setConversationId(conversation.getId());
                assistantMsg.setParentId(currentUserMsg != null ? currentUserMsg.getId() : null);
                assistantMsg.setRole(Role.ASSISTANT.getValue());
                assistantMsg.setContent(fullResponse.toString());
                assistantMsg.setSourceChunks(referenceDocs);
                assistantMsg.setModel("qwen-plus");
                assistantMsg.setDurationMs((int) responseTime);
                assistantMsg.setStatus(1);
                assistantMsg.setCreateTime(LocalDateTime.now());
                
                assistantMsg.setTurnId(req.getTurnId() != null ? req.getTurnId() : (currentUserMsg != null ? currentUserMsg.getTurnId() : generateTurnId()));
                assistantMsg.setQVersion(req.getQVersion() != null ? req.getQVersion() : 0);
                assistantMsg.setAVersion(req.getAVersion() != null ? req.getAVersion() : 0);
                
                messageMapper.insert(assistantMsg);

                // 更新 conversation 统计
                boolean isFirstMessage = conversation.getMessageCount() == 0;
                // 如果是重新生成，或者编辑消息，消息总数不应该+2，如果是重新生成只+1（生成了一个新的assistantMsg），编辑+2（生成了一个userMsg和一个assistantMsg）
                if (Boolean.TRUE.equals(req.getIsRegenerate())) {
                    conversation.setMessageCount(conversation.getMessageCount() + 1);
                } else {
                    conversation.setMessageCount(conversation.getMessageCount() + 2);
                }
                conversation.setUpdateTime(LocalDateTime.now());
                
                if (isFirstMessage && (conversation.getTitle() == null || conversation.getTitle().trim().isEmpty() || conversation.getTitle().equals("新对话"))) {
                    try {
                        String titlePrompt = "请根据用户的这句话，生成一个简短的对话标题（不超过10个字），只返回标题内容，不要有任何标点符号和其他废话：" + req.getQuestion();
                        Generation titleGen = new Generation();
                        GenerationParam titleParam = GenerationParam.builder()
                                .apiKey(apiKey)
                                .model("qwen-plus")
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
                    // ignore
                }
                emitter.completeWithError(e);
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
                    .model("qwen-plus")
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
}
