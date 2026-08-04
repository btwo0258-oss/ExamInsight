package com.example.llm.auth.security;

import com.example.llm.auth.config.AuthProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class AuthCrypto {
    private static final char[] CROCKFORD = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();
    private final byte[] hashKey;

    public AuthCrypto(AuthProperties properties) {
        String secret = properties.getHashSecret();
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("AUTH_HASH_SECRET 必须配置且长度不能少于 32 个字符");
        }
        this.hashKey = secret.getBytes(StandardCharsets.UTF_8);
    }

    public String digest(String namespace, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hashKey, "HmacSHA256"));
            byte[] bytes = mac.doFinal((namespace + '\0' + value).getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(bytes);
        } catch (Exception exception) {
            throw new IllegalStateException("无法计算安全摘要", exception);
        }
    }

    public boolean matches(String namespace, String rawValue, String expectedDigest) {
        if (rawValue == null || expectedDigest == null) {
            return false;
        }
        byte[] actual = digest(namespace, rawValue).getBytes(StandardCharsets.US_ASCII);
        byte[] expected = expectedDigest.getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(actual, expected);
    }

    public String newOpaqueToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String newVerificationCode() {
        return "%06d".formatted(RANDOM.nextInt(1_000_000));
    }

    public String newExternalId() {
        byte[] bytes = new byte[16];
        long timestamp = System.currentTimeMillis();
        for (int index = 5; index >= 0; index--) {
            bytes[index] = (byte) timestamp;
            timestamp >>>= 8;
        }
        byte[] randomness = new byte[10];
        RANDOM.nextBytes(randomness);
        System.arraycopy(randomness, 0, bytes, 6, randomness.length);

        BigInteger value = new BigInteger(1, bytes);
        char[] result = new char[26];
        BigInteger radix = BigInteger.valueOf(32);
        for (int index = result.length - 1; index >= 0; index--) {
            BigInteger[] division = value.divideAndRemainder(radix);
            result[index] = CROCKFORD[division[1].intValue()];
            value = division[0];
        }
        return new String(result);
    }
}
