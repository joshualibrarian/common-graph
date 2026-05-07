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
import dev.everydaythings.graph.item.id.ContentID;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.linguistics.GrammaticalFeature;
import dev.everydaythings.graph.linguistics.Gloss;
import dev.everydaythings.graph.linguistics.Language;
import dev.everydaythings.graph.linguistics.Lexeme;
import dev.everydaythings.graph.linguistics.PartOfSpeech;
import dev.everydaythings.graph.runtime.Librarian;
import dev.everydaythings.graph.semantics.CoreVocabulary;
import dev.everydaythings.graph.semantics.ThematicRole;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The next establishment event in an identity's KEL for one key-track.
 *
 * <p>A ROTATION replaces the current key-set with a new one, reveals the preimage
 * of the prior pre-rotation commitment, and publishes a fresh pre-rotation commitment
 * for the next round. Per-identity, per-track — signing-track and encryption-track
 * rotate independently on independent KELs.
 *
 * <p>Chain-anchored: {@code FOLLOWS → #prior-event-CID} links to the preceding
 * INCEPTION or ROTATION. KEL replay walks this chain backward to verify history.
 *
 * <p>Preimage-revealing: the new {@code INSTRUMENT} keys hash to one of the prior
 * event's {@code INSTRUMENT [NEXT]} digests. This proves the rotator possessed the
 * next-key all along, defeating current-key-compromise attacks (an attacker with
 * the current key can't rotate to their own key without also having the next-key
 * preimage).
 *
 * <p>Authority-asymmetric: the rotation is authorized by the OLD keys (records
 * signed by the prior establishment event's INSTRUMENT keys) and proven by the
 * NEW keys (records signed by the new INSTRUMENT keys). A valid rotation typically
 * carries both kinds of signatures.
 *
 * <p>Body shape (= INCEPTION + FOLLOWS + SEQUENCE):
 * <pre>
 * ROTATION
 *     THEME                  → @identity-iid
 *     PURPOSE                → cg.purpose:signing (or encryption)
 *     FOLLOWS                → #prior-event-CID         # chain link
 *     ATTRIBUTE [SEQUENCE]   → integer                  # n+1
 *     INSTRUMENT             → MultiKey                 # NEW current keys (preimage of prior NEXT)
 *     INSTRUMENT [NEXT]      → #digest                  # NEW pre-rotation commitments
 *     ATTRIBUTE [THRESHOLD]  → integer                  # new m-of-n
 *     PARTNER [WITNESS]      → @witness-iid             # OPTIONAL: full current witness set
 *     AGENT [DELEGATOR]      → @parent-iid              # OPTIONAL: only delegated
 *     TIME                   → timestamp
 * </pre>
 *
 * <p>Phase 1 keeps {@code onFrameAssembled} minimal — body persistence is enough.
 * Future phases add full chain-replay verification (preimage match, sequence,
 * old/new key signatures) and key-state cache advancement.
 */
@Seed(key = Rotation.KEY)
@Embodies(key = Rotation.KEY)
public class Rotation extends Item {

    /** Canonical key for the rotation sememe. */
    public static final String KEY = "cg.sememe:rotation";

    /** The deterministic IID for the rotation sememe. */
    public static final ItemID IID = ItemID.fromString(KEY);

    @Bind(predicate = Gloss.KEY,
          role = ThematicRole.Value.KEY,
          qualifiers = {Language.English.KEY})
    static final String englishGloss =
            "evolving an identity's committed keys for one key-track, with pre-rotation reveal";

    @Bind(predicate = Lexeme.KEY,
          role = ThematicRole.Value.KEY,
          qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY})
    static final String englishNounLemma = "rotation";

    @Bind(predicate = Lexeme.KEY,
          role = ThematicRole.Value.KEY,
          qualifiers = {Language.English.KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY})
    static final String englishVerbLemma = "rotate";

    public Rotation(ItemID iid, Librarian librarian) {
        super(iid, librarian);
    }

    /**
     * Read the FOLLOWS chain link — the content-reference to the prior establishment
     * event (INCEPTION or previous ROTATION).
     */
    public static Optional<ContentID> readFollows(Body body) {
        return body.binding(CompoundKey.of(ThematicRole.Follows.IID))
                .map(b -> extractContentId(b.target()));
    }

    /**
     * Read the SEQUENCE number — the rotation's ordinal position. INCEPTION is
     * implicit 0; first rotation is 1; each subsequent rotation increments.
     */
    public static Optional<Long> readSequence(Body body) {
        for (Binding b : body.bindings()) {
            if (!ThematicRole.Attribute.IID.equals(b.role())) continue;
            if (!hasQualifier(b, CoreVocabulary.Sequence.IID)) continue;
            if (!(b.target() instanceof Literal lit)) continue;
            if (!Literal.TYPE_INTEGER.equals(lit.valueType())) continue;
            try {
                return Optional.of(lit.asInteger());
            } catch (RuntimeException ignored) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    /**
     * Extract the next-key digest content-references from the body's
     * {@code INSTRUMENT [NEXT]} bindings — the pre-rotation commitments.
     */
    public static List<ContentID> nextKeyDigests(Body body) {
        List<ContentID> digests = new ArrayList<>();
        for (Binding b : body.bindings()) {
            if (!ThematicRole.Instrument.IID.equals(b.role())) continue;
            if (!hasQualifier(b, CoreVocabulary.Next.IID)) continue;
            ContentID cid = extractContentId(b.target());
            if (cid != null) digests.add(cid);
        }
        return digests;
    }

    private static boolean hasQualifier(Binding b, ItemID qualifierIid) {
        return b.qualifiers().stream()
                .anyMatch(q -> q instanceof CompoundKey.Sememe s
                        && qualifierIid.equals(s.id()));
    }

    private static ContentID extractContentId(BindingTarget target) {
        if (target instanceof BindingTarget.RefTarget ref && !ref.isCompound()) {
            try {
                return ref.asCid();
            } catch (RuntimeException ignored) {
                return null;
            }
        }
        return null;
    }
}
