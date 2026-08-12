package com.taqlyn.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ClientTest {
    @Test
    void createShortLinkSendsSignedPostAndReturnsResponse() throws Exception {
        AtomicReference<String> method = new AtomicReference<>();
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        AtomicReference<String> clientId = new AtomicReference<>();
        AtomicReference<String> timestamp = new AtomicReference<>();
        AtomicReference<String> signature = new AtomicReference<>();

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/short-links", exchange -> {
            method.set(exchange.getRequestMethod());
            path.set(exchange.getRequestURI().getPath());
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            clientId.set(exchange.getRequestHeaders().getFirst("X-Taqlyn-Client-Id"));
            timestamp.set(exchange.getRequestHeaders().getFirst("X-Taqlyn-Timestamp"));
            signature.set(exchange.getRequestHeaders().getFirst("X-Taqlyn-Signature"));

            byte[] response = """
                    {"id":"sl_1","code":"Ab12Cd","shortUrl":"https://go.localhost/Ab12Cd",\
                    "host":"go.localhost","mode":"web_only",\
                    "destinationWeb":"https://example.com/offer","env":"sandbox",\
                    "orgId":"org_1","appId":"app_1"}\
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(201, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        try {
            Client client = new Client(
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/",
                    SignerTest.GOLDEN_CLIENT_ID,
                    SignerTest.GOLDEN_PEM,
                    java.net.http.HttpClient.newHttpClient(),
                    Clock.fixed(Instant.ofEpochSecond(SignerTest.GOLDEN_TIMESTAMP), ZoneOffset.UTC));

            Client.ShortLink link = client.createShortLink(
                    new Client.CreateShortLinkRequest(
                            "https://example.com/offer", "web_only"));

            assertEquals("https://go.localhost/Ab12Cd", link.shortUrl());
            assertEquals("POST", method.get());
            assertEquals("/v1/short-links", path.get());
            assertEquals(SignerTest.GOLDEN_BODY, body.get());
            assertEquals(SignerTest.GOLDEN_CLIENT_ID, clientId.get());
            assertEquals("1700000000", timestamp.get());
            assertEquals(SignerTest.GOLDEN_SIGNATURE, signature.get());
        } finally {
            server.stop(0);
        }
    }
}
