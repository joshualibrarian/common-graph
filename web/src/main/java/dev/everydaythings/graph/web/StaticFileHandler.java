package dev.everydaythings.graph.web;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.*;
import lombok.extern.log4j.Log4j2;

import java.io.InputStream;
import java.util.Map;

/**
 * Serves static files from classpath resources for the web client.
 *
 * <p>Handles HTTP GET requests by looking up {@code /web/<path>} on the classpath.
 * Passes through non-GET requests and WebSocket upgrade requests to the next handler.
 *
 * <p>Falls back to {@code index.html} for unmatched paths (SPA routing).
 */
@Log4j2
public class StaticFileHandler extends ChannelInboundHandlerAdapter {

    private static final String RESOURCE_PREFIX = "/web/";

    private static final Map<String, String> CONTENT_TYPES = Map.ofEntries(
            Map.entry("html", "text/html; charset=utf-8"),
            Map.entry("css", "text/css; charset=utf-8"),
            Map.entry("js", "application/javascript; charset=utf-8"),
            Map.entry("json", "application/json; charset=utf-8"),
            Map.entry("png", "image/png"),
            Map.entry("svg", "image/svg+xml"),
            Map.entry("ico", "image/x-icon"),
            Map.entry("woff2", "font/woff2"),
            Map.entry("woff", "font/woff")
    );

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (!(msg instanceof FullHttpRequest request)) {
            ctx.fireChannelRead(msg);
            return;
        }

        // Let WebSocket upgrades pass through
        if (isWebSocketUpgrade(request)) {
            ctx.fireChannelRead(msg);
            return;
        }

        // Only serve GET requests
        if (request.method() != HttpMethod.GET) {
            sendError(ctx, request, HttpResponseStatus.METHOD_NOT_ALLOWED);
            return;
        }

        String path = sanitizePath(request.uri());
        byte[] content = loadResource(path);

        if (content == null) {
            // SPA fallback: serve index.html for unmatched paths
            content = loadResource("index.html");
            path = "index.html";
        }

        if (content == null) {
            sendError(ctx, request, HttpResponseStatus.NOT_FOUND);
            return;
        }

        ByteBuf buf = Unpooled.wrappedBuffer(content);
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.OK, buf);

        response.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType(path));
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, content.length);
        response.headers().set(HttpHeaderNames.CACHE_CONTROL, "no-cache");

        request.release();
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    private boolean isWebSocketUpgrade(FullHttpRequest request) {
        return request.uri().startsWith("/ws") &&
                request.headers().contains(HttpHeaderNames.UPGRADE, "websocket", true);
    }

    private String sanitizePath(String uri) {
        // Strip query string
        int queryIdx = uri.indexOf('?');
        if (queryIdx >= 0) uri = uri.substring(0, queryIdx);

        // Strip leading slash
        if (uri.startsWith("/")) uri = uri.substring(1);

        // Default to index.html
        if (uri.isEmpty()) uri = "index.html";

        // Prevent path traversal
        if (uri.contains("..")) return "index.html";

        return uri;
    }

    private byte[] loadResource(String path) {
        try (InputStream is = getClass().getResourceAsStream(RESOURCE_PREFIX + path)) {
            return is != null ? is.readAllBytes() : null;
        } catch (Exception e) {
            logger.debug("Failed to load resource: {}", path);
            return null;
        }
    }

    private String contentType(String path) {
        int dot = path.lastIndexOf('.');
        if (dot >= 0) {
            String ext = path.substring(dot + 1).toLowerCase();
            return CONTENT_TYPES.getOrDefault(ext, "application/octet-stream");
        }
        return "application/octet-stream";
    }

    private void sendError(ChannelHandlerContext ctx, FullHttpRequest request, HttpResponseStatus status) {
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, status,
                Unpooled.wrappedBuffer(status.toString().getBytes()));
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=utf-8");
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        request.release();
    }
}
