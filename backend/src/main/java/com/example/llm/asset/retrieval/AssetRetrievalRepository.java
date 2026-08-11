package com.example.llm.asset.retrieval;

import com.example.llm.asset.processing.config.AssetProcessingProperties;
import com.example.llm.asset.retrieval.RetrievalModels.Scope;
import com.example.llm.asset.retrieval.RetrievalModels.ScopeType;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Repository
public class AssetRetrievalRepository {
    private final JdbcTemplate jdbc;
    private final AssetProcessingProperties processingProperties;
    private final AssetRetrievalProperties retrievalProperties;

    public AssetRetrievalRepository(
            @Qualifier("v2JdbcTemplate") JdbcTemplate jdbc,
            AssetProcessingProperties processingProperties,
            AssetRetrievalProperties retrievalProperties) {
        this.jdbc = jdbc;
        this.processingProperties = processingProperties;
        this.retrievalProperties = retrievalProperties;
    }

    public ResolvedScope resolveScope(long userId, Scope scope) {
        return switch (scope.type()) {
            case PERSONAL_LIBRARY -> new ResolvedScope(
                    ScopeType.PERSONAL_LIBRARY, null, null, true);
            case KNOWLEDGE_BASE -> resolveKnowledgeBase(userId, scope.knowledgeBaseExternalId());
            case ASSET_SET -> resolveAssets(userId, scope.assetExternalIds());
            case VERSION_SET -> resolveVersions(userId, scope.versionExternalIds());
            case EXPLICIT_SOURCES -> resolveExplicitSources(
                    userId, scope.knowledgeBaseExternalId(), scope.assetExternalIds());
        };
    }

    public boolean hasRetrievableChunks(long userId, ResolvedScope scope) {
        if (scope.isExplicitlyEmpty()) {
            return false;
        }
        StringBuilder sql = new StringBuilder("""
                SELECT EXISTS(
                    SELECT 1
                      FROM document_chunk c
                      JOIN asset_parse_result pr ON pr.id = c.parse_result_id
                      JOIN asset_version av ON av.id = pr.asset_version_id
                      JOIN asset a ON a.id = av.asset_id
                     WHERE a.user_id = ?
                       AND av.status = 'READY'
                       AND pr.status = 'READY'
                       AND av.active_parse_result_id = pr.id
                """);
        List<Object> args = new ArrayList<>();
        args.add(userId);
        appendScopePredicate(sql, args, scope);
        sql.append(" LIMIT 1)");
        Boolean exists = jdbc.queryForObject(sql.toString(), Boolean.class, args.toArray());
        return Boolean.TRUE.equals(exists);
    }

    public List<ChunkDocument> hydrateSemanticCandidates(
            long userId,
            ResolvedScope scope,
            List<String> chunkExternalIds) {
        if (chunkExternalIds.isEmpty() || scope.isExplicitlyEmpty()) {
            return List.of();
        }
        List<String> uniqueChunkIds = new ArrayList<>(new LinkedHashSet<>(chunkExternalIds));
        StringBuilder sql = new StringBuilder("""
                SELECT c.id AS chunk_id, c.external_id AS chunk_external_id,
                       c.sequence_no, c.content, c.content_sha256, c.token_count,
                       c.page_from, c.page_to, c.heading_path, c.locator_json,
                       a.id AS asset_id, a.external_id AS asset_external_id, a.name AS asset_name,
                       av.id AS version_id, av.external_id AS version_external_id
                  FROM document_chunk c
                  JOIN asset_parse_result pr ON pr.id = c.parse_result_id
                  JOIN asset_version av ON av.id = pr.asset_version_id
                  JOIN asset a ON a.id = av.asset_id
                  JOIN embedding_record er ON er.chunk_id = c.id
                  JOIN model_definition md ON md.id = er.model_definition_id
                 WHERE c.external_id IN (
                """);
        sql.append(placeholders(uniqueChunkIds.size())).append(")");
        sql.append("""
                   AND a.user_id = ?
                   AND av.status = 'READY'
                   AND pr.status = 'READY'
                   AND av.active_parse_result_id = pr.id
                   AND er.status = 'INDEXED'
                   AND er.embedding_version = ?
                   AND er.index_name = ?
                   AND er.content_sha256 = c.content_sha256
                   AND md.model_key = ?
                """);
        List<Object> args = new ArrayList<>(uniqueChunkIds);
        args.add(userId);
        args.add(processingProperties.getIndexing().getEmbeddingVersion());
        args.add(processingProperties.getIndexing().getIndexName());
        args.add(processingProperties.getIndexing().getModelKey());
        appendScopePredicate(sql, args, scope);
        return jdbc.query(sql.toString(), (rs, rowNum) -> mapChunk(rs, 0), args.toArray());
    }

    public List<ChunkDocument> searchKeyword(
            long userId,
            ResolvedScope scope,
            String query,
            int limit) {
        if (scope.isExplicitlyEmpty()) {
            return List.of();
        }
        StringBuilder sql = new StringBuilder("""
                SELECT c.id AS chunk_id, c.external_id AS chunk_external_id,
                       c.sequence_no, c.content, c.content_sha256, c.token_count,
                       c.page_from, c.page_to, c.heading_path, c.locator_json,
                       a.id AS asset_id, a.external_id AS asset_external_id, a.name AS asset_name,
                       av.id AS version_id, av.external_id AS version_external_id,
                       MATCH(c.content) AGAINST (? IN NATURAL LANGUAGE MODE) AS keyword_score
                  FROM document_chunk c
                  JOIN asset_parse_result pr ON pr.id = c.parse_result_id
                  JOIN asset_version av ON av.id = pr.asset_version_id
                  JOIN asset a ON a.id = av.asset_id
                 WHERE a.user_id = ?
                   AND av.status = 'READY'
                   AND pr.status = 'READY'
                   AND av.active_parse_result_id = pr.id
                   AND MATCH(c.content) AGAINST (? IN NATURAL LANGUAGE MODE) > 0
                """);
        List<Object> args = new ArrayList<>();
        args.add(query);
        args.add(userId);
        args.add(query);
        appendScopePredicate(sql, args, scope);
        sql.append(" ORDER BY keyword_score DESC, c.id ASC LIMIT ?");
        args.add(limit);
        return jdbc.query(sql.toString(), (rs, rowNum) ->
                mapChunk(rs, rs.getDouble("keyword_score")), args.toArray());
    }

    private ResolvedScope resolveKnowledgeBase(long userId, String externalId) {
        List<Long> knowledgeBaseIds = jdbc.query("""
                SELECT id
                  FROM knowledge_base
                 WHERE external_id = ? AND user_id = ? AND status = 'ACTIVE'
                """, (rs, rowNum) -> rs.getLong(1), externalId, userId);
        if (knowledgeBaseIds.isEmpty()) {
            throw new RetrievalException("RETRIEVAL_SCOPE_NOT_FOUND", "Retrieval scope is unavailable");
        }
        List<Long> assetIds = jdbc.query("""
                SELECT a.id
                  FROM knowledge_base_asset membership
                  JOIN asset a ON a.id = membership.asset_id
                 WHERE membership.knowledge_base_id = ?
                   AND membership.added_by_user_id = ?
                   AND a.user_id = ?
                   AND a.status = 'ACTIVE'
                 ORDER BY membership.sort_order ASC, membership.id ASC
                """, (rs, rowNum) -> rs.getLong(1), knowledgeBaseIds.get(0), userId, userId);
        assertScopeSize(assetIds.size());
        return new ResolvedScope(ScopeType.KNOWLEDGE_BASE, List.copyOf(assetIds), null, true);
    }

    private ResolvedScope resolveAssets(long userId, List<String> externalIds) {
        List<String> uniqueIds = uniqueNonBlank(externalIds);
        String sql = """
                SELECT external_id, id
                  FROM asset
                 WHERE user_id = ? AND status = 'ACTIVE' AND external_id IN (%s)
                """.formatted(placeholders(uniqueIds.size()));
        List<Object> args = new ArrayList<>();
        args.add(userId);
        args.addAll(uniqueIds);
        Map<String, Long> resolved = new LinkedHashMap<>();
        jdbc.query(sql, (rs, rowNum) -> {
            resolved.put(rs.getString(1), rs.getLong(2));
            return Boolean.TRUE;
        }, args.toArray());
        if (resolved.size() != uniqueIds.size()) {
            throw new RetrievalException("RETRIEVAL_SCOPE_NOT_FOUND", "Retrieval scope is unavailable");
        }
        List<Long> orderedIds = uniqueIds.stream().map(resolved::get).toList();
        return new ResolvedScope(ScopeType.ASSET_SET, orderedIds, null, true);
    }

    private ResolvedScope resolveVersions(long userId, List<String> externalIds) {
        List<String> uniqueIds = uniqueNonBlank(externalIds);
        String sql = """
                SELECT av.external_id, av.id
                  FROM asset_version av
                  JOIN asset a ON a.id = av.asset_id
                 WHERE a.user_id = ?
                   AND a.status <> 'PURGED'
                   AND av.status = 'READY'
                   AND av.external_id IN (%s)
                """.formatted(placeholders(uniqueIds.size()));
        List<Object> args = new ArrayList<>();
        args.add(userId);
        args.addAll(uniqueIds);
        Map<String, Long> resolved = new LinkedHashMap<>();
        jdbc.query(sql, (rs, rowNum) -> {
            resolved.put(rs.getString(1), rs.getLong(2));
            return Boolean.TRUE;
        }, args.toArray());
        if (resolved.size() != uniqueIds.size()) {
            throw new RetrievalException("RETRIEVAL_SCOPE_NOT_FOUND", "Retrieval scope is unavailable");
        }
        List<Long> orderedIds = uniqueIds.stream().map(resolved::get).toList();
        return new ResolvedScope(ScopeType.VERSION_SET, null, orderedIds, false);
    }

    private ResolvedScope resolveExplicitSources(
            long userId,
            String knowledgeBaseExternalId,
            List<String> assetExternalIds) {
        LinkedHashSet<Long> assetIds = new LinkedHashSet<>();
        if (knowledgeBaseExternalId != null && !knowledgeBaseExternalId.isBlank()) {
            ResolvedScope knowledgeBase = resolveKnowledgeBase(userId, knowledgeBaseExternalId.trim());
            assetIds.addAll(knowledgeBase.assetIds());
        }
        if (assetExternalIds != null && !assetExternalIds.isEmpty()) {
            ResolvedScope explicitAssets = resolveAssets(userId, assetExternalIds);
            assetIds.addAll(explicitAssets.assetIds());
        }
        if (assetIds.isEmpty()) {
            throw new RetrievalException("INVALID_RETRIEVAL_SCOPE", "Retrieval scope is invalid");
        }
        assertScopeSize(assetIds.size());
        return new ResolvedScope(
                ScopeType.EXPLICIT_SOURCES,
                List.copyOf(assetIds),
                null,
                true);
    }

    private List<String> uniqueNonBlank(List<String> externalIds) {
        if (externalIds == null || externalIds.isEmpty()
                || externalIds.size() > retrievalProperties.getMaxExplicitScopeItems()) {
            throw new RetrievalException("INVALID_RETRIEVAL_SCOPE", "Retrieval scope is invalid");
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String value : externalIds) {
            if (value == null || value.isBlank()) {
                throw new RetrievalException("INVALID_RETRIEVAL_SCOPE", "Retrieval scope is invalid");
            }
            unique.add(value.trim());
        }
        return List.copyOf(unique);
    }

    private void assertScopeSize(int size) {
        if (size > retrievalProperties.getMaxScopedAssets()) {
            throw new RetrievalException(
                    "RETRIEVAL_SCOPE_TOO_LARGE",
                    "Retrieval scope exceeds the public beta limit");
        }
    }

    private void appendScopePredicate(
            StringBuilder sql,
            List<Object> args,
            ResolvedScope scope) {
        if (scope.currentVersionsOnly()) {
            sql.append(" AND a.status = 'ACTIVE' AND a.current_version_id = av.id");
        } else {
            sql.append(" AND a.status <> 'PURGED'");
        }
        if (scope.assetIds() != null) {
            sql.append(" AND a.id IN (").append(placeholders(scope.assetIds().size())).append(")");
            args.addAll(scope.assetIds());
        }
        if (scope.versionIds() != null) {
            sql.append(" AND av.id IN (").append(placeholders(scope.versionIds().size())).append(")");
            args.addAll(scope.versionIds());
        }
    }

    private ChunkDocument mapChunk(ResultSet rs, double keywordScore) throws SQLException {
        return new ChunkDocument(
                rs.getLong("chunk_id"), rs.getString("chunk_external_id"),
                rs.getLong("asset_id"), rs.getString("asset_external_id"),
                rs.getString("asset_name"), rs.getLong("version_id"),
                rs.getString("version_external_id"), rs.getInt("sequence_no"),
                rs.getString("content"), rs.getString("content_sha256"),
                rs.getInt("token_count"), nullableInteger(rs, "page_from"),
                nullableInteger(rs, "page_to"), rs.getString("heading_path"),
                rs.getString("locator_json"), keywordScore);
    }

    private Integer nullableInteger(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        return value == null ? null : ((Number) value).intValue();
    }

    private String placeholders(int count) {
        if (count <= 0) {
            return "NULL";
        }
        return String.join(",", java.util.Collections.nCopies(count, "?"));
    }

    public record ResolvedScope(
            ScopeType type,
            List<Long> assetIds,
            List<Long> versionIds,
            boolean currentVersionsOnly) {
        public boolean isExplicitlyEmpty() {
            return (assetIds != null && assetIds.isEmpty())
                    || (versionIds != null && versionIds.isEmpty());
        }
    }

    public record ChunkDocument(
            long chunkId,
            String chunkExternalId,
            long assetId,
            String assetExternalId,
            String assetName,
            long versionId,
            String versionExternalId,
            int sequenceNo,
            String content,
            String contentSha256,
            int tokenCount,
            Integer pageFrom,
            Integer pageTo,
            String headingPath,
            String locatorJson,
            double keywordScore) {
    }
}
