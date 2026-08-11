package com.example.llm.asset.retrieval;

import java.util.List;
import java.util.Objects;

public final class RetrievalModels {
    private RetrievalModels() {
    }

    public enum ScopeType {
        PERSONAL_LIBRARY,
        KNOWLEDGE_BASE,
        ASSET_SET,
        VERSION_SET,
        EXPLICIT_SOURCES
    }

    public enum Status {
        SUCCEEDED,
        DEGRADED,
        EMPTY,
        DISABLED
    }

    public enum Mode {
        HYBRID,
        SEMANTIC,
        KEYWORD,
        NONE
    }

    public record Scope(
            ScopeType type,
            String knowledgeBaseExternalId,
            List<String> assetExternalIds,
            List<String> versionExternalIds) {
        public Scope {
            type = Objects.requireNonNull(type, "type");
            assetExternalIds = assetExternalIds == null ? List.of() : List.copyOf(assetExternalIds);
            versionExternalIds = versionExternalIds == null ? List.of() : List.copyOf(versionExternalIds);
        }

        public static Scope personalLibrary() {
            return new Scope(ScopeType.PERSONAL_LIBRARY, null, List.of(), List.of());
        }

        public static Scope knowledgeBase(String externalId) {
            return new Scope(ScopeType.KNOWLEDGE_BASE, externalId, List.of(), List.of());
        }

        public static Scope assets(List<String> externalIds) {
            return new Scope(ScopeType.ASSET_SET, null, externalIds, List.of());
        }

        public static Scope versions(List<String> externalIds) {
            return new Scope(ScopeType.VERSION_SET, null, List.of(), externalIds);
        }

        public static Scope explicitSources(String knowledgeBaseExternalId, List<String> assetExternalIds) {
            return new Scope(
                    ScopeType.EXPLICIT_SOURCES,
                    knowledgeBaseExternalId,
                    assetExternalIds,
                    List.of());
        }
    }

    public record Request(
            String query,
            Scope scope,
            Integer topK,
            Integer maxContextTokens) {
    }

    public record Source(
            int citationNo,
            String citationKey,
            String assetExternalId,
            String assetName,
            String assetVersionExternalId,
            String chunkExternalId,
            int sequenceNo,
            Integer pageFrom,
            Integer pageTo,
            String headingPath,
            String locatorJson,
            String content,
            int tokenCount,
            double score,
            Mode mode) {
    }

    public record Bundle(
            Status status,
            Mode mode,
            String contextJson,
            int contextTokens,
            List<Source> sources,
            String degradationCode) {
        public Bundle {
            sources = sources == null ? List.of() : List.copyOf(sources);
        }
    }
}
