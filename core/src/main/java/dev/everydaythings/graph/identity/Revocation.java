package dev.everydaythings.graph.identity;

import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.frame.BindingTarget;
import dev.everydaythings.graph.frame.Body;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.id.CompoundKey;
import dev.everydaythings.graph.item.id.ContentID;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.linguistics.GrammaticalFeature;
import dev.everydaythings.graph.linguistics.Gloss;
import dev.everydaythings.graph.linguistics.Language;
import dev.everydaythings.graph.linguistics.Lexeme;
import dev.everydaythings.graph.linguistics.PartOfSpeech;
import dev.everydaythings.graph.runtime.Librarian;
import dev.everydaythings.graph.semantics.ThematicRole;

import java.util.Optional;

/**
 * The generic "I take it back" — a withdrawal of any prior assertion.
 *
 * <p>Polymorphic via target type. The same predicate handles withdrawal of
 * identities, specific events, delegations, trust assertions, retracted opinions,
 * tweets-regretted, factual claims later disproven — anything signed and on the
 * record.
 *
 * <p>Auth-machinery cases (identity / delegation / trust retirement) are specific
 * reactions by the affected items/predicates. Most revocations are just data:
 * receivers' trust matrices and presentation layers interpret the consequence.
 * "They admitted they were wrong" raises social standing in the trust graph
 * without any system-level code reasoning about it.
 *
 * <p>Body shape:
 * <pre>
 * REVOCATION
 *     THEME                  → polymorphic target       # what's being revoked
 *     PURPOSE                → reason-sememe            # OPTIONAL: formal reason
 *                                                        # (CoreVocabulary.Compromise/Retirement/Fraud/Mistake, ...)
 *     TIME                   → timestamp
 *     [+ arbitrary contextual bindings — VALUE for free-text reason, etc.]
 * </pre>
 *
 * <p>Target polymorphism:
 * <ul>
 *   <li>{@code @identity-iid} — retire whole identity (terminal KEL mark)</li>
 *   <li>{@code #event-cid} — repudiate a specific INCEPTION/ROTATION event</li>
 *   <li>{@code #delegation-cid} — withdraw a specific authorization</li>
 *   <li>{@code #trust-cid} — withdraw a TRUST assertion</li>
 *   <li>{@code #any-frame-cid} — retract a tweet, claim, opinion (just data)</li>
 * </ul>
 *
 * <p>Authority via signer: the record signer must have authority over the revoked
 * thing (identity → identity itself; delegation → delegator; trust → truster;
 * arbitrary frame → original signer). Verification rule: the same authority that
 * created the thing is who can revoke it.
 *
 * <p>Recovery / un-revocation: revocation is terminal. To "come back" after
 * identity revocation, incept a new identity. RECOVERY workflows (parent
 * restoring child after compromise) are a separate future concern.
 *
 * <p>Phase 1 keeps {@code onFrameAssembled} minimal — body persistence is enough.
 * Affected sememes (Identity, Delegation, Trust) react via their own queries
 * when they look up "is this still valid?"
 */
@Seed.Item(key = Revocation.KEY, head = dev.everydaythings.graph.item.Item.Predicate.KEY)
@Seed.Embodies(key = Revocation.KEY)
public class Revocation extends Item {

    /** Canonical key for the revocation sememe. */
    public static final String KEY = "cg.sememe:revocation";

    /** The deterministic IID for the revocation sememe. */
    public static final ItemID IID = ItemID.fromString(KEY);

    @Seed.Frame(predicate = Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
    static final String englishGloss =
            "the generic withdrawal of any prior assertion — \"I take it back\"";

    @Seed.Frame(predicate = Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String englishNounLemma = "revocation";

    @Seed.Frame(predicate = Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String englishVerbLemma = "revoke";

    public Revocation(ItemID iid, Librarian librarian) {
        super(iid, librarian);
    }

    /**
     * Read the THEME binding's raw target — the polymorphic "what's being revoked"
     * value. Caller dispatches on the target's concrete type:
     * <ul>
     *   <li>{@link BindingTarget.IidTarget} → identity revocation</li>
     *   <li>{@link BindingTarget.RefTarget} → content-addressed body revocation
     *       (event / delegation / trust / arbitrary frame)</li>
     * </ul>
     */
    public static Optional<BindingTarget> readTarget(Body body) {
        return body.binding(CompoundKey.of(ThematicRole.Theme.IID))
                .map(b -> b.target());
    }

    /**
     * Convenience: if the THEME is an identity reference (IidTarget or non-compound
     * RefTarget pointing at an item), return the IID. Empty otherwise.
     */
    public static Optional<ItemID> readTargetIid(Body body) {
        return readTarget(body).map(Revocation::extractIid).filter(java.util.Objects::nonNull);
    }

    /**
     * Convenience: if the THEME is a content-addressed body reference, return
     * the ContentID. Empty otherwise.
     */
    public static Optional<ContentID> readTargetCid(Body body) {
        return readTarget(body)
                .map(t -> t instanceof BindingTarget.RefTarget ref && !ref.isCompound()
                        ? safeAsCid(ref) : null)
                .filter(java.util.Objects::nonNull);
    }

    /**
     * Read the PURPOSE — the formal reason sememe if present (e.g.,
     * {@link dev.everydaythings.graph.semantics.CoreVocabulary.Compromise},
     * {@link dev.everydaythings.graph.semantics.CoreVocabulary.Retirement}).
     * Empty if no formal reason was provided (free-text VALUE bindings may still
     * carry an explanation).
     */
    public static Optional<ItemID> readReason(Body body) {
        return body.binding(CompoundKey.of(ThematicRole.Purpose.IID))
                .map(b -> extractIid(b.target()))
                .filter(java.util.Objects::nonNull);
    }

    private static ContentID safeAsCid(BindingTarget.RefTarget ref) {
        try {
            return ref.asCid();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static ItemID extractIid(BindingTarget target) {
        if (target instanceof BindingTarget.IidTarget iid) return iid.iid();
        if (target instanceof BindingTarget.RefTarget ref && !ref.isCompound()) {
            return ref.asItemId();
        }
        return null;
    }
}
