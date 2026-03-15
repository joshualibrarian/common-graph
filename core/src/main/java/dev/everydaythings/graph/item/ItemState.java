package dev.everydaythings.graph.item;

import com.upokecenter.cbor.CBORObject;
import com.upokecenter.cbor.CBORType;
import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.frame.EndorsementsTable;
import dev.everydaythings.graph.frame.FrameEndorsement;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Unified state for an Item version.
 *
 * <p>ItemState wraps an {@link EndorsementsTable} that holds all of an Item's
 * versioned state: frames (components, relations, vocabulary, policy).
 *
 * <p><b>Serialization:</b> The manifest serializes {@link FrameEndorsement}
 * objects (key + bodyHash + mounts). The full table is a runtime-only
 * structure reconstructed during hydration.
 *
 * <p><b>Backward compatibility:</b> Old manifests containing legacy frame
 * entry arrays are detected during decode and loaded into the table via
 * {@link EndorsementsTable#fromCborTree(CBORObject)}.
 */
@Getter
public class ItemState implements Canonical {

    /**
     * Endorsements for serialization in the manifest.
     *
     * <p>On commit, built from EndorsementsTable frames. On decode from
     * new-format manifests, populated directly. Null for old-format
     * manifests (entries are loaded directly into {@link #frames}).
     */
    private List<FrameEndorsement> endorsements;

    /** Runtime frame table — transient, not serialized. */
    private transient final EndorsementsTable frames;

    public ItemState() {
        this.frames = new EndorsementsTable();
    }

    public ItemState(EndorsementsTable frames) {
        this.frames = frames != null ? frames : new EndorsementsTable();
    }

    public void setOwner(Item owner) {
        frames.setOwner(owner);
    }

    /**
     * Build endorsements from the current table.
     *
     * <p>Called during commit to prepare the manifest serialization.
     */
    public void buildEndorsements() {
        this.endorsements = frames.buildEndorsements();
    }

    /**
     * Get endorsements, or an empty list if none built yet.
     */
    public List<FrameEndorsement> endorsements() {
        return endorsements != null ? endorsements : List.of();
    }

    /** @deprecated Use {@link #frames()} */
    @Deprecated
    public EndorsementsTable content() {
        return frames;
    }

    public int totalEntries() {
        return frames.size();
    }

    public boolean isEmpty() {
        return frames.isEmpty() && (endorsements == null || endorsements.isEmpty());
    }

    // ==================================================================================
    // CBOR Encoding / Decoding
    // ==================================================================================

    @Override
    public CBORObject toCborTree(Scope scope) {
        CBORObject array = CBORObject.NewArray();

        if (endorsements != null && !endorsements.isEmpty()) {
            // New format: serialize endorsements
            CBORObject endorsementsArray = CBORObject.NewArray();
            for (FrameEndorsement e : endorsements) {
                endorsementsArray.Add(e.toCborTree(scope));
            }
            array.Add(endorsementsArray);
        } else {
            // Fallback: serialize table (old format, for compatibility)
            array.Add(frames.toCborTree(scope));
        }

        return array;
    }

    @Factory
    public static ItemState fromCborTree(CBORObject node) {
        if (node == null || node.isNull()) return new ItemState();
        if (node.getType() != CBORType.Array || node.size() == 0) return new ItemState();

        CBORObject firstField = node.get(0);
        if (firstField == null || firstField.isNull() || firstField.getType() != CBORType.Array) {
            return new ItemState();
        }

        // Detect format by inspecting the first element of the inner array
        if (firstField.size() > 0) {
            CBORObject firstElement = firstField.get(0);
            if (isEndorsementFormat(firstElement)) {
                return decodeEndorsementFormat(firstField);
            }
        }

        // Old format: legacy frame entries — decode via EndorsementsTable's legacy CBOR support
        EndorsementsTable table = EndorsementsTable.fromCborTree(firstField);
        return new ItemState(table);
    }

    /**
     * Detect if a CBOR element looks like a FrameEndorsement rather than a legacy frame entry.
     */
    private static boolean isEndorsementFormat(CBORObject element) {
        if (element == null || element.isNull()) return false;
        if (element.getType() != CBORType.Array) return false;
        return element.size() <= 4;
    }

    private static ItemState decodeEndorsementFormat(CBORObject endorsementsArray) {
        ItemState state = new ItemState();
        List<FrameEndorsement> endorsements = new ArrayList<>();
        for (CBORObject eNode : endorsementsArray.getValues()) {
            FrameEndorsement e = Canonical.fromCborTree(eNode, FrameEndorsement.class, Scope.RECORD);
            if (e != null) {
                endorsements.add(e);
            }
        }
        state.endorsements = Collections.unmodifiableList(endorsements);
        return state;
    }

    @Override
    public String toString() {
        if (endorsements != null && !endorsements.isEmpty()) {
            return "ItemState[endorsements=" + endorsements.size() + "]";
        }
        return "ItemState[frames=" + frames.size() + "]";
    }
}
