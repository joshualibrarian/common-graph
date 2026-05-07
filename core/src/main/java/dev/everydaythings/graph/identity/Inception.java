package dev.everydaythings.graph.identity;

import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.crypt.MultiKey;
import dev.everydaythings.graph.crypt.VarSig;
import dev.everydaythings.graph.frame.Binding;
import dev.everydaythings.graph.frame.BindingTarget;
import dev.everydaythings.graph.frame.Body;
import dev.everydaythings.graph.frame.Frame;
import dev.everydaythings.graph.frame.Record;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The founding key-state declaration for one identity's one key-track.
 *
 * <p>An INCEPTION frame establishes the first cryptographic capability for an
 * identity on a specific track (signing or encryption). Per-identity, per-track:
 * separate signing-track and encryption-track INCEPTION events start independent KELs.
 *
 * <p>Self-anchored: the body's records must include at least one signature from
 * the keys committed in the body. This is the cryptographic foundation —
 * without self-attestation, anyone could forge an inception for anyone's identity.
 *
 * <p>Forward-committing: optional {@code INSTRUMENT [NEXT]} bindings carry the
 * digest(s) of the next-key-set, enabling pre-rotation. ROTATION reveals the
 * preimage when the keys are actually rolled.
 *
 * <p>Optionally delegated: when {@code AGENT [DELEGATOR] → @parent-iid} is present,
 * a corresponding {@link Delegation} frame from the parent authorizes this child's
 * inception. Otherwise the inception is self-asserting only.
 *
 * <p>Body shape:
 * <pre>
 * INCEPTION
 *     THEME                  → @identity-iid           # who's being incepted
 *     PURPOSE                → cg.purpose:signing (or encryption)
 *     INSTRUMENT             → MultiKey                # current keys (multiset)
 *     INSTRUMENT [NEXT]      → #digest                 # pre-rotation commitments (multiset)
 *     ATTRIBUTE [THRESHOLD]  → integer                 # m-of-n; default 1
 *     PARTNER [WITNESS]      → @witness-iid            # OPTIONAL: designated witnesses
 *     AGENT [DELEGATOR]      → @parent-iid             # OPTIONAL: only if delegated
 *     TIME                   → timestamp
 * </pre>
 *
 * <p>This Java class registers Inception as a seed sememe and provides utilities
 * for reading inception bodies. Phase 1 keeps {@code onFrameAssembled} minimal —
 * persistence is enough; key-state lookup happens on-demand by querying for valid
 * inceptions when a verification is needed. Future phases add a librarian-side
 * key-state cache populated from valid inceptions/rotations.
 */
@Seed(key = Inception.KEY)
@Embodies(key = Inception.KEY)
public class Inception extends Item {

    /** Canonical key for the inception sememe. POS-agnostic — a unit of meaning. */
    public static final String KEY = "cg.sememe:inception";

    /** The deterministic IID for the inception sememe. */
    public static final ItemID IID = ItemID.fromString(KEY);

    @Bind(predicate = Gloss.KEY,
          role = ThematicRole.Value.KEY,
          qualifiers = {Language.English.KEY})
    static final String englishGloss =
            "the founding key-state declaration for one identity's one key-track";

    @Bind(predicate = Lexeme.KEY,
          role = ThematicRole.Value.KEY,
          qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY})
    static final String englishNounLemma = "inception";

    @Bind(predicate = Lexeme.KEY,
          role = ThematicRole.Value.KEY,
          qualifiers = {Language.English.KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY})
    static final String englishVerbLemma = "incept";

    public Inception(ItemID iid, Librarian librarian) {
        super(iid, librarian);
    }

    /**
     * Read the THEME identity from an INCEPTION body — who's being incepted.
     */
    public static Optional<ItemID> readTheme(Body body) {
        return body.binding(CompoundKey.of(ThematicRole.Theme.IID))
                .map(b -> extractIid(b.target()));
    }

    /**
     * Read the PURPOSE (key-track) sememe from an INCEPTION body — typically
     * {@link IdentityVocabulary.Signing#IID} or {@link IdentityVocabulary.Encryption#IID}.
     */
    public static Optional<ItemID> readPurpose(Body body) {
        return body.binding(CompoundKey.of(ThematicRole.Purpose.IID))
                .map(b -> extractIid(b.target()));
    }

    /**
     * Extract the current (non-NEXT) INSTRUMENT keys from an INCEPTION or ROTATION body.
     *
     * <p>{@code INSTRUMENT} bindings without a {@code NEXT} qualifier carry the
     * currently-committed keys; {@code INSTRUMENT [NEXT]} bindings carry pre-rotation
     * digests (forward commitments). This method returns the former.
     */
    public static List<MultiKey> currentKeys(Body body) {
        List<MultiKey> keys = new ArrayList<>();
        for (Binding b : body.bindings()) {
            if (!ThematicRole.Instrument.IID.equals(b.role())) continue;
            if (hasQualifier(b, CoreVocabulary.Next.IID)) continue;
            if (!(b.target() instanceof Literal lit)) continue;
            if (!Literal.TYPE_MULTIKEY.equals(lit.valueType())) continue;
            try {
                keys.add(lit.asMultiKey());
            } catch (RuntimeException ignored) {
                // Bad key bytes; skip
            }
        }
        return keys;
    }

    /**
     * Self-attestation check: at least one record's signature must verify against
     * one of the keys committed in the body. Without this, the inception isn't
     * cryptographically anchored to its own identity claim.
     */
    public static boolean isSelfAttested(Frame frame) {
        List<MultiKey> committedKeys = currentKeys(frame.body());
        if (committedKeys.isEmpty()) return false;

        byte[] signedBytes = frame.body().encodeBinary(Canonical.Scope.BODY);
        for (Record record : frame.records()) {
            VarSig sig = record.varsig();
            for (MultiKey key : committedKeys) {
                if (Librarian.verify(key, signedBytes, sig)) return true;
            }
        }
        return false;
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
