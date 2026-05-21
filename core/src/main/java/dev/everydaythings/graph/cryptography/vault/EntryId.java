package dev.everydaythings.graph.cryptography.vault;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;

/**
 * EntryId — an entry's stable identifier within its owning vault.
 *
 * <p>Opaque 128-bit value, vault-minted when an entry is first created.
 * Stable across version-chained updates (an entry that mutates by being
 * superseded by a new version via a {@code FOLLOWS} binding keeps the same
 * EntryId across all versions in the chain).
 *
 * <p>Distinct from CG content addresses (CIDs / IIDs).  An EntryId names
 * "this thread of entries in this vault"; a body's CID names "these specific
 * bytes."  When an entry is updated by appending a new version, the new
 * version has a new body CID but the same EntryId.
 *
 * <p>Never leaves the vault's data plane.  Not signed, not published,
 * not used as a binding target in CG-store frames.  Vault-internal only.
 */
public final class EntryId {

    private static final SecureRandom RNG = new SecureRandom();
    private static final int LENGTH_BYTES = 16;
    private static final HexFormat HEX = HexFormat.of();

    private final byte[] bytes;

    private EntryId(byte[] bytes) {
        this.bytes = bytes;
    }

    /** Mint a fresh random EntryId. */
    public static EntryId fresh() {
        byte[] buf = new byte[LENGTH_BYTES];
        RNG.nextBytes(buf);
        return new EntryId(buf);
    }

    /** Wrap existing bytes as an EntryId (defensive copy). */
    public static EntryId of(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length != LENGTH_BYTES) {
            throw new IllegalArgumentException(
                    "EntryId requires " + LENGTH_BYTES + " bytes, got " + bytes.length);
        }
        return new EntryId(bytes.clone());
    }

    /** Parse a hex-encoded EntryId. */
    public static EntryId parse(String hex) {
        Objects.requireNonNull(hex, "hex");
        byte[] decoded = HEX.parseHex(hex);
        return of(decoded);
    }

    /** Defensive copy of the underlying bytes. */
    public byte[] bytes() {
        return bytes.clone();
    }

    /** Hex-string encoding suitable for filenames, logs, debugging. */
    public String asHex() {
        return HEX.formatHex(bytes);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EntryId other)) return false;
        return Arrays.equals(bytes, other.bytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bytes);
    }

    @Override
    public String toString() {
        return "EntryId{" + asHex() + "}";
    }
}
