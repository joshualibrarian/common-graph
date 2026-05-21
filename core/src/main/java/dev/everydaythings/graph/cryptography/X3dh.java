package dev.everydaythings.graph.cryptography;

import dev.everydaythings.graph.cryptography.algorithm.Kdf;
import dev.everydaythings.graph.cryptography.algorithm.KeyAgreement;
import dev.everydaythings.graph.cryptography.vault.Vault;

import java.security.KeyPair;
import java.security.PublicKey;

/**
 * X3DH initial-key-agreement (Signal X3DH).
 *
 * <p>Produces a 32-byte shared secret SK that both the initiator and the
 * responder can compute independently after the initiator publishes their
 * ephemeral pubkey.  SK seeds the {@link DoubleRatchet} root key.
 *
 * <h2>Three or four DHs</h2>
 *
 * <pre>
 *   DH1 = DH(IK_a, SPK_b)   binds the initiator's identity to the responder's pre-key
 *   DH2 = DH(EK_a, IK_b)    binds the initiator's freshness to the responder's identity
 *   DH3 = DH(EK_a, SPK_b)   binds the initiator's freshness to the responder's pre-key
 *   DH4 = DH(EK_a, OPK_b)   (optional) binds the initiator's freshness to a single-use OTPK
 *   SK  = HKDF(salt=0^32, ikm=F || DH1 || DH2 || DH3 [|| DH4], info="cg.x3dh", length=32)
 * </pre>
 *
 * <p>F is 32 bytes of 0xFF, prepended to differentiate this protocol from
 * the underlying curve's hash domain (per the Signal spec).
 *
 * <p>DH4 with a one-time pre-key (OTPK) bumps forward secrecy on the first
 * message: even if the signed pre-key is later compromised, the OTPK's
 * private side is destroyed on first use.  Pass {@code null} for the OTPK
 * arg to fall back to plain three-DH.
 *
 * <p>Identity-key (IK) DHs use the {@link Vault} (private side never
 * leaves).  Ephemeral DHs use the algorithm primitive directly because the
 * ephemeral keypair is short-lived and in-memory.
 */
public final class X3dh {

    private static final byte[] X3DH_INFO = "cg.x3dh".getBytes();
    private static final int SK_LEN = 32;
    private static final KeyAgreement.X25519 X25519 = KeyAgreement.X25519.builtin();
    private static final Kdf.HkdfSha256       HKDF   = Kdf.HkdfSha256.builtin();

    private X3dh() {}

    /**
     * Initiator's path.  Uses the initiator's vault for the IK DH and the
     * provided ephemeral keypair for the EK DHs.
     *
     * @param initiatorVault   the initiator's Vault (IK private stays inside)
     * @param initiatorEphemeral the initiator's freshly-generated ephemeral keypair
     * @param responderIkPub   responder's identity (X25519) public key
     * @param responderSpkPub  responder's signed pre-key public key
     * @return 32-byte SK
     */
    public static byte[] initiator(Vault initiatorVault,
                                   KeyPair initiatorEphemeral,
                                   PublicKey responderIkPub,
                                   PublicKey responderSpkPub) {
        return initiator(initiatorVault, initiatorEphemeral, responderIkPub, responderSpkPub, null);
    }

    /**
     * Initiator's path with an optional one-time pre-key.  When
     * {@code responderOtpkPub} is non-null, the fourth DH adds extra forward
     * secrecy.  When null, this is equivalent to the three-DH overload.
     */
    public static byte[] initiator(Vault initiatorVault,
                                   KeyPair initiatorEphemeral,
                                   PublicKey responderIkPub,
                                   PublicKey responderSpkPub,
                                   PublicKey responderOtpkPub) {
        byte[] dh1 = initiatorVault.agree(responderSpkPub);                                  // IK_a × SPK_b
        byte[] dh2 = X25519.agree(initiatorEphemeral.getPrivate(), responderIkPub);          // EK_a × IK_b
        byte[] dh3 = X25519.agree(initiatorEphemeral.getPrivate(), responderSpkPub);         // EK_a × SPK_b
        byte[] dh4 = responderOtpkPub == null
                ? null
                : X25519.agree(initiatorEphemeral.getPrivate(), responderOtpkPub);          // EK_a × OPK_b
        return kdf(dh1, dh2, dh3, dh4);
    }

    /**
     * Responder's path.  Uses the responder's vault for the IK DH and the
     * responder's signed-pre-key keypair for the SPK DHs.
     *
     * @param responderVault   the responder's Vault (IK private stays inside)
     * @param responderSpk     the responder's signed-pre-key keypair
     * @param initiatorIkPub   initiator's identity (X25519) public key
     * @param initiatorEkPub   initiator's ephemeral public key
     * @return 32-byte SK (matches the initiator's)
     */
    public static byte[] responder(Vault responderVault,
                                   KeyPair responderSpk,
                                   PublicKey initiatorIkPub,
                                   PublicKey initiatorEkPub) {
        return responder(responderVault, responderSpk, null, initiatorIkPub, initiatorEkPub);
    }

    /**
     * Responder's path with an optional one-time pre-key keypair.  When
     * {@code responderOtpk} is non-null, the fourth DH is included.  When
     * null, this is equivalent to the three-DH overload.
     */
    public static byte[] responder(Vault responderVault,
                                   KeyPair responderSpk,
                                   KeyPair responderOtpk,
                                   PublicKey initiatorIkPub,
                                   PublicKey initiatorEkPub) {
        byte[] dh1 = X25519.agree(responderSpk.getPrivate(), initiatorIkPub);                // SPK_b × IK_a
        byte[] dh2 = responderVault.agree(initiatorEkPub);                                   // IK_b × EK_a
        byte[] dh3 = X25519.agree(responderSpk.getPrivate(), initiatorEkPub);                // SPK_b × EK_a
        byte[] dh4 = responderOtpk == null
                ? null
                : X25519.agree(responderOtpk.getPrivate(), initiatorEkPub);                 // OPK_b × EK_a
        return kdf(dh1, dh2, dh3, dh4);
    }

    private static byte[] kdf(byte[] dh1, byte[] dh2, byte[] dh3, byte[] dh4) {
        int dh4Len = dh4 == null ? 0 : dh4.length;
        byte[] ikm = new byte[32 + dh1.length + dh2.length + dh3.length + dh4Len];
        for (int i = 0; i < 32; i++) ikm[i] = (byte) 0xFF;
        int off = 32;
        System.arraycopy(dh1, 0, ikm, off, dh1.length); off += dh1.length;
        System.arraycopy(dh2, 0, ikm, off, dh2.length); off += dh2.length;
        System.arraycopy(dh3, 0, ikm, off, dh3.length); off += dh3.length;
        if (dh4 != null) {
            System.arraycopy(dh4, 0, ikm, off, dh4.length);
        }
        byte[] salt = new byte[SK_LEN];
        return HKDF.derive(ikm, salt, X3DH_INFO, SK_LEN);
    }
}
