package com.example.llm.controller;

import com.example.llm.common.Result;
import com.example.llm.common.UserContext;
import com.example.llm.service.DocumentService;
import com.example.llm.vo.DocStatusVO;
import com.example.llm.vo.DocumentVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpServletResponse;

import java.util.List;

@RestController
@RequestMapping("/api/doc")
public class DocumentController {

    @Autowired
    private DocumentService documentService;

    @PostMapping("/extract")
    public Result<String> extract(@RequestParam("file") MultipartFile file) {
        try {
            java.io.File tempFile = java.io.File.createTempFile("extract-", file.getOriginalFilename());
            file.transferTo(tempFile);
            com.example.llm.component.DocumentParser parser = new com.example.llm.component.DocumentParser();
            String content = parser.parse(tempFile);
            tempFile.delete();
            return Result.success("提取成功", content);
        } catch (Exception e) {
            return Result.error(500, "文件解析失败: " + e.getMessage());
        }
    }

    @PostMapping("/upload")
    public Result<DocumentVO> upload(@RequestParam("kbId") Long kbId,
                                   @RequestParam("file") MultipartFile file) {
        return Result.success("上传成功，正在处理中", 
                documentService.uploadDocument(UserContext.getUserId(), kbId, file));
    }

    @GetMapping("/list")
    public Result<List<DocumentVO>> list(@RequestParam("kbId") Long kbId) {
        return Result.success(documentService.getDocumentList(UserContext.getUserId(), kbId));
    }

    @GetMapping("/{id}")
    public Result<DocumentVO> detail(@PathVariable("id") Long id) {
        return Result.success(documentService.getDocumentDetail(UserContext.getUserId(), id));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        documentService.deleteDocument(UserContext.getUserId(), id);
        return Result.success("删除成功", null);
    }

    @GetMapping("/status/{id}")
    public Result<DocStatusVO> status(@PathVariable("id") Long id) {
        return Result.success(documentService.getDocumentStatus(UserContext.getUserId(), id));
    }

    @GetMapping("/download/{id}")
    public void download(@PathVariable("id") Long id, HttpServletResponse response) {
        documentService.downloadDocument(UserContext.getUserId(), id, response);
    }
}
