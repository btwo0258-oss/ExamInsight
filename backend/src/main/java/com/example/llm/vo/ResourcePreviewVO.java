package com.example.llm.vo;

import lombok.Data;
import java.util.Map;

@Data
public class ResourcePreviewVO {
    private LibraryResourceVO resource;
    private String status;
    private String previewKind;
    private String textContent;
    private String previewUrl;
    private String transcript;
    private String presentationId;
    private String spreadsheetId;
    private Long mindMapId;
    private String errorMessage;
    private Map<String, Object> previewData;
}
