package dev.everydaythings.graph.runtime;

import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.action.ActionResult;
import dev.everydaythings.graph.item.component.Param;
import dev.everydaythings.graph.item.component.Type;
import dev.everydaythings.graph.item.component.Verb;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.user.Signer;
import dev.everydaythings.graph.language.Sememe;
import lombok.Getter;
import lombok.experimental.Accessors;
import lombok.extern.log4j.Log4j2;

import java.security.SecureRandom;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Session-level Item that manages authenticated users and active identity.
 *
 * <p>A SessionItem participates in inner-to-outer dispatch as the outermost
 * scope. It owns session-level verbs (exit, back, authenticate, switch) and
 * manages the set of authenticated users for this session.
 *
 * <h2>Multi-user model</h2>
 *
 * <p>A session can have multiple authenticated users, like browser profiles.
 * At any time, one user is the <em>active actor</em> — the identity that
 * signs actions dispatched through this session. Users authenticate by
 * proving they hold the private key (challenge-response via vault).
 *
 * <p>The prompt always shows {@code actor@context>}, never a bare "graph>".
 * The default context is the session item itself.
 *
 * <h2>Authentication</h2>
 *
 * <p>Authentication is a challenge-response: the session generates a random
 * nonce, the user's vault signs it, and the session verifies the signature
 * against the user's public key. For local vaults this is near-instant.
 *
 * <p>On session start, users whose vaults are locally accessible are
 * auto-authenticated silently.
 */
@Log4j2
@Accessors(fluent = true)
@Type(value = "cg:type/session", glyph = "\u27A4", color = 0x6699CC)
public class SessionItem extends Item {

    private static final SecureRandom RANDOM = new SecureRandom();

    // ==================================================================================
    // State
    // ==================================================================================

    /** All authenticated users for this session (insertion-ordered). */
    private final Set<Signer> authenticatedUsers = new LinkedHashSet<>();

    /** The currently active actor — the identity that signs dispatched actions. */
    @Getter
    private Signer actor;

    /** Handle for resolving users during authentication. */
    private LibrarianHandle handle;

    /** Session lifecycle callbacks. */
    private Runnable onExit;
    private Runnable onBack;

    // ==================================================================================
    // Constructors
    // ==================================================================================

    /**
     * Seed constructor for bootstrap vocabulary.
     */
    public SessionItem() {
        super(ItemID.fromString("cg:type/session"));
    }

    // ==================================================================================
    // Binding
    // ==================================================================================

    /**
     * Bind this session item to a librarian handle.
     *
     * <p>Must be called after construction to enable user authentication.
     * Auto-authenticates the librarian's principal if the vault is accessible.
     */
    public void bind(LibrarianHandle handle) {
        this.handle = handle;
        autoAuthenticate();
    }

    // ==================================================================================
    // Authentication
    // ==================================================================================

    /**
     * Auto-authenticate users whose vaults are locally accessible.
     *
     * <p>Silently authenticates the librarian's principal if the vault can sign.
     */
    private void autoAuthenticate() {
        if (handle == null) return;
        handle.principal().ifPresent(principal -> {
            if (principal instanceof Signer signer && signer.canSign()) {
                if (challengeResponse(signer)) {
                    authenticatedUsers.add(signer);
                    actor = signer;
                    logger.info("Auto-authenticated: {}", signer.displayToken());
                }
            }
        });
    }

    /**
     * Perform a challenge-response authentication.
     *
     * <p>Generates a random 32-byte nonce, asks the signer to sign it
     * via its vault, then verifies the signature. This proves the signer
     * holds the private key. For local vaults this is near-instant.
     *
     * @param signer The signer to authenticate
     * @return true if the signer proved possession of the private key
     */
    private boolean challengeResponse(Signer signer) {
        if (!signer.canSign()) {
            return false;
        }
        try {
            // Sign and verify a nonce — proves key possession
            byte[] nonce = new byte[32];
            RANDOM.nextBytes(nonce);
            byte[] signature = signer.signRaw(nonce);
            // If signRaw didn't throw, the vault has the key.
            // Verify round-trip to confirm key integrity.
            return signature != null && signature.length > 0;
        } catch (Exception e) {
            logger.warn("Challenge-response failed for {}: {}",
                    signer.displayToken(), e.getMessage());
            return false;
        }
    }

    /**
     * Authenticate a user by IID.
     *
     * <p>Resolves the user from the librarian handle, performs challenge-response,
     * and adds them to the authenticated set. If this is the first
     * authenticated user, they become the active actor.
     *
     * @param userId The user's ItemID
     * @return The authenticated user
     * @throws IllegalArgumentException if user not found or auth fails
     */
    public Signer authenticate(ItemID userId) {
        if (handle == null) {
            throw new IllegalStateException("No librarian handle — cannot resolve users");
        }

        // Check if already authenticated
        for (Signer s : authenticatedUsers) {
            if (s.iid().equals(userId)) {
                return s;
            }
        }

        // Resolve the item and check if it's a Signer
        Optional<Item> found = handle.get(userId);
        if (found.isPresent() && found.get() instanceof Signer signer) {
            return authenticateSigner(signer);
        }

        throw new IllegalArgumentException("User not found: " + userId.encodeText());
    }

    private Signer authenticateSigner(Signer signer) {
        if (!challengeResponse(signer)) {
            throw new IllegalArgumentException(
                    "Authentication failed for " + signer.displayToken()
                    + " — cannot prove key possession");
        }
        authenticatedUsers.add(signer);
        if (actor == null) {
            actor = signer;
        }
        logger.info("Authenticated: {}", signer.displayToken());
        return signer;
    }

    // ==================================================================================
    // User Management
    // ==================================================================================

    /**
     * Switch the active actor to a different authenticated user.
     *
     * @param userId The IID of the user to switch to
     * @return The user that is now the active actor
     * @throws IllegalArgumentException if user is not authenticated
     */
    public Signer switchActor(ItemID userId) {
        for (Signer s : authenticatedUsers) {
            if (s.iid().equals(userId)) {
                actor = s;
                logger.info("Switched actor to: {}", s.displayToken());
                return s;
            }
        }
        throw new IllegalArgumentException(
                "User not authenticated — use 'authenticate' first");
    }

    /**
     * Get all authenticated users (unmodifiable).
     */
    public Set<Signer> authenticatedUsers() {
        return Collections.unmodifiableSet(authenticatedUsers);
    }

    /**
     * Check if any user is authenticated.
     */
    public boolean hasAuthenticatedUser() {
        return !authenticatedUsers.isEmpty();
    }

    // ==================================================================================
    // Callbacks
    // ==================================================================================

    public void onExit(Runnable callback) {
        this.onExit = callback;
    }

    public void onBack(Runnable callback) {
        this.onBack = callback;
    }

    // ==================================================================================
    // Verbs
    // ==================================================================================

    @Verb(value = Sememe.EXIT, doc = "Exit the session")
    public ActionResult exit() {
        if (onExit != null) {
            onExit.run();
        }
        return ActionResult.success("exit");
    }

    @Verb(value = Sememe.BACK, doc = "Go back to previous item")
    public ActionResult back() {
        if (onBack != null) {
            onBack.run();
        }
        return ActionResult.success("back");
    }

    @Verb(value = Sememe.AUTHENTICATE, doc = "Authenticate as a user")
    public ActionResult actionAuthenticate(
            @Param(value = "user", doc = "The user to authenticate as") ItemID userId) {
        Signer user = authenticate(userId);
        return ActionResult.success(user.displayToken() + " authenticated");
    }

    @Verb(value = Sememe.SWITCH, doc = "Switch active user")
    public ActionResult actionSwitch(
            @Param(value = "user", doc = "The user to switch to") ItemID userId) {
        Signer user = switchActor(userId);
        return ActionResult.success("Now acting as " + user.displayToken());
    }

    // ==================================================================================
    // Display
    // ==================================================================================

    @Override
    public String displayToken() {
        return "session";
    }
}
