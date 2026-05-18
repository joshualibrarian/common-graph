package dev.everydaythings.graph.bridges.http;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Simple method + exact-path router for {@link HttpServer}.
 *
 * <p>Routes are matched in registration order; first match wins.  Path
 * matching is exact (case-sensitive) — no wildcards or templates yet.
 * The KERI bridge will surface real cases (e.g., {@code /oobi/<aid>}) that
 * justify path parameters; until then, exact paths are enough.
 *
 * <p>Handlers run on the Netty worker thread that delivered the request;
 * a handler returning a non-already-completed {@link CompletableFuture}
 * is fine — the server waits for completion before writing the response.
 */
public final class HttpRouter {

    private final List<Route> routes = new ArrayList<>();
    private Function<HttpRequest, CompletableFuture<HttpResponse>> fallback =
            req -> CompletableFuture.completedFuture(HttpResponse.of(404));

    /** Register a handler for a method + exact path. */
    public HttpRouter on(HttpMethod method, String path,
                         Function<HttpRequest, CompletableFuture<HttpResponse>> handler) {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(handler, "handler");
        routes.add(new Route(method, path, handler));
        return this;
    }

    /** Convenience for synchronous handlers. */
    public HttpRouter onSync(HttpMethod method, String path,
                             Function<HttpRequest, HttpResponse> handler) {
        return on(method, path, req -> CompletableFuture.completedFuture(handler.apply(req)));
    }

    /** Replace the default 404 handler. */
    public HttpRouter fallback(Function<HttpRequest, CompletableFuture<HttpResponse>> handler) {
        this.fallback = Objects.requireNonNull(handler, "handler");
        return this;
    }

    /**
     * Resolve a request to a handler.  Returns the registered handler if
     * method+path match, else the fallback.
     */
    public CompletableFuture<HttpResponse> dispatch(HttpRequest request) {
        Optional<Route> match = match(request);
        Function<HttpRequest, CompletableFuture<HttpResponse>> handler =
                match.map(r -> r.handler).orElse(fallback);
        try {
            return handler.apply(request);
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private Optional<Route> match(HttpRequest request) {
        String path = request.uri().getRawPath();
        if (path == null) path = "";
        for (Route r : routes) {
            if (r.method == request.method() && r.path.equals(path)) return Optional.of(r);
        }
        return Optional.empty();
    }

    private static final class Route {
        final HttpMethod method;
        final String path;
        final Function<HttpRequest, CompletableFuture<HttpResponse>> handler;

        Route(HttpMethod method, String path,
              Function<HttpRequest, CompletableFuture<HttpResponse>> handler) {
            this.method = method;
            this.path = path;
            this.handler = handler;
        }
    }
}
