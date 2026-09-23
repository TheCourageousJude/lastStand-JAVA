package laststand.entity;

/** Heavier units take less knockback; lighter units get shoved further. */
public enum WeightClass {
    LIGHT(1.6),
    MEDIUM(1.0),
    HEAVY(0.5);

    public final double knockbackMultiplier;

    WeightClass(double knockbackMultiplier) {
        this.knockbackMultiplier = knockbackMultiplier;
    }
}
