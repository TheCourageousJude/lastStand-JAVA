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
        // seam "lines" between tiles -- turn it off for this flat, blocky tile pass only.
        Object oldHint = g.getRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_OFF);

        for (int c = startC; c <= endC; c++) {
            for (int r = startR; r <= endR; r++) {
                int tx = c * tile - camX, ty = r * tile - camY;
                if (grid[c][r] == TileType.OBSTACLE) {
                    g.setColor(theme.obstacleColor);
                    g.fillRect(tx, ty, tile, tile);
                    // simple bevel shading (light from top-left) so walls read as raised blocks
                    // from the bird's-eye view instead of flat tiles with a border line
                    g.setColor(theme.obstacleColor.brighter());
                    g.fillRect(tx, ty, tile, 3);
                    g.fillRect(tx, ty, 3, tile);
                    g.setColor(theme.obstacleColor.darker());
                    g.fillRect(tx, ty + tile - 4, tile, 4);
                    g.fillRect(tx + tile - 4, ty, 4, tile);
                } else {
                    g.setColor(theme.pathColor);
                    g.fillRect(tx, ty, tile, tile);
                }
            }
        }

        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                oldHint != null ? oldHint : java.awt.RenderingHints.VALUE_ANTIALIAS_DEFAULT);

        drawSentry(g, camX, camY);
        for (Portal p : portals) p.draw(g, camX, camY);
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
