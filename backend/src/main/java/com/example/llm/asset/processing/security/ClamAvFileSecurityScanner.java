package com.example.llm.asset.processing.security;

import com.example.llm.asset.processing.ProcessingFailure;
import com.example.llm.asset.processing.config.AssetProcessingProperties;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

@Component
public class ClamAvFileSecurityScanner implements FileSecurityScanner {
    private static final byte[] INSTREAM_COMMAND = "zINSTREAM\0".getBytes(StandardCharsets.US_ASCII);
    private static final int MAX_RESPONSE_BYTES = 4096;

    private final AssetProcessingProperties properties;

    public ClamAvFileSecurityScanner(AssetProcessingProperties properties) {
        this.properties = properties;
    }

    @Override
    public String scannerKey() {
        return "clamav-clamd";
    }

    @Override
    public String scannerVersion() {
        return "instream-v1";
    }

    @Override
    public ScanResult scan(InputStream content) {
        AssetProcessingProperties.Scanner config = properties.getScanner();
        if (!"clamav".equalsIgnoreCase(config.getMode())) {
            throw ProcessingFailure.retryable(
                    "FILE_SCANNER_UNAVAILABLE",
                    "文件安全扫描服务暂时不可用。",
                    null);
        }

        try (Socket socket = new Socket()) {
            socket.connect(
                    new InetSocketAddress(config.getHost(), config.getPort()),
                    Math.toIntExact(config.getConnectTimeout().toMillis()));
            socket.setSoTimeout(Math.toIntExact(config.getReadTimeout().toMillis()));

            DataOutputStream output = new DataOutputStream(socket.getOutputStream());
            output.write(INSTREAM_COMMAND);
            byte[] buffer = new byte[config.getStreamChunkSize()];
            int read;
            while ((read = content.read(buffer)) >= 0) {
                if (read == 0) {
                    continue;
                }
                output.writeInt(read);
                output.write(buffer, 0, read);
            }
            output.writeInt(0);
            output.flush();

            String response = readNullTerminated(socket.getInputStream());
            if (response.endsWith(" OK")) {
                return ScanResult.cleanFile();
            }
            if (response.endsWith(" FOUND")) {
                int separator = response.indexOf(": ");
                String threat = separator >= 0
                        ? response.substring(separator + 2, response.length() - " FOUND".length()).trim()
                        : "MALWARE";
                return ScanResult.infected(threat.isBlank() ? "MALWARE" : threat);
            }
            throw ProcessingFailure.retryable(
                    "FILE_SCANNER_ERROR",
                    "文件安全扫描服务暂时不可用。",
                    new IOException("Unexpected clamd response"));
        } catch (ProcessingFailure failure) {
            throw failure;
        } catch (IOException | ArithmeticException exception) {
            throw ProcessingFailure.retryable(
                    "FILE_SCANNER_UNAVAILABLE",
                    "文件安全扫描服务暂时不可用。",
                    exception);
        }
    }

    private String readNullTerminated(InputStream input) throws IOException {
        ByteArrayOutputStream response = new ByteArrayOutputStream();
        for (int index = 0; index < MAX_RESPONSE_BYTES; index++) {
            int value = input.read();
            if (value < 0 || value == 0) {
                break;
            }
            response.write(value);
        }
        if (response.size() == 0 || response.size() >= MAX_RESPONSE_BYTES) {
            throw new IOException("Invalid clamd response length");
        }
        return response.toString(StandardCharsets.UTF_8).trim();
    }
}
