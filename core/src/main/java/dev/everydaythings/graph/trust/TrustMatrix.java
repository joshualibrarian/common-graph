package dev.everydaythings.graph.trust;

import dev.everydaythings.graph.item.id.ItemID;

public interface TrustMatrix {

    float trustFor(ItemID target, ItemID domain);

}
