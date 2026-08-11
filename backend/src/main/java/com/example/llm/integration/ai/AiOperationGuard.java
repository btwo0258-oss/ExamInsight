package com.example.llm.integration.ai;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/** Prevents concurrent retries from executing the same billable AI operation twice. */
@Component
public class AiOperationGuard {

    private static final String KEY_PREFIX = "examinsight:ai-operation:";
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final StringRedisTemplate redis;

    public AiOperationGuard(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public Lease acquire(String operationKey, Duration ttl) {
        if (operationKey == null || operationKey.isBlank()) {
            throw new IllegalArgumentException("AI 操作幂等键不能为空");
        }
        String key = KEY_PREFIX + operationKey;
        String token = UUID.randomUUID().toString();
        Boolean acquired = redis.opsForValue().setIfAbsent(key, token, ttl);
        if (!Boolean.TRUE.equals(acquired)) {
            throw new IllegalStateException("相同的生成任务正在处理中，请勿重复提交");
        }
        return new Lease(redis, key, token);
    }

    public static final class Lease implements AutoCloseable {
        private final StringRedisTemplate redis;
        private final String key;
        private final String token;
        private boolean closed;

        private Lease(StringRedisTemplate redis, String key, String token) {
            this.redis = redis;
            this.key = key;
            this.token = token;
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            redis.execute(RELEASE_SCRIPT, List.of(key), token);
        }
    }
}
