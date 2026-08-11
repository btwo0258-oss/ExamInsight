-- CJK-aware keyword fallback for V2 retrieval. MySQL's built-in ngram parser
-- keeps retrieval available when the embedding provider or Elasticsearch is
-- temporarily unavailable. MySQL remains the authoritative content store.
ALTER TABLE document_chunk
    ADD FULLTEXT INDEX ft_document_chunk__content (content) WITH PARSER ngram;
