package com.example.llm.chatv2.artifact;

import com.example.llm.auth.security.AuthCrypto;
import com.example.llm.chatv2.api.ChatV2ApiException;
import com.example.llm.chatv2.artifact.ArtifactBinaryRenderer.RenderedArtifact;
import com.example.llm.chatv2.artifact.ArtifactDraftRepository.ArtifactRow;
import com.example.llm.chatv2.artifact.ArtifactModels.ArtifactView;
import com.example.llm.chatv2.artifact.ArtifactModels.DocumentDraftInput;
import com.example.llm.chatv2.artifact.ArtifactModels.ImageGenerationInput;
import com.example.llm.chatv2.artifact.ArtifactModels.MindMapDraftInput;
import com.example.llm.chatv2.artifact.ArtifactModels.MindMapNode;
import com.example.llm.chatv2.artifact.ArtifactModels.PresentationDraftInput;
import com.example.llm.chatv2.artifact.ArtifactModels.SlideInput;
import com.example.llm.chatv2.artifact.ArtifactModels.ToolResult;
import com.example.llm.chatv2.artifact.ArtifactModels.Type;
import com.example.llm.chatv2.artifact.ArtifactModels.UpdateArtifactRequest;
import com.example.llm.chatv2.repository.ChatV2Repository.RunExecutionContext;
import com.example.llm.integration.ai.AiCallResult;
import com.example.llm.integration.ai.AiCapabilityRouter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class ArtifactDraftService {
    private static final int MAX_MIND_MAP_NODES = 500;
    private static final int MAX_MIND_MAP_DEPTH = 10;

    private final ArtifactDraftRepository repository;
    private final GeneratedAssetWriter assetWriter;
    private final ArtifactBinaryRenderer renderer;
    private final AiCapabilityRouter ai;
    private final AuthCrypto crypto;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactions;

    public ArtifactDraftService(
            ArtifactDraftRepository repository,
            GeneratedAssetWriter assetWriter,
            ArtifactBinaryRenderer renderer,
            AiCapabilityRouter ai,
            AuthCrypto crypto,
            ObjectMapper objectMapper,
            @Qualifier("v2TransactionTemplate") TransactionTemplate transactions) {
        this.repository = repository;
        this.assetWriter = assetWriter;
        this.renderer = renderer;
        this.ai = ai;
        this.crypto = crypto;
        this.objectMapper = objectMapper;
        this.transactions = transactions;
    }

    public ToolResult createDocument(RunExecutionContext context, DocumentDraftInput input) {
        if (input == null) throw invalid("INVALID_DOCUMENT_DRAFT", "Document draft input is required");
        validateTitle(input.title());
        validateDocument(input.markdown());
        ArtifactView draft = createDraft(context, Type.DOCUMENT, input.title(),
                Map.of("markdown", input.markdown()));
        return draftResult(draft, "Document draft created. The user can edit it before confirmation.");
    }

    public ToolResult createMindMap(RunExecutionContext context, MindMapDraftInput input) {
        if (input == null) throw invalid("INVALID_MIND_MAP", "Mind-map input is required");
        validateTitle(input.title());
        validateMindMap(input.root());
        ArtifactView draft = createDraft(context, Type.MINDMAP, input.title(),
                Map.of("root", objectMapper.convertValue(input.root(), Map.class)));
        return draftResult(draft, "Mind-map draft created. The user can edit it before confirmation.");
    }

    public ToolResult createPresentation(RunExecutionContext context, PresentationDraftInput input) {
        if (input == null) throw invalid("INVALID_PRESENTATION", "Presentation input is required");
        validateTitle(input.title());
        validatePresentation(input.slides());
        ArtifactView draft = createDraft(context, Type.PRESENTATION, input.title(),
                Map.of("slides", objectMapper.convertValue(input.slides(), List.class)));
        return draftResult(draft, "Presentation draft created. The user can edit it before confirmation.");
    }

    public ToolResult generateImage(RunExecutionContext context, ImageGenerationInput input) {
        if (input == null) throw invalid("INVALID_IMAGE_REQUEST", "Image generation input is required");
        validateTitle(input.title());
        if (input.prompt() == null || input.prompt().isBlank() || input.prompt().length() > 4_000) {
            throw invalid("INVALID_IMAGE_PROMPT", "Image prompt must contain between 1 and 4000 characters");
        }
        int width = imageDimension(input.width());
        int height = imageDimension(input.height());
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("prompt", input.prompt());
        content.put("width", width);
        content.put("height", height);
        ArtifactView generating = createDraft(
                context, Type.IMAGE, input.title(), content, "GENERATING");
        ArtifactRow row = requireOwned(context.userId(), generating.id(), false);
        try {
            AiCallResult<byte[]> result = ai.generateImage(input.prompt(), width, height);
            GeneratedAssetWriter.WrittenAsset asset = assetWriter.write(
                    context.userId(), context.runId(), fileName(input.title(), ".png"),
                    "image", "image/png", result.value());
            transactions.executeWithoutResult(status -> repository.markConfirmed(row.id(), asset.versionId()));
            return new ToolResult("CONFIRMED", generating.id(), Type.IMAGE, input.title(),
                    asset.assetExternalId(), "Image generated and saved to the user's library.");
        } catch (RuntimeException exception) {
            transactions.executeWithoutResult(status -> repository.markFailed(row.id(), "IMAGE_GENERATION_FAILED"));
            // Keep the failed draft visible to the chat. The caller can render an
            // honest failure state and the user can retry from the run controls;
            // returning a tool result also prevents a failed image from looking
            // like a successful, but missing, asset.
            return new ToolResult("FAILED", generating.id(), Type.IMAGE, input.title(),
                    null, "Image generation failed. No file was saved.");
        }
    }

    public ArtifactView get(long userId, String artifactId) {
        return repository.view(requireOwned(userId, artifactId, false));
    }

    public List<ArtifactView> list(long userId, String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            throw invalid("INVALID_CONVERSATION_ID", "Conversation id is required");
        }
        return repository.findOwnedByConversation(userId, conversationId).stream()
                .map(repository::view)
                .toList();
    }

    public ArtifactView update(long userId, String artifactId, UpdateArtifactRequest request) {
        if (request == null || request.version() == null) {
            throw invalid("INVALID_ARTIFACT_UPDATE", "Artifact update is required");
        }
        validateTitle(request.title());
        return Objects.requireNonNull(transactions.execute(status -> {
            ArtifactRow current = requireOwned(userId, artifactId, true);
            requireEditable(current);
            validateContent(current.type(), request.content());
            int revision = current.currentRevision() + 1;
            String json = json(request.content());
            repository.insertRevision(current.id(), revision, "USER", json,
                    crypto.digest("artifact-content", json), userId);
            repository.updateDraft(current.id(), request.title().trim(), json, revision, request.version());
            return repository.view(requireOwned(userId, artifactId, false));
        }));
    }

    public ArtifactView confirm(long userId, String artifactId) {
        return Objects.requireNonNull(transactions.execute(status -> {
            ArtifactRow current = requireOwned(userId, artifactId, true);
            requireEditable(current);
            validateContent(current.type(), current.content());
            RenderedArtifact rendered = renderer.render(current);
            GeneratedAssetWriter.WrittenAsset asset = assetWriter.write(
                    userId, current.aiRunId(), rendered.fileName(), rendered.generationLabel(),
                    rendered.mimeType(), rendered.bytes());
            repository.markConfirmed(current.id(), asset.versionId());
            return repository.view(requireOwned(userId, artifactId, false));
        }));
    }

    private ArtifactView createDraft(
            RunExecutionContext context,
            Type type,
            String title,
            Map<String, Object> content) {
        return createDraft(context, type, title, content, "DRAFT");
    }

    private ArtifactView createDraft(
            RunExecutionContext context,
            Type type,
            String title,
            Map<String, Object> content,
            String status) {
        validateTitle(title);
        return Objects.requireNonNull(transactions.execute(transaction -> {
            String externalId = crypto.newExternalId();
            String json = json(content);
            long id = repository.insertDraft(
                    externalId, context.userId(), context.conversationId(), context.runId(),
                    context.requestMessageId(), type, status, title.trim(), json);
            repository.insertRevision(id, 1, "AI", json,
                    crypto.digest("artifact-content", json), null);
            return repository.view(requireOwned(context.userId(), externalId, false));
        }));
    }

    private ToolResult draftResult(ArtifactView draft, String message) {
        return new ToolResult(draft.status(), draft.id(), draft.type(), draft.title(), null, message);
    }

    private ArtifactRow requireOwned(long userId, String artifactId, boolean forUpdate) {
        if (artifactId == null || artifactId.isBlank()) {
            throw api(HttpStatus.BAD_REQUEST, "INVALID_ARTIFACT_ID", "Artifact id is required");
        }
        return repository.findOwned(userId, artifactId, forUpdate)
                .orElseThrow(() -> api(HttpStatus.NOT_FOUND, "ARTIFACT_NOT_FOUND", "Artifact was not found"));
    }

    private void requireEditable(ArtifactRow row) {
        if (row.type() == Type.IMAGE || !(row.status().equals("DRAFT") || row.status().equals("READY"))) {
            throw api(HttpStatus.CONFLICT, "ARTIFACT_NOT_EDITABLE",
                    "Only unconfirmed document, mind-map, and presentation drafts can be edited");
        }
    }

    private void validateContent(Type type, Map<String, Object> content) {
        if (content == null) throw api(HttpStatus.BAD_REQUEST, "INVALID_ARTIFACT_CONTENT", "Artifact content is required");
        try {
            switch (type) {
                case DOCUMENT -> validateDocument(String.valueOf(content.getOrDefault("markdown", "")));
                case MINDMAP -> validateMindMap(objectMapper.convertValue(content.get("root"), MindMapNode.class));
                case PRESENTATION -> validatePresentation(objectMapper.convertValue(
                        content.get("slides"), objectMapper.getTypeFactory().constructCollectionType(List.class, SlideInput.class)));
                case IMAGE -> throw api(HttpStatus.CONFLICT, "IMAGE_NOT_EDITABLE", "Generated images are immutable");
            }
        } catch (ChatV2ApiException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw api(HttpStatus.BAD_REQUEST, "INVALID_ARTIFACT_CONTENT", "Artifact content is invalid");
        }
    }

    private void validateDocument(String markdown) {
        if (markdown == null || markdown.isBlank() || markdown.length() > 100_000) {
            throw api(HttpStatus.BAD_REQUEST, "INVALID_DOCUMENT_DRAFT",
                    "Document content must contain between 1 and 100000 characters");
        }
    }

    private void validateMindMap(MindMapNode root) {
        if (root == null) throw api(HttpStatus.BAD_REQUEST, "INVALID_MIND_MAP", "Mind-map root is required");
        record NodeDepth(MindMapNode node, int depth) { }
        ArrayDeque<NodeDepth> queue = new ArrayDeque<>();
        queue.add(new NodeDepth(root, 1));
        int count = 0;
        while (!queue.isEmpty()) {
            NodeDepth current = queue.removeFirst();
            if (++count > MAX_MIND_MAP_NODES || current.depth() > MAX_MIND_MAP_DEPTH) {
                throw api(HttpStatus.BAD_REQUEST, "MIND_MAP_TOO_COMPLEX",
                        "Mind map exceeds the node or depth limit");
            }
            String text = current.node().text();
            if (text == null || text.isBlank() || text.length() > 500) {
                throw api(HttpStatus.BAD_REQUEST, "INVALID_MIND_MAP_NODE", "Mind-map node text is invalid");
            }
            for (MindMapNode child : current.node().children()) {
                if (child == null) throw api(HttpStatus.BAD_REQUEST, "INVALID_MIND_MAP_NODE", "Mind-map child is invalid");
                queue.addLast(new NodeDepth(child, current.depth() + 1));
            }
        }
    }

    private void validatePresentation(List<SlideInput> slides) {
        if (slides == null || slides.isEmpty() || slides.size() > 40) {
            throw api(HttpStatus.BAD_REQUEST, "INVALID_PRESENTATION", "Presentation must contain 1 to 40 slides");
        }
        for (SlideInput slide : slides) {
            if (slide == null || slide.title() == null || slide.title().isBlank()
                    || slide.title().length() > 255 || slide.bullets().size() > 20) {
                throw api(HttpStatus.BAD_REQUEST, "INVALID_PRESENTATION_SLIDE", "Presentation slide is invalid");
            }
            for (String bullet : slide.bullets()) {
                if (bullet == null || bullet.isBlank() || bullet.length() > 1_000) {
                    throw api(HttpStatus.BAD_REQUEST, "INVALID_PRESENTATION_BULLET", "Presentation bullet is invalid");
                }
            }
        }
    }

    private int imageDimension(Integer dimension) {
        int value = dimension == null ? 1024 : dimension;
        if (value < 512 || value > 2048 || value % 64 != 0) {
            throw api(HttpStatus.BAD_REQUEST, "INVALID_IMAGE_SIZE",
                    "Image dimensions must be multiples of 64 between 512 and 2048");
        }
        return value;
    }

    private void validateTitle(String title) {
        if (title == null || title.isBlank() || title.length() > 255) {
            throw invalid("INVALID_ARTIFACT_TITLE", "Artifact title must contain between 1 and 255 characters");
        }
    }

    private String json(Map<String, Object> content) {
        try {
            return objectMapper.writeValueAsString(content);
        } catch (JsonProcessingException exception) {
            throw api(HttpStatus.BAD_REQUEST, "INVALID_ARTIFACT_CONTENT", "Artifact content is not valid JSON");
        }
    }

    private String fileName(String title, String extension) {
        String safe = title.trim().replaceAll("[\\\\/:*?\"<>|]", "_");
        return safe.endsWith(extension) ? safe : safe + extension;
    }

    private ChatV2ApiException api(HttpStatus status, String code, String message) {
        return new ChatV2ApiException(status, code, message);
    }

    private ChatV2ApiException invalid(String code, String message) {
        return api(HttpStatus.BAD_REQUEST, code, message);
    }
}
