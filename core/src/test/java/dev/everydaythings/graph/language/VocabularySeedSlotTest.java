package dev.everydaythings.graph.language;

import dev.everydaythings.graph.frame.ItemFrame;
import dev.everydaythings.graph.item.id.ItemID;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests verifying that vocabulary seed expected roles are correctly declared
 * as {@code @ItemFrame(predicate = Expects.KEY)} annotations.
 */
class VocabularySeedSlotTest {

    /**
     * Read the expected roles from @ItemFrame(predicate = Expects.KEY) annotations
     * on static fields of the given class. Returns the qualifier keys (which identify
     * the expected thematic role) in field declaration order.
     */
    private static List<ItemID> expectedRolesOf(Class<?> seedClass) {
        List<ItemID> roles = new ArrayList<>();
        for (Field field : seedClass.getDeclaredFields()) {
            var frame = field.getAnnotation(ItemFrame.class);
            if (frame != null && frame.predicate().equals(CoreVocabulary.Expects.KEY)) {
                // The qualifier on fieldAs identifies which role is expected
                var qualifiers = frame.fieldAs().qualifiers();
                assertThat(qualifiers)
                        .as("EXPECTS frame on %s.%s should have exactly one qualifier",
                                seedClass.getSimpleName(), field.getName())
                        .hasSize(1);
                roles.add(ItemID.fromString(qualifiers[0]));
            }
        }
        return roles;
    }

    @Test
    void createVerbHasExpectedSlotRoles() {
        var roles = expectedRolesOf(CoreVocabulary.Create.class);
        assertThat(roles)
                .as("CREATE should have 5 expected roles")
                .hasSize(5);

        assertThat(roles.get(0)).isEqualTo(ThematicRole.Theme.IID);
        assertThat(roles.get(1)).isEqualTo(ThematicRole.Goal.IID);
        assertThat(roles.get(2)).isEqualTo(ThematicRole.Name.IID);
        assertThat(roles.get(3)).isEqualTo(ThematicRole.Partner.IID);
        assertThat(roles.get(4)).isEqualTo(ThematicRole.Source.IID);
    }

    @Test
    void getVerbHasExpectedSlotRoles() {
        var roles = expectedRolesOf(CoreVocabulary.Get.class);
        assertThat(roles)
                .as("GET should have 1 expected role")
                .hasSize(1);

        assertThat(roles.get(0)).isEqualTo(ThematicRole.Theme.IID);
    }

    @Test
    void relationVerbHasThemeAndGoalSlots() {
        // HYPERNYM is a binary relation predicate — Theme (subject) → Goal (object)
        var roles = expectedRolesOf(LexicalVocabulary.Hypernym.class);
        assertThat(roles)
                .as("Relation verbs should have Theme and Goal expected roles")
                .hasSize(2);
        assertThat(roles.get(0)).isEqualTo(ThematicRole.Theme.IID);
        assertThat(roles.get(1)).isEqualTo(ThematicRole.Goal.IID);
    }

    @Test
    void editVerbHasPatientSlot() {
        var roles = expectedRolesOf(CoreVocabulary.Edit.class);
        assertThat(roles).hasSize(1);
        assertThat(roles.get(0)).isEqualTo(ThematicRole.Patient.IID);
    }

    @Test
    void findVerbHasThreeSlots() {
        var roles = expectedRolesOf(CoreVocabulary.Find.class);
        assertThat(roles).hasSize(3);
        assertThat(roles.get(0)).isEqualTo(ThematicRole.Theme.IID);
        assertThat(roles.get(1)).isEqualTo(ThematicRole.Recipient.IID);
        assertThat(roles.get(2)).isEqualTo(ThematicRole.Source.IID);
    }
}
