package dev.everydaythings.graph.cryptography.jca;

import java.security.Provider;
import java.security.Security;

/**
 * JCA {@link Provider} that exposes {@link dev.everydaythings.graph.cryptography.vault.Vault
 * Vault}-backed signing operations through the standard Java security
 * APIs.  Once installed, any code that asks for
 * {@code Signature.getInstance(algorithmName, VaultProvider.NAME)} gets a
 * Signature object whose {@code sign()} routes through the Vault — the
 * cleartext private key never leaves the Vault's process boundary.
 *
 * <p>Algorithms supported (v1):
 * <ul>
 *   <li>{@code Ed25519} — uses the Vault's signing-purpose key by default
 *       (caller controls via the {@link VaultPrivateKey} they pass to
 *       {@code initSign}).</li>
 * </ul>
 *
 * <p>Other algorithms (ECDSA, RSA, etc.) land alongside as the Vault gains
 * the matching key tracks.
 *
 * <h2>Usage</h2>
 *
 * <pre>
 * VaultProvider.install();                                  // idempotent global registration
 * Vault vault = InMemoryVault.generate();
 * vault.incept(ItemRef.iid(Signing.KEY));
 *
 * Signature sig = Signature.getInstance("Ed25519", VaultProvider.NAME);
 * sig.initSign(VaultPrivateKey.signing(vault));
 * sig.update(message);
 * byte[] signatureBytes = sig.sign();                       // signed via Vault
 *
 * // Verify with the standard JDK provider against vault.signingPublicKey():
 * Signature verify = Signature.getInstance("Ed25519");
 * verify.initVerify(vault.signingPublicKey().get().publicKey());
 * verify.update(message);
 * verify.verify(signatureBytes);                            // true
 * </pre>
 *
 * <h2>Why a separate provider name</h2>
 *
 * <p>We deliberately don't shadow the JDK's default Ed25519 implementation
 * — registering as a global Ed25519 would surprise callers who expect the
 * default to handle ordinary JDK private keys.  Opting into Vault delegation
 * is explicit: name our provider when you want it.
 */
public final class VaultProvider extends Provider {

    /** The provider's JCA-registered name. */
    public static final String NAME = "CG-Vault";

    private static final String VERSION = "1.0";
    private static final String INFO = "Common Graph Vault-delegated signing provider";

    public VaultProvider() {
        super(NAME, VERSION, INFO);
        put("Signature.Ed25519", VaultSignatureSpi.Ed25519.class.getName());
    }

    /**
     * Register this provider with the JCA framework.  Idempotent — calling
     * twice does not register two copies.
     */
    public static synchronized void install() {
        if (Security.getProvider(NAME) == null) {
            Security.addProvider(new VaultProvider());
        }
    }

    /**
     * Remove this provider from the JCA framework.  Mostly useful for tests
     * that need a clean teardown.
     */
    public static synchronized void uninstall() {
        Security.removeProvider(NAME);
    }
}
