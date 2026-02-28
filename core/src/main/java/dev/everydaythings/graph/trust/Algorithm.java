package dev.everydaythings.graph.trust;

import com.upokecenter.cbor.CBORObject;
import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.item.component.Factory;
import lombok.AllArgsConstructor;
import lombok.Getter;
import java.util.*;

/**
 * COSE algorithm identifiers.
 *
 * <p>Encodes as a plain CBOR integer (positive or negative) per COSE specification.
 * This is more compact than the previous 4-byte encoding:
 * <ul>
 *   <li>ED25519 (-8) encodes as 1 byte (0x27)</li>
 *   <li>AES_GCM_128 (1) encodes as 1 byte (0x01)</li>
 * </ul>
 */
public sealed interface Algorithm extends Canonical
        permits Algorithm.Asymmetric, Algorithm.Aead {

    int coseId();
    Kind kind();

    enum Kind { SIGN, KEY_MGMT, AEAD }

    // === Canonical encoding ===

    /**
     * Encode as CBOR integer (COSE algorithm ID).
     */
    @Override
    default CBORObject toCborTree(Scope scope) {
        return CBORObject.FromInt32(coseId());
    }

    /**
     * Decode from CBOR integer.
     *
     * <p>Annotated with {@code @Decoder} to enable explicit decoder discovery
     * instead of relying on magic method name matching.
     */
    @Factory
    static Algorithm fromCborTree(CBORObject node) {
        return fromCose(node.AsInt32());
    }

    /**
     * Text form is the exact enum constant name (ALL CAPS).
     */
    default String encodeText() {
        return ((Enum<?>) this).name();
    }

    @Factory
    static Algorithm decodeText(String text) {
        Objects.requireNonNull(text, "text");
        try { return Sign.valueOf(text);    } catch (IllegalArgumentException ignored) {}
        try { return KeyMgmt.valueOf(text); } catch (IllegalArgumentException ignored) {}
        try { return Aead.valueOf(text);    } catch (IllegalArgumentException ignored) {}
        throw new IllegalArgumentException("Unknown Algorithm text (expected exact enum name): " + text);
    }

    /**
     * Lazy holder for COSE ID map to avoid class initialization ordering issues.
     * The BY_COSE map is built when first accessed, after all enum constants are initialized.
     */
    final class ByCoseHolder {
        static final Map<Integer, Algorithm> BY_COSE;
        static {
            Map<Integer, Algorithm> m = HashMap.newHashMap(16);
            for (Algorithm a : Sign.values())    m.put(a.coseId(), a);
            for (Algorithm a : KeyMgmt.values()) m.put(a.coseId(), a);
            for (Algorithm a : Aead.values())    m.put(a.coseId(), a);
            BY_COSE = Collections.unmodifiableMap(m);
        }
    }

    static Algorithm fromCose(int coseId) {
        Algorithm a = ByCoseHolder.BY_COSE.get(coseId);
        if (a == null) throw new IllegalArgumentException("Unknown COSE alg id: " + coseId);
        return a;
    }

    /* ---- asymmetric-only details ---- */
    sealed interface Asymmetric extends Algorithm permits Sign, KeyMgmt {

        KeyFamily keyFamily();

        String keyFactoryName();               // JCA KeyFactory (e.g., "Ed25519", "EC", "RSA")

        enum KeyFamily { OKP, EC, RSA }
    }

    /* SIGNING algorithms (Ed25519, ES256, PS256, …) */
    @AllArgsConstructor @Getter
    enum Sign implements Asymmetric {
        ED25519(-8,  KeyFamily.OKP, "Ed25519", "Ed25519",      null),          // curveName null for OKP
        ES256  (-7,  KeyFamily.EC,  "EC",      "SHA256withECDSA", "secp256r1"),
        ES256K (-47, KeyFamily.EC,  "EC",      "SHA256withECDSA", "secp256k1"),
        PS256  (-37, KeyFamily.RSA, "RSA",     "RSASSA-PSS",      null);

        private final int coseId;
        private final Kind kind = Kind.SIGN;
        private final KeyFamily keyFamily;
        private final String keyFactoryName;
        private final String signatureName;
        private final String curveName;

        // inside Algorithm.Sign enum
        public static Sign byJcaAlgorithmName(String algName) {
            if (algName == null) throw new IllegalArgumentException("algName is null");
            // JCA PublicKey.getAlgorithm() typical values: "Ed25519", "EC", "RSA"
            switch (algName) {
                case "Ed25519", "EdDSA" -> { return ED25519; }
                case "EC", "ECDSA"      -> { return ES256; }   // default EC → ES256 (P-256)
                case "RSA"              -> { return PS256; }   // default RSA → PS256
                default -> throw new IllegalArgumentException("Unsupported JCA PublicKey algorithm: " + algName);
            }
        }
    }

    /* KEY MANAGEMENT (agreement / transport for CEKs) */
    @AllArgsConstructor @Getter
    enum KeyMgmt implements Asymmetric {
        // Agreement: ECDH-ES + HKDF-SHA256 (curves driven by key material: P-256, X25519)
        ECDH_ES_HKDF_256(-25, KeyFamily.EC,  "EC",  "ECDH",  "HKDF-SHA256"),
        // Transport: RSA-OAEP with SHA-256
        RSA_OAEP_256     (-41, KeyFamily.RSA, "RSA", null,    "OAEP-SHA256");

        private final int coseId;
        private final Kind kind = Kind.KEY_MGMT;
        private final KeyFamily keyFamily;
        private final String keyFactoryName;
        private final String agreementName;   // "ECDH" for agreement; null for transport
        private final String kdfOrWrap;       // "HKDF-SHA256" or "OAEP-SHA256"
    }

    /* AEAD content ciphers (symmetric) */
    @AllArgsConstructor @Getter
    enum Aead implements Algorithm {
        AES_GCM_128(1,  "AES/GCM/NoPadding",       16, 12, 128),
        AES_GCM_256(3,  "AES/GCM/NoPadding",       32, 12, 128),
        CHACHA20_POLY1305(24, "ChaCha20-Poly1305", 32, 12, 128);

        private final int coseId;
        private final Kind kind = Kind.AEAD;
        private final String transformation;
        private final int keyBytes;
        private final int nonceBytes;
        private final int tagBits;
    }

}