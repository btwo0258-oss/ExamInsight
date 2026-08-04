package com.example.llm.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Data
@ConfigurationProperties(prefix = "app.auth")
public class AuthProperties {
    private String hashSecret = "";
    private final Cookie cookie = new Cookie();
    private final HumanVerification humanVerification = new HumanVerification();
    private final Mail mail = new Mail();
    private final Session session = new Session();
    private final Verification verification = new Verification();

    @Data
    public static class Cookie {
        private String name = "EXAMINSIGHT_SESSION";
        private String csrfName = "XSRF-TOKEN";
        private boolean secure;
        private String sameSite = "Lax";
    }

    @Data
    public static class HumanVerification {
        private String mode = "disabled";
        private String verifyUrl = "";
        private String secret = "";
        private String sceneId = "";
        private String endpoint = "captcha.cn-shanghai.aliyuncs.com";
    }

    @Data
    public static class Mail {
        private String from = "";
    }

    @Data
    public static class Session {
        private Duration idleTimeout = Duration.ofHours(24);
        private Duration absoluteTimeout = Duration.ofDays(30);
        private Duration rotationInterval = Duration.ofHours(24);
        private Duration activityWriteInterval = Duration.ofMinutes(5);
        private int maximumActiveSessions = 5;
    }

    @Data
    public static class Verification {
        private Duration codeTtl = Duration.ofMinutes(10);
        private Duration proofTtl = Duration.ofMinutes(10);
        private Duration resendCooldown = Duration.ofSeconds(60);
        private int maximumAttempts = 5;
    }
}
