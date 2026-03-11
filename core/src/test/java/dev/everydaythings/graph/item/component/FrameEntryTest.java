package dev.everydaythings.graph.item.component;

import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.Hash;
import dev.everydaythings.graph.item.id.ContentID;
import dev.everydaythings.graph.item.id.FrameKey;
import dev.everydaythings.graph.item.id.ItemID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FrameEntryTest {

    static final FrameKey HANDLE = FrameKey.literal("test");
    static final ItemID TYPE = ItemID.fromString("cg:type/test");
    static final ItemID TARGET = ItemID.fromString("cg:test/target");

    @Nested
    @DisplayName("Reference mode")
    class ReferenceMode {

        @Test
        @DisplayName("reference() creates entry with referenceTarget set")
        void referenceFactoryCreatesEntry() {
            FrameEntry entry = FrameEntry.reference(HANDLE, TYPE, TARGET);

            assertThat(entry.isReference()).isTrue();
            assertThat(entry.payload().referenceTarget()).isEqualTo(TARGET);
            assertThat(entry.frameKey()).isEqualTo(HANDLE);
            assertThat(entry.type()).isEqualTo(TYPE);
        }

        @Test
        @DisplayName("reference() defaults identity to false")
        void referenceDefaultsIdentityFalse() {
            FrameEntry entry = FrameEntry.reference(HANDLE, TYPE, TARGET);
            assertThat(entry.identity()).isFalse();
        }

        @Test
        @DisplayName("reference with string handle preserves alias")
        void referenceWithStringHandle() {
            FrameEntry entry = FrameEntry.reference("myref", TYPE, TARGET);

            assertThat(entry.alias()).isEqualTo("myref");
            assertThat(entry.frameKey()).isEqualTo(FrameKey.literal("myref"));
        }

        @Test
        @DisplayName("reference with explicit identity flag")
        void referenceWithExplicitIdentity() {
            FrameEntry entry = FrameEntry.reference("myref", TYPE, TARGET, true);
            assertThat(entry.identity()).isTrue();
        }

        @Test
        @DisplayName("reference() rejects null target")
        void referenceRejectsNullTarget() {
            assertThatThrownBy(() -> FrameEntry.reference(HANDLE, TYPE, null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("reference has no snapshot")
        void referenceHasNoSnapshot() {
            FrameEntry entry = FrameEntry.reference(HANDLE, TYPE, TARGET);
            assertThat(entry.hasSnapshot()).isFalse();
        }

        @Test
        @DisplayName("reference is not stream-based")
        void referenceIsNotStream() {
            FrameEntry entry = FrameEntry.reference(HANDLE, TYPE, TARGET);
            assertThat(entry.hasStream()).isFalse();
        }

        @Test
        @DisplayName("reference is not a local resource")
        void referenceIsNotLocalResource() {
            FrameEntry entry = FrameEntry.reference(HANDLE, TYPE, TARGET);
            assertThat(entry.isLocalResource()).isFalse();
        }

        @Test
        @DisplayName("reference emoji is link symbol")
        void referenceEmojiIsLink() {
            FrameEntry entry = FrameEntry.reference(HANDLE, TYPE, TARGET);
            assertThat(entry.emoji()).isEqualTo("\uD83D\uDD17");
        }

        @Test
        @DisplayName("reference displaySubtitle shows arrow to target")
        void referenceDisplaySubtitle() {
            FrameEntry entry = FrameEntry.reference("chess", TYPE, TARGET);
            String subtitle = entry.displaySubtitle();
            assertThat(subtitle).startsWith("\u2192 ");
        }
    }

    @Nested
    @DisplayName("Mode predicates - non-reference entries")
    class ModePredicates {

        @Test
        @DisplayName("snapshot is not a reference")
        void snapshotIsNotReference() {
            ContentID cid = new ContentID(new byte[32], Hash.DEFAULT);
            FrameEntry entry = FrameEntry.snapshot(HANDLE, TYPE, cid);
            assertThat(entry.isReference()).isFalse();
        }

        @Test
        @DisplayName("stream is not a reference")
        void streamIsNotReference() {
            FrameEntry entry = FrameEntry.stream(HANDLE, TYPE, List.of(), true);
            assertThat(entry.isReference()).isFalse();
        }

        @Test
        @DisplayName("local resource is not a reference")
        void localResourceIsNotReference() {
            FrameEntry entry = FrameEntry.localResource(HANDLE, TYPE);
            assertThat(entry.isReference()).isFalse();
            assertThat(entry.isLocalResource()).isTrue();
        }
    }

    @Nested
    @DisplayName("CBOR round-trip")
    class CborRoundTrip {

        @Test
        @DisplayName("reference entry survives encode/decode")
        void referenceRoundTrip() {
            FrameEntry original = FrameEntry.reference("myref", TYPE, TARGET);
            byte[] bytes = original.encodeBinary(Canonical.Scope.RECORD);
            FrameEntry decoded = FrameEntry.decode(bytes);

            assertThat(decoded.isReference()).isTrue();
            assertThat(decoded.payload().referenceTarget()).isEqualTo(TARGET);
            assertThat(decoded.alias()).isEqualTo("myref");
            assertThat(decoded.type()).isEqualTo(TYPE);
            assertThat(decoded.identity()).isFalse();
        }

        @Test
        @DisplayName("snapshot entry still decodes correctly (backward compat)")
        void snapshotStillDecodes() {
            ContentID cid = new ContentID(new byte[32], Hash.DEFAULT);
            FrameEntry original = FrameEntry.snapshot("snap", TYPE, cid);
            byte[] bytes = original.encodeBinary(Canonical.Scope.RECORD);
            FrameEntry decoded = FrameEntry.decode(bytes);

            assertThat(decoded.isReference()).isFalse();
            assertThat(decoded.payload().referenceTarget()).isNull();
            assertThat(decoded.hasSnapshot()).isTrue();
        }
    }
}
