package com.example.llm.integration.xfyun;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

@Component
public class XfyunAuthSigner {

    public URI signedUrl(String endpoint, String method, String apiKey, String apiSecret) {
        try {
            URI uri = URI.create(endpoint);
            String date = DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now(java.time.ZoneOffset.UTC));
            String requestLine = method.toUpperCase() + " " + uri.getRawPath() + " HTTP/1.1";
            String signatureOrigin = "host: " + uri.getHost() + "\ndate: " + date + "\n" + requestLine;
            String signature = Base64.getEncoder().encodeToString(hmac("HmacSHA256", apiSecret, signatureOrigin));
            String authorizationOrigin = "api_key=\"" + apiKey
                    + "\", algorithm=\"hmac-sha256\", headers=\"host date request-line\", signature=\""
                    + signature + "\"";
            String authorization = Base64.getEncoder().encodeToString(
                    authorizationOrigin.getBytes(StandardCharsets.UTF_8));
            String query = "authorization=" + encode(authorization)
                    + "&date=" + encode(date)
                    + "&host=" + encode(uri.getHost());
            String separator = endpoint.contains("?") ? "&" : "?";
            return URI.create(endpoint + separator + query);
        } catch (Exception e) {
            throw new IllegalStateException("生成讯飞鉴权地址失败", e);
        }
    }

    public String pptSignature(String appId, String apiSecret, long timestamp) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] md5 = digest.digest((appId + timestamp).getBytes(StandardCharsets.UTF_8));
            StringBuilder auth = new StringBuilder();
            for (byte value : md5) auth.append(String.format("%02x", value & 0xff));
            return Base64.getEncoder().encodeToString(hmac("HmacSHA1", apiSecret, auth.toString()));
        } catch (Exception e) {
            throw new IllegalStateException("生成讯飞 PPT 签名失败", e);
        }
    }

    private byte[] hmac(String algorithm, String secret, String content) throws Exception {
        Mac mac = Mac.getInstance(algorithm);
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), algorithm));
        return mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
