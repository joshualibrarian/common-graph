package dev.everydaythings.graph.item.id;

import dev.everydaythings.graph.Hash;

import java.nio.charset.StandardCharsets;

public class DirectoryID extends HashID {

    public DirectoryID(String path) {
        super(Hash.DEFAULT.digest(path.getBytes(StandardCharsets.UTF_8)));
    }
}
