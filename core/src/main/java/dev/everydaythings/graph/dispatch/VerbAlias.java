package dev.everydaythings.graph.dispatch;

import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.Canonical.Canon;
import dev.everydaythings.graph.item.id.ItemID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Maps a custom token to an existing verb sememe.
 *
 * <p>Aliases enable per-item customization of verb dispatch:
 * "make" → CREATE, "mv" → MOVE, etc. Stored in {@link Vocabulary}'s
 * custom layer and serialized via CG-CBOR.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Canonical.Canonization
public class VerbAlias implements Canonical {

    /** The alias text (e.g. "make", "mv"). */
    @Canon(order = 0)
    private String token;

    /** The target verb sememe ID. */
    @Canon(order = 1)
    private ItemID sememeId;

    @Override
    public String toString() {
        return token + " → " + sememeId.encodeText();
    }
}
