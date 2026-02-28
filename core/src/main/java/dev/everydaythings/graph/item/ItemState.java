package dev.everydaythings.graph.item;

import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.item.component.ComponentEntry;
import dev.everydaythings.graph.item.component.ComponentTable;
import lombok.Getter;

import java.util.List;

/**
 * Unified state for an Item version.
 *
 * <p>ItemState wraps a {@link ComponentTable} that holds all of an Item's
 * versioned state: content components, relations, vocabulary, and policy.
 */
@Getter
@Canonical.Canonization
public class ItemState implements Canonical {

    @Canon(order = 0)
    private final ComponentTable content;

    public ItemState() {
        this.content = new ComponentTable();
    }

    public ItemState(ComponentTable content) {
        this.content = content != null ? content : new ComponentTable();
    }

    public void setOwner(Item owner) {
        content.setOwner(owner);
    }

    public List<ComponentEntry> componentSnapshot() {
        return content.stream().toList();
    }

    public void loadComponents(List<ComponentEntry> entries) {
        if (entries == null) return;
        for (ComponentEntry entry : entries) {
            content.add(entry);
        }
    }

    public int totalEntries() {
        return content.size();
    }

    public boolean isEmpty() {
        return content.isEmpty();
    }

    @Override
    public String toString() {
        return "ItemState[components=" + content.size() + "]";
    }
}
