package laststand.world;

import laststand.core.Constants;
import laststand.entity.Entity;

import java.awt.Color;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Cross-shaped arena matching the hand-drawn map: a plus-shaped walkable
 * path with obstacle-filled corners, a portal at the tip of each of the 4
 * arms, and a sentry block + "trade zone" ring in the center.
 */
public class Arena {

    public final ArenaTheme theme;
    public final TileType[][] grid; // [col][row]
    public final int worldWidth, worldHeight; // px
    public final List<Portal> portals = new ArrayList<>();
    private final int centerCol, centerRow;
    private final Random rng = new Random();

    public Arena(ArenaTheme theme) {
        this.theme = theme;
        int cols = Constants.ARENA_COLS;
        int rows = Constants.ARENA_ROWS;
        this.grid = new TileType[cols][rows];
        this.worldWidth = cols * Constants.TILE_SIZE;
        this.worldHeight = rows * Constants.TILE_SIZE;
        this.centerCol = cols / 2;
        this.centerRow = rows / 2;
        generateCross();
        generateDecorations();
        createPortals();
    }

    // Reverted: the Enchanting Center is passable again -- making it a solid obstacle let
    // mob AI (no real pathfinding) get permanently stuck against it, and made the center
    // annoyingly hard to reach for players at times. It's back to purely visual, same as
    // the original "sentry" design.

    private void generateCross() {
        int half = Constants.ARM_HALF_WIDTH;
        for (int c = 0; c < grid.length; c++) {
            for (int r = 0; r < grid[0].length; r++) {
                boolean inVerticalArm = Math.abs(c - centerCol) <= half;
                boolean inHorizontalArm = Math.abs(r - centerRow) <= half;
                grid[c][r] = (inVerticalArm || inHorizontalArm) ? TileType.PATH : TileType.OBSTACLE;
            }
        }
    }

    // Which obstacle tiles get a decoration doodle (grass tuft / cactus) drawn on top -- rolled
    // ONCE at generation time and stored, not re-rolled every frame, so a decorated tile stays
    // decorated (no flicker) as the camera pans past it. Collision is unaffected either way --
    // decoration is purely visual, checked nowhere in moveWithCollision()/isObstacleAtWorld().
    private static final double DECORATION_CHANCE = 0.05; // "5% chance on each affected tile"
    private boolean[][] decorated;

    private void generateDecorations() {
        decorated = new boolean[grid.length][grid[0].length];
        for (int c = 0; c < grid.length; c++) {
            for (int r = 0; r < grid[0].length; r++) {
                if (grid[c][r] == TileType.OBSTACLE) {
                    decorated[c][r] = rng.nextDouble() < DECORATION_CHANCE;
                }
            }
        }
    }

    private void createPortals() {
        int tile = Constants.TILE_SIZE;
        double cx = centerCol * tile + tile / 2.0;
        double cy = centerRow * tile + tile / 2.0;
        portals.add(new Portal(Portal.Dir.NORTH, cx, tile * 1.5));
        portals.add(new Portal(Portal.Dir.SOUTH, cx, worldHeight - tile * 1.5));
        portals.add(new Portal(Portal.Dir.WEST, tile * 1.5, cy));
        portals.add(new Portal(Portal.Dir.EAST, worldWidth - tile * 1.5, cy));
    }

    public boolean isObstacleAtWorld(double wx, double wy) {
        int c = (int) (wx / Constants.TILE_SIZE);
        int r = (int) (wy / Constants.TILE_SIZE);
        if (c < 0 || r < 0 || c >= grid.length || r >= grid[0].length) return true; // out of bounds = blocked
        return grid[c][r] == TileType.OBSTACLE;
    }

    private boolean rectBlocked(double x, double y, int size) {
        return isObstacleAtWorld(x, y) || isObstacleAtWorld(x + size, y)
                || isObstacleAtWorld(x, y + size) || isObstacleAtWorld(x + size, y + size);
    }

    /** Moves the entity by (dx, dy), sliding along walls instead of stopping dead. */
    public void moveWithCollision(Entity e, double dx, double dy) {
        double newX = clamp(e.x + dx, 0, worldWidth - e.size);
        if (!rectBlocked(newX, e.y, e.size)) {
            e.x = newX;
        }
        double newY = clamp(e.y + dy, 0, worldHeight - e.size);
        if (!rectBlocked(e.x, newY, e.size)) {
            e.y = newY;
        }
    }

    private double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /** Random open spot within radius of the portal, used for "random spawn from portal". */
    public double[] randomSpawnNear(Portal portal, int size) {
        for (int attempt = 0; attempt < 15; attempt++) {
            double angle = rng.nextDouble() * Math.PI * 2;
            double dist = rng.nextDouble() * Constants.TILE_SIZE * 2.5;
            double x = portal.x + Math.cos(angle) * dist - size / 2.0;
            double y = portal.y + Math.sin(angle) * dist - size / 2.0;
            x = clamp(x, 0, worldWidth - size);
            y = clamp(y, 0, worldHeight - size);
            if (!rectBlocked(x, y, size)) {
                return new double[]{x, y};
            }
        }
        return new double[]{portal.x - size / 2.0, portal.y - size / 2.0};
    }

    public double centerX() { return centerCol * Constants.TILE_SIZE + Constants.TILE_SIZE / 2.0; }
    public double centerY() { return centerRow * Constants.TILE_SIZE + Constants.TILE_SIZE / 2.0; }

    /** World-space radius (px) at which a player is "close enough" to interact with the
     *  Enchanting Center -- matches the dashed trade-zone ring drawn around it. */
    public double enchantInteractRadius() {
        return (Constants.SENTRY_HALF_SIZE * Constants.TILE_SIZE) + Constants.TILE_SIZE;
    }

    public boolean isNearEnchantCenter(double px, double py) {
        return Math.hypot(px - centerX(), py - centerY()) <= enchantInteractRadius();
    }

    public int[] clampCamera(double focusX, double focusY, int viewportW, int viewportH) {
        int camX = (int) (focusX - viewportW / 2.0);
        int camY = (int) (focusY - viewportH / 2.0);
        camX = (int) clamp(camX, 0, Math.max(0, worldWidth - viewportW));
        camY = (int) clamp(camY, 0, Math.max(0, worldHeight - viewportH));
        return new int[]{camX, camY};
    }

    public void draw(Graphics2D g, int camX, int camY, int viewportW, int viewportH) {
        int tile = Constants.TILE_SIZE;
        int startC = Math.max(0, camX / tile);
        int startR = Math.max(0, camY / tile);
        int endC = Math.min(grid.length - 1, (camX + viewportW) / tile + 1);
        int endR = Math.min(grid[0].length - 1, (camY + viewportH) / tile + 1);

        // Antialiasing blurs the edges of adjacent same-size fillRects, which reads as thin
        // seam "lines" between tiles -- turn it off for this flat tile-fill pass only. Obstacles
        // are a flat single-color fill now (no bevel/border -- that was the "blocky" look this
        // replaced); the rare decoration doodle is a separate pass below, with AA back on.
        Object oldHint = g.getRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_OFF);

        for (int c = startC; c <= endC; c++) {
            for (int r = startR; r <= endR; r++) {
                int tx = c * tile - camX, ty = r * tile - camY;
                g.setColor(grid[c][r] == TileType.OBSTACLE ? theme.obstacleColor : theme.pathColor);
                g.fillRect(tx, ty, tile, tile);
            }
        }

        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                oldHint != null ? oldHint : java.awt.RenderingHints.VALUE_ANTIALIAS_DEFAULT);

        // Decoration pass: the ~5% of obstacle tiles rolled in generateDecorations() get a small
        // grass-tuft (Forest) or cactus (Desert) doodle so the terrain doesn't read as a uniform
        // grid. AA is back on here since these are small organic line shapes, not edge-to-edge
        // fills, so there's no seam risk.
        for (int c = startC; c <= endC; c++) {
            for (int r = startR; r <= endR; r++) {
                if (grid[c][r] == TileType.OBSTACLE && decorated[c][r]) {
                    int tx = c * tile - camX, ty = r * tile - camY;
                    drawDecoration(g, tx, ty, tile, c, r);
                }
            }
        }

        drawSentry(g, camX, camY);
        for (Portal p : portals) p.draw(g, camX, camY);
    }

    /** Draws one tile's decoration doodle. Seeded off the tile's WORLD (c, r) indices, not its
     *  on-screen position, so the exact same shape/jitter is redrawn every frame regardless of
     *  camera position -- using screen coords for the seed would make it "swim" as you pan. */
    private void drawDecoration(Graphics2D g, int tx, int ty, int tile, int c, int r) {
        Random seeded = new Random(c * 92821L + r * 68917L);
        g.setColor(theme.decorationColor);
        java.awt.Stroke oldStroke = g.getStroke();
        g.setStroke(new java.awt.BasicStroke(2.2f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
        if (theme == ArenaTheme.DESERT) {
            drawCactus(g, tx, ty, tile, seeded);
        } else {
            drawGrassTuft(g, tx, ty, tile, seeded);
        }
        g.setStroke(oldStroke);
    }

    /** A couple of short grass-blade strokes near the tile's base, jittered per tile so a patch
     *  of decorated tiles doesn't all look identical -- loosely matches the reference sketch. */
    private void drawGrassTuft(Graphics2D g, int tx, int ty, int tile, Random seeded) {
        int baseY = ty + tile - 3 - seeded.nextInt(5);
        int blades = 2 + seeded.nextInt(2); // 2-3 blades
        for (int i = 0; i < blades; i++) {
            int bx = tx + tile / 6 + seeded.nextInt(tile - tile / 3);
            int h = tile / 3 + seeded.nextInt(tile / 4);
            int kinkX = bx + (seeded.nextBoolean() ? 1 : -1) * (2 + seeded.nextInt(4));
            int kinkY = baseY - h / 2;
            g.drawLine(bx, baseY, kinkX, kinkY);
            int tipX = kinkX + (seeded.nextBoolean() ? 1 : -1) * (2 + seeded.nextInt(3));
            g.drawLine(kinkX, kinkY, tipX, baseY - h);
        }
    }

    /** A small saguaro-style cactus: a vertical stem with 1-2 side arms, matching the reference
     *  sketch. Jittered per tile for a bit of variety across a patch of decorated tiles. */
    private void drawCactus(Graphics2D g, int tx, int ty, int tile, Random seeded) {
        int cx = tx + tile / 2 + seeded.nextInt(7) - 3;
        int baseY = ty + tile - 3;
        int stemH = tile / 2 + seeded.nextInt(tile / 4);
        int topY = baseY - stemH;
        g.drawLine(cx, baseY, cx, topY); // main stem

        int leftY = baseY - stemH / 3;
        g.drawLine(cx, leftY, cx - tile / 4, leftY);
        g.drawLine(cx - tile / 4, leftY, cx - tile / 4, leftY - tile / 4);

        if (seeded.nextDouble() < 0.7) { // right arm only sometimes, per the sketch's uneven look
            int rightY = baseY - (int) (stemH * 0.6);
            g.drawLine(cx, rightY, cx + tile / 5, rightY);
            g.drawLine(cx + tile / 5, rightY, cx + tile / 5, rightY - tile / 5);
        }
    }

    private void drawSentry(Graphics2D g, int camX, int camY) {
        int tile = Constants.TILE_SIZE;
        int half = Constants.SENTRY_HALF_SIZE * tile;
        int cx = (int) centerX() - camX;
        int cy = (int) centerY() - camY;

        // trade zone ring (dashed) -- the Enchanting Center's interact boundary, now colored
        // to match the center block per your note
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setColor(new Color(90, 90, 200));
        g2.setStroke(new java.awt.BasicStroke(2, java.awt.BasicStroke.CAP_BUTT,
                java.awt.BasicStroke.JOIN_MITER, 10, new float[]{8, 6}, 0));
        int ringPad = half + tile;
        g2.drawRect(cx - ringPad, cy - ringPad, ringPad * 2, ringPad * 2);
        g2.dispose();

        // sentry block
        g.setColor(new Color(90, 90, 200));
        g.fillRect(cx - half, cy - half, half * 2, half * 2);
        g.setColor(Color.BLACK);
        g.drawRect(cx - half, cy - half, half * 2, half * 2);
    }
}
