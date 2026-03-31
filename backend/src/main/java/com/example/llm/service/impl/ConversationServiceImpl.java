package com.example.llm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.llm.dto.ConversationCreateReq;
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
    public Conversation createConversation(Long userId, ConversationCreateReq req) {
        if (req.getKbId() != null) {
            KnowledgeBase kb = knowledgeBaseMapper.selectById(req.getKbId());
            if (kb == null || !kb.getUserId().equals(userId)) {
                throw new IllegalArgumentException("知识库不存在或无权限");
            }
        }

        Conversation conversation = new Conversation();
        conversation.setUserId(userId);
        conversation.setKbId(req.getKbId());
        conversation.setTitle(req.getTitle());
        conversation.setMessageCount(0);
        conversation.setTotalTokens(0);
        conversation.setStatus(1);
        conversation.setCreateTime(LocalDateTime.now());
        conversation.setUpdateTime(LocalDateTime.now());
        
        conversationMapper.insert(conversation);
        return conversation;
    }

    @Override
    public List<ConversationListVO> getConversationList(Long userId) {
        LambdaQueryWrapper<Conversation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Conversation::getUserId, userId)
               .eq(Conversation::getStatus, 1)
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

        List<ConversationListVO> result = new ArrayList<>();
        for (Conversation c : conversations) {
            ConversationListVO vo = new ConversationListVO();
            BeanUtils.copyProperties(c, vo);
            if (c.getKbId() != null) {
                vo.setKbName(kbNameMap.get(c.getKbId()));
            }
            result.add(vo);
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
    public void updateConversationTitle(Long userId, Long conversationId, ConversationUpdateReq req) {
        Conversation c = conversationMapper.selectById(conversationId);
        if (c == null || !c.getUserId().equals(userId) || c.getStatus() == 0) {
            throw new IllegalArgumentException("对话不存在或无权限");
        }

        c.setTitle(req.getTitle());
        c.setUpdateTime(LocalDateTime.now());
        conversationMapper.updateById(c);
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
