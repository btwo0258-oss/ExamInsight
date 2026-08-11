package com.example.llm.chatv2.controller;

import com.example.llm.chatv2.artifact.ArtifactDraftService;
import com.example.llm.chatv2.artifact.ArtifactModels.ArtifactView;
import com.example.llm.chatv2.artifact.ArtifactModels.UpdateArtifactRequest;
import com.example.llm.common.UserContext;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v2/artifacts")
public class ArtifactV2Controller {
    private final ArtifactDraftService artifacts;

    public ArtifactV2Controller(ArtifactDraftService artifacts) {
        this.artifacts = artifacts;
    }

    @GetMapping("/{artifactId}")
    public ArtifactView get(@PathVariable String artifactId) {
        return artifacts.get(UserContext.requireSession().userId(), artifactId);
    }

    @GetMapping
    public List<ArtifactView> list(@RequestParam String conversationId) {
        return artifacts.list(UserContext.requireSession().userId(), conversationId);
    }

    @PatchMapping("/{artifactId}")
    public ArtifactView update(
            @PathVariable String artifactId,
            @Valid @RequestBody UpdateArtifactRequest request) {
        return artifacts.update(UserContext.requireSession().userId(), artifactId, request);
    }

    @PostMapping("/{artifactId}/confirm")
    public ArtifactView confirm(@PathVariable String artifactId) {
        return artifacts.confirm(UserContext.requireSession().userId(), artifactId);
    }
}
