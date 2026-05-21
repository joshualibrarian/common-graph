package dev.everydaythings.graph.cryptography.vault;

import dev.everydaythings.graph.cryptography.algorithm.KeyAgreement;
import dev.everydaythings.graph.cryptography.algorithm.Signing;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.EdECPrivateKey;
import java.security.interfaces.XECPrivateKey;
import java.security.spec.EdECPrivateKeySpec;
import java.security.spec.NamedParameterSpec;
import java.security.spec.XECPrivateKeySpec;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * JwkSerializer — bidirectional conversion between Java {@link KeyPair}
 * instances and JWK (JSON Web Key, RFC 7517) maps for the algorithms CG
 * uses: Ed25519 (signing) and X25519 (key agreement).
 *
 * <p>A JWK here is a {@code Map<String, Object>} ready for Jackson to
 * serialize.  Keys follow RFC 8037 (OKP curve representations) and RFC
 * 7518 (algorithm identifiers).
 *
 * <p>Encoding:
 * <pre>
 * Ed25519 keypair → { kty: "OKP", crv: "Ed25519",
 *                     d: base64url(32-byte seed),
 *                     x: base64url(32-byte raw public) }
 *
 * X25519 keypair  → { kty: "OKP", crv: "X25519",
 *                     d: base64url(32-byte scalar),
 *                     x: base64url(32-byte raw public) }
 * </pre>
 *
 * <p>Raw public-key bytes use each algorithm's existing extraction:
 * {@link Signing#publicKeyToRaw(PublicKey)} and
 * {@link KeyAgreement#publicKeyToRaw(PublicKey)}.  Raw private bytes use
 * Java's built-in {@link EdECPrivateKey#getBytes()} and
 * {@link XECPrivateKey#getScalar()}.
 *
 * <p>Round-trip contract: {@code fromJwk(toJwk(kp))} yields a {@link KeyPair}
 * whose public bytes and private bytes match the original exactly.
 */
public final class JwkSerializer {

    private static final Base64.Encoder URL_NO_PAD = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();

    private static final String KTY  = "kty";
    private static final String CRV  = "crv";
    private static final String D    = "d";
    private static final String X    = "x";

    private static final String OKP            = "OKP";
    private static final String CRV_ED25519    = "Ed25519";
    private static final String CRV_X25519     = "X25519";

    private JwkSerializer() {}

    // ==================================================================================
    // Encoding — KeyPair → JWK map
    // ==================================================================================

    /**
     * Encode an Ed25519 keypair as a JWK map.  The map carries only the
     * standard JWK fields; callers add their own extension fields (e.g.,
     * {@code kid}, {@code cg:purpose}) after the fact.
     */
    public static Map<String, Object> encodeEd25519(KeyPair keyPair) {
        Objects.requireNonNull(keyPair, "keyPair");
        byte[] privateBytes = ed25519PrivateBytes(keyPair.getPrivate());
        byte[] publicBytes  = Signing.Ed25519.builtin().publicKeyToRaw(keyPair.getPublic());
        Map<String, Object> jwk = new LinkedHashMap<>();
        jwk.put(KTY, OKP);
        jwk.put(CRV, CRV_ED25519);
        jwk.put(D,   URL_NO_PAD.encodeToString(privateBytes));
        jwk.put(X,   URL_NO_PAD.encodeToString(publicBytes));
        return jwk;
    }

    /**
     * Encode an X25519 keypair as a JWK map.  See {@link #encodeEd25519}
     * for the field shape.
     */
    public static Map<String, Object> encodeX25519(KeyPair keyPair) {
        Objects.requireNonNull(keyPair, "keyPair");
        byte[] privateBytes = x25519PrivateBytes(keyPair.getPrivate());
        byte[] publicBytes  = KeyAgreement.X25519.builtin().publicKeyToRaw(keyPair.getPublic());
        Map<String, Object> jwk = new LinkedHashMap<>();
        jwk.put(KTY, OKP);
        jwk.put(CRV, CRV_X25519);
        jwk.put(D,   URL_NO_PAD.encodeToString(privateBytes));
        jwk.put(X,   URL_NO_PAD.encodeToString(publicBytes));
        return jwk;
    }

    // ==================================================================================
    // Decoding — JWK map → KeyPair
    // ==================================================================================

    /**
     * Decode a JWK map back to a {@link KeyPair}.  Dispatches on
     * {@code kty} and {@code crv}.  Unknown combinations throw.
     */
    public static KeyPair decode(Map<String, ?> jwk) {
        Objects.requireNonNull(jwk, "jwk");
        String kty = stringField(jwk, KTY);
        String crv = stringField(jwk, CRV);
        if (!OKP.equals(kty)) {
            throw new IllegalArgumentException(
                    "Unsupported JWK kty=" + kty + " (only OKP supported)");
        }
        return switch (crv) {
            case CRV_ED25519 -> decodeEd25519(jwk);
            case CRV_X25519  -> decodeX25519(jwk);
            default -> throw new IllegalArgumentException(
                    "Unsupported JWK crv=" + crv + " (expected Ed25519 or X25519)");
        };
    }

    private static KeyPair decodeEd25519(Map<String, ?> jwk) {
        byte[] privateBytes = URL_DECODER.decode(stringField(jwk, D));
        byte[] publicBytes  = URL_DECODER.decode(stringField(jwk, X));
        try {
            KeyFactory factory = KeyFactory.getInstance("Ed25519");
            EdECPrivateKeySpec privSpec = new EdECPrivateKeySpec(NamedParameterSpec.ED25519, privateBytes);
            PrivateKey priv = factory.generatePrivate(privSpec);
            PublicKey pub   = Signing.Ed25519.builtin().decodePublicKey(publicBytes);
            return new KeyPair(pub, priv);
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("Failed to decode Ed25519 JWK", e);
        }
    }

    private static KeyPair decodeX25519(Map<String, ?> jwk) {
        byte[] privateBytes = URL_DECODER.decode(stringField(jwk, D));
        byte[] publicBytes  = URL_DECODER.decode(stringField(jwk, X));
        try {
            KeyFactory factory = KeyFactory.getInstance("X25519");
            XECPrivateKeySpec privSpec = new XECPrivateKeySpec(NamedParameterSpec.X25519, privateBytes);
            PrivateKey priv = factory.generatePrivate(privSpec);
            PublicKey pub   = KeyAgreement.X25519.builtin().decodePublicKey(publicBytes);
            return new KeyPair(pub, priv);
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("Failed to decode X25519 JWK", e);
        }
    }

    // ==================================================================================
    // Helpers
    // ==================================================================================

    private static byte[] ed25519PrivateBytes(PrivateKey priv) {
        if (!(priv instanceof EdECPrivateKey ed)) {
            throw new IllegalArgumentException(
                    "Expected EdECPrivateKey, got " + priv.getClass().getName());
        }
        return ed.getBytes().orElseThrow(() ->
                new IllegalArgumentException("Ed25519 private key has no raw seed bytes available"));
    }

    private static byte[] x25519PrivateBytes(PrivateKey priv) {
        if (!(priv instanceof XECPrivateKey xec)) {
            throw new IllegalArgumentException(
                    "Expected XECPrivateKey, got " + priv.getClass().getName());
        }
        return xec.getScalar().orElseThrow(() ->
                new IllegalArgumentException("X25519 private key has no raw scalar bytes available"));
    }

    private static String stringField(Map<String, ?> jwk, String key) {
        Object value = jwk.get(key);
        if (!(value instanceof String s)) {
            throw new IllegalArgumentException(
                    "JWK missing or non-string field '" + key + "': " + value);
        }
        return s;
    }
}
