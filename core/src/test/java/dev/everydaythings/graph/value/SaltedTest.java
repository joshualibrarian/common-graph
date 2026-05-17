package dev.everydaythings.graph.value;

import dev.everydaythings.graph.canonical.HashTree;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Salted wraps an inner value with random salt bytes; the structural hash
 * incorporates the salt, so even identical inner values produce different
 * DatumIDs when salted with different salts.  That's the privacy property
 * — the elided form of a salted value can't be brute-forced without
 * guessing the salt too.
 */
class SaltedTest {

    @Test
    @DisplayName("two Salted bodies with same inner but different salts have different DatumIDs")
    void differentSaltsDifferentHashes() {
        byte[] saltA = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};
        byte[] saltB = new byte[]{99, 98, 97, 96, 95, 94, 93, 92, 91, 90, 89, 88, 87, 86, 85, 84};

        Salted a = new Salted(Boolean.TRUE, saltA);
        Salted b = new Salted(Boolean.TRUE, saltB);

        byte[] hashA = HashTree.hashOf(a, HashTree.DEFAULT_DIGEST);
        byte[] hashB = HashTree.hashOf(b, HashTree.DEFAULT_DIGEST);

        assertThat(hashA).isNotEqualTo(hashB);
    }

    @Test
    @DisplayName("Salted bodies with same inner and same salt have identical DatumIDs (deterministic)")
    void sameSaltsSameHashes() {
        byte[] salt = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};

        Salted a = new Salted(Boolean.TRUE, salt);
        Salted b = new Salted(Boolean.TRUE, salt);

        byte[] hashA = HashTree.hashOf(a, HashTree.DEFAULT_DIGEST);
        byte[] hashB = HashTree.hashOf(b, HashTree.DEFAULT_DIGEST);

        assertThat(hashA).isEqualTo(hashB);
    }

    @Test
    @DisplayName("random-salt factory produces 16 bytes by default")
    void randomSaltDefaultLength() {
        byte[] salt = Salted.randomSalt(Salted.DEFAULT_SALT_BYTES);
        assertThat(salt).hasSize(16);
    }

    @Test
    @DisplayName("Salted around different inner values produces different hashes")
    void differentInnersDifferentHashes() {
        byte[] salt = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};

        Salted trueValue  = new Salted(Boolean.TRUE,  salt);
        Salted falseValue = new Salted(Boolean.FALSE, salt);

        byte[] hashTrue  = HashTree.hashOf(trueValue,  HashTree.DEFAULT_DIGEST);
        byte[] hashFalse = HashTree.hashOf(falseValue, HashTree.DEFAULT_DIGEST);

        assertThat(hashTrue).isNotEqualTo(hashFalse);
    }
}
