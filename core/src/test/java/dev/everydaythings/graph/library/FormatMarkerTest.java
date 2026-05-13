package dev.everydaythings.graph.library;

import dev.everydaythings.graph.encoding.CgCbor;
import dev.everydaythings.graph.encoding.Encoding;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FormatMarkerTest {

    @Test
    @DisplayName("write creates .librarian/format with the encoder's FormatCode")
    void writeCreatesMarker(@TempDir Path root) throws Exception {
        FormatMarker.write(root, CgCbor.codec());

        Path markerPath = root.resolve(".librarian").resolve("format");
        assertThat(Files.exists(markerPath)).isTrue();
        byte[] bytes = Files.readAllBytes(markerPath);
        assertThat(bytes).hasSize(1);
        assertThat(bytes[0]).isEqualTo((byte) Encoding.CgCborV1.FORMAT_CODE);
    }

    @Test
    @DisplayName("read returns the stored FormatCode byte")
    void readReturnsStoredByte(@TempDir Path root) {
        FormatMarker.write(root, CgCbor.codec());
        byte stored = FormatMarker.read(root);
        assertThat(stored).isEqualTo((byte) Encoding.CgCborV1.FORMAT_CODE);
    }

    @Test
    @DisplayName("read throws NotFound when no marker exists at root")
    void readThrowsWhenMissing(@TempDir Path root) {
        assertThatThrownBy(() -> FormatMarker.read(root))
                .isInstanceOf(LibraryException.NotFound.class)
                .hasMessageContaining("No format marker");
    }

    @Test
    @DisplayName("verify matches when marker agrees with runtime encoder")
    void verifyMatches(@TempDir Path root) {
        FormatMarker.write(root, CgCbor.codec());
        // Does not throw.
        FormatMarker.verify(root, CgCbor.codec());
    }

    @Test
    @DisplayName("verify throws Corrupted when marker disagrees with runtime encoder")
    void verifyMismatch(@TempDir Path root) throws Exception {
        // Write a different format code into the marker (simulate a foreign encoder).
        Path markerPath = root.resolve(".librarian").resolve("format");
        Files.createDirectories(markerPath.getParent());
        Files.write(markerPath, new byte[]{(byte) 0x99});

        assertThatThrownBy(() -> FormatMarker.verify(root, CgCbor.codec()))
                .isInstanceOf(LibraryException.Corrupted.class)
                .hasMessageContaining("0x99")
                .hasMessageContaining("0x01");
    }

    @Test
    @DisplayName("write is idempotent under matching FormatCode (no-op-by-contents)")
    void writeIdempotent(@TempDir Path root) {
        FormatMarker.write(root, CgCbor.codec());
        // Second write of same code: should not throw.
        FormatMarker.write(root, CgCbor.codec());
        assertThat(FormatMarker.read(root)).isEqualTo((byte) Encoding.CgCborV1.FORMAT_CODE);
    }

    @Test
    @DisplayName("write throws Corrupted when marker exists with a different FormatCode")
    void writeRejectsCrossEncoder(@TempDir Path root) throws Exception {
        Path markerPath = root.resolve(".librarian").resolve("format");
        Files.createDirectories(markerPath.getParent());
        Files.write(markerPath, new byte[]{(byte) 0x77});

        assertThatThrownBy(() -> FormatMarker.write(root, CgCbor.codec()))
                .isInstanceOf(LibraryException.Corrupted.class)
                .hasMessageContaining("0x77")
                .hasMessageContaining("0x01");
    }

    @Test
    @DisplayName("read throws Corrupted on a malformed (non-1-byte) marker file")
    void readRejectsBadMarker(@TempDir Path root) throws Exception {
        Path markerPath = root.resolve(".librarian").resolve("format");
        Files.createDirectories(markerPath.getParent());
        Files.write(markerPath, new byte[]{(byte) 0x01, (byte) 0x02});  // 2 bytes — invalid

        assertThatThrownBy(() -> FormatMarker.read(root))
                .isInstanceOf(LibraryException.Corrupted.class)
                .hasMessageContaining("exactly 1 byte");
    }

    @Test
    @DisplayName("specific exception types subclass LibraryException for broad catching")
    void exceptionsAreCatchableAsLibraryException(@TempDir Path root) {
        assertThatThrownBy(() -> FormatMarker.read(root))
                .isInstanceOf(LibraryException.class);
    }
}
