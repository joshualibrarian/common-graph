package dev.everydaythings.graph.item.id;

import dev.everydaythings.graph.Hash;
import io.ipfs.multihash.Multihash;
import lombok.extern.log4j.Log4j2;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

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
     * Cache of canonical-key → ItemID. Held in a lazy holder class so the cache
     * isn't initialized as part of {@code ItemID}'s own static init — that would
     * cause an NPE when bootstrap code (e.g. {@code HashID.DISPLAY_WIDTH}) calls
     * {@code ItemID.fromString} before {@code ItemID}'s static fields finish.
     *
     * <p>Canonical keys are a bounded vocabulary (a few hundred entries even in a
     * richly-seeded librarian), so the map does not need eviction. The cache makes
     * {@code fromString(KEY)} cheap enough to use directly at call sites instead of
     * holding a precomputed {@code public static final ItemID IID} on every seed
     * class.
     */
    private static final class FromStringCache {
        static final ConcurrentHashMap<String, ItemID> CACHE = new ConcurrentHashMap<>();
    }

    /**
     * Create a deterministic ItemID from a string.
     * Useful for creating well-known IDs (predicates, types, etc.) from names.
     *
     * <p>Memoized: repeated calls with the same string return the same instance.
     */
    public static ItemID fromString(String s) {
        requireNonNull(s, "s");
        return FromStringCache.CACHE.computeIfAbsent(s, ItemID::computeFromString);
    }

    private static ItemID computeFromString(String s) {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        return new ItemID(Hash.DEFAULT.digest(bytes), Hash.DEFAULT);
    }

    /**
     * Create an ItemID by hashing the given multikey-encoded bytes — used to
     * derive a Signer's IID from its initial signing public key, cryptographically
     * binding identity to key. An attacker can't preemptively claim an IID
     * because they can't generate a key whose multikey-encoded bytes hash to it.
     *
     * <p>This is the CG analog of KERI's AID-from-inception derivation. Use for
     * Signers (Users, Bots, Librarians, Groups, Hosts-when-active) — anything
     * with an initial signing keypair. Sememes and other content-addressed
     * concepts continue to use {@link #fromString(String)} from a canonical key.
     */
    public static ItemID fromMultikeyBytes(byte[] multikeyBytes) {
        requireNonNull(multikeyBytes, "multikeyBytes");
        return new ItemID(Hash.DEFAULT.digest(multikeyBytes), Hash.DEFAULT);
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