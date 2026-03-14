package dev.everydaythings.graph.item.user;

import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.dispatch.ActionContext;
import dev.everydaythings.graph.item.Param;
import dev.everydaythings.graph.item.Type;
import dev.everydaythings.graph.item.Verb;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.CoreVocabulary;
import dev.everydaythings.graph.runtime.Librarian;
import dev.everydaythings.graph.crypt.InMemoryVault;

import java.nio.file.Path;

/**
 * A User is a Signer that represents a human identity.
 *
 * <p>Users are full Signers with their own vault, keypair, and KeyLog.
 * They are created via {@code create user alice} and can self-sign after creation.
 *
 * <p>When created with a file-based Librarian, users get a home directory at
 * {@code <rootPath>/users/<name>/} with a real SoftwareVault on disk.
 * For in-memory Librarians (testing), users use an InMemoryVault.
 *
 * <p>User creation and principal assignment are separate operations:
 * <ul>
 *   <li>{@code create user alice} — creates the User item</li>
 *   <li>{@code serve alice} — tells the Librarian to serve this user as principal</li>
 * </ul>
 */
@Type(value = User.KEY, glyph = "👤")
public class User extends Signer {

    public static final String KEY = "cg:type/user";

    // ==================================================================================
    // Constructors
    // ==================================================================================

    /**
     * Type seed constructor for SeedVocabulary bootstrap.
     *
     * <p>Creates a deterministic seed item for the User type.
     * Used only during vocabulary bootstrap — not for creating actual users.
     *
     * @param iid The deterministic type IID (from "cg:type/user")
     */
    protected User(ItemID iid) {
        super(iid);
    }

    /**
     * Hydration constructor for loading an existing User from store.
     *
     * @param librarian The librarian (provides store access)
     * @param manifest  The manifest describing this user's state
     */
    protected User(Librarian librarian, Manifest manifest) {
        super(librarian, manifest);
    }

    /**
     * Path-based constructor for creating/loading a User at a home directory.
     *
     * <p>If the path exists, loads the existing user. If not, creates a new user
     * with a real SoftwareVault on disk.
     *
     * @param librarian The librarian (provides store access and library)
     * @param homePath  The filesystem path for this user's home directory
     */
    protected User(Librarian librarian, Path homePath) {
        super(librarian, homePath);
    }

    /**
     * In-memory constructor for testing.
     *
     * <p>Creates an ephemeral user with an InMemoryVault. Used when the
     * Librarian has no rootPath (in-memory mode).
     *
     * @param librarian The librarian (provides store access)
     * @param marker    Marker to distinguish from other constructors
     */
    protected User(Librarian librarian, InMemoryMarker marker) {
        super(librarian, marker);
    }

    // ==================================================================================
    // Factory
    // ==================================================================================

    /**
     * Create a new User with the given name.
     *
     * <p>When the Librarian has a rootPath, creates the user at
     * {@code <rootPath>/users/<name>/} with a real SoftwareVault on disk.
     * Otherwise creates an in-memory user with an InMemoryVault.
     *
     * <p>The Librarian signs the initial creation (device certifies "this user
     * was created here"). The user can self-sign after that.
     *
     * @param lib  The librarian (provides store access and library)
     * @param name The user's name
     * @return The newly created User
     */
    public static User create(Librarian lib, String name) {
        User user;
        if (lib.rootPath() != null) {
            Path homePath = lib.rootPath().resolve("users").resolve(name);
            user = new User(lib, homePath);
        } else {
            user = new User(lib, InMemoryMarker.INSTANCE);
        }
        user.setName(name);
        user.commit(lib);
        lib.library().cache(user);
        return user;
    }

    // ==================================================================================
    // Verbs
    // ==================================================================================

    /**
     * Create a new user.
     *
     * <p>Delegates to {@link #create(Librarian, String)}.
     *
     * <p>Does NOT set the principal — use {@code serve <name>} for that.
     *
     * @param ctx  The action context
     * @param name The user's name
     * @return The newly created User
     */
    @Verb(value = CoreVocabulary.Create.KEY, doc = "Create a new user")
    public User actionNew(ActionContext ctx, @Param(value = "name", role = "NAME") String name) {
        Librarian lib = ctx.librarian();
        if (lib == null) {
            throw new IllegalStateException("Cannot create user without librarian");
        }
        return create(lib, name);
    }

    // ==================================================================================
    // Display
    // ==================================================================================

    @Override
    public String displayToken() {
        return name() != null ? name() : super.displayToken();
    }
}
