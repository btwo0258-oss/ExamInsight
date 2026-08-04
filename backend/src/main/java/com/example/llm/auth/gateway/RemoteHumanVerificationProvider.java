package com.example.llm.auth.gateway;

import com.example.llm.auth.api.AuthApiException;
import com.example.llm.auth.config.AuthProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

@Component
class RemoteHumanVerificationProvider implements HumanVerificationProvider {
    private final AuthProperties properties;
    private final RestClient restClient = RestClient.create();

    RemoteHumanVerificationProvider(AuthProperties properties) {
        this.properties = properties;
    }

    @Override
    public String mode() {
        return "remote";
    }

    @Override
    public void verify(String token, String remoteAddress) {
        AuthProperties.HumanVerification config = properties.getHumanVerification();
        if (config.getVerifyUrl().isBlank() || config.getSecret().isBlank()) {
            throw unavailable();
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("secret", config.getSecret());
        form.add("response", token);
        if (remoteAddress != null && !remoteAddress.isBlank()) {
            form.add("remoteip", remoteAddress);
        }

        try {
            Map<?, ?> response = restClient.post()
                    .uri(config.getVerifyUrl())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(Map.class);
            if (response == null || !Boolean.TRUE.equals(response.get("success"))) {
                throw failed();
            }
        } catch (AuthApiException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw unavailable();
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
