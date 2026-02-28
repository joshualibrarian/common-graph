package dev.everydaythings.graph.item.id;

import dev.everydaythings.graph.Hash;
import io.ipfs.multihash.Multihash;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

/**
 * Item-scoped handle ID for content/relation entries.
 *
 * HandleIDs are short, human-friendly identifiers that are unique
 * within an item (not globally). They're used to reference components,
 * mounts, and other item-local resources.
 *
 * <p>HandleIDs use a truncated 20-byte digest (160 bits) rather than
 * the full 32-byte digest used by other IDs. This is sufficient for
 * item-scoped uniqueness while keeping the encoded form shorter.
 */
public class HandleID extends HashID {

    public static final String HID_PREFIX = "hid:";

    /** HandleIDs use 20 bytes (160 bits) - sufficient for item-scoped uniqueness. */
    public static final int HANDLE_LENGTH = 20;

    /**
     * Reconstruct a HandleID from its encoded text form (e.g., "hid:baakzxyz...").
     * Strips the "hid:" prefix and decodes the multibase+multihash bytes.
     *
     * <p>This is the inverse of {@link #encodeText()}.
     * Use {@link #of(String)} to create a HandleID by hashing a name.
     */
    public static HandleID parse(String encodedText) {
        if (encodedText == null || encodedText.isBlank()) {
            throw new IllegalArgumentException("encoded text cannot be null or blank");
        }
        String token = encodedText;
        if (token.startsWith(HID_PREFIX)) {
            token = token.substring(HID_PREFIX.length());
        }
        Multihash mh = Hash.decode(token);
        return new HandleID(mh);
    }

    /**
     * Create a random HandleID with proper 20-byte length.
     *
     * <p>Used for dynamically added components where the HandleID
     * doesn't need to be deterministic.
     */
    public static HandleID random() {
        byte[] bytes = new byte[HANDLE_LENGTH];
        new SecureRandom().nextBytes(bytes);
        return new HandleID(bytes, Hash.ID);
    }

    /**
     * Create a HandleID from a string handle name.
     * The handle is hashed and truncated to 20 bytes.
     */
    public static HandleID of(String handle) {
        if (handle == null || handle.isBlank()) {
            throw new IllegalArgumentException("Handle cannot be null or blank");
        }
        byte[] bytes = handle.getBytes(StandardCharsets.UTF_8);
        byte[] fullDigest = Hash.DEFAULT.digest(bytes);
        // Truncate to 20 bytes for shorter item-scoped IDs
        byte[] truncated = new byte[HANDLE_LENGTH];
        System.arraycopy(fullDigest, 0, truncated, 0, HANDLE_LENGTH);
        // Use Hash.ID type since we have a non-standard length
        return new HandleID(truncated, Hash.ID);
    }

    public HandleID() {
        super();
    }

    public HandleID(Multihash multihash) {
        super(multihash);
    }

    public HandleID(byte[] digest, Hash hash) {
        super(digest, hash);
    }

    public HandleID(byte[] multihashBytes) {
        super(multihashBytes);
    }

    @Override
    public String prefix() {
        return HID_PREFIX;
    }

    @Override
    public String emoji() {
        return "🏷";  // Tag/label - handles are local names
    }
}
