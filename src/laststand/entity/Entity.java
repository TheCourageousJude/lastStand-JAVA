package laststand.entity;

import laststand.world.Arena;

import java.awt.Color;
import java.awt.Graphics2D;

public abstract class Entity {
    public double x, y;              // world coordinates (top-left)
    public int size;                 // square side length in px
    public int health;
    public int maxHealth;
    public Color color;
    public WeightClass weightClass = WeightClass.MEDIUM;
    public boolean alive = true;

    // Knockback velocity, decays each frame via applyKnockbackDecay()
    protected double kx = 0, ky = 0;
    private static final double KNOCKBACK_FRICTION = 0.85;

    protected Entity(double x, double y, int size, int maxHealth, Color color) {
        this.x = x;
        this.y = y;
        this.size = size;
        this.maxHealth = maxHealth;
        this.health = maxHealth;
        this.color = color;
    }

    public java.awt.Rectangle bounds() {
        return new java.awt.Rectangle((int) x, (int) y, size, size);
    }

    public double centerX() { return x + size / 2.0; }
    public double centerY() { return y + size / 2.0; }

    /** Push this entity away from (fromX, fromY). Ignored if knockback-proof (bosses). */
    public void applyKnockback(double fromX, double fromY, double strength) {
        if (isKnockbackImmune()) return;
        double dx = centerX() - fromX;
        double dy = centerY() - fromY;
        double dist = Math.max(1.0, Math.hypot(dx, dy));
        double mult = weightClass.knockbackMultiplier;
        kx += (dx / dist) * strength * mult;
        ky += (dy / dist) * strength * mult;
    }

    protected boolean isKnockbackImmune() {
        return false;
    }

    /**
     * Call once per frame after normal movement to bleed off knockback velocity.
     * Routes through Arena's collision-aware mover (same one normal movement uses)
     * so a hard hit can no longer shove something INTO a solid block and strand it there --
     * the knockback just stops at the wall instead, same as walking into it would.
     */
    protected void tickKnockback(Arena arena) {
        if (Math.abs(kx) > 0.05 || Math.abs(ky) > 0.05) {
            arena.moveWithCollision(this, kx, ky);
            kx *= KNOCKBACK_FRICTION;
            ky *= KNOCKBACK_FRICTION;
        } else {
            kx = 0;
            ky = 0;
        }
    }

    public void damage(int amount) {
        health -= amount;
        if (health <= 0) {
            health = 0;
            alive = false;
        }
    }

    public void draw(Graphics2D g, int camX, int camY) {
        int sx = (int) x - camX;
        int sy = (int) y - camY;
        g.setColor(color);
        g.fillRect(sx, sy, size, size);
        g.setColor(color.darker());
        g.drawRect(sx, sy, size, size);
        drawHealthBar(g, sx, sy);
    }

    protected void drawHealthBar(Graphics2D g, int sx, int sy) {
        if (health >= maxHealth) return; // only show when damaged, keeps it clean
        int barW = size;
        int barH = 5;
        int by = sy - barH - 3;
        g.setColor(Color.BLACK);
        g.fillRect(sx, by, barW, barH);
        g.setColor(new Color(60, 200, 60));
        int fillW = (int) (barW * (health / (double) maxHealth));
        g.fillRect(sx, by, Math.max(0, fillW), barH);
    }
}
