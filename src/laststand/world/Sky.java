package laststand.world;

import laststand.core.Constants;

import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.Random;

/**
 * Everything drawn BEHIND the arena: a sky-blue backdrop with slow-drifting blocky clouds, and the
 * rocky rim / underside that makes the arena read as a floating island. The camera is centered on
 * the player and no longer clamped to the map, so near an edge you see this instead of a hard stop.
 *
 * Clouds sit in a tiling "parallax space" (they scroll at ~45% of camera speed, so they feel far
 * away) that repeats forever in every direction -- there is always sky to look at however far you
 * could theoretically pan. Layout is seeded and fixed, so it never flickers between frames.
 */
public final class Sky {
    private Sky() {}

    private static final double PARALLAX = 0.45;
    private static final int PERIOD_W = 2200, PERIOD_H = 1500; // parallax-space repeat size
    private static final double DRIFT_PX_PER_MS = 0.006;        // ~6 px/s, slow wind

    private static final int[][][] CLOUD_SHAPES = {
        // each cloud = a set of {x, y, w, h} blocks in cloud units (1 unit = a "pixel" of the cloud)
        {{0, 1, 7, 2}, {1, 0, 3, 1}, {4, -1, 2, 2}},               // wide, two bumps
        {{0, 1, 5, 2}, {1, 0, 3, 1}, {2, -1, 1, 1}},               // small puffy
        {{0, 2, 9, 2}, {1, 1, 4, 1}, {2, 0, 2, 1}, {6, 0, 2, 2}},  // long and low
    };

    private static final class Cloud {
        final double x, y; final int shape; final int unit;
        Cloud(double x, double y, int shape, int unit) { this.x = x; this.y = y; this.shape = shape; this.unit = unit; }
    }
    private static final Cloud[] CLOUDS = buildClouds();

    private static Cloud[] buildClouds() {
        Random r = new Random(20260705L);
        Cloud[] out = new Cloud[18];
        for (int i = 0; i < out.length; i++) {
            out[i] = new Cloud(r.nextDouble() * PERIOD_W, r.nextDouble() * PERIOD_H, r.nextInt(CLOUD_SHAPES.length), 14 + r.nextInt(3) * 6);
        }
        return out;
    }

    /** Sky gradient + clouds, filling a (vw x vh) viewport. timeMs only drives the slow cloud drift. */
    public static void drawSky(Graphics2D g, int camX, int camY, int vw, int vh, long timeMs) {
        g.setPaint(new GradientPaint(0, 0, new Color(92, 172, 238), 0, vh, new Color(178, 224, 250)));
        g.fillRect(0, 0, vw, vh);

        Object oldAA = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF); // crisp blocks
        double driftX = timeMs * DRIFT_PX_PER_MS;
        for (Cloud c : CLOUDS) {
            double px = floorMod(c.x - camX * PARALLAX - driftX, PERIOD_W);
            double py = floorMod(c.y - camY * PARALLAX, PERIOD_H);
            int[][] blocks = CLOUD_SHAPES[c.shape];
            int cw = 0, ch = 0;
            for (int[] b : blocks) { cw = Math.max(cw, b[0] + b[2]); ch = Math.max(ch, b[1] + b[3]); }
            // a cloud can straddle the seam of the repeat, so try each neighbouring copy
            for (int ox = -1; ox <= 0; ox++) {
                for (int oy = -1; oy <= 0; oy++) {
                    int sx = (int) (px + ox * PERIOD_W), sy = (int) (py + oy * PERIOD_H);
                    if (sx > vw || sy > vh || sx + cw * c.unit < 0 || sy + (ch + 1) * c.unit < 0) continue;
                    drawCloud(g, blocks, sx, sy, c.unit);
                }
            }
        }
        if (oldAA != null) g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA);
    }

    private static void drawCloud(Graphics2D g, int[][] blocks, int sx, int sy, int unit) {
        Color body = new Color(255, 255, 255, 240), shade = new Color(214, 232, 248, 240);
        for (int[] b : blocks) {
            g.setColor(body);
            g.fillRect(sx + b[0] * unit, sy + b[1] * unit, b[2] * unit, b[3] * unit);
        }
        // soft underside: the lowest row of each block column gets a bluish shade
        for (int[] b : blocks) {
            g.setColor(shade);
            g.fillRect(sx + b[0] * unit, sy + (b[1] + b[3]) * unit - unit / 3, b[2] * unit, unit / 3);
        }
    }

    private static double floorMod(double v, double m) {
        double r = v % m;
        return r < 0 ? r + m : r;
    }

    // ------------------------------------------------------------------ the floating island

    /**
     * The jagged blocky underside hanging below the island, following the border's uneven bottom
     * outline column by column (deepest toward the middle, tapering to the corners), so the arena
     * reads as a chunk of land floating in the sky. Draw after the sky and BEFORE the arena tiles.
     * (The sides and top need nothing extra: the rock border itself is their edge.)
     */
    public static void drawIsland(Graphics2D g, int camX, int camY, Arena arena) {
        int tile = Constants.TILE_SIZE;
        boolean desert = arena.theme == ArenaTheme.DESERT;
        Color rock1 = desert ? new Color(176, 130, 84) : new Color(104, 100, 98);
        Color rock2 = desert ? new Color(150, 108, 70) : new Color(86, 82, 82);
        Color rock3 = desert ? new Color(122, 88, 58) : new Color(68, 64, 66);
        Color pebble = desert ? new Color(204, 170, 130) : new Color(142, 138, 134);

        Object oldAA = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

        int cols = arena.grid.length;
        Random r = new Random(4242L);
        for (int c = 0; c < cols; c++) {
            int bottom = arena.lowestSolidRow(c);
            if (bottom < 0) continue;
            double edge = Math.min(c, cols - 1 - c) / (cols / 2.0);          // 0 at the corners .. 1 mid-map
            int depth = 1 + (int) Math.round(8 * Math.pow(edge, 0.75)) + r.nextInt(3) - 1;
            depth = Math.max(1, depth);
            int x = c * tile - camX;
            for (int row = 0; row < depth; row++) {
                int y = (bottom + 1 + row) * tile - camY;
                g.setColor(row < 2 ? rock1 : row < 5 ? rock2 : rock3);
                g.fillRect(x, y, tile, tile);
                if ((c * 31 + row * 17) % 9 == 0) {                           // the odd embedded stone
                    g.setColor(pebble);
                    g.fillRect(x + 8, y + 9, 10, 8);
                }
            }
        }
        if (oldAA != null) g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA);
    }
}
