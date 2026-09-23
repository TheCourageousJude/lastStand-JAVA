package laststand.shop;

import java.awt.Color;

/**
 * Armor ladder: NONE -> Leather -> Copper -> Iron -> Gold -> Diamond. One
 * purchase upgrades the WHOLE set at once (all 3 pieces share this single
 * tier) -- defensePerPiece x 3 = total Defense (0/3/6/9/15/24).
 *
 * Tank starts the whole set at LEATHER for free (no purchase needed); Ranger
 * starts at NONE and has to buy up from scratch. Diamond is Tank-exclusive.
 */
public enum ArmorTier {
    NONE(0, null, 0, new Color(90, 90, 90)),
    LEATHER(10, Currency.DARK, 1, new Color(150, 100, 60)),
    COPPER(20, Currency.DARK, 2, new Color(200, 120, 60)),
    IRON(30, Currency.DARK, 3, new Color(180, 180, 190)),
    GOLD(40, Currency.DARK, 5, new Color(230, 190, 60)),
    DIAMOND(5, Currency.SILVER, 8, new Color(110, 220, 235));

    public final int cost;
    public final Currency currency; // null for NONE, which is never purchased
    public final int defensePerPiece;
    public final Color displayColor; // used by the buff-bar armor icons

    ArmorTier(int cost, Currency currency, int defensePerPiece, Color displayColor) {
        this.cost = cost;
        this.currency = currency;
        this.defensePerPiece = defensePerPiece;
        this.displayColor = displayColor;
    }

    public ArmorTier next() {
        int i = ordinal();
        ArmorTier[] all = values();
        return (i + 1 < all.length) ? all[i + 1] : null;
    }
}
