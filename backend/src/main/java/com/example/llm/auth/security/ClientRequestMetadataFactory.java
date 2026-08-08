package com.example.llm.auth.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.Arrays;

@Component
public class ClientRequestMetadataFactory {
    private final AuthCrypto crypto;

    public ClientRequestMetadataFactory(AuthCrypto crypto) {
        this.crypto = crypto;
    }

    public ClientRequestMetadata from(HttpServletRequest request, String deviceId) {
        String remoteAddress = request.getRemoteAddr();
        String ipPrefix = normalizeIpPrefix(remoteAddress);
        String userAgent = request.getHeader("User-Agent");
        return new ClientRequestMetadata(
                remoteAddress,
                crypto.digest("ip-prefix", ipPrefix),
                userAgent == null ? null : crypto.digest("user-agent", userAgent),
                crypto.digest("device-fingerprint", deviceId));
    }

    public ClientRequestMetadata from(HttpServletRequest request) {
        String remoteAddress = request.getRemoteAddr();
        String ipPrefix = normalizeIpPrefix(remoteAddress);
        String userAgent = request.getHeader("User-Agent");
        return new ClientRequestMetadata(
                remoteAddress,
                crypto.digest("ip-prefix", ipPrefix),
                userAgent == null ? null : crypto.digest("user-agent", userAgent),
                null);
    }

    private String normalizeIpPrefix(String rawAddress) {
        try {
            InetAddress address = InetAddress.getByName(rawAddress);
            byte[] bytes = address.getAddress();
            if (address instanceof Inet6Address) {
                Arrays.fill(bytes, 8, 16, (byte) 0);
            } else {
                bytes[3] = 0;
            }
            return InetAddress.getByAddress(bytes).getHostAddress();
        } catch (Exception exception) {
            return "unknown";
        }
    }
}
