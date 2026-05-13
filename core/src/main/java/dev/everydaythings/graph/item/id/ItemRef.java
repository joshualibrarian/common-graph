package dev.everydaythings.graph.item.id;

import dev.everydaythings.graph.encoding.TextBase;

import java.io.ByteArrayOutputStream;
import java.util.Objects;
import java.util.Optional;

/**
 * Reference to an item, optionally pinned to a specific version.
 *
 * <p>Text form: {@code @<IID>} or {@code @<IID>\<VID>}.
 *
 * <p>Binary form (inside Tag 6): {@code 0x40 <multihash-IID> [0x5C <multihash-VID>]}.
 *
 * @param iid     the item's identity
 * @param version optional version pin; when present, references the specific manifest version
 */
public record ItemRef(ItemID iid, Optional<ContentID> version) implements Reference {

    public ItemRef {
        Objects.requireNonNull(iid, "iid");
        Objects.requireNonNull(version, "version (use Optional.empty() for unpinned)");
    }

    /** Construct an unpinned item reference. */
    public static ItemRef of(ItemID iid) {
        return new ItemRef(iid, Optional.empty());
    }

    /** Construct an item reference pinned to a specific version. */
    public static ItemRef of(ItemID iid, ContentID version) {
        return new ItemRef(iid, Optional.of(version));
    }

    @Override
    public Variant variant() {
        return Variant.ITEM;
    }

    /** Whether this reference is pinned to a specific version. */
    public boolean isPinned() {
        return version.isPresent();
    }

    @Override
    public byte[] toRefBytes() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(PREFIX_ITEM & 0xFF);
        byte[] iidBytes = iid.encodeBinary();
        out.writeBytes(iidBytes);
        if (version.isPresent()) {
            Reference.writeSeparator(out);
            out.writeBytes(version.get().encodeBinary());
        }
        return out.toByteArray();
    }

    @Override
    public String encodeText() {
        StringBuilder sb = new StringBuilder();
        sb.append('@');
        sb.append(TextBase.Base32Lower.encode(iid.encodeBinary()));
        if (version.isPresent()) {
            sb.append('\\');
            sb.append(TextBase.Base32Lower.encode(version.get().encodeBinary()));
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return encodeText();
    }

    /**
     * Decode an ItemRef from its full binary form (including the leading prefix byte).
     *
     * <p>Package-private; called by {@link Reference#fromRefBytes(byte[])}.
     */
    static ItemRef fromRefBytesPayload(byte[] bytes) {
        if (bytes.length < 1 || bytes[0] != PREFIX_ITEM) {
            throw new IllegalArgumentException(
                    "ItemRef payload must start with '@' (0x40)");
        }
        HashID.Slice iidSlice = Reference.readMultihash(bytes, 1);
        ItemID iid = new ItemID(iidSlice.bytes());
        int pos = iidSlice.next();
        Optional<ContentID> version = Optional.empty();
        if (pos < bytes.length) {
            if (bytes[pos] != SEPARATOR) {
                throw new IllegalArgumentException(
                        "ItemRef expected separator (0x5C) after IID, got 0x"
                                + String.format("%02x", bytes[pos] & 0xFF));
            }
            HashID.Slice vidSlice = Reference.readMultihash(bytes, pos + 1);
            version = Optional.of(new ContentID(vidSlice.bytes()));
            if (vidSlice.next() != bytes.length) {
                throw new IllegalArgumentException(
                        "ItemRef has unexpected trailing bytes after VID");
            }
        }
        return new ItemRef(iid, version);
    }

    /**
     * Parse an ItemRef from text form: {@code @<IID>[\<VID>]}.
     */
    static ItemRef parseText(String text) {
        if (text == null || text.isEmpty() || text.charAt(0) != '@') {
            throw new IllegalArgumentException(
                    "ItemRef text must start with '@', got: " + text);
        }
        String body = text.substring(1);
        int sep = body.indexOf('\\');
        if (sep < 0) {
            ItemID iid = new ItemID(io.ipfs.multibase.Multibase.decode(body));
            return new ItemRef(iid, Optional.empty());
        }
        String iidText = body.substring(0, sep);
        String vidText = body.substring(sep + 1);
        ItemID iid = new ItemID(io.ipfs.multibase.Multibase.decode(iidText));
        ContentID vid = new ContentID(io.ipfs.multibase.Multibase.decode(vidText));
        return new ItemRef(iid, Optional.of(vid));
    }
}
