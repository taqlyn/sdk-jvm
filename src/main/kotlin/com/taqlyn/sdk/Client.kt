package com.taqlyn.sdk

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonPropertyOrder
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.security.PrivateKey
import java.time.Clock
import java.util.Objects

/** Minimal Taqlyn server client for privileged short-link creation. */
class Client @JvmOverloads constructor(
    baseUrl: String?,
    val clientId: String,
    privateKeyPem: String,
    val httpClient: HttpClient = HttpClient.newHttpClient(),
    val clock: Clock = Clock.systemUTC()
) {
    val baseUri: URI
    private val privateKey: PrivateKey

    init {
        val effectiveBaseUrl = resolveBaseUrl(baseUrl)
        require(effectiveBaseUrl.isNotBlank()) { "baseUrl is required" }
        require(clientId.startsWith("app_test_") || clientId.startsWith("app_live_")) {
            "clientId must start with app_test_ or app_live_"
        }
        this.baseUri = URI.create(effectiveBaseUrl.replace("/+$".toRegex(), ""))
        this.privateKey = Signer.loadPkcs8Pem(privateKeyPem)
        Objects.requireNonNull(httpClient, "httpClient")
        Objects.requireNonNull(clock, "clock")
    }

    /** 2-argument constructor for Java and Kotlin callers (clientId, privateKeyPem). */
    constructor(
        clientId: String,
        privateKeyPem: String
    ) : this(
        baseUrl = null,
        clientId = clientId,
        privateKeyPem = privateKeyPem,
        httpClient = HttpClient.newHttpClient(),
        clock = Clock.systemUTC()
    )

    @Throws(TaqlynException::class)
    fun createShortLink(input: CreateShortLinkRequest): ShortLink {
        Objects.requireNonNull(input, "input")
        require(input.destinationWeb.isNotBlank()) { "destinationWeb is required" }

        val body = try {
            JSON.writeValueAsBytes(input)
        } catch (exception: IOException) {
            throw IllegalArgumentException("Unable to serialize short-link request", exception)
        }

        val timestamp = clock.instant().epochSecond
        val signingHeaders = Signer.signedHeaders(
            privateKey, clientId, "POST", SHORT_LINKS_PATH, body, timestamp
        )

        val request = HttpRequest.newBuilder(baseUri.resolve(SHORT_LINKS_PATH))
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofByteArray(body))

        signingHeaders.forEach { (k, v) -> request.header(k, v) }

        return try {
            val response = httpClient.send(request.build(), HttpResponse.BodyHandlers.ofByteArray())
            if (response.statusCode() !in 200..299) {
                throw apiException(response.statusCode(), response.body())
            }
            JSON.readValue(response.body(), ShortLink::class.java)
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
            throw TaqlynException("Request interrupted", exception)
        } catch (exception: IOException) {
            throw TaqlynException("Taqlyn API request failed", exception)
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonPropertyOrder("destinationWeb", "mode", "destinationPath", "params", "env")
    data class CreateShortLinkRequest @JvmOverloads constructor(
        val destinationWeb: String,
        val mode: String? = null,
        val destinationPath: String? = null,
        val params: Map<String, Any?>? = null,
        val env: String? = null
    ) {
        // Record-style and getter compatibility for Java callers
        fun destinationWeb(): String = destinationWeb
        fun mode(): String? = mode
        fun destinationPath(): String? = destinationPath
        fun params(): Map<String, Any?>? = params
        fun env(): String? = env
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    data class ShortLink(
        val id: String,
        val code: String,
        val shortUrl: String,
        val host: String,
        val mode: String? = null,
        val destinationWeb: String? = null,
        val env: String? = null,
        val orgId: String? = null,
        val appId: String? = null
    ) {
        // Record-style and getter compatibility for Java callers
        fun id(): String = id
        fun code(): String = code
        fun shortUrl(): String = shortUrl
        fun host(): String = host
        fun mode(): String? = mode
        fun destinationWeb(): String? = destinationWeb
        fun env(): String? = env
        fun orgId(): String? = orgId
        fun appId(): String? = appId
    }

    open class TaqlynException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

    class TaqlynApiException(
        val status: Int,
        val code: String?,
        message: String
    ) : TaqlynException("Taqlyn API returned HTTP $status: $message", null) {
        fun status(): Int = status
        fun code(): String? = code
    }

    companion object {
        const val DEFAULT_API_BASE_URL: String = "https://api.taqlyn.com"
        private const val SHORT_LINKS_PATH = "/v1/short-links"
        private val JSON: ObjectMapper = ObjectMapper().registerKotlinModule()

        @JvmStatic
        fun resolveBaseUrl(baseUrl: String?): String {
            if (!baseUrl.isNullOrBlank()) {
                return baseUrl.trim()
            }
            val envBase = System.getenv("TAQLYN_BASE_URL")
            if (!envBase.isNullOrBlank()) {
                return envBase.trim()
            }
            val envApi = System.getenv("TAQLYN_API_URL")
            if (!envApi.isNullOrBlank()) {
                return envApi.trim()
            }
            return DEFAULT_API_BASE_URL
        }

        private fun apiException(status: Int, responseBody: ByteArray): TaqlynApiException {
            var code: String? = null
            var message = String(responseBody, StandardCharsets.UTF_8)
            try {
                val json: JsonNode? = JSON.readTree(responseBody)
                if (json != null) {
                    if (json.path("error").isTextual) {
                        code = json.path("error").asText()
                    }
                    if (json.path("message").isTextual) {
                        message = json.path("message").asText()
                    }
                }
            } catch (_: IOException) {
                // Keep raw response body as error message
            }
            return TaqlynApiException(status, code, message)
        }
    }
}

// Top-level aliases for idiomatic Kotlin imports
typealias CreateShortLinkRequest = Client.CreateShortLinkRequest
typealias ShortLink = Client.ShortLink
typealias TaqlynException = Client.TaqlynException
typealias TaqlynApiException = Client.TaqlynApiException
