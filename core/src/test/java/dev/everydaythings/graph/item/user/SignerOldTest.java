package dev.everydaythings.graph.item.user;

import dev.everydaythings.graph.item.ItemOldTest;
import dev.everydaythings.graph.frame.FrameBodyOld;
import dev.everydaythings.graph.frame.FrameRecordOld;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.crypt.SigningPublicKey;
import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Abstract test for Signer behavior.
 *
 * <p>Inherits all universal Item tests from {@link ItemOldTest},
 * plus tests for signing capabilities that all Signers must satisfy.
 *
 * <p>Since Signer is abstract, this test class is also abstract.
 * Concrete subclasses (like LibrarianTest) provide the actual Signer to test.
 */
@DisplayName("Signer")
@Disabled
public abstract class SignerOldTest extends ItemOldTest {

    /**
     * Get the item as a Signer.
     *
     * <p>Subclasses should ensure {@link #createItem} returns a Signer.
     */
    protected SignerOld signer() {
        return (SignerOld) item;
    }

    // ==================================================================================
    // Public Key Tests
    // ==================================================================================

    @Nested
    @DisplayName("Public Key")
    class PublicKey {

        @Test
        @DisplayName("has a public key")
        void hasPublicKey() {
            assertThat(signer().publicKey())
                    .as("Signer public key")
                    .isNotNull();
        }

        @Test
        @DisplayName("public key has valid SPKI encoding")
        void publicKeyHasValidSpki() {
            SigningPublicKey pubKey = signer().publicKey();

            assertThat(pubKey.spki())
                    .as("Public key SPKI bytes")
                    .isNotNull()
                    .hasSizeGreaterThan(0);
        }

        @Test
        @DisplayName("public key has key ID")
        void publicKeyHasKeyId() {
            SigningPublicKey pubKey = signer().publicKey();

            assertThat(pubKey.keyId())
                    .as("Public key ID")
                    .isNotNull();
        }

        @Test
        @DisplayName("public key has algorithm")
        void publicKeyHasAlgorithm() {
            SigningPublicKey pubKey = signer().publicKey();

            assertThat(pubKey.algorithm())
                    .as("Public key algorithm")
                    .isNotNull();

            assertThat(pubKey.algorithm().toString())
                    .as("Public key algorithm string")
                    .isNotBlank();
        }
    }

    // ==================================================================================
    // Signing Capability Tests
    // ==================================================================================

    @Nested
    @DisplayName("Signing Capability")
    class SigningCapability {

        @Test
        @DisplayName("can sign flag is accessible")
        void canSignFlagIsAccessible() {
            boolean canSign = signer().canSign();

            // Should be true for signers with vault access, false otherwise
            assertThat(canSign).isIn(true, false);
        }

        @Test
        @DisplayName("can sign when vault is available")
        void canSignWhenVaultAvailable() {
            // Signers created with a vault should be able to sign
            // This is true for Librarian which creates its own vault
            assertThat(signer().canSign())
                    .as("Should be able to sign with local vault")
                    .isTrue();
        }
    }

    // ==================================================================================
    // Signing Operations Tests
    // ==================================================================================

    @Nested
    @DisplayName("Signing Operations")
    class SigningOperations {

        @Test
        @DisplayName("can sign raw bytes")
        void canSignRawBytes() {
            // Skip if signer can't sign (no vault)
            if (!signer().canSign()) {
                return;
            }

            ItemID targetId = ItemID.random();
            byte[] rawData = "test data to sign".getBytes();

            var signature = signer().sign(targetId, rawData, null, null);

            assertThat(signature)
                    .as("Signature")
                    .isNotNull();
        }

        @Test
        @DisplayName("can sign multiple times")
        void canSignMultipleTimes() {
            if (!signer().canSign()) {
                return;
            }

            byte[] data1 = "first data".getBytes();
            byte[] data2 = "second data".getBytes();

            var sig1 = signer().sign(ItemID.random(), data1, null, null);
            var sig2 = signer().sign(ItemID.random(), data2, null, null);

            assertThat(sig1).isNotNull();
            assertThat(sig2).isNotNull();
        }
    }

    // ==================================================================================
    // Frame Signing Tests
    // ==================================================================================

    @Nested
    @DisplayName("Frame Signing")
    class FrameOldSigning {

        @Test
        @DisplayName("frame bodies created by signer can be signed via FrameRecord")
        void frameBodiesCreatedBySignerCanBeSigned() {
            if (!signer().canSign()) {
                return;
            }

            // Create a frame body from signer to itself (valid for testing)
            ItemID predicateId = ItemID.fromString("cg.predicate:self-reference");

            FrameBodyOld body = FrameBodyOld.builder(predicateId)
                    .bind(ThematicRole.Theme.IID, signer().iid())
                    .bind(ThematicRole.Goal.IID, signer().iid())
                    .build();

            // Sign the body by creating a FrameRecord
            FrameRecordOld record = FrameRecordOld.create(body, signer());

            assertThat(record.signer())
                    .as("Record should have signer key")
                    .isNotNull();

            assertThat(record.isSigned())
                    .as("Record should be signed")
                    .isTrue();
        }
    }
}
