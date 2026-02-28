package dev.everydaythings.graph.item.id;

import dev.everydaythings.graph.Hash;

public final class VersionID extends HashID implements BlockID {
    public static final String VERSION_PREFIX = "@";
    public static final String PREFIX = VERSION_PREFIX;

    /** Parse a version ID from text, guessing the format. */
    public static VersionID bestGuess(String token) {
        if (token == null || token.isEmpty()) {
            throw new IllegalArgumentException("empty version token");
        }
        String t = token.startsWith(PREFIX) ? token.substring(PREFIX.length()) : token;
        return new VersionID(Hash.decode(t).toBytes());
    }

    public VersionID(byte[] serializedMultihash) {
        super(serializedMultihash);
    }

    public VersionID(byte[] rawDigest, Hash type) {
        super(rawDigest, type);
    }

    @Override
    public String prefix() {
        return VERSION_PREFIX;
    }

    @Override
    public String emoji() {
        return "@";  // At sign - represents "at this version"
    }
}
