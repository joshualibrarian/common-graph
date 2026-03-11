package dev.everydaythings.graph.crypt;

import com.upokecenter.cbor.CBORObject;
import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.item.component.FrameEntry;
import dev.everydaythings.graph.item.component.Type;
import dev.everydaythings.graph.item.id.ContentID;
import dev.everydaythings.graph.library.ItemStore;
import dev.everydaythings.graph.runtime.Librarian;
import dev.everydaythings.graph.trust.EncryptionPublicKey;
import dev.everydaythings.graph.vault.InMemoryVault;
import dev.everydaythings.graph.vault.Vault;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.everydaythings.graph.policy.PolicySet;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 3 integration tests: frame-level encryption through the commit/hydrate cycle.
 *
 * <p>Tests that frames are encrypted during commit when an EncryptionContext is provided,
 * that the manifest carries dual CIDs (plaintext snapshotCid + encryptedCid), and that
 * hydration transparently decrypts the content back.
 */
@DisplayName("Frame-Level Encryption")
class FrameEncryptionTest {

    private Librarian librarian;
    private EncryptionPublicKey libEncKey;

    @BeforeEach
    void setUp() {
        librarian = Librarian.createInMemory();
        // Librarian generates encryption keys on boot
        libEncKey = librarian.encryptionPublicKey();
    }

    /**
     * A minimal test component for exercising encryption on @Frame fields.
     * Librarian itself has @Frame fields (library, types, keys, certs, vault),
     * so we test encryption using the Librarian as our subject.
     */

    @Nested
    @DisplayName("Commit with Encryption")
    class CommitWithEncryption {

        @Test
        @DisplayName("librarian encrypted commit produces encryptedCid on frame entries")
        void encryptedCommitSetsEncryptedCid() {
            assertThat(libEncKey)
                    .as("Librarian should have an encryption key")
                    .isNotNull();

            // Librarian has @Frame fields (keys, certs, types, library, vault)
            // Edit and re-commit with encryption
            librarian.edit();
            EncryptionContext ctx = EncryptionContext.allFrames(List.of(libEncKey));
            ContentID vid = librarian.commit(librarian, ctx);
            assertThat(vid).isNotNull();

            // Check that at least one non-local frame entry has an encryptedCid
            // (localOnly frames like vault should NOT be encrypted)
            boolean anyEncrypted = librarian.content().stream()
                    .filter(e -> e.payload() != null)
                    .anyMatch(e -> e.payload().isEncrypted());
            assertThat(anyEncrypted)
                    .as("At least one frame should be encrypted")
                    .isTrue();
        }

        @Test
        @DisplayName("encrypted frame has distinct snapshotCid and encryptedCid")
        void dualCids() {
            assertThat(libEncKey).isNotNull();

            librarian.edit();
            EncryptionContext ctx = EncryptionContext.allFrames(List.of(libEncKey));
            librarian.commit(librarian, ctx);

            // Find an encrypted frame entry
            Optional<FrameEntry> encrypted = librarian.content().stream()
                    .filter(e -> e.payload() != null && e.payload().isEncrypted())
                    .findFirst();

            assertThat(encrypted).isPresent();
            FrameEntry entry = encrypted.get();
            assertThat(entry.payload().snapshotCid())
                    .as("snapshotCid should be set (plaintext hash)")
                    .isNotNull();
            assertThat(entry.payload().encryptedCid())
                    .as("encryptedCid should be set (envelope hash)")
                    .isNotNull();
            assertThat(entry.payload().snapshotCid())
                    .as("snapshotCid and encryptedCid should differ")
                    .isNotEqualTo(entry.payload().encryptedCid());
        }

        @Test
        @DisplayName("cleartext commit has no encryptedCid")
        void cleartextCommitNoEncryptedCid() {
            // Librarian's initial commit is cleartext
            boolean anyEncrypted = librarian.content().stream()
                    .filter(e -> e.payload() != null)
                    .anyMatch(e -> e.payload().isEncrypted());
            assertThat(anyEncrypted)
                    .as("No frames should be encrypted without EncryptionContext")
                    .isFalse();
        }

        @Test
        @DisplayName("EncryptionContext.NONE produces no encryption")
        void noneContextNoEncryption() {
            librarian.edit();
            librarian.commit(librarian, EncryptionContext.NONE);

            boolean anyEncrypted = librarian.content().stream()
                    .filter(e -> e.payload() != null)
                    .anyMatch(e -> e.payload().isEncrypted());
            assertThat(anyEncrypted)
                    .as("NONE context should produce no encryption")
                    .isFalse();
        }

        @Test
        @DisplayName("localOnly frames are NOT encrypted")
        void localOnlyNotEncrypted() {
            assertThat(libEncKey).isNotNull();

            librarian.edit();
            EncryptionContext ctx = EncryptionContext.allFrames(List.of(libEncKey));
            librarian.commit(librarian, ctx);

            // vault is localOnly — should not be encrypted even with allFrames
            Optional<FrameEntry> vaultEntry = librarian.content().stream()
                    .filter(e -> "vault".equals(e.alias()))
                    .findFirst();

            // If vault has a payload, it should not be encrypted
            vaultEntry.ifPresent(entry -> {
                if (entry.payload() != null) {
                    assertThat(entry.payload().isEncrypted())
                            .as("localOnly frames should not be encrypted")
                            .isFalse();
                }
            });
        }
    }

    @Nested
    @DisplayName("Encrypted Content in Object Store")
    class ObjectStoreContent {

        @Test
        @DisplayName("encrypted envelope is stored at encryptedCid")
        void envelopeStoredAtEncryptedCid() {
            assertThat(libEncKey).isNotNull();

            librarian.edit();
            EncryptionContext ctx = EncryptionContext.allFrames(List.of(libEncKey));
            librarian.commit(librarian, ctx);

            // Find an encrypted entry
            Optional<FrameEntry> encrypted = librarian.content().stream()
                    .filter(e -> e.payload() != null && e.payload().isEncrypted())
                    .findFirst();
            assertThat(encrypted).isPresent();

            ContentID encCid = encrypted.get().payload().encryptedCid();
            ItemStore store = librarian.library().primaryStore().orElseThrow();

            // The object store should have content at the encryptedCid
            Optional<byte[]> storedBytes = store.content(encCid);
            assertThat(storedBytes)
                    .as("Encrypted envelope should be stored at encryptedCid")
                    .isPresent();

            // The stored bytes should be a valid Tag 10 CBOR envelope
            CBORObject cbor = CBORObject.DecodeFromBytes(storedBytes.get());
            assertThat(cbor.HasMostOuterTag(Canonical.CgTag.ENCRYPTED))
                    .as("Stored content should be a Tag 10 encrypted envelope")
                    .isTrue();
        }

        @Test
        @DisplayName("plaintext is NOT stored at snapshotCid when encrypted")
        void plaintextNotStoredWhenEncrypted() {
            assertThat(libEncKey).isNotNull();

            librarian.edit();
            EncryptionContext ctx = EncryptionContext.allFrames(List.of(libEncKey));
            librarian.commit(librarian, ctx);

            // Find an encrypted entry
            Optional<FrameEntry> encrypted = librarian.content().stream()
                    .filter(e -> e.payload() != null && e.payload().isEncrypted())
                    .findFirst();
            assertThat(encrypted).isPresent();

            ContentID snapshotCid = encrypted.get().payload().snapshotCid();
            ItemStore store = librarian.library().primaryStore().orElseThrow();

            // The plaintext should NOT be stored — only the encrypted envelope is stored
            Optional<byte[]> plaintextBytes = store.content(snapshotCid);
            assertThat(plaintextBytes)
                    .as("Plaintext should not be stored when encryption is active")
                    .isEmpty();
        }
    }

    @Nested
    @DisplayName("Multi-Recipient Frames")
    class MultiRecipient {

        @Test
        @DisplayName("frame encrypted to multiple recipients")
        void multipleRecipients() {
            // Create a second vault/key
            Vault bobVault = InMemoryVault.create();
            bobVault.generateEncryptionKey();
            EncryptionPublicKey bobEncKey = EncryptionPublicKey.builder()
                    .jcaPublicKey(bobVault.encryptionPublicKey().orElseThrow())
                    .build();

            librarian.edit();
            EncryptionContext ctx = EncryptionContext.allFrames(
                    List.of(libEncKey, bobEncKey));
            librarian.commit(librarian, ctx);

            // Find an encrypted entry and verify the envelope has 2 recipients
            Optional<FrameEntry> encrypted = librarian.content().stream()
                    .filter(e -> e.payload() != null && e.payload().isEncrypted())
                    .findFirst();
            assertThat(encrypted).isPresent();

            ContentID encCid = encrypted.get().payload().encryptedCid();
            ItemStore store = librarian.library().primaryStore().orElseThrow();
            byte[] envelopeBytes = store.content(encCid).orElseThrow();

            CBORObject cbor = CBORObject.DecodeFromBytes(envelopeBytes);
            EncryptedEnvelope envelope = EncryptedEnvelope.fromCborTree(cbor);
            assertThat(envelope.recipients())
                    .as("Envelope should have 2 recipients")
                    .hasSize(2);
        }
    }

    @Nested
    @DisplayName("EncryptionPolicy on Frame Config")
    class EncryptionPolicyOnConfig {

        @Test
        @DisplayName("EncryptionPolicy survives commit cycle (config carry-forward)")
        void policyCarriedForward() {
            assertThat(libEncKey).isNotNull();

            // Set an EncryptionPolicy on a non-local frame's config
            Optional<FrameEntry> frame = librarian.content().stream()
                    .filter(e -> e.payload() != null && e.payload().snapshotCid() != null)
                    .findFirst();
            assertThat(frame).isPresent();
            String targetAlias = frame.get().alias();

            PolicySet policy = PolicySet.builder()
                    .encryption(PolicySet.EncryptionPolicy.toReaders())
                    .build();
            frame.get().setPolicy(policy);

            // Commit (cleartext — no explicit EncryptionContext)
            librarian.edit();
            librarian.commit(librarian);

            // After commit, the entry should still have the encryption policy
            Optional<FrameEntry> afterCommit = librarian.content().stream()
                    .filter(e -> targetAlias.equals(e.alias()))
                    .findFirst();
            assertThat(afterCommit).isPresent();
            assertThat(afterCommit.get().policy()).isNotNull();
            assertThat(afterCommit.get().policy().encryption()).isNotNull();
            assertThat(afterCommit.get().policy().encryption().isEnabled())
                    .as("EncryptionPolicy should survive commit")
                    .isTrue();
            assertThat(afterCommit.get().policy().encryption().encryptToReaders())
                    .as("encryptToReaders flag should survive commit")
                    .isTrue();
        }

        @Test
        @DisplayName("EncryptionContext.fromEncryptionPolicy creates working context")
        void fromEncryptionPolicyFactory() {
            PolicySet.EncryptionPolicy policy = PolicySet.EncryptionPolicy.toRecipients(List.of());

            // With pre-resolved keys
            EncryptionContext ctx = EncryptionContext.fromEncryptionPolicy(
                    policy, List.of(libEncKey));

            assertThat(ctx.shouldEncrypt(null)).isTrue();
            assertThat(ctx.recipients(null)).containsExactly(libEncKey);
        }

        @Test
        @DisplayName("fromEncryptionPolicy with null/disabled returns NONE")
        void fromDisabledPolicyReturnsNone() {
            EncryptionContext fromNull = EncryptionContext.fromEncryptionPolicy(null, List.of());
            assertThat(fromNull.shouldEncrypt(null)).isFalse();

            EncryptionContext fromDisabled = EncryptionContext.fromEncryptionPolicy(
                    PolicySet.EncryptionPolicy.none(), List.of(libEncKey));
            assertThat(fromDisabled.shouldEncrypt(null)).isFalse();
        }

        @Test
        @DisplayName("EncryptionPolicy with explicit recipients drives encryption without EncryptionContext")
        void policyWithRecipientsEncryptsAutomatically() {
            assertThat(libEncKey).isNotNull();

            // Set an EncryptionPolicy with the librarian's own IID as recipient
            Optional<FrameEntry> frame = librarian.content().stream()
                    .filter(e -> e.payload() != null && e.payload().snapshotCid() != null)
                    .findFirst();
            assertThat(frame).isPresent();
            String targetAlias = frame.get().alias();

            PolicySet policy = PolicySet.builder()
                    .encryption(PolicySet.EncryptionPolicy.toRecipients(
                            List.of(librarian.iid())))
                    .build();
            frame.get().setPolicy(policy);

            // Commit WITHOUT explicit EncryptionContext — policy should drive encryption
            librarian.edit();
            librarian.commit(librarian);

            // The frame should now be encrypted
            Optional<FrameEntry> afterCommit = librarian.content().stream()
                    .filter(e -> targetAlias.equals(e.alias()))
                    .findFirst();
            assertThat(afterCommit).isPresent();
            assertThat(afterCommit.get().payload().isEncrypted())
                    .as("Frame with EncryptionPolicy recipients should be encrypted")
                    .isTrue();
        }

        @Test
        @DisplayName("encryptToReaders with AccessPolicy READ rules drives encryption")
        void encryptToReadersWithAccessPolicy() {
            assertThat(libEncKey).isNotNull();

            // Set policy with encryptToReaders + an AccessPolicy with a READ rule
            // pointing to the librarian's IID
            Optional<FrameEntry> frame = librarian.content().stream()
                    .filter(e -> e.payload() != null && e.payload().snapshotCid() != null)
                    .findFirst();
            assertThat(frame).isPresent();
            String targetAlias = frame.get().alias();

            PolicySet policy = PolicySet.builder()
                    .encryption(PolicySet.EncryptionPolicy.toReaders())
                    .access(PolicySet.AccessPolicy.builder()
                            .defaultEffect(PolicySet.AccessPolicy.Effect.DENY)
                            .rules(List.of(PolicySet.AccessPolicy.Rule.builder()
                                    .subject(PolicySet.AccessPolicy.Subject.builder()
                                            .who(librarian.iid().encodeText())
                                            .build())
                                    .action(PolicySet.AccessPolicy.Action.READ)
                                    .effect(PolicySet.AccessPolicy.Effect.ALLOW)
                                    .build()))
                            .build())
                    .build();
            frame.get().setPolicy(policy);

            // Commit without explicit EncryptionContext
            librarian.edit();
            librarian.commit(librarian);

            // The frame should be encrypted (recipients derived from READ rule)
            Optional<FrameEntry> afterCommit = librarian.content().stream()
                    .filter(e -> targetAlias.equals(e.alias()))
                    .findFirst();
            assertThat(afterCommit).isPresent();
            assertThat(afterCommit.get().payload().isEncrypted())
                    .as("encryptToReaders should encrypt to the READ rule's subject")
                    .isTrue();
        }

        @Test
        @DisplayName("encryptToReaders with no AccessPolicy produces no encryption")
        void encryptToReadersWithoutAccessProducesNoEncryption() {
            // Set encryptToReaders but no AccessPolicy — no readers to derive
            Optional<FrameEntry> frame = librarian.content().stream()
                    .filter(e -> e.payload() != null && e.payload().snapshotCid() != null)
                    .findFirst();
            assertThat(frame).isPresent();
            String targetAlias = frame.get().alias();

            PolicySet policy = PolicySet.builder()
                    .encryption(PolicySet.EncryptionPolicy.toReaders())
                    // No access policy — no readers
                    .build();
            frame.get().setPolicy(policy);

            librarian.edit();
            librarian.commit(librarian);

            Optional<FrameEntry> afterCommit = librarian.content().stream()
                    .filter(e -> targetAlias.equals(e.alias()))
                    .findFirst();
            assertThat(afterCommit).isPresent();
            assertThat(afterCommit.get().payload().isEncrypted())
                    .as("encryptToReaders with no AccessPolicy should not encrypt")
                    .isFalse();
        }

        @Test
        @DisplayName("resolveEncryptionKeys returns librarian's own key")
        void resolveEncryptionKeysForSelf() {
            var keys = librarian.resolveEncryptionKeys(librarian.iid());
            assertThat(keys).hasSize(1);
            assertThat(keys.get(0).keyId()).isEqualTo(libEncKey.keyId());
        }

        @Test
        @DisplayName("resolveEncryptionKeys returns empty for unknown principal")
        void resolveEncryptionKeysForUnknown() {
            var unknownId = dev.everydaythings.graph.item.id.ItemID.random();
            var keys = librarian.resolveEncryptionKeys(unknownId);
            assertThat(keys).isEmpty();
        }

        @Test
        @DisplayName("explicit EncryptionContext overrides per-frame policy")
        void explicitContextOverridesPolicy() {
            assertThat(libEncKey).isNotNull();

            // Set a "no encryption" policy on a frame
            Optional<FrameEntry> frame = librarian.content().stream()
                    .filter(e -> e.payload() != null && e.payload().snapshotCid() != null)
                    .findFirst();
            assertThat(frame).isPresent();

            frame.get().setPolicy(PolicySet.builder()
                    .encryption(PolicySet.EncryptionPolicy.none())
                    .build());

            // But commit with explicit encryption context — explicit wins
            librarian.edit();
            EncryptionContext ctx = EncryptionContext.allFrames(List.of(libEncKey));
            librarian.commit(librarian, ctx);

            boolean anyEncrypted = librarian.content().stream()
                    .filter(e -> e.payload() != null)
                    .anyMatch(e -> e.payload().isEncrypted());
            assertThat(anyEncrypted)
                    .as("Explicit EncryptionContext should override per-frame policy")
                    .isTrue();
        }
    }

    @Nested
    @DisplayName("Hydrate with Decryption")
    class HydrateWithDecryption {

        @Test
        @DisplayName("encrypted frame can be decrypted by the librarian")
        void decryptionRoundTrip() {
            assertThat(libEncKey).isNotNull();

            // Edit and re-commit with encryption
            librarian.edit();
            EncryptionContext ctx = EncryptionContext.allFrames(List.of(libEncKey));
            librarian.commit(librarian, ctx);

            // Find an encrypted entry
            Optional<FrameEntry> encrypted = librarian.content().stream()
                    .filter(e -> e.payload() != null && e.payload().isEncrypted())
                    .findFirst();
            assertThat(encrypted).isPresent();

            // Manually decrypt the stored envelope to verify the round-trip
            ContentID encCid = encrypted.get().payload().encryptedCid();
            ItemStore store = librarian.library().primaryStore().orElseThrow();
            byte[] envelopeBytes = store.content(encCid).orElseThrow();

            CBORObject cbor = CBORObject.DecodeFromBytes(envelopeBytes);
            EncryptedEnvelope envelope = EncryptedEnvelope.fromCborTree(cbor);

            // Decrypt using the librarian's key
            byte[] plaintext = librarian.decryptEnvelope(envelope, libEncKey.keyId());
            assertThat(plaintext)
                    .as("Decrypted plaintext should not be empty")
                    .isNotEmpty();
        }
    }
}
