package dev.everydaythings.graph.item.mount;

import dev.everydaythings.graph.item.id.BlockID;
import dev.everydaythings.graph.item.id.DirectoryID;
import dev.everydaythings.graph.item.id.HandleID;

// Target: what a path resolves to
public sealed interface Target permits
        Target.ImmutableBlock,
        Target.MutableHandle,
        Target.Directory,
        Target.NotFound {

    record ImmutableBlock(BlockID id) implements Target {}
    record MutableHandle(HandleID hid) implements Target {}
    record Directory(DirectoryID id) implements Target {} // can be synthetic, see below
    record NotFound() implements Target {}
}
