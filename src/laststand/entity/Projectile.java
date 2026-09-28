package laststand.entity;

import java.awt.Color;
import java.awt.Graphics2D;

public class Projectile {
    public double x, y;
    public double vx, vy;
    public int radius = 5;
    public int damage;
    public int aoeRadius; // 0 = no splash
    public boolean fromPlayer; // true = shot by a player, false = shot by an enemy
    public int ownerPlayerNumber = 0; // 1 or 2 when fromPlayer, used to credit kill rewards to the right wallet
    public double originX, originY; // set by the shooter -- used for the Ranger's distance-based Critical bonus
    public boolean alive = true;
    // Player and enemy shots are colored differently now so they're easy to tell apart at a
    // glance in duo play -- player arrows/darts are a warm yellowish-orange, enemy shots stay
    // the original ruby red.
    public static final Color PLAYER_BULLET_COLOR = new Color(25, 55, 150); // dark blue
    public static final Color ENEMY_BULLET_COLOR = new Color(200, 30, 40); // ruby red, per spec

    public Projectile(double x, double y, double vx, double vy, int damage, int aoeRadius, boolean fromPlayer) {
        this.x = x;
        this.y = y;
        this.vx = vx;
        this.vy = vy;
        this.damage = damage;
        this.aoeRadius = aoeRadius;
        this.fromPlayer = fromPlayer;
    }

    public void update() {
        x += vx;
        y += vy;
    }

    public java.awt.Rectangle bounds() {
        return new java.awt.Rectangle((int) (x - radius), (int) (y - radius), radius * 2, radius * 2);
    }

    public void draw(Graphics2D g, int camX, int camY) {
        g.setColor(fromPlayer ? PLAYER_BULLET_COLOR : ENEMY_BULLET_COLOR);
        g.fillOval((int) (x - radius) - camX, (int) (y - radius) - camY, radius * 2, radius * 2);
    }
}
