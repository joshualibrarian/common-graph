package dev.everydaythings.graph.runtime.user;

import dev.everydaythings.graph.CoreVocabulary;
import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.cryptography.Signer;
import dev.everydaythings.graph.cryptography.vault.Vault;
import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.language.GrammaticalFeature;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.LexicalVocabulary;
import dev.everydaythings.graph.language.PartOfSpeech;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.ref.TypeRef;
import dev.everydaythings.graph.cryptography.IdentityVocabulary;
import dev.everydaythings.graph.runtime.librarian.Librarian;
import dev.everydaythings.graph.value.identifier.Handle;
import dev.everydaythings.graph.value.identifier.Identifier;

import java.util.List;
import java.util.Objects;

/**
 * User — a human's cryptographic identity in the graph.  Subclass of
 * {@link Signer} distinguished from other Signer subclasses (Librarian,
 * Host, Service) by the shape humans bring:
 *
 * <ul>
 *   <li>Interactive bootstrap (prompt for name on first run, optional
 *       passphrase, etc.).</li>
 *   <li>{@code IDENTIFIED_BY} frames as the norm — Name at minimum,
 *       optionally Email, PhoneNumber, etc.  See
 *       {@code dev.everydaythings.graph.value.identifier} for the
 *       Identifier subtypes.</li>
 *   <li>Optionally {@code REPRESENTS} a {@link
 *       dev.everydaythings.graph.actor.Person Person} (real-world human
 *       bridge).  Optional in both directions: many Users have no Person
 *       link (no need); many Persons have no User (historical figures,
 *       people the user knows about but not on the system).</li>
 *   <li>Always the principal in a {@code SERVES} relationship with one or
 *       more Librarians.</li>
 *   <li>Always the delegator when authenticating to a {@code Session} —
 *       Users grant time-bounded delegated key-bundles that the Session's
 *       ephemeral keys carry while signing user-attributed frames.</li>
 * </ul>
 *
 * <h2>One Signer per cryptographic-isolation boundary</h2>
 *
 * <p>Most users have ONE User identity.  Role-context (work vs personal,
 * group memberships, etc.) is expressed via attestations issued TO that
 * one Signer and via per-frame audience / visibility rules — not by
 * separate Users.
 *
 * <p>The exception is truly-anonymous personas (pseudonyms, separate-
 * from-real-identity work).  These get a separate User instance with its
 * own independent vault and no on-disk link from the primary.  Same
 * archetype, different instance.
 *
 * <h2>Construction</h2>
 *
 * <p>Mirrors {@link Signer}'s constructor set; User is just a typed
 * specialization.  Most callers want one of the librarian-bound forms.
 * Bootstrap tooling (interactive create-and-materialize) lands as
 * separate factory methods when first-run UX is wired.
 *
 * <h2>Materialization</h2>
 *
 * <p>By convention the primary User materializes at {@code ~/.item/} on
 * the local machine.  Additional Users (anonymous personas, role-specific
 * Signers) live at their own root dirs.  See the
 * project_materialization_paths memory entry for the broader convention.
 */
@Seed.Item(key = User.KEY)
@Seed.Embodies(key = User.CODE_KEY, archetype = User.KEY)
public class User extends Signer {

    /** Canonical key for User-the-archetype. */
    public static final String KEY = "cg.archetype:user";

    /** The archetype IID for User instances. */
    public static final ItemRef ARCHETYPE = ItemRef.fromString(KEY);

    /**
     * Canonical key for the CodeItem representing this Java embodiment of
     * User.  Two-level {@link Seed.Embodies @Seed.Embodies}: the archetype
     * (User.KEY) is the data identity; the CodeItem (User.CODE_KEY)
     * declares "this Java class implements User."
     */
    public static final String CODE_KEY = "cg.code:user-java-default";

    /** IID of the CodeItem for this Java implementation of User. */
    public static final ItemRef CODE_IID = ItemRef.fromString(CODE_KEY);

    @Override
    public ItemRef archetype() {
        return ARCHETYPE;
    }

    @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
    static final String englishGloss =
            "a human's cryptographic identity — a Signer subclass distinguished by "
                    + "interactive bootstrap, IDENTIFIED_BY frames, optional REPRESENTS "
                    + "to a Person, and the role of principal in SERVES and DELEGATION";

    @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String[] englishNounLemmas = {"user", "account"};

    // ==================================================================================
    // Template bindings — what User instances are expected to carry.
    //
    // These !-role (SchemaRef) bindings on the User archetype manifest
    // declare the shape concrete User instances should populate.  Per the
    // schema model, instances may carry these as direct manifest bindings
    // (self-asserted by the User itself) OR as separate frames pointing at
    // the instance (third-party attribution).  The schema doesn't pin
    // which.
    //
    //   !Identifier → ?Handle
    // ==================================================================================

    /**
     * Users are expected to have a username.  The username is an Identifier
     * (the role) of subtype Handle (the value type).  Reads as: "User
     * instances have an Identifier-position binding (or frame) whose target
     * is a Handle."
     *
     * <p>The role and the value-type share semantic territory because of
     * morphological collapse — Identifier (noun) and identify-by (verb-
     * participle predicate) are the same underlying sememe in different
     * grammatical positions.  See {@link Identifier}'s class doc for the
     * dual-role pattern.
     */
    @Seed.Property(schemaRole = Identifier.KEY)
    static final TypeRef expectsUsername = TypeRef.iid(Handle.KEY);

    // ==================================================================================
    // Constructors — mirror Signer's set.
    // ==================================================================================

    /**
     * Identity-only User with no librarian, no vault, no signing capability.
     * Used for test fixtures and bare-identity objects.
     */
    public User(ItemRef iid) {
        super(iid);
    }

    /**
     * Identity-only User with a librarian binding.  No vault.  Used when
     * hydrating a User the local node has observed but doesn't hold private
     * keys for (a peer's User, etc.).
     */
    public User(ItemRef iid, Librarian librarian) {
        super(iid, librarian);
    }

    /**
     * Auto-generated vault — mints a fresh User signing identity using the
     * default algorithm, derives the IID from the vault, binds the
     * librarian, and auto-incepts.  The convenience form for "create a
     * brand-new User."
     */
    public User(Librarian librarian) {
        super(librarian);
    }

    /**
     * Vault-supplied constructor — full User with vault + librarian, IID
     * derived from the vault, signing chain auto-incepted.  Use when the
     * vault material was created or loaded externally (passphrase-unlocked
     * JWKS file, OS keychain, hardware token, etc.) and you want to bind
     * it as a User identity.
     */
    public User(Librarian librarian, Vault vault) {
        super(librarian, vault);
    }

    // ==================================================================================
    // Factory methods
    // ==================================================================================

    /**
     * Mint a fresh User against {@code librarian}.  Generates a vault,
     * derives the IID from it, auto-incepts on the signing track, and
     * publishes a {@code SERVES} frame asserting the librarian's service
     * relationship with the new User (Librarian → SERVES → User, signed
     * by the librarian).  The returned User has no Identifier frames yet;
     * attach them via {@link #identifyAs(Identifier)}.
     */
    public static User create(Librarian librarian) {
        User user = new User(librarian);
        publishServes(librarian, user);
        return user;
    }

    /**
     * Mint a fresh User against {@code librarian} with the given username
     * as its first Identifier.  Convenience for the common "create a user
     * with a name" flow.
     */
    public static User create(Librarian librarian, String username) {
        User user = create(librarian);
        user.identifyAs(Handle.of(username));
        return user;
    }

    // ==================================================================================
    // Identifier attachment
    // ==================================================================================

    /**
     * Publish an Identifier frame asserting this User is identified by
     * {@code identifier}.  The frame is signed by the User itself
     * (self-attestation): {@code Identifier {THEME → this, VALUE →
     * identifier}}, where the frame's head is the Identifier predicate
     * sememe per the dual-role pattern (see
     * {@link Identifier#KEY Identifier} class doc).
     *
     * <p>Equivalent third-party attribution (someone else asserting an
     * Identifier for this User) is just the same frame shape signed by
     * the asserting party instead of the User; this method is the
     * self-attestation convenience.
     *
     * <p>Returns the User for fluent chaining.
     */
    public User identifyAs(Identifier identifier) {
        Objects.requireNonNull(identifier, "identifier");
        if (librarian == null) {
            throw new IllegalStateException(
                    "User has no librarian binding; cannot publish an Identifier frame");
        }
        Body frameBody = Body.of(
                ItemRef.iid(Identifier.KEY),
                List.of(
                        new Binding(ItemRef.iid(ThematicRole.Theme.KEY), iid()),
                        new Binding(ItemRef.iid(ThematicRole.Value.KEY), identifier)));
        librarian.assembleFrame(frameBody, this);
        return this;
    }

    /**
     * Publish the SERVES frame for a freshly-minted User: the librarian
     * asserts its service relationship with the new User.  Signed by the
     * librarian (it's the librarian making the claim).
     */
    private static void publishServes(Librarian librarian, User user) {
        Body servesBody = Body.of(
                ItemRef.iid(IdentityVocabulary.Serves.KEY),
                List.of(
                        new Binding(ItemRef.iid(ThematicRole.Theme.KEY), librarian.iid()),
                        new Binding(ItemRef.iid(ThematicRole.Goal.KEY), user.iid())));
        librarian.assembleFrame(servesBody, librarian);
    }
}
