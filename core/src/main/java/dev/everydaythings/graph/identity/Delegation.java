package dev.everydaythings.graph.identity;

import dev.everydaythings.graph.frame.Binding;
import dev.everydaythings.graph.frame.BindingTarget;
import dev.everydaythings.graph.frame.Body;
import dev.everydaythings.graph.item.Bind;
import dev.everydaythings.graph.item.Embodies;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.Literal;
import dev.everydaythings.graph.item.Seed;
import dev.everydaythings.graph.item.id.CompoundKey;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.linguistics.GrammaticalFeature;
import dev.everydaythings.graph.linguistics.Gloss;
import dev.everydaythings.graph.linguistics.Language;
import dev.everydaythings.graph.linguistics.Lexeme;
import dev.everydaythings.graph.linguistics.PartOfSpeech;
import dev.everydaythings.graph.runtime.Librarian;
import dev.everydaythings.graph.semantics.CoreVocabulary;
import dev.everydaythings.graph.semantics.ThematicRole;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A parent identity's authorization for a child identity to operate under the
 * parent's authority — a first-class assertion of granted scope, separable from
 * any specific delegated event.
 *
 * <p>Parent-issued: authored and signed by the delegator (parent). The child
 * doesn't issue this — they consume it. Decoupled from individual delegated
 * events: a child's INCEPTION/ROTATION just declares
 * {@code AGENT [DELEGATOR] → @parent-iid}; receivers query for matching valid
 * DELEGATION frames at verification time.
 *
 * <p>Scope-bearing: optional {@code PURPOSE} bindings narrow what the child may
 * do (multiset; empty = fully general authority). Common purpose-sememes:
 * {@link IdentityVocabulary.Signing}, {@link IdentityVocabulary.Encryption};
 * future scopes when needed.
 *
 * <p>Revocable / expirable: parent can later REVOKE the delegation (targeting
 * the delegation's body CID); optional {@code ATTRIBUTE [EXPIRES]} gives auto-expiry.
 *
 * <p>Body shape:
 * <pre>
 * DELEGATION
 *     AGENT                  → @parent-iid              # who's delegating
 *     THEME                  → @child-iid               # who's being delegated to
 *     PURPOSE                → some-sememe              # OPTIONAL: scope (multiset)
 *     ATTRIBUTE [EXPIRES]    → timestamp                # OPTIONAL: end of authority
 *     TIME                   → timestamp                # when issued
 *     [+ arbitrary contextual bindings]
 * </pre>
 *
 * <p>Phase 1 keeps {@code onFrameAssembled} minimal — body persistence is enough.
 * Verification (matching delegations to delegated events, checking scope, checking
 * expiry, applying revocations) happens at lookup time when the trust matrix
 * evaluates a delegated event.
 */
@Seed(key = Delegation.KEY)
@Embodies(key = Delegation.KEY)
public class Delegation extends Item {

    /** Canonical key for the delegation sememe. */
    public static final String KEY = "cg.sememe:delegation";

    /** The deterministic IID for the delegation sememe. */
    public static final ItemID IID = ItemID.fromString(KEY);

    @Bind(predicate = Gloss.KEY,
          role = ThematicRole.Value.KEY,
          qualifiers = {Language.English.KEY})
    static final String englishGloss =
            "a parent identity's authorization for a child identity to operate under its authority";

    @Bind(predicate = Lexeme.KEY,
          role = ThematicRole.Value.KEY,
          qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY})
    static final String englishNounLemma = "delegation";

    @Bind(predicate = Lexeme.KEY,
          role = ThematicRole.Value.KEY,
          qualifiers = {Language.English.KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY})
    static final String englishVerbLemma = "delegate";

    public Delegation(ItemID iid, Librarian librarian) {
        super(iid, librarian);
    }

    /**
     * Read the AGENT — the delegator (parent identity).
     */
    public static Optional<ItemID> readDelegator(Body body) {
        return body.binding(CompoundKey.of(ThematicRole.Agent.IID))
                .map(b -> extractIid(b.target()));
    }

    /**
     * Read the THEME — the delegate (child identity being authorized).
     */
    public static Optional<ItemID> readDelegate(Body body) {
        return body.binding(CompoundKey.of(ThematicRole.Theme.IID))
                .map(b -> extractIid(b.target()));
    }

    /**
     * Read the PURPOSE bindings — the scope sememes this delegation covers.
     * Multiset: empty list means "fully general authority"; multiple PURPOSE
     * bindings mean the delegation covers multiple scopes (e.g., both signing
     * and encryption tracks).
     */
    public static List<ItemID> readPurposes(Body body) {
        List<ItemID> purposes = new ArrayList<>();
        for (Binding b : body.bindings()) {
            if (!ThematicRole.Purpose.IID.equals(b.role())) continue;
            ItemID iid = extractIid(b.target());
            if (iid != null) purposes.add(iid);
        }
        return purposes;
    }

    /**
     * Read the {@code ATTRIBUTE [EXPIRES]} timestamp if present — when this
     * delegation's authority ends. Empty if the delegation has no expiry.
     */
    public static Optional<Instant> readExpires(Body body) {
        for (Binding b : body.bindings()) {
            if (!ThematicRole.Attribute.IID.equals(b.role())) continue;
            if (!hasQualifier(b, CoreVocabulary.Expires.IID)) continue;
            if (!(b.target() instanceof Literal lit)) continue;
            if (!Literal.TYPE_INSTANT.equals(lit.valueType())) continue;
            try {
                return Optional.of(lit.asInstantMillis());
            } catch (RuntimeException ignored) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private static boolean hasQualifier(Binding b, ItemID qualifierIid) {
        return b.qualifiers().stream()
                .anyMatch(q -> q instanceof CompoundKey.Sememe s
                        && qualifierIid.equals(s.id()));
    }

    private static ItemID extractIid(BindingTarget target) {
        if (target instanceof BindingTarget.IidTarget iid) return iid.iid();
        if (target instanceof BindingTarget.RefTarget ref && !ref.isCompound()) {
            return ref.asItemId();
        }
        return null;
    }
}
