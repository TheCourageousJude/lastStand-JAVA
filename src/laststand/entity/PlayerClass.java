package laststand.entity;

/**
 * Base stats for the two playable archetypes. Numbers are a first pass for
 * balance -- tune freely, nothing else in the codebase depends on the exact
 * values.
 *
 * `damage` here is now unused for actual combat -- WeaponTier's explicit
 * per-class damage table (see shop/WeaponTier) is the real source, with
 * WOODEN matching these starting numbers (Tank 50, Ranger 90) exactly.
 *
 * Stat overhaul: both classes now 100 max HP.
 * Passive healing: Tank +3 HP/sec, Ranger +1 HP/sec
 */
public enum PlayerClass {
    TANK(100, 6.0, AttackType.MELEE, 50, 96, 650, WeightClass.HEAVY, 0, 3),
    RANGER(100, TANK.moveSpeed, AttackType.RANGED, 90, 320, 1000, WeightClass.LIGHT, 20, 1);

    public final int maxHealth;
    public final double moveSpeed;      // px/frame
    public final AttackType attackType;
    public final int damage;
    public final int range;             // melee reach or ranged max distance (px)
    public final int attackCooldownMs;
    public final WeightClass weightClass;
    public final int startingAmmo;      // 0 for melee classes; Ranger's initial arrow stock (uncapped after)
    public final int healPerSecond;     // passive regen

    PlayerClass(int maxHealth, double moveSpeed, AttackType attackType, int damage,
                int range, int attackCooldownMs, WeightClass weightClass, int startingAmmo, int healPerSecond) {
        this.maxHealth = maxHealth;
        this.moveSpeed = moveSpeed;
        this.attackType = attackType;
        this.damage = damage;
        this.range = range;
        this.attackCooldownMs = attackCooldownMs;
        this.weightClass = weightClass;
        this.startingAmmo = startingAmmo;
        this.healPerSecond = healPerSecond;
    }
}
