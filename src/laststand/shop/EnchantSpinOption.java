package laststand.shop;

import java.awt.Color;
import java.util.Random;

/**
 * "1 XP still default, but if clicking: 30 XP..." -- 5 fixed spin options, each a straight
 * price (now paid in whole LEVELS, not fractional EXP) with its own odds for landing Tier 1-5.
 * DEFAULT matches the original 75/20/4/1/0 numbers; the rest are exactly as specified, with the
 * 30-level row inferred as "shift everything up one tier" per the (qualitative) description --
 * flagged in the README. Prices were nerfed across the board in a later balance pass (30->20,
 * 90->55, 270->115, 450->185), then nerfed again in a second pass (20->10, 55->20, 115->45,
 * 185->85) to make the pricier tiers actually reachable; DEFAULT also picked up a per-wave usage
 * cap around the same time (see Player.DEFAULT_SPINS_PER_WAVE) since it has no other limiter
 * besides 1 LVL.
 */
public enum EnchantSpinOption {
    DEFAULT(1, new double[]{74.9, 20.0, 4.0, 1.0, 0.1}),
    SPIN_30(10, new double[]{0, 75, 20, 4, 1}),
    SPIN_90(20, new double[]{0, 0, 66, 24, 10}),
    SPIN_270(45, new double[]{0, 0, 20, 50, 30}),
    SPIN_450(85, new double[]{0, 0, 0, 50, 50});

    // Tier 1-5 display colors, matching the ArmorTier/WeaponTier palette conventions
    public static final Color[] TIER_COLORS = {
            new Color(140, 200, 60),  // T1 green
            new Color(220, 130, 40),  // T2 orange/bronze
            new Color(190, 190, 200), // T3 silver/gray
            new Color(230, 190, 60),  // T4 gold
            new Color(110, 220, 235)  // T5 diamond
    };

    public final int levelCost;
    private final double[] weights; // index 0 = Tier 1 ... index 4 = Tier 5

    EnchantSpinOption(int levelCost, double[] weights) {
        this.levelCost = levelCost;
        this.weights = weights;
    }

    public double weightFor(int tier1to5) {
        return weights[tier1to5 - 1];
    }

    /** Rolls a Tier from 1-5 according to this option's odds. */
    public int rollTier(Random rng) {
        double total = 0;
        for (double w : weights) total += w;
        double roll = rng.nextDouble() * Math.max(0.0001, total);
        double cumulative = 0;
        for (int i = 0; i < weights.length; i++) {
            cumulative += weights[i];
            if (roll < cumulative) return i + 1;
        }
        return weights.length; // fallback, shouldn't hit
    }
}
