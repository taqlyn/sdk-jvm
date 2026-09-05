package com.taqlyn.sdk

import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import java.util.HexFormat
import java.util.LinkedHashMap
import java.util.Locale
import java.util.Objects

/** Ed25519 request signing compatible with Taqlyn's `TaqlynEd25519` scheme. */
object Signer {
    private const val PRIVATE_KEY_BEGIN = "-----BEGIN PRIVATE KEY-----"
    private const val PRIVATE_KEY_END = "-----END PRIVATE KEY-----"

    @JvmStatic
    fun canonicalMessage(
        method: String,
        path: String,
        unixTimestamp: Long,
        clientId: String,
        body: ByteArray
    ): String {
        Objects.requireNonNull(method, "method")
        Objects.requireNonNull(path, "path")
        Objects.requireNonNull(clientId, "clientId")
        Objects.requireNonNull(body, "body")

        return listOf(
            "taqlyn-v1",
            method.uppercase(Locale.ROOT),
            path,
            unixTimestamp.toString(),
            clientId,
            sha256Hex(body)
        ).joinToString("\n")
    }

    @JvmStatic
    fun loadPkcs8Pem(pem: String): PrivateKey {
        Objects.requireNonNull(pem, "privateKey")
        val normalized = normalizePem(pem)
        if (normalized.startsWith("sk_")) {
            throw IllegalArgumentException(
                "privateKey: sk_* is a UX handle only; pass the PKCS#8 PEM returned at key issue"
            )
        }
        if (!normalized.contains(PRIVATE_KEY_BEGIN) || !normalized.contains(PRIVATE_KEY_END)) {
            throw IllegalArgumentException(
                "privateKey: expected an Ed25519 PKCS#8 PEM (-----BEGIN PRIVATE KEY-----)"
            )
        }

        val base64 = normalized
            .replace(PRIVATE_KEY_BEGIN, "")
            .replace(PRIVATE_KEY_END, "")
            .replace("\\s".toRegex(), "")
        return try {
            val der = Base64.getDecoder().decode(base64)
            val key = KeyFactory.getInstance("Ed25519")
                .generatePrivate(PKCS8EncodedKeySpec(der))
            if (!"Ed25519".equals(key.algorithm, ignoreCase = true) &&
                !"EdDSA".equals(key.algorithm, ignoreCase = true)
            ) {
                throw IllegalArgumentException("privateKey: PKCS#8 key is not Ed25519")
            }
            key
        } catch (exception: IllegalArgumentException) {
            throw exception
        } catch (exception: Exception) {
            throw IllegalArgumentException("privateKey: invalid Ed25519 PKCS#8 PEM", exception)
        }
    }

    @JvmStatic
    fun sign(
        privateKey: PrivateKey,
        method: String,
        path: String,
        unixTimestamp: Long,
        clientId: String,
        body: ByteArray
    ): String {
        Objects.requireNonNull(privateKey, "privateKey")
        val canonical = canonicalMessage(method, path, unixTimestamp, clientId, body)
        return try {
            val signer = Signature.getInstance("Ed25519")
            signer.initSign(privateKey)
            signer.update(canonical.toByteArray(StandardCharsets.UTF_8))
            Base64.getEncoder().encodeToString(signer.sign())
        } catch (exception: Exception) {
            throw IllegalArgumentException("Unable to sign request with Ed25519 key", exception)
        }
    }

    @JvmStatic
    fun signedHeaders(
        privateKey: PrivateKey,
        clientId: String,
        method: String,
        path: String,
        body: ByteArray,
        unixTimestamp: Long
    ): Map<String, String> {
        val headers = LinkedHashMap<String, String>()
        headers["X-Taqlyn-Client-Id"] = clientId
        headers["X-Taqlyn-Timestamp"] = unixTimestamp.toString()
        headers["X-Taqlyn-Signature"] = sign(privateKey, method, path, unixTimestamp, clientId, body)
        return java.util.Map.copyOf(headers)
    }

    @JvmStatic
    fun normalizePem(pem: String): String {
        val normalized = pem.trim()
        return if (normalized.contains("\\n") && !normalized.contains("\n")) {
            normalized.replace("\\n", "\n")
        } else {
            normalized
        }
    }

    private fun sha256Hex(body: ByteArray): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256").digest(body)
            HexFormat.of().formatHex(digest)
        } catch (exception: Exception) {
            throw IllegalStateException("SHA-256 is unavailable", exception)
        }
    }
}
