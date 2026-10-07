package laststand.fx;

import laststand.entity.Player;
import laststand.entity.PlayerClass;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.util.EnumMap;
import java.util.Map;
import java.util.Random;

/**
 * All the drawing for the two ultimates, kept in one place so it's easy to restyle:
 *   - the Ranger's GIANT ARROW sprite (also used, small, as the Ranger's ultimate icon)
 *   - the Tank's THORNS aura: a red shield with 6 dark-green spikes that bulge in and out
 *   - the ultimate box in the HUD: blurred icon + upward-filling charge bar while charging, and a
 *     crisp icon with an "electrifying" lightning animation once fully charged
 * Everything is procedural -- no image files.
 */
public final class UltimateArt {
    private UltimateArt() {}

    // ================================================================== Giant Arrow sprite

    public static final int GIANT_ARROW_LENGTH = 84; // tail-to-tip, px; the hit-test uses the same length

    /** Draws the arrow pointing along +x, centered on the origin of the current transform. */
    public static void drawGiantArrowShape(Graphics2D g, double scale, boolean withTrail) {
        AffineTransform saved = g.getTransform();
        g.scale(scale, scale);
        int L = GIANT_ARROW_LENGTH, half = L / 2;
        if (withTrail) { // soft golden streak behind it
            g.setPaint(new GradientPaint(-half - 60, 0, new Color(255, 220, 120, 0), -half + 10, 0, new Color(255, 215, 110, 120)));
            g.fillRoundRect(-half - 60, -9, 70, 18, 18, 18);
            g.setPaint(new GradientPaint(0, 0, new Color(255, 235, 150, 90), half + 16, 0, new Color(255, 235, 150, 0)));
            g.fillOval(-8, -18, half + 40, 36);
        }
        // fletching: two pairs of feathers at the tail
        for (int i = 0; i < 2; i++) {
            int fx = -half + 4 + i * 11;
            g.setColor(i == 0 ? new Color(205, 45, 45) : new Color(245, 245, 245));
            Polygon top = new Polygon(new int[]{fx, fx + 9, fx + 15, fx + 6}, new int[]{-2, -13, -13, -2}, 4);
            Polygon bot = new Polygon(new int[]{fx, fx + 9, fx + 15, fx + 6}, new int[]{2, 13, 13, 2}, 4);
            g.fill(top); g.fill(bot);
            g.setColor(new Color(60, 30, 30, 160)); g.draw(top); g.draw(bot);
        }
        // shaft
        g.setColor(new Color(142, 94, 52));
        g.fillRect(-half + 6, -3, L - 30, 6);
        g.setColor(new Color(196, 144, 86));
        g.fillRect(-half + 6, -3, L - 30, 2);
        g.setColor(new Color(70, 42, 24));
        g.drawRect(-half + 6, -3, L - 30, 6);
        // arrowhead: big steel blade with a notched back and a bright edge
        Polygon head = new Polygon(new int[]{half, half - 28, half - 21, half - 28}, new int[]{0, -14, 0, 14}, 4);
        g.setColor(new Color(206, 212, 224));
        g.fill(head);
        g.setColor(new Color(244, 248, 255));
        g.fillPolygon(new int[]{half, half - 28, half - 21}, new int[]{0, -14, 0}, 3);
        g.setColor(new Color(82, 88, 108));
        g.setStroke(new BasicStroke(1.5f));
        g.draw(head);
        g.setTransform(saved);
    }

    // ================================================================== Thorns aura

    private static final Color THORN_FILL = new Color(210, 40, 50, 70);
    private static final Color THORN_EDGE = new Color(235, 60, 60, 230);
    private static final Color SPIKE_FILL = new Color(24, 96, 44);
    private static final Color SPIKE_EDGE = new Color(70, 165, 84);
    public static final double THORN_BASE_RADIUS = 38; // the red shield
    public static final double THORN_SPIKE_REACH = 24; // how far the spikes bulge out at full extension

    /** Spike extension 0..1 for the current moment: out and back once per pulse period. */
    public static double thornExtension(long elapsedMs, long pulseMs) {
        double phase = (elapsedMs % pulseMs) / (double) pulseMs;
        return Math.sin(Math.PI * phase);
    }

    /** The red shield with 6 dark-green spikes bulging in and out. (cx, cy) is the player's center on screen. */
    public static void drawThorns(Graphics2D g, int cx, int cy, long elapsedMs, long remainingMs, long pulseMs) {
        float alpha = 1f;
        if (remainingMs < 2000) alpha = (remainingMs / 120 % 2 == 0) ? 1f : 0.45f; // blink when about to end
        java.awt.Composite old = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        Object oldAA = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        double ext = thornExtension(elapsedMs, pulseMs);
        double spin = elapsedMs * 0.0006; // slow rotation
        double reach = THORN_BASE_RADIUS + THORN_SPIKE_REACH * ext;

        for (int i = 0; i < 6; i++) { // spikes first, so the shield disc overlaps their roots
            double a = spin + i * Math.PI / 3;
            double ca = Math.cos(a), sa = Math.sin(a), nx = -sa, ny = ca;
            double rootR = THORN_BASE_RADIUS - 6, halfW = 8;
            Path2D spike = new Path2D.Double();
            spike.moveTo(cx + ca * rootR + nx * halfW, cy + sa * rootR + ny * halfW);
            spike.lineTo(cx + ca * reach, cy + sa * reach);
            spike.lineTo(cx + ca * rootR - nx * halfW, cy + sa * rootR - ny * halfW);
            spike.closePath();
            g.setColor(SPIKE_FILL);
            g.fill(spike);
            g.setColor(SPIKE_EDGE);
            g.setStroke(new BasicStroke(1.6f));
            g.draw(spike);
        }
        int r = (int) THORN_BASE_RADIUS;
        g.setColor(THORN_FILL);
        g.fillOval(cx - r, cy - r, r * 2, r * 2);
        g.setColor(THORN_EDGE);
        g.setStroke(new BasicStroke(3f));
        g.drawOval(cx - r, cy - r, r * 2, r * 2);
        if (ext > 0.85) { // a bright flash ring right when the spikes are fully out (that's when they hit)
            g.setColor(new Color(255, 120, 110, 140));
            g.setStroke(new BasicStroke(2f));
            int rr = (int) (reach + 3);
            g.drawOval(cx - rr, cy - rr, rr * 2, rr * 2);
        }
        g.setStroke(new BasicStroke(1f));
        if (oldAA != null) g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA);
        g.setComposite(old);
    }

    // ================================================================== HUD ultimate box

    private static final int ICON = 40;
    private static final Map<PlayerClass, BufferedImage> CRISP = new EnumMap<>(PlayerClass.class);
    private static final Map<PlayerClass, BufferedImage> BLURRED = new EnumMap<>(PlayerClass.class);

    /** The class's ultimate icon (40x40), either crisp or heavily blurred. Cached. */
    public static BufferedImage icon(PlayerClass cls, boolean blurred) {
        Map<PlayerClass, BufferedImage> cache = blurred ? BLURRED : CRISP;
        BufferedImage img = cache.get(cls);
        if (img != null) return img;
        BufferedImage crisp = CRISP.get(cls);
        if (crisp == null) {
            crisp = new BufferedImage(ICON, ICON, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = crisp.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (cls == PlayerClass.RANGER) {
                g.translate(ICON / 2.0, ICON / 2.0);
                g.rotate(-Math.PI / 4);
                drawGiantArrowShape(g, 0.46, false);
            } else {
                // the aura is ~60px across at full size -- shrink it so the whole shield + spikes fit the 40px icon
                g.translate(ICON / 2.0, ICON / 2.0);
                g.scale(0.31, 0.31);
                drawThorns(g, 0, 0, 330, 99999, 500); // frozen mid-bulge
            }
            g.dispose();
            CRISP.put(cls, crisp);
        }
        if (!blurred) return crisp;
        float[] k = new float[25];
        java.util.Arrays.fill(k, 1f / 25f);
        ConvolveOp blur = new ConvolveOp(new Kernel(5, 5, k), ConvolveOp.EDGE_NO_OP, null);
        BufferedImage padded = new BufferedImage(ICON, ICON, BufferedImage.TYPE_INT_ARGB);
        padded.getGraphics().drawImage(crisp, 0, 0, null);
        BufferedImage once = blur.filter(padded, null);
        BufferedImage twice = blur.filter(once, null);
        BLURRED.put(cls, twice);
        return twice;
    }

    /**
     * The ultimate box beside the hotbar. Charging: blurred icon with a colored bar filling UP from
     * the bottom. Fully charged: crisp icon with an electric animation. Thorns running: crisp icon
     * with the bar draining as the 10 seconds tick away.
     */
    public static void drawUltimateBox(Graphics2D g, Player p, int bx, int by, int size, long nowMs) {
        boolean active = p.isThornsActive(nowMs);
        boolean ready = !active && p.ultimateReady();
        double frac = active ? p.thornsRemainingMs(nowMs) / (double) Player.THORNS_DURATION_MS
                : ready ? 1.0 : p.ultimateChargeFraction();
        long wall = System.currentTimeMillis(); // animation clock: keeps sparking even while the game is paused

        Object oldAA = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        RoundRectangle2D box = new RoundRectangle2D.Double(bx, by, size, size, 10, 10);

        if (ready) { // pulsing glow behind the box
            double pulse = 0.5 + 0.5 * Math.sin(wall / 140.0);
            g.setColor(new Color(110, 220, 255, (int) (50 + 70 * pulse)));
            g.fill(new RoundRectangle2D.Double(bx - 4, by - 4, size + 8, size + 8, 14, 14));
        }
        g.setColor(new Color(34, 30, 48));
        g.fill(box);

        // icon: blurred while charging, crisp once ready / while Thorns is running
        int ix = bx + (size - ICON) / 2, iy = by + (size - ICON) / 2;
        java.awt.Shape oldClip = g.getClip();
        g.clip(box);
        if (ready || active) {
            g.drawImage(icon(p.playerClass, false), ix, iy, null);
        } else {
            java.awt.Composite c0 = g.getComposite();
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.6f));
            g.drawImage(icon(p.playerClass, true), ix, iy, null);
            g.setComposite(c0);
        }
        // the filling bar, rising from the bottom edge of the box
        int fillH = (int) Math.round((size - 4) * Math.max(0, Math.min(1, frac)));
        int fillAlpha = ready ? 60 : 125; // once full, let the crisp icon pop instead of tinting it solid
        Color fill = active ? new Color(70, 190, 95, 120)
                : p.playerClass == PlayerClass.TANK ? new Color(225, 70, 70, fillAlpha) : new Color(245, 205, 70, fillAlpha);
        if (fillH > 0) {
            g.setColor(fill);
            g.fillRect(bx + 2, by + size - 2 - fillH, size - 4, fillH);
            g.setColor(new Color(fill.getRed(), fill.getGreen(), fill.getBlue(), 230));
            g.fillRect(bx + 2, by + size - 2 - fillH, size - 4, 2); // bright waterline
        }
        g.setClip(oldClip);

        // border
        Color border;
        if (ready) {
            double pulse = 0.5 + 0.5 * Math.sin(wall / 90.0);
            border = new Color((int) (130 + 125 * pulse), (int) (225 + 30 * pulse), 255);
        } else if (active) {
            border = new Color(230, 80, 80);
        } else {
            border = new Color(140, 115, 200);
        }
        g.setColor(border);
        g.setStroke(new BasicStroke(ready ? 2.5f : 1.5f));
        g.draw(box);

        if (ready) drawLightning(g, bx, by, size, wall);
        g.setStroke(new BasicStroke(1f));
        if (oldAA != null) g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA);
    }

    /** Jagged bolts crackling off the edges of the box; reshuffled every ~85 ms so it flickers. */
    private static void drawLightning(Graphics2D g, int bx, int by, int size, long wall) {
        long tick = wall / 85;
        for (int k = 0; k < 5; k++) {
            Random r = new Random(tick * 7919L + k * 104729L);
            double t = r.nextDouble() * 4; // position around the perimeter
            double sx, sy, ox, oy;
            int side = (int) t; double u = t - side;
            switch (side) {
                case 0 -> { sx = bx + u * size; sy = by; ox = 0; oy = -1; }
                case 1 -> { sx = bx + size; sy = by + u * size; ox = 1; oy = 0; }
                case 2 -> { sx = bx + size - u * size; sy = by + size; ox = 0; oy = 1; }
                default -> { sx = bx; sy = by + size - u * size; ox = -1; oy = 0; }
            }
            double len = 9 + r.nextDouble() * 9;
            Path2D bolt = new Path2D.Double();
            bolt.moveTo(sx, sy);
            double px = sx, py = sy;
            int segs = 3 + r.nextInt(2);
            for (int s = 1; s <= segs; s++) {
                double d = len * s / segs;
                double jitter = (r.nextDouble() - 0.5) * 10;
                px = sx + ox * d + (-oy) * jitter;
                py = sy + oy * d + ox * jitter;
                bolt.lineTo(px, py);
            }
            g.setStroke(new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(90, 200, 255, 170));
            g.draw(bolt);
            g.setStroke(new BasicStroke(1.1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(235, 250, 255, 240));
            g.draw(bolt);
        }
        // a few sparks inside
        Random r = new Random(tick * 31L);
        g.setColor(new Color(220, 245, 255, 210));
        for (int i = 0; i < 4; i++) {
            int x = bx + 5 + r.nextInt(size - 10), y = by + 5 + r.nextInt(size - 10);
            g.fillRect(x, y, 2, 2);
        }
    }
}
