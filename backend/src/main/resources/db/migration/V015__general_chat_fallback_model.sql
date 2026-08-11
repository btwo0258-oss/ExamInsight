-- Register the audited Spark fallback used by the server-side general-chat router.
-- Provider credentials remain environment-backed and are never persisted here.

INSERT IGNORE INTO model_definition (
    external_id, provider_id, model_key, provider_model_name, role, status,
    context_token_limit, max_output_tokens, supports_tools,
    supports_json_schema, supports_streaming, capability_json
)
SELECT
    '01J0000000000000000000000E', provider.id,
    'xfyun-spark-x', 'spark-x', 'REASONING', 'ACTIVE',
    NULL, NULL, FALSE, FALSE, TRUE,
    JSON_OBJECT('chat', TRUE, 'fallbackOnly', TRUE, 'apiStyle', 'openai-compatible')
FROM model_provider provider
WHERE provider.provider_key = 'xfyun';

INSERT IGNORE INTO model_policy_route (
    external_id, policy_version_id, role, priority, model_definition_id,
    condition_json, generation_config_json, timeout_ms, max_retries, enabled
)
SELECT
    '01J0000000000000000000000F', policy.id, 'REASONING', 2, model.id,
    JSON_OBJECT('mode', 'GENERAL_CHAT', 'fallbackOnly', TRUE),
    JSON_OBJECT('temperature', 0.3, 'stream', TRUE),
    120000, 0, TRUE
FROM model_policy_version policy
JOIN model_definition model ON model.model_key = 'xfyun-spark-x'
WHERE policy.policy_key = 'general-chat' AND policy.version_no = 1;
