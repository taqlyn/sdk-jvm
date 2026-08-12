package com.taqlyn.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SignerTest {
    static final String GOLDEN_PEM = """
            -----BEGIN PRIVATE KEY-----
            MC4CAQAwBQYDK2VwBCIEIAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8g
            -----END PRIVATE KEY-----
            """;
    static final String GOLDEN_BODY =
            "{\"destinationWeb\":\"https://example.com/offer\",\"mode\":\"web_only\"}";
    static final long GOLDEN_TIMESTAMP = 1_700_000_000L;
    static final String GOLDEN_CLIENT_ID = "app_test_abc";
    static final String GOLDEN_SIGNATURE =
            "zTe0VimeWAe6dzpPxAIn+DDR46E58G63ypiSTkXd1jT1o3oxYJ4jzAof05lf3s/8sbZ7l46VjDh8ohtB+NISAA==";

    @Test
    void canonicalMessageMatchesNodeVector() {
        String message = Signer.canonicalMessage(
                "post",
                "/v1/short-links",
                GOLDEN_TIMESTAMP,
                GOLDEN_CLIENT_ID,
                GOLDEN_BODY.getBytes(StandardCharsets.UTF_8));

        assertEquals(
                """
                taqlyn-v1
                POST
                /v1/short-links
                1700000000
                app_test_abc
                45b1df36051aec5657b955f266811307e521e19fad0baa2dd052f2ed4a8bd6c7""",
                message);
    }

    @Test
    void signsGoldenPkcs8PemVector() {
        PrivateKey key = Signer.loadPkcs8Pem(GOLDEN_PEM);

        String signature = Signer.sign(
                key,
                "POST",
                "/v1/short-links",
                GOLDEN_TIMESTAMP,
                GOLDEN_CLIENT_ID,
                GOLDEN_BODY.getBytes(StandardCharsets.UTF_8));

        assertEquals(GOLDEN_SIGNATURE, signature);
        Map<String, String> headers = Signer.signedHeaders(
                key,
                GOLDEN_CLIENT_ID,
                "POST",
                "/v1/short-links",
                GOLDEN_BODY.getBytes(StandardCharsets.UTF_8),
                GOLDEN_TIMESTAMP);
        assertEquals(GOLDEN_CLIENT_ID, headers.get("X-Taqlyn-Client-Id"));
        assertEquals("1700000000", headers.get("X-Taqlyn-Timestamp"));
        assertEquals(GOLDEN_SIGNATURE, headers.get("X-Taqlyn-Signature"));
    }

    @Test
    void acceptsEscapedNewlinesAndRejectsSecretHandle() {
        String escapedPem = GOLDEN_PEM.trim().replace("\n", "\\n");
        assertEquals("EdDSA", Signer.loadPkcs8Pem(escapedPem).getAlgorithm());

        IllegalArgumentException error =
                assertThrows(IllegalArgumentException.class, () -> Signer.loadPkcs8Pem("sk_test_deadbeef"));
        assertEquals(
                "privateKey: sk_* is a UX handle only; pass the PKCS#8 PEM returned at key issue",
                error.getMessage());
    }
}
