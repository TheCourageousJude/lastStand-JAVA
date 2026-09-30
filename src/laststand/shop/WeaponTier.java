package laststand.shop;

import java.awt.Color;

/**
 * Same tier ladder for both weapons, but damage is now an explicit table per
 * class rather than a shared multiplier -- the new numbers don't scale
 * evenly, so a flat lookup replaces the old multiplier math.
 */
public enum WeaponTier {
    // Material colors -- what the weapon glyph itself is drawn in. The T1-T5 green/orange/
    // silver/gold/cyan palette belongs to the ENCHANT tier instead (see EnchantSpinOption.
    // TIER_COLORS), used for the box BEHIND the glyph -- these are two independent things now:
    // material (what you bought) vs. enchant investment (what you've rolled into it).
    WOODEN("Wooden", 0, null, 50, 90, new Color(150, 111, 51)),
    FLINT("Flint", 20, Currency.DARK, 60, 105, new Color(100, 100, 105)),
    IRON("Iron", 25, Currency.DARK, 75, 130, new Color(195, 195, 200)),
    GOLDEN("Golden", 30, Currency.DARK, 95, 165, new Color(218, 165, 32)),
    DIAMOND("Diamond", 5, Currency.SILVER, 110, 220, new Color(80, 220, 230));

    public final String displayName;
    public final int cost;
    public final Currency currency; // null for the free starting tier
    public final int swordDamage;
    public final int bowDamage;
    public final Color displayColor; // the weapon glyph's own color -- material, not enchant tier (see HUD)

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
