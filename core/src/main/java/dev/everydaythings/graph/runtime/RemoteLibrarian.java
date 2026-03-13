package dev.everydaythings.graph.runtime;

import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.id.Ref;
import dev.everydaythings.graph.dispatch.Vocabulary;
import dev.everydaythings.graph.dispatch.ActionResult;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.Posting;
import dev.everydaythings.graph.network.session.SessionClient;
import dev.everydaythings.graph.network.session.SessionMessage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Remote (network) reference to a Librarian.
 *
 * <p>RemoteLibrarian extends {@link SessionClient} to provide library-specific
 * operations over the Session Protocol. It implements {@link LibrarianHandle}
 * to allow transparent use of remote libraries.
 *
 * <p>Use this class when you need to interact with a Librarian running in
 * another process or on another machine.
 *
 * <p>Example usage:
 * <pre>{@code
 * // Connect to local librarian via Unix socket
 * RemoteLibrarian lib = new RemoteLibrarian("~/.librarian/session.sock");
 * lib.connectAndAuthenticate(null);  // Auto-detect token
 *
 * // Or connect to remote librarian via TCP
 * RemoteLibrarian lib = new RemoteLibrarian("localhost", 7474);
 * lib.connectAndAuthenticate("my-auth-token");
 *
 * // Use the library
 * lib.lookup("chess").forEach(p -> System.out.println(p.token()));
 * }</pre>
 */
public final class RemoteLibrarian extends SessionClient implements LibrarianHandle {

    private static final Logger log = LogManager.getLogger(RemoteLibrarian.class);

    // Library-specific state
    private ItemID librarianIid;

    // ==================================================================================
    // Constructors
    // ==================================================================================

    /**
     * Create a RemoteLibrarian for TCP connection.
     */
    public RemoteLibrarian(String host, int port) {
        super(host, port);
    }

    /**
     * Create a RemoteLibrarian for Unix socket connection.
     */
    public RemoteLibrarian(Path socketPath) {
        super(socketPath);
    }

    /**
     * Create a RemoteLibrarian with explicit connection target.
     *
     * @param target Either "host:port" for TCP or a path for Unix socket
     */
    public RemoteLibrarian(String target) {
        super(target);
    }

    // ==================================================================================
    // LibrarianHandle Interface
    // ==================================================================================

    @Override
    public ItemID iid() {
        checkOpen();
        if (librarianIid == null) {
            // Request the librarian's IID via context
            try {
                SessionMessage.ContextResponse ctx = getContext();
                if (ctx.itemId() != null) {
                    librarianIid = ctx.itemId();
                }
            } catch (IOException e) {
                log.warn("Failed to get librarian IID: {}", e.getMessage());
            }
        }
        if (librarianIid == null) {
            throw new IllegalStateException("Librarian IID not yet known (connect first)");
        }
        return librarianIid;
    }

    @Override
    public ItemID principalId() {
        return principalId;
    }

    /**
     * Set the principal identity for this session.
     *
     * <p>Called after resolving the {@code --as} flag.
     * This also sets the principalId on the underlying SessionClient
     * so DISPATCH messages include the caller identity.
     */
    public void setPrincipalId(ItemID id) {
        this.principalId = id;
    }

    @Override
    public boolean isLocal() {
        return false;
    }

    @Override
    public String connectionString() {
        return connectionTarget();
    }

    @Override
    public ActionResult dispatch(String command, List<String> args) {
        checkOpen();
        try {
            SessionMessage.DispatchResponse response = sendDispatch(command, args);
            if (response.success()) {
                return ActionResult.success(response.view());
            } else {
                return ActionResult.failure(new RuntimeException(response.error()));
            }
        } catch (IOException e) {
            return ActionResult.failure(e);
        }
    }

    @Override
    public ActionResult dispatch(ItemID target, String command, List<String> args) {
        checkOpen();
        try {
            // First set context to the target item
            SessionMessage.ContextResponse ctxResponse = setContext(target);
            if (ctxResponse.error() != null) {
                return ActionResult.failure(new RuntimeException(ctxResponse.error()));
            }

            // Then dispatch the command
            return dispatch(command, args);
        } catch (IOException e) {
            return ActionResult.failure(e);
        }
    }

    @Override
    public <T extends Item> Optional<T> get(ItemID iid, Class<T> type) {
        checkOpen();
        // Items can't be directly transferred over the protocol - they're local objects.
        // The protocol works with Views and dispatch results.
        // For now, return empty. A proper implementation would need to serialize/deserialize Items.
        log.warn("get() called on RemoteLibrarian - Items cannot be transferred over protocol");
        return Optional.empty();
    }

    @Override
    public Stream<Posting> lookup(String query) {
        checkOpen();
        try {
            List<Posting> postings = super.lookup(query, 100);
            return postings.stream();
        } catch (IOException e) {
            log.error("Lookup failed", e);
            return Stream.empty();
        }
    }

    @Override
    public Stream<Posting> lookup(String query, ItemID... scopes) {
        // TODO: Pass scopes through session protocol
        return lookup(query);
    }

    @Override
    public Stream<Posting> prefix(String text, int limit) {
        // Prefix search reuses lookup with a limit
        return lookup(text);
    }

    @Override
    public Vocabulary vocabulary() {
        checkOpen();
        // Vocabulary is a local object; remote access would need a different approach
        log.warn("vocabulary() called on RemoteLibrarian - not available for remote connections");
        return null;
    }

    @Override
    public Optional<Item> principal() {
        checkOpen();
        if (principalId != null) {
            return get(principalId);
        }
        return Optional.empty();
    }

    // ==================================================================================
    // Event Subscriptions
    // ==================================================================================

    /**
     * Start listening for async events from the server.
     *
     * <p>Must be called after {@link #connectAndAuthenticate(String)}.
     * EVENT messages from subscribed items will be delivered to the
     * listener set via {@link #onEvent(java.util.function.Consumer)}.
     */
    public void startEventListener() {
        startAsyncReader();
    }

    /**
     * Subscribe to updates on an item.
     */
    public void subscribe(ItemID itemId) {
        try {
            sendSubscribe(itemId);
        } catch (IOException e) {
            log.error("Subscribe failed for {}", itemId.encodeText(), e);
        }
    }

    // ==================================================================================
    // Overrides
    // ==================================================================================

    @Override
    public void close() {
        super.close();
        log.debug("RemoteLibrarian closed");
    }

    @Override
    public String toString() {
        return "RemoteLibrarian[" + connectionTarget() +
               (sessionId() != null ? ", session=" + sessionId() : "") + "]";
    }
}
