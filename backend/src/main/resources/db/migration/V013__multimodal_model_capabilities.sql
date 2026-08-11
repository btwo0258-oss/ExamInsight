-- Register the public-beta multimodal capability set without storing credentials.
-- Runtime routing remains server-side and environment configured; this registry is
-- the auditable product contract used by later policy/admin modules.

ALTER TABLE model_definition
    DROP CHECK ck_model_definition__role;

ALTER TABLE model_definition
    ADD CONSTRAINT ck_model_definition__role CHECK (
        role IN ('FAST', 'REASONING', 'VALIDATOR', 'EMBEDDING', 'OCR', 'IMAGE', 'VISION', 'ASR', 'PPT')
    );

ALTER TABLE model_policy_route
    DROP CHECK ck_model_policy_route__role;

ALTER TABLE model_policy_route
    ADD CONSTRAINT ck_model_policy_route__role CHECK (
        role IN ('FAST', 'REASONING', 'VALIDATOR', 'EMBEDDING', 'OCR', 'IMAGE', 'VISION', 'ASR', 'PPT')
    );

ALTER TABLE model_policy_route
    ADD COLUMN generation_config_json JSON NULL AFTER condition_json;

ALTER TABLE model_policy_route
    ADD CONSTRAINT ck_model_policy_route__generation_config_object CHECK (
        generation_config_json IS NULL OR JSON_TYPE(generation_config_json) = 'OBJECT'
    );

UPDATE model_provider
SET base_url = 'https://dashscope.aliyuncs.com/compatible-mode/v1',
    metadata_json = JSON_OBJECT(
        'protocols', JSON_ARRAY('openai-compatible', 'dashscope-native'),
        'credentialSource', 'environment'
    )
WHERE provider_key = 'dashscope';

UPDATE model_definition definition
JOIN model_provider provider ON provider.id = definition.provider_id
SET definition.model_key = 'dashscope-qwen3.7-text-embedding-1024',
    definition.provider_model_name = 'qwen3.7-text-embedding',
    definition.capability_json = JSON_OBJECT(
        'dimensions', 1024,
        'vectorEncoding', 'float',
        'apiStyle', 'openai-compatible'
    )
WHERE provider.provider_key = 'dashscope'
  AND definition.model_key = 'dashscope-text-embedding-v4-1024';

INSERT IGNORE INTO model_definition (
    external_id, provider_id, model_key, provider_model_name, role, status,
    context_token_limit, max_output_tokens, supports_tools,
    supports_json_schema, supports_streaming, capability_json
)
SELECT
    '01J00000000000000000000005', provider.id,
    'dashscope-qwen3.7-plus', 'qwen3.7-plus', 'REASONING', 'ACTIVE',
    NULL, NULL, TRUE, TRUE, TRUE,
    JSON_OBJECT('chat', TRUE, 'vision', TRUE, 'apiStyle', 'openai-compatible')
FROM model_provider provider
WHERE provider.provider_key = 'dashscope';

INSERT IGNORE INTO model_definition (
    external_id, provider_id, model_key, provider_model_name, role, status,
    context_token_limit, max_output_tokens, supports_tools,
    supports_json_schema, supports_streaming, capability_json
)
SELECT
    '01J00000000000000000000006', provider.id,
    'dashscope-qwen3.5-ocr', 'qwen3.5-ocr', 'OCR', 'ACTIVE',
    NULL, NULL, FALSE, FALSE, FALSE,
    JSON_OBJECT('imageInput', TRUE, 'apiStyle', 'openai-compatible')
FROM model_provider provider
WHERE provider.provider_key = 'dashscope';

INSERT IGNORE INTO model_definition (
    external_id, provider_id, model_key, provider_model_name, role, status,
    context_token_limit, max_output_tokens, supports_tools,
    supports_json_schema, supports_streaming, capability_json
)
SELECT
    '01J00000000000000000000007', provider.id,
    'dashscope-qwen-image-3.0', 'qwen-image-3.0', 'IMAGE', 'ACTIVE',
    NULL, NULL, FALSE, FALSE, FALSE,
    JSON_OBJECT('imageGeneration', TRUE, 'apiStyle', 'dashscope-native')
FROM model_provider provider
WHERE provider.provider_key = 'dashscope';

INSERT IGNORE INTO model_definition (
    external_id, provider_id, model_key, provider_model_name, role, status,
    context_token_limit, max_output_tokens, supports_tools,
    supports_json_schema, supports_streaming, capability_json
)
SELECT
    '01J00000000000000000000008', provider.id,
    'dashscope-qwen3-asr-flash', 'qwen3-asr-flash', 'ASR', 'ACTIVE',
    NULL, NULL, FALSE, FALSE, FALSE,
    JSON_OBJECT('audioInput', TRUE, 'apiStyle', 'openai-compatible')
FROM model_provider provider
WHERE provider.provider_key = 'dashscope';
