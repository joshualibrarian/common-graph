package dev.everydaythings.graph.item;

import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.Canonical.Canon;
import dev.everydaythings.graph.item.id.ItemID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pre-bound parameter values for a verb.
 *
 * <p>Presets enable per-item shortcuts that fill in common parameter
 * combinations. For example, a "quick game" preset on chess could
 * pre-bind time control and color preferences.
 *
 * <p>Stored in {@link Vocabulary}'s custom layer and serialized via CG-CBOR.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Canonical.Canonization
public class VerbPreset implements Canonical {

    /** Human-readable preset name. */
    @Canon(order = 0)
    private String name;

    /** The target verb sememe ID. */
    @Canon(order = 1)
    private ItemID sememeId;

    /** Parameter name → serialized value bindings. */
    @Canon(order = 2)
    private Map<String, String> bindings = new LinkedHashMap<>();

    @Override
    public String toString() {
        return name + " [" + sememeId.encodeText() + "] " + bindings;
    }
}
