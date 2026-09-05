package com.taqlyn.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Validates that Java applications can consume the Kotlin SDK via seamless Java interoperability.
 */
public class JavaInteropTest {

    @Test
    public void testJavaCanCallSignerStaticMethods() {
        PrivateKey key = Signer.loadPkcs8Pem(SignerTest.GOLDEN_PEM);
        String signature = Signer.sign(
                key,
                "POST",
                "/v1/short-links",
                SignerTest.GOLDEN_TIMESTAMP,
                SignerTest.GOLDEN_CLIENT_ID,
                SignerTest.GOLDEN_BODY.getBytes(StandardCharsets.UTF_8));
        assertEquals(SignerTest.GOLDEN_SIGNATURE, signature);

        Map<String, String> headers = Signer.signedHeaders(
                key,
                SignerTest.GOLDEN_CLIENT_ID,
                "POST",
                "/v1/short-links",
                SignerTest.GOLDEN_BODY.getBytes(StandardCharsets.UTF_8),
                SignerTest.GOLDEN_TIMESTAMP);
        assertEquals(SignerTest.GOLDEN_CLIENT_ID, headers.get("X-Taqlyn-Client-Id"));
    }

    @Test
    public void testJavaCanConstructClientAndCreateShortLink() throws Exception {
        AtomicReference<String> method = new AtomicReference<>();
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        AtomicReference<String> clientId = new AtomicReference<>();

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/short-links", exchange -> {
            method.set(exchange.getRequestMethod());
            path.set(exchange.getRequestURI().getPath());
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            clientId.set(exchange.getRequestHeaders().getFirst("X-Taqlyn-Client-Id"));

            byte[] response = """
                    {"id":"sl_java","code":"Jv12Cd","shortUrl":"https://go.localhost/Jv12Cd",\
                    "host":"go.localhost","mode":"web_only",\
                    "destinationWeb":"https://example.com/java","env":"sandbox",\
                    "orgId":"org_java","appId":"app_java"}\
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(201, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        try {
            // Test Java constructors
            Client client2Args = new Client(SignerTest.GOLDEN_CLIENT_ID, SignerTest.GOLDEN_PEM);
            assertEquals("https://api.taqlyn.com", client2Args.getBaseUri().toString());

            Client client3Args = new Client(
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/",
                    SignerTest.GOLDEN_CLIENT_ID,
                    SignerTest.GOLDEN_PEM);

            Client client = new Client(
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/",
                    SignerTest.GOLDEN_CLIENT_ID,
                    SignerTest.GOLDEN_PEM,
                    HttpClient.newHttpClient(),
                    Clock.fixed(Instant.ofEpochSecond(SignerTest.GOLDEN_TIMESTAMP), ZoneOffset.UTC));

            // Test CreateShortLinkRequest with Java record-style and getter accessors
            Client.CreateShortLinkRequest req = new Client.CreateShortLinkRequest(
                    "https://example.com/java", "web_only");
            assertEquals("https://example.com/java", req.destinationWeb());
            assertEquals("https://example.com/java", req.getDestinationWeb());
            assertEquals("web_only", req.mode());
            assertEquals("web_only", req.getMode());

            Client.ShortLink link = client.createShortLink(req);

            assertEquals("https://go.localhost/Jv12Cd", link.shortUrl());
            assertEquals("https://go.localhost/Jv12Cd", link.getShortUrl());
            assertEquals("sl_java", link.id());
            assertEquals("sl_java", link.getId());
            assertEquals("Jv12Cd", link.code());
            assertEquals("POST", method.get());
            assertEquals("/v1/short-links", path.get());
        } finally {
            server.stop(0);
        }
    }
}
