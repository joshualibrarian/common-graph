package dev.everydaythings.graph.cryptography.vault;

import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.cryptography.CredentialVocabulary.CryptoSeed;
import dev.everydaythings.graph.cryptography.CredentialVocabulary.Digits;
import dev.everydaythings.graph.cryptography.CredentialVocabulary.Gpg;
import dev.everydaythings.graph.cryptography.CredentialVocabulary.Password;
import dev.everydaythings.graph.cryptography.CredentialVocabulary.PersonalAccessToken;
import dev.everydaythings.graph.cryptography.CredentialVocabulary.Period;
import dev.everydaythings.graph.cryptography.CredentialVocabulary.Ssh;
import dev.everydaythings.graph.cryptography.CredentialVocabulary.Totp;
import dev.everydaythings.graph.cryptography.CredentialVocabulary.Username;
import dev.everydaythings.graph.cryptography.CredentialVocabulary.Webauthn;
import dev.everydaythings.graph.cryptography.IdentityVocabulary.Encryption;
import dev.everydaythings.graph.cryptography.IdentityVocabulary.KeyAgreement;
import dev.everydaythings.graph.cryptography.IdentityVocabulary.Signing;
import dev.everydaythings.graph.cryptography.algorithm.Algorithm;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.value.identifier.URL;

import java.util.ArrayList;
import java.util.List;

/**
 * Authentication — a vault entry holding the credentials for an account at
 * an external service: username, password, time-based one-time-password
 * configuration, recovery codes, personal access tokens, and any keypairs
 * registered with the service (WebAuthn passkey, SSH key, GPG sub-keys).
 *
 * <p>One entry per (service, account).  The entry's {@code theme} is the
 * service's IID (or {@code null} for an account not associated with any
 * particular service in the graph yet).
 *
 * <h2>Binding schema</h2>
 *
 * <pre>
 * Bearer credentials:
 *   (USERNAME)               → String
 *   (PASSWORD)               → String
 *   (URL)                    → String
 *
 * TOTP configuration (RFC 6238):
 *   (TOTP, SEED)             → byte[]
 *   (TOTP, DIGITS)           → int (6 or 8)
 *   (TOTP, PERIOD)           → int (seconds, default 30)
 *   (TOTP, ALGORITHM)        → @SHA1/SHA256/...
 *
 * Service-issued credentials:
 *   (RECOVERY_CODE)          → String (multiset, one per code)
 *   (PERSONAL_ACCESS_TOKEN)  → String
 *
 * Protocol-bound keypairs (any subset):
 *   (SIGNING, WEBAUTHN)      → SigningKey      # passkey
 *   (SIGNING, SSH)           → SigningKey      # SSH signing/auth key
 *   (SIGNING, GPG)           → SigningKey      # GPG signing sub-key
 *   (KEY_AGREEMENT, GPG)     → KeyAgreementKey # GPG encryption sub-key
 *   (ENCRYPTION, GPG)        → KeyAgreementKey # alternate purpose tag for GPG
 * </pre>
 *
 * <p>All fields are optional.  An entry might have only a username and
 * password; another might be passwordless with just a WebAuthn passkey.
 * Beyond declared fields the entry can carry arbitrary free-form bindings:
 * security-question answers, license keys, free-text notes, attachments.
 * Read via {@link #binding} on the inherited {@link VaultEntry} API.
 */
@Seed.Item(key = Authentication.KEY)
@Seed.Gloss(english =
        "credentials for an account at a service: username, password, TOTP, "
                + "recovery codes, access tokens, and any keypairs registered with "
                + "the service (passkey, SSH, GPG)")
public class Authentication extends VaultEntry {

    /** Canonical key for the Authentication vault-entry archetype. */
    public static final String KEY = "cg.vault:authentication";

    // ==================================================================================
    // Bearer credentials
    // ==================================================================================

    @Seed.Property(role = Username.KEY)
    public String username;

    @Seed.Property(role = Password.KEY)
    public String password;

    @Seed.Property(role = URL.KEY)
    public String url;

    @Seed.Property(role = PersonalAccessToken.KEY)
    public String personalAccessToken;

    /**
     * Service-issued recovery codes, single-use.  Populated by the vault
     * from {@code (RECOVERY_CODE)} bindings (multiset).
     */
    public List<String> recoveryCodes = new ArrayList<>();

    // ==================================================================================
    // TOTP configuration
    // ==================================================================================

    @Seed.Property(role = Totp.KEY, qualifiers = {CryptoSeed.KEY})
    public byte[] totpSeed;

    @Seed.Property(role = Totp.KEY, qualifiers = {Digits.KEY})
    public int totpDigits;

    @Seed.Property(role = Totp.KEY, qualifiers = {Period.KEY})
    public int totpPeriod;

    @Seed.Property(role = Totp.KEY, qualifiers = {Algorithm.KEY})
    public ItemRef totpAlgorithm;

    // ==================================================================================
    // Protocol-bound keypairs (optional, any subset)
    // ==================================================================================

    @Seed.Property(role = Signing.KEY, qualifiers = {Webauthn.KEY})
    public SigningKey passkey;

    @Seed.Property(role = Signing.KEY, qualifiers = {Ssh.KEY})
    public SigningKey sshSigning;

    @Seed.Property(role = Signing.KEY, qualifiers = {Gpg.KEY})
    public SigningKey gpgSigning;

    @Seed.Property(role = KeyAgreement.KEY, qualifiers = {Gpg.KEY})
    public KeyAgreementKey gpgKeyAgreement;

    @Seed.Property(role = Encryption.KEY, qualifiers = {Gpg.KEY})
    public KeyAgreementKey gpgEncryption;

    // ==================================================================================
    // Construction
    // ==================================================================================

    /**
     * Construct an Authentication entry.
     *
     * @param id      vault-assigned stable identifier
     * @param service the service IID this account is with, or {@code null}
     *                if the service is not (yet) named in the graph
     */
    public Authentication(EntryId id, ItemRef service) {
        super(id, service);
    }

    @Override
    public ItemRef archetype() {
        return ItemRef.iid(KEY);
    }
}
