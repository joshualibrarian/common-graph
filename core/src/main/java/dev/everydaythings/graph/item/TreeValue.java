package dev.everydaythings.graph.item;

import dev.everydaythings.graph.item.id.ItemID;
import lombok.Value;

import java.util.Collection;
import java.util.Map;

/**
 * Wrapper that gives a value tree context (owner + path).
 *
 * <p>Values (scalars, collections, etc.) don't inherently know their
 * context - they're just data. When displayed in the tree, we wrap
 * them with a TreeValue to provide addressability.
 *
 * <p>This allows any value to appear in the tree with proper
 * Link addressability.
 */
@Value
public class TreeValue {

    String label;
    Object value;
    Item owner;
    String path;

    /**
     * Create a TreeValue for a named value within an Item.
     */
    public static TreeValue of(String label, Object value, Item owner, String path) {
        return new TreeValue(label, value, owner, path);
    }

    public Link link() {
        return owner != null ? Link.of(owner.iid(), path) : null;
    }

    public String displayToken() {
        return label;
    }

    public boolean isExpandable() {
        return value instanceof Map || value instanceof Collection;
    }

    public String colorCategory() {
        return "value";
    }

    public String displaySubtitle() {
        if (value == null) return "(null)";
        String str = String.valueOf(value);
        return str.length() > 50 ? str.substring(0, 47) + "..." : str;
    }

    public ItemID icon() {
        return null; // Use default value icon
    }
}
