package dev.everydaythings.graph.identity.vault;


import dev.everydaythings.graph.canonical.HashTree;
import dev.everydaythings.graph.identity.AlgorithmHandle;
import dev.everydaythings.graph.identity.AlgorithmVocabulary;
import dev.everydaythings.graph.identity.JcaAlgorithmHandle;
import dev.everydaythings.graph.identity.MultiKey;
import dev.everydaythings.graph.identity.VarSig;
import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.datum.Frame;
import dev.everydaythings.graph.datum.Record;
import dev.everydaythings.graph.identity.IdentityVocabulary;
import dev.everydaythings.graph.identity.IdentityVocabulary.Delegation;
import dev.everydaythings.graph.identity.IdentityVocabulary.Inception;
import dev.everydaythings.graph.identity.IdentityVocabulary.Revocation;
import dev.everydaythings.graph.identity.IdentityVocabulary.Rotation;
import dev.everydaythings.graph.id.CompoundKey;
import dev.everydaythings.graph.id.ContentRef;
import dev.everydaythings.graph.id.DatumRef;
import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.identity.IdentityVocabulary.Multikey;
import dev.everydaythings.graph.identity.IdentityVocabulary.Next;
import dev.everydaythings.graph.CoreVocabulary.Expires;
import dev.everydaythings.graph.CoreVocabulary.Sequence;
import dev.everydaythings.graph.language.ThematicRole;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.EdECPublicKey;
import java.security.spec.EdECPoint;
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
 * construction.  Additional purposes (encryption, key-agreement) will be added
 * in later phases.
 *
 * <p>Each purpose tracks its own state independently: current keypair, next
 * keypair (pre-rotation commitment), KEL chain head, and sequence number.
 * Events (incept/rotate/delegate/revoke) mutate this state and return signed
 * {@link Frame}s as their native output.
 *
 * <p>In-memory vaults are always unlocked — there's no encrypted-at-rest
 * material to gate behind a credential.  {@link #lock()} is a no-op.
 *
 * <p>Phase 1 supports Ed25519 only.  The JCA hooks and raw-key encoding paths
 * are hard-coded against the {@link AlgorithmVocabulary.Ed25519} sememe; when
 * other algorithms or encryption-track keys become relevant, this class will
 * be extended or other {@link Vault} implementations will appear alongside it.
 */
public final class InMemoryVault implements Vault {

    /** Sememe IID for the Ed25519 algorithm — the only algorithm this vault supports. */
    private static final ItemRef ED25519 = ItemRef.iid(AlgorithmVocabulary.Ed25519.KEY);

    /**
     * Static Ed25519 handle used for VarSig / MultiKey construction.  Built off
     * the {@link AlgorithmVocabulary.Ed25519} constants, so it works without a
     * librarian present — important for tests and bare {@code Signer.inMemory()}
     * usage.
     */
    private static final AlgorithmHandle ED25519_HANDLE = JcaAlgorithmHandle.ofEd25519();

    /**
     * Per-purpose state: keypairs (current + pre-rotation next) plus KEL position.
     */
    private static final class PurposeState {
        final ItemRef algorithm;
        KeyPair currentKeyPair;
        KeyPair nextKeyPair;
        DatumRef chainHead;     // null = un-incepted
        long sequence;         // 0 before INCEPTION; 1 after; +1 per ROTATION

        PurposeState(ItemRef algorithm, KeyPair currentKeyPair, KeyPair nextKeyPair) {
            this.algorithm = algorithm;
            this.currentKeyPair = currentKeyPair;
            this.nextKeyPair = nextKeyPair;
            this.chainHead = null;
            this.sequence = 0L;
        }
    }

    private final Map<ItemRef, PurposeState> purposes;
    private final ItemRef identity;  // stable across rotations — derived from initial signing pubkey

    private InMemoryVault(Map<ItemRef, PurposeState> purposes, ItemRef identity) {
        this.purposes = purposes;
        this.identity = identity;
    }

    // ==================================================================================
    // Factory
    // ==================================================================================

    /**
     * Generate a fresh InMemoryVault with two newly-generated Ed25519 keypairs
     * (current and pre-rotation next) on the signing purpose.
     *
     * <p>InMemoryVault is currently Ed25519-only.  When other algorithms come
     * online they will arrive in dedicated vault implementations rather than
     * by parameterizing this factory.
     */
    public static InMemoryVault generate() {
        KeyPair current = generateEd25519KeyPair();
        KeyPair next = generateEd25519KeyPair();
        PurposeState signing = new PurposeState(ED25519, current, next);
        Map<ItemRef, PurposeState> purposes = new HashMap<>();
        purposes.put(ItemRef.iid(IdentityVocabulary.Signing.KEY), signing);
        ItemRef identity = ItemRef.fromMultikeyBytes(
                MultiKey.of(ED25519_HANDLE, rawEd25519PublicKey(current.getPublic()))
                        .encoded());
        return new InMemoryVault(purposes, identity);
    }

    // ==================================================================================
    // Vault interface — identity & state queries
    // ==================================================================================

    @Override
    public ItemRef identity() {
        return identity;
    }

    @Override
    public Optional<MultiKey> publicKey(ItemRef purpose) {
        PurposeState state = purposes.get(purpose);
        if (state == null) return Optional.empty();
        return Optional.of(currentPublicKey(state));
    }

    @Override
    public Optional<ContentRef> nextKeyDigest(ItemRef purpose) {
        PurposeState state = purposes.get(purpose);
        if (state == null) return Optional.empty();
        return Optional.of(ContentRef.of(nextPublicKey(state).encoded()));
    }

    @Override
    public Optional<DatumRef> chainHead(ItemRef purpose) {
        PurposeState state = purposes.get(purpose);
        if (state == null || state.chainHead == null) return Optional.empty();
        return Optional.of(state.chainHead);
    }

    @Override
    public long sequence(ItemRef purpose) {
        PurposeState state = purposes.get(purpose);
        return state == null ? 0L : state.sequence;
    }

    // ==================================================================================
    // Vault interface — events
    // ==================================================================================

    @Override
    public Frame incept(ItemRef purpose) {
        Objects.requireNonNull(purpose, "purpose");
        PurposeState state = requirePurpose(purpose);
        if (state.chainHead != null) {
            throw new IllegalStateException(
                    "Purpose " + purpose + " has already been incepted (sequence=" + state.sequence + ")");
        }
        MultiKey currentKey = currentPublicKey(state);
        ContentRef nextDigest = ContentRef.of(nextPublicKey(state).encoded());

        List<Binding> bindings = new ArrayList<>();
        bindings.add(Binding.ref(ItemRef.iid(ThematicRole.Theme.KEY), identity));
        bindings.add(Binding.ref(ItemRef.iid(ThematicRole.Purpose.KEY), purpose));
        bindings.add(new Binding(
                ItemRef.iid(ThematicRole.Instrument.KEY),
                List.of(new CompoundKey.Sememe(ItemRef.iid(Multikey.KEY))),
                currentKey.encoded()));
        bindings.add(new Binding(
                ItemRef.iid(ThematicRole.Instrument.KEY),
                List.of(new CompoundKey.Sememe(ItemRef.iid(Next.KEY))),
                nextDigest));
        bindings.add(new Binding(
                ItemRef.iid(ThematicRole.Time.KEY),
                List.of(),
                Instant.now()));

        Body body = Body.of(ItemRef.of(ItemRef.iid(Inception.KEY)), bindings);
        VarSig sig = signWith(state.currentKeyPair, HashTree.signingPayload(body));
        Record record = Record.of(DatumRef.of(body.datumId()), List.of(), sig);

        state.chainHead = body.datumId();
        state.sequence = 1L;

        return Frame.of(body, List.of(record));
    }

    @Override
    public Frame rotate(ItemRef purpose) {
        Objects.requireNonNull(purpose, "purpose");
        PurposeState state = requirePurpose(purpose);
        if (state.chainHead == null) {
            throw new IllegalStateException(
                    "Purpose " + purpose + " has not been incepted; cannot rotate");
        }
        KeyPair oldCurrent = state.currentKeyPair;
        KeyPair newCurrent = state.nextKeyPair;
        KeyPair newNext = generateEd25519KeyPair();

        MultiKey newCurrentPublic = MultiKey.of(ED25519_HANDLE,
                rawEd25519PublicKey(newCurrent.getPublic()));
        MultiKey newNextPublic = MultiKey.of(ED25519_HANDLE,
                rawEd25519PublicKey(newNext.getPublic()));
        ContentRef newNextDigest = ContentRef.of(newNextPublic.encoded());

        long newSequence = state.sequence + 1L;

        List<Binding> bindings = new ArrayList<>();
        bindings.add(Binding.ref(ItemRef.iid(ThematicRole.Theme.KEY), identity));
        bindings.add(Binding.ref(ItemRef.iid(ThematicRole.Purpose.KEY), purpose));
        bindings.add(new Binding(
                ItemRef.iid(ThematicRole.Follows.KEY),
                List.of(),
                state.chainHead));
        bindings.add(new Binding(
                ItemRef.iid(ThematicRole.Attribute.KEY),
                List.of(new CompoundKey.Sememe(ItemRef.iid(Sequence.KEY))),
                (long) (newSequence)));
        bindings.add(new Binding(
                ItemRef.iid(ThematicRole.Instrument.KEY),
                List.of(new CompoundKey.Sememe(ItemRef.iid(Multikey.KEY))),
                newCurrentPublic.encoded()));
        bindings.add(new Binding(
                ItemRef.iid(ThematicRole.Instrument.KEY),
                List.of(new CompoundKey.Sememe(ItemRef.iid(Next.KEY))),
                newNextDigest));
        bindings.add(new Binding(
                ItemRef.iid(ThematicRole.Time.KEY),
                List.of(),
                Instant.now()));

        Body body = Body.of(ItemRef.of(ItemRef.iid(Rotation.KEY)), bindings);
        byte[] payload = HashTree.signingPayload(body);
        VarSig sigOld = signWith(oldCurrent, payload);
        VarSig sigNew = signWith(newCurrent, payload);

        DatumRef bodyRef = DatumRef.of(body.datumId());
        Record recordOld = Record.of(bodyRef, List.of(), sigOld);
        Record recordNew = Record.of(bodyRef, List.of(), sigNew);

        state.currentKeyPair = newCurrent;
        state.nextKeyPair = newNext;
        state.chainHead = body.datumId();
        state.sequence = newSequence;

        return Frame.of(body, List.of(recordOld, recordNew));
    }

    @Override
    public Frame delegate(ItemRef delegateId, ItemRef purpose, DelegationConditions conditions) {
        Objects.requireNonNull(delegateId, "delegateId");
        Objects.requireNonNull(purpose, "purpose");
        Objects.requireNonNull(conditions, "conditions");
        // Delegations are signed by the delegator's signing key.
        PurposeState signingState = requirePurpose(ItemRef.iid(IdentityVocabulary.Signing.KEY));

        List<Binding> bindings = new ArrayList<>();
        bindings.add(Binding.ref(ItemRef.iid(ThematicRole.Agent.KEY), identity));
        bindings.add(Binding.ref(ItemRef.iid(ThematicRole.Theme.KEY), delegateId));
        bindings.add(Binding.ref(ItemRef.iid(ThematicRole.Purpose.KEY), purpose));
        conditions.expiresAt().ifPresent(expiresAt -> bindings.add(new Binding(
                ItemRef.iid(ThematicRole.Attribute.KEY),
                List.of(new CompoundKey.Sememe(ItemRef.iid(Expires.KEY))),
                expiresAt)));
        bindings.add(new Binding(
                ItemRef.iid(ThematicRole.Time.KEY),
                List.of(),
                Instant.now()));

        Body body = Body.of(ItemRef.of(ItemRef.iid(Delegation.KEY)), bindings);
        VarSig sig = signWith(signingState.currentKeyPair, HashTree.signingPayload(body));
        Record record = Record.of(DatumRef.of(body.datumId()), List.of(), sig);

        return Frame.of(body, List.of(record));
    }

    @Override
    public Frame revoke(Object target, ItemRef reason) {
        Objects.requireNonNull(target, "target");
        // Revocations are signed by the signing key.
        PurposeState signingState = requirePurpose(ItemRef.iid(IdentityVocabulary.Signing.KEY));

        List<Binding> bindings = new ArrayList<>();
        bindings.add(new Binding(ItemRef.iid(ThematicRole.Theme.KEY), List.of(), target));
        if (reason != null) {
            bindings.add(Binding.ref(ItemRef.iid(ThematicRole.Purpose.KEY), reason));
        }
        bindings.add(new Binding(
                ItemRef.iid(ThematicRole.Time.KEY),
                List.of(),
                Instant.now()));

        Body body = Body.of(ItemRef.of(ItemRef.iid(Revocation.KEY)), bindings);
        VarSig sig = signWith(signingState.currentKeyPair, HashTree.signingPayload(body));
        Record record = Record.of(DatumRef.of(body.datumId()), List.of(), sig);

        return Frame.of(body, List.of(record));
    }

    // ==================================================================================
    // Vault interface — signing
    // ==================================================================================

    @Override
    public VarSig sign(byte[] message) {
        PurposeState state = requirePurpose(ItemRef.iid(IdentityVocabulary.Signing.KEY));
        return signWith(state.currentKeyPair, message);
    }

    @Override
    public VarSig sign(byte[] message, ItemRef purpose) {
        PurposeState state = requirePurpose(purpose);
        return signWith(state.currentKeyPair, message);
    }

    // ==================================================================================
    // Vault interface — legacy signing-track API (delegates to the purposes map)
    // ==================================================================================

    @Override
    public Optional<ItemRef> signingAlgorithm() {
        PurposeState state = purposes.get(ItemRef.iid(IdentityVocabulary.Signing.KEY));
        return state == null ? Optional.empty() : Optional.of(state.algorithm);
    }

    @Override
    public Optional<MultiKey> signingPublicKey() {
        return publicKey(ItemRef.iid(IdentityVocabulary.Signing.KEY));
    }

    @Override
    public Optional<ContentRef> signingNextKeyDigest() {
        return nextKeyDigest(ItemRef.iid(IdentityVocabulary.Signing.KEY));
    }

    // ==================================================================================
    // Internal helpers
    // ==================================================================================

    /** Look up the state for a purpose; throw if absent (vault has no keys for it). */
    private PurposeState requirePurpose(ItemRef purpose) {
        PurposeState state = purposes.get(purpose);
        if (state == null) {
            throw new IllegalStateException(
                    "Vault has no keys for purpose " + purpose);
        }
        return state;
    }

    private static MultiKey currentPublicKey(PurposeState state) {
        return MultiKey.of(ED25519_HANDLE, rawEd25519PublicKey(state.currentKeyPair.getPublic()));
    }

    private static MultiKey nextPublicKey(PurposeState state) {
        return MultiKey.of(ED25519_HANDLE, rawEd25519PublicKey(state.nextKeyPair.getPublic()));
    }

    /** Sign a message with an explicit keypair (rotation needs both old + new). */
    private static VarSig signWith(KeyPair keyPair, byte[] message) {
        try {
            Signature sig = Signature.getInstance(AlgorithmVocabulary.Ed25519.SIGNATURE_NAME);
            sig.initSign(keyPair.getPrivate());
            sig.update(message);
            return VarSig.of(ED25519_HANDLE, sig.sign());
        } catch (Exception e) {
            throw new RuntimeException("Signing failed", e);
        }
    }

    // ==================================================================================
    // Ed25519-specific helpers
    // ==================================================================================

    private static KeyPair generateEd25519KeyPair() {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance(AlgorithmVocabulary.Ed25519.KEY_FACTORY);
            return gen.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Ed25519 unavailable in this JCA provider", e);
        }
    }

    /**
     * Extract the 32-byte raw form of an Ed25519 public key — little-endian
     * y-coordinate with the x-coordinate sign bit packed into bit 7 of the
     * last byte.  Matches RFC 8032 and the multikey 0xed encoding.
     */
    private static byte[] rawEd25519PublicKey(PublicKey pub) {
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
}
