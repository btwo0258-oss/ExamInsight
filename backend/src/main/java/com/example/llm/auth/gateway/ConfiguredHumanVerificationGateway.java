package com.example.llm.auth.gateway;

import com.example.llm.auth.api.AuthApiException;
import com.example.llm.auth.config.AuthProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ConfiguredHumanVerificationGateway implements HumanVerificationGateway {
    private final AuthProperties properties;
    private final Map<String, HumanVerificationProvider> providers;

    public ConfiguredHumanVerificationGateway(
            AuthProperties properties,
            List<HumanVerificationProvider> providers) {
        this.properties = properties;
        this.providers = providers.stream().collect(Collectors.toUnmodifiableMap(
                provider -> provider.mode().toLowerCase(Locale.ROOT), Function.identity()));
    }

    @Override
    public void verify(String token, String remoteAddress) {
        String mode = properties.getHumanVerification().getMode().trim().toLowerCase(Locale.ROOT);
        HumanVerificationProvider provider = providers.get(mode);
        if (provider == null) {
            throw new AuthApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "HUMAN_VERIFICATION_UNAVAILABLE", "人机验证服务暂时不可用，请稍后重试。");
        }
        provider.verify(token, remoteAddress);
    }
}
