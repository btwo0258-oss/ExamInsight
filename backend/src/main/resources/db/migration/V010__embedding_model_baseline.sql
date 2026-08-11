-- Required production reference data for the first V2 embedding adapter.
-- Credentials remain environment-backed; no secret or test data is stored here.

INSERT IGNORE INTO model_provider (
    external_id, provider_key, display_name, status, base_url,
    credential_secret_ref, region, timeout_ms, metadata_json
) VALUES (
    '01J00000000000000000000001',
    'xfyun',
    'iFlytek Open Platform',
    'ACTIVE',
    'https://emb-cn-huabei-1.xf-yun.com/',
    'env:XFYUN_APP_ID,XFYUN_API_KEY,XFYUN_API_SECRET',
    'cn-huabei-1',
    60000,
    JSON_OBJECT('protocol', 'llm-embedding', 'credentialSource', 'environment')
);

INSERT IGNORE INTO model_definition (
    external_id, provider_id, model_key, provider_model_name, role, status,
    context_token_limit, max_output_tokens, supports_tools,
    supports_json_schema, supports_streaming, capability_json
)
SELECT
    '01J00000000000000000000002',
    provider.id,
    'xfyun-llm-embedding-2560',
    'llm-embedding',
    'EMBEDDING',
    'ACTIVE',
    2048,
    NULL,
    FALSE,
    FALSE,
    FALSE,
    JSON_OBJECT(
        'dimensions', 2560,
        'documentDomain', 'para',
        'queryDomain', 'query',
        'vectorEncoding', 'float32-little-endian'
    )
FROM model_provider provider
WHERE provider.provider_key = 'xfyun';
