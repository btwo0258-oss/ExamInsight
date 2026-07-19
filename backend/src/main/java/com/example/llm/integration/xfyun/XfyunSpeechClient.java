package com.example.llm.integration.xfyun;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

@Component
public class XfyunSpeechClient {

    private final XfyunConfig config;
    private final XfyunAuthSigner signer;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

    public XfyunSpeechClient(XfyunConfig config, XfyunAuthSigner signer, ObjectMapper objectMapper) {
        this.config = config;
        this.signer = signer;
        this.objectMapper = objectMapper;
    }

    public String transcribe(byte[] source, String fileName) {
        config.requireApiCredentials();
        boolean mp3 = fileName != null && fileName.toLowerCase().endsWith(".mp3");
        byte[] audio = mp3 ? source : stripWaveHeader(source);
        String encoding = mp3 ? "lame" : "raw";
        CompletableFuture<String> result = new CompletableFuture<>();
        StringBuilder transcript = new StringBuilder();
        StringBuilder frame = new StringBuilder();

        WebSocket.Listener listener = new WebSocket.Listener() {
            @Override
            public void onOpen(WebSocket webSocket) {
                webSocket.request(1);
                CompletableFuture.runAsync(() -> sendAudio(webSocket, audio, encoding, result));
            }

            @Override
            public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                frame.append(data);
                if (last) {
                    try {
                        JsonNode root = objectMapper.readTree(frame.toString());
                        frame.setLength(0);
                        int code = root.path("header").path("code").asInt(0);
                        if (code != 0) {
                            result.completeExceptionally(new IllegalStateException(
                                    root.path("header").path("message").asText("语音识别失败")));
                        } else {
                            String encodedText = root.path("payload").path("result").path("text").asText();
                            if (!encodedText.isBlank()) appendRecognition(encodedText, transcript);
                            if (root.path("header").path("status").asInt() == 2) result.complete(transcript.toString());
                        }
                    } catch (Exception e) {
                        result.completeExceptionally(e);
                    }
                }
                webSocket.request(1);
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public void onError(WebSocket webSocket, Throwable error) {
                result.completeExceptionally(error);
            }
        };

        WebSocket socket = httpClient.newWebSocketBuilder().buildAsync(
                signer.signedUrl(config.getSpeechUrl(), "GET", config.getApiKey(), config.getApiSecret()), listener).join();
        try {
            String value = result.get(100, TimeUnit.SECONDS).trim();
            if (value.isBlank()) throw new IllegalStateException("录音中没有识别到有效语音");
            return value;
        } catch (Exception e) {
            if (e instanceof IllegalStateException) throw (IllegalStateException) e;
            throw new IllegalStateException("语音识别调用失败: " + e.getMessage(), e);
        } finally {
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "done");
        }
    }

    private void sendAudio(WebSocket socket, byte[] audio, String encoding, CompletableFuture<String> result) {
        try {
            int frameSize = 1280;
            int sequence = 1;
            for (int offset = 0; offset < audio.length; offset += frameSize) {
                int length = Math.min(frameSize, audio.length - offset);
                byte[] chunk = Arrays.copyOfRange(audio, offset, offset + length);
                int status = offset == 0 ? 0 : 1;
                Map<String, Object> body = audioFrame(chunk, encoding, sequence++, status, status == 0);
                socket.sendText(objectMapper.writeValueAsString(body), true).join();
                Thread.sleep(40);
            }
            socket.sendText(objectMapper.writeValueAsString(audioFrame(
                    new byte[0], encoding, sequence, 2, false)), true).join();
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            result.completeExceptionally(e);
        }
    }

    private Map<String, Object> audioFrame(byte[] chunk, String encoding, int sequence, int status, boolean first) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("header", Map.of("app_id", config.getAppId(), "status", status));
        if (first) {
            root.put("parameter", Map.of("iat", Map.of(
                    "domain", "slm", "language", "zh_cn", "accent", "mandarin", "eos", 6000,
                    "result", Map.of("encoding", "utf8", "compress", "raw", "format", "json"))));
        }
        root.put("payload", Map.of("audio", Map.of(
                "encoding", encoding, "sample_rate", 16000, "channels", 1, "bit_depth", 16,
                "seq", sequence, "status", status, "audio", Base64.getEncoder().encodeToString(chunk))));
        return root;
    }

    private void appendRecognition(String encodedText, StringBuilder transcript) throws Exception {
        String json = new String(Base64.getDecoder().decode(encodedText), java.nio.charset.StandardCharsets.UTF_8);
        JsonNode root = objectMapper.readTree(json);
        JsonNode words = root.path("ws");
        if (!words.isArray()) return;
        for (JsonNode word : words) {
            JsonNode candidates = word.path("cw");
            if (candidates.isArray() && !candidates.isEmpty()) transcript.append(candidates.get(0).path("w").asText(""));
        }
    }

    private byte[] stripWaveHeader(byte[] source) {
        if (source.length < 44 || source[0] != 'R' || source[1] != 'I' || source[2] != 'F' || source[3] != 'F') {
            throw new IllegalArgumentException("语音识别仅支持16kHz单声道PCM WAV或MP3文件");
        }
        int offset = 12;
        while (offset + 8 <= source.length) {
            String id = new String(source, offset, 4, java.nio.charset.StandardCharsets.US_ASCII);
            int size = (source[offset + 4] & 0xff) | ((source[offset + 5] & 0xff) << 8)
                    | ((source[offset + 6] & 0xff) << 16) | ((source[offset + 7] & 0xff) << 24);
            if ("data".equals(id) && offset + 8 + size <= source.length) {
                return Arrays.copyOfRange(source, offset + 8, offset + 8 + size);
            }
            offset += 8 + Math.max(0, size) + (size & 1);
        }
        throw new IllegalArgumentException("WAV文件缺少有效PCM音频数据");
    }
}
