package dev.everydaythings.graph.cryptography;

import dev.everydaythings.graph.canonical.HashTree;
import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.datum.Frame;
import dev.everydaythings.graph.datum.Record;
import dev.everydaythings.graph.ref.CompoundKey;
import dev.everydaythings.graph.ref.DatumRef;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.cryptography.algorithm.Signing;
import dev.everydaythings.graph.cryptography.vault.Vault;
import dev.everydaythings.graph.ThematicRole;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Builders and readers for {@link IdentityVocabulary.Attestation Attestation}
 * frames — the CG-native form of "X attests subject Y's key is K."
 *
 * <p>CG-native genesis (an identity attesting its own key, or one identity
 * attesting another's) goes through {@link #selfSign} or {@link #attest}
 * directly.  The Vault signs the CG body bytes; no X.509 detour.
 *
 * <p>X.509 only enters the picture when an Attestation Frame needs to be
 * presented to a PKI-shaped peer (TLS handshake, foreign cert exchange).
 * That's the {@link dev.everydaythings.graph.bridges.x509.CertEmitter
 * CertEmitter} direction; equally,
 * {@link dev.everydaythings.graph.bridges.x509.CertIngester CertIngester}
 * goes the other way — X.509 bytes from PKI back into an Attestation Frame.
 *
 * <p>The reader methods (subject, attester, subjectPubkey, ...) provide a
 * single canonical place to extract Attestation fields, used by CertEmitter
 * and by anything that needs to inspect an Attestation body.
 */
public final class Attestations {

    private Attestations() {}

    // ==================================================================================
    // Builders
    // ==================================================================================

    /**
     * Build a self-attested key binding — subject == attester.  The Vault's
     * signing-purpose key is both being declared and doing the attesting.
     * The most common shape for CG-native AID certs (one identity vouching
     * for its own pubkey).
     *
     * <p>For the KEL-style founding event (with pre-rotation commitment and
     * chain head), use {@link Vault#incept Vault.incept(Signing)} instead.
     * Attestation is the more general form without KEL semantics.
     *
     * @throws IllegalStateException if the vault has no signing key
     */
    public static Frame selfSign(Vault vault, Instant validFrom, Instant validUntil) {
        return selfSign(vault, ItemRef.iid(IdentityVocabulary.Signing.KEY), validFrom, validUntil);
    }

    /**
     * Self-sign for a non-default purpose (e.g., encryption track).
     */
    public static Frame selfSign(Vault vault, ItemRef purpose,
                                 Instant validFrom, Instant validUntil) {
        Objects.requireNonNull(vault, "vault");
        Objects.requireNonNull(purpose, "purpose");
        MultiKey subjectKey = vault.publicKey(purpose)
                .orElseThrow(() -> new IllegalStateException(
                        "Vault has no key for purpose " + purpose));
        ItemRef subjectIid = ItemRef.fromMultikeyBytes(subjectKey.encoded());
        return build(vault, subjectIid, subjectIid, subjectKey, purpose, validFrom, validUntil);
    }

    /**
     * Build a third-party attestation — {@code attestingVault} signs an
     * assertion about {@code subjectIid}'s pubkey.  Used when one identity
     * vouches for another (CA-style attestations, peer attestations).
     */
    public static Frame attest(Vault attestingVault,
                               ItemRef subjectIid, MultiKey subjectPubkey,
                               ItemRef purpose,
                               Instant validFrom, Instant validUntil) {
        Objects.requireNonNull(attestingVault, "attestingVault");
        Objects.requireNonNull(subjectIid, "subjectIid");
        Objects.requireNonNull(subjectPubkey, "subjectPubkey");
        Objects.requireNonNull(purpose, "purpose");
        MultiKey attesterKey = attestingVault.signingPublicKey()
                .orElseThrow(() -> new IllegalStateException(
                        "Attesting vault has no signing key"));
        ItemRef attesterIid = ItemRef.fromMultikeyBytes(attesterKey.encoded());
        return build(attestingVault, attesterIid, subjectIid, subjectPubkey,
                purpose, validFrom, validUntil);
    }

    private static Frame build(Vault signingVault,
                               ItemRef attesterIid, ItemRef subjectIid,
                               MultiKey subjectPubkey, ItemRef purpose,
                               Instant validFrom, Instant validUntil) {
        Objects.requireNonNull(validFrom, "validFrom");
        Objects.requireNonNull(validUntil, "validUntil");
        if (validUntil.isBefore(validFrom)) {
            throw new IllegalArgumentException(
                    "validUntil " + validUntil + " is before validFrom " + validFrom);
        }

        List<Binding> bindings = new ArrayList<>();
        bindings.add(Binding.ref(ItemRef.iid(ThematicRole.Agent.KEY), attesterIid));
        bindings.add(Binding.ref(ItemRef.iid(ThematicRole.Theme.KEY), subjectIid));
        bindings.add(new Binding(ItemRef.iid(ThematicRole.Instrument.KEY), subjectPubkey.encoded()));
        bindings.add(Binding.ref(ItemRef.iid(ThematicRole.Purpose.KEY), purpose));
        bindings.add(Binding.qualified(
                ItemRef.iid(ThematicRole.Attribute.KEY),
                List.of(new CompoundKey.Sememe(ItemRef.iid(IdentityVocabulary.ValidityFrom.KEY))),
                validFrom));
        bindings.add(Binding.qualified(
                ItemRef.iid(ThematicRole.Attribute.KEY),
                List.of(new CompoundKey.Sememe(ItemRef.iid(IdentityVocabulary.ValidityUntil.KEY))),
                validUntil));
        bindings.add(new Binding(ItemRef.iid(ThematicRole.Time.KEY), Instant.now()));

        Body body = Body.of(
                ItemRef.of(ItemRef.iid(IdentityVocabulary.Attestation.KEY)),
                bindings);
        byte[] payload = HashTree.signingPayload(body);
        VarSig sig = signingVault.sign(payload);
        Record record = Record.of(DatumRef.of(body.datumId()), List.of(), sig);
        return Frame.of(body, List.of(record));
    }

    // ==================================================================================
    // Readers — extract Attestation fields from a body
    // ==================================================================================

    /** The attester's IID — who signed off on this key binding. */
    public static Optional<ItemRef> attester(Body attestation) {
        return refBindingAt(attestation, ThematicRole.Agent.KEY);
    }

    /** The subject's IID — whose key is being attested. */
    public static Optional<ItemRef> subject(Body attestation) {
        return refBindingAt(attestation, ThematicRole.Theme.KEY);
    }

    /**
     * The subject's pubkey, decoded from the INSTRUMENT binding's multikey
     * bytes.  Resolves the algorithm via the built-in registry when
     * possible (so the resulting MultiKey can produce a JCA PublicKey
     * directly even without librarian context).
     */
    public static Optional<MultiKey> subjectPubkey(Body attestation) {
        return targetAt(attestation, ThematicRole.Instrument.KEY, byte[].class)
                .map(Attestations::decodeWithBuiltinAlgorithm);
    }

    private static MultiKey decodeWithBuiltinAlgorithm(byte[] bytes) {
        MultiKey mk = MultiKey.decode(bytes);
        if (mk.algorithm() != null) return mk;
        // Only Ed25519 has a built-in librarian-less form today.  Other
        // algorithms reach this path with algorithm()==null; callers either
        // resolve later through a librarian, or accept a no-algorithm MultiKey.
        if (mk.code() == (int) Signing.Ed25519.MULTIKEY_CODE) {
            return MultiKey.of(Signing.Ed25519.builtin(), mk.rawKey());
        }
        return mk;
    }

    /** The key's cryptographic purpose (signing, encryption, etc.). */
    public static Optional<ItemRef> purpose(Body attestation) {
        return refBindingAt(attestation, ThematicRole.Purpose.KEY);
    }

    /** Lower bound of the validity window. */
    public static Optional<Instant> validFrom(Body attestation) {
        return compoundAttribute(attestation, IdentityVocabulary.ValidityFrom.KEY, Instant.class);
    }

    /** Upper bound of the validity window. */
    public static Optional<Instant> validUntil(Body attestation) {
        return compoundAttribute(attestation, IdentityVocabulary.ValidityUntil.KEY, Instant.class);
    }

    /** When the attestation was issued (the TIME binding). */
    public static Optional<Instant> issuedAt(Body attestation) {
        return targetAt(attestation, ThematicRole.Time.KEY, Instant.class);
    }

    // ==================================================================================
    // Reader helpers
    // ==================================================================================

    private static Optional<ItemRef> refBindingAt(Body body, String roleKey) {
        return targetAt(body, roleKey, ItemRef.class);
    }

    private static <T> Optional<T> targetAt(Body body, String roleKey, Class<T> type) {
        ItemRef role = ItemRef.iid(roleKey);
        for (Object entry : body.entries()) {
            if (!(entry instanceof Binding b)) continue;
            if (!role.equals(b.role())) continue;
            if (!b.qualifiers().isEmpty()) continue;
            Object target = b.target();
            if (target != null && type.isInstance(target)) {
                return Optional.of(type.cast(target));
            }
        }
        return Optional.empty();
    }

    private static <T> Optional<T> compoundAttribute(Body body, String qualifierKey, Class<T> type) {
        ItemRef role = ItemRef.iid(ThematicRole.Attribute.KEY);
        ItemRef qual = ItemRef.iid(qualifierKey);
        for (Object entry : body.entries()) {
            if (!(entry instanceof Binding b)) continue;
            if (!role.equals(b.role())) continue;
            boolean matches = b.qualifiers().stream()
                    .anyMatch(q -> q instanceof CompoundKey.Sememe s && qual.equals(s.id()));
            if (!matches) continue;
            Object target = b.target();
            if (target != null && type.isInstance(target)) {
                return Optional.of(type.cast(target));
            }
        }
        return Optional.empty();
    }
}
