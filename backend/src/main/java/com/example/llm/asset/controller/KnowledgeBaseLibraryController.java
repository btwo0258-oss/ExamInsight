package com.example.llm.asset.controller;

import com.example.llm.asset.api.LibraryDtos;
import com.example.llm.asset.service.LibraryApplicationService;
import com.example.llm.common.UserContext;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/knowledge-bases")
public class KnowledgeBaseLibraryController {
    private final LibraryApplicationService library;

    public KnowledgeBaseLibraryController(LibraryApplicationService library) {
        this.library = library;
    }

    @GetMapping
    public LibraryDtos.Page<LibraryDtos.KnowledgeBaseItem> list(
            @RequestParam(defaultValue = "library") String view,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor) {
        return library.listKnowledgeBases(
                UserContext.requireSession().userId(), view, limit, cursor);
    }

    @PostMapping
    public ResponseEntity<LibraryDtos.KnowledgeBaseDetail> create(
            @Valid @RequestBody LibraryDtos.CreateKnowledgeBaseRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                library.createKnowledgeBase(UserContext.requireSession().userId(), request));
    }

    @GetMapping("/{knowledgeBaseId}")
    public LibraryDtos.KnowledgeBaseDetail detail(@PathVariable String knowledgeBaseId) {
        return library.getKnowledgeBase(
                UserContext.requireSession().userId(), knowledgeBaseId);
    }

    @PatchMapping("/{knowledgeBaseId}")
    public LibraryDtos.KnowledgeBaseDetail update(
            @PathVariable String knowledgeBaseId,
            @Valid @RequestBody LibraryDtos.UpdateKnowledgeBaseRequest request) {
        return library.updateKnowledgeBase(
                UserContext.requireSession().userId(), knowledgeBaseId, request);
    }

    @PostMapping("/{knowledgeBaseId}/trash")
    public LibraryDtos.KnowledgeBaseDetail moveToTrash(
            @PathVariable String knowledgeBaseId) {
        return library.moveKnowledgeBaseToTrash(
                UserContext.requireSession().userId(), knowledgeBaseId);
    }

    @PostMapping("/{knowledgeBaseId}/restore")
    public LibraryDtos.KnowledgeBaseDetail restore(@PathVariable String knowledgeBaseId) {
        return library.restoreKnowledgeBase(
                UserContext.requireSession().userId(), knowledgeBaseId);
    }

    @DeleteMapping("/{knowledgeBaseId}")
    public ResponseEntity<Void> purge(@PathVariable String knowledgeBaseId) {
        library.purgeKnowledgeBase(UserContext.requireSession().userId(), knowledgeBaseId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{knowledgeBaseId}/assets")
    public LibraryDtos.Page<LibraryDtos.AssetItem> assets(
            @PathVariable String knowledgeBaseId,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor) {
        return library.listKnowledgeBaseAssets(
                UserContext.requireSession().userId(), knowledgeBaseId, limit, cursor);
    }

    @PutMapping("/{knowledgeBaseId}/assets/{assetId}")
    public ResponseEntity<Void> addAsset(
            @PathVariable String knowledgeBaseId,
            @PathVariable String assetId) {
        library.addAssetToKnowledgeBase(
                UserContext.requireSession().userId(), knowledgeBaseId, assetId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{knowledgeBaseId}/assets/{assetId}")
    public ResponseEntity<Void> removeAsset(
            @PathVariable String knowledgeBaseId,
            @PathVariable String assetId) {
        library.removeAssetFromKnowledgeBase(
                UserContext.requireSession().userId(), knowledgeBaseId, assetId);
        return ResponseEntity.noContent().build();
    }
}
