package laststand.wave;

/**
 * Wave scenarios. Waves 1-4 and every boss wave always use NORMAL. Every other wave from 6 on
 * rolls one of the eligible scenarios below at random -- see fixedChance().
 *
 * Spawn pacing is specified as "all of them out in ~N seconds", so the per-enemy interval is
 * derived: spawnWindowMs / referenceCount. A scenario whose count shrinks with fewer portals
 * (HEAVY_HITTERS, GHASTLIES) keeps the same per-enemy interval, so the wave just ends sooner.
 *
 * Multipliers apply on top of the enemy's normal wave-scaled stats.
 */
public enum Scenario {
    //            label             window  refCnt  hp    speed  dmg
    NORMAL        ("Normal",         0,      0,     1.0,  1.0,   1.0),
    SWARM         ("Swarm",          5000,   30,    0.5,  1.35,  0.8), // 30 fast, fragile, weaker hitters -- 3 portals only
    HEAVY_HITTERS ("Heavy Hitters",  5000,   15,    1.0,  0.6,   1.6), // slow but hard-hitting -- 1 portal: 8, 2 portals: 15
    STANDARD      ("Standard",       7500,   20,    1.0,  1.0,   1.0), // 20 classic-stat enemies
    POWER_RANGERS ("Power Rangers",  5000,   30,    1.0,  1.0,   1.0), // forces 3 portals, 20 skeletons + 10 blaster golems
    SAVAGES       ("Savages",        3000,   20,    1.0,  1.0,   1.0), // wave 26+, 2 portals: 20 red savage enemies (Savage / skeleton / blaster golem)
    GHASTLIES     ("Ghastlies",      3000,   20,    1.0,  1.0,   1.0); // wave 26+, melee-only, 1 portal: 15 / 2 portals: 20, near-invisible


    // --- how a wave 6+ (non-boss) scenario is chosen ---
    // Scenarios with a fixed chance (below) are rolled first, in declaration order, but only when
    // eligible for the wave. Whatever probability is left is split evenly among the eligible
    // scenarios that have no fixed chance (Swarm / Heavy Hitters / Standard).
    //   Power Rangers: 10% on every wave.
    //   Savages: 40% -- wave 26+, 2-portal waves only.
    //   Ghastlies: 8% -- wave 26+, 1- or 2-portal waves. Kept well below Savages on purpose.
    // e.g. wave 26+ with 2 portals: PR 10 / Savage 40 / Ghastly 8 / Heavy 21 / Standard 21.
    //      wave 26+ with 1 portal : PR 10 / Ghastly 8 / Heavy 41 / Standard 41.
    public double fixedChance() {
        return switch (this) {
            case POWER_RANGERS -> 0.10;
            case SAVAGES -> 0.40;
            case GHASTLIES -> 0.08;
            default -> 0.0;
        };
    }

    public static final int MIN_SPECIAL_WAVE = 26; // "only available waves past 25"

    public final String label;
    private final int spawnWindowMs;
    private final int referenceCount;
    public final double hpMult, speedMult, damageMult;

    Scenario(String label, int spawnWindowMs, int referenceCount,
             double hpMult, double speedMult, double damageMult) {
        this.label = label;
        this.spawnWindowMs = spawnWindowMs;
        this.referenceCount = referenceCount;
        this.hpMult = hpMult;
        this.speedMult = speedMult;
        this.damageMult = damageMult;
    }

    /** Can this scenario run on a non-boss wave >= 6 that would naturally open 'portals' portals? */
    public boolean eligible(int wave, int portals) {
        return switch (this) {
            case NORMAL -> false;                       // never rolled; used for waves 1-5 / bosses
            case SWARM -> portals == 3;
            case HEAVY_HITTERS -> portals <= 2;
            case STANDARD -> true;
            case POWER_RANGERS -> true;                 // overrides the portal count
            case SAVAGES -> wave >= MIN_SPECIAL_WAVE && portals == 2;
            case GHASTLIES -> wave >= MIN_SPECIAL_WAVE && portals <= 2;
        };
    }

    /** Portals actually opened (POWER_RANGERS forces all 3). */
    public int portalsToOpen(int naturalPortals) {
        return this == POWER_RANGERS ? 3 : naturalPortals;
    }

    public int enemyCount(int wave, int portals) {
        return switch (this) {
            case NORMAL -> 3 + wave;
            case HEAVY_HITTERS -> portals == 1 ? 8 : 15;
            case GHASTLIES -> portals == 1 ? 15 : 20;
            default -> referenceCount;
        };
    }

    public int spawnIntervalMs(int normalIntervalMs) {
        return this == NORMAL ? normalIntervalMs : Math.max(1, spawnWindowMs / referenceCount);
    }
}
