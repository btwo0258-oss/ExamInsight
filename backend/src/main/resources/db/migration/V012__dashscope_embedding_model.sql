-- Provider/model registry for the public-beta DashScope embedding adapter.
-- The API key remains environment-backed and is never stored in MySQL.

INSERT IGNORE INTO model_provider (
    external_id, provider_key, display_name, status, base_url,
    credential_secret_ref, region, timeout_ms, metadata_json
) VALUES (
    '01J00000000000000000000003',
    'dashscope',
    'Alibaba Cloud Model Studio',
    'ACTIVE',
    'https://dashscope.aliyuncs.com/compatible-mode/v1/embeddings',
    'env:DASHSCOPE_API_KEY',
    'cn-beijing',
    60000,
    JSON_OBJECT('protocol', 'openai-compatible-embeddings', 'credentialSource', 'environment')
);

INSERT IGNORE INTO model_definition (
    external_id, provider_id, model_key, provider_model_name, role, status,
    context_token_limit, max_output_tokens, supports_tools,
    supports_json_schema, supports_streaming, capability_json
)
SELECT
    '01J00000000000000000000004',
    provider.id,
    'dashscope-text-embedding-v4-1024',
    'text-embedding-v4',
    'EMBEDDING',
    'ACTIVE',
    8192,
    NULL,
    FALSE,
    FALSE,
    FALSE,
    JSON_OBJECT(
        'dimensions', 1024,
        'vectorEncoding', 'float',
        'apiStyle', 'openai-compatible'
    )
FROM model_provider provider
WHERE provider.provider_key = 'dashscope';
