package dev.everydaythings.graph.runtime.librarian;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

/**
 * LibrarianPresence — mutual-exclusion handle for a librarian's data
 * directory.
 *
 * <p>At most one librarian process may operate on a given
 * {@code <data-dir>/} at a time.  Mutual exclusion is enforced by an
 * advisory file lock on {@code <data-dir>/parley.pid}: the running
 * librarian holds the lock for its lifetime; another process attempting
 * to start at the same data dir fails fast.
 *
 * <h2>Acquire / release</h2>
 *
 * <ul>
 *   <li>{@link #acquire(Path)} opens the pidfile, attempts an exclusive
 *       lock, writes the current PID on success.  Throws
 *       {@link IllegalStateException} if the lock is held by another
 *       process.</li>
 *   <li>{@link #close()} releases the lock and removes the pidfile.</li>
 *   <li>JVM exit (clean or crash) releases the OS-held flock automatically;
 *       a stale pidfile may remain but is harmless — the next startup will
 *       overwrite it once it acquires the lock.</li>
 * </ul>
 *
 * <h2>Liveness probing</h2>
 *
 * <p>{@link #isAlive(Path)} probes the lock from another process by
 * attempting to acquire it momentarily.  Returns true iff the lock is
 * currently held — meaning a librarian is actively running at that data
 * dir.  Used by client discovery (e.g. {@code Graph} in {@code :ui}) to decide whether
 * to connect to a running librarian vs. start one embedded.
 *
 * <p>Note: liveness via flock is independent of socket reachability.  An
 * alive presence guarantees the OS-level "this process is running" check;
 * it doesn't by itself guarantee parley.sock is bound or accepting.  The
 * socket presence is a separate concern handled by the transport layer.
 */
public final class LibrarianPresence implements AutoCloseable {

    /** The conventional pidfile name within a librarian's data dir. */
    public static final String PID_FILE_NAME = "parley.pid";

    private final Path dataDir;
    private final Path pidFile;
    private final FileChannel channel;
    private final FileLock lock;
    private final long pid;
    private volatile boolean closed;

    private LibrarianPresence(Path dataDir, Path pidFile,
                              FileChannel channel, FileLock lock, long pid) {
        this.dataDir = dataDir;
        this.pidFile = pidFile;
        this.channel = channel;
        this.lock = lock;
        this.pid = pid;
    }

    // ==================================================================================
    // Acquire
    // ==================================================================================

    /**
     * Acquire exclusive presence at the given data directory.
     *
     * @throws IllegalStateException if another librarian is already running
     *         at {@code dataDir} (the lock is contended)
     * @throws UncheckedIOException if filesystem I/O fails
     */
    public static LibrarianPresence acquire(Path dataDir) {
        Objects.requireNonNull(dataDir, "dataDir");
        try {
            Files.createDirectories(dataDir);
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Cannot create data directory " + dataDir, e);
        }
        Path pidFile = dataDir.resolve(PID_FILE_NAME);

        FileChannel channel;
        try {
            channel = FileChannel.open(pidFile,
                    StandardOpenOption.READ,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.CREATE);
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Cannot open pidfile " + pidFile, e);
        }

        FileLock lock;
        try {
            lock = channel.tryLock();
        } catch (OverlappingFileLockException overlap) {
            closeQuietly(channel);
            throw new IllegalStateException(
                    "Another librarian is already running in this JVM at " + dataDir,
                    overlap);
        } catch (IOException e) {
            closeQuietly(channel);
            throw new UncheckedIOException(
                    "Failed to probe lock on " + pidFile, e);
        }
        if (lock == null) {
            // Lock is held by another process.
            String existingPid = readPidQuietly(channel);
            closeQuietly(channel);
            throw new IllegalStateException(
                    "Another librarian is already running at " + dataDir
                            + (existingPid.isEmpty() ? "" : " (pid " + existingPid + ")"));
        }

        // Lock acquired.  Overwrite any stale content with the current PID.
        long pid = ProcessHandle.current().pid();
        try {
            channel.truncate(0);
            channel.write(ByteBuffer.wrap(
                    Long.toString(pid).getBytes(StandardCharsets.UTF_8)));
            channel.force(true);
        } catch (IOException e) {
            try { lock.release(); } catch (IOException ignored) {}
            closeQuietly(channel);
            throw new UncheckedIOException(
                    "Failed to write pidfile " + pidFile, e);
        }
        return new LibrarianPresence(dataDir, pidFile, channel, lock, pid);
    }

    // ==================================================================================
    // Probes
    // ==================================================================================

    /**
     * Whether a librarian is currently running at the given data directory.
     *
     * <p>Returns {@code false} when:
     * <ul>
     *   <li>no pidfile exists, or</li>
     *   <li>the pidfile exists but no process currently holds the lock
     *       (previous librarian crashed and left a stale pidfile).</li>
     * </ul>
     *
     * <p>Returns {@code true} only when an exclusive lock is currently held
     * on the pidfile by some process — meaning a live librarian is
     * operating on this data dir.
     */
    public static boolean isAlive(Path dataDir) {
        Objects.requireNonNull(dataDir, "dataDir");
        Path pidFile = dataDir.resolve(PID_FILE_NAME);
        if (!Files.isRegularFile(pidFile)) {
            return false;
        }
        try (FileChannel ch = FileChannel.open(pidFile,
                StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            FileLock probe;
            try {
                probe = ch.tryLock();
            } catch (OverlappingFileLockException overlap) {
                // Same JVM holds the lock — definitely alive.
                return true;
            }
            if (probe == null) {
                return true;   // someone else holds it
            }
            probe.release();
            return false;       // we got it, so nobody else had it
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Read the PID written in the pidfile at the given data directory.
     * Returns empty if the file is missing, empty, or malformed.
     *
     * <p>Note: this is informational only.  Liveness should be probed via
     * {@link #isAlive(Path)} rather than checking whether the PID's process
     * still exists, because flock semantics are more reliable than
     * {@code kill -0}.
     */
    public static java.util.Optional<Long> readPid(Path dataDir) {
        Objects.requireNonNull(dataDir, "dataDir");
        Path pidFile = dataDir.resolve(PID_FILE_NAME);
        if (!Files.isRegularFile(pidFile)) {
            return java.util.Optional.empty();
        }
        try {
            String text = Files.readString(pidFile, StandardCharsets.UTF_8).trim();
            if (text.isEmpty()) return java.util.Optional.empty();
            return java.util.Optional.of(Long.parseLong(text));
        } catch (IOException | NumberFormatException e) {
            return java.util.Optional.empty();
        }
    }

    // ==================================================================================
    // Accessors
    // ==================================================================================

    /** The data directory whose presence is held. */
    public Path dataDir() { return dataDir; }

    /** The PID of the librarian process holding this presence. */
    public long pid() { return pid; }

    // ==================================================================================
    // Lifecycle
    // ==================================================================================

    /**
     * Release the lock and delete the pidfile.  Idempotent.
     */
    @Override
    public void close() {
        if (closed) return;
        closed = true;
        try { lock.release(); } catch (IOException ignored) {}
        try { channel.close(); } catch (IOException ignored) {}
        try { Files.deleteIfExists(pidFile); } catch (IOException ignored) {}
    }

    // ==================================================================================
    // Helpers
    // ==================================================================================

    private static String readPidQuietly(FileChannel channel) {
        try {
            ByteBuffer buf = ByteBuffer.allocate(64);
            channel.position(0);
            int n = channel.read(buf);
            if (n <= 0) return "";
            return new String(buf.array(), 0, n, StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            return "";
        }
    }

    private static void closeQuietly(FileChannel channel) {
        try { channel.close(); } catch (IOException ignored) {}
    }
}
