package laststand.shop;

import java.awt.Color;

/**
 * Same tier ladder for both weapons, but damage is now an explicit table per
 * class rather than a shared multiplier -- the new numbers don't scale
 * evenly, so a flat lookup replaces the old multiplier math.
 */
public enum WeaponTier {
    WOODEN("Wooden", 0, null, 50, 90, new Color(139, 100, 60)),
    FLINT("Flint", 20, Currency.DARK, 60, 105, new Color(110, 110, 115)),
    IRON("Iron", 25, Currency.DARK, 75, 130, new Color(205, 205, 210)),
    GOLDEN("Golden", 30, Currency.DARK, 95, 165, new Color(230, 190, 60)),
    DIAMOND("Diamond", 5, Currency.SILVER, 110, 220, new Color(110, 230, 230));

    public final String displayName;
    public final int cost;
    public final Currency currency; // null for the free starting tier
    public final int swordDamage;
    public final int bowDamage;
    public final Color displayColor; // used by the inventory hotbar icon -- "corresponding color to what weapon they have"

    WeaponTier(String displayName, int cost, Currency currency, int swordDamage, int bowDamage, Color displayColor) {
        this.displayName = displayName;
        this.cost = cost;
        this.currency = currency;
        this.swordDamage = swordDamage;
        this.bowDamage = bowDamage;
        this.displayColor = displayColor;
    }

    public WeaponTier next() {
        int i = ordinal();
        WeaponTier[] all = values();
        return (i + 1 < all.length) ? all[i + 1] : null;
    }
}
