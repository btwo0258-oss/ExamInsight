package com.example.llm.asset.processing.security;

import com.example.llm.asset.processing.config.AssetProcessingProperties;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class ClamAvFileSecurityScannerTest {

    @Test
    void sendsClamdInstreamFramesAndAcceptsCleanResult() throws Exception {
        byte[] payload = "safe exam notes".getBytes(StandardCharsets.UTF_8);
        try (ServerSocket server = new ServerSocket(0)) {
            CompletableFuture<byte[]> received = serveOne(server, "stream: OK\0");
            FileSecurityScanner.ScanResult result = scanner(server.getLocalPort())
                    .scan(new ByteArrayInputStream(payload));

            assertThat(result.clean()).isTrue();
            assertThat(received.get(3, TimeUnit.SECONDS)).isEqualTo(payload);
        }
    }

    @Test
    void mapsFoundReplyToRejectedFileWithoutExposingItAsInfrastructureFailure() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            CompletableFuture<byte[]> received = serveOne(server, "stream: Eicar-Test-Signature FOUND\0");
            FileSecurityScanner.ScanResult result = scanner(server.getLocalPort())
                    .scan(new ByteArrayInputStream("infected".getBytes(StandardCharsets.UTF_8)));

            assertThat(result.clean()).isFalse();
            assertThat(result.threatName()).isEqualTo("Eicar-Test-Signature");
            received.get(3, TimeUnit.SECONDS);
        }
    }

    private CompletableFuture<byte[]> serveOne(ServerSocket server, String response) {
        return CompletableFuture.supplyAsync(() -> {
            try (Socket socket = server.accept()) {
                DataInputStream input = new DataInputStream(socket.getInputStream());
                byte[] command = input.readNBytes("zINSTREAM\0".length());
                assertThat(new String(command, StandardCharsets.US_ASCII)).isEqualTo("zINSTREAM\0");
                java.io.ByteArrayOutputStream payload = new java.io.ByteArrayOutputStream();
                int length;
                while ((length = input.readInt()) > 0) {
                    payload.write(input.readNBytes(length));
                }
                OutputStream output = socket.getOutputStream();
                output.write(response.getBytes(StandardCharsets.UTF_8));
                output.flush();
                return payload.toByteArray();
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        });
    }

    private ClamAvFileSecurityScanner scanner(int port) {
        AssetProcessingProperties properties = new AssetProcessingProperties();
        properties.getScanner().setHost("127.0.0.1");
        properties.getScanner().setPort(port);
        properties.getScanner().setConnectTimeout(Duration.ofSeconds(2));
        properties.getScanner().setReadTimeout(Duration.ofSeconds(2));
        properties.getScanner().setStreamChunkSize(4096);
        return new ClamAvFileSecurityScanner(properties);
    }
}
