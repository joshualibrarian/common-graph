package dev.everydaythings.graph.bridges.keri;

import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.ref.DatumRef;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.cryptography.algorithm.Hash;
import dev.everydaythings.graph.cryptography.algorithm.KeyAgreement;
import dev.everydaythings.graph.cryptography.algorithm.Signing;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.runtime.librarian.Librarian;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@link MatterCode}'s {@code @Seed.Frame(theme = ...)}
 * declarations land in the graph as frames themed to the targeted algorithm
 * items.
 *
 * <p>This exercises three things at once:
 * <ul>
 *   <li>The new {@code theme()} attribute on {@code @Seed.Frame}.</li>
 *   <li>Seed scanning picking up {@link MatterCode}'s static code fields.</li>
 *   <li>The reference-binding index resolving "frames about algorithm X."</li>
 * </ul>
 */
@DisplayName("MatterCode frames")
class MatterCodeTest {

    @Test
    @DisplayName("SHA2-256 carries a matter-code frame with value 'I'")
    void sha2HasMatterCode() {
        Librarian lib = Librarian.inMemory();
        lib.bootstrap();
        assertMatterCode(lib, Hash.Sha256.KEY, MatterCode.SHA2_256);
    }

    @Test
    @DisplayName("Blake3 carries a matter-code frame with value 'E'")
    void blake3HasMatterCode() {
        Librarian lib = Librarian.inMemory();
        lib.bootstrap();
        assertMatterCode(lib, Hash.Blake3.KEY, MatterCode.BLAKE3_256);
    }

    @Test
    @DisplayName("Ed25519 carries both key and signature CESR codes")
    void ed25519HasKeyAndSignatureCodes() {
        Librarian lib = Librarian.inMemory();
        lib.bootstrap();
        List<String> codes = collectMatterCodes(lib, Signing.Ed25519.KEY);
        assertThat(codes).containsExactlyInAnyOrder(
                MatterCode.ED25519, MatterCode.ED25519_NT, MatterCode.ED25519_SIG);
    }

    @Test
    @DisplayName("X25519 carries a matter-code frame with value 'C'")
    void x25519HasMatterCode() {
        Librarian lib = Librarian.inMemory();
        lib.bootstrap();
        assertMatterCode(lib, KeyAgreement.X25519.KEY, MatterCode.X25519);
    }

    // ==================================================================================
    // Helpers
    // ==================================================================================

    private static void assertMatterCode(Librarian lib, String algorithmKey, String expectedCode) {
        List<String> codes = collectMatterCodes(lib, algorithmKey);
        assertThat(codes)
                .as("CESR matter codes themed to %s", algorithmKey)
                .contains(expectedCode);
    }

    private static List<String> collectMatterCodes(Librarian lib, String algorithmKey) {
        ItemRef algorithmIid = ItemRef.iid(algorithmKey);
        ItemRef themeRole = ItemRef.iid(ThematicRole.Theme.KEY);
        ItemRef matterCodePredicate = ItemRef.iid(MatterCode.KEY);
        ItemRef valueRole = ItemRef.iid(ThematicRole.Value.KEY);

        List<DatumRef> bodyCids = lib.library().bodyCidsForReferenceBinding(themeRole, algorithmIid);
        return bodyCids.stream()
                .map(cid -> lib.library().fetchBody(cid))
                .flatMap(Optional::stream)
                .filter(body -> matterCodePredicate.equals(body.head()))
                .map(body -> extractStringValue(body, valueRole))
                .flatMap(Optional::stream)
                .toList();
    }

    private static Optional<String> extractStringValue(Body body, ItemRef valueRole) {
        for (Binding b : body.bindings()) {
            if (b.role().equals(valueRole) && b.qualifiers().isEmpty()
                    && b.target() instanceof String s) {
                return Optional.of(s);
            }
        }
        return Optional.empty();
    }
}
