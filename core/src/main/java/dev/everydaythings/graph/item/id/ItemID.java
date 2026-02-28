package dev.everydaythings.graph.item.id;

import dev.everydaythings.graph.Hash;
import io.ipfs.multihash.Multihash;
import lombok.extern.log4j.Log4j2;

import java.nio.charset.StandardCharsets;

import static java.util.Objects.requireNonNull;

/**
 * Pure Item Identity (IID only).
 *
 * Canonical text:  iid:<multibase(multihash-bytes)>
 * Canonical binary: multihash bytes
 */
//@Log4j2
public final class ItemID extends HashID {

    public static final String IID_PREFIX = "iid:";

    /** Generate a random ItemID (256-bit random identity). */
    public static ItemID random() {
        return new ItemID();
    }

    /**
     * Create a deterministic ItemID from a string.
     * Useful for creating well-known IDs (predicates, types, etc.) from names.
     */
    public static ItemID fromString(String s) {
        requireNonNull(s, "s");
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        ItemID result = new ItemID(Hash.DEFAULT.digest(bytes), Hash.DEFAULT);
        return result;
    }

    /** Strict: requires iid: prefix. */
    public static ItemID parse(String text) {
        requireNonNull(text, "text");
        if (containsWhitespace(text)) throw new IllegalArgumentException("whitespace not allowed in ItemID: " + text);
        if (!text.startsWith(IID_PREFIX)) throw new IllegalArgumentException("ItemID must start with " + IID_PREFIX);
        String token = text.substring(IID_PREFIX.length());
        if (token.isEmpty()) throw new IllegalArgumentException("empty iid token");
        return new ItemID(Hash.decode(token));
    }

    public ItemID(Multihash multihash) {
        super(multihash);
    }

    public ItemID(byte[] multihashBytes) {
        super(multihashBytes);
    }

    public ItemID(byte[] rawDigest, Hash type) {
        super(rawDigest, type);
    }

    public ItemID(String multibaseText) {
        super(multibaseText);
    }

    public ItemID() {
        super();
    }

    @Override
    public byte[] encodeBinary() {
        return multihash.toBytes();
    }

    @Override
    public String prefix() {
        return IID_PREFIX;
    }

    @Override
    public String emoji() {
        return "📦";  // Package - items are packages of content
    }
}