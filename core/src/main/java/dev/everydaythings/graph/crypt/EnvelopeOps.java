package dev.everydaythings.graph.crypt;

public final class EnvelopeOps {
//    public static EncryptedEnvelope encrypt(
//            byte[] plaintext, byte[] aad,
//            AeadAlg aead, KemAlg kem,
//            Signer signer,              // has SIGN private key + GraphPublicKey
//            List<SigningPublicKey> recipients,   // recipients’ public keys (AGREE or TRANSPORT)
//            Provider providerOpt        // null unless you need BC
//    ) {
//        // 1) Generate a random CEK of aead.keyLen()
//        byte[] cek = Crypto.rand(aead.keyLen());
//
//        // 2) Build recipient entries
//        var recs = new java.util.ArrayList<EncryptedEnvelope.Recipient>(recipients.size());
//        byte[] info = null; // optional context for HKDF (include iid/channel if you want)
//        for (GraphPublicKey r : recipients) {
//            switch (kem) {
//                case ECDH_ES_HKDF_256 -> {
//                    // epk: sender ephemeral X25519 or EC(P-256) public (SPKI)
//                    EphemeralKeyPair eph = EphemeralKeyPair.forRecipient(r, providerOpt); // curve matches recipient
//                    PublicKey rPk = SignAlg.fromCose(r.getAlgorithm()).toPublicKey(r.getPublicKeySpki(), providerOpt);
//                    byte[] z = KemAlg.ECDH_ES_HKDF_256.deriveSecret(eph.privateKey(), rPk, providerOpt);
//                    byte[] derivedCek = KdfAlg.HKDF_SHA256.hkdf(z, /*salt*/null, info, aead.keyLen());
//                    // For multi‑recipient, either re‑derive per recipient (fine), or wrap a single CEK:
//                    // Here we use per‑recipient derivedCek == cek (to keep it simple, pick one approach and stick to it).
//                    if (!java.util.Arrays.equals(derivedCek, cek)) {
//                        // If you prefer “single CEK”, then encrypt with CEK and store no wrappedCek; each recipient derives CEK independently → keep 'cek'.
//                    }
//                    recs.add(EncryptedEnvelope.Recipient.builder()
//                            .kid(r.getKid())
//                            .epkSpki(eph.publicSpki())
//                            .wrappedCek(null)            // ECDH-ES derive, no wrap
//                            .build());
//                }
//                case RSA_OAEP_256 -> {
//                    PublicKey rPk = SignAlg.PS256.toPublicKey(r.getPublicKeySpki(), providerOpt); // keyType RSA
//                    byte[] wrapped = Algorithm.KemAlg.RSA_OAEP_256.wrapCek(rPk, cek, providerOpt);
//                    recs.add(EncryptedEnvelope.Recipient.builder()
//                            .kid(r.getKid())
//                            .epkSpki(null)
//                            .wrappedCek(wrapped)
//                            .build());
//                }
//            }
//        }
//
//        // 3) AEAD encrypt payload
//        byte[] nonce12 = Crypto.rand(12);
//        byte[] ciphertext = aead.encrypt(cek, nonce12, aad, plaintext, providerOpt);
//
//        // 4) Build unsigned envelope (no senderSig yet)
//        var env = EncryptedEnvelope.builder()
//                .kemAlg(kem.cose())
//                .aeadAlg(aead.cose())
//                .recipients(recs)
//                .nonce12(nonce12)
//                .aad(aad)
//                .ciphertext(ciphertext)
//                .senderAlg(signer.alg().cose)
//                .senderKid(signer.publicKey().getKid())
//                .subjectIid(/*iid bytes*/ null)
//                .senderSig(new byte[0]) // fill below
//                .build();
//
//        // 5) Sign over bodyToSign() (header + recipients + nonce + aad + ciphertext)
//        var sig = signer.sign(env);
//        return env.toBuilder().senderSig(sig.getSig()).build();
//    }
}
