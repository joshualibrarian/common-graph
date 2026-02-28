package dev.everydaythings.graph.game;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class ZoneMapTest {

    @Test
    void define_createsNamedZone() {
        ZoneMap<String> zones = new ZoneMap<>();
        zones.define("deck", ZoneVisibility.HIDDEN);

        assertThat(zones.hasZone("deck")).isTrue();
        assertThat(zones.zone("deck").name()).isEqualTo("deck");
        assertThat(zones.zone("deck").visibility()).isEqualTo(ZoneVisibility.HIDDEN);
    }

    @Test
    void definePerPlayer_createsIndexedZones() {
        ZoneMap<String> zones = new ZoneMap<>();
        zones.definePerPlayer("hand", 3, ZoneVisibility.OWNER);

        assertThat(zones.hasZone("hand:0")).isTrue();
        assertThat(zones.hasZone("hand:1")).isTrue();
        assertThat(zones.hasZone("hand:2")).isTrue();
        assertThat(zones.hasZone("hand:3")).isFalse();
        assertThat(zones.zoneCount()).isEqualTo(3);
    }

    @Test
    void zone_throwsOnUnknown() {
        ZoneMap<String> zones = new ZoneMap<>();
        assertThatIllegalArgumentException()
                .isThrownBy(() -> zones.zone("missing"))
                .withMessageContaining("Unknown zone");
    }

    @Test
    void zone_byBaseNameAndSeat() {
        ZoneMap<String> zones = new ZoneMap<>();
        zones.definePerPlayer("hand", 2, ZoneVisibility.OWNER);

        Zone<String> hand1 = zones.zone("hand", 1);
        assertThat(hand1.name()).isEqualTo("hand:1");
    }

    @Test
    void transfer_movesTopItem() {
        ZoneMap<String> zones = new ZoneMap<String>()
                .define("deck", ZoneVisibility.HIDDEN)
                .define("hand", ZoneVisibility.OWNER);

        zones.zone("deck").addTop("ace");
        zones.zone("deck").addTop("king");

        var moved = zones.transfer("deck", "hand");
        assertThat(moved).hasValue("king");
        assertThat(zones.zone("deck").size()).isEqualTo(1);
        assertThat(zones.zone("hand").size()).isEqualTo(1);
        assertThat(zones.zone("hand").contents()).containsExactly("king");
    }

    @Test
    void transfer_returnsEmptyFromEmptyZone() {
        ZoneMap<String> zones = new ZoneMap<String>()
                .define("deck", ZoneVisibility.HIDDEN)
                .define("hand", ZoneVisibility.OWNER);

        var moved = zones.transfer("deck", "hand");
        assertThat(moved).isEmpty();
    }

    @Test
    void transfer_specificItem() {
        ZoneMap<String> zones = new ZoneMap<String>()
                .define("hand", ZoneVisibility.OWNER)
                .define("discard", ZoneVisibility.PUBLIC);

        zones.zone("hand").addTop("ace");
        zones.zone("hand").addTop("king");
        zones.zone("hand").addTop("queen");

        boolean moved = zones.transfer("hand", "discard", "king");
        assertThat(moved).isTrue();
        assertThat(zones.zone("hand").contents()).containsExactly("ace", "queen");
        assertThat(zones.zone("discard").contents()).containsExactly("king");
    }

    @Test
    void transfer_specificItem_returnsFalseIfNotFound() {
        ZoneMap<String> zones = new ZoneMap<String>()
                .define("hand", ZoneVisibility.OWNER)
                .define("discard", ZoneVisibility.PUBLIC);

        zones.zone("hand").addTop("ace");

        boolean moved = zones.transfer("hand", "discard", "king");
        assertThat(moved).isFalse();
        assertThat(zones.zone("hand").size()).isEqualTo(1);
        assertThat(zones.zone("discard").size()).isEqualTo(0);
    }

    @Test
    void totalCount_sumsAcrossAllZones() {
        ZoneMap<String> zones = new ZoneMap<String>()
                .define("deck", ZoneVisibility.HIDDEN)
                .define("hand:0", ZoneVisibility.OWNER)
                .define("hand:1", ZoneVisibility.OWNER);

        zones.zone("deck").addTop("a");
        zones.zone("deck").addTop("b");
        zones.zone("deck").addTop("c");
        zones.zone("hand:0").addTop("d");
        zones.zone("hand:1").addTop("e");

        assertThat(zones.totalCount()).isEqualTo(5);
    }

    @Test
    void zoneNames_returnsAllDefined() {
        ZoneMap<String> zones = new ZoneMap<String>()
                .define("deck", ZoneVisibility.HIDDEN)
                .define("discard", ZoneVisibility.PUBLIC);

        assertThat(zones.zoneNames().toList()).containsExactly("deck", "discard");
    }

    // ==================================================================================
    // Zone Visibility Tests
    // ==================================================================================

    @Test
    void visibility_public_showsAll() {
        Zone<String> zone = new Zone<>("table", ZoneVisibility.PUBLIC);
        zone.addTop("ace");
        zone.addTop("king");

        assertThat(zone.contentsVisibleTo(0)).containsExactly("ace", "king");
        assertThat(zone.contentsVisibleTo(1)).containsExactly("ace", "king");
        assertThat(zone.contentsVisibleTo(-1)).containsExactly("ace", "king"); // spectator
    }

    @Test
    void visibility_hidden_showsNothing() {
        Zone<String> zone = new Zone<>("deck", ZoneVisibility.HIDDEN);
        zone.addTop("ace");
        zone.addTop("king");

        assertThat(zone.contentsVisibleTo(0)).isEmpty();
        assertThat(zone.contentsVisibleTo(-1)).isEmpty();
    }

    @Test
    void visibility_topOnly_showsLastItem() {
        Zone<String> zone = new Zone<>("discard", ZoneVisibility.TOP_ONLY);
        zone.addTop("ace");
        zone.addTop("king");

        assertThat(zone.contentsVisibleTo(0)).containsExactly("king");
        assertThat(zone.contentsVisibleTo(1)).containsExactly("king");
    }

    @Test
    void visibility_topOnly_emptyZone() {
        Zone<String> zone = new Zone<>("discard", ZoneVisibility.TOP_ONLY);
        assertThat(zone.contentsVisibleTo(0)).isEmpty();
    }

    @Test
    void visibility_owner_showsToOwnerOnly() {
        Zone<String> zone = new Zone<>("hand:2", ZoneVisibility.OWNER);
        zone.addTop("ace");
        zone.addTop("king");

        assertThat(zone.contentsVisibleTo(2)).containsExactly("ace", "king");
        assertThat(zone.contentsVisibleTo(0)).isEmpty();
        assertThat(zone.contentsVisibleTo(1)).isEmpty();
        assertThat(zone.contentsVisibleTo(-1)).isEmpty(); // spectator
    }

    @Test
    void visibility_ownerAndCount_showsToOwnerOnly() {
        Zone<String> zone = new Zone<>("hand:1", ZoneVisibility.OWNER_AND_COUNT);
        zone.addTop("ace");
        zone.addTop("king");

        // Owner sees contents
        assertThat(zone.contentsVisibleTo(1)).containsExactly("ace", "king");

        // Others don't see contents but size() is always available
        assertThat(zone.contentsVisibleTo(0)).isEmpty();
        assertThat(zone.size()).isEqualTo(2); // count always accessible
    }

    // ==================================================================================
    // Zone Operations
    // ==================================================================================

    @Test
    void zone_stackBehavior_addTopRemoveTop() {
        Zone<String> zone = new Zone<>("stack", ZoneVisibility.PUBLIC);
        zone.addTop("bottom");
        zone.addTop("middle");
        zone.addTop("top");

        assertThat(zone.removeTop()).hasValue("top");
        assertThat(zone.removeTop()).hasValue("middle");
        assertThat(zone.removeTop()).hasValue("bottom");
        assertThat(zone.removeTop()).isEmpty();
    }

    @Test
    void zone_queueBehavior_addTopRemoveBottom() {
        Zone<String> zone = new Zone<>("queue", ZoneVisibility.PUBLIC);
        zone.addTop("first");
        zone.addTop("second");
        zone.addTop("third");

        assertThat(zone.removeBottom()).hasValue("first");
        assertThat(zone.removeBottom()).hasValue("second");
        assertThat(zone.removeBottom()).hasValue("third");
        assertThat(zone.removeBottom()).isEmpty();
    }

    @Test
    void zone_removeSpecificItem() {
        Zone<String> zone = new Zone<>("hand", ZoneVisibility.PUBLIC);
        zone.addTop("ace");
        zone.addTop("king");
        zone.addTop("queen");

        assertThat(zone.remove("king")).isTrue();
        assertThat(zone.contents()).containsExactly("ace", "queen");

        assertThat(zone.remove("king")).isFalse(); // already removed
    }

    @Test
    void zone_peek_doesNotRemove() {
        Zone<String> zone = new Zone<>("deck", ZoneVisibility.PUBLIC);
        zone.addTop("card");

        assertThat(zone.peek()).hasValue("card");
        assertThat(zone.size()).isEqualTo(1);
    }

    @Test
    void zone_shuffle_isDeterministic() {
        Zone<String> zone1 = new Zone<>("deck", ZoneVisibility.HIDDEN);
        Zone<String> zone2 = new Zone<>("deck", ZoneVisibility.HIDDEN);

        List<String> cards = List.of("A", "2", "3", "4", "5", "6", "7", "8", "9", "10");
        zone1.addAll(cards);
        zone2.addAll(cards);

        byte[] seed = "test-seed".getBytes();
        zone1.shuffle(GameRandom.fromSeed(seed));
        zone2.shuffle(GameRandom.fromSeed(seed));

        assertThat(zone1.contents()).isEqualTo(zone2.contents());
    }

    @Test
    void zone_ownerSeat_parsedFromName() {
        assertThat(new Zone<>("hand:0", ZoneVisibility.OWNER).ownerSeat()).isEqualTo(0);
        assertThat(new Zone<>("hand:5", ZoneVisibility.OWNER).ownerSeat()).isEqualTo(5);
        assertThat(new Zone<>("rack:12", ZoneVisibility.OWNER).ownerSeat()).isEqualTo(12);
        assertThat(new Zone<>("deck", ZoneVisibility.HIDDEN).ownerSeat()).isEqualTo(-1);
        assertThat(new Zone<>("some:thing:3", ZoneVisibility.OWNER).ownerSeat()).isEqualTo(3);
    }

    @Test
    void zone_clear_removesAll() {
        Zone<String> zone = new Zone<>("hand", ZoneVisibility.PUBLIC);
        zone.addTop("ace");
        zone.addTop("king");
        zone.clear();

        assertThat(zone.isEmpty()).isTrue();
        assertThat(zone.size()).isEqualTo(0);
    }

    // ==================================================================================
    // Integration: Card Game Setup
    // ==================================================================================

    @Test
    void pokerSetup_definesDeckHandsCommunity() {
        ZoneMap<String> zones = new ZoneMap<String>()
                .define("deck", ZoneVisibility.HIDDEN)
                .define("community", ZoneVisibility.PUBLIC)
                .define("discard", ZoneVisibility.PUBLIC)
                .definePerPlayer("hand", 4, ZoneVisibility.OWNER);

        // Load deck
        for (String suit : List.of("H", "D", "C", "S")) {
            for (String rank : List.of("A", "2", "3", "4", "5", "6", "7", "8", "9", "T", "J", "Q", "K")) {
                zones.zone("deck").addTop(rank + suit);
            }
        }
        assertThat(zones.zone("deck").size()).isEqualTo(52);

        // Shuffle
        zones.zone("deck").shuffle(GameRandom.fromSeed("poker-game-1".getBytes()));

        // Deal 2 cards to each player
        for (int seat = 0; seat < 4; seat++) {
            zones.transfer("deck", "hand:" + seat);
            zones.transfer("deck", "hand:" + seat);
        }

        assertThat(zones.zone("deck").size()).isEqualTo(44);
        for (int seat = 0; seat < 4; seat++) {
            assertThat(zones.zone("hand", seat).size()).isEqualTo(2);
        }

        // Verify visibility: player 0 sees own hand, not others
        Zone<String> hand0 = zones.zone("hand", 0);
        assertThat(hand0.contentsVisibleTo(0)).hasSize(2);
        assertThat(hand0.contentsVisibleTo(1)).isEmpty();

        // Total items preserved
        assertThat(zones.totalCount()).isEqualTo(52);
    }
}
