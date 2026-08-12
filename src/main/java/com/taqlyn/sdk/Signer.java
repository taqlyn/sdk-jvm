package com.taqlyn.sdk;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Ed25519 request signing compatible with Taqlyn's {@code TaqlynEd25519} scheme. */
public final class Signer {
    private static final String PRIVATE_KEY_BEGIN = "-----BEGIN PRIVATE KEY-----";
    private static final String PRIVATE_KEY_END = "-----END PRIVATE KEY-----";

    private Signer() {}

    public static String canonicalMessage(
            String method,
            String path,
            long unixTimestamp,
            String clientId,
            byte[] body) {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(clientId, "clientId");
        Objects.requireNonNull(body, "body");

        return String.join(
                "\n",
                "taqlyn-v1",
                method.toUpperCase(Locale.ROOT),
                path,
                Long.toString(unixTimestamp),
                clientId,
                sha256Hex(body));
    }

    public static PrivateKey loadPkcs8Pem(String pem) {
        Objects.requireNonNull(pem, "privateKey");
        String normalized = normalizePem(pem);
        if (normalized.startsWith("sk_")) {
            throw new IllegalArgumentException(
                    "privateKey: sk_* is a UX handle only; pass the PKCS#8 PEM returned at key issue");
        }
        if (!normalized.contains(PRIVATE_KEY_BEGIN) || !normalized.contains(PRIVATE_KEY_END)) {
            throw new IllegalArgumentException(
                    "privateKey: expected an Ed25519 PKCS#8 PEM (-----BEGIN PRIVATE KEY-----)");
        }

        String base64 = normalized
                .replace(PRIVATE_KEY_BEGIN, "")
                .replace(PRIVATE_KEY_END, "")
                .replaceAll("\\s", "");
        try {
            byte[] der = Base64.getDecoder().decode(base64);
            PrivateKey key = KeyFactory.getInstance("Ed25519")
                    .generatePrivate(new PKCS8EncodedKeySpec(der));
            if (!"Ed25519".equalsIgnoreCase(key.getAlgorithm())
                    && !"EdDSA".equalsIgnoreCase(key.getAlgorithm())) {
                throw new IllegalArgumentException("privateKey: PKCS#8 key is not Ed25519");
            }
            return key;
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("privateKey: invalid Ed25519 PKCS#8 PEM", exception);
        }
    }

    public static String sign(
            PrivateKey privateKey,
            String method,
            String path,
            long unixTimestamp,
            String clientId,
            byte[] body) {
        Objects.requireNonNull(privateKey, "privateKey");
        String canonical = canonicalMessage(method, path, unixTimestamp, clientId, body);
        try {
            Signature signer = Signature.getInstance("Ed25519");
            signer.initSign(privateKey);
            signer.update(canonical.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signer.sign());
        } catch (Exception exception) {
            throw new IllegalArgumentException("Unable to sign request with Ed25519 key", exception);
        }
    }

    public static Map<String, String> signedHeaders(
            PrivateKey privateKey,
            String clientId,
            String method,
            String path,
            byte[] body,
            long unixTimestamp) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X-Taqlyn-Client-Id", clientId);
        headers.put("X-Taqlyn-Timestamp", Long.toString(unixTimestamp));
        headers.put(
                "X-Taqlyn-Signature",
                sign(privateKey, method, path, unixTimestamp, clientId, body));
        return Map.copyOf(headers);
    }

    static String normalizePem(String pem) {
        String normalized = pem.trim();
        if (normalized.contains("\\n") && !normalized.contains("\n")) {
            normalized = normalized.replace("\\n", "\n");
        }
        return normalized;
    }

    private static String sha256Hex(byte[] body) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(body);
            return java.util.HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
