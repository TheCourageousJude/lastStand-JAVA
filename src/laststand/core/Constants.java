package laststand.core;

public final class Constants {
    private Constants() {}

    public static final String VERSION = "1.1.5";

    // Window / rendering
    public static final int SCREEN_WIDTH = 1000;
    public static final int SCREEN_HEIGHT = 740; // windowed height (was 700 for years, 800 briefly; 740 leaves the taskbar/desktop reachable); fullscreen scales from this design size
    public static final int HUD_HEIGHT = 150; // tall enough for the 52px ultimate box (and its electric flicker) beside the hotbar
    public static final int BUFF_BAR_HEIGHT = 48; // bottom bar showing both players' upgrade levels
    public static final int TILE_SIZE = 32;

    // Arena (world is bigger than the viewport -> camera scrolls, "free-roam")
    // Square now (was 50x45) -- unequal cols/rows made the horizontal arms reach noticeably
    // farther from the center than the vertical arms in the cross-shaped path. Square keeps
    // all 4 arms/portals the same distance from center.
    // ARENA_COLS x ARENA_ROWS is the PLAYABLE area (the cross, its corner blocks and the portals);
    // the rock border is added around it, so the playable distances stay exactly these numbers.
    public static final int ARENA_COLS = 55;
    public static final int ARENA_ROWS = 45; // odd sizes keep the cross perfectly symmetric (no 1-tile longer arm)
    // Irregular rock border around the playable area: thickness varies per tile between MIN and
    // MAX (so the island has a natural, uneven outline). The grid reserves MAX tiles on every side.
    public static final int ARENA_BORDER_MIN_TILES = 3;
    public static final int ARENA_BORDER_TILES = 6;
    public static final int ARM_HALF_WIDTH = 5; // half-width (in tiles) of the cross-shaped path
    public static final int SENTRY_HALF_SIZE = 2; // half-size (in tiles) of the center sentry block

    public static final int FPS = 60;
}
