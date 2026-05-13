package dev.everydaythings.graph.frame;

import dev.everydaythings.graph.encoding.Canonical;
import lombok.Builder;
import lombok.Getter;

/**
 * Physical display properties — resolution, refresh rate, scale, OS position.
 *
 * <p>Stored as the live instance on a DISPLAY frame on a Host item.
 * Each connected monitor produces one DisplayConfig.
 *
 * @see dev.everydaythings.graph.language.DeviceVocabulary.Display
 */
@Getter
@Builder
@Canonical.Canonization
public class DisplayConfig implements Canonical {

    @Canon(order = 0) private String name;
    @Canon(order = 1) private int widthPx;
    @Canon(order = 2) private int heightPx;
    @Canon(order = 3) private int refreshRate;
    /** DPI scale as percentage — 100 = 1.0x, 200 = 2.0x (Retina). */
    @Canon(order = 4) private int scalePercent;
    @Canon(order = 5) private int osX;
    @Canon(order = 6) private int osY;

    /** No-arg constructor for Canonical decode. */
    @SuppressWarnings("unused")
    private DisplayConfig() {}

    private DisplayConfig(String name, int widthPx, int heightPx,
                          int refreshRate, int scalePercent, int osX, int osY) {
        this.name = name;
        this.widthPx = widthPx;
        this.heightPx = heightPx;
        this.refreshRate = refreshRate;
        this.scalePercent = scalePercent;
        this.osX = osX;
        this.osY = osY;
    }
}
