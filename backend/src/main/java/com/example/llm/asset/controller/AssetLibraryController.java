package com.example.llm.asset.controller;

import com.example.llm.asset.api.LibraryDtos;
import com.example.llm.asset.api.AssetApiException;
import com.example.llm.asset.service.AssetContentService;
import com.example.llm.asset.service.LibraryApplicationService;
import com.example.llm.common.UserContext;
import jakarta.validation.Valid;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/api/v2/assets")
public class AssetLibraryController {
    private final LibraryApplicationService library;
    private final AssetContentService contentService;

    public AssetLibraryController(
            LibraryApplicationService library,
            AssetContentService contentService) {
        this.library = library;
        this.contentService = contentService;
    }

    @GetMapping
    public LibraryDtos.Page<LibraryDtos.AssetItem> list(
            @RequestParam(defaultValue = "library") String view,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor) {
        return library.listAssets(
                UserContext.requireSession().userId(), view, limit, cursor);
    }

    @GetMapping("/{assetId}")
    public LibraryDtos.AssetDetail detail(@PathVariable String assetId) {
        return library.getAsset(UserContext.requireSession().userId(), assetId);
    }

    @PatchMapping("/{assetId}")
    public LibraryDtos.AssetItem rename(
            @PathVariable String assetId,
            @Valid @RequestBody LibraryDtos.RenameAssetRequest request) {
        return library.renameAsset(
                UserContext.requireSession().userId(), assetId, request.name());
    }

    @PostMapping("/{assetId}/trash")
    public LibraryDtos.AssetItem moveToTrash(@PathVariable String assetId) {
        return library.moveAssetToTrash(UserContext.requireSession().userId(), assetId);
    }

    @PostMapping("/{assetId}/restore")
    public LibraryDtos.AssetItem restore(@PathVariable String assetId) {
        return library.restoreAsset(UserContext.requireSession().userId(), assetId);
    }

    @DeleteMapping("/{assetId}")
    public ResponseEntity<LibraryDtos.PurgeJobView> purge(@PathVariable String assetId) {
        return ResponseEntity.accepted().body(
                library.requestAssetPurge(UserContext.requireSession().userId(), assetId));
    }

    @GetMapping("/{assetId}/purge-job")
    public LibraryDtos.PurgeJobView purgeJob(@PathVariable String assetId) {
        return library.getAssetPurgeJob(UserContext.requireSession().userId(), assetId);
    }

    @GetMapping("/{assetId}/content")
    public ResponseEntity<InputStreamResource> content(
            @PathVariable String assetId,
            @RequestParam(defaultValue = "inline") String disposition) {
        if (!disposition.equals("inline") && !disposition.equals("attachment")) {
            throw new AssetApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_DISPOSITION",
                    "文件响应方式不正确。",
                    Map.of());
        }
        var content = contentService.open(UserContext.requireSession().userId(), assetId);
        ContentDisposition contentDisposition = disposition.equals("attachment")
                ? ContentDisposition.attachment().filename(content.name(), StandardCharsets.UTF_8).build()
                : ContentDisposition.inline().filename(content.name(), StandardCharsets.UTF_8).build();
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(content.mimeType());
        } catch (RuntimeException exception) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header("X-Content-Type-Options", "nosniff")
                .contentType(mediaType)
                .contentLength(content.sizeBytes())
                .body(new InputStreamResource(content.stream()));
    }
}
