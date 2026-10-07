package laststand.wave;

import laststand.entity.Enemy.Mods;
import laststand.entity.PlayerClass;

/**
 * Boss specials. Every boss wave (5, 10, 15, ...) rolls one of these; they are never announced.
 * Bosses no longer have the old 0.5x speed penalty -- they move at their type's regular speed,
 * and each special adjusts from there. Baseline boss stats (5x HP, 2.5x damage, knockback-proof)
 * still apply on top.
 *
 * Player top speed is PlayerClass.TANK.moveSpeed (6.0 px/frame); the two "fast" specials are
 * expressed as a fraction of that, rising with each boss defeated (see speedFraction()).
 */
public enum BossScenario {
    MINIATURE("Miniature"),
    ARMORED  ("Armored"),
    SPEEDY   ("Speedy"),
    STRONG   ("Strong"),
    SAVAGE   ("Savage"),     // only from wave 25
    SORCERER ("Sorcerer");

    public static final int SAVAGE_MIN_WAVE = 25;
    /** From wave 25 a boss wave is Savage 25% of the time; from wave 50 on, 50%. The other specials
     *  split whatever is left evenly. */
    public static final double SAVAGE_CHANCE = 0.25;
    public static final int SAVAGE_BOOST_WAVE = 50;
    public static final double SAVAGE_BOOSTED_CHANCE = 0.50;

    public static double savageChance(int wave) {
        return wave >= SAVAGE_BOOST_WAVE ? SAVAGE_BOOSTED_CHANCE : SAVAGE_CHANCE;
    }

    // --- tuning ---
    static final int    MINIATURE_SIZE = 14;      // vs 46 for a normal boss
    // Speed is a fraction of the player's top speed: base + step per boss defeated (all bosses count,
    // including ones beaten before Savage unlocks at wave 25). Miniature never exceeds the player.
    static final double MINIATURE_SPEED_BASE = 0.60, MINIATURE_SPEED_PER_BOSS = 0.01, MINIATURE_SPEED_MAX = 1.00;
    static final double SAVAGE_SPEED_BASE = 0.60, SAVAGE_SPEED_PER_BOSS = 0.02;
    static final double SAVAGE_SPEED_MAX = Double.POSITIVE_INFINITY; // uncapped: "insanely fast"

    static final double ARMORED_SPEED = 0.75;     // somewhat slow
    static final double ARMORED_DAMAGE_TAKEN = 0.80; // takes 80% of every player hit
    static final double SPEEDY_SPEED = 1.5;       // moderately faster (still well short of Miniature)
    static final double STRONG_DAMAGE = 2.5;      // on top of the usual boss damage
    static final double STRONG_SPEED = 0.85;      // a little slow
    static final double SORCERER_SPEED = 0.40;    // very slow
    // Sorcerer: summon when HP first drops to each threshold, this many of the boss's own type
    public static final double[] SORCERER_THRESHOLDS = {0.75, 0.50, 0.25};
    public static final int[]    SORCERER_SUMMONS    = {5, 7, 10};
    public static final double   SORCERER_RING_RADIUS = 90;
    /** Summoned minions "move and shoot a little faster". */
    public static final Mods SORCERER_MINION = Mods.NONE.withSpeed(1.2).withCooldown(0.8);

    public final String label;

    BossScenario(String label) {
        this.label = label;
    }

    public boolean eligible(int wave) {
        return this != SAVAGE || wave >= SAVAGE_MIN_WAVE;
    }

    /** Player-speed fraction this special moves at, given how many bosses have been defeated so far. */
    public double speedFraction(int bossesDefeated) {
        return switch (this) {
            case MINIATURE -> Math.min(MINIATURE_SPEED_MAX, MINIATURE_SPEED_BASE + MINIATURE_SPEED_PER_BOSS * bossesDefeated);
            case SAVAGE    -> Math.min(SAVAGE_SPEED_MAX, SAVAGE_SPEED_BASE + SAVAGE_SPEED_PER_BOSS * bossesDefeated);
            default        -> 0;
        };
    }

    public Mods mods(int bossesDefeated) {
        double playerSpeed = PlayerClass.TANK.moveSpeed;
        return switch (this) {
            case MINIATURE -> Mods.NONE.withSize(MINIATURE_SIZE).withAbsSpeed(speedFraction(bossesDefeated) * playerSpeed);
            case ARMORED   -> Mods.NONE.withSpeed(ARMORED_SPEED).withDamageTaken(ARMORED_DAMAGE_TAKEN);
            case SPEEDY    -> Mods.NONE.withSpeed(SPEEDY_SPEED);
            case STRONG    -> Mods.NONE.withDamage(STRONG_DAMAGE).withSpeed(STRONG_SPEED);
            case SAVAGE    -> Mods.NONE.withSavage().withAbsSpeed(speedFraction(bossesDefeated) * playerSpeed);
            case SORCERER  -> Mods.NONE.withSpeed(SORCERER_SPEED);
        };
    }
}
