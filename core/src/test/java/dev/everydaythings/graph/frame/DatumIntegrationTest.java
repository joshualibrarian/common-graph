package dev.everydaythings.graph.frame;

import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.crypt.Algorithm;
import dev.everydaythings.graph.crypt.MultiKey;
import dev.everydaythings.graph.crypt.VarSig;
import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.item.id.ContentID;
import dev.everydaythings.graph.item.id.FrameRef;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.id.ItemRef;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
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

    static final ItemID AUTHORED = ItemID.fromString("cg.predicate:authored");
    static final ItemID DOCUMENT = ItemID.fromString("cg.archetype:document");
    static final ItemID THEME    = ItemID.fromString("cg.role:theme");
    static final ItemID AGENT    = ItemID.fromString("cg.role:agent");
    static final ItemID SIGNER   = ItemID.fromString("cg.role:signer");
    static final ItemID TIME     = ItemID.fromString("cg.role:time");

    @Test
    @DisplayName("end-to-end: build body, sign with Ed25519, wrap as record, verify")
    void endToEndPropositionalFrame() throws Exception {
        // Generate a real Ed25519 keypair via JCA
        KeyPairGenerator gen = KeyPairGenerator.getInstance("Ed25519");
        KeyPair kp = gen.generateKeyPair();

        // Extract the raw 32-byte public key from the SPKI form
        byte[] rawPublicKey = extractRawEd25519PublicKey(kp.getPublic());
        assertThat(rawPublicKey).hasSize(32);

        // Wrap it as a MultiKey
        MultiKey signerKey = MultiKey.of(Algorithm.Sign.ED25519, rawPublicKey);

        // Build a body — "Tolkien authored the Hobbit"
        ItemID tolkien = ItemID.fromString("person.tolkien");
        ItemID hobbit  = ItemID.fromString("book.hobbit");
        Body body = Body.of(ItemRef.of(AUTHORED), List.of(
                Binding.ref(AGENT, tolkien),
                Binding.ref(THEME, hobbit)
        ));
        ContentID bodyCid = body.cid();

        // Build a record body (head + bindings, no signature yet) for signing
        Record proto = Record.of(
                FrameRef.of(bodyCid),
                List.of(
                        Binding.ref(SIGNER, ItemID.fromString("dummy")),
                        Binding.literal(TIME, dev.everydaythings.graph.frame.BindingTarget.iid(
                                ItemID.fromString("2026-05-04")))
                ),
                new byte[]{0x01}  // placeholder; we'll replace below
        );

        // The bytes the signature attests
        byte[] toSign = proto.encodeBodyForSigning(Canonical.Scope.BODY);

        // Sign with the private key
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(kp.getPrivate());
        signer.update(toSign);
        byte[] rawSignature = signer.sign();
        assertThat(rawSignature).hasSize(64);

        // Wrap raw signature as VarSig
        VarSig varsig = VarSig.of(Algorithm.Sign.ED25519, rawSignature);

        // Build the real record with the actual signature
        Record record = Record.of(
                FrameRef.of(bodyCid),
                proto.bindings(),
                varsig
        );

        // Aggregate into a Frame
        Frame frame = Frame.of(body, List.of(record));
        assertThat(frame.records()).hasSize(1);
        assertThat(frame.bodyCID()).isEqualTo(bodyCid);

        // Verify the signature: re-encode the body-for-signing portion of the
        // record and check against the public key
        byte[] recoveredToSign = record.encodeBodyForSigning(Canonical.Scope.BODY);
        assertThat(recoveredToSign).containsExactly(toSign);

        Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(kp.getPublic());
        verifier.update(recoveredToSign);
        VarSig recoveredSig = record.varsig();
        assertThat(recoveredSig.algorithm()).isEqualTo(Algorithm.Sign.ED25519);
        assertThat(verifier.verify(recoveredSig.rawSig())).isTrue();

        // Round-trip the multikey through wire form and confirm
        MultiKey roundTrippedKey = MultiKey.decode(signerKey.encoded());
        assertThat(roundTrippedKey).isEqualTo(signerKey);
        assertThat(roundTrippedKey.algorithm()).isEqualTo(Algorithm.Sign.ED25519);
    }

    @Test
    @DisplayName("end-to-end: build archetypal manifest with ITEM_ID, FOLLOWS, ENDORSES")
    void endToEndManifest() throws Exception {
        ItemID iid = ItemID.fromString("my-document-item");
        ItemID parentVid = ItemID.fromString("v1");
        ItemID endorsedFrameCid = ItemID.fromString("frame-1");

        // Inception manifest (no FOLLOWS): body has ITEM_ID + ENDORSES
        Body inceptionBody = Body.of(ItemRef.of(DOCUMENT), List.of(
                Binding.ref(Manifest.ITEM_ID, iid),
                Binding.ref(Manifest.ENDORSES, endorsedFrameCid)
        ));
        Manifest inception = Manifest.of(inceptionBody);

        assertThat(inception.itemId()).isEqualTo(iid);
        assertThat(inception.parents()).isEmpty();
        assertThat(inception.endorses()).hasSize(1);
        assertThat(inception.versionId()).isEqualTo(inceptionBody.cid());
        assertThat(inception.isArchetypal()).isTrue();

        // V2 follows V1
        Body v2Body = Body.of(ItemRef.of(DOCUMENT), List.of(
                Binding.ref(Manifest.ITEM_ID, iid),
                Binding.ref(Manifest.FOLLOWS, parentVid),
                Binding.ref(Manifest.ENDORSES, endorsedFrameCid),
                Binding.ref(Manifest.ENDORSES, ItemID.fromString("frame-2"))
        ));
        Manifest v2 = Manifest.of(v2Body);

        assertThat(v2.itemId()).isEqualTo(iid);
        assertThat(v2.parents()).hasSize(1);
        assertThat(v2.endorses()).hasSize(2);

        // CBOR round-trip preserves identity
        Body decoded = Body.fromCborTree(v2Body.toCborTree(Canonical.Scope.BODY));
        assertThat(decoded.cid()).isEqualTo(v2Body.cid());
        Manifest reconstituted = Manifest.of(decoded);
        assertThat(reconstituted.itemId()).isEqualTo(iid);
        assertThat(reconstituted.parents()).hasSize(1);
        assertThat(reconstituted.endorses()).hasSize(2);
    }

    @Test
    @DisplayName("Frame and Manifest distinguish via ITEM_ID binding presence")
    void frameVsManifest() {
        ItemID iid = ItemID.fromString("doc-item");

        // Without ITEM_ID — propositional Frame
        Body propBody = Body.of(ItemRef.of(AUTHORED), List.of(
                Binding.ref(THEME, ItemID.fromString("hobbit"))
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

    /**
     * Extract the raw 32-byte Ed25519 public key from the X.509 SubjectPublicKeyInfo
     * encoding that JCA returns from {@code PublicKey.getEncoded()}.
     *
     * <p>The SPKI structure wraps the raw key in an ASN.1 OCTET STRING; BouncyCastle's
     * helper extracts it cleanly.
     */
    private static byte[] extractRawEd25519PublicKey(PublicKey pk) {
        SubjectPublicKeyInfo spki = SubjectPublicKeyInfo.getInstance(pk.getEncoded());
        return spki.getPublicKeyData().getBytes();
    }
}
