package laststand.world;

import laststand.core.Constants;
import laststand.entity.Entity;

import java.awt.Color;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Random;

/**
 * Cross-shaped arena matching the hand-drawn map: a plus-shaped walkable
 * path with obstacle-filled corners, a portal at the tip of each of the 4
 * arms, and a sentry block + "trade zone" ring in the center.
 *
 * The playable cross is wrapped in an irregular rock border (at least ARENA_BORDER_MIN_TILES thick
 * everywhere, up to ARENA_BORDER_TILES, with rounded corners) so the arena reads as a natural
 * floating island; beyond the border's uneven outline is VOID (sky). Both ROCK and VOID are solid.
 */
public class Arena {

    public final ArenaTheme theme;
    public final TileType[][] grid; // [col][row]
    public final int worldWidth, worldHeight; // px
    public final List<Portal> portals = new ArrayList<>();
    /** Pixel bounds of the PLAYABLE rectangle inside the grid: [playX0, playX1) x [playY0, playY1). */
    public final int playX0, playY0, playX1, playY1;
    private final int centerCol, centerRow;
    private final Random rng = new Random();

    public Arena(ArenaTheme theme) {
        this.theme = theme;
        int b = Constants.ARENA_BORDER_TILES;
        int cols = Constants.ARENA_COLS + 2 * b;
        int rows = Constants.ARENA_ROWS + 2 * b;
        this.grid = new TileType[cols][rows];
        this.worldWidth = cols * Constants.TILE_SIZE;
        this.worldHeight = rows * Constants.TILE_SIZE;
        this.playX0 = b * Constants.TILE_SIZE;
        this.playY0 = b * Constants.TILE_SIZE;
        this.playX1 = (b + Constants.ARENA_COLS) * Constants.TILE_SIZE;
        this.playY1 = (b + Constants.ARENA_ROWS) * Constants.TILE_SIZE;
        this.centerCol = cols / 2; // the border is the same size on every side, so this is also the playable center
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
        int b = Constants.ARENA_BORDER_TILES;
        int pc0 = b, pc1 = b + Constants.ARENA_COLS;   // playable columns [pc0, pc1)
        int pr0 = b, pr1 = b + Constants.ARENA_ROWS;   // playable rows    [pr0, pr1)

        // Border thickness (in tiles) along each side, as a bounded random walk -- seeded so the island
        // is the same shape every run, but uneven along every edge. Never below ARENA_BORDER_MIN_TILES.
        int[] tTop = thicknessWalk(Constants.ARENA_COLS, 1001L), tBottom = thicknessWalk(Constants.ARENA_COLS, 2002L);
        int[] tLeft = thicknessWalk(Constants.ARENA_ROWS, 3003L), tRight = thicknessWalk(Constants.ARENA_ROWS, 4004L);

        for (int c = 0; c < grid.length; c++) {
            for (int r = 0; r < grid[0].length; r++) {
                boolean left = c < pc0, right = c >= pc1, top = r < pr0, bottom = r >= pr1;
                if (!left && !right && !top && !bottom) {
                    boolean inVerticalArm = Math.abs(c - centerCol) <= half;
                    boolean inHorizontalArm = Math.abs(r - centerRow) <= half;
                    grid[c][r] = (inVerticalArm || inHorizontalArm) ? TileType.PATH : TileType.OBSTACLE;
                    continue;
                }
                boolean rock;
                if (!top && !bottom) {                        // left / right strip: depth measured outward from the playable edge
                    int row = r - pr0;
                    rock = left ? (pc0 - c) <= tLeft[row] : (c - pc1 + 1) <= tRight[row];
                } else if (!left && !right) {                 // top / bottom strip
                    int col = c - pc0;
                    rock = top ? (pr0 - r) <= tTop[col] : (r - pr1 + 1) <= tBottom[col];
                } else {                                      // corner square: a rounded (elliptical) blob
                    int dx = left ? pc0 - c : c - pc1 + 1;
                    int dy = top ? pr0 - r : r - pr1 + 1;
                    int limX = left ? (top ? tLeft[0] : tLeft[Constants.ARENA_ROWS - 1]) : (top ? tRight[0] : tRight[Constants.ARENA_ROWS - 1]);
                    int limY = top ? (left ? tTop[0] : tTop[Constants.ARENA_COLS - 1]) : (left ? tBottom[0] : tBottom[Constants.ARENA_COLS - 1]);
                    double e = (dx / (double) limX) * (dx / (double) limX) + (dy / (double) limY) * (dy / (double) limY);
                    rock = e <= 1.15;
                }
                grid[c][r] = rock ? TileType.ROCK : TileType.VOID;
            }
        }
    }

    /** Border thickness for each of n tiles along one side: wanders between MIN and MAX tiles. */
    private static int[] thicknessWalk(int n, long seed) {
        Random r = new Random(seed);
        int min = Constants.ARENA_BORDER_MIN_TILES, max = Constants.ARENA_BORDER_TILES;
        int v = min + 1 + r.nextInt(max - min);
        int[] out = new int[n];
        for (int i = 0; i < n; i++) {
            int roll = r.nextInt(10);
            if (roll < 3) v--; else if (roll < 6) v++;
            v = Math.max(min, Math.min(max, v));
            out[i] = v;
        }
        return out;
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
        // measured from the playable area's edges (the rock border lies beyond them)
        portals.add(new Portal(Portal.Dir.NORTH, cx, playY0 + tile * 1.5));
        portals.add(new Portal(Portal.Dir.SOUTH, cx, playY1 - tile * 1.5));
        portals.add(new Portal(Portal.Dir.WEST, playX0 + tile * 1.5, cy));
        portals.add(new Portal(Portal.Dir.EAST, playX1 - tile * 1.5, cy));
    }

    public boolean isObstacleAtWorld(double wx, double wy) {
        int c = (int) (wx / Constants.TILE_SIZE);
        int r = (int) (wy / Constants.TILE_SIZE);
        if (c < 0 || r < 0 || c >= grid.length || r >= grid[0].length) return true; // out of bounds = blocked
        return grid[c][r] != TileType.PATH; // obstacle blocks, rock border and open sky all block
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

    /** Open spot on a ring of the given radius around (cx, cy) at 'angle'; pulls inward if blocked. */
    public double[] openSpotAround(double cx, double cy, double radius, double angle, int size) {
        for (double f = 1.0; f > 0.1; f -= 0.25) {
            double x = clamp(cx + Math.cos(angle) * radius * f - size / 2.0, 0, worldWidth - size);
            double y = clamp(cy + Math.sin(angle) * radius * f - size / 2.0, 0, worldHeight - size);
            if (!rectBlocked(x, y, size)) return new double[]{x, y};
        }
        return new double[]{clamp(cx - size / 2.0, 0, worldWidth - size), clamp(cy - size / 2.0, 0, worldHeight - size)};
    }


    // ------------------------------------------------------------------ enemy navigation
    // Enemies used to walk straight at the player and (if ranged) stop the moment they were "in range",
    // with no idea walls existed -- so they got pinned against the corner blocks, or parked behind one
    // firing into it. These helpers give them (1) a real line-of-sight test that matches how shots
    // actually die on walls, and (2) a shortest-path map around the obstacle blocks to follow
    // whenever the straight route is blocked.

    /**
     * True if a shot flying straight from (x1,y1) to (x2,y2) would never touch an obstacle tile --
     * the same point-vs-tile test projectiles use. 'margin' (px) also checks lines offset that far
     * to either side, so a shot isn't allowed to merely graze a wall corner.
     */
    public boolean hasLineOfSight(double x1, double y1, double x2, double y2, double margin) {
        double dx = x2 - x1, dy = y2 - y1;
        double len = Math.hypot(dx, dy);
        if (len < 0.001) return !isObstacleAtWorld(x1, y1);
        double nx = -dy / len * margin, ny = dx / len * margin; // sideways offset
        int steps = Math.max(1, (int) Math.ceil(len / 4.0));
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            double px = x1 + dx * t, py = y1 + dy * t;
            if (isObstacleAtWorld(px, py)) return false;
            if (margin > 0 && (isObstacleAtWorld(px + nx, py + ny) || isObstacleAtWorld(px - nx, py - ny))) return false;
        }
        return true;
    }

    /** True if a square body of 'size' px, centered on (cx,cy), can slide in a straight line to (tx,ty). */
    public boolean bodyPathClear(double cx, double cy, double tx, double ty, int size) {
        double dx = tx - cx, dy = ty - cy;
        double len = Math.hypot(dx, dy);
        int steps = Math.max(1, (int) Math.ceil(len / 6.0));
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            if (rectBlocked(cx + dx * t - size / 2.0, cy + dy * t - size / 2.0, size)) return false;
        }
        return true;
    }

    private static final int[][] DIRS = {{1,0},{-1,0},{0,1},{0,-1},{1,1},{1,-1},{-1,1},{-1,-1}};
    private static final int NEAR_WALL_PENALTY = 6; // nudges routes off the wall edge so big bodies don't snag corners
    private boolean[][] nearWall;
    private final Map<Integer, int[][]> fieldCache = new LinkedHashMap<>(16, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Integer, int[][]> e) { return size() > 16; }
    };

    private boolean pathTile(int c, int r) {
        return c >= 0 && r >= 0 && c < grid.length && r < grid[0].length && grid[c][r] == TileType.PATH;
    }

    /** A step between adjacent tiles; diagonals aren't allowed to cut across a wall corner. */
    private boolean stepOk(int c, int r, int dc, int dr) {
        if (!pathTile(c + dc, r + dr)) return false;
        return dc == 0 || dr == 0 || (pathTile(c + dc, r) && pathTile(c, r + dr));
    }

    private void buildNearWall() {
        nearWall = new boolean[grid.length][grid[0].length];
        for (int c = 0; c < grid.length; c++) {
            for (int r = 0; r < grid[0].length; r++) {
                if (!pathTile(c, r)) continue;
                for (int[] d : DIRS) {
                    if (!pathTile(c + d[0], r + d[1])) { nearWall[c][r] = true; break; }
                }
            }
        }
    }

    /**
     * Shortest-route cost from every walkable tile to the tile containing (wx, wy), or -1 where
     * unreachable. Cached per target tile (a stationary player costs one search, not one per enemy
     * per frame). Returns null if the target is inside a wall / off the map.
     */
    private int[][] distanceFieldTo(double wx, double wy) {
        int tc = (int) Math.floor(wx / Constants.TILE_SIZE), tr = (int) Math.floor(wy / Constants.TILE_SIZE);
        if (!pathTile(tc, tr)) return null;
        int key = tc * grid[0].length + tr;
        int[][] cached = fieldCache.get(key);
        if (cached != null) return cached;
        if (nearWall == null) buildNearWall();

        int cols = grid.length, rows = grid[0].length;
        int[][] dist = new int[cols][rows];
        for (int[] col : dist) java.util.Arrays.fill(col, Integer.MAX_VALUE);
        PriorityQueue<int[]> pq = new PriorityQueue<>((a, b) -> Integer.compare(a[0], b[0]));
        dist[tc][tr] = 0;
        pq.add(new int[]{0, tc, tr});
        while (!pq.isEmpty()) {
            int[] cur = pq.poll();
            int c = cur[1], r = cur[2];
            if (cur[0] > dist[c][r]) continue;
            for (int[] d : DIRS) {
                if (!stepOk(c, r, d[0], d[1])) continue;
                int nc = c + d[0], nr = r + d[1];
                int cost = cur[0] + (d[0] != 0 && d[1] != 0 ? 14 : 10) + (nearWall[nc][nr] ? NEAR_WALL_PENALTY : 0);
                if (cost < dist[nc][nr]) {
                    dist[nc][nr] = cost;
                    pq.add(new int[]{cost, nc, nr});
                }
            }
        }
        for (int[] col : dist) for (int i = 0; i < col.length; i++) if (col[i] == Integer.MAX_VALUE) col[i] = -1;
        fieldCache.put(key, dist);
        return dist;
    }

    /** Neighbor tile with the lowest route cost (i.e. one step downhill), or null at the target / when stuck. */
    private int[] downhill(int[][] f, int c, int r) {
        int best = f[c][r];
        int[] out = null;
        for (int[] d : DIRS) {
            if (!stepOk(c, r, d[0], d[1])) continue;
            int v = f[c + d[0]][r + d[1]];
            if (v >= 0 && v < best) { best = v; out = new int[]{c + d[0], r + d[1]}; }
        }
        return out;
    }

    /**
     * Where an enemy of 'size' px centered at (cx,cy) should head next to get to (tx,ty) around walls:
     * follows the shortest route, then looks a few tiles ahead and aims at the farthest point it can
     * walk to in a straight line (so it cuts corners smoothly instead of zig-zagging tile to tile).
     * Returns the waypoint's world center, or null if there's no route / it's already at the target.
     */
    public double[] waypointToward(double cx, double cy, int size, double tx, double ty) {
        int[][] f = distanceFieldTo(tx, ty);
        if (f == null) return null;
        int c = (int) Math.floor(cx / Constants.TILE_SIZE), r = (int) Math.floor(cy / Constants.TILE_SIZE);
        if (!pathTile(c, r) || f[c][r] < 0) return null;
        double[] best = null;
        for (int i = 0; i < 6; i++) { // look-ahead of up to 6 tiles
            int[] next = downhill(f, c, r);
            if (next == null) break;
            double wx = (next[0] + 0.5) * Constants.TILE_SIZE, wy = (next[1] + 0.5) * Constants.TILE_SIZE;
            if (best != null && !bodyPathClear(cx, cy, wx, wy, size)) break;
            best = new double[]{wx, wy};
            c = next[0]; r = next[1];
        }
        return best;
    }

    /** Lowest row index in this column that isn't open sky (the island's underside starts below it), or -1. */
    public int lowestSolidRow(int col) {
        for (int r = grid[0].length - 1; r >= 0; r--) if (grid[col][r] != TileType.VOID) return r;
        return -1;
    }

    private boolean isVoidAt(int c, int r) {
        return c < 0 || r < 0 || c >= grid.length || r >= grid[0].length || grid[c][r] == TileType.VOID;
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
                TileType type = grid[c][r];
                if (type == TileType.VOID) continue;              // open sky -- the backdrop shows through
                if (type == TileType.ROCK) { drawRock(g, tx, ty, tile, c, r); continue; }
                g.setColor(type == TileType.OBSTACLE ? theme.obstacleColor : theme.pathColor);
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


    // ---------------------------------------------------------------- rock border texture
    private static final Color[] FOREST_ROCK = { new Color(120, 116, 112), new Color(108, 104, 102), new Color(130, 124, 117) };
    private static final Color[] DESERT_ROCK = { new Color(192, 152, 106), new Color(178, 138, 94), new Color(202, 164, 118) };

    /**
     * One rock-border tile, drawn as chunky pixel blocks: a base shade picked per tile, a lighter and a
     * darker facet, a few speckles, an occasional zig-zag crack, and a bright/dark edge wherever it meets
     * open sky. Seeded off the tile's world (c, r) so it never shimmers as the camera moves.
     */
    private void drawRock(Graphics2D g, int tx, int ty, int tile, int c, int r) {
        boolean desert = theme == ArenaTheme.DESERT;
        Color[] palette = desert ? DESERT_ROCK : FOREST_ROCK;
        Color crack = desert ? new Color(128, 92, 60) : new Color(70, 66, 64);
        Color light = desert ? new Color(226, 194, 150) : new Color(160, 154, 146);
        Color dark = desert ? new Color(150, 110, 74) : new Color(86, 82, 80);
        Random rr = new Random(c * 7919L + r * 104729L);

        g.setColor(palette[rr.nextInt(palette.length)]);
        g.fillRect(tx, ty, tile, tile);
        // two facets: a light patch up-left, a dark patch down-right
        g.setColor(light);
        g.fillRect(tx + 2 + rr.nextInt(8), ty + 2 + rr.nextInt(6), 8 + rr.nextInt(6), 4 + rr.nextInt(3));
        g.setColor(dark);
        g.fillRect(tx + 12 + rr.nextInt(8), ty + 16 + rr.nextInt(8), 8 + rr.nextInt(6), 5 + rr.nextInt(3));
        // speckles
        for (int i = 0; i < 4; i++) {
            g.setColor(rr.nextBoolean() ? light : dark);
            g.fillRect(tx + rr.nextInt(tile - 4), ty + rr.nextInt(tile - 3), 2 + rr.nextInt(3), 2);
        }
        // a zig-zag crack on about 40% of tiles
        if (rr.nextInt(100) < 40) {
            g.setColor(crack);
            int x = tx + 6 + rr.nextInt(18), y = ty + 3 + rr.nextInt(6);
            for (int i = 0; i < 4 + rr.nextInt(3); i++) {
                g.fillRect(x, y, 2, 4);
                x += rr.nextBoolean() ? 2 : -2;
                y += 4;
            }
        }
        // mossy / sandy tuft on the top-facing rocks
        if (isVoidAt(c, r - 1) && rr.nextInt(100) < 45) {
            g.setColor(desert ? new Color(222, 196, 140) : new Color(88, 140, 72));
            g.fillRect(tx + 4 + rr.nextInt(20), ty + 3, 5 + rr.nextInt(4), 3);
        }
        // edges against open sky: bright rim on top, dark underside, thin dark sides
        if (isVoidAt(c, r - 1)) { g.setColor(light); g.fillRect(tx, ty, tile, 3); }
        if (isVoidAt(c, r + 1)) { g.setColor(dark); g.fillRect(tx, ty + tile - 4, tile, 4); }
        if (isVoidAt(c - 1, r)) { g.setColor(dark); g.fillRect(tx, ty, 2, tile); }
        if (isVoidAt(c + 1, r)) { g.setColor(dark); g.fillRect(tx + tile - 2, ty, 2, tile); }
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
