package dev.everydaythings.graph.identity.vault;

import dev.everydaythings.graph.canonical.HashTree;
import dev.everydaythings.graph.identity.Algorithm;
import dev.everydaythings.graph.identity.MultiKey;
import dev.everydaythings.graph.identity.VarSig;
import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.BindingTarget;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.datum.Frame;
import dev.everydaythings.graph.datum.Record;
import dev.everydaythings.graph.identity.IdentityVocabulary;
import dev.everydaythings.graph.identity.IdentityVocabulary.Delegation;
import dev.everydaythings.graph.identity.IdentityVocabulary.Inception;
import dev.everydaythings.graph.identity.IdentityVocabulary.Revocation;
import dev.everydaythings.graph.identity.IdentityVocabulary.Rotation;
import dev.everydaythings.graph.value.Literal;
import dev.everydaythings.graph.id.CompoundKey;
import dev.everydaythings.graph.id.ContentID;
import dev.everydaythings.graph.id.DatumID;
import dev.everydaythings.graph.id.FrameRef;
import dev.everydaythings.graph.id.ItemID;
import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.identity.IdentityVocabulary.Multikey;
import dev.everydaythings.graph.identity.IdentityVocabulary.Next;
import dev.everydaythings.graph.semantics.CoreVocabulary.Expires;
import dev.everydaythings.graph.semantics.CoreVocabulary.Sequence;
import dev.everydaythings.graph.semantics.ThematicRole;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.EdECPublicKey;
import java.security.spec.EdECPoint;
import java.security.spec.EdECPublicKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.NamedParameterSpec;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Ephemeral vault — keypairs held in process memory, lost on exit.
 *
 * <p>Generates a fresh current + next keypair for the signing purpose at
 * construction. Additional purposes (encryption, key-agreement) will be added
 * in later phases.
 *
 * <p>Each purpose tracks its own state independently: current keypair, next
 * keypair (pre-rotation commitment), KEL chain head, and sequence number.
 * Events (incept/rotate/delegate/revoke) mutate this state and return signed
 * {@link Frame}s as their native output.
 *
 * <p>In-memory vaults are always unlocked — there's no encrypted-at-rest
 * material to gate behind a credential. {@link #lock()} is a no-op.
 *
 * <p>Phase 1 supports Ed25519 only on the signing purpose. When other algorithms
 * or encryption-track keys become relevant, this class will be extended or other
 * Vault implementations will appear alongside it.
 */
public final class InMemoryVault implements Vault {

    /**
     * Per-purpose state: keypairs (current + pre-rotation next) plus KEL position.
     */
    private static final class PurposeState {
        final Algorithm.Sign algorithm;
        KeyPair currentKeyPair;
        KeyPair nextKeyPair;
        DatumID chainHead;     // null = un-incepted
        long sequence;         // 0 before INCEPTION; 1 after; +1 per ROTATION

        PurposeState(Algorithm.Sign algorithm, KeyPair currentKeyPair, KeyPair nextKeyPair) {
            this.algorithm = algorithm;
            this.currentKeyPair = currentKeyPair;
            this.nextKeyPair = nextKeyPair;
            this.chainHead = null;
            this.sequence = 0L;
        }
    }

    private final Map<ItemID, PurposeState> purposes;
    private final ItemID identity;  // stable across rotations — derived from initial signing pubkey

    private InMemoryVault(Map<ItemID, PurposeState> purposes, ItemID identity) {
        this.purposes = purposes;
        this.identity = identity;
    }

    // ==================================================================================
    // Factory
    // ==================================================================================

    /**
     * Generate a fresh InMemoryVault with two newly-generated keypairs (current
     * and pre-rotation next) on the signing purpose, using the given algorithm.
     */
    public static InMemoryVault generate(Algorithm.Sign algorithm) {
        Objects.requireNonNull(algorithm, "algorithm");
        PurposeState signing = new PurposeState(
                algorithm,
                generateKeyPair(algorithm),
                generateKeyPair(algorithm));
        Map<ItemID, PurposeState> purposes = new HashMap<>();
        purposes.put(IdentityVocabulary.Signing.IID, signing);
        ItemID identity = ItemID.fromMultikeyBytes(
                MultiKey.of(algorithm, rawPublicKey(signing.currentKeyPair.getPublic(), algorithm))
                        .encoded());
        return new InMemoryVault(purposes, identity);
    }

    /** Generate using the default signing algorithm (Ed25519). */
    public static InMemoryVault generate() {
        return generate(Algorithm.Sign.ED25519);
    }

    // ==================================================================================
    // Vault interface — identity & state queries
    // ==================================================================================

    @Override
    public ItemID identity() {
        return identity;
    }

    @Override
    public Optional<MultiKey> publicKey(ItemID purpose) {
        PurposeState state = purposes.get(purpose);
        if (state == null) return Optional.empty();
        return Optional.of(currentPublicKey(state));
    }

    @Override
    public Optional<ContentID> nextKeyDigest(ItemID purpose) {
        PurposeState state = purposes.get(purpose);
        if (state == null) return Optional.empty();
        return Optional.of(ContentID.of(nextPublicKey(state).encoded()));
    }

    @Override
    public Optional<DatumID> chainHead(ItemID purpose) {
        PurposeState state = purposes.get(purpose);
        if (state == null || state.chainHead == null) return Optional.empty();
        return Optional.of(state.chainHead);
    }

    @Override
    public long sequence(ItemID purpose) {
        PurposeState state = purposes.get(purpose);
        return state == null ? 0L : state.sequence;
    }

    // ==================================================================================
    // Vault interface — events
    // ==================================================================================

    @Override
    public Frame incept(ItemID purpose) {
        Objects.requireNonNull(purpose, "purpose");
        PurposeState state = requirePurpose(purpose);
        if (state.chainHead != null) {
            throw new IllegalStateException(
                    "Purpose " + purpose + " has already been incepted (sequence=" + state.sequence + ")");
        }
        MultiKey currentKey = currentPublicKey(state);
        ContentID nextDigest = ContentID.of(nextPublicKey(state).encoded());

        List<Binding> bindings = new ArrayList<>();
        bindings.add(Binding.ref(ThematicRole.Theme.IID, identity));
        bindings.add(Binding.ref(ThematicRole.Purpose.IID, purpose));
        bindings.add(new Binding(
                ThematicRole.Instrument.IID,
                List.of(new CompoundKey.Sememe(Multikey.IID)),
                Literal.ofMultiKey(currentKey)));
        bindings.add(new Binding(
                ThematicRole.Instrument.IID,
                List.of(new CompoundKey.Sememe(Next.IID)),
                BindingTarget.ref(nextDigest)));
        bindings.add(new Binding(
                ThematicRole.Time.IID,
                List.of(),
                Literal.ofInstant(Instant.now())));

        Body body = Body.of(ItemRef.of(Inception.IID), bindings);
        VarSig sig = signWith(state.currentKeyPair, state.algorithm, HashTree.signingPayload(body));
        Record record = Record.of(FrameRef.of(body.datumId()), List.of(), sig);

        state.chainHead = body.datumId();
        state.sequence = 1L;

        return Frame.of(body, List.of(record));
    }

    @Override
    public Frame rotate(ItemID purpose) {
        Objects.requireNonNull(purpose, "purpose");
        PurposeState state = requirePurpose(purpose);
        if (state.chainHead == null) {
            throw new IllegalStateException(
                    "Purpose " + purpose + " has not been incepted; cannot rotate");
        }
        KeyPair oldCurrent = state.currentKeyPair;
        KeyPair newCurrent = state.nextKeyPair;
        KeyPair newNext = generateKeyPair(state.algorithm);

        MultiKey newCurrentPublic = MultiKey.of(state.algorithm,
                rawPublicKey(newCurrent.getPublic(), state.algorithm));
        MultiKey newNextPublic = MultiKey.of(state.algorithm,
                rawPublicKey(newNext.getPublic(), state.algorithm));
        ContentID newNextDigest = ContentID.of(newNextPublic.encoded());

        long newSequence = state.sequence + 1L;

        List<Binding> bindings = new ArrayList<>();
        bindings.add(Binding.ref(ThematicRole.Theme.IID, identity));
        bindings.add(Binding.ref(ThematicRole.Purpose.IID, purpose));
        bindings.add(new Binding(
                ThematicRole.Follows.IID,
                List.of(),
                BindingTarget.ref(state.chainHead)));
        bindings.add(new Binding(
                ThematicRole.Attribute.IID,
                List.of(new CompoundKey.Sememe(Sequence.IID)),
                Literal.ofInteger(newSequence)));
        bindings.add(new Binding(
                ThematicRole.Instrument.IID,
                List.of(new CompoundKey.Sememe(Multikey.IID)),
                Literal.ofMultiKey(newCurrentPublic)));
        bindings.add(new Binding(
                ThematicRole.Instrument.IID,
                List.of(new CompoundKey.Sememe(Next.IID)),
                BindingTarget.ref(newNextDigest)));
        bindings.add(new Binding(
                ThematicRole.Time.IID,
                List.of(),
                Literal.ofInstant(Instant.now())));

        Body body = Body.of(ItemRef.of(Rotation.IID), bindings);
        byte[] payload = HashTree.signingPayload(body);
        VarSig sigOld = signWith(oldCurrent, state.algorithm, payload);
        VarSig sigNew = signWith(newCurrent, state.algorithm, payload);

        FrameRef bodyRef = FrameRef.of(body.datumId());
        Record recordOld = Record.of(bodyRef, List.of(), sigOld);
        Record recordNew = Record.of(bodyRef, List.of(), sigNew);

        state.currentKeyPair = newCurrent;
        state.nextKeyPair = newNext;
        state.chainHead = body.datumId();
        state.sequence = newSequence;

        return Frame.of(body, List.of(recordOld, recordNew));
    }

    @Override
    public Frame delegate(ItemID delegateId, ItemID purpose, DelegationConditions conditions) {
        Objects.requireNonNull(delegateId, "delegateId");
        Objects.requireNonNull(purpose, "purpose");
        Objects.requireNonNull(conditions, "conditions");
        // Delegations are signed by the delegator's signing key.
        PurposeState signingState = requirePurpose(IdentityVocabulary.Signing.IID);

        List<Binding> bindings = new ArrayList<>();
        bindings.add(Binding.ref(ThematicRole.Agent.IID, identity));
        bindings.add(Binding.ref(ThematicRole.Theme.IID, delegateId));
        bindings.add(Binding.ref(ThematicRole.Purpose.IID, purpose));
        conditions.expiresAt().ifPresent(expiresAt -> bindings.add(new Binding(
                ThematicRole.Attribute.IID,
                List.of(new CompoundKey.Sememe(Expires.IID)),
                Literal.ofInstant(expiresAt))));
        bindings.add(new Binding(
                ThematicRole.Time.IID,
                List.of(),
                Literal.ofInstant(Instant.now())));

        Body body = Body.of(ItemRef.of(Delegation.IID), bindings);
        VarSig sig = signWith(signingState.currentKeyPair, signingState.algorithm, HashTree.signingPayload(body));
        Record record = Record.of(FrameRef.of(body.datumId()), List.of(), sig);

        return Frame.of(body, List.of(record));
    }

    @Override
    public Frame revoke(BindingTarget target, ItemID reason) {
        Objects.requireNonNull(target, "target");
        // Revocations are signed by the signing key.
        PurposeState signingState = requirePurpose(IdentityVocabulary.Signing.IID);

        List<Binding> bindings = new ArrayList<>();
        bindings.add(new Binding(ThematicRole.Theme.IID, List.of(), target));
        if (reason != null) {
            bindings.add(Binding.ref(ThematicRole.Purpose.IID, reason));
        }
        bindings.add(new Binding(
                ThematicRole.Time.IID,
                List.of(),
                Literal.ofInstant(Instant.now())));

        Body body = Body.of(ItemRef.of(Revocation.IID), bindings);
        VarSig sig = signWith(signingState.currentKeyPair, signingState.algorithm, HashTree.signingPayload(body));
        Record record = Record.of(FrameRef.of(body.datumId()), List.of(), sig);

        return Frame.of(body, List.of(record));
    }

    // ==================================================================================
    // Vault interface — signing
    // ==================================================================================

    @Override
    public VarSig sign(byte[] message) {
        PurposeState state = requirePurpose(IdentityVocabulary.Signing.IID);
        return signWith(state.currentKeyPair, state.algorithm, message);
    }

    @Override
    public VarSig sign(byte[] message, ItemID purpose) {
        PurposeState state = requirePurpose(purpose);
        return signWith(state.currentKeyPair, state.algorithm, message);
    }

    // ==================================================================================
    // Vault interface — legacy signing-track API (delegates to the purposes map)
    // ==================================================================================

    @Override
    public Optional<Algorithm.Sign> signingAlgorithm() {
        PurposeState state = purposes.get(IdentityVocabulary.Signing.IID);
        return state == null ? Optional.empty() : Optional.of(state.algorithm);
    }

    @Override
    public Optional<MultiKey> signingPublicKey() {
        return publicKey(IdentityVocabulary.Signing.IID);
    }

    @Override
    public Optional<ContentID> signingNextKeyDigest() {
        return nextKeyDigest(IdentityVocabulary.Signing.IID);
    }

    // ==================================================================================
    // Internal helpers
    // ==================================================================================

    /** Look up the state for a purpose; throw if absent (vault has no keys for it). */
    private PurposeState requirePurpose(ItemID purpose) {
        PurposeState state = purposes.get(purpose);
        if (state == null) {
            throw new IllegalStateException(
                    "Vault has no keys for purpose " + purpose);
        }
        return state;
    }

    private static MultiKey currentPublicKey(PurposeState state) {
        return MultiKey.of(state.algorithm,
                rawPublicKey(state.currentKeyPair.getPublic(), state.algorithm));
    }

    private static MultiKey nextPublicKey(PurposeState state) {
        return MultiKey.of(state.algorithm,
                rawPublicKey(state.nextKeyPair.getPublic(), state.algorithm));
    }

    /** Sign a message with an explicit keypair (rotation needs both old + new). */
    private static VarSig signWith(KeyPair keyPair, Algorithm.Sign algorithm, byte[] message) {
        try {
            Signature sig = Signature.getInstance(algorithm.signatureName());
            sig.initSign(keyPair.getPrivate());
            sig.update(message);
            return VarSig.of(algorithm, sig.sign());
        } catch (Exception e) {
            throw new RuntimeException("Signing failed", e);
        }
    }

    // ==================================================================================
    // Algorithm-specific helpers (Ed25519 only for Phase 1)
    // ==================================================================================

    private static KeyPair generateKeyPair(Algorithm.Sign algorithm) {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance(algorithm.keyGeneratorName());
            return gen.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Unsupported algorithm: " + algorithm, e);
        }
    }

    private static byte[] rawPublicKey(PublicKey pub, Algorithm.Sign algorithm) {
        if (algorithm != Algorithm.Sign.ED25519) {
            throw new UnsupportedOperationException(
                    "Raw public key extraction not implemented for " + algorithm);
        }
        if (!(pub instanceof EdECPublicKey edPub)) {
            throw new IllegalStateException("Expected Ed25519 public key, got " + pub.getClass());
        }
        EdECPoint point = edPub.getPoint();
        byte[] yBE = point.getY().toByteArray();
        byte[] raw = new byte[32];
        int copyLen = Math.min(yBE.length, 32);
        for (int i = 0; i < copyLen; i++) {
            raw[i] = yBE[yBE.length - 1 - i];
        }
        if (point.isXOdd()) {
            raw[31] |= (byte) 0x80;
        }
        return raw;
    }

    /**
     * Decode an Ed25519 public key from the raw 32-byte little-endian-y-with-x-bit form.
     * Static helper used by Vault implementations and verification flows.
     */
    public static PublicKey publicKeyFromRaw(byte[] raw, Algorithm.Sign algorithm) {
        if (algorithm != Algorithm.Sign.ED25519) {
            throw new UnsupportedOperationException(
                    "Raw public key decoding not implemented for " + algorithm);
        }
        if (raw.length != 32) {
            throw new IllegalArgumentException("Ed25519 raw key must be 32 bytes, got " + raw.length);
        }
        boolean xOdd = (raw[31] & 0x80) != 0;
        byte[] yLE = raw.clone();
        yLE[31] &= 0x7F;
        byte[] yBE = new byte[32];
        for (int i = 0; i < 32; i++) {
            yBE[i] = yLE[31 - i];
        }
        BigInteger y = new BigInteger(1, yBE);
        try {
            EdECPoint point = new EdECPoint(xOdd, y);
            EdECPublicKeySpec spec = new EdECPublicKeySpec(NamedParameterSpec.ED25519, point);
            KeyFactory kf = KeyFactory.getInstance("Ed25519");
            return kf.generatePublic(spec);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new RuntimeException("Failed to decode Ed25519 public key", e);
        }
    }
}
