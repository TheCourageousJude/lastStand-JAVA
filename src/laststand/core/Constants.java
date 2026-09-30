package laststand.core;

public final class Constants {
    private Constants() {}

    public static final String VERSION = "1.1.0";

    // Window / rendering
    public static final int SCREEN_WIDTH = 1000;
    public static final int SCREEN_HEIGHT = 700;
    public static final int HUD_HEIGHT = 136;
    public static final int BUFF_BAR_HEIGHT = 48; // bottom bar showing both players' 9 upgrade levels
    public static final int TILE_SIZE = 32;

    // Arena (world is bigger than the viewport -> camera scrolls, "free-roam")
    // Square now (was 50x45) -- unequal cols/rows made the horizontal arms reach noticeably
    // farther from the center than the vertical arms in the cross-shaped path. Square keeps
    // all 4 arms/portals the same distance from center.
    public static final int ARENA_COLS = 60;
    public static final int ARENA_ROWS = 40;
    public static final int ARM_HALF_WIDTH = 5; // half-width (in tiles) of the cross-shaped path
    public static final int SENTRY_HALF_SIZE = 2; // half-size (in tiles) of the center sentry block

    public static final int FPS = 60;
}
