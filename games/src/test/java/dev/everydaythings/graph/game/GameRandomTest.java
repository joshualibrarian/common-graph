package dev.everydaythings.graph.game;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class GameRandomTest {

    @Test
    void fromSeed_isDeterministic() {
        byte[] seed = "test-seed-42".getBytes();
        GameRandom rng1 = GameRandom.fromSeed(seed);
        GameRandom rng2 = GameRandom.fromSeed(seed);

        for (int i = 0; i < 100; i++) {
            assertThat(rng1.nextInt(1000)).isEqualTo(rng2.nextInt(1000));
        }
    }

    @Test
    void differentSeeds_produceDifferentSequences() {
        GameRandom rng1 = GameRandom.fromSeed("seed-a".getBytes());
        GameRandom rng2 = GameRandom.fromSeed("seed-b".getBytes());

        // With high probability, at least one of the first 10 values differs
        boolean anyDifferent = false;
        for (int i = 0; i < 10; i++) {
            if (rng1.nextInt(10000) != rng2.nextInt(10000)) {
                anyDifferent = true;
                break;
            }
        }
        assertThat(anyDifferent).isTrue();
    }

    @Test
    void nextInt_boundRespected() {
        GameRandom rng = GameRandom.fromSeed("bounds".getBytes());
        for (int i = 0; i < 1000; i++) {
            int val = rng.nextInt(6);
            assertThat(val).isBetween(0, 5);
        }
    }

    @Test
    void nextInt_originBoundRespected() {
        GameRandom rng = GameRandom.fromSeed("dice".getBytes());
        for (int i = 0; i < 1000; i++) {
            int val = rng.nextInt(1, 7); // d6
            assertThat(val).isBetween(1, 6);
        }
    }

    @Test
    void shuffle_isDeterministic() {
        byte[] seed = "shuffle-test".getBytes();

        List<String> list1 = new ArrayList<>(List.of("A", "B", "C", "D", "E", "F", "G", "H"));
        List<String> list2 = new ArrayList<>(List.of("A", "B", "C", "D", "E", "F", "G", "H"));

        GameRandom.fromSeed(seed).shuffle(list1);
        GameRandom.fromSeed(seed).shuffle(list2);

        assertThat(list1).isEqualTo(list2);
    }

    @Test
    void shuffle_actuallyPermutes() {
        List<String> original = List.of("A", "B", "C", "D", "E", "F", "G", "H", "I", "J");
        List<String> shuffled = new ArrayList<>(original);
        GameRandom.fromSeed("permute".getBytes()).shuffle(shuffled);

        // With very high probability, the shuffled list differs from original
        assertThat(shuffled).isNotEqualTo(original);
        // But contains the same elements
        assertThat(shuffled).containsExactlyInAnyOrderElementsOf(original);
    }

    @Test
    void fromCombinedSeeds_isOrderIndependent() {
        byte[] seed1 = "player-one".getBytes();
        byte[] seed2 = "player-two".getBytes();

        GameRandom rngA = GameRandom.fromCombinedSeeds(List.of(seed1, seed2));
        GameRandom rngB = GameRandom.fromCombinedSeeds(List.of(seed2, seed1));

        // Same sequence regardless of seed order
        for (int i = 0; i < 50; i++) {
            assertThat(rngA.nextInt(1000)).isEqualTo(rngB.nextInt(1000));
        }
    }

    @Test
    void fromCombinedSeeds_differentFromSingleSeed() {
        byte[] seed1 = "player-one".getBytes();
        byte[] seed2 = "player-two".getBytes();

        GameRandom combined = GameRandom.fromCombinedSeeds(List.of(seed1, seed2));
        GameRandom single = GameRandom.fromSeed(seed1);

        // With high probability, these produce different sequences
        boolean anyDifferent = false;
        for (int i = 0; i < 10; i++) {
            if (combined.nextInt(10000) != single.nextInt(10000)) {
                anyDifferent = true;
                break;
            }
        }
        assertThat(anyDifferent).isTrue();
    }

    @Test
    void nextBoolean_returnsValues() {
        GameRandom rng = GameRandom.fromSeed("bool-test".getBytes());
        boolean seenTrue = false;
        boolean seenFalse = false;
        for (int i = 0; i < 100; i++) {
            if (rng.nextBoolean()) seenTrue = true;
            else seenFalse = true;
        }
        assertThat(seenTrue).isTrue();
        assertThat(seenFalse).isTrue();
    }

    @Test
    void nextDouble_inRange() {
        GameRandom rng = GameRandom.fromSeed("double-test".getBytes());
        for (int i = 0; i < 1000; i++) {
            double val = rng.nextDouble();
            assertThat(val).isBetween(0.0, 1.0);
        }
    }
}
