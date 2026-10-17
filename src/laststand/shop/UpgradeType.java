package laststand.shop;

import laststand.entity.PlayerClass;

import java.awt.Color;
import java.util.Arrays;

/**
 * The perks offered in the shop's upgrade row. Each levels up independently, capped at
 * MAX_LEVEL, purchased for 1 yellow orb per level. All reset to 0 on a new game (Player objects
 * are simply recreated in GamePanel.startGame()).
 *
 * Perk pass: Armored and Grow were removed. Rechargeability takes one of those slots; the other
 * is intentionally vacant for now (to be filled once decided). Recovery was reworked and renamed
 * "Innate Talent" -- the constant keeps the name RECOVERY internally so save files that contain
 * it keep loading; only the label players see changed.
 *
 * ARMORED and GROW stay in this enum as RETIRED constants purely so older save files (whose
 * upgrade map names them) can still be deserialized -- Java refuses to read an enum constant that
 * no longer exists, which would make the whole save unreadable. Retired perks are never offered,
 * shown, or applied: always go through active() instead of values() for anything player-facing,
 * and drop them when loading (see GamePanel.loadGame()).
 */
public enum UpgradeType {
    SPEEDY("Speedy", new Color(90, 200, 90)) {
        @Override public String effectFor(PlayerClass cls) { return "+6% move speed / level"; }
    },
    RECOVERY("Innate Talent", new Color(230, 210, 60)) {
        @Override public String effectFor(PlayerClass cls) {
            return cls == PlayerClass.TANK
                    ? "Restores 1% of your Max HP per enemy slain, +1% per level (up to 20%)"
                    : "3% chance per level that a shot doesn't use an arrow (up to 60%)";
        }
    },
    CRITICAL("Critical", new Color(220, 60, 60)) {
        @Override public String effectFor(PlayerClass cls) { return "+3% chance to deal double damage / level"; }
    },
    PRESERVED_POWER("Preserved Power", new Color(230, 140, 40)) {
        @Override public String effectFor(PlayerClass cls) {
            return "Grants a minor increase to stats the higher your EXP level";
        }
    },
    DARK_SORCERY("Dark Sorcery", new Color(140, 90, 210)) {
        @Override public String effectFor(PlayerClass cls) { return "+1-4 dark orbs per wave, per level (scales to lvl*1..lvl*4)"; }
    },
    SILVER_BANK("Silver Bank", new Color(185, 185, 195)) {
        @Override public String effectFor(PlayerClass cls) { return "+1-2 silver orbs per wave, per level (scales to lvl*1..lvl*2)"; }
    },
    REFORGED("Reforged", new Color(70, 160, 220)) {
        @Override public String effectFor(PlayerClass cls) { return "+2 damage per wave cleared, per level (stacks forever)"; }
    },
    RECHARGEABILITY("Rechargeability", new Color(90, 220, 220)) {
        @Override public String effectFor(PlayerClass cls) { return "-1 charge needed for your ultimate on levels 1-4, 6-9, 11-14, 16-19 (20 -> 4); levels 5/10/15/20 boost Giant Arrow to 175%/200%/250%/300% of arrow damage"; }
    },

    // ---- RETIRED: kept only so old save files still deserialize. Never offered or applied. ----
    ARMORED("Armored", new Color(120, 90, 60), true) {
        @Override public String effectFor(PlayerClass cls) { return ""; }
    },
    GROW("Grow", new Color(210, 70, 80), true) {
        @Override public String effectFor(PlayerClass cls) { return ""; }
    };

    public static final int MAX_LEVEL = 20;
    private static final UpgradeType[] ACTIVE = Arrays.stream(values()).filter(t -> !t.retired).toArray(UpgradeType[]::new);

    public final Color color;
    private final String label;
    public final boolean retired;

    UpgradeType(String label, Color color) {
        this(label, color, false);
    }

    UpgradeType(String label, Color color, boolean retired) {
        this.label = label;
        this.color = color;
        this.retired = retired;
    }

    /** The perks that actually exist in play (everything except the retired save-compat constants). */
    public static UpgradeType[] active() {
        return ACTIVE.clone();
    }

    public abstract String effectFor(PlayerClass cls);

    public String displayName() {
        return label;
    }
}
