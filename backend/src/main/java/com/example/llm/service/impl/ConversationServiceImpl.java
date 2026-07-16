package com.example.llm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.llm.dto.ConversationCreateReq;
import com.example.llm.dto.ConversationDto;
import com.example.llm.dto.ConversationUpdateReq;
import com.example.llm.entity.Conversation;
import com.example.llm.entity.KnowledgeBase;
import com.example.llm.entity.Message;
import com.example.llm.mapper.ConversationMapper;
import com.example.llm.mapper.KnowledgeBaseMapper;
import com.example.llm.mapper.MessageMapper;
import com.example.llm.service.ConversationService;
import com.example.llm.vo.ConversationListVO;
import com.example.llm.vo.MessageVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ConversationServiceImpl implements ConversationService {

    @Autowired
    private ConversationMapper conversationMapper;

    @Autowired
    private MessageMapper messageMapper;

    @Autowired
    private KnowledgeBaseMapper knowledgeBaseMapper;

    @Override
    public ConversationDto createConversation(Long userId, ConversationCreateReq req) {
        if (req.getKbId() != null) {
            KnowledgeBase kb = knowledgeBaseMapper.selectById(req.getKbId());
            if (kb == null || !kb.getUserId().equals(userId)) {
                throw new IllegalArgumentException("知识库不存在或无权限");
            }
        }

        Conversation conversation = new Conversation();
        conversation.setUserId(userId);
        conversation.setKbId(req.getKbId());
        conversation.setTitle(req.getTitle() != null && !req.getTitle().trim().isEmpty() ? req.getTitle() : "新对话");
        conversation.setMessageCount(0);
        conversation.setTotalTokens(0);
        conversation.setIsPinned(0);
        conversation.setLearningProjectId(req.getLearningProjectId());
        conversation.setConversationType(req.getConversationType() != null ? req.getConversationType() : "general");
        conversation.setStatus(1);
        conversation.setCreateTime(LocalDateTime.now());
        conversation.setUpdateTime(LocalDateTime.now());
        
        conversationMapper.insert(conversation);
        return convertToDto(conversation);
    }

    @Override
    public List<ConversationDto> getConversationList(Long userId) {
        LambdaQueryWrapper<Conversation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Conversation::getUserId, userId)
               .eq(Conversation::getStatus, 1)
               .orderByDesc(Conversation::getIsPinned)
               .orderByDesc(Conversation::getUpdateTime);
        
        List<Conversation> conversations = conversationMapper.selectList(wrapper);
        if (conversations.isEmpty()) {
            return new ArrayList<>();
        }

        List<Long> kbIds = conversations.stream()
                .map(Conversation::getKbId)
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toList());

        Map<Long, String> kbNameMap = new java.util.HashMap<>();
        if (!kbIds.isEmpty()) {
            List<KnowledgeBase> kbs = knowledgeBaseMapper.selectBatchIds(kbIds);
            kbNameMap = kbs.stream().collect(Collectors.toMap(KnowledgeBase::getId, KnowledgeBase::getName));
        }

        List<ConversationDto> result = new ArrayList<>();
        for (Conversation c : conversations) {
            ConversationDto dto = new ConversationDto();
            dto.setId(c.getId());
            dto.setTitle(c.getTitle());
            dto.setKbId(c.getKbId());
            dto.setIsPinned(c.getIsPinned() != null && c.getIsPinned() == 1);
            dto.setMessageCount(c.getMessageCount());
            dto.setTotalTokens(c.getTotalTokens());
            dto.setLearningProjectId(c.getLearningProjectId());
            dto.setConversationType(c.getConversationType());
            dto.setUpdateTime(c.getUpdateTime());
            dto.setCreateTime(c.getCreateTime());
            if (c.getKbId() != null) {
                dto.setKbName(kbNameMap.get(c.getKbId()));
            }
            result.add(dto);
        }

        return result;
    }

    @Override
    public List<MessageVO> getConversationMessages(Long userId, Long conversationId) {
        Conversation c = conversationMapper.selectById(conversationId);
        if (c == null || !c.getUserId().equals(userId) || c.getStatus() == 0) {
            throw new IllegalArgumentException("对话不存在或无权限");
        }

        LambdaQueryWrapper<Message> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Message::getConversationId, conversationId)
               .eq(Message::getStatus, 1)
               .orderByAsc(Message::getCreateTime);

        List<Message> messages = messageMapper.selectList(wrapper);
        return messages.stream().map(m -> {
            MessageVO vo = new MessageVO();
            vo.setId(m.getId());
            vo.setConversationId(m.getConversationId());
            vo.setParentId(m.getParentId());
            vo.setRole(m.getRole());
            vo.setContent(m.getContent());
            vo.setSourceChunks(m.getSourceChunks());
            vo.setModel(m.getModel());
            vo.setDurationMs(m.getDurationMs());
            vo.setCreateTime(m.getCreateTime());
            vo.setTurnId(m.getTurnId());
            vo.setQVersion(m.getQVersion());
            vo.setAVersion(m.getAVersion());
            vo.setFiles(m.getFiles());
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    public ConversationDto updateConversation(Long userId, Long conversationId, ConversationUpdateReq req) {
        Conversation c = conversationMapper.selectById(conversationId);
        if (c == null || !c.getUserId().equals(userId) || c.getStatus() == 0) {
            throw new IllegalArgumentException("对话不存在或无权限");
        }

        if (req.getTitle() != null) {
            c.setTitle(req.getTitle());
        }
        if (req.getIsPinned() != null) {
            c.setIsPinned(req.getIsPinned());
        }
        if (req.getKnowledgeBaseId() != null) {
            c.setKbId(req.getKnowledgeBaseId());
        }
        if (req.getLearningProjectId() != null) {
            c.setLearningProjectId(req.getLearningProjectId());
        }
        if (req.getConversationType() != null) {
            c.setConversationType(req.getConversationType());
        }
        c.setUpdateTime(LocalDateTime.now());
        conversationMapper.updateById(c);

        return convertToDto(c);
    }

    private ConversationDto convertToDto(Conversation c) {
        ConversationDto dto = new ConversationDto();
        dto.setId(c.getId());
        dto.setTitle(c.getTitle());
        dto.setKbId(c.getKbId());
        dto.setIsPinned(c.getIsPinned() != null && c.getIsPinned() == 1);
        dto.setMessageCount(c.getMessageCount());
        dto.setTotalTokens(c.getTotalTokens());
        dto.setLearningProjectId(c.getLearningProjectId());
        dto.setConversationType(c.getConversationType());
        dto.setUpdateTime(c.getUpdateTime());
        dto.setCreateTime(c.getCreateTime());

        // 查询知识库名称
        if (c.getKbId() != null) {
            KnowledgeBase kb = knowledgeBaseMapper.selectById(c.getKbId());
            if (kb != null) {
                dto.setKbName(kb.getName());
            }
        }

        // 查询学习项目名称
        if (c.getLearningProjectId() != null) {
            // TODO: 从学习项目表查询名称,暂时使用ID作为名称
            dto.setLearningProjectName("学习项目#" + c.getLearningProjectId());
        }

        return dto;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteConversation(Long userId, Long conversationId) {
        Conversation c = conversationMapper.selectById(conversationId);
        if (c == null || !c.getUserId().equals(userId) || c.getStatus() == 0) {
            throw new IllegalArgumentException("对话不存在或无权限");
        }

        c.setStatus(0); // logic delete
        c.setUpdateTime(LocalDateTime.now());
        conversationMapper.updateById(c);

        // Soft delete messages
        Message updateMsg = new Message();
        updateMsg.setStatus(0);
        LambdaQueryWrapper<Message> msgWrapper = new LambdaQueryWrapper<>();
        msgWrapper.eq(Message::getConversationId, conversationId);
        messageMapper.update(updateMsg, msgWrapper);
    }
}
