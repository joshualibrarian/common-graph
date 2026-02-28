package dev.everydaythings.graph.item.id;

import dev.everydaythings.graph.Hash;

public final class ContentID extends HashID implements BlockID {
    public static final String CONTENT_PREFIX = "\\";
    public static final String PREFIX = CONTENT_PREFIX;

    public ContentID(byte[] rawDigest, Hash type) {
        super(rawDigest, type);
    }

    public ContentID(byte[] serializedMultihash) {
        super(serializedMultihash);
    }

    /** Parse a content ID from text, guessing the format. */
    public static ContentID bestGuess(String token) {
        if (token == null || token.isEmpty()) {
            throw new IllegalArgumentException("empty content token");
        }
        String t = token.startsWith(PREFIX) ? token.substring(PREFIX.length()) : token;
        return new ContentID(Hash.decode(t).toBytes());
    }

    /**
     * Create a ContentID by hashing raw content bytes.
     * Uses Hash.DEFAULT (SHA2-256) algorithm.
     */
    public static ContentID of(byte[] content) {
        if (content == null) {
            throw new IllegalArgumentException("content cannot be null");
        }
        byte[] digest = Hash.DEFAULT.digest(content);
        return new ContentID(digest, Hash.DEFAULT);
    }

    @Override
    public String prefix() {
        return CONTENT_PREFIX;
    }

    @Override
    public String emoji() {
        return "📄";  // Document - content is stored data
    }
}
