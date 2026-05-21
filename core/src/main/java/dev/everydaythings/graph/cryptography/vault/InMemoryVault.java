package dev.everydaythings.graph.cryptography.vault;


import dev.everydaythings.graph.canonical.HashTree;
import dev.everydaythings.graph.cryptography.MultiKey;
import dev.everydaythings.graph.cryptography.algorithm.KeyAgreement;
import dev.everydaythings.graph.cryptography.algorithm.PublicKeyAlgorithm;
import dev.everydaythings.graph.cryptography.algorithm.Signing;
import dev.everydaythings.graph.cryptography.VarSig;
import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.datum.Frame;
import dev.everydaythings.graph.datum.Record;
import dev.everydaythings.graph.cryptography.IdentityVocabulary;
import dev.everydaythings.graph.cryptography.IdentityVocabulary.Delegation;
import dev.everydaythings.graph.cryptography.IdentityVocabulary.Inception;
import dev.everydaythings.graph.cryptography.IdentityVocabulary.Revocation;
import dev.everydaythings.graph.cryptography.IdentityVocabulary.Rotation;
import dev.everydaythings.graph.ref.CompoundKey;
import dev.everydaythings.graph.ref.ContentRef;
import dev.everydaythings.graph.ref.DatumRef;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.cryptography.EncryptionVocabulary.Multikey;
import dev.everydaythings.graph.cryptography.IdentityVocabulary.Next;
import dev.everydaythings.graph.CoreVocabulary.Expires;
import dev.everydaythings.graph.CoreVocabulary.Sequence;
import dev.everydaythings.graph.language.ThematicRole;

import java.security.KeyPair;
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
 * <p>Vault operations dispatch through the {@link Signing} (and, when added,
 * {@link dev.everydaythings.graph.cryptography.algorithm.KeyAgreement}) algorithm
 * instances held in each purpose's state.  No hardcoded knowledge of any
 * specific algorithm lives in this class — adding a new signing algorithm or
 * a new key-agreement track is a matter of selecting the right algorithm at
 * construction.
 */
public final class InMemoryVault implements Vault {

    /**
     * Per-purpose state: the algorithm instance, keypairs (current +
     * pre-rotation next), and KEL position.  All operations (sign, mint a
     * new keypair, encode the public key) dispatch through {@link #algorithm}.
     */
    private static final class PurposeState {
        final ItemRef algorithmId;
        final PublicKeyAlgorithm algorithm;
        KeyPair currentKeyPair;
        KeyPair nextKeyPair;
        DatumRef chainHead;     // null = un-incepted
        long sequence;         // 0 before INCEPTION; 1 after; +1 per ROTATION

        PurposeState(ItemRef algorithmId, PublicKeyAlgorithm algorithm,
                     KeyPair currentKeyPair, KeyPair nextKeyPair) {
            this.algorithmId = algorithmId;
            this.algorithm = algorithm;
            this.currentKeyPair = currentKeyPair;
            this.nextKeyPair = nextKeyPair;
            this.chainHead = null;
            this.sequence = 0L;
        }
    }

    private final Map<ItemRef, PurposeState> purposes;
    private final ItemRef identity;  // stable across rotations — derived from initial signing pubkey
    // Retained signed pre-keys.  Newest first; index 0 is the "current" SPK
    // used for new outgoing sessions.  Older entries are kept for in-flight
    // incoming first-messages until destroyOldSignedPreKeys() is called.
    private final java.util.List<KeyPair> signedPreKeys = new java.util.ArrayList<>();
    private final Map<ItemRef, dev.everydaythings.graph.cryptography.DoubleRatchet> sessions = new java.util.HashMap<>();
    // Unconsumed one-time pre-keys.  Single-use; private side destroyed on
    // first consumption.
    private final java.util.List<KeyPair> oneTimePreKeys = new java.util.ArrayList<>();

    private InMemoryVault(Map<ItemRef, PurposeState> purposes, ItemRef identity, KeyPair initialSpk) {
        this.purposes = purposes;
        this.identity = identity;
        this.signedPreKeys.add(initialSpk);
    }

    /** The current (newest) SPK keypair.  Throws if the vault has no SPKs. */
    private KeyPair currentSpk() {
        if (signedPreKeys.isEmpty()) {
            throw new IllegalStateException("Vault has no signed pre-key");
        }
        return signedPreKeys.get(0);
    }

    // ==================================================================================
    // Factory
    // ==================================================================================

    /**
     * Generate a fresh InMemoryVault with two key tracks: Ed25519 on the
     * signing purpose and X25519 on the key-agreement purpose.  Each track
     * gets its own current + pre-rotation-next keypair.
     *
     * <p>Defaults are deliberately fixed for the in-memory implementation:
     * Ed25519 for signing, X25519 for key agreement.  Other algorithms arrive
     * in dedicated vault implementations rather than by parameterizing this
     * factory.
     */
    public static InMemoryVault generate() {
        Signing ed25519 = Signing.Ed25519.builtin();
        KeyPair signingCurrent = ed25519.generateKeyPair();
        KeyPair signingNext = ed25519.generateKeyPair();

        KeyAgreement x25519 = KeyAgreement.X25519.builtin();
        KeyPair kaCurrent = x25519.generateKeyPair();
        KeyPair kaNext = x25519.generateKeyPair();

        // Signed pre-key: a separate X25519 keypair used in X3DH for opening
        // Double-Ratchet sessions asynchronously.  Independent of the
        // key-agreement KEL track; published as a SignedPreKey frame.
        KeyPair spk = x25519.generateKeyPair();

        // Generate one one-time pre-key.  Real Signal-style deployments
        // pre-generate batches of ~100; one suffices to validate X3DH-4DH
        // and gets replenished as OTPKs are consumed.
        KeyPair otpk = x25519.generateKeyPair();

        return fromIdentityKeyPairs(
                signingCurrent, signingNext, kaCurrent, kaNext, spk, otpk);
    }

    /**
     * Construct an InMemoryVault from already-existing keypairs.  Used by
     * persistent vault implementations ({@link JwksFileVault}, future
     * hardware vaults) that hold their keypairs through a different path
     * (decoded from a file, retrieved from a token) but want to reuse this
     * class's operation machinery (sign, agree, KEL events, DR sessions).
     *
     * <p>The IID is derived from {@code signingCurrent}'s public key in
     * multikey form, the same way {@link #generate()} derives it.
     */
    public static InMemoryVault fromIdentityKeyPairs(
            KeyPair signingCurrent, KeyPair signingNext,
            KeyPair kaCurrent,      KeyPair kaNext,
            KeyPair spk,            KeyPair otpk) {
        Signing ed25519 = Signing.Ed25519.builtin();
        KeyAgreement x25519 = KeyAgreement.X25519.builtin();

        PurposeState signing = new PurposeState(
                ItemRef.iid(Signing.Ed25519.KEY), ed25519, signingCurrent, signingNext);
        PurposeState keyAgreement = new PurposeState(
                ItemRef.iid(KeyAgreement.X25519.KEY), x25519, kaCurrent, kaNext);

        Map<ItemRef, PurposeState> purposes = new HashMap<>();
        purposes.put(ItemRef.iid(IdentityVocabulary.Signing.KEY), signing);
        purposes.put(ItemRef.iid(IdentityVocabulary.KeyAgreement.KEY), keyAgreement);

        ItemRef identity = ItemRef.fromMultikeyBytes(
                MultiKey.of(ed25519, ed25519.publicKeyToRaw(signingCurrent.getPublic()))
                        .encoded());
        InMemoryVault vault = new InMemoryVault(purposes, identity, spk);
        if (otpk != null) {
            vault.oneTimePreKeys.add(otpk);
        }
        return vault;
    }

    // ==================================================================================
    // Vault interface — identity & state queries
    // ==================================================================================

    @Override
    public ItemRef identity() {
        return identity;
    }

    @Override
    public Identity rootIdentity() {
        Identity entry = new Identity(rootIdentityEntryId());
        PurposeState signing = purposes.get(ItemRef.iid(IdentityVocabulary.Signing.KEY));
        PurposeState ka      = purposes.get(ItemRef.iid(IdentityVocabulary.KeyAgreement.KEY));

        if (signing != null) {
            entry.currentSigning = buildSigningKey(signing, signing.currentKeyPair);
            entry.nextSigning    = buildSigningKey(signing, signing.nextKeyPair);
        }
        if (ka != null) {
            entry.currentKeyAgreement = buildKeyAgreementKey(ka, ka.currentKeyPair);
            entry.nextKeyAgreement    = buildKeyAgreementKey(ka, ka.nextKeyPair);
        }

        if (!signedPreKeys.isEmpty() && ka != null && ka.algorithm instanceof KeyAgreement xAlg) {
            entry.currentSignedPreKey = buildKeyAgreementKeyDirect(xAlg, signedPreKeys.get(0));
            for (int i = 1; i < signedPreKeys.size(); i++) {
                entry.retainedSignedPreKeys.add(
                        buildKeyAgreementKeyDirect(xAlg, signedPreKeys.get(i)));
            }
            for (KeyPair otpk : oneTimePreKeys) {
                entry.oneTimePreKeys.add(buildKeyAgreementKeyDirect(xAlg, otpk));
            }
        }

        return entry;
    }

    /**
     * Derive the root Identity entry's EntryId deterministically from the
     * vault's IID.  Stable across multiple {@link #rootIdentity()} calls so
     * callers can compare ids.
     */
    private EntryId rootIdentityEntryId() {
        byte[] iidBytes = identity.toRefBytes();
        byte[] entryBytes = new byte[16];
        System.arraycopy(iidBytes, 0, entryBytes, 0, Math.min(iidBytes.length, 16));
        return EntryId.of(entryBytes);
    }

    /** Build a SigningKey handle whose sign lambda closes over the given keypair. */
    private static SigningKey buildSigningKey(PurposeState state, KeyPair kp) {
        if (!(state.algorithm instanceof Signing sig)) {
            throw new IllegalStateException(
                    "Signing purpose has non-signing algorithm: " + state.algorithmId);
        }
        return new SigningKey(
                sig, kp.getPublic(),
                message -> sig.sign(message, kp.getPrivate()));
    }

    /** Build a KeyAgreementKey handle when we only have the typed algorithm directly. */
    private static KeyAgreementKey buildKeyAgreementKeyDirect(KeyAgreement ka, KeyPair kp) {
        return new KeyAgreementKey(
                ka, kp.getPublic(),
                peerPub -> ka.agree(kp.getPrivate(), peerPub));
    }

    /** Build a KeyAgreementKey handle for a purpose track. */
    private static KeyAgreementKey buildKeyAgreementKey(PurposeState state, KeyPair kp) {
        if (!(state.algorithm instanceof KeyAgreement ka)) {
            throw new IllegalStateException(
                    "Key-agreement purpose has non-key-agreement algorithm: " + state.algorithmId);
        }
        return buildKeyAgreementKeyDirect(ka, kp);
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
        PurposeState signingState = signingState();
        Signing signingAlg = signingAlgorithm(signingState);

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
        // Always signed by the signing track's current key.  For signing-track
        // inception this is self-attestation (the body declares the same key
        // that signs it); for non-signing tracks it is the signing identity
        // authorizing the new track.
        VarSig sig = signWith(signingAlg, signingState.currentKeyPair, HashTree.signingPayload(body));
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
        PurposeState signingState = signingState();
        Signing signingAlg = signingAlgorithm(signingState);

        KeyPair oldCurrent = state.currentKeyPair;
        KeyPair newCurrent = state.nextKeyPair;
        KeyPair newNext = state.algorithm.generateKeyPair();

        MultiKey newCurrentPublic = MultiKey.of(state.algorithm,
                state.algorithm.publicKeyToRaw(newCurrent.getPublic()));
        MultiKey newNextPublic = MultiKey.of(state.algorithm,
                state.algorithm.publicKeyToRaw(newNext.getPublic()));
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
        DatumRef bodyRef = DatumRef.of(body.datumId());

        List<Record> records;
        if (state == signingState) {
            // Signing-track rotation: dual signature by old and new signing
            // keys (the previous commitment proven satisfied, the new key
            // proven controlled).
            VarSig sigOld = signWith(signingAlg, oldCurrent, payload);
            VarSig sigNew = signWith(signingAlg, newCurrent, payload);
            records = List.of(
                    Record.of(bodyRef, List.of(), sigOld),
                    Record.of(bodyRef, List.of(), sigNew));
        } else {
            // Non-signing-track rotation: signed once by the signing key,
            // which is itself unchanged by this event.
            VarSig sig = signWith(signingAlg, signingState.currentKeyPair, payload);
            records = List.of(Record.of(bodyRef, List.of(), sig));
        }

        state.currentKeyPair = newCurrent;
        state.nextKeyPair = newNext;
        state.chainHead = body.datumId();
        state.sequence = newSequence;

        return Frame.of(body, records);
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
        VarSig sig = signWith(signingAlgorithm(signingState), signingState.currentKeyPair, HashTree.signingPayload(body));
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
        VarSig sig = signWith(signingAlgorithm(signingState), signingState.currentKeyPair, HashTree.signingPayload(body));
        Record record = Record.of(DatumRef.of(body.datumId()), List.of(), sig);

        return Frame.of(body, List.of(record));
    }

    // ==================================================================================
    // Vault interface — signing
    // ==================================================================================

    @Override
    public VarSig sign(byte[] message) {
        PurposeState state = signingState();
        return signWith(signingAlgorithm(state), state.currentKeyPair, message);
    }

    @Override
    public VarSig sign(byte[] message, ItemRef purpose) {
        PurposeState state = requirePurpose(purpose);
        return signWith(signingAlgorithm(state), state.currentKeyPair, message);
    }

    // ==================================================================================
    // Vault interface — legacy signing-track API (delegates to the purposes map)
    // ==================================================================================

    @Override
    public Optional<ItemRef> signingAlgorithm() {
        PurposeState state = purposes.get(ItemRef.iid(IdentityVocabulary.Signing.KEY));
        return state == null ? Optional.empty() : Optional.of(state.algorithmId);
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
    // Vault interface — key-agreement track
    // ==================================================================================

    @Override
    public Optional<ItemRef> keyAgreementAlgorithm() {
        PurposeState state = purposes.get(ItemRef.iid(IdentityVocabulary.KeyAgreement.KEY));
        return state == null ? Optional.empty() : Optional.of(state.algorithmId);
    }

    @Override
    public Optional<MultiKey> keyAgreementPublicKey() {
        return publicKey(ItemRef.iid(IdentityVocabulary.KeyAgreement.KEY));
    }

    @Override
    public Optional<ContentRef> keyAgreementNextKeyDigest() {
        return nextKeyDigest(ItemRef.iid(IdentityVocabulary.KeyAgreement.KEY));
    }

    @Override
    public byte[] agree(java.security.PublicKey peerPublicKey) {
        Objects.requireNonNull(peerPublicKey, "peerPublicKey");
        PurposeState state = requirePurpose(ItemRef.iid(IdentityVocabulary.KeyAgreement.KEY));
        if (!(state.algorithm instanceof KeyAgreement ka)) {
            throw new IllegalStateException(
                    "Key-agreement purpose has non-key-agreement algorithm: " + state.algorithmId);
        }
        return ka.agree(state.currentKeyPair.getPrivate(), peerPublicKey);
    }

    // ==================================================================================
    // Signed pre-key + Double-Ratchet sessions
    // ==================================================================================

    @Override
    public Optional<MultiKey> signedPreKeyPublicKey() {
        if (signedPreKeys.isEmpty()) return Optional.empty();
        KeyAgreement.X25519 x = KeyAgreement.X25519.builtin();
        return Optional.of(MultiKey.of(x, x.publicKeyToRaw(currentSpk().getPublic())));
    }

    @Override
    public Frame signedPreKeyFrame() {
        return preKeyFrame(
                ItemRef.iid(IdentityVocabulary.SignedPreKey.KEY),
                currentSpk().getPublic());
    }

    @Override
    public Optional<MultiKey> rotateSignedPreKey() {
        KeyAgreement.X25519 x = KeyAgreement.X25519.builtin();
        KeyPair fresh = x.generateKeyPair();
        signedPreKeys.add(0, fresh);   // newest first
        return Optional.of(MultiKey.of(x, x.publicKeyToRaw(fresh.getPublic())));
    }

    @Override
    public void destroyOldSignedPreKeys() {
        if (signedPreKeys.size() <= 1) return;
        // Drop everything but the current (index 0).  Their private sides
        // are released when their KeyPair references are no longer reachable.
        KeyPair current = signedPreKeys.get(0);
        signedPreKeys.clear();
        signedPreKeys.add(current);
    }

    @Override
    public Optional<KeyPair> consumeSignedPreKey(byte[] rawPubKey) {
        KeyAgreement.X25519 x = KeyAgreement.X25519.builtin();
        for (KeyPair kp : signedPreKeys) {
            byte[] ourRaw = x.publicKeyToRaw(kp.getPublic());
            if (java.util.Arrays.equals(ourRaw, rawPubKey)) {
                return Optional.of(kp);   // SPKs are reusable until rotated out
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<MultiKey> oneTimePreKeyPublicKey() {
        if (oneTimePreKeys.isEmpty()) return Optional.empty();
        KeyAgreement.X25519 x = KeyAgreement.X25519.builtin();
        KeyPair otpk = oneTimePreKeys.get(0);
        return Optional.of(MultiKey.of(x, x.publicKeyToRaw(otpk.getPublic())));
    }

    @Override
    public Optional<Frame> oneTimePreKeyFrame() {
        if (oneTimePreKeys.isEmpty()) return Optional.empty();
        KeyPair otpk = oneTimePreKeys.get(0);
        return Optional.of(preKeyFrame(
                ItemRef.iid(IdentityVocabulary.OneTimePreKey.KEY),
                otpk.getPublic()));
    }

    @Override
    public Optional<KeyPair> consumeOneTimePreKey(byte[] rawPubKey) {
        KeyAgreement.X25519 x = KeyAgreement.X25519.builtin();
        for (java.util.Iterator<KeyPair> it = oneTimePreKeys.iterator(); it.hasNext();) {
            KeyPair kp = it.next();
            byte[] ourRaw = x.publicKeyToRaw(kp.getPublic());
            if (java.util.Arrays.equals(ourRaw, rawPubKey)) {
                it.remove();   // single-use: destroy on first use
                return Optional.of(kp);
            }
        }
        return Optional.empty();
    }

    /**
     * Build a self-signed pre-key frame (SignedPreKey or OneTimePreKey
     * depending on {@code predicateIid}) for the given X25519 pubkey.
     */
    private Frame preKeyFrame(ItemRef predicateIid, java.security.PublicKey pub) {
        KeyAgreement.X25519 x = KeyAgreement.X25519.builtin();
        MultiKey keyMulti = MultiKey.of(x, x.publicKeyToRaw(pub));

        List<Binding> bindings = new ArrayList<>();
        bindings.add(Binding.ref(ItemRef.iid(ThematicRole.Theme.KEY), identity));
        bindings.add(new Binding(
                ItemRef.iid(ThematicRole.Instrument.KEY),
                List.of(new CompoundKey.Sememe(ItemRef.iid(Multikey.KEY))),
                keyMulti.encoded()));
        bindings.add(Binding.ref(
                ItemRef.iid(ThematicRole.Purpose.KEY),
                ItemRef.iid(IdentityVocabulary.KeyAgreement.KEY)));
        bindings.add(new Binding(
                ItemRef.iid(ThematicRole.Time.KEY),
                List.of(),
                Instant.now()));

        Body body = Body.of(ItemRef.of(predicateIid), bindings);
        PurposeState signingState = signingState();
        VarSig sig = signWith(signingAlgorithm(signingState),
                signingState.currentKeyPair,
                HashTree.signingPayload(body));
        Record record = Record.of(DatumRef.of(body.datumId()), List.of(), sig);
        return Frame.of(body, List.of(record));
    }

    @Override
    public void openSessionTo(ItemRef peerIid, MultiKey peerIkPub, MultiKey peerSpkPub,
                              MultiKey peerOtpkPub) {
        Objects.requireNonNull(peerIid, "peerIid");
        Objects.requireNonNull(peerIkPub, "peerIkPub");
        Objects.requireNonNull(peerSpkPub, "peerSpkPub");

        KeyAgreement.X25519 x = KeyAgreement.X25519.builtin();
        KeyPair ephemeral = x.generateKeyPair();
        java.security.PublicKey peerIkJca  = x.decodePublicKey(peerIkPub.rawKey());
        java.security.PublicKey peerSpkJca = x.decodePublicKey(peerSpkPub.rawKey());
        java.security.PublicKey peerOtpkJca = peerOtpkPub == null
                ? null
                : x.decodePublicKey(peerOtpkPub.rawKey());

        byte[] sk = dev.everydaythings.graph.cryptography.X3dh.initiator(
                this, ephemeral, peerIkJca, peerSpkJca, peerOtpkJca);

        // Bootstrap bindings: included on every outgoing message until the
        // peer responds, so the peer can run X3DH responder asynchronously.
        MultiKey ourIk = keyAgreementPublicKey().orElseThrow();
        byte[] ourEkPub = x.publicKeyToRaw(ephemeral.getPublic());
        java.util.List<dev.everydaythings.graph.datum.Binding> bootstrap = new java.util.ArrayList<>();
        bootstrap.add(new dev.everydaythings.graph.datum.Binding(
                ItemRef.iid(dev.everydaythings.graph.cryptography.EncryptionVocabulary.InitiatorIdentityKey.KEY),
                ourIk.rawKey()));
        bootstrap.add(new dev.everydaythings.graph.datum.Binding(
                ItemRef.iid(dev.everydaythings.graph.cryptography.EncryptionVocabulary.InitiatorEphemeralKey.KEY),
                ourEkPub));
        // Name which of the peer's SPKs we used — needed so the responder
        // can find the matching private key among its retained SPKs.
        bootstrap.add(new dev.everydaythings.graph.datum.Binding(
                ItemRef.iid(dev.everydaythings.graph.cryptography.EncryptionVocabulary.InitiatorSignedPreKey.KEY),
                peerSpkPub.rawKey()));
        if (peerOtpkPub != null) {
            bootstrap.add(new dev.everydaythings.graph.datum.Binding(
                    ItemRef.iid(dev.everydaythings.graph.cryptography.EncryptionVocabulary.ConsumedPreKey.KEY),
                    peerOtpkPub.rawKey()));
        }

        dev.everydaythings.graph.cryptography.DoubleRatchet dr =
                dev.everydaythings.graph.cryptography.DoubleRatchet.initInitiator(
                        sk, peerSpkPub.rawKey(), bootstrap);
        sessions.put(peerIid, dr);
    }

    @Override
    public dev.everydaythings.graph.cryptography.DoubleRatchet.EncryptedMessage encryptInSession(
            ItemRef peerIid, byte[] plaintext) {
        return requireSession(peerIid).encrypt(plaintext, java.util.List.of());
    }

    @Override
    public byte[] decryptInSession(ItemRef peerIid,
                                   dev.everydaythings.graph.cryptography.DoubleRatchet.EncryptedMessage message) {
        dev.everydaythings.graph.cryptography.DoubleRatchet dr = sessions.get(peerIid);
        if (dr == null) {
            dr = bootstrapResponderSession(peerIid, message.recordBindings());
            sessions.put(peerIid, dr);
        }
        return dr.decrypt(message);
    }

    /**
     * Construct a responder-side DR session from the bootstrap bindings on a
     * first message: extracts INITIATOR_IDENTITY_KEY and
     * INITIATOR_EPHEMERAL_KEY, runs X3DH's responder path with our signed
     * pre-key, and initializes a fresh DR state.
     */
    private dev.everydaythings.graph.cryptography.DoubleRatchet bootstrapResponderSession(
            ItemRef peerIid, java.util.List<dev.everydaythings.graph.datum.Binding> bindings) {
        byte[] initiatorIkRaw = null;
        byte[] initiatorEkRaw = null;
        byte[] usedSpkRaw = null;
        byte[] consumedOtpkRaw = null;
        ItemRef ikRole = ItemRef.iid(dev.everydaythings.graph.cryptography.EncryptionVocabulary.InitiatorIdentityKey.KEY);
        ItemRef ekRole = ItemRef.iid(dev.everydaythings.graph.cryptography.EncryptionVocabulary.InitiatorEphemeralKey.KEY);
        ItemRef spkRole = ItemRef.iid(dev.everydaythings.graph.cryptography.EncryptionVocabulary.InitiatorSignedPreKey.KEY);
        ItemRef otpkRole = ItemRef.iid(dev.everydaythings.graph.cryptography.EncryptionVocabulary.ConsumedPreKey.KEY);
        for (dev.everydaythings.graph.datum.Binding b : bindings) {
            Object role = b.role();
            if (!(role instanceof ItemRef ir)) continue;
            if (ir.equals(ikRole) && b.target() instanceof byte[] bytes) {
                initiatorIkRaw = bytes;
            } else if (ir.equals(ekRole) && b.target() instanceof byte[] bytes) {
                initiatorEkRaw = bytes;
            } else if (ir.equals(spkRole) && b.target() instanceof byte[] bytes) {
                usedSpkRaw = bytes;
            } else if (ir.equals(otpkRole) && b.target() instanceof byte[] bytes) {
                consumedOtpkRaw = bytes;
            }
        }
        if (initiatorIkRaw == null || initiatorEkRaw == null) {
            throw new IllegalStateException(
                    "No session with peer " + peerIid
                    + " and message lacks INITIATOR_IDENTITY_KEY/INITIATOR_EPHEMERAL_KEY bootstrap bindings");
        }
        KeyAgreement.X25519 x = KeyAgreement.X25519.builtin();
        java.security.PublicKey peerIkJca = x.decodePublicKey(initiatorIkRaw);
        java.security.PublicKey peerEkJca = x.decodePublicKey(initiatorEkRaw);

        // Find the SPK the initiator used.  With rotation, the vault may
        // hold several; pick the matching one.  Fall back to current (for
        // bootstrap messages that pre-date the SPK-binding requirement, none
        // exist in this codebase but the fallback keeps responder-side
        // robust).
        KeyPair spkUsed;
        if (usedSpkRaw != null) {
            spkUsed = consumeSignedPreKey(usedSpkRaw).orElseThrow(() -> new IllegalStateException(
                    "Bootstrap message references an SPK we don't have (rotated out and destroyed)"));
        } else {
            spkUsed = currentSpk();
        }

        // If the initiator consumed one of our OTPKs, look it up and destroy
        // the private side.  X3DH-responder gets the matching keypair for DH4.
        KeyPair otpk = consumedOtpkRaw == null
                ? null
                : consumeOneTimePreKey(consumedOtpkRaw).orElseThrow(() -> new IllegalStateException(
                        "Bootstrap message references an OTPK we don't have (already consumed, or never minted)"));

        byte[] sk = dev.everydaythings.graph.cryptography.X3dh.responder(
                this, spkUsed, otpk, peerIkJca, peerEkJca);
        return dev.everydaythings.graph.cryptography.DoubleRatchet.initResponder(sk, spkUsed);
    }

    @Override
    public boolean hasSessionWith(ItemRef peerIid) {
        return sessions.containsKey(peerIid);
    }

    @Override
    public void closeSession(ItemRef peerIid) {
        sessions.remove(peerIid);
    }

    private dev.everydaythings.graph.cryptography.DoubleRatchet requireSession(ItemRef peerIid) {
        dev.everydaythings.graph.cryptography.DoubleRatchet dr = sessions.get(peerIid);
        if (dr == null) {
            throw new IllegalStateException("No session with peer " + peerIid);
        }
        return dr;
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

    /** Convenience accessor for the signing purpose's state. */
    private PurposeState signingState() {
        return requirePurpose(ItemRef.iid(IdentityVocabulary.Signing.KEY));
    }

    /**
     * Extract the signing-algorithm runtime instance from a {@link PurposeState}
     * that's expected to be the signing track.  Throws if the state's
     * algorithm is not a {@link Signing}.
     */
    private static Signing signingAlgorithm(PurposeState state) {
        if (state.algorithm instanceof Signing s) return s;
        throw new IllegalStateException(
                "Purpose " + state.algorithmId + " is not a signing algorithm");
    }

    private static MultiKey currentPublicKey(PurposeState state) {
        return MultiKey.of(state.algorithm,
                state.algorithm.publicKeyToRaw(state.currentKeyPair.getPublic()));
    }

    private static MultiKey nextPublicKey(PurposeState state) {
        return MultiKey.of(state.algorithm,
                state.algorithm.publicKeyToRaw(state.nextKeyPair.getPublic()));
    }

    /**
     * Sign a message with an explicit keypair (rotation needs both old + new).
     * Always produces a {@link Signing}-based signature; the caller chooses
     * which keypair to use.
     */
    private static VarSig signWith(Signing algorithm, KeyPair keyPair, byte[] message) {
        byte[] sig = algorithm.sign(message, keyPair.getPrivate());
        return VarSig.of(algorithm, sig);
    }
}
