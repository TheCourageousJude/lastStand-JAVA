package laststand.entity;

import java.awt.Color;

/**
 * The 8 monster types. Stat numbers per the latest overhaul pass.
 *
 * speed        : px/frame, wave-1 baseline (Enemy adds +10% every 5 waves cleared)
 * damage       : per hit, wave-1 baseline ("Attack" in the new Attack-Defense formula) --
 *                no longer grows per wave; see Enemy.globalBossDamageBonus instead
 * range        : melee reach or ranged max distance, in px
 * cooldown     : ms between attacks
 * aoeRadius    : >0 only for splash-damage ranged attacks (blaster golem)
 */
public enum EnemyType {
    // baseHealth here is the wave-1 value -- WaveManager/Enemy add +10 HP per wave on top of this,
    // then multiply the whole total by 1.18 per boss defeated (compounding, see
    // WaveManager.bossKillHpMultiplier())
    // -100 HP and -50% damage from the last pass -- early waves were taking way too many hits to clear
    ZOMBIE      ("Zombie",         new Color(46, 158, 46),   250, 1.3, 18, AttackType.MELEE,  WeightClass.MEDIUM, 40,  700,  0, 0),
    SKELETON    ("Skeleton",       new Color(160, 160, 160), 200, 1.1, 15, AttackType.RANGED, WeightClass.MEDIUM, 260, 1400, 6, 0),
    BEAR        ("Bear",           new Color(139, 90, 43),   225, 1.5, 18, AttackType.MELEE,  WeightClass.HEAVY,  44,  650,  0, 0),
    DEER        ("Deer",           new Color(178, 220, 30),  175, 1.7, 17, AttackType.MELEE,  WeightClass.LIGHT,  40,  700,  0, 0),
    ASSASSIN    ("Assassin",       new Color(245, 245, 245), 110, 1.9, 15, AttackType.MELEE,  WeightClass.LIGHT,  36,  500,  0, 0),
    WOLF        ("Wolf",           new Color(181, 160, 220), 105, 1.8, 14, AttackType.MELEE,  WeightClass.LIGHT,  38,  550,  0, 0),
    GOLEM       ("Golem",          new Color(120, 220, 220), 350, 1.1, 23, AttackType.MELEE,  WeightClass.HEAVY,  46,  800,  0, 0),
    BLASTER_GOLEM("Blaster Golem", new Color(235, 140, 30),  275, 0.8, 18, AttackType.RANGED, WeightClass.HEAVY,  300, 1800, 5, 30);

    public final String displayName;
    public final Color color;
    public final int baseHealth;
    public final double baseSpeed;
    public final int baseDamage;
    public final AttackType attackType;
    public final WeightClass weightClass;
    public final int range;
    public final int cooldownMs;
    public final double projectileSpeed; // only used for RANGED
    public final int aoeRadius;          // 0 = no splash

    EnemyType(String displayName, Color color, int baseHealth, double baseSpeed, int baseDamage,
              AttackType attackType, WeightClass weightClass, int range, int cooldownMs,
              double projectileSpeed, int aoeRadius) {
        this.displayName = displayName;
        this.color = color;
        this.baseHealth = baseHealth;
        this.baseSpeed = baseSpeed;
        this.baseDamage = baseDamage;
        this.attackType = attackType;
        this.weightClass = weightClass;
        this.range = range;
        this.cooldownMs = cooldownMs;
        this.projectileSpeed = projectileSpeed;
        this.aoeRadius = aoeRadius;
    }
}
