package com.taqlyn.sdk

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.http.HttpClient
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicReference
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ClientTest {
    @Test
    fun createShortLinkSendsSignedPostAndReturnsResponse() {
        val method = AtomicReference<String>()
        val path = AtomicReference<String>()
        val body = AtomicReference<String>()
        val clientId = AtomicReference<String>()
        val timestamp = AtomicReference<String>()
        val signature = AtomicReference<String>()

        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/short-links") { exchange ->
            method.set(exchange.requestMethod)
            path.set(exchange.requestURI.path)
            body.set(String(exchange.requestBody.readAllBytes(), StandardCharsets.UTF_8))
            clientId.set(exchange.requestHeaders.getFirst("X-Taqlyn-Client-Id"))
            timestamp.set(exchange.requestHeaders.getFirst("X-Taqlyn-Timestamp"))
            signature.set(exchange.requestHeaders.getFirst("X-Taqlyn-Signature"))

            val response = """
                {"id":"sl_1","code":"Ab12Cd","shortUrl":"https://go.localhost/Ab12Cd",
                "host":"go.localhost","mode":"web_only",
                "destinationWeb":"https://example.com/offer","env":"sandbox",
                "orgId":"org_1","appId":"app_1"}
            """.trimIndent().toByteArray(StandardCharsets.UTF_8)
            exchange.responseHeaders.set("Content-Type", "application/json")
            exchange.sendResponseHeaders(201, response.size.toLong())
            exchange.responseBody.write(response)
            exchange.close()
        }
        server.start()

        try {
            val client = Client(
                baseUrl = "http://127.0.0.1:${server.address.port}/",
                clientId = SignerTest.GOLDEN_CLIENT_ID,
                privateKeyPem = SignerTest.GOLDEN_PEM,
                httpClient = HttpClient.newHttpClient(),
                clock = Clock.fixed(Instant.ofEpochSecond(SignerTest.GOLDEN_TIMESTAMP), ZoneOffset.UTC)
            )

            val link = client.createShortLink(
                CreateShortLinkRequest(
                    destinationWeb = "https://example.com/offer",
                    mode = "web_only"
                )
            )

            assertEquals("https://go.localhost/Ab12Cd", link.shortUrl)
            assertEquals("POST", method.get())
            assertEquals("/v1/short-links", path.get())
            assertEquals(SignerTest.GOLDEN_BODY, body.get())
            assertEquals(SignerTest.GOLDEN_CLIENT_ID, clientId.get())
            assertEquals("1700000000", timestamp.get())
            assertEquals(SignerTest.GOLDEN_SIGNATURE, signature.get())
        } finally {
            server.stop(0)
        }
    }
}
