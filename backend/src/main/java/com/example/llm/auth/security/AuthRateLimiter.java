package com.example.llm.auth.security;

import com.example.llm.auth.api.AuthApiException;
import com.example.llm.auth.config.AuthProperties;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class AuthRateLimiter {
    private final StringRedisTemplate redis;
    private final AuthProperties properties;

    public AuthRateLimiter(StringRedisTemplate redis, AuthProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    public void consumeRegistration(String emailHash, String deviceHash, String ipHash) {
        guarded(() -> {
            consume("auth:register:email:h:" + emailHash, 5, Duration.ofHours(1));
            consume("auth:register:email:d:" + emailHash, 10, Duration.ofDays(1));
            consume("auth:register:device:h:" + deviceHash, 10, Duration.ofHours(1));
            consume("auth:register:device:d:" + deviceHash, 30, Duration.ofDays(1));
            consume("auth:register:ip:h:" + ipHash, 30, Duration.ofHours(1));
            consume("auth:register:ip:d:" + ipHash, 200, Duration.ofDays(1));
            consume("auth:register:global:m", 300, Duration.ofMinutes(1));
            Boolean first = redis.opsForValue().setIfAbsent(
                    "auth:register:cooldown:" + emailHash,
                    "1",
                    properties.getVerification().getResendCooldown());
            if (!Boolean.TRUE.equals(first)) {
                throw limited("VERIFICATION_RESEND_COOLDOWN", "请等待 60 秒后再重新发送验证码。");
            }
        });
    }

    public void consumePasswordReset(String emailHash, String deviceHash, String ipHash) {
        guarded(() -> {
            consume("auth:password-reset:email:h:" + emailHash, 5, Duration.ofHours(1));
            consume("auth:password-reset:email:d:" + emailHash, 10, Duration.ofDays(1));
            consume("auth:password-reset:device:h:" + deviceHash, 10, Duration.ofHours(1));
            consume("auth:password-reset:device:d:" + deviceHash, 30, Duration.ofDays(1));
            consume("auth:password-reset:ip:h:" + ipHash, 30, Duration.ofHours(1));
            consume("auth:password-reset:ip:d:" + ipHash, 200, Duration.ofDays(1));
            consume("auth:password-reset:global:m", 300, Duration.ofMinutes(1));
            Boolean first = redis.opsForValue().setIfAbsent(
                    "auth:password-reset:cooldown:" + emailHash,
                    "1",
                    properties.getVerification().getResendCooldown());
            if (!Boolean.TRUE.equals(first)) {
                throw limited("VERIFICATION_RESEND_COOLDOWN", "请等待 60 秒后再重新发送验证码。");
            }
        });
    }

    public boolean requiresHumanVerificationForLogin(String emailHash) {
        return guardedResult(() -> readLong("auth:login:failure:email:" + emailHash) >= 3);
    }

    public void assertLoginAllowed(String emailHash, String ipHash) {
        guarded(() -> {
            if (Boolean.TRUE.equals(redis.hasKey("auth:login:block:email:" + emailHash))) {
                throw limited("LOGIN_TEMPORARILY_BLOCKED", "登录尝试过多，请稍后再试。");
            }
            if (readLong("auth:login:failure:ip:" + ipHash) >= 100) {
                throw limited("LOGIN_IP_RATE_LIMITED", "当前网络登录尝试过多，请稍后再试。");
            }
        });
    }

    public void recordLoginFailure(String emailHash, String ipHash) {
        guarded(() -> {
            long accountFailures = increment("auth:login:failure:email:" + emailHash, Duration.ofMinutes(15));
            increment("auth:login:failure:ip:" + ipHash, Duration.ofMinutes(15));
            if (accountFailures >= 5) {
                Duration block = accountFailures == 5
                        ? Duration.ofMinutes(1)
                        : accountFailures == 6 ? Duration.ofMinutes(5) : Duration.ofMinutes(15);
                redis.opsForValue().set("auth:login:block:email:" + emailHash, "1", block);
            }
        });
    }

    public void recordLoginSuccess(String emailHash) {
        guarded(() -> redis.delete(java.util.List.of(
                "auth:login:failure:email:" + emailHash,
                "auth:login:block:email:" + emailHash)));
    }

    private void consume(String key, long limit, Duration window) {
        long count = increment(key, window);
        if (count > limit) {
            throw limited("RATE_LIMITED", "请求过于频繁，请稍后再试。");
        }
    }

    private long increment(String key, Duration window) {
        Long count = redis.opsForValue().increment(key);
        if (count == null) {
            throw unavailable();
        }
        if (count == 1) {
            redis.expire(key, window);
        }
        return count;
    }

    private long readLong(String key) {
        String value = redis.opsForValue().get(key);
        return value == null ? 0 : Long.parseLong(value);
    }

    private void guarded(Runnable action) {
        try {
            action.run();
        } catch (AuthApiException exception) {
            throw exception;
        } catch (DataAccessException | NumberFormatException exception) {
            throw unavailable();
        }
    }

    private <T> T guardedResult(java.util.function.Supplier<T> action) {
        try {
            return action.get();
        } catch (AuthApiException exception) {
            throw exception;
        } catch (DataAccessException | NumberFormatException exception) {
            throw unavailable();
        }
    }

    private AuthApiException limited(String code, String message) {
        return new AuthApiException(HttpStatus.TOO_MANY_REQUESTS, code, message);
    }

    private AuthApiException unavailable() {
        return new AuthApiException(HttpStatus.SERVICE_UNAVAILABLE,
                "RATE_LIMITER_UNAVAILABLE", "认证服务暂时不可用，请稍后重试。");
    }
}
