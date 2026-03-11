package dev.everydaythings.graph.item.id;

import dev.everydaythings.graph.Hash;
import io.ipfs.multihash.Multihash;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.Objects;

/**
 * @deprecated Relations are frames. Use body hash ({@link ContentID}) instead.
 */
@Deprecated
@Getter
@EqualsAndHashCode(callSuper = true)
public final class RelationID extends HashID implements BlockID {
    public static final String RELATION_PREFIX = "#";
    public static final String PREFIX = RELATION_PREFIX;

    /** Parse a relation ID from text, guessing the format. */
    public static RelationID bestGuess(String token) {
        if (token == null || token.isEmpty()) {
            throw new IllegalArgumentException("empty relation token");
        }
        String t = token.startsWith(PREFIX) ? token.substring(PREFIX.length()) : token;
        return new RelationID(Hash.decode(t).toBytes());
    }

    public RelationID(byte[] serializedMultihash) {
        super(serializedMultihash);
    }

    public RelationID(byte[] rawDigest, Hash type) {
        super(rawDigest, type);
    }

    @Override
    public String prefix() {
        return RELATION_PREFIX;
    }

    @Override
    public String emoji() {
        return "🔗";  // Link - relations connect items
    }
}