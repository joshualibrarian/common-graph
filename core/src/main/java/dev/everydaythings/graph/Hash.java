package dev.everydaythings.graph;

import io.ipfs.multibase.Multibase;
import io.ipfs.multihash.Multihash;
import io.ipfs.multihash.Multihash.Type;
import lombok.AllArgsConstructor;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Objects;

@AllArgsConstructor
public enum Hash {
    ID       (Type.id,       null),
    SHA1     (Type.sha1,     "SHA-1"),
    SHA2_256 (Type.sha2_256, "SHA-256"),
    SHA2_512 (Type.sha2_512, "SHA-512"),
    SHA3_256 (Type.sha3_256, "SHA3-256"),
    SHA3_512 (Type.sha3_512, "SHA3-512"),
    // Optional (via provider):
    BLAKE2B_256 (Type.blake2b_256, "BLAKE2B-256"),
    BLAKE2B_512 (Type.blake2b_512, "BLAKE2B-512"),
    BLAKE3_256  (Type.blake3,      "BLAKE3-256"); // if your multihash enum has it

    public final Type multihashType;
    public final String jcaName;

    public MessageDigest digest() {
        return messageDigest(jcaName);
    }

    public byte[] digest(byte[] bytes) {
        if (jcaName == null) return null;
        Objects.requireNonNull(bytes, "no bytes to hash");
        return messageDigest(jcaName).digest(bytes);
    }

    public Multihash digestToMultihash(byte[] bytes) {
        if (jcaName == null) return null;
        Objects.requireNonNull(bytes, "no bytes to hash");
        byte[] digest = digest(bytes);
        Objects.requireNonNull(digest, "missing digest");
        return new Multihash(multihashType, digest);
    }

//    public Multihash multihashFor(byte[] bytes) {
//        if (jcaName == null) return null;
//        return new Multihash(multihashType, digest(bytes));
//    }

    public static final Hash DEFAULT = Hash.SHA2_256;

    public static Hash of(Type multihashType) {
        for (Hash h : values()) {
            if (h.multihashType == multihashType) return h;
        }
        throw new IllegalArgumentException("Unsupported type: " + multihashType);
    }

    public static Hash of(String s) {
        String k = s.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return Hash.valueOf(k);
    }

    public static Multihash decode(String encoded) {
        return Multihash.deserialize(Multibase.decode(encoded));
    }

    private static MessageDigest messageDigest(String name) {
        try {
            return MessageDigest.getInstance(name);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException(name, e);
        }
    }
}
