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
 * `range` is likewise now unused for the Tank -- weapon remake replaced the melee sword swing
 * with a thrown Dagger, whose own short range lives in Player.DAGGER_MAX_RANGE_PX instead (it
 * doesn't cleanly fit one flat per-class number the way this field implies). Left here rather
 * than removed since AttackType.MELEE/RANGED elsewhere still uses this enum's shape.
 *
 * Stat overhaul: both classes now 100 max HP.
 * Passive healing: Tank +3 HP/sec, Ranger +1 HP/sec
 *
 * Weapon remake: Tank's old melee sword swing is now a thrown, infinite-ammo Dagger (short
 * range, medium ~400ms cooldown here). Ranger's old melee dagger-fallback is now the Combat
 * Pin, a fired, infinite-ammo sidearm with its own much faster ~100ms cooldown, independent of
 * this class's attackCooldownMs (see Player.COMBAT_PIN_COOLDOWN_MS). Neither class swings a
 * melee weapon anymore.
 */
public enum PlayerClass {
    TANK(100, 6.0, AttackType.MELEE, 50, 96, 400, WeightClass.HEAVY, 0, 3),
    RANGER(100, TANK.moveSpeed, AttackType.RANGED, 90, 320, 1000, WeightClass.LIGHT, 20, 1);

    public final int maxHealth;
    public final double moveSpeed;      // px/frame
    public final AttackType attackType;
    public final int damage;
    public final int range;             // unused now -- see class doc's "range" note
    public final int attackCooldownMs;  // Tank's Dagger / Ranger's bow only -- NOT the Combat Pin
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
