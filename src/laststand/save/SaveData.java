package laststand.save;

import laststand.entity.PlayerClass;
import laststand.shop.ArmorTier;
import laststand.shop.EnchantCategory;
import laststand.shop.EnchantType;
import laststand.shop.UpgradeType;
import laststand.shop.WeaponTier;

import java.io.Serializable;
import java.util.EnumMap;
import java.util.Map;

/**
 * Everything needed to resume a run: gear, currencies, and wave progress. Deliberately does NOT
 * capture in-progress combat state (enemy positions, projectiles in flight, exact player
 * position/HP) -- saving/loading always resumes at the start of the saved wave, at full health,
 * same as a fresh wave transition. That keeps this format small, human-readable-ish, and immune
 * to breaking if enemy/projectile internals change later, at the cost of losing a few seconds of
 * "already-in-progress" wave state, which nobody is likely to miss.
 *
 * Also deliberately does NOT capture the arena theme or the "pause during shop/enchant" option --
 * those are session/display settings, not run progress, so loading a save must never override
 * whatever the player currently has them set to (see GamePanel.loadGame()).
 */
public class SaveData implements Serializable {
    private static final long serialVersionUID = 1L;

    // theme and pauseDuringMenus used to live here and get restored on load -- removed on
    // purpose: a save is about a run's progress, not the menu/display options active when it
    // was written, so loading must never override whatever the player currently has those set
    // to. An older save file that still has these two fields in its stream deserializes fine;
    // Java's default serialization just ignores fields the class no longer declares.
    public boolean p2Enabled;

    public int currentWave;
    public int wavesCompleted;
    public int bossKillCount;

    public PlayerSave p1;
    public PlayerSave p2; // null if p2Enabled was false when saved

    /** One player's gear and progress. Position/HP/ammo-in-flight are not saved -- see the class
     *  doc on SaveData for why. */
    public static class PlayerSave implements Serializable {
        private static final long serialVersionUID = 1L;

        public PlayerClass playerClass;
        public int level;
        public int exp;
        public WeaponTier weaponTier;
        public ArmorTier armorTier;
        public int medicKits;
        public boolean shieldUnlocked;
        public Map<UpgradeType, Integer> upgradeLevels = new EnumMap<>(UpgradeType.class);
        public Map<EnchantCategory, Map<EnchantType, Integer>> enchants = new EnumMap<>(EnchantCategory.class);

        public int darkOrbs;
        public int silverOrbs;
        public int yellowOrbs;
    }
}
