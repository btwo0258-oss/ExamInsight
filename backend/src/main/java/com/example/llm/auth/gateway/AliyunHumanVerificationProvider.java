package com.example.llm.auth.gateway;

import com.aliyun.captcha20230305.Client;
import com.aliyun.captcha20230305.models.VerifyIntelligentCaptchaRequest;
import com.aliyun.captcha20230305.models.VerifyIntelligentCaptchaResponse;
import com.aliyun.captcha20230305.models.VerifyIntelligentCaptchaResponseBody;
import com.aliyun.teaopenapi.models.Config;
import com.example.llm.auth.api.AuthApiException;
import com.example.llm.auth.config.AuthProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Slf4j
@Component
class AliyunHumanVerificationProvider implements HumanVerificationProvider {
    private final AuthProperties properties;
    private final Object clientMonitor = new Object();
    private volatile Client client;

    AliyunHumanVerificationProvider(AuthProperties properties) {
        this.properties = properties;
    }

    @Override
    public String mode() {
        return "aliyun";
    }

    @Override
    public void verify(String token, String remoteAddress) {
        AuthProperties.HumanVerification config = properties.getHumanVerification();
        if (token == null || token.isBlank()) {
            throw failed();
        }
        if (config.getSceneId().isBlank() || config.getEndpoint().isBlank()) {
            throw unavailable();
        }

        try {
            VerifyIntelligentCaptchaRequest request = new VerifyIntelligentCaptchaRequest()
                    .setCaptchaVerifyParam(token)
                    .setSceneId(config.getSceneId());
            VerifyIntelligentCaptchaResponse response = client(config).verifyIntelligentCaptcha(request);
            VerifyIntelligentCaptchaResponseBody body = response == null ? null : response.getBody();
            if (body == null || !Boolean.TRUE.equals(body.getSuccess())) {
                log.warn("Alibaba Cloud Captcha request failed, requestId={}, code={}",
                        body == null ? null : body.getRequestId(), body == null ? null : body.getCode());
                throw unavailable();
            }
            VerifyIntelligentCaptchaResponseBody.VerifyIntelligentCaptchaResponseBodyResult result = body.getResult();
            if (result == null || !Boolean.TRUE.equals(result.getVerifyResult())) {
                log.info("Alibaba Cloud Captcha rejected verification, requestId={}, verifyCode={}",
                        body.getRequestId(), result == null ? null : result.getVerifyCode());
                throw failed();
            }
        } catch (AuthApiException exception) {
            throw exception;
        } catch (Exception exception) {
            log.warn("Alibaba Cloud Captcha is unavailable: {}", exception.getClass().getSimpleName());
            throw unavailable();
        }
    }

    private Client client(AuthProperties.HumanVerification config) throws Exception {
        Client current = client;
        if (current != null) {
            return current;
        }
        synchronized (clientMonitor) {
            if (client == null) {
                com.aliyun.credentials.Client credentials = new com.aliyun.credentials.Client();
                Config sdkConfig = new Config()
                        .setCredential(credentials)
                        .setEndpoint(config.getEndpoint())
                        .setConnectTimeout(5_000)
                        .setReadTimeout(5_000);
                client = new Client(sdkConfig);
            }
            return client;
        }
    }

    private AuthApiException failed() {
        return new AuthApiException(HttpStatus.BAD_REQUEST, "HUMAN_VERIFICATION_FAILED",
                "人机验证未通过，请重新完成验证。");
    }

    private AuthApiException unavailable() {
        return new AuthApiException(HttpStatus.SERVICE_UNAVAILABLE,
                "HUMAN_VERIFICATION_UNAVAILABLE", "人机验证服务暂时不可用，请稍后重试。");
    }
}
