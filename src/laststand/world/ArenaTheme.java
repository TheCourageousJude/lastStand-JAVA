package laststand.world;

import java.awt.Color;

/**
 * Obstacle look reworked per the "stop looking like bordered blocks" reference mockup: flat
 * single-color fill (no bevel/border) with a small per-tile chance of a themed decoration doodle
 * (grass tuft / cactus) instead of the old beveled block. obstacleColor and decorationColor below
 * are pixel-sampled straight from that mockup.
 */
public enum ArenaTheme {
    FOREST("Forest", new Color(181, 230, 29), new Color(205, 205, 200), new Color(34, 177, 76)),
    DESERT("Desert", new Color(255, 195, 101), new Color(235, 220, 170), new Color(34, 177, 76));

    public final String displayName;
    public final Color obstacleColor;
    public final Color pathColor;
    public final Color decorationColor; // grass-tuft (Forest) / cactus (Desert) doodle color

    ArenaTheme(String displayName, Color obstacleColor, Color pathColor, Color decorationColor) {
        this.displayName = displayName;
        this.obstacleColor = obstacleColor;
        this.pathColor = pathColor;
        this.decorationColor = decorationColor;
    }
}
