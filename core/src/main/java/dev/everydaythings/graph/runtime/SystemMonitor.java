package dev.everydaythings.graph.runtime;

import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.Canonical.Canon;
import dev.everydaythings.graph.item.component.Tick;
import dev.everydaythings.graph.item.component.Type;
import dev.everydaythings.graph.ui.scene.Scene;
import dev.everydaythings.graph.ui.scene.Scene.Direction;
import dev.everydaythings.graph.ui.scene.surface.primitive.ProgressBarSurface;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.FileStore;
import java.nio.file.FileSystems;

/**
 * Live system metrics — CPU, memory, disk, and uptime.
 *
 * <p>Samples metrics every 2 seconds via {@code @Tick}.
 * Provides computed {@link ProgressBarSurface} instances for each metric,
 * with color thresholds: green ≤ 60%, yellow ≤ 80%, red > 80%.
 *
 * <p>Surface layout is a vertical stack of labeled progress bars with
 * a hostname header and uptime footer.
 *
 * @see ProgressBarSurface
 * @see Host
 */
@Getter
@NoArgsConstructor
@Canonical.Canonization
@Type(value = "cg:type/system-monitor", glyph = "📊")
@Scene.Container(direction = Direction.VERTICAL, gap = "0.75em",
        padding = "0.75em", width = "100%")
public class SystemMonitor implements Canonical {

    // --- Canonical: configuration ---

    @Canon(order = 0)
    private int refreshInterval = 2000;

    // --- Transient: runtime metrics (never serialized) ---

    private transient double cpuLoad;
    private transient long usedMemory;
    private transient long totalMemory;
    private transient long usedDisk;
    private transient long totalDisk;
    private transient long uptimeMs;
    private transient String hostname;
    private transient int processorCount;

    // ==================================================================================
    // Surface layout — vertical stack of labeled progress bars
    // ==================================================================================

    @Scene.Container(direction = Direction.HORIZONTAL, gap = "0.5em",
            style = "align-center")
    static class Header {
        @Scene.Text(content = "📊", style = "heading")
        static class Icon {}

        @Scene.Text(bind = "value.hostnameLabel", style = "heading")
        static class Hostname {}
    }

    @Scene.Container(direction = Direction.VERTICAL, gap = "0.125em")
    static class CpuSection {
        @Scene.Text(bind = "value.cpuLabel", style = {"muted", "small"})
        static class Label {}

        @Scene.Container(width = "100%")
        static class BarBox {
            @Scene.Embed(bind = "value.cpuBar")
            static class Bar {}
        }
    }

    @Scene.Container(direction = Direction.VERTICAL, gap = "0.125em")
    static class MemorySection {
        @Scene.Text(bind = "value.memoryLabel", style = {"muted", "small"})
        static class Label {}

        @Scene.Container(width = "100%")
        static class BarBox {
            @Scene.Embed(bind = "value.memoryBar")
            static class Bar {}
        }
    }

    @Scene.Container(direction = Direction.VERTICAL, gap = "0.125em")
    static class DiskSection {
        @Scene.Text(bind = "value.diskLabel", style = {"muted", "small"})
        static class Label {}

        @Scene.Container(width = "100%")
        static class BarBox {
            @Scene.Embed(bind = "value.diskBar")
            static class Bar {}
        }
    }

    @Scene.Text(bind = "value.uptimeLabel", style = {"muted", "small"})
    static class Uptime {}

    // ==================================================================================
    // Tick — sample metrics every 2s
    // ==================================================================================

    @Tick(interval = 2000)
    public void tick() {
        OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();

        processorCount = os.getAvailableProcessors();

        // CPU
        if (os instanceof com.sun.management.OperatingSystemMXBean sunOs) {
            cpuLoad = sunOs.getCpuLoad();
            if (cpuLoad < 0) {
                // Fallback: load average / processors
                double loadAvg = os.getSystemLoadAverage();
                cpuLoad = loadAvg >= 0 ? Math.min(loadAvg / processorCount, 1.0) : 0.0;
            }
        } else {
            double loadAvg = os.getSystemLoadAverage();
            cpuLoad = loadAvg >= 0 ? Math.min(loadAvg / processorCount, 1.0) : 0.0;
        }

        // Memory
        if (os instanceof com.sun.management.OperatingSystemMXBean sunOs) {
            totalMemory = sunOs.getTotalMemorySize();
            long freeMemory = sunOs.getFreeMemorySize();
            usedMemory = totalMemory - freeMemory;
        }

        // Disk — sum all real file stores
        long diskTotal = 0;
        long diskUsable = 0;
        for (FileStore store : FileSystems.getDefault().getFileStores()) {
            try {
                long total = store.getTotalSpace();
                if (total > 0) {
                    diskTotal += total;
                    diskUsable += store.getUsableSpace();
                }
            } catch (IOException ignored) {}
        }
        totalDisk = diskTotal;
        usedDisk = diskTotal - diskUsable;

        // Uptime
        uptimeMs = runtime.getUptime();

        // Hostname (sample once)
        if (hostname == null) {
            try {
                hostname = InetAddress.getLocalHost().getHostName();
            } catch (UnknownHostException e) {
                hostname = "localhost";
            }
        }
    }

    // ==================================================================================
    // Factory
    // ==================================================================================

    /** Create a SystemMonitor and take an initial sample. */
    public static SystemMonitor create() {
        SystemMonitor monitor = new SystemMonitor();
        monitor.tick();
        return monitor;
    }

    // ==================================================================================
    // Computed properties for surface binding
    // ==================================================================================

    public String hostnameLabel() {
        return hostname != null ? hostname : "…";
    }

    public String cpuLabel() {
        return String.format("CPU (%d cores)", processorCount);
    }

    public String memoryLabel() {
        return String.format("Memory  %s / %s",
                formatBytes(usedMemory), formatBytes(totalMemory));
    }

    public String diskLabel() {
        return String.format("Disk  %s / %s",
                formatBytes(usedDisk), formatBytes(totalDisk));
    }

    public String uptimeLabel() {
        return "Up " + formatUptime(uptimeMs);
    }

    /** Progress bar for CPU usage. */
    public ProgressBarSurface cpuBar() {
        return ProgressBarSurface.of(cpuLoad, thresholdColor(cpuLoad),
                String.format("%.0f%%", cpuLoad * 100));
    }

    /** Progress bar for memory usage. */
    public ProgressBarSurface memoryBar() {
        double ratio = totalMemory > 0 ? (double) usedMemory / totalMemory : 0.0;
        return ProgressBarSurface.of(ratio, thresholdColor(ratio),
                String.format("%.0f%%", ratio * 100));
    }

    /** Progress bar for disk usage. */
    public ProgressBarSurface diskBar() {
        double ratio = totalDisk > 0 ? (double) usedDisk / totalDisk : 0.0;
        return ProgressBarSurface.of(ratio, thresholdColor(ratio),
                String.format("%.0f%%", ratio * 100));
    }

    // ==================================================================================
    // Color thresholds
    // ==================================================================================

    /** Green ≤ 60%, yellow ≤ 80%, red > 80%. */
    private static String thresholdColor(double ratio) {
        if (ratio <= 0.6) return "#A6E3A1";  // green
        if (ratio <= 0.8) return "#F9E2AF";  // yellow
        return "#F38BA8";                      // red
    }

    // ==================================================================================
    // Formatting helpers
    // ==================================================================================

    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format("%.0f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format("%.0f MB", mb);
        double gb = mb / 1024.0;
        if (gb < 100) return String.format("%.1f GB", gb);
        return String.format("%.0f GB", gb);
    }

    private static String formatUptime(long ms) {
        long totalSeconds = ms / 1000;
        long days = totalSeconds / 86400;
        long hours = (totalSeconds % 86400) / 3600;
        long minutes = (totalSeconds % 3600) / 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0 || days > 0) sb.append(hours).append("h ");
        sb.append(minutes).append("m");
        return sb.toString();
    }
}
