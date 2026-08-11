package com.example.llm.asset.controller;

import com.example.llm.asset.api.UploadDtos;
import com.example.llm.asset.service.UploadApplicationService;
import com.example.llm.common.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("/api/v2/uploads")
public class UploadController {
    private final UploadApplicationService uploadService;

    public UploadController(UploadApplicationService uploadService) {
        this.uploadService = uploadService;
    }

    @PostMapping
    public ResponseEntity<UploadDtos.UploadSessionResponse> createUpload(
            @Valid @RequestBody UploadDtos.CreateUploadRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                uploadService.createUpload(UserContext.requireSession().userId(), request));
    }

    @PutMapping(
            path = "/{uploadId}/parts/{partNumber}",
            consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public UploadDtos.UploadPartResponse uploadPart(
            @PathVariable String uploadId,
            @PathVariable int partNumber,
            HttpServletRequest request) throws IOException {
        return uploadService.uploadPart(
                UserContext.requireSession().userId(),
                uploadId,
                partNumber,
                request.getInputStream());
    }

    @PostMapping("/{uploadId}/complete")
    public UploadDtos.UploadCompletionResponse completeUpload(@PathVariable String uploadId) {
        return uploadService.completeUpload(UserContext.requireSession().userId(), uploadId);
    }

    @DeleteMapping("/{uploadId}")
    public ResponseEntity<Void> abortUpload(@PathVariable String uploadId) {
        uploadService.abortUpload(UserContext.requireSession().userId(), uploadId);
        return ResponseEntity.noContent().build();
    }
}
