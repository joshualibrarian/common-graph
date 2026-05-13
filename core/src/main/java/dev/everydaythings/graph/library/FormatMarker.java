package dev.everydaythings.graph.library;

import dev.everydaythings.graph.encoding.Encoding;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * The {@code .librarian/format} marker file — a single byte at
 * {@code <root>/.librarian/format} declaring which encoder a persisted
 * Librarian was created under.
 *
 * <p>Mirrors the {@code .item/iid} convention: a tiny, self-describing
 * protocol-level marker that any new system reading this directory can
 * consult to know how to interpret what it finds.
 *
 * <p>Written on {@code Librarian.fresh(path)}; read and validated on
 * {@code Librarian.load(path)}. A mismatch (file says one FormatCode, runtime
 * has another) is fatal — the runtime cannot proceed without speaking the
 * same encoding the bytes were written under.
 */
public final class FormatMarker {

    /** Subdirectory under the Librarian root that holds protocol-level markers. */
    public static final String LIBRARIAN_DIR = ".librarian";

    /** Marker file name within {@link #LIBRARIAN_DIR}. */
    public static final String FORMAT_FILE = "format";

    private FormatMarker() {}

    /** The full path to the marker file under {@code root}. */
    public static Path pathOf(Path root) {
        Objects.requireNonNull(root, "root");
        return root.resolve(LIBRARIAN_DIR).resolve(FORMAT_FILE);
    }

    /**
     * Write the encoder's FormatCode into the marker file at
     * {@code <root>/.librarian/format}. Creates the {@code .librarian/}
     * directory if needed. Idempotent — writing the same byte over an
     * existing marker is a no-op-by-contents.
     *
     * @throws LibraryException.Corrupted if the marker already exists with a
     *                                    different FormatCode (cross-encoder
     *                                    scenario — the bytes at this root
     *                                    were written under a different
     *                                    encoding than the runtime offers)
     * @throws LibraryException.IOError   on filesystem I/O failure
     */
    public static void write(Path root, Encoding encoder) {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(encoder, "encoder");
        byte code = encoder.formatCode();
        Path file = pathOf(root);
        try {
            Optional<Byte> existing = readIfPresent(root);
            if (existing.isPresent() && existing.get() != code) {
                throw new LibraryException.Corrupted(
                        "Format marker at " + file + " says 0x" + hex(existing.get())
                                + " but runtime encoder is 0x" + hex(code));
            }
            Files.createDirectories(file.getParent());
            Files.write(file, new byte[]{code});
        } catch (IOException e) {
            throw new LibraryException.IOError("Failed to write format marker at " + file, e);
        }
    }

    /**
     * Read the marker byte at {@code <root>/.librarian/format}.
     *
     * @throws LibraryException.NotFound  if the marker file does not exist
     * @throws LibraryException.Corrupted if the marker is the wrong length
     * @throws LibraryException.IOError   on filesystem I/O failure
     */
    public static byte read(Path root) {
        return readIfPresent(root).orElseThrow(() -> new LibraryException.NotFound(
                "No format marker at " + pathOf(root) + "; not a Librarian root"));
    }

    /** Optional read — empty if the marker file doesn't exist yet. */
    public static Optional<Byte> readIfPresent(Path root) {
        Objects.requireNonNull(root, "root");
        Path file = pathOf(root);
        if (!Files.exists(file)) return Optional.empty();
        try {
            byte[] bytes = Files.readAllBytes(file);
            if (bytes.length != 1) {
                throw new LibraryException.Corrupted(
                        "Format marker at " + file + " must be exactly 1 byte, found "
                                + bytes.length);
            }
            return Optional.of(bytes[0]);
        } catch (IOException e) {
            throw new LibraryException.IOError("Failed to read format marker at " + file, e);
        }
    }

    /**
     * Confirm that the marker's FormatCode matches the given encoder. Reads
     * the marker, throws if missing, throws if it disagrees.
     *
     * @throws LibraryException.NotFound  if no marker file exists
     * @throws LibraryException.Corrupted if the marker disagrees with the runtime encoder
     */
    public static void verify(Path root, Encoding encoder) {
        Objects.requireNonNull(encoder, "encoder");
        byte stored = read(root);
        byte runtime = encoder.formatCode();
        if (stored != runtime) {
            throw new LibraryException.Corrupted(
                    "Format marker at " + pathOf(root) + " is 0x" + hex(stored)
                            + " but runtime encoder is 0x" + hex(runtime));
        }
    }

    private static String hex(byte b) {
        return String.format("%02x", b & 0xFF);
    }
}
