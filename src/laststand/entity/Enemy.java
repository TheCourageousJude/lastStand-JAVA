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
    /** >0 = each melee hit deals this fraction of the victim's Max HP (Savage / Ghastly). */
    public final double percentDamage;
    /** Boss special name shown in yellow above the boss (null = no label). Set by WaveManager. */
    public String specialLabel = null;
    /** Savage = the Savage type, or any type given the Savage mutation (skeleton / blaster golem in
     *  Savage waves): red skin, Monolith-style hits, purple 75%-faster bullets, big knockback. */
    public final boolean savage;
    private final double damageTakenMult;
    private long lastAttackAt = -999999;

    // Boss multipliers, per design doc: 5x health, 2.5x damage, knockback-proof. (The old 0.5x speed
    // is gone: bosses move at their type's regular speed; boss specials adjust it per scenario.)
    private static final double BOSS_HEALTH_MULT = 5.0;
    private static final double BOSS_SPEED_MULT = 1.0;
    // Savage hits: normal damage PLUS this fraction of the victim's Max HP (ignores Defense)
    private static final float GHASTLY_BODY_ALPHA = 0.035f; // ~9/255 -- near-invisible on both floors
    public static final double SAVAGE_HIT_PCT = 0.16;
    public static final double SAVAGE_BOSS_HIT_PCT = 0.25;
    public static final double SAVAGE_BULLET_SPEED_MULT = 1.75; // 75% faster than the type's regular bullets
    private static final double BOSS_DAMAGE_MULT = 2.5;
    private static final int HP_PER_WAVE = 10; // was +5 -- "this is gonna be tough"
    private static final double SPEED_INCREASE_PER_5_WAVES = 0.10; // was 0.15 -- see also EnemyType's doc comment

    /**
     * Per-spawn tweaks layered on top of normal wave scaling (used by wave scenarios and boss
     * specials). NONE = a perfectly ordinary enemy.
     *   hp/speed/damage/cooldown : plain multipliers (cooldown < 1 = attacks more often)
     *   size                     : pixel size override, 0 = default (30 normal / 46 boss)
     *   absSpeed                 : if > 0, replaces speed entirely (px/frame, ignores wave scaling)
     *   damageTaken              : multiplier on damage this enemy RECEIVES from players (< 1 = armored)
     */
    public record Mods(double hp, double speed, double damage, double cooldown,
                       int size, double absSpeed, double damageTaken, boolean savage) {
        public static final Mods NONE = new Mods(1, 1, 1, 1, 0, 0, 1, false);
        public static Mods of(double hp, double speed, double damage) {
            return new Mods(hp, speed, damage, 1, 0, 0, 1, false);
        }
        public Mods withSpeed(double v)       { return new Mods(hp, v, damage, cooldown, size, absSpeed, damageTaken, savage); }
        public Mods withDamage(double v)      { return new Mods(hp, speed, v, cooldown, size, absSpeed, damageTaken, savage); }
        public Mods withCooldown(double v)    { return new Mods(hp, speed, damage, v, size, absSpeed, damageTaken, savage); }
        public Mods withSize(int v)           { return new Mods(hp, speed, damage, cooldown, v, absSpeed, damageTaken, savage); }
        public Mods withAbsSpeed(double v)    { return new Mods(hp, speed, damage, cooldown, size, v, damageTaken, savage); }
        public Mods withSavage()              { return new Mods(hp, speed, damage, cooldown, size, absSpeed, damageTaken, true); }
        public Mods withDamageTaken(double v) { return new Mods(hp, speed, damage, cooldown, size, absSpeed, v, savage); }
    }

    /** Pixel size an enemy spawns at (WaveManager needs it to find an open spawn spot). */
    public static int sizeFor(boolean boss, Mods m) {
        return m.size() > 0 ? m.size() : (boss ? 46 : 30);
    }

    public Enemy(EnemyType type, boolean boss, double x, double y, int waveNumber,
                 int globalBossDamageBonus, double bossKillHpMult) {
        this(type, boss, x, y, waveNumber, globalBossDamageBonus, bossKillHpMult, Mods.NONE);
    }

    public Enemy(EnemyType type, boolean boss, double x, double y, int waveNumber,
                 int globalBossDamageBonus, double bossKillHpMult, Mods mods) {
        super(x, y, sizeFor(boss, mods), scaledHealth(type, boss, waveNumber, bossKillHpMult * mods.hp()), type.color);
        this.type = type;
        this.boss = boss;
        this.savage = type == EnemyType.SAVAGE || mods.savage();
        this.weightClass = savage ? WeightClass.FRAIL : type.weightClass;
        this.damageTakenMult = mods.damageTaken();
        int milestones = (waveNumber - 1) / 5; // one "level" every 5 waves cleared
        double speedMult = (boss ? BOSS_SPEED_MULT : 1.0) * (1.0 + SPEED_INCREASE_PER_5_WAVES * milestones);
        this.speed = mods.absSpeed() > 0 ? mods.absSpeed() : type.baseSpeed * speedMult * mods.speed();
        // "+2 dmg per wave was tragic" -- replaced with a flat, game-wide +4 per boss defeated
        // (tracked in WaveManager.bossDamageBonus, incremented in GamePanel.onEnemyKilled()) so
        // damage only ramps up in response to actual boss kills, not just time/waves passing.
        int scaledDamage = type.baseDamage + globalBossDamageBonus;
        this.damage = (int) Math.max(1, Math.round(scaledDamage * (boss ? BOSS_DAMAGE_MULT : 1.0) * mods.damage()));
        this.percentDamage = savage ? (boss ? SAVAGE_BOSS_HIT_PCT : SAVAGE_HIT_PCT) : type.percentMaxHpDamage();
        this.range = type.range;
        this.cooldownMs = (int) Math.max(1, Math.round(type.cooldownMs * mods.cooldown()));
        this.aoeRadius = type.aoeRadius;
        if (boss) {
            this.color = type.color.brighter();
        }
        if (savage) {
            // red -> dark red, picked per enemy (flat shades, not bloody)
            double t = Math.random();
            this.color = new Color((int) (225 - 115 * t), (int) (45 - 45 * t), (int) (40 - 40 * t));
        }
    }

    /** Armored bosses: what a player hit of 'amount' actually does after this enemy's damage
     *  reduction. Applied after the player's own scaling (crit, enchants, perks). Min 1. */
    public int scaleIncoming(int amount) {
        if (damageTakenMult >= 1.0) return amount;
        return Math.max(1, (int) Math.round(amount * damageTakenMult));
    }

    private static int scaledHealth(EnemyType type, boolean boss, int waveNumber, double bossKillHpMult) {
        int waveScaledBase = type.baseHealth + HP_PER_WAVE * (waveNumber - 1);
        return (int) Math.round(waveScaledBase * (boss ? BOSS_HEALTH_MULT : 1.0) * bossKillHpMult);
    }

    @Override
    protected boolean isKnockbackImmune() {
        return boss || type.knockbackProof(); // bosses and Ghastlies are 100% knockback-proof
    }

    /** Returns a freshly-spawned Projectile if this update caused a ranged attack, else null. */
    // line-of-sight side clearance (px) for ranged enemies: needed to start firing / enough to keep firing
    private static final double LOS_MARGIN_START = 7.0;
    private static final double LOS_MARGIN_HOLD = 2.0;
    private boolean engaged = false; // currently standing and attacking (has a clear line to its target)

    /** One step toward (tx,ty): straight if the body can walk there directly, else along the route around walls. */
    private void moveToward(Arena arena, double tx, double ty, double dist) {
        if (dist < 0.001) return;
        double cx = centerX(), cy = centerY();
        double gx = tx, gy = ty;
        if (!arena.bodyPathClear(cx, cy, tx, ty, size)) {
            double[] wp = arena.waypointToward(cx, cy, size, tx, ty);
            if (wp != null) { gx = wp[0]; gy = wp[1]; }
        }
        double ddx = gx - cx, ddy = gy - cy;
        double d = Math.hypot(ddx, ddy);
        if (d < 0.001) return;
        double step = Math.min(speed, d); // don't overshoot a waypoint (avoids jitter)
        arena.moveWithCollision(this, ddx / d * step, ddy / d * step);
    }

    public Projectile update(List<Player> players, Arena arena, long nowMs) {
        if (!alive) return null;
        Player target = nearestAlivePlayer(players);
        if (target == null) return null;

        double tx = target.centerX(), ty = target.centerY();
        double dx = tx - centerX();
        double dy = ty - centerY();
        double dist = Math.hypot(dx, dy);

        // Attack only from a spot with a CLEAR line to the target. Being "in range" isn't enough --
        // behind a wall corner you'd just shoot (or swing) into stone. Ranged enemies ask for a bit of
        // side clearance to start firing and settle for less to keep firing (so they don't flicker
        // between shooting and repositioning right at a corner's edge).
        boolean ranged = type.attackType != AttackType.MELEE;
        double losMargin = !ranged ? 0 : (engaged ? LOS_MARGIN_HOLD : LOS_MARGIN_START);
        boolean canAttack = dist <= range && arena.hasLineOfSight(centerX(), centerY(), tx, ty, losMargin);
        engaged = canAttack;

        if (!canAttack) {
            // chase -- straight at the target if the way is open, otherwise follow the route around the
            // walls (which also repositions a ranged enemy until it can actually see the player)
            moveToward(arena, tx, ty, dist);
        } else {
            // clear line + in range: attack on cooldown
            if (nowMs - lastAttackAt >= cooldownMs) {
                lastAttackAt = nowMs;
                if (type.attackType == AttackType.MELEE) {
                    // Shield (if active) is checked inside takeDamage() itself now, works for any damage source
                    boolean landed;
                    if (percentDamage > 0) {
                        landed = target.takeHybridDamage(damage, percentDamage, centerX(), centerY(), 10.0, nowMs); // raw (vs Defense) + % Max HP
                    } else {
                        landed = target.takeDamage(damage, centerX(), centerY(), 10.0, nowMs); // Dodge + Armored applied inside
                    }
                    if (landed) target.registerThornsRetaliation(this, nowMs); // Tank's Thorns hits back at whoever just hit it
                } else {
                    double ux = dist > 0.001 ? dx / dist : 0;
                    double uy = dist > 0.001 ? dy / dist : -1;
                    tickKnockback(arena);
                    double shotSpeed = type.projectileSpeed * (savage ? SAVAGE_BULLET_SPEED_MULT : 1.0);
                    Projectile proj = new Projectile(centerX(), centerY(),
                            ux * shotSpeed, uy * shotSpeed,
                            damage, aoeRadius, false);
                    proj.percentDamage = percentDamage; // Savage shots add their % Max HP on hit too
                    if (savage) proj.color = Projectile.SAVAGE_BULLET_COLOR;
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
        int sx = (int) x - camX;
        int sy = (int) y - camY;
        if (type == EnemyType.GHASTLY) {
            // Nearly invisible body + face, so players bump into them by accident. Like every other
            // enemy, the health bar only appears once it has been hit -- and that bar is full opacity.
            java.awt.Composite old = g.getComposite();
            g.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, GHASTLY_BODY_ALPHA));
            g.setColor(color);
            g.fillRect(sx, sy, size, size);
            drawFace(g, sx, sy);
            g.setComposite(old);
            drawHealthBar(g, sx, sy);
            return;
        }
        super.draw(g, camX, camY);
        drawFace(g, sx, sy);
        if (boss) {
            g.setColor(Color.YELLOW);
            g.drawRect(sx - 2, sy - 2, size + 4, size + 4);
            drawSpecialLabel(g, sx, sy);
        }
    }

    /** Yellow boss-special name, centered above the health bar. Fixed position (the bar itself only
     *  appears once damaged) so the text never jumps. Dark outline keeps it readable on any floor. */
    private void drawSpecialLabel(Graphics2D g, int sx, int sy) {
        if (specialLabel == null) return;
        java.awt.Font oldFont = g.getFont();
        Object oldAA = g.getRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING);
        g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(oldFont.deriveFont(java.awt.Font.BOLD, 15f));
        int tw = g.getFontMetrics().stringWidth(specialLabel);
        int tx = sx + size / 2 - tw / 2;
        int ty = sy - 14; // baseline sits just above the health bar (bar top is at sy - 8)
        g.setColor(Color.BLACK);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx != 0 || dy != 0) g.drawString(specialLabel, tx + dx, ty + dy);
            }
        }
        g.setColor(new Color(255, 230, 0));
        g.drawString(specialLabel, tx, ty);
        g.setFont(oldFont);
        if (oldAA != null) g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, oldAA);
    }

    @Override
    protected void drawHealthBar(Graphics2D g, int sx, int sy) {
        if (size >= 20) { super.drawHealthBar(g, sx, sy); return; } // only shown once damaged (see Entity)
        if (health >= maxHealth) return;
        int barW = 28, bx = sx + size / 2 - barW / 2, by = sy - 8; // tiny bosses: wider, centered bar
        g.setColor(Color.BLACK);
        g.fillRect(bx, by, barW, 5);
        g.setColor(new Color(60, 200, 60));
        g.fillRect(bx, by, Math.max(0, (int) (barW * (health / (double) maxHealth))), 5);
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
            case SAVAGE -> { // angry slanted eyes + bared teeth
                g.setColor(Color.BLACK);
                g.drawLine(leftEyeX, eyeY, leftEyeX + eyeSize, eyeY + eyeSize / 2);
                g.drawLine(rightEyeX + eyeSize, eyeY, rightEyeX, eyeY + eyeSize / 2);
                g.setColor(new Color(240, 230, 220));
                g.fillRect(sx + size / 4, mouthY, size / 2, 3);
            }
            case GHASTLY -> { // hollow eyes + wailing mouth
                g.setColor(Color.BLACK);
                g.fillOval(leftEyeX, eyeY, eyeSize, eyeSize + 2);
                g.fillOval(rightEyeX, eyeY, eyeSize, eyeSize + 2);
                g.fillOval(sx + size / 2 - 3, mouthY, 6, 8);
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
