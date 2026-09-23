package laststand.shop;

import java.util.ArrayList;
import java.util.List;

/**
 * 9 enchant types, 3 per gear category, each with 3 amplifier levels (I/II/III).
 * amounts[0]=I, amounts[1]=II, amounts[2]=III. "unit" is just for display text.
 */
public enum EnchantType {
    // ARMOR -- category is null, meaning "applies to any of the 3 armor pieces"
    REGEN(null, "Regen", new double[]{1, 3, 5}, "HP/sec"),
    PROTECTION(null, "Protection", new double[]{10, 25, 50}, "% of total Defense"),
    HP_BOOST(null, "HP Boost", new double[]{20, 45, 90}, "% additional Max HP"),

    // BOW (Ranger only)
    QUICK_CHARGE(EnchantCategory.BOW, "Quick Charge", new double[]{10, 25, 50}, "% cooldown reduction"),
    ARROW_DAMAGE(EnchantCategory.BOW, "Arrow Damage", new double[]{10, 25, 50}, "arrow damage"),
    ARROW_SPEED(EnchantCategory.BOW, "Arrow Speed", new double[]{25, 50, 75}, "% arrow speed & knockback"),

    // SWORD (Tank only)
    SWING_SPEED(EnchantCategory.SWORD, "Swing Speed", new double[]{10, 25, 50}, "% cooldown reduction"),
    SWORD_DAMAGE(EnchantCategory.SWORD, "Sword Damage", new double[]{7, 14, 28}, "sword damage"),
    LUCKY_STRIKE(EnchantCategory.SWORD, "Lucky Strike", new double[]{5, 10, 20}, "% crit chance (stacks with Critical)");

    public final EnchantCategory category;
    public final String displayName;
    private final double[] amounts;
    public final String unit;

    EnchantType(EnchantCategory category, String displayName, double[] amounts, String unit) {
        this.category = category;
        this.displayName = displayName;
        this.amounts = amounts;
        this.unit = unit;
    }

    /** amplifier is 1 (I), 2 (II), or 3 (III). */
    public double amountFor(int amplifier) {
        return amounts[Math.max(1, Math.min(3, amplifier)) - 1];
    }

    public static String romanNumeral(int amplifier) {
        return switch (amplifier) {
            case 1 -> "I";
            case 2 -> "II";
            default -> "III";
        };
    }

    public static List<EnchantType> forCategory(EnchantCategory category) {
        List<EnchantType> list = new ArrayList<>();
        for (EnchantType t : values()) {
            boolean matches = (t.category == null) ? category.isArmorPiece() : t.category == category;
            if (matches) list.add(t);
        }
        return list;
    }
}
