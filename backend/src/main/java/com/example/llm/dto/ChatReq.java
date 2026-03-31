package com.example.llm.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import com.fasterxml.jackson.annotation.JsonProperty;

@Data
public class ChatReq {
    @NotNull(message = "conversationId不能为空")
    private Long conversationId;
    
    private Long parentId; // The ID of the message this new message is replying to
    
    @NotBlank(message = "问题不能为空")
    private String question;
    
    private Boolean isRegenerate = false; // F4008 重新生成
    
    private Long editMsgId; // F4007 编辑消息的ID

    private String fileContext; // 上传的文件解析内容，不展示给用户，但作为上下文

    private java.util.List<MessageDto> history; // 前端传来的历史上下文

    private String turnId;

    @JsonProperty("qVersion")
    private Integer qVersion;// 问题版本号

    @JsonProperty("aVersion")
    private Integer aVersion;// 回答版本号

    private String files; // JSON string of attached files [{name, type, size}]

    @Data
    public static class MessageDto {
        private String role;
        private String content;
    }
}
