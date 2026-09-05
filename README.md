# Taqlyn JVM SDK (Kotlin & Java)

**Full guide:** [JVM Server SDK](../../apps/docs/content/server/java.md) on the docs site (not the Android mobile SDK).

Enterprise **server** SDK written in **Kotlin** with complete **Java interoperability** for creating Taqlyn short links with Ed25519 request signing. Compatible with Java 17+, Kotlin 1.9+, Spring Boot, Quarkus, Micronaut, and Ktor.

Maven coordinates: `com.taqlyn:sdk`.

> This package is server-only. It does not include mobile Match, resolve, deferred deep-link, or navigation APIs. For Android mobile apps, use `com.taqlyn:sdk-android`.

## Installation

### Gradle (Kotlin DSL)
```kotlin
implementation("com.taqlyn:sdk:0.1.0")
```

### Gradle (Groovy DSL)
```groovy
implementation 'com.taqlyn:sdk:0.1.0'
```

### Maven
```xml
<dependency>
    <groupId>com.taqlyn</groupId>
    <artifactId>sdk</artifactId>
    <version>0.1.0</version>
</dependency>
```

## Quickstart

Configure the API origin, credential client ID, and the PKCS#8 Ed25519 private key returned when the credential was issued:

```bash
# Optional override: defaults to https://api.taqlyn.com in production
# export TAQLYN_BASE_URL=https://api.taqlyn.com

export TAQLYN_CLIENT_ID=app_test_abc
export TAQLYN_PRIVATE_KEY='-----BEGIN PRIVATE KEY-----
MC4CAQAw...
-----END PRIVATE KEY-----'
```

Do not use an `sk_test_*` or `sk_live_*` value as the private key. Those values are credential handles and cannot sign requests. Literal `\n` sequences in the PEM environment variable are accepted.

### Kotlin Usage

```kotlin
import com.taqlyn.sdk.Client
import com.taqlyn.sdk.CreateShortLinkRequest

// Zero-config: baseUrl is optional, defaults to TAQLYN_BASE_URL env var or "https://api.taqlyn.com"
val client = Client(
    clientId = System.getenv("TAQLYN_CLIENT_ID"),
    privateKeyPem = System.getenv("TAQLYN_PRIVATE_KEY")
)

val link = client.createShortLink(
    CreateShortLinkRequest(
        destinationWeb = "https://example.com/offer",
        destinationPath = "/offer",
        params = mapOf("orderId" to "order_123", "channel" to "email"),
        mode = "deferred_app"
    )
)

println("Short URL: ${link.shortUrl}")
```

### Java Usage (Full Interoperability)

The SDK is designed with full Java interop annotations (`@JvmOverloads`, `@JvmStatic`, record-style accessors, and JavaBean getters):

```java
import com.taqlyn.sdk.Client;

// Calling Kotlin from Java:
Client client = new Client(
    System.getenv("TAQLYN_CLIENT_ID"),
    System.getenv("TAQLYN_PRIVATE_KEY")
);

Client.ShortLink link = client.createShortLink(
    new Client.CreateShortLinkRequest(
        "https://example.com/offer",
        "web_only"
    )
);

// Both record-style accessors and JavaBean getters are supported:
System.out.println(link.shortUrl());    // record-style
System.out.println(link.getShortUrl()); // JavaBean getter
```

## Request Signing

The SDK attaches `X-Taqlyn-Client-Id`, `X-Taqlyn-Timestamp`, and `X-Taqlyn-Signature` headers automatically. The standard-base64 Ed25519 signature covers this canonical newline-separated message:

```text
taqlyn-v1
{METHOD}
{PATH}
{unixTimestamp}
{clientId}
{hex(sha256(body))}
```

## Testing

```bash
mvn test
```

## License

MIT — see [LICENSE](./LICENSE).
