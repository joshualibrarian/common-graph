package dev.everydaythings.graph.datum;

import dev.everydaythings.graph.encoding.CgCbor;

import dev.everydaythings.graph.canonical.HashTree;
import dev.everydaythings.graph.identity.AlgorithmVocabulary;
import dev.everydaythings.graph.identity.algorithm.Algorithm;
import dev.everydaythings.graph.identity.algorithm.Signing;
import dev.everydaythings.graph.identity.MultiKey;
import dev.everydaythings.graph.identity.VarSig;
import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.id.ContentRef;
import dev.everydaythings.graph.id.DatumRef;
import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.identity.algorithm.Signing;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.Signature;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration test demonstrating that the new Datum/Body/Record/Frame/
 * Manifest types compose correctly with real Ed25519 signing and verification.
 *
 * <p>This is the proof-of-life for the new structural foundation: a body is built,
 * signed by a real key, wrapped in a record, aggregated into a frame, and
 * verified end-to-end. Then the same pattern is exercised for a manifest
 * (archetypal body with ITEM_ID, FOLLOWS, ENDORSES).
 */
class DatumIntegrationTest {

    static final ItemRef AUTHORED = ItemRef.fromString("cg.predicate:authored");
    static final ItemRef DOCUMENT = ItemRef.fromString("cg.archetype:document");
    static final ItemRef THEME    = ItemRef.fromString("cg.role:theme");
    static final ItemRef AGENT    = ItemRef.fromString("cg.role:agent");
    static final ItemRef SIGNER   = ItemRef.fromString("cg.role:signer");
    static final ItemRef TIME     = ItemRef.fromString("cg.role:time");

    @Test
    @DisplayName("end-to-end: build body, sign with Ed25519, wrap as record, verify")
    void endToEndPropositionalFrame() throws Exception {
        // Generate a real Ed25519 keypair via JCA
        KeyPairGenerator gen = KeyPairGenerator.getInstance("Ed25519");
        KeyPair kp = gen.generateKeyPair();

        // Extract the raw 32-byte public key from the SPKI form
        byte[] rawPublicKey = extractRawEd25519PublicKey(kp.getPublic());
        assertThat(rawPublicKey).hasSize(32);

        // Wrap it as a MultiKey using the Ed25519 multikey codec.
        MultiKey signerKey = MultiKey.of((int) Signing.Ed25519.MULTIKEY_CODE, rawPublicKey);

        // Build a body — "Tolkien authored the Hobbit"
        ItemRef tolkien = ItemRef.fromString("person.tolkien");
        ItemRef hobbit  = ItemRef.fromString("book.hobbit");
        Body body = Body.of(ItemRef.of(AUTHORED), List.of(
                Binding.ref(AGENT, tolkien),
                Binding.ref(THEME, hobbit)
        ));
        DatumRef bodyId = body.datumId();
        ContentRef bodyCid = ContentRef.of(CgCbor.encode(body));

        // Build a record body (head + bindings, no signature yet) for signing
        Record proto = Record.of(
                DatumRef.of(bodyId),
                List.of(
                        Binding.ref(SIGNER, ItemRef.fromString("dummy")),
                        Binding.literal(TIME, dev.everydaythings.graph.datum.BindingTarget.iid(
                                ItemRef.fromString("2026-05-04")))
                ),
                new byte[]{0x01}  // placeholder; we'll replace below
        );

        // The bytes the signature attests — the Merkle root over head + bindings
        byte[] toSign = HashTree.signingPayload(proto);

        // Sign with the private key
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(kp.getPrivate());
        signer.update(toSign);
        byte[] rawSignature = signer.sign();
        assertThat(rawSignature).hasSize(64);

        // Wrap raw signature as VarSig using the Ed25519 varsig codec.
        VarSig varsig = VarSig.of((int) Signing.Ed25519.VARSIG_CODE, rawSignature);

        // Build the real record with the actual signature
        Record record = Record.of(
                DatumRef.of(bodyId),
                proto.bindings(),
                varsig
        );

        // Aggregate into a Frame
        Frame frame = Frame.of(body, List.of(record));
        assertThat(frame.records()).hasSize(1);
        // bodyCID assertion deleted — ContentRef computation lives at the Library boundary.

        // Verify the signature: recompute the signing payload (Merkle root) from
        // the record and check against the public key
        byte[] recoveredToSign = HashTree.signingPayload(record);
        assertThat(recoveredToSign).containsExactly(toSign);

        Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(kp.getPublic());
        verifier.update(recoveredToSign);
        VarSig recoveredSig = record.varsig();
        assertThat(recoveredSig.code()).isEqualTo((int) Signing.Ed25519.VARSIG_CODE);
        assertThat(verifier.verify(recoveredSig.rawSig())).isTrue();

        // Round-trip the multikey through wire form and confirm
        MultiKey roundTrippedKey = MultiKey.decode(signerKey.encoded());
        assertThat(roundTrippedKey).isEqualTo(signerKey);
        assertThat(roundTrippedKey.code()).isEqualTo((int) Signing.Ed25519.MULTIKEY_CODE);
    }

    @Test
    @DisplayName("end-to-end: build archetypal manifest with ITEM_ID, FOLLOWS, ENDORSES")
    void endToEndManifest() throws Exception {
        ItemRef iid = ItemRef.fromString("my-document-item");
        ItemRef parentVid = ItemRef.fromString("v1");
        ItemRef endorsedFrameCid = ItemRef.fromString("frame-1");

        // Inception manifest (no FOLLOWS): body has ITEM_ID + ENDORSES
        Body inceptionBody = Body.of(ItemRef.of(DOCUMENT), List.of(
                Binding.ref(Manifest.ITEM_ID, iid),
                Binding.ref(Manifest.ENDORSES, endorsedFrameCid)
        ));
        Manifest inception = Manifest.of(inceptionBody);

        assertThat(inception.itemId()).isEqualTo(iid);
        assertThat(inception.parents()).isEmpty();
        assertThat(inception.endorses()).hasSize(1);
        assertThat(inception.versionId()).isEqualTo(inceptionBody.datumId());
        assertThat(inception.isArchetypal()).isTrue();

        // V2 follows V1
        Body v2Body = Body.of(ItemRef.of(DOCUMENT), List.of(
                Binding.ref(Manifest.ITEM_ID, iid),
                Binding.ref(Manifest.FOLLOWS, parentVid),
                Binding.ref(Manifest.ENDORSES, endorsedFrameCid),
                Binding.ref(Manifest.ENDORSES, ItemRef.fromString("frame-2"))
        ));
        Manifest v2 = Manifest.of(v2Body);

        assertThat(v2.itemId()).isEqualTo(iid);
        assertThat(v2.parents()).hasSize(1);
        assertThat(v2.endorses()).hasSize(2);

        // CBOR round-trip preserves identity
        Body decoded = CgCbor.decodeBody(CgCbor.toCbor(v2Body));
        assertThat(ContentRef.of(CgCbor.encode(decoded)))
                .isEqualTo(ContentRef.of(CgCbor.encode(v2Body)));
        Manifest reconstituted = Manifest.of(decoded);
        assertThat(reconstituted.itemId()).isEqualTo(iid);
        assertThat(reconstituted.parents()).hasSize(1);
        assertThat(reconstituted.endorses()).hasSize(2);
    }

    @Test
    @DisplayName("Frame and Manifest distinguish via ITEM_ID binding presence")
    void frameVsManifest() {
        ItemRef iid = ItemRef.fromString("doc-item");

        // Without ITEM_ID — propositional Frame
        Body propBody = Body.of(ItemRef.of(AUTHORED), List.of(
                Binding.ref(THEME, ItemRef.fromString("hobbit"))
        ));
        Frame frame = Frame.of(propBody);
        assertThat(frame.isArchetypal()).isFalse();
        assertThat(frame.asManifest()).isEmpty();

        // With ITEM_ID — archetypal Manifest
        Body manBody = Body.of(ItemRef.of(DOCUMENT), List.of(
                Binding.ref(Manifest.ITEM_ID, iid)
        ));
        Manifest manifest = Manifest.of(manBody);
        assertThat(manifest.isArchetypal()).isTrue();
        assertThat(manifest.asManifest()).contains(manifest);
    }

    /** Extract the raw 32-byte Ed25519 public key from a JCA PublicKey. */
    private static byte[] extractRawEd25519PublicKey(PublicKey pk) {
        return Signing.Ed25519.builtin().publicKeyToRaw(pk);
    }
}
