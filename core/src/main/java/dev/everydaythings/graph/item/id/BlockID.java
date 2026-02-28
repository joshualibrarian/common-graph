package dev.everydaythings.graph.item.id;

import dev.everydaythings.graph.Canonical;

public sealed interface BlockID extends Canonical
        permits ContentID, VersionID, RelationID {

}
