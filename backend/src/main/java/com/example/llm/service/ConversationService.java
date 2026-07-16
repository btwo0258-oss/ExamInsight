package com.example.llm.service;

import com.example.llm.dto.ConversationCreateReq;
import com.example.llm.dto.ConversationDto;
import com.example.llm.dto.ConversationUpdateReq;
import com.example.llm.vo.MessageVO;

import java.util.List;

public interface ConversationService {
    
    ConversationDto createConversation(Long userId, ConversationCreateReq req);
    
    List<ConversationDto> getConversationList(Long userId);
    
    List<MessageVO> getConversationMessages(Long userId, Long conversationId);
    
    ConversationDto updateConversation(Long userId, Long conversationId, ConversationUpdateReq req);
    
    void deleteConversation(Long userId, Long conversationId);
}
