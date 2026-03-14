package dev.everydaythings.graph.language;

import dev.everydaythings.graph.item.id.ItemID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests verifying that vocabulary seed slot roles are correctly populated
 * from seed declarations.
 */
class VocabularySeedSlotTest {

    @Test
    void createVerbHasExpectedSlotRoles() {
        var roles = CoreVocabulary.Create.SEED.slotRoles();
        assertThat(roles)
                .as("CREATE should have 5 slot roles")
                .hasSize(5);

        assertThat(roles.get(0)).isEqualTo(ItemID.fromString(ThematicRole.Theme.KEY));
        assertThat(roles.get(1)).isEqualTo(ItemID.fromString(ThematicRole.Goal.KEY));
        assertThat(roles.get(2)).isEqualTo(ItemID.fromString(ThematicRole.Name.KEY));
        assertThat(roles.get(3)).isEqualTo(ItemID.fromString(ThematicRole.Partner.KEY));
        assertThat(roles.get(4)).isEqualTo(ItemID.fromString(ThematicRole.Source.KEY));
    }

    @Test
    void getVerbHasExpectedSlotRoles() {
        var roles = CoreVocabulary.Get.SEED.slotRoles();
        assertThat(roles)
                .as("GET should have 1 slot role")
                .hasSize(1);

        assertThat(roles.get(0)).isEqualTo(ItemID.fromString(ThematicRole.Theme.KEY));
    }

    @Test
    void relationVerbHasThemeAndGoalSlots() {
        // HYPERNYM is a binary relation predicate — Theme (subject) → Goal (object)
        var roles = LexicalVocabulary.Hypernym.SEED.slotRoles();
        assertThat(roles)
                .as("Relation verbs should have Theme and Goal slots")
                .hasSize(2);
        assertThat(roles.get(0)).isEqualTo(ItemID.fromString(ThematicRole.Theme.KEY));
        assertThat(roles.get(1)).isEqualTo(ItemID.fromString(ThematicRole.Goal.KEY));
    }

    @Test
    void editVerbHasPatientSlot() {
        var roles = CoreVocabulary.Edit.SEED.slotRoles();
        assertThat(roles).hasSize(1);
        assertThat(roles.get(0)).isEqualTo(ItemID.fromString(ThematicRole.Patient.KEY));
    }

    @Test
    void findVerbHasThreeSlots() {
        var roles = CoreVocabulary.Find.SEED.slotRoles();
        assertThat(roles).hasSize(3);
        assertThat(roles.get(0)).isEqualTo(ItemID.fromString(ThematicRole.Theme.KEY));
        assertThat(roles.get(1)).isEqualTo(ItemID.fromString(ThematicRole.Recipient.KEY));
        assertThat(roles.get(2)).isEqualTo(ItemID.fromString(ThematicRole.Source.KEY));
    }
}
