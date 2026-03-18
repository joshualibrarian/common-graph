package dev.everydaythings.graph.language;

import dev.everydaythings.graph.item.ItemSeed;
import dev.everydaythings.graph.item.id.ItemID;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests verifying that vocabulary seed slot roles are correctly declared
 * in {@link ItemSeed} annotations.
 */
class VocabularySeedSlotTest {

    /** Read the slots array from the @ItemSeed annotation on the given class. */
    private static List<ItemID> slotsOf(Class<?> seedClass) {
        var annotation = seedClass.getAnnotation(ItemSeed.class);
        assertThat(annotation)
                .as("@ItemSeed annotation on %s", seedClass.getSimpleName())
                .isNotNull();
        return Arrays.stream(annotation.slots())
                .map(ItemID::fromString)
                .toList();
    }

    @Test
    void createVerbHasExpectedSlotRoles() {
        var roles = slotsOf(CoreVocabulary.Create.class);
        assertThat(roles)
                .as("CREATE should have 5 slot roles")
                .hasSize(5);

        assertThat(roles.get(0)).isEqualTo(ThematicRole.Theme.IID);
        assertThat(roles.get(1)).isEqualTo(ThematicRole.Goal.IID);
        assertThat(roles.get(2)).isEqualTo(ThematicRole.Name.IID);
        assertThat(roles.get(3)).isEqualTo(ThematicRole.Partner.IID);
        assertThat(roles.get(4)).isEqualTo(ThematicRole.Source.IID);
    }

    @Test
    void getVerbHasExpectedSlotRoles() {
        var roles = slotsOf(CoreVocabulary.Get.class);
        assertThat(roles)
                .as("GET should have 1 slot role")
                .hasSize(1);

        assertThat(roles.get(0)).isEqualTo(ThematicRole.Theme.IID);
    }

    @Test
    void relationVerbHasThemeAndGoalSlots() {
        // HYPERNYM is a binary relation predicate — Theme (subject) → Goal (object)
        var roles = slotsOf(LexicalVocabulary.Hypernym.class);
        assertThat(roles)
                .as("Relation verbs should have Theme and Goal slots")
                .hasSize(2);
        assertThat(roles.get(0)).isEqualTo(ThematicRole.Theme.IID);
        assertThat(roles.get(1)).isEqualTo(ThematicRole.Goal.IID);
    }

    @Test
    void editVerbHasPatientSlot() {
        var roles = slotsOf(CoreVocabulary.Edit.class);
        assertThat(roles).hasSize(1);
        assertThat(roles.get(0)).isEqualTo(ThematicRole.Patient.IID);
    }

    @Test
    void findVerbHasThreeSlots() {
        var roles = slotsOf(CoreVocabulary.Find.class);
        assertThat(roles).hasSize(3);
        assertThat(roles.get(0)).isEqualTo(ThematicRole.Theme.IID);
        assertThat(roles.get(1)).isEqualTo(ThematicRole.Recipient.IID);
        assertThat(roles.get(2)).isEqualTo(ThematicRole.Source.IID);
    }
}
