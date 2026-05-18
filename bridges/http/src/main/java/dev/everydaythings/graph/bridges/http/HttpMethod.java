package dev.everydaythings.graph.bridges.http;

/**
 * HTTP request methods, scoped to what the bridge actually uses today.
 *
 * <p>HEAD, PATCH, OPTIONS land when a bridge needs them.  Keeping the enum
 * tight makes the dispatch code on top easier to reason about.
 */
public enum HttpMethod {
    GET,
    POST,
    PUT,
    DELETE
}
