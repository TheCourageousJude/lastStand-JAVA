package laststand.world;

import java.awt.Color;

public enum ArenaTheme {
    FOREST("Forest", new Color(58, 130, 58), new Color(205, 205, 200)),
    DESERT("Desert", new Color(210, 120, 40), new Color(235, 220, 170));

    public final String displayName;
    public final Color obstacleColor;
    public final Color pathColor;

    ArenaTheme(String displayName, Color obstacleColor, Color pathColor) {
        this.displayName = displayName;
        this.obstacleColor = obstacleColor;
        this.pathColor = pathColor;
    }
}
