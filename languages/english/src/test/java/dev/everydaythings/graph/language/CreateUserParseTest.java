package dev.everydaythings.graph.language;

import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.Frame;
import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.ref.CompoundKey;
import dev.everydaythings.graph.ref.CompoundKey.Qualifier;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.text.FrameMap.Part;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.runtime.SubmitResult;
import dev.everydaythings.graph.runtime.librarian.Librarian;
import dev.everydaythings.graph.runtime.librarian.LibrarianVocabulary;
import dev.everydaythings.graph.runtime.user.User;
import dev.everydaythings.graph.value.identifier.Name;
import dev.everydaythings.graph.text.FrameMap;
import dev.everydaythings.graph.text.FrameMap.BindingMap;
import dev.everydaythings.graph.text.ParseParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * "create user" parses to CREATE{THEME→User} via the existing transitive-verb
 * pattern — the verb-plus-object case needs no create-specific parse code.
 * Supplying the expected Identifier ("... named bob") and wiring the parsed
 * frame to the create spine are the remaining slice-B work.
 */
class CreateUserParseTest {

    private Librarian librarian;
    private Item orchestrator;
    private ParseParams englishParams;

    @BeforeEach
    void setUp() {
        librarian = Librarian.inMemory();
        librarian.bootstrap();
        Optional<Item> englishItem = librarian.fetchItem(ItemRef.iid(Language.English.KEY));
        assertThat(englishItem).as("English seeded into bootstrap").isPresent();

        orchestrator = new Item(ItemRef.fromString("test.create-user-orchestrator"), librarian);
        englishParams = ParseParams.defaults().toBuilder()
                .languageStack(List.of(ItemRef.iid(Language.English.KEY)))
                .build();
    }

    @Test
    @DisplayName("\"create user\" → CREATE{THEME→User}")
    void parsesCreateUser() {
        FrameMap result = orchestrator.parse("create user", englishParams);

        assertThat(result.predicate()).as("predicate present").isNotNull();
        assertThat(result.predicate().value().iid())
                .as("predicate is CREATE")
                .isEqualTo(ItemRef.iid(LibrarianVocabulary.Create.KEY));

        BindingMap theme = bindingForRole(result, dev.everydaythings.graph.ThematicRole.Theme.KEY);
        assertThat(theme).as("THEME binding present").isNotNull();
        assertThat(theme.target().value())
                .as("THEME = User archetype")
                .isEqualTo(User.ARCHETYPE);
    }

    @Test
    @DisplayName("\"create user named bob\" → CREATE{THEME→User, Attribute[Name]→\"bob\"}")
    void parsesCreateUserNamed() {
        FrameMap result = orchestrator.parse("create user named bob", englishParams);

        assertThat(result.predicate()).as("predicate present").isNotNull();
        assertThat(result.predicate().value().iid())
                .as("predicate is CREATE")
                .isEqualTo(ItemRef.iid(LibrarianVocabulary.Create.KEY));

        BindingMap theme = bindingForRole(result, dev.everydaythings.graph.ThematicRole.Theme.KEY);
        assertThat(theme).as("THEME binding present").isNotNull();
        assertThat(theme.target().value()).as("THEME = User").isEqualTo(User.ARCHETYPE);

        BindingMap nameAttr = attributeBinding(result, Name.KEY);
        assertThat(nameAttr).as("Attribute[Name] binding from 'named bob'").isNotNull();
        assertThat(nameAttr.target().value()).as("Attribute[Name] = \"bob\"").isEqualTo("bob");
    }

    @Test
    @DisplayName("\"create user called bob\" also fills Attribute[Name] (dub-sense)")
    void parsesCreateUserCalled() {
        FrameMap result = orchestrator.parse("create user called bob", englishParams);
        BindingMap nameAttr = attributeBinding(result, Name.KEY);
        assertThat(nameAttr).as("Attribute[Name] binding from 'called bob'").isNotNull();
        assertThat(nameAttr.target().value()).isEqualTo("bob");
    }

    @Test
    @DisplayName("\"create user\" parses, converts, and reaches the create spine — rejected for the missing Identifier")
    void createUserReachesSpine() {
        FrameMap parsed = orchestrator.parse("create user", englishParams);
        Frame createFrame = Frame.of(parsed.toBody(), List.of());

        // End to end: text → parse → toBody → execute → librarian create handler →
        // ItemSketch completeness check.  "create user" supplies no Identifier, so
        // the spine rejects it, naming the missing expected field.  (Slice A will
        // turn this rejection into a prompt; "... named bob" will satisfy it.)
        assertThatThrownBy(() -> orchestrator.execute(createFrame))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("missing expected fields");
    }

    @Test
    @DisplayName("\"create user named bob\" mints a User named bob — full pipe")
    void createUserNamedMintsUser() {
        FrameMap parsed = orchestrator.parse("create user named bob", englishParams);
        Frame createFrame = Frame.of(parsed.toBody(), List.of());

        SubmitResult result = orchestrator.execute(createFrame);

        assertThat(result.responses()).as("the committed manifest comes back").hasSize(1);
        ItemRef newIid = (ItemRef) result.responses().get(0).body()
                .binding(CompoundKey.of(Manifest.ITEM_ID))
                .map(Binding::target)
                .orElseThrow();

        Item created = librarian.fetchItem(newIid).orElseThrow();
        assertThat(created).as("a User was minted").isInstanceOf(User.class);

        Object name = created.current().body()
                .binding(CompoundKey.of(ItemRef.iid(Name.KEY)))
                .map(Binding::target)
                .orElse(null);
        assertThat(name).as("the Name fill landed on the new user").isEqualTo("bob");
    }

    private static BindingMap bindingForRole(FrameMap fm, String roleKey) {
        ItemRef role = ItemRef.iid(roleKey);
        for (BindingMap b : fm.bindings()) {
            if (b.role() == null || b.role().value() == null) continue;
            if (role.equals(b.role().value().iid())) return b;
        }
        return null;
    }

    /** First binding with role=Attribute and a Sememe qualifier matching {@code kindKey}. */
    private static BindingMap attributeBinding(FrameMap fm, String kindKey) {
        ItemRef attribute = ItemRef.iid(dev.everydaythings.graph.ThematicRole.Attribute.KEY);
        ItemRef kind = ItemRef.iid(kindKey);
        for (BindingMap b : fm.bindings()) {
            if (b.role() == null || b.role().value() == null) continue;
            if (!attribute.equals(b.role().value().iid())) continue;
            for (Part<Qualifier> q : b.qualifiers()) {
                if (q != null && q.value() instanceof CompoundKey.Sememe sem
                        && kind.equals(sem.id())) {
                    return b;
                }
            }
        }
        return null;
    }
}
