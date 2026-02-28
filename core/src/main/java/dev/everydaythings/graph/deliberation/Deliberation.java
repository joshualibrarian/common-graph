package dev.everydaythings.graph.deliberation;

import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.runtime.Librarian;

/**
 * A Deliberation is an item for collaborative decision-making.
 */
public class Deliberation extends Item {

    // decisionTarget

    protected Deliberation(Librarian librarian) {
        super(librarian);
    }

    protected Deliberation(Librarian librarian, ItemID iid) {
        super(librarian, iid);
    }

    protected Deliberation(Librarian librarian, Manifest manifest) {
        super(librarian, manifest);
    }
}

