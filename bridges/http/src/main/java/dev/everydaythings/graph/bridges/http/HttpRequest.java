package dev.everydaythings.graph.bridges.http;

import java.net.URI;
import java.util.Objects;

/**
 * An HTTP request — method, target URI, headers, body bytes.
 *
 * <p>For client use, the URI is the full target {@code https://host:port/path}.
 * For server-received requests, the URI carries the request-target as
 * received (typically path + query); the absolute form is filled in by the
 * server using its bound endpoint.
 *
 * <p>Body bytes are owned by the request; pass an empty array for
 * GET/DELETE.  Larger bodies (multipart, chunked) are deferred until a
 * bridge needs them.
 */
public final class HttpRequest {

    private final HttpMethod method;
    private final URI uri;
    private final HttpHeaders headers;
    private final byte[] body;

    public HttpRequest(HttpMethod method, URI uri, HttpHeaders headers, byte[] body) {
        this.method = Objects.requireNonNull(method, "method");
        this.uri = Objects.requireNonNull(uri, "uri");
        this.headers = Objects.requireNonNull(headers, "headers");
        this.body = Objects.requireNonNull(body, "body").clone();
    }

    public static HttpRequest get(URI uri) {
        return new HttpRequest(HttpMethod.GET, uri, new HttpHeaders(), new byte[0]);
    }

    public static HttpRequest post(URI uri, byte[] body, String contentType) {
        HttpHeaders h = new HttpHeaders().set("Content-Type", contentType);
        return new HttpRequest(HttpMethod.POST, uri, h, body);
    }

    public HttpMethod method() { return method; }
    public URI uri() { return uri; }
    public HttpHeaders headers() { return headers; }
    public byte[] body() { return body.clone(); }
    public int bodyLength() { return body.length; }

    /** Direct view of the body bytes, no copy.  Treat as read-only. */
    byte[] bodyRaw() { return body; }

    @Override
    public String toString() {
        return method + " " + uri + " (" + body.length + " bytes)";
    }
}
