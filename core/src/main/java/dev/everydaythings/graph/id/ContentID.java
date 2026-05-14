package dev.everydaythings.graph.id;

import dev.everydaythings.graph.encoding.Digest;
import io.ipfs.multibase.Multibase;
import io.ipfs.multihash.Multihash;

public final class ContentID extends HashID {
    public static final String CONTENT_PREFIX = "\\";
    public static final String PREFIX = CONTENT_PREFIX;

    public ContentID(byte[] rawDigest, Multihash.Type type) {
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
        return new ContentID(Multihash.deserialize(Multibase.decode(t)).toBytes());
    }

    /**
     * Create a ContentID by hashing raw content bytes.
     * Uses Digest.Sha256 — the protocol-pinned default for content addressing.
     */
    public static ContentID of(byte[] content) {
        if (content == null) {
            throw new IllegalArgumentException("content cannot be null");
        }
        byte[] digest = Digest.Sha256.digest(content);
        return new ContentID(digest, Digest.Sha256.MULTIHASH_TYPE);
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
