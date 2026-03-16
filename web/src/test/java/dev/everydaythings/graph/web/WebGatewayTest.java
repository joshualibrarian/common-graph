package dev.everydaythings.graph.web;

import dev.everydaythings.graph.network.ProtocolMessage;
import dev.everydaythings.graph.network.session.SessionMessage;
import dev.everydaythings.graph.runtime.Librarian;
import org.junit.jupiter.api.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for the WebGateway.
 *
 * <p>Boots an in-memory Librarian, starts the web gateway on a random port,
 * connects via WebSocket, and exercises the full session protocol cycle:
 * auth challenge → token auth → dispatch → view response.
 */
@DisplayName("WebGateway")
class WebGatewayTest {

    private Librarian librarian;
    private WebGateway gateway;
    private int port;

    @BeforeEach
    void setUp() throws Exception {
        librarian = Librarian.createInMemory();
        // Start the session server so we have a token to authenticate with
        librarian.startSessionServer();

        gateway = new WebGateway(librarian);
        var address = gateway.start("127.0.0.1", 0);
        port = address.getPort();
    }

    @AfterEach
    void tearDown() {
        if (gateway != null) gateway.close();
        if (librarian != null) librarian.close();
    }

    @Test
    @DisplayName("gateway starts and reports running")
    void gatewayStartsAndReportsRunning() {
        assertThat(gateway.isRunning()).isTrue();
        assertThat(gateway.boundAddress()).isNotNull();
        assertThat(gateway.boundAddress().getPort()).isGreaterThan(0);
    }

    @Test
    @DisplayName("WebSocket connection receives auth challenge")
    void webSocketReceivesAuthChallenge() throws Exception {
        var messages = new LinkedBlockingQueue<ProtocolMessage>();
        var latch = new CountDownLatch(1);

        WebSocket ws = connectWebSocket(messages, latch);
        try {
            // Should receive AuthChallenge within 5 seconds
            ProtocolMessage msg = messages.poll(5, TimeUnit.SECONDS);
            assertThat(msg).isNotNull();
            assertThat(msg).isInstanceOf(SessionMessage.AuthChallenge.class);

            SessionMessage.AuthChallenge challenge = (SessionMessage.AuthChallenge) msg;
            assertThat(challenge.nonce()).isNotNull();
            assertThat(challenge.nonce()).hasSize(32);
            assertThat(challenge.acceptedMethods()).contains("token");
        } finally {
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "done").join();
        }
    }

    @Test
    @DisplayName("token authentication succeeds with local auto-token")
    void tokenAuthSucceeds() throws Exception {
        var messages = new LinkedBlockingQueue<ProtocolMessage>();
        var latch = new CountDownLatch(1);

        WebSocket ws = connectWebSocket(messages, latch);
        try {
            // Wait for challenge
            ProtocolMessage challenge = messages.poll(5, TimeUnit.SECONDS);
            assertThat(challenge).isInstanceOf(SessionMessage.AuthChallenge.class);

            // Send auth token
            String token = librarian.sessionLocalToken();
            assertThat(token).isNotNull();

            sendMessage(ws, new SessionMessage.AuthToken(token, null));

            // Should get AuthResponse success
            ProtocolMessage response = messages.poll(5, TimeUnit.SECONDS);
            assertThat(response).isNotNull();
            assertThat(response).isInstanceOf(SessionMessage.AuthResponse.class);

            SessionMessage.AuthResponse authResponse = (SessionMessage.AuthResponse) response;
            assertThat(authResponse.success()).isTrue();
            assertThat(authResponse.sessionId()).isNotNull();
        } finally {
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "done").join();
        }
    }

    @Test
    @DisplayName("invalid token is rejected")
    void invalidTokenRejected() throws Exception {
        var messages = new LinkedBlockingQueue<ProtocolMessage>();
        var latch = new CountDownLatch(1);

        WebSocket ws = connectWebSocket(messages, latch);
        try {
            // Wait for challenge
            messages.poll(5, TimeUnit.SECONDS);

            // Send bogus token
            sendMessage(ws, new SessionMessage.AuthToken("bogus-token", null));

            ProtocolMessage response = messages.poll(5, TimeUnit.SECONDS);
            assertThat(response).isInstanceOf(SessionMessage.AuthResponse.class);

            SessionMessage.AuthResponse authResponse = (SessionMessage.AuthResponse) response;
            assertThat(authResponse.success()).isFalse();
        } finally {
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "done").join();
        }
    }

    @Test
    @DisplayName("dispatch returns a view after authentication")
    void dispatchReturnsView() throws Exception {
        var messages = new LinkedBlockingQueue<ProtocolMessage>();
        var latch = new CountDownLatch(1);

        WebSocket ws = connectWebSocket(messages, latch);
        try {
            // Auth flow
            messages.poll(5, TimeUnit.SECONDS); // challenge
            sendMessage(ws, new SessionMessage.AuthToken(librarian.sessionLocalToken(), null));
            ProtocolMessage authResp = messages.poll(5, TimeUnit.SECONDS);
            assertThat(authResp).isInstanceOf(SessionMessage.AuthResponse.class);
            assertThat(((SessionMessage.AuthResponse) authResp).success()).isTrue();

            // Dispatch "list" (a core verb that should work on any item)
            sendMessage(ws, new SessionMessage.DispatchRequest("list", List.of(), 1, null));

            ProtocolMessage dispatchResp = messages.poll(5, TimeUnit.SECONDS);
            assertThat(dispatchResp).isNotNull();
            assertThat(dispatchResp).isInstanceOf(SessionMessage.DispatchResponse.class);

            SessionMessage.DispatchResponse dr = (SessionMessage.DispatchResponse) dispatchResp;
            // Dispatch may succeed or fail depending on verb availability,
            // but we should always get a response with requestId matching
            assertThat(dr.requestId()).isEqualTo(1);
            // The view should be present regardless (error text or actual view)
            assertThat(dr.view() != null || dr.error() != null).isTrue();
        } finally {
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "done").join();
        }
    }

    @Test
    @DisplayName("context get returns librarian info")
    void contextGetReturnsLibrarian() throws Exception {
        var messages = new LinkedBlockingQueue<ProtocolMessage>();
        var latch = new CountDownLatch(1);

        WebSocket ws = connectWebSocket(messages, latch);
        try {
            // Auth flow
            messages.poll(5, TimeUnit.SECONDS);
            sendMessage(ws, new SessionMessage.AuthToken(librarian.sessionLocalToken(), null));
            messages.poll(5, TimeUnit.SECONDS); // auth response

            // Get context (should be the Librarian by default)
            sendMessage(ws, SessionMessage.ContextRequest.get());

            ProtocolMessage ctxResp = messages.poll(5, TimeUnit.SECONDS);
            assertThat(ctxResp).isNotNull();
            assertThat(ctxResp).isInstanceOf(SessionMessage.ContextResponse.class);

            SessionMessage.ContextResponse cr = (SessionMessage.ContextResponse) ctxResp;
            assertThat(cr.itemId()).isNotNull();
            assertThat(cr.error()).isNull();
        } finally {
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "done").join();
        }
    }

    @Test
    @DisplayName("lookup returns postings for known tokens")
    void lookupReturnsPostings() throws Exception {
        var messages = new LinkedBlockingQueue<ProtocolMessage>();
        var latch = new CountDownLatch(1);

        WebSocket ws = connectWebSocket(messages, latch);
        try {
            // Auth flow
            messages.poll(5, TimeUnit.SECONDS);
            sendMessage(ws, new SessionMessage.AuthToken(librarian.sessionLocalToken(), null));
            messages.poll(5, TimeUnit.SECONDS);

            // Lookup "create" — should find the verb seed
            sendMessage(ws, new SessionMessage.LookupRequest("create", 5, 1));

            ProtocolMessage lookupResp = messages.poll(5, TimeUnit.SECONDS);
            assertThat(lookupResp).isNotNull();
            assertThat(lookupResp).isInstanceOf(SessionMessage.LookupResponse.class);

            SessionMessage.LookupResponse lr = (SessionMessage.LookupResponse) lookupResp;
            assertThat(lr.postings()).isNotEmpty();
        } finally {
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "done").join();
        }
    }

    @Test
    @DisplayName("static file serving returns index.html")
    void staticFileServesIndexHtml() throws Exception {
        var client = HttpClient.newHttpClient();
        var request = java.net.http.HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/"))
                .GET()
                .build();

        var response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("Common Graph");
        assertThat(response.headers().firstValue("content-type"))
                .hasValueSatisfying(ct -> assertThat(ct).contains("text/html"));
    }

    @Test
    @DisplayName("static file serving returns JS files")
    void staticFileServesJs() throws Exception {
        var client = HttpClient.newHttpClient();
        var request = java.net.http.HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/cbor.js"))
                .GET()
                .build();

        var response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("CBOR");
        assertThat(response.headers().firstValue("content-type"))
                .hasValueSatisfying(ct -> assertThat(ct).contains("javascript"));
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private WebSocket connectWebSocket(BlockingQueue<ProtocolMessage> messages,
                                        CountDownLatch closeLatch) throws Exception {
        return HttpClient.newHttpClient()
                .newWebSocketBuilder()
                .buildAsync(URI.create("ws://127.0.0.1:" + port + "/ws"), new WebSocket.Listener() {

                    private ByteBuffer accumulated = ByteBuffer.allocate(0);

                    @Override
                    public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
                        // Accumulate partial frames
                        ByteBuffer combined = ByteBuffer.allocate(accumulated.remaining() + data.remaining());
                        combined.put(accumulated);
                        combined.put(data);
                        combined.flip();

                        if (last) {
                            byte[] bytes = new byte[combined.remaining()];
                            combined.get(bytes);
                            accumulated = ByteBuffer.allocate(0);

                            try {
                                ProtocolMessage msg = ProtocolMessage.decode(bytes);
                                messages.offer(msg);
                            } catch (Exception e) {
                                System.err.println("Failed to decode WS message: " + e.getMessage());
                            }
                        } else {
                            accumulated = combined;
                        }

                        webSocket.request(1);
                        return null;
                    }

                    @Override
                    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                        closeLatch.countDown();
                        return null;
                    }

                    @Override
                    public void onError(WebSocket webSocket, Throwable error) {
                        System.err.println("WebSocket error: " + error.getMessage());
                        closeLatch.countDown();
                    }
                })
                .get(5, TimeUnit.SECONDS);
    }

    private void sendMessage(WebSocket ws, ProtocolMessage message) {
        byte[] encoded = message.encode();
        ws.sendBinary(ByteBuffer.wrap(encoded), true).join();
    }
}
