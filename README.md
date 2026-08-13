# Taqlyn JVM SDK

Minimal Java 17+ **server** SDK for creating Taqlyn short links with Ed25519
request signing. Maven coordinates: `com.taqlyn:sdk`.

This package is server-only. It does not include mobile Match, resolve, deferred
deep-link, or navigation APIs.

## Quickstart

Configure the API origin, credential client ID, and the PKCS#8 Ed25519 private
key returned when the credential was issued:

```bash
export TAQLYN_BASE_URL=https://api.rutvik.qzz.io
export TAQLYN_CLIENT_ID=app_test_abc
export TAQLYN_PRIVATE_KEY='-----BEGIN PRIVATE KEY-----
...
-----END PRIVATE KEY-----'
```

Do not use an `sk_test_*` or `sk_live_*` value as the private key. Those values
are credential handles and cannot sign requests. Literal `\n` sequences in the
PEM environment variable are accepted.

Public tunnel demo: [`examples/server/jvm`](../../examples/server/jvm),
[`docs/guides/public-demo.md`](../../docs/guides/public-demo.md).

```java
import com.taqlyn.sdk.Client;

Client client = new Client(
    System.getenv("TAQLYN_BASE_URL"),
    System.getenv("TAQLYN_CLIENT_ID"),
    System.getenv("TAQLYN_PRIVATE_KEY")
);

Client.ShortLink link = client.createShortLink(
    new Client.CreateShortLinkRequest(
        "https://example.com/offer",
        "web_only"
    )
);

System.out.println(link.shortUrl());
```

## Signing

The SDK sends `X-Taqlyn-Client-Id`, `X-Taqlyn-Timestamp`, and
`X-Taqlyn-Signature`. The standard-base64 Ed25519 signature covers this
newline-separated message, with no trailing newline:

```text
taqlyn-v1
{METHOD}
{PATH}
{unixTimestamp}
{clientId}
{hex(sha256(body))}
```

## Test

```bash
mvn test
```

## License

MIT — see [LICENSE](./LICENSE).
