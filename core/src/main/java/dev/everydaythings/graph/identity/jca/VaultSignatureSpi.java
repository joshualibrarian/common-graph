package dev.everydaythings.graph.identity.jca;

import dev.everydaythings.graph.identity.VarSig;

import java.io.ByteArrayOutputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.SignatureSpi;

/**
 * JCA {@link SignatureSpi} that delegates {@code engineSign} to a
 * {@link dev.everydaythings.graph.identity.vault.Vault Vault}.
 *
 * <p>Each algorithm-specific subclass is registered with
 * {@link VaultProvider} via JCA's standard service map and looked up
 * by callers via
 * {@code Signature.getInstance(algorithmName, VaultProvider.NAME)}.
 *
 * <h2>Sign path</h2>
 *
 * <p>{@link #engineInitSign(PrivateKey)} accepts only
 * {@link VaultPrivateKey}.  Subsequent {@code engineUpdate} calls
 * accumulate the message bytes in an in-memory buffer; {@code engineSign}
 * hands those bytes to {@code Vault.sign(message, purpose)} and returns
 * the raw signature bytes (unwrapped from {@link VarSig}).
 *
 * <h2>Verify path</h2>
 *
 * <p>{@link #engineInitVerify(PublicKey)} delegates to the JDK's default
 * provider for the same algorithm — verification needs no special
 * Vault-backed machinery, just a regular public key.  This keeps the SPI
 * usable as a drop-in replacement when callers (notably JSSE) ask for a
 * Signature object and then use it for either direction.
 */
public abstract class VaultSignatureSpi extends SignatureSpi {

    private final ByteArrayOutputStream pendingSign = new ByteArrayOutputStream();
    private VaultPrivateKey vaultKey;
    private Signature verifyDelegate;

    /** JCA algorithm name this SPI handles — e.g., {@code "Ed25519"}. */
    protected abstract String algorithmName();

    @Override
    protected void engineInitSign(PrivateKey privateKey) throws InvalidKeyException {
        if (!(privateKey instanceof VaultPrivateKey vk)) {
            throw new InvalidKeyException(
                    VaultProvider.NAME + " Signature requires a VaultPrivateKey for signing; got "
                            + (privateKey == null ? "null" : privateKey.getClass().getName()));
        }
        if (!algorithmName().equals(vk.getAlgorithm())) {
            throw new InvalidKeyException(
                    "VaultPrivateKey algorithm " + vk.getAlgorithm()
                            + " does not match this SPI's " + algorithmName());
        }
        this.vaultKey = vk;
        this.verifyDelegate = null;
        this.pendingSign.reset();
    }

    @Override
    protected void engineInitVerify(PublicKey publicKey) throws InvalidKeyException {
        try {
            // Delegate to whichever provider holds the default algorithm impl
            // (typically the JDK's built-in).  Explicitly avoid recursing into
            // ourselves by NOT specifying our provider.
            this.verifyDelegate = Signature.getInstance(algorithmName());
        } catch (NoSuchAlgorithmException e) {
            throw new InvalidKeyException(
                    "No default JCA provider for " + algorithmName() + " verification", e);
        }
        this.verifyDelegate.initVerify(publicKey);
        this.vaultKey = null;
        this.pendingSign.reset();
    }

    @Override
    protected void engineUpdate(byte b) throws SignatureException {
        if (verifyDelegate != null) {
            verifyDelegate.update(b);
        } else {
            pendingSign.write(b);
        }
    }

    @Override
    protected void engineUpdate(byte[] data, int off, int len) throws SignatureException {
        if (verifyDelegate != null) {
            verifyDelegate.update(data, off, len);
        } else {
            pendingSign.write(data, off, len);
        }
    }

    @Override
    protected byte[] engineSign() throws SignatureException {
        if (vaultKey == null) {
            throw new SignatureException("Signature not initialized for signing");
        }
        VarSig sig;
        try {
            sig = vaultKey.vault().sign(pendingSign.toByteArray(), vaultKey.purpose());
        } catch (RuntimeException e) {
            throw new SignatureException("Vault signing failed", e);
        } finally {
            pendingSign.reset();
        }
        return sig.rawSig();
    }

    @Override
    protected boolean engineVerify(byte[] signature) throws SignatureException {
        if (verifyDelegate == null) {
            throw new SignatureException("Signature not initialized for verification");
        }
        return verifyDelegate.verify(signature);
    }

    // ==================================================================================
    // Deprecated parameter API — left as no-ops; modern callers use
    // engineSetParameter(AlgorithmParameterSpec) which we don't override either
    // because Ed25519 takes no parameters.
    // ==================================================================================

    @Override
    @SuppressWarnings("deprecation")
    protected void engineSetParameter(String param, Object value) {
        // no-op
    }

    @Override
    @SuppressWarnings("deprecation")
    protected Object engineGetParameter(String param) {
        return null;
    }

    // ==================================================================================
    // Algorithm-specific subclasses — one per algorithm we support.  JCA looks
    // these up by fully-qualified name from the entries VaultProvider puts in
    // its service map.
    // ==================================================================================

    /** Ed25519 over a Vault-backed signing key. */
    public static final class Ed25519 extends VaultSignatureSpi {
        @Override protected String algorithmName() { return "Ed25519"; }
    }
}
