package dev.everydaythings.graph.language;

import dev.everydaythings.graph.item.id.ItemID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests verifying that VerbSememe slot roles are correctly populated
 * from seed declarations.
 */
class VerbSememePersistenceTest {

    @Test
    void createVerbHasExpectedSlotRoles() {
        var roles = Sememe.create.slotRoles();
        assertThat(roles)
                .as("CREATE should have 5 slot roles")
                .hasSize(5);

        assertThat(roles.get(0)).isEqualTo(ItemID.fromString(ThematicRole.Theme.KEY));
        assertThat(roles.get(1)).isEqualTo(ItemID.fromString(ThematicRole.Target.KEY));
        assertThat(roles.get(2)).isEqualTo(ItemID.fromString(ThematicRole.Name.KEY));
        assertThat(roles.get(3)).isEqualTo(ItemID.fromString(ThematicRole.Comitative.KEY));
        assertThat(roles.get(4)).isEqualTo(ItemID.fromString(ThematicRole.Source.KEY));
    }

    @Test
    void getVerbHasExpectedSlotRoles() {
        var roles = Sememe.get.slotRoles();
        assertThat(roles)
                .as("GET should have 1 slot role")
                .hasSize(1);

        assertThat(roles.get(0)).isEqualTo(ItemID.fromString(ThematicRole.Theme.KEY));
    }

    @Test
    void relationVerbHasNoSlotRoles() {
        // HYPERNYM is a relation predicate, not a user-facing action — no slots
        assertThat(Sememe.HYPERNYM.slotRoles())
                .as("Relation verbs should have no slot roles")
                .isEmpty();
    }

    @Test
    void editVerbHasPatientSlot() {
        var roles = Sememe.edit.slotRoles();
        assertThat(roles).hasSize(1);
        assertThat(roles.get(0)).isEqualTo(ItemID.fromString(ThematicRole.Patient.KEY));
    }

    @Test
    void findVerbHasThreeSlots() {
        var roles = Sememe.find.slotRoles();
        assertThat(roles).hasSize(3);
        assertThat(roles.get(0)).isEqualTo(ItemID.fromString(ThematicRole.Theme.KEY));
        assertThat(roles.get(1)).isEqualTo(ItemID.fromString(ThematicRole.Recipient.KEY));
        assertThat(roles.get(2)).isEqualTo(ItemID.fromString(ThematicRole.Source.KEY));
    }
}
