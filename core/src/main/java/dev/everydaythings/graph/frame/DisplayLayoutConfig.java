package dev.everydaythings.graph.frame;

import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.Canonical.Canon;
import dev.everydaythings.graph.item.id.ItemID;
import lombok.Builder;
import lombok.Getter;

/**
 * Display position within a session's unified coordinate space.
 *
 * <p>Stored as the live instance on a DISPLAY_LAYOUT frame on a Session.
 * Maps a physical display (identified by host + displayId) to a rectangle
 * in session-space coordinates.
 *
 * <p>For single-host sessions, sessionX/Y equals the OS-reported position
 * from {@link DisplayConfig} (identity transform).
 *
 * @see dev.everydaythings.graph.language.ViewVocabulary.DisplayLayout
 */
@Getter
@Builder
@Canonical.Canonization
public class DisplayLayoutConfig implements Canonical {

    @Canon(order = 0) private ItemID hostId;
    @Canon(order = 1) private String displayId;
    @Canon(order = 2) private int sessionX;
    @Canon(order = 3) private int sessionY;
    @Canon(order = 4) private int widthPx;
    @Canon(order = 5) private int heightPx;

    /** No-arg constructor for Canonical decode. */
    @SuppressWarnings("unused")
    private DisplayLayoutConfig() {}

    private DisplayLayoutConfig(ItemID hostId, String displayId,
                                int sessionX, int sessionY, int widthPx, int heightPx) {
        this.hostId = hostId;
        this.displayId = displayId;
        this.sessionX = sessionX;
        this.sessionY = sessionY;
        this.widthPx = widthPx;
        this.heightPx = heightPx;
    }

    /**
     * Check if a rectangle (in session-space) intersects this display.
     */
    public boolean intersects(int x, int y, int w, int h) {
        return x < sessionX + widthPx && x + w > sessionX
                && y < sessionY + heightPx && y + h > sessionY;
    }
}
