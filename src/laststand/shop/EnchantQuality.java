package laststand.shop;

import java.awt.Color;
import java.util.Random;

/**
 * Quality of the enchant table itself (separate from the Tier that gets rolled).
 * Odds are weights out of 100 for landing Tier 1..5. BASIC matches the exact
 * numbers given (~75/20/4/1/0); the rest are my own progressively-better curves,
 * shifting weight toward higher tiers as quality goes up -- not specified in
 * the original request, easy to retune in one place.
 */
public enum EnchantQuality {
    BASIC(new int[]{75, 20, 4, 1, 0}),
    BRONZE(new int[]{50, 30, 15, 4, 1}),
    SILVER(new int[]{25, 35, 25, 10, 5}),
    GOLD(new int[]{10, 20, 30, 25, 15}),
    DIAMOND(new int[]{0, 5, 20, 35, 40});

    // Tier 1-5 display colors, matching the ArmorTier/WeaponTier palette conventions
    public static final Color[] TIER_COLORS = {
            new Color(90, 200, 90),   // T1 green
            new Color(180, 110, 60),  // T2 bronze
            new Color(190, 190, 200), // T3 silver
            new Color(230, 190, 60),  // T4 gold
            new Color(110, 220, 235)  // T5 diamond
    };

    private final int[] weights; // index 0 = Tier 1 ... index 4 = Tier 5

    EnchantQuality(int[] weights) {
        this.weights = weights;
    }

    /** Rolls a Tier from 1-5 according to this quality's odds. */
    public int rollTier(Random rng) {
        int total = 0;
        for (int w : weights) total += w;
        int roll = rng.nextInt(Math.max(1, total));
        int cumulative = 0;
        for (int i = 0; i < weights.length; i++) {
            cumulative += weights[i];
            if (roll < cumulative) return i + 1;
        }
        return weights.length; // fallback, shouldn't hit
    }

    public EnchantQuality next() {
        int i = ordinal();
        EnchantQuality[] all = values();
        return all[(i + 1) % all.length]; // wraps around
    }

    public EnchantQuality previous() {
        int i = ordinal();
        EnchantQuality[] all = values();
        return all[(i - 1 + all.length) % all.length]; // wraps around
    }
}
