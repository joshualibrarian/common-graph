package dev.everydaythings.graph.crypt;

import dev.everydaythings.graph.item.id.ContentID;
import dev.everydaythings.graph.library.ItemStore;
import dev.everydaythings.graph.library.Library;
import dev.everydaythings.graph.library.bytestore.ByteStore;
import dev.everydaythings.graph.runtime.Librarian;
import dev.everydaythings.graph.crypt.InMemoryVault;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for at-rest encryption: key derivation, encrypt/decrypt, library integration.
 */
@Tag("slow")
class AtRestEncryptionTest {

    // ==================================================================================
    // Unit tests: AtRestEncryption encrypt/decrypt
    // ==================================================================================

    @Nested
    class EncryptDecrypt {

        @Test
        void roundTrip() {
            byte[] key = new byte[32];
            Arrays.fill(key, (byte) 0x42);
            AtRestEncryption enc = AtRestEncryption.withKey(key);

            byte[] plaintext = "Hello, encrypted world!".getBytes();
            byte[] encrypted = enc.encrypt(plaintext);

            assertThat(encrypted).isNotEqualTo(plaintext);
            assertThat(encrypted.length).isGreaterThan(plaintext.length); // nonce + tag overhead

            byte[] decrypted = enc.decrypt(encrypted);
            assertThat(decrypted).isEqualTo(plaintext);
        }

        @Test
        void differentNoncesProduceDifferentCiphertext() {
            byte[] key = new byte[32];
            Arrays.fill(key, (byte) 0x42);
            AtRestEncryption enc = AtRestEncryption.withKey(key);

            byte[] plaintext = "same data".getBytes();
            byte[] enc1 = enc.encrypt(plaintext);
            byte[] enc2 = enc.encrypt(plaintext);

            // Same plaintext, different random nonces → different ciphertext
            assertThat(enc1).isNotEqualTo(enc2);

            // But both decrypt to the same plaintext
            assertThat(enc.decrypt(enc1)).isEqualTo(plaintext);
            assertThat(enc.decrypt(enc2)).isEqualTo(plaintext);
        }

        @Test
        void nullHandling() {
            byte[] key = new byte[32];
            AtRestEncryption enc = AtRestEncryption.withKey(key);

            assertThat(enc.encrypt(null)).isNull();
            assertThat(enc.decrypt(null)).isNull();
        }

        @Test
        void wrongKeyCannotDecrypt() {
            byte[] key1 = new byte[32];
            byte[] key2 = new byte[32];
            Arrays.fill(key1, (byte) 0x01);
            Arrays.fill(key2, (byte) 0x02);

            AtRestEncryption enc1 = AtRestEncryption.withKey(key1);
            AtRestEncryption enc2 = AtRestEncryption.withKey(key2);

            byte[] encrypted = enc1.encrypt("secret".getBytes());

            assertThatThrownBy(() -> enc2.decrypt(encrypted))
                    .isInstanceOf(SecurityException.class);
        }

        @Test
        void tamperedDataFailsDecryption() {
            byte[] key = new byte[32];
            AtRestEncryption enc = AtRestEncryption.withKey(key);

            byte[] encrypted = enc.encrypt("data".getBytes());
            // Flip a bit in the ciphertext
            encrypted[encrypted.length - 5] ^= 0x01;

            assertThatThrownBy(() -> enc.decrypt(encrypted))
                    .isInstanceOf(SecurityException.class);
        }

        @Test
        void emptyPlaintext() {
            byte[] key = new byte[32];
            AtRestEncryption enc = AtRestEncryption.withKey(key);

            byte[] encrypted = enc.encrypt(new byte[0]);
            byte[] decrypted = enc.decrypt(encrypted);
            assertThat(decrypted).isEmpty();
        }

        @Test
        void largeData() {
            byte[] key = new byte[32];
            AtRestEncryption enc = AtRestEncryption.withKey(key);

            // 1MB of data
            byte[] large = new byte[1024 * 1024];
            Arrays.fill(large, (byte) 0xAB);

            byte[] encrypted = enc.encrypt(large);
            byte[] decrypted = enc.decrypt(encrypted);
            assertThat(decrypted).isEqualTo(large);
        }
    }

    // ==================================================================================
    // Key derivation from Vault
    // ==================================================================================

    @Nested
    class KeyDerivation {

        @Test
        void fromVaultProducesDeterministicKey() {
            InMemoryVault vault = InMemoryVault.create();
            vault.generateEncryptionKey();

            AtRestEncryption enc1 = AtRestEncryption.fromVault(vault);
            AtRestEncryption enc2 = AtRestEncryption.fromVault(vault);

            // Same vault → same derived key → can decrypt each other's output
            byte[] data = "deterministic".getBytes();
            byte[] encrypted = enc1.encrypt(data);
            byte[] decrypted = enc2.decrypt(encrypted);
            assertThat(decrypted).isEqualTo(data);

            enc1.destroy();
            enc2.destroy();
        }

        @Test
        void differentVaultsProduceDifferentKeys() {
            InMemoryVault vault1 = InMemoryVault.create();
            vault1.generateEncryptionKey();
            InMemoryVault vault2 = InMemoryVault.create();
            vault2.generateEncryptionKey();

            AtRestEncryption enc1 = AtRestEncryption.fromVault(vault1);
            AtRestEncryption enc2 = AtRestEncryption.fromVault(vault2);

            byte[] encrypted = enc1.encrypt("secret".getBytes());

            assertThatThrownBy(() -> enc2.decrypt(encrypted))
                    .isInstanceOf(SecurityException.class);

            enc1.destroy();
            enc2.destroy();
        }

        @Test
        void vaultWithoutEncryptionKeyThrows() {
            InMemoryVault vault = InMemoryVault.create(); // only has signing key

            assertThatThrownBy(() -> AtRestEncryption.fromVault(vault))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("no encryption key");
        }
    }

    // ==================================================================================
    // Key zeroization
    // ==================================================================================

    @Nested
    class Zeroization {

        @Test
        void destroyPreventsEncrypt() {
            byte[] key = new byte[32];
            AtRestEncryption enc = AtRestEncryption.withKey(key);
            enc.destroy();

            assertThat(enc.isDestroyed()).isTrue();
            assertThatThrownBy(() -> enc.encrypt("data".getBytes()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("destroyed");
        }

        @Test
        void destroyPreventsDecrypt() {
            byte[] key = new byte[32];
            AtRestEncryption enc = AtRestEncryption.withKey(key);
            byte[] encrypted = enc.encrypt("data".getBytes());

            enc.destroy();

            assertThatThrownBy(() -> enc.decrypt(encrypted))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("destroyed");
        }
    }

    // ==================================================================================
    // Library integration
    // ==================================================================================

    @Nested
    class LibraryIntegration {

        @Test
        void encryptedLibraryStoresAndRetrievesContent() {
            InMemoryVault vault = InMemoryVault.create();
            vault.generateEncryptionKey();

            try (Library lib = Library.memoryEncrypted(vault)) {
                assertThat(lib.isEncrypted()).isTrue();

                // Store content
                byte[] data = "encrypted at rest".getBytes();
                ContentID cid = lib.store().content(data);

                // Retrieve content
                byte[] retrieved = lib.store().retrieveContent(cid);
                assertThat(retrieved).isEqualTo(data);
            }
        }

        @Test
        void encryptedLibraryStoresAndRetrievesManifest() {
            InMemoryVault vault = InMemoryVault.create();
            vault.generateEncryptionKey();

            // Create a librarian with encrypted library
            Librarian librarian = Librarian.createInMemory();
            Library encLib = Library.memoryEncrypted(vault);
            encLib.setLibrarian(librarian);

            // Store raw content as a proxy for manifest storage
            byte[] manifestBytes = "manifest-body-bytes".getBytes();
            ContentID cid = encLib.store().content(manifestBytes);
            assertThat(cid).isNotNull();

            // Retrieve content
            byte[] retrieved = encLib.store().retrieveContent(cid);
            assertThat(retrieved).isEqualTo(manifestBytes);

            encLib.close();
        }

        @Test
        void unencryptedLibraryCannotReadEncryptedData() {
            InMemoryVault vault = InMemoryVault.create();
            vault.generateEncryptionKey();

            // Store data in encrypted library
            Library encLib = Library.memoryEncrypted(vault);
            byte[] data = "secret data".getBytes();
            ContentID cid = encLib.store().content(data);

            // Read raw bytes directly from the ByteStore layer (bypasses ItemStore encryption)
            @SuppressWarnings("unchecked")
            ByteStore<ItemStore.Column> byteStore = (ByteStore<ItemStore.Column>) encLib.store();
            byte[] key = ItemStore.Column.OBJECTS.key(cid);
            byte[] rawBytes = byteStore.get(ItemStore.Column.OBJECTS, key);

            // Raw bytes should NOT equal the plaintext (they're encrypted)
            assertThat(rawBytes).isNotEqualTo(data);
            assertThat(rawBytes.length).isGreaterThan(data.length);

            encLib.close();
        }

        @Test
        void keyZeroizedOnLibraryClose() {
            InMemoryVault vault = InMemoryVault.create();
            vault.generateEncryptionKey();

            Library lib = Library.memoryEncrypted(vault);
            assertThat(lib.isEncrypted()).isTrue();

            lib.close();
            assertThat(lib.isEncrypted()).isFalse();
        }

        @Test
        void doubleEncryptionContentPlusAtRest() {
            // Content-encrypted frame inside at-rest encrypted store
            InMemoryVault vault = InMemoryVault.create();
            vault.generateEncryptionKey();

            try (Library lib = Library.memoryEncrypted(vault)) {
                // Simulate a Tag 10 encrypted envelope stored at rest
                byte[] tag10Envelope = "pretend-this-is-a-tag10-envelope".getBytes();
                ContentID cid = lib.store().content(tag10Envelope);

                // Retrieve: at-rest decryption happens transparently
                byte[] retrieved = lib.store().retrieveContent(cid);
                assertThat(retrieved).isEqualTo(tag10Envelope);

                // The raw storage has double-encrypted bytes
                @SuppressWarnings("unchecked")
                ByteStore<ItemStore.Column> byteStore = (ByteStore<ItemStore.Column>) lib.store();
                byte[] rawKey = ItemStore.Column.OBJECTS.key(cid);
                byte[] rawBytes = byteStore.get(ItemStore.Column.OBJECTS, rawKey);
                assertThat(rawBytes).isNotEqualTo(tag10Envelope);
            }
        }

        @Test
        void plainLibraryIsNotEncrypted() {
            try (Library lib = Library.memory()) {
                assertThat(lib.isEncrypted()).isFalse();
            }
        }
    }
}
