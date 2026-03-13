package dev.everydaythings.graph.runtime;

import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.id.Ref;
import dev.everydaythings.graph.dispatch.Vocabulary;
import dev.everydaythings.graph.dispatch.ActionResult;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.Posting;

import java.io.Closeable;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Abstract reference to a Librarian - either local or remote.
 *
 * <p>This is the "thin client" layer that Sessions use to interact with
 * a Librarian without knowing whether it's in the same JVM or across
 * the network.
 *
 * <p>Implementations:
 * <ul>
 *   <li>{@link LocalLibrarian} - wraps a local {@link Librarian} instance (same JVM)</li>
 *   <li>{@link RemoteLibrarian} - connects to a Librarian daemon via Session Protocol
 *       (Unix socket or TCP)</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>{@code
 * // Local
 * LibrarianRef ref = LibrarianRef.local(path);
 *
 * // Remote (future)
 * LibrarianRef ref = LibrarianRef.remote("localhost", 7474);
 *
 * // Use uniformly
 * var result = ref.dispatch("create", List.of("Note"));
 * }</pre>
 */
public sealed interface LibrarianHandle extends Closeable permits LocalLibrarian, RemoteLibrarian {

    // ==================================================================================
    // Factory Methods
    // ==================================================================================

    /**
     * Create a reference to a local Librarian at the given path.
     *
     * <p>Opens or creates the Librarian at the specified location.
     */
    static LibrarianHandle local(Path path) {
        return new LocalLibrarian(Librarian.open(path));
    }

    /**
     * Create a reference to an ephemeral in-memory Librarian.
     *
     * <p>No filesystem access - perfect for testing, demos, and quick sessions.
     * Data is lost when the session ends.
     */
    static LibrarianHandle inMemory() {
        return new LocalLibrarian(Librarian.createInMemory());
    }

    /**
     * Wrap an existing local Librarian.
     */
    static LibrarianHandle wrap(Librarian librarian) {
        return new LocalLibrarian(librarian);
    }

    /**
     * Create a reference to a remote Librarian over TCP.
     *
     * @param host The hostname or IP address
     * @param port The TCP port
     */
    static LibrarianHandle remote(String host, int port) {
        return new RemoteLibrarian(host, port);
    }

    /**
     * Create a reference to a remote Librarian using a connection target.
     *
     * <p>The target can be:
     * <ul>
     *   <li>"host:port" - TCP connection</li>
     *   <li>"/path/to/socket" or "path/to/socket" - Unix socket</li>
     * </ul>
     *
     * @param connectionTarget The connection target string
     */
    static LibrarianHandle remote(String connectionTarget) {
        return new RemoteLibrarian(connectionTarget);
    }

    /**
     * Create a reference to a remote Librarian over Unix socket.
     *
     * @param socketPath Path to the Unix domain socket
     */
    static LibrarianHandle remote(Path socketPath) {
        return new RemoteLibrarian(socketPath);
    }

    // ==================================================================================
    // Identity
    // ==================================================================================

    /**
     * Get the Librarian's ItemID.
     */
    ItemID iid();

    /**
     * Get the principal (user) identity for this session.
     *
     * <p>For local handles, returns the Librarian's principal IID.
     * For remote handles, returns the IID resolved from {@code --as}.
     *
     * @return The principal's ItemID, or null if no principal is configured
     */
    ItemID principalId();

    /**
     * Check if this is a local (same JVM) reference.
     */
    boolean isLocal();

    /**
     * Get the connection description (path for local, host:port for remote).
     */
    String connectionString();

    // ==================================================================================
    // Dispatch
    // ==================================================================================

    /**
     * Dispatch a command to the Librarian.
     *
     * @param command The command/verb to execute
     * @param args String arguments
     * @return The action result
     */
    ActionResult dispatch(String command, List<String> args);

    /**
     * Dispatch a command to a specific item.
     *
     * @param target The target item ID
     * @param command The command/verb to execute
     * @param args String arguments
     * @return The action result
     */
    ActionResult dispatch(ItemID target, String command, List<String> args);

    // ==================================================================================
    // Item Access
    // ==================================================================================

    /**
     * Get an item by ID.
     */
    <T extends Item> Optional<T> get(ItemID iid, Class<T> type);

    /**
     * Get an item by ID (as generic Item).
     */
    default Optional<Item> get(ItemID iid) {
        return get(iid, Item.class);
    }

    // ==================================================================================
    // Lookup
    // ==================================================================================

    /**
     * Look up tokens in the token dictionary.
     *
     * @param query The search query
     * @return Stream of matching postings
     */
    Stream<Posting> lookup(String query);

    /**
     * Look up tokens with explicit scope chain.
     *
     * <p>Scopes control which postings are visible. A typical scope chain:
     * <ol>
     *   <li>Focused item IID (for proper nouns/aliases)</li>
     *   <li>Language IID (for language words)</li>
     *   <li>{@code null} (for universal symbols)</li>
     * </ol>
     *
     * @param query  The search query
     * @param scopes Scope chain (order = priority)
     * @return Stream of matching postings
     */
    Stream<Posting> lookup(String query, ItemID... scopes);

    /**
     * Prefix search for autocomplete.
     *
     * @param text  The prefix text to search for
     * @param limit Maximum number of results
     * @return Stream of matching postings
     */
    Stream<Posting> prefix(String text, int limit);

    /**
     * Get the vocabulary for the Librarian.
     */
    Vocabulary vocabulary();

    /**
     * Get the principal (user) as an Item.
     *
     * <p>For local handles, returns the Librarian's principal Signer directly.
     * For remote handles, resolves via {@link #principalId()}.
     */
    Optional<Item> principal();

    /**
     * Get the default context for a new session.
     *
     * <p>Prefers the principal (user) as context; falls back to the librarian itself.
     */
    default Ref defaultContext() {
        return principal()
                .map(p -> Ref.of(p.iid()))
                .orElse(Ref.of(iid()));
    }

    // ==================================================================================
    // Lifecycle
    // ==================================================================================

    /**
     * Check if the connection is open/valid.
     */
    boolean isOpen();

    /**
     * Close the connection and release resources.
     */
    @Override
    void close();
}
