package laststand.entity;

import laststand.world.Arena;

import java.awt.Color;
import java.awt.Graphics2D;
import java.util.List;

public class Enemy extends Entity {

    public final EnemyType type;
    public final boolean boss;
    public final double speed;
    public final int damage;
    public final int range;
    public final int cooldownMs;
    public final int aoeRadius;
    private long lastAttackAt = -999999;

    // Boss multipliers, per design doc: 5x health, 0.5x speed, 2.5x damage, knockback-proof
    private static final double BOSS_HEALTH_MULT = 5.0;
    private static final double BOSS_SPEED_MULT = 0.5;
    private static final double BOSS_DAMAGE_MULT = 2.5;
    private static final int HP_PER_WAVE = 10; // was +5 -- "this is gonna be tough"
    private static final double SPEED_INCREASE_PER_5_WAVES = 0.10; // was 0.15 -- see also EnemyType's doc comment

    public Enemy(EnemyType type, boolean boss, double x, double y, int waveNumber, int globalBossDamageBonus) {
        super(x, y, boss ? 46 : 30, scaledHealth(type, boss, waveNumber), type.color);
        this.type = type;
        this.boss = boss;
        this.weightClass = type.weightClass;
        int milestones = (waveNumber - 1) / 5; // one "level" every 5 waves cleared
        double speedMult = (boss ? BOSS_SPEED_MULT : 1.0) * (1.0 + SPEED_INCREASE_PER_5_WAVES * milestones);
        this.speed = type.baseSpeed * speedMult;
        // "+2 dmg per wave was tragic" -- replaced with a flat, game-wide +4 per boss defeated
        // (tracked in WaveManager.bossDamageBonus, incremented in GamePanel.onEnemyKilled()) so
        // damage only ramps up in response to actual boss kills, not just time/waves passing.
        int scaledDamage = type.baseDamage + globalBossDamageBonus;
        this.damage = (int) Math.round(scaledDamage * (boss ? BOSS_DAMAGE_MULT : 1.0));
        this.range = type.range;
        this.cooldownMs = type.cooldownMs;
        this.aoeRadius = type.aoeRadius;
        if (boss) {
            this.color = type.color.brighter();
        }
    }

    private static int scaledHealth(EnemyType type, boolean boss, int waveNumber) {
        int waveScaledBase = type.baseHealth + HP_PER_WAVE * (waveNumber - 1);
        return (int) Math.round(waveScaledBase * (boss ? BOSS_HEALTH_MULT : 1.0));
    }

    @Override
    protected boolean isKnockbackImmune() {
        return boss; // bosses are 100% knockback-proof
    }

    /** Returns a freshly-spawned Projectile if this update caused a ranged attack, else null. */
    public Projectile update(List<Player> players, Arena arena, long nowMs) {
        if (!alive) return null;
        Player target = nearestAlivePlayer(players);
        if (target == null) return null;

        double dx = target.centerX() - centerX();
        double dy = target.centerY() - centerY();
        double dist = Math.hypot(dx, dy);

        if (dist > range) {
            // chase
            if (dist > 0.001) {
                double ux = dx / dist, uy = dy / dist;
                arena.moveWithCollision(this, ux * speed, uy * speed);
            }
        } else {
            // in range: attack on cooldown
            if (nowMs - lastAttackAt >= cooldownMs) {
                lastAttackAt = nowMs;
                if (type.attackType == AttackType.MELEE) {
                    // Shield (if active) is checked inside takeDamage() itself now, works for any damage source
                    target.takeDamage(damage, centerX(), centerY(), 10.0, nowMs); // Dodge + Armored applied inside
                } else {
                    double ux = dist > 0.001 ? dx / dist : 0;
                    double uy = dist > 0.001 ? dy / dist : -1;
                    tickKnockback(arena);
                    Projectile proj = new Projectile(centerX(), centerY(),
                            ux * type.projectileSpeed, uy * type.projectileSpeed,
                            damage, aoeRadius, false);
                    if (type == EnemyType.BLASTER_GOLEM) proj.radius *= 2; // bigger bullet to read against its (shortened) AoE
                    return proj;
                }
            }
        }
        tickKnockback(arena);
        return null;
    }

    private Player nearestAlivePlayer(List<Player> players) {
        Player best = null;
        double bestDist = Double.MAX_VALUE;
        for (Player p : players) {
            if (!p.alive) continue;
            double d = Math.hypot(p.centerX() - centerX(), p.centerY() - centerY());
            if (d < bestDist) {
                bestDist = d;
                best = p;
            }
        }
        return best;
    }

    @Override
    public void draw(Graphics2D g, int camX, int camY) {
        super.draw(g, camX, camY);
        int sx = (int) x - camX;
        int sy = (int) y - camY;
        drawFace(g, sx, sy);
        if (boss) {
            g.setColor(Color.YELLOW);
            g.drawRect(sx - 2, sy - 2, size + 4, size + 4);
        }
    }

    /** Simple blocky pixel face so each of the 8 types reads at a glance. */
    private void drawFace(Graphics2D g, int sx, int sy) {
        int eyeSize = Math.max(3, size / 8);
        int eyeY = sy + size / 3;
        int leftEyeX = sx + size / 4 - eyeSize / 2;
        int rightEyeX = sx + size * 3 / 4 - eyeSize / 2;
        int mouthY = sy + size * 2 / 3;

        switch (type) {
            case ZOMBIE -> { // X eyes, flat grim mouth
                g.setColor(Color.BLACK);
                drawX(g, leftEyeX, eyeY, eyeSize);
                drawX(g, rightEyeX, eyeY, eyeSize);
                g.fillRect(sx + size / 4, mouthY, size / 2, 2);
            }
            case SKELETON -> { // hollow round eyes, skull grin
                g.setColor(Color.BLACK);
                g.drawOval(leftEyeX, eyeY, eyeSize, eyeSize);
                g.drawOval(rightEyeX, eyeY, eyeSize, eyeSize);
                g.drawLine(sx + size / 3, mouthY, sx + size * 2 / 3, mouthY);
            }
            case BEAR -> { // small round eyes, snout dot
                g.setColor(Color.BLACK);
                g.fillOval(leftEyeX, eyeY, eyeSize, eyeSize);
                g.fillOval(rightEyeX, eyeY, eyeSize, eyeSize);
                g.fillRect(sx + size / 2 - 2, mouthY, 4, 4);
            }
            case DEER -> { // round eyes, little antler ticks on top
                g.setColor(Color.BLACK);
                g.fillOval(leftEyeX, eyeY, eyeSize, eyeSize);
                g.fillOval(rightEyeX, eyeY, eyeSize, eyeSize);
                g.fillRect(sx + size / 4, sy + 2, 2, 4);
                g.fillRect(sx + size * 3 / 4 - 2, sy + 2, 2, 4);
            }
            case ASSASSIN -> { // narrow angry slit eyes
                g.setColor(Color.BLACK);
                g.fillRect(leftEyeX, eyeY, eyeSize, 2);
                g.fillRect(rightEyeX, eyeY, eyeSize, 2);
            }
            case WOLF -> { // round eyes + two fangs
                g.setColor(Color.BLACK);
                g.fillOval(leftEyeX, eyeY, eyeSize, eyeSize);
                g.fillOval(rightEyeX, eyeY, eyeSize, eyeSize);
                g.setColor(Color.WHITE);
                g.fillRect(sx + size / 2 - 4, mouthY, 2, 4);
                g.fillRect(sx + size / 2 + 2, mouthY, 2, 4);
            }
            case GOLEM -> { // single glowing eye
                g.setColor(new Color(120, 255, 255));
                g.fillRect(sx + size / 2 - eyeSize / 2, eyeY, eyeSize, eyeSize);
            }
            case BLASTER_GOLEM -> { // glowing eye + cannon port on chest
                g.setColor(new Color(255, 200, 120));
                g.fillRect(sx + size / 2 - eyeSize / 2, eyeY, eyeSize, eyeSize);
                g.setColor(Color.BLACK);
                g.fillOval(sx + size / 2 - 3, mouthY, 6, 6);
            }
        }
    }

    private void drawX(Graphics2D g, int x, int y, int s) {
        g.drawLine(x, y, x + s, y + s);
        g.drawLine(x, y + s, x + s, y);
    }
}
