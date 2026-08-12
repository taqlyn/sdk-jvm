package com.taqlyn.sdk;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.time.Clock;
import java.util.Map;
import java.util.Objects;

/** Minimal Taqlyn server client for privileged short-link creation. */
public final class Client {
    private static final String SHORT_LINKS_PATH = "/v1/short-links";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final URI baseUri;
    private final String clientId;
    private final PrivateKey privateKey;
    private final HttpClient httpClient;
    private final Clock clock;

    public Client(String baseUrl, String clientId, String privateKeyPem) {
        this(baseUrl, clientId, privateKeyPem, HttpClient.newHttpClient(), Clock.systemUTC());
    }

    public Client(
            String baseUrl,
            String clientId,
            String privateKeyPem,
            HttpClient httpClient,
            Clock clock) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("baseUrl is required");
        }
        if (clientId == null
                || !(clientId.startsWith("app_test_") || clientId.startsWith("app_live_"))) {
            throw new IllegalArgumentException("clientId must start with app_test_ or app_live_");
        }
        this.baseUri = URI.create(baseUrl.replaceAll("/+$", ""));
        this.clientId = clientId.trim();
        this.privateKey = Signer.loadPkcs8Pem(privateKeyPem);
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public ShortLink createShortLink(CreateShortLinkRequest input) {
        Objects.requireNonNull(input, "input");
        if (input.destinationWeb() == null || input.destinationWeb().isBlank()) {
            throw new IllegalArgumentException("destinationWeb is required");
        }

        byte[] body;
        try {
            body = JSON.writeValueAsBytes(input);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to serialize short-link request", exception);
        }

        long timestamp = clock.instant().getEpochSecond();
        Map<String, String> signingHeaders = Signer.signedHeaders(
                privateKey, clientId, "POST", SHORT_LINKS_PATH, body, timestamp);
        HttpRequest.Builder request = HttpRequest.newBuilder(baseUri.resolve(SHORT_LINKS_PATH))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body));
        signingHeaders.forEach(request::header);

        try {
            HttpResponse<byte[]> response =
                    httpClient.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw apiException(response.statusCode(), response.body());
            }
            return JSON.readValue(response.body(), ShortLink.class);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new TaqlynException("Request interrupted", exception);
        } catch (IOException exception) {
            throw new TaqlynException("Taqlyn API request failed", exception);
        }
    }

    private static TaqlynApiException apiException(int status, byte[] responseBody) {
        String code = null;
        String message = new String(responseBody, StandardCharsets.UTF_8);
        try {
            JsonNode json = JSON.readTree(responseBody);
            if (json != null) {
                code = json.path("error").isTextual() ? json.path("error").asText() : null;
                if (json.path("message").isTextual()) {
                    message = json.path("message").asText();
                }
            }
        } catch (IOException ignored) {
            // Keep the raw response body as the error message.
        }
        return new TaqlynApiException(status, code, message);
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonPropertyOrder({"destinationWeb", "mode", "destinationPath", "params", "env"})
    public record CreateShortLinkRequest(
            String destinationWeb,
            String mode,
            String destinationPath,
            Map<String, Object> params,
            String env) {
        public CreateShortLinkRequest(String destinationWeb, String mode) {
            this(destinationWeb, mode, null, null, null);
        }

        public CreateShortLinkRequest(String destinationWeb) {
            this(destinationWeb, null, null, null, null);
        }
    }

    public record ShortLink(
            String id,
            String code,
            String shortUrl,
            String host,
            String mode,
            String destinationWeb,
            String env,
            String orgId,
            String appId) {}

    public static class TaqlynException extends RuntimeException {
        public TaqlynException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static final class TaqlynApiException extends TaqlynException {
        private final int status;
        private final String code;

        private TaqlynApiException(int status, String code, String message) {
            super("Taqlyn API returned HTTP " + status + ": " + message, null);
            this.status = status;
            this.code = code;
        }

        public int status() {
            return status;
        }

        public String code() {
            return code;
        }
    }
}
