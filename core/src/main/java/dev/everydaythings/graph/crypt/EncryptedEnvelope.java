package dev.everydaythings.graph.crypt;

import dev.everydaythings.graph.item.id.HashID;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.trust.Signing;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/** CG envelope for encrypting content/relations to one or more recipients. */
@Getter @Builder
@Canonical.Canonization(classType = Canonical.ClassCollectionType.MAP)
public final class EncryptedEnvelope implements Canonical, Signing.Target {
    /* ----- who & how (KEM / transport) ----- */
    @Canon(order=1) private final int  kemAlg;              // COSE id (e.g., -25 ECDH-ES+HKDF-256, -41 RSA-OAEP-256)
    @Canon(order=2) private final int  aeadAlg;             // COSE id (e.g., 1 AES-GCM-128, 3 AES-GCM-256, 24 ChaCha20-Poly1305)
    @Canon(order=3) private final List<Recipient> recipients;

    /* ----- payload (AEAD) ----- */
    @Canon(order=4) private final byte[] nonce12;           // 12B nonce (GCM/ChaCha20)
    @Canon(order=5) private final byte[] aad;               // optional associated data (may be null/empty)
    @Canon(order=6) private final byte[] ciphertext;        // AEAD output (ciphertext || tag)

    /* ----- sender authenticity (detached) ----- */
    @Canon(order=7) private final long   senderAlg;         // COSE SignAlg
    @Canon(order=8) private final byte[] senderKid;         // sha256(SPKI) of sender’s SIGN key
    @Canon(order=9) private final byte[] senderSig;         // signature over bodyToSign()

    /* Tie to an item (optional but recommended) */
    @Canon(order=10) private final byte[] subjectIid;       // the item this envelope belongs to (for policy/audit)

    /** Each recipient gets a way to recover the CEK: ECDH-ES (with ephemeral pub) or RSA-OAEP wrap. */
    @Getter @Builder
    @Canonical.Canonization(classType = Canonical.ClassCollectionType.MAP)
    public static final class Recipient implements Canonical {
        @Canon(order=1) private final byte[] kid;             // recipient GraphPublicKey.kid (selects key)
        @Canon(order=2) private final byte[] epkSpki;         // sender’s ephemeral SPKI (ECDH-ES). null for RSA-OAEP.
        @Canon(order=3) private final byte[] wrappedCek;      // RSA-OAEP (or AES-KW) wrapped CEK; null for ECDH-ES direct
    }

    /* ===== Signing.Target ===== */
    @Override public HashID targetId() {
        // if you want to bind to the item, return its IID here; otherwise use a synthetic "envelope" IID
        return new ItemID(subjectIid);
    }
}
