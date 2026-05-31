package dev.everydaythings.graph.cryptography;

import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.GrammaticalFeature;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.LexicalVocabulary;
import dev.everydaythings.graph.PartOfSpeech;
import dev.everydaythings.graph.ThematicRole;

/**
 * Credential vocabulary — sememes for user-facing credentials stored in a
 * vault: passwords, usernames, time-based one-time passwords, recovery codes,
 * personal access tokens, and protocol-context qualifiers for keys bound to
 * external services (WebAuthn, SSH, GPG).
 *
 * <p>Sibling crypto-domain vocabularies:
 * <ul>
 *   <li>{@link IdentityVocabulary} — identities, key tracks, KEL events,
 *       pre-keys, current/next/retained qualifiers.</li>
 *   <li>{@link EncryptionVocabulary} — encryption-flow primitives and the
 *       Conversation per-peer DR state.</li>
 *   <li>{@link AlgorithmVocabulary} — the algorithm sememes themselves
 *       (Ed25519, X25519, AES-GCM) plus their metadata.</li>
 *   <li>{@link RecordVocabulary} — record acts (Created / Encrypted /
 *       Verified / etc.).</li>
 * </ul>
 *
 * <p>These sememes appear as compound-key heads and qualifiers on vault entry
 * bindings.  Examples:
 * <pre>
 * (USERNAME)              → "joshua@example.com"
 * (PASSWORD)              → "•••••••••"
 * (TOTP, SEED)            → 0xDEADBEEF...
 * (TOTP, DIGITS)           → 6
 * (TOTP, PERIOD)           → 30
 * (TOTP, ALGORITHM)        → @SHA256
 * (RECOVERY_CODE)          → ["code1", "code2", ...]
 * (PERSONAL_ACCESS_TOKEN)  → "ghp_..."
 * (SIGNING, SSH)           → SSH signing keypair
 * (SIGNING, WEBAUTHN)      → WebAuthn passkey
 * (KEY_AGREEMENT, GPG)     → GPG encryption sub-key
 * </pre>
 *
 * <p>The protocol qualifiers ({@link Ssh}, {@link Gpg}, {@link Webauthn})
 * pair with the existing {@code SIGNING} / {@code KEY_AGREEMENT} /
 * {@code ENCRYPTION} purpose sememes from {@link IdentityVocabulary} to
 * mark keys whose use is bound to a specific external protocol.  The
 * cryptographic shape of the key is the purpose; the protocol is just
 * context.
 */
public final class CredentialVocabulary {

    private CredentialVocabulary() {}

    // ==================================================================================
    // Bearer credential role sememes
    // ==================================================================================

    /**
     * USERNAME — the account identifier a user presents to a service when
     * authenticating.  Often an email address, sometimes a chosen handle.
     * The field's value is the username string itself.
     */
    @Seed.Item(key = Username.KEY)
    @Seed.Gloss(english =
            "the account identifier a user presents when authenticating to a service")
    public static final class Username {
        public static final String KEY = "cg.role:username";
        private Username() {}

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY,
                                    qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "username";
    }

    /**
     * PASSWORD — a shared secret presented alongside a username for
     * authentication.  Stored encrypted at rest by the vault implementation;
     * revealed only after the vault is unlocked.
     */
    @Seed.Item(key = Password.KEY)
    @Seed.Gloss(english =
            "a shared secret presented alongside a username to authenticate to a service")
    public static final class Password {
        public static final String KEY = "cg.role:password";
        private Password() {}

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY,
                                    qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "password";
    }

    /**
     * PERSONAL_ACCESS_TOKEN — an opaque string credential issued by a service
     * for programmatic access, equivalent in usage to a password but typically
     * scoped, revocable independently of the account password, and longer-lived
     * than a session token.  Examples: GitHub PAT, GitLab token, Stripe API
     * key.
     */
    @Seed.Item(key = PersonalAccessToken.KEY)
    @Seed.Gloss(english =
            "an opaque string credential issued by a service for programmatic access; "
                    + "scoped and independently revocable; examples include GitHub PATs and "
                    + "service API keys")
    public static final class PersonalAccessToken {
        public static final String KEY = "cg.role:personal-access-token";
        private PersonalAccessToken() {}
    }

    /**
     * RECOVERY_CODE — a single-use backup code issued by a service for
     * account recovery when other authentication factors are unavailable.
     * Typically presented as a list of 8-12 codes generated at account-setup
     * time; consumed one per recovery event.
     *
     * <p>An {@code Authentication} entry typically carries a list of these,
     * with each code being one entry in the list.
     */
    @Seed.Item(key = RecoveryCode.KEY)
    @Seed.Gloss(english =
            "a single-use backup code for account recovery when other factors are unavailable")
    public static final class RecoveryCode {
        public static final String KEY = "cg.role:recovery-code";
        private RecoveryCode() {}
    }

    // ==================================================================================
    // TOTP — time-based one-time password configuration
    // ==================================================================================

    /**
     * TOTP — the compound-key head for time-based one-time-password
     * configuration on an authentication entry.  Qualifiers on TOTP bindings
     * carry the parameters needed to compute codes per RFC 6238:
     *
     * <pre>
     * (TOTP, SEED)      → byte[]           # the shared HMAC key
     * (TOTP, DIGITS)    → int (6 or 8)     # number of decimal digits per code
     * (TOTP, PERIOD)    → int (default 30) # seconds per code window
     * (TOTP, ALGORITHM) → @SHA1/SHA256/... # HMAC hash algorithm
     * </pre>
     *
     * <p>The vault stores the seed as bearer material; code computation is
     * performed by the caller (HMAC + dynamic truncation per RFC 4226) so
     * the vault doesn't need a per-code roundtrip.  Hardware-protected TOTP
     * (where the seed lives in a token and the vault exposes an HMAC
     * operation) is a future extension; the binding shape stays the same.
     */
    @Seed.Item(key = Totp.KEY)
    @Seed.Gloss(english =
            "time-based one-time password (RFC 6238): the compound-key head for TOTP "
                    + "configuration; qualifiers carry seed, digits, period, and algorithm")
    public static final class Totp {
        public static final String KEY = "cg.role:totp";
        private Totp() {}

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY,
                                    qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "TOTP";
    }

    /**
     * SEED — cryptographic seed bytes; the secret input to a deterministic
     * generator.  Used as a qualifier on TOTP bindings to name the HMAC key
     * bytes: {@code (TOTP, SEED) → byte[]}.  Generic enough to apply
     * elsewhere (HMAC keys, KDF inputs, deterministic key-pair seeds), so
     * declared at role-level rather than scoped to TOTP.
     */
    @Seed.Item(key = CryptoSeed.KEY)
    @Seed.Gloss(english =
            "cryptographic seed bytes — the secret input to a deterministic generator")
    public static final class CryptoSeed {
        public static final String KEY = "cg.role:seed";
        private CryptoSeed() {}
    }

    /**
     * DIGITS — number of decimal digits in a generated code.  Used on TOTP
     * bindings: {@code (TOTP, DIGITS) → 6 | 8}.  Default per RFC 6238 is 6;
     * 8-digit codes appear in some banking and corporate deployments.
     */
    @Seed.Item(key = Digits.KEY)
    @Seed.Gloss(english =
            "number of decimal digits in a generated code")
    public static final class Digits {
        public static final String KEY = "cg.role:digits";
        private Digits() {}
    }

    /**
     * PERIOD — duration in seconds of a single TOTP code's validity window.
     * Used on TOTP bindings: {@code (TOTP, PERIOD) → 30}.  Default per RFC
     * 6238 is 30 seconds; some deployments use 60.
     */
    @Seed.Item(key = Period.KEY)
    @Seed.Gloss(english =
            "duration in seconds of a single TOTP code's validity window")
    public static final class Period {
        public static final String KEY = "cg.role:period";
        private Period() {}
    }

    // ==================================================================================
    // Protocol-context qualifiers on keypair bindings
    // ==================================================================================

    /**
     * WEBAUTHN — protocol-context qualifier marking a signing keypair bound
     * to a WebAuthn / FIDO2 / passkey registration with a relying party.
     * Used on Authentication entry key bindings:
     * {@code (SIGNING, WEBAUTHN) → signing keypair}.
     *
     * <p>The keypair is cryptographically a regular signing keypair (most
     * commonly Ed25519 or ECDSA P-256); the WEBAUTHN qualifier marks its
     * protocol slot.  The Authentication entry's theme identifies which
     * relying party the registration is with; this qualifier marks the slot
     * within the entry.
     */
    @Seed.Item(key = Webauthn.KEY)
    @Seed.Gloss(english =
            "WebAuthn / FIDO2 / passkey — qualifier marking a signing keypair bound to a "
                    + "relying-party registration via the WebAuthn protocol")
    public static final class Webauthn {
        public static final String KEY = "cg.role:webauthn";
        private Webauthn() {}
    }

    /**
     * SSH — protocol-context qualifier marking a keypair used with the SSH
     * protocol.  Used on Authentication entry key bindings:
     * {@code (SIGNING, SSH) → ssh signing keypair}.
     *
     * <p>SSH uses Ed25519, RSA, or ECDSA signing keys for authentication and,
     * via the {@code ssh-rsa-cert-v01@openssh.com} family, can also sign
     * arbitrary data.  The SSH qualifier marks the keypair's protocol slot.
     */
    @Seed.Item(key = Ssh.KEY)
    @Seed.Gloss(english =
            "SSH — qualifier marking a keypair used for SSH protocol authentication or signing")
    public static final class Ssh {
        public static final String KEY = "cg.role:ssh";
        private Ssh() {}
    }

    /**
     * GPG — protocol-context qualifier marking a keypair used with the
     * OpenPGP / GnuPG protocol.  Used on Authentication entry key bindings:
     *
     * <pre>
     * (SIGNING, GPG)        → GPG signing sub-key
     * (KEY_AGREEMENT, GPG)  → GPG encryption sub-key
     * (ENCRYPTION, GPG)     → GPG encryption sub-key (alternate purpose tag)
     * </pre>
     *
     * <p>OpenPGP traditionally separates a primary signing key from one or
     * more encryption sub-keys; this qualifier marks the protocol context
     * regardless of which sub-key role the entry holds.
     */
    @Seed.Item(key = Gpg.KEY)
    @Seed.Gloss(english =
            "GPG / OpenPGP — qualifier marking a keypair used with the OpenPGP protocol")
    public static final class Gpg {
        public static final String KEY = "cg.role:gpg";
        private Gpg() {}
    }
}
