package laststand.entity;

public enum ArmorSlot {
    HELMET("Helmet"),
    CHESTPLATE("Chestplate"),
    LEGGINGS("Leggings");

    public final String displayName;

    ArmorSlot(String displayName) {
        this.displayName = displayName;
    }
}
