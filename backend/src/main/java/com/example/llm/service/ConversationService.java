package com.example.llm.service;

import com.example.llm.dto.ConversationCreateReq;
import com.example.llm.dto.ConversationUpdateReq;
import com.example.llm.entity.Conversation;
import com.example.llm.vo.ConversationListVO;
import com.example.llm.vo.MessageVO;

import java.util.List;

public interface ConversationService {
    
    Conversation createConversation(Long userId, ConversationCreateReq req);
    
    List<ConversationListVO> getConversationList(Long userId);
    
    List<MessageVO> getConversationMessages(Long userId, Long conversationId);
    
    void updateConversationTitle(Long userId, Long conversationId, ConversationUpdateReq req);
    
    void deleteConversation(Long userId, Long conversationId);
}
