package com.example.llm.auth.gateway;

import com.example.llm.auth.api.AuthApiException;
import com.example.llm.auth.config.AuthProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfiguredHumanVerificationGatewayTest {
    @Test
    void routesOnlyToTheExplicitlyConfiguredProvider() {
        AuthProperties properties = new AuthProperties();
        properties.getHumanVerification().setMode("ALIYUN");
        RecordingProvider aliyun = new RecordingProvider("aliyun");
        RecordingProvider remote = new RecordingProvider("remote");
        ConfiguredHumanVerificationGateway gateway = new ConfiguredHumanVerificationGateway(
                properties, List.of(aliyun, remote));

        gateway.verify("proof", "127.0.0.1");

        assertThat(aliyun.token).isEqualTo("proof");
        assertThat(aliyun.remoteAddress).isEqualTo("127.0.0.1");
        assertThat(remote.token).isNull();
    }

    @Test
    void disabledOrUnknownModeFailsClosed() {
        AuthProperties properties = new AuthProperties();
        properties.getHumanVerification().setMode("disabled");
        ConfiguredHumanVerificationGateway gateway = new ConfiguredHumanVerificationGateway(
                properties, List.of(new RecordingProvider("aliyun")));

        assertThatThrownBy(() -> gateway.verify("proof", "127.0.0.1"))
                .isInstanceOf(AuthApiException.class)
                .satisfies(error -> assertThat(((AuthApiException) error).code())
                        .isEqualTo("HUMAN_VERIFICATION_UNAVAILABLE"));
    }

    private static final class RecordingProvider implements HumanVerificationProvider {
        private final String mode;
        private String token;
        private String remoteAddress;

        private RecordingProvider(String mode) {
            this.mode = mode;
        }

        @Override
        public String mode() {
            return mode;
        }

        @Override
        public void verify(String token, String remoteAddress) {
            this.token = token;
            this.remoteAddress = remoteAddress;
        }
    }
}
