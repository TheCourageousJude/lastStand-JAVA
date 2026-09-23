package laststand.shop;

import laststand.entity.PlayerClass;

import java.awt.Color;

/**
 * The 9 perks offered in the shop's upgrade row. Each levels up independently,
 * capped at MAX_LEVEL, purchased for 1 yellow orb per level. All reset to 0
 * on a new game (Player objects are simply recreated in GamePanel.startGame()).
 *
 * Rework pass: Melee Power -> Recovery (on-kill sustain instead of damage),
 * Toughen -> Grow (per-wave HP growth instead of a one-time %), Dodge ->
 * Reforged (per-wave flat damage instead of evasion). Armored also now gets
 * a stacking +1 Defense per boss kill on top of its normal levels.
 */
public enum UpgradeType {
    SPEEDY(new Color(90, 200, 90)) {
        @Override public String effectFor(PlayerClass cls) { return "+10% move speed / level"; }
    },
    ARMORED(new Color(120, 90, 60)) {
        @Override public String effectFor(PlayerClass cls) { return "+2 Defense / level, plus +1 Defense per boss defeated (stacks forever)"; }
    },
    RECOVERY(new Color(230, 210, 60)) {
        @Override public String effectFor(PlayerClass cls) {
            return cls == PlayerClass.TANK ? "+2 HP recovered per enemy slain, per level"
                    : "+1 arrow recovered per enemy slain, per level";
        }
    },
    CRITICAL(new Color(220, 60, 60)) {
        @Override public String effectFor(PlayerClass cls) { return "+3% chance to deal double damage / level"; }
    },
    INNATE_PROWESS(new Color(230, 140, 40)) {
        @Override public String effectFor(PlayerClass cls) {
            return cls == PlayerClass.TANK ? "+10 melee damage / level" : "+20 arrow damage / level";
        }
    },
    DARK_SORCERY(new Color(140, 90, 210)) {
        @Override public String effectFor(PlayerClass cls) { return "+1-3 dark orbs per wave, per level (scales to lvl*1..lvl*3)"; }
    },
    SILVER_BANK(new Color(185, 185, 195)) {
        @Override public String effectFor(PlayerClass cls) { return "+1-2 silver orbs per wave, per level (scales to lvl*1..lvl*2)"; }
    },
    GROW(new Color(210, 70, 80)) {
        @Override public String effectFor(PlayerClass cls) { return "+2 max HP per wave cleared, per level (stacks forever)"; }
    },
    REFORGED(new Color(70, 160, 220)) {
        @Override public String effectFor(PlayerClass cls) { return "+1 damage per wave cleared, per level (stacks forever)"; }
    };

    public static final int MAX_LEVEL = 20;
    public final Color color;

    UpgradeType(Color color) {
        this.color = color;
    }

    public abstract String effectFor(PlayerClass cls);

    public String displayName() {
        String[] parts = name().split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1).toLowerCase()).append(' ');
        }
        return sb.toString().trim();
    }
}
