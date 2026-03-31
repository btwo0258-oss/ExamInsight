package com.example.llm.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.llm.dto.DocUploadReq;
import com.example.llm.entity.Document;
import com.example.llm.vo.DocStatusVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface DocumentService extends IService<Document> {
    
    Document uploadDocument(Long userId, Long kbId, MultipartFile file);

    List<Document> getDocumentList(Long userId, Long kbId);

    Document getDocumentDetail(Long userId, Long docId);

    void deleteDocument(Long userId, Long docId);

    DocStatusVO getDocumentStatus(Long userId, Long docId);

    void downloadDocument(Long userId, Long docId, jakarta.servlet.http.HttpServletResponse response);
}