package com.example.llm.controller;

import com.example.llm.common.Result;
import com.example.llm.common.UserContext;
import com.example.llm.dto.ConversationCreateReq;
import com.example.llm.dto.ConversationDto;
import com.example.llm.dto.ConversationUpdateReq;
import com.example.llm.service.ConversationService;
import com.example.llm.vo.ConversationListVO;
import com.example.llm.vo.MessageVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/conversation")
public class ConversationController {

    @Autowired
    private ConversationService conversationService;

    @PostMapping("/create")
    public Result<ConversationDto> create(@Validated @RequestBody ConversationCreateReq req) {
        return Result.success("创建成功", conversationService.createConversation(UserContext.getUserId(), req));
    }

    @GetMapping("/list")
    public Result<List<ConversationDto>> list() {
        return Result.success(conversationService.getConversationList(UserContext.getUserId()));
    }

    @GetMapping("/{id}/messages") // 获取指定会话的所有消息
    public Result<List<MessageVO>> getMessages(@PathVariable("id") Long id) {
        return Result.success(conversationService.getConversationMessages(UserContext.getUserId(), id));
    }

    @PutMapping("/{id}") // 更新指定会话
    public Result<ConversationDto> update(@PathVariable("id") Long id, @Validated @RequestBody ConversationUpdateReq req) {
        ConversationDto dto = conversationService.updateConversation(UserContext.getUserId(), id, req);
        return Result.success("更新成功", dto);
    }

    @DeleteMapping("/{id}") // 删除指定会话
    public Result<Void> delete(@PathVariable("id") Long id) {
        conversationService.deleteConversation(UserContext.getUserId(), id);
        return Result.success("删除成功", null);
    }
}
