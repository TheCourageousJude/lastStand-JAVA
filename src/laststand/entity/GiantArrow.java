package laststand.entity;

import laststand.fx.UltimateArt;

import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * The Ranger's ultimate: a huge arrow (its own sprite -- see UltimateArt) that flies at a moderate
 * speed, passes through every enemy it touches (each is hit once), applies no knockback, and
 * ignores walls entirely, flying until it leaves the map. The body is long, so an enemy is hit the
 * moment ANY part of the arrow overlaps it, not just the tip.
 */
public class GiantArrow {
    public static final double SPEED_PX = 7.5; // "moderate" -- a bit slower than a normal arrow (9.0)
    public static final double DAMAGE_MULT = 1.5; // 150% of the Ranger's current arrow damage
    private static final double EDGE_FORGIVENESS = 7; // half-thickness added around enemies for the hit test
    private static final double OFFSCREEN_MARGIN = 300;

    public double x, y;            // center of the arrow
    public final double vx, vy;
    public final double angle;
    public final int damage;
    public final int ownerPlayerNumber;
    public boolean alive = true;
    private final Set<Enemy> alreadyHit = Collections.newSetFromMap(new IdentityHashMap<>());

    public GiantArrow(double x, double y, double dirX, double dirY, int damage, int ownerPlayerNumber) {
        double len = Math.hypot(dirX, dirY);
        double ux = len > 0.0001 ? dirX / len : 1, uy = len > 0.0001 ? dirY / len : 0;
        this.x = x;
        this.y = y;
        this.vx = ux * SPEED_PX;
        this.vy = uy * SPEED_PX;
        this.angle = Math.atan2(uy, ux);
        this.damage = damage;
        this.ownerPlayerNumber = ownerPlayerNumber;
    }

    public void update() {
        x += vx;
        y += vy;
    }

    public boolean isOutOfWorld(double worldW, double worldH) {
        return x < -OFFSCREEN_MARGIN || y < -OFFSCREEN_MARGIN || x > worldW + OFFSCREEN_MARGIN || y > worldH + OFFSCREEN_MARGIN;
    }

    /** True the first time the arrow's body overlaps this enemy (and records it, so it's hit only once). */
    public boolean tryHit(Enemy enemy) {
        if (alreadyHit.contains(enemy)) return false;
        double half = UltimateArt.GIANT_ARROW_LENGTH / 2.0;
        double ux = Math.cos(angle), uy = Math.sin(angle);
        Line2D body = new Line2D.Double(x - ux * half, y - uy * half, x + ux * half, y + uy * half);
        java.awt.Rectangle b = enemy.bounds();
        Rectangle2D target = new Rectangle2D.Double(b.x - EDGE_FORGIVENESS, b.y - EDGE_FORGIVENESS,
                b.width + EDGE_FORGIVENESS * 2, b.height + EDGE_FORGIVENESS * 2);
        if (!body.intersects(target)) return false;
        alreadyHit.add(enemy);
        return true;
    }

    public void draw(Graphics2D g, int camX, int camY) {
        AffineTransform saved = g.getTransform();
        g.translate(x - camX, y - camY);
        g.rotate(angle);
        UltimateArt.drawGiantArrowShape(g, 1.0, true);
        g.setTransform(saved);
    }
}
