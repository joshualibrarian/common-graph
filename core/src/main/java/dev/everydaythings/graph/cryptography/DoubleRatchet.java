package dev.everydaythings.graph.cryptography;

import dev.everydaythings.graph.canonical.HashTree;
import io.ipfs.multihash.Multihash;
import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.cryptography.EncryptionVocabulary.DoubleRatchetV1;
import dev.everydaythings.graph.cryptography.EncryptionVocabulary.InitiatorEphemeralKey;
import dev.everydaythings.graph.cryptography.EncryptionVocabulary.InitiatorIdentityKey;
import dev.everydaythings.graph.cryptography.EncryptionVocabulary.MessageNumber;
import dev.everydaythings.graph.cryptography.EncryptionVocabulary.Method;
import dev.everydaythings.graph.cryptography.EncryptionVocabulary.PreviousChainLength;
import dev.everydaythings.graph.cryptography.EncryptionVocabulary.SenderRatchetKey;
import dev.everydaythings.graph.cryptography.RecordVocabulary.Act;
import dev.everydaythings.graph.cryptography.RecordVocabulary.Encrypted;
import dev.everydaythings.graph.cryptography.algorithm.Aead;
import dev.everydaythings.graph.cryptography.algorithm.Kdf;
import dev.everydaythings.graph.cryptography.algorithm.KeyAgreement;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Double Ratchet session state machine (Signal Double Ratchet).
 *
 * <p>Encrypts and decrypts a stream of messages between two parties with
 * forward secrecy and post-compromise security.  Each side holds one
 * instance per peer.  State evolves on every send and every receive.
 *
 * <p>Cryptographic primitives: X25519 (DH ratchet), AES-GCM-256 (AEAD),
 * HKDF-SHA-256 (root-chain KDF), HMAC-SHA-256 (symmetric-chain step).
 *
 * <p>The root key seed (32 bytes) comes from {@link X3dh}.  See
 * {@link #initInitiator} and {@link #initResponder} for the two entry
 * points.
 *
 * <h2>Wire format: records, not inline bytes</h2>
 *
 * <p>Each {@link #encrypt} call returns an {@link EncryptedMessage} carrying
 * the AEAD ciphertext plus the binding list to put on the record that
 * accompanies the encrypted body.  Bindings declare ACT=Encrypted,
 * METHOD=DoubleRatchetV1, the sender's DH ratchet pubkey, previous-chain
 * length, message number, and (for the first message of a new session) the
 * initiator's identity and ephemeral pubkeys for the recipient's X3DH
 * responder path.
 *
 * <p>The AEAD's associated-data is the canonical hash of the full binding
 * list.  Any tampering with bindings breaks the hash, breaks the AEAD tag,
 * and fails decryption.  Recipients recompute the same hash from the
 * bindings they receive.
 */
public final class DoubleRatchet {

    static final int DH_LEN = 32;
    static final int KEY_LEN = 32;
    private static final int MAX_SKIP_PER_CHAIN = 1000;
    private static final int MAX_SKIPPED_TOTAL = 2000;
    private static final byte[] CHAIN_STEP_MK = { 0x01 };
    private static final byte[] CHAIN_STEP_CK = { 0x02 };
    private static final byte[] HKDF_INFO_RK  = "cg.dr.rk".getBytes();

    private static final KeyAgreement.X25519 X25519 = KeyAgreement.X25519.builtin();
    private static final Aead.AesGcm256       AEAD   = Aead.AesGcm256.builtin();
    private static final Kdf.HkdfSha256       HKDF   = Kdf.HkdfSha256.builtin();

    // Pre-computed IIDs used in every bindings list.
    private static final ItemRef ACT_ROLE          = ItemRef.iid(Act.KEY);
    private static final ItemRef ENCRYPTED_VALUE   = ItemRef.iid(Encrypted.KEY);
    private static final ItemRef METHOD_ROLE       = ItemRef.iid(Method.KEY);
    private static final ItemRef DR_V1_VALUE       = ItemRef.iid(DoubleRatchetV1.KEY);
    private static final ItemRef SENDER_RATCHET    = ItemRef.iid(SenderRatchetKey.KEY);
    private static final ItemRef PREVIOUS_CHAIN    = ItemRef.iid(PreviousChainLength.KEY);
    private static final ItemRef MESSAGE_NUMBER    = ItemRef.iid(MessageNumber.KEY);
    private static final ItemRef INITIATOR_IK      = ItemRef.iid(InitiatorIdentityKey.KEY);
    private static final ItemRef INITIATOR_EK      = ItemRef.iid(InitiatorEphemeralKey.KEY);

    // -- State --

    private KeyPair dhs;        // our current DH ratchet keypair
    private PublicKey dhr;      // peer's current DH ratchet pubkey
    private byte[] rk;          // root key
    private byte[] cks;         // send chain key
    private byte[] ckr;         // recv chain key
    private long ns;            // send counter (within current send chain)
    private long nr;            // recv counter (within current recv chain)
    private long pn;            // length of previous send chain
    private final Map<SkipKey, byte[]> mkSkipped = new HashMap<>();

    /**
     * Bindings included on every outgoing message until the recipient has
     * been heard from at least once.  Initiator-side X3DH bootstrap info
     * (INITIATOR_IDENTITY_KEY + INITIATOR_EPHEMERAL_KEY) lives here; the
     * responder's instance has this empty.  Cleared when the first receive
     * chain establishes (the peer has provably processed our X3DH).
     */
    private List<Binding> pendingBootstrap = List.of();

    private DoubleRatchet() {}

    /**
     * Initialize as the session initiator (the sender of the first message).
     *
     * @param sharedSecret      32-byte SK from {@link X3dh#initiator}
     * @param peerSpkPubRaw     the responder's signed pre-key (raw X25519 bytes)
     * @param bootstrapBindings bindings to include on outgoing messages until
     *                          the recipient has been heard from (typically
     *                          INITIATOR_IDENTITY_KEY + INITIATOR_EPHEMERAL_KEY
     *                          so the recipient can run X3DH responder)
     */
    public static DoubleRatchet initInitiator(byte[] sharedSecret, byte[] peerSpkPubRaw,
                                              List<Binding> bootstrapBindings) {
        DoubleRatchet d = new DoubleRatchet();
        d.rk = sharedSecret.clone();
        d.dhs = X25519.generateKeyPair();
        d.dhr = X25519.decodePublicKey(peerSpkPubRaw.clone());
        byte[][] kdf = kdfRk(d.rk, X25519.agree(d.dhs.getPrivate(), d.dhr));
        d.rk  = kdf[0];
        d.cks = kdf[1];
        d.ckr = null;
        d.ns = 0; d.nr = 0; d.pn = 0;
        d.pendingBootstrap = List.copyOf(bootstrapBindings);
        return d;
    }

    /**
     * Initialize as the session responder.  The responder's keypair is
     * their published signed pre-key.
     */
    public static DoubleRatchet initResponder(byte[] sharedSecret, KeyPair spk) {
        DoubleRatchet d = new DoubleRatchet();
        d.rk = sharedSecret.clone();
        d.dhs = spk;
        d.dhr = null;
        d.cks = null;
        d.ckr = null;
        d.ns = 0; d.nr = 0; d.pn = 0;
        return d;
    }

    // ==================================================================================
    // Public encrypt / decrypt
    // ==================================================================================

    /**
     * Result of encrypting one message: the raw AEAD ciphertext (to live in
     * the Opaque.Encrypted body) plus the binding list to put on the
     * accompanying record.
     */
    public record EncryptedMessage(byte[] ciphertext, List<Binding> recordBindings) {}

    /**
     * Encrypt {@code plaintext} for the peer.  Advances the send chain.
     *
     * @param extraBindings additional bindings to include on the record
     *                      (and authenticate via AEAD AD).  Empty for
     *                      continuation messages; carries
     *                      INITIATOR_IDENTITY/EPHEMERAL on bootstrap
     *                      messages (caller-provided).
     */
    public EncryptedMessage encrypt(byte[] plaintext, List<Binding> extraBindings) {
        if (cks == null) {
            throw new IllegalStateException(
                    "no send chain (must receive at least one message first)");
        }
        byte[] senderPub = X25519.publicKeyToRaw(dhs.getPublic());
        long messageN = ns;
        long previousN = pn;

        // Include bootstrap bindings (INITIATOR_*) on every outgoing message
        // until the peer has been heard from.  Once we receive their reply,
        // they've proven they processed our X3DH and we drop the bootstrap.
        List<Binding> outgoingExtras = pendingBootstrap.isEmpty() ? extraBindings : merge(pendingBootstrap, extraBindings);
        List<Binding> bindings = buildBindings(senderPub, previousN, messageN, outgoingExtras);
        byte[] aad = adFromBindings(bindings);

        byte[] mk = chainStep(true);
        ns++;
        byte[] ciphertext = AEAD.encrypt(mk, zeroNonce(), aad, plaintext);
        return new EncryptedMessage(ciphertext, bindings);
    }

    private static List<Binding> merge(List<Binding> a, List<Binding> b) {
        if (a.isEmpty()) return b;
        if (b.isEmpty()) return a;
        List<Binding> out = new ArrayList<>(a.size() + b.size());
        out.addAll(a);
        out.addAll(b);
        return out;
    }

    /**
     * Decrypt a message from the peer.  Handles DH ratchet steps and
     * out-of-order delivery via the skipped-keys cache.
     */
    public byte[] decrypt(EncryptedMessage message) {
        return decrypt(message.ciphertext(), message.recordBindings());
    }

    /** Decrypt with bindings and ciphertext split (e.g., reconstructed from a Record + Body). */
    public byte[] decrypt(byte[] ciphertext, List<Binding> bindings) {
        DrHeader header = parseHeader(bindings);
        byte[] aad = adFromBindings(bindings);

        // Out-of-order: a previously-skipped message in an earlier chain.
        byte[] skipped = trySkipped(header.senderPub(), header.messageN());
        if (skipped != null) {
            return AEAD.decrypt(skipped, zeroNonce(), aad, ciphertext);
        }

        // New DH from peer triggers a ratchet step.
        if (dhr == null || !Arrays.equals(header.senderPub(), X25519.publicKeyToRaw(dhr))) {
            skipMessageKeys(header.previousN());
            dhRatchet(header.senderPub());
        }

        skipMessageKeys(header.messageN());
        byte[] mk = chainStep(false);
        nr++;
        return AEAD.decrypt(mk, zeroNonce(), aad, ciphertext);
    }

    // ==================================================================================
    // Bindings construction / parsing
    // ==================================================================================

    private static List<Binding> buildBindings(byte[] senderPub, long pn, long ns,
                                               List<Binding> extras) {
        List<Binding> out = new ArrayList<>(6 + extras.size());
        out.add(Binding.ref(ACT_ROLE, ENCRYPTED_VALUE));
        out.add(Binding.ref(METHOD_ROLE, DR_V1_VALUE));
        out.add(new Binding(SENDER_RATCHET, senderPub));
        out.add(new Binding(PREVIOUS_CHAIN, pn));
        out.add(new Binding(MESSAGE_NUMBER, ns));
        out.addAll(extras);
        // Canonical ordering up front so the bindings on the wire match the
        // order the receiver will see after a Record passes through Datum's
        // canonical sort.  AD is hashed from this ordering on both sides.
        out.sort(dev.everydaythings.graph.canonical.HashTree.CANONICAL);
        return out;
    }

    /** Compute AD = canonical hash of the binding list. */
    private static byte[] adFromBindings(List<Binding> bindings) {
        return HashTree.hashOf(bindings, Multihash.Type.sha2_256);
    }

    private record DrHeader(byte[] senderPub, long previousN, long messageN) {}

    private static DrHeader parseHeader(List<Binding> bindings) {
        byte[] senderPub = null;
        Long previousN = null;
        Long messageN = null;
        for (Binding b : bindings) {
            ItemRef role = roleOf(b);
            if (role == null) continue;
            if (SENDER_RATCHET.equals(role) && b.target() instanceof byte[] bytes) {
                senderPub = bytes;
            } else if (PREVIOUS_CHAIN.equals(role) && b.target() instanceof Long n) {
                previousN = n;
            } else if (MESSAGE_NUMBER.equals(role) && b.target() instanceof Long n) {
                messageN = n;
            }
        }
        if (senderPub == null || previousN == null || messageN == null) {
            throw new IllegalArgumentException(
                    "DR record missing required bindings (sender-ratchet-key, previous-chain-length, message-number)");
        }
        return new DrHeader(senderPub, previousN, messageN);
    }

    private static ItemRef roleOf(Binding b) {
        Object roleNode = b.role();
        return roleNode instanceof ItemRef ir ? ir : null;
    }

    // ==================================================================================
    // Internals
    // ==================================================================================

    private void dhRatchet(byte[] peerDhRaw) {
        pn = ns;
        ns = 0;
        nr = 0;
        dhr = X25519.decodePublicKey(peerDhRaw.clone());
        byte[][] kdf1 = kdfRk(rk, X25519.agree(dhs.getPrivate(), dhr));
        rk  = kdf1[0];
        ckr = kdf1[1];
        dhs = X25519.generateKeyPair();
        byte[][] kdf2 = kdfRk(rk, X25519.agree(dhs.getPrivate(), dhr));
        rk  = kdf2[0];
        cks = kdf2[1];
        // Peer has responded — they've processed our X3DH.  Stop including
        // bootstrap bindings on outgoing messages.
        pendingBootstrap = List.of();
    }

    private void skipMessageKeys(long until) {
        if (ckr == null) return;
        if (until - nr > MAX_SKIP_PER_CHAIN) {
            throw new IllegalStateException("too many messages to skip in one chain");
        }
        byte[] peerDhRaw = X25519.publicKeyToRaw(dhr);
        while (nr < until) {
            byte[] mk = chainStep(false);
            if (mkSkipped.size() >= MAX_SKIPPED_TOTAL) {
                throw new IllegalStateException("skipped-message-key cache exhausted");
            }
            mkSkipped.put(new SkipKey(peerDhRaw, nr), mk);
            nr++;
        }
    }

    private byte[] trySkipped(byte[] peerDhRaw, long n) {
        return mkSkipped.remove(new SkipKey(peerDhRaw, n));
    }

    private byte[] chainStep(boolean sending) {
        byte[] chain = sending ? cks : ckr;
        byte[] mk     = hmacSha256(chain, CHAIN_STEP_MK);
        byte[] nextCk = hmacSha256(chain, CHAIN_STEP_CK);
        if (sending) cks = nextCk; else ckr = nextCk;
        return mk;
    }

    private static byte[][] kdfRk(byte[] rk, byte[] dhOut) {
        byte[] out = HKDF.derive(dhOut, rk, HKDF_INFO_RK, 2 * KEY_LEN);
        return new byte[][] {
                Arrays.copyOfRange(out, 0, KEY_LEN),
                Arrays.copyOfRange(out, KEY_LEN, 2 * KEY_LEN)
        };
    }

    // Per-MK nonces are unique by construction, so AEAD nonce is a fixed
    // zero block (standard Signal convention for Double Ratchet).
    private static byte[] zeroNonce() {
        return new byte[12];
    }

    private static byte[] hmacSha256(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new RuntimeException("HMAC-SHA-256 failed", e);
        }
    }

    private record SkipKey(byte[] dhPub, long n) {
        @Override public boolean equals(Object o) {
            return o instanceof SkipKey s && Arrays.equals(dhPub, s.dhPub) && n == s.n;
        }
        @Override public int hashCode() {
            return 31 * Arrays.hashCode(dhPub) + Long.hashCode(n);
        }
    }

    // ==================================================================================
    // Accessors
    // ==================================================================================

    /** Whether this session has established a send chain (can encrypt). */
    public boolean canSend() { return cks != null; }

    /** Whether this session has established a recv chain (has received ≥ 1 message). */
    public boolean canReceive() { return ckr != null; }
}
