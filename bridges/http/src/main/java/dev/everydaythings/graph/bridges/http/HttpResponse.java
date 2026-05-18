package dev.everydaythings.graph.bridges.http;

import java.util.Objects;

/**
 * An HTTP response — status code, headers, body bytes.
 *
 * <p>Carries the raw bytes; decoding into a typed value (CESR-encoded
 * KERI event, JSON, ...) is the caller's responsibility.
 */
public final class HttpResponse {

    private final int status;
    private final HttpHeaders headers;
    private final byte[] body;

    public HttpResponse(int status, HttpHeaders headers, byte[] body) {
        if (status < 100 || status > 599) {
            throw new IllegalArgumentException("HTTP status out of range: " + status);
        }
        this.status = status;
        this.headers = Objects.requireNonNull(headers, "headers");
        this.body = Objects.requireNonNull(body, "body").clone();
    }

    /** Body-less response (status + headers only). */
    public static HttpResponse of(int status) {
        return new HttpResponse(status, new HttpHeaders(), new byte[0]);
    }

    /** Status + body with a Content-Type header set. */
    public static HttpResponse of(int status, byte[] body, String contentType) {
        HttpHeaders h = new HttpHeaders().set("Content-Type", contentType);
        return new HttpResponse(status, h, body);
    }

    public int status() { return status; }
    public HttpHeaders headers() { return headers; }
    public byte[] body() { return body.clone(); }
    public int bodyLength() { return body.length; }
    public boolean isSuccess() { return status >= 200 && status < 300; }

    /** Direct view of the body bytes, no copy.  Treat as read-only. */
    byte[] bodyRaw() { return body; }

    @Override
    public String toString() {
        return "HttpResponse[" + status + ", " + body.length + " bytes]";
    }
}
