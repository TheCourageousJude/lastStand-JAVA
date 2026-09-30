package laststand.entity;

import laststand.core.Constants;
import laststand.core.InputHandler;
import laststand.shop.ArmorTier;
import laststand.shop.Currency;
import laststand.shop.EnchantCategory;
import laststand.shop.EnchantSpinOption;
import laststand.shop.EnchantType;
import laststand.shop.UpgradeType;
import laststand.shop.Wallet;
import laststand.shop.WeaponTier;
import laststand.world.Arena;

import java.awt.Color;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class Player extends Entity {

    public final int playerNumber; // 1 or 2
    public PlayerClass playerClass;
    public int ammo;
    public double facingX = 0, facingY = -1; // default facing "up"
    public int facingSide = 1;               // 1 = east/right, -1 = west/left (weapon sits on this side)
    private long lastAttackAt = -999999;
    private long lastCombatPinAt = -999999;

    // -------------------------------------------------------------------------------------
    // Weapon remake: melee swings don't exist anymore for either class -- everything is now a
    // thrown/fired projectile (see GamePanel's fireDagger/fireArrow/fireCombatPin).
    //   - Tank's old sword swing is now an infinite-ammo thrown DAGGER: short range, medium
    //     fire rate, small knockback, a bit smaller than an arrow.
    //   - Ranger's old melee dagger-fallback is now an infinite-ammo COMBAT PIN: tiny range,
    //     very fast fire rate, no knockback at all, tinier than the Dagger.
    // Tuned-by-feel starting numbers -- easy to retune, all in one place.
    // -------------------------------------------------------------------------------------
    public static final double DAGGER_MAX_RANGE_PX = 3.0 * Constants.TILE_SIZE; // ~3.0 tiles (was 1.5)
    public static final double DAGGER_SPEED_PX = 6.0;    // px/frame -- a controlled toss
    public static final double DAGGER_KNOCKBACK = 4.0;   // small, vs. the bow's ~8
    public static final int DAGGER_PROJECTILE_RADIUS = 4; // a little smaller than an arrow (5)

    public static final long COMBAT_PIN_COOLDOWN_MS = 100; // independent of the bow's cooldown
    public static final double COMBAT_PIN_MAX_RANGE_PX = 1.75 * Constants.TILE_SIZE; // ~1.75 tiles (was 0.75)
    public static final double COMBAT_PIN_SPEED_PX = 10.0; // faster than the Dagger -- a flick, not a throw
    public static final double COMBAT_PIN_KNOCKBACK = 0.0; // none at all
    public static final int COMBAT_PIN_PROJECTILE_RADIUS = 2; // a lot smaller than the Dagger (4)

    // Shop-purchased gear
    public WeaponTier weaponTier = WeaponTier.WOODEN;
    public int medicKits = 0;
    public static final int MEDIC_KIT_HEAL = 30;
    private long lastHealFlashAt = -999999;

    // Armor: ONE tier shared by all 3 pieces (Helmet/Chest/Legs upgrade together in a single
    // purchase) -- Tank starts the whole set at Leather for free; Ranger starts with none at all.
    public ArmorTier armorTier;

    public int armorDefense() {
        return armorTier.defensePerPiece * 3;
    }

    /** Returns true if the upgrade went through (had enough orbs, wasn't maxed, and -- for
     *  Diamond -- the player is a Tank, since Diamond armor is Tank-exclusive). Upgrades the
     *  whole set (Helmet + Chest + Legs) at once. */
    public boolean tryUpgradeArmor(Wallet wallet) {
        ArmorTier next = armorTier.next();
        if (next == null) return false;
        if (next == ArmorTier.DIAMOND && playerClass != PlayerClass.TANK) return false;
        if (!wallet.trySpend(next.currency, next.cost)) return false;
        armorTier = next;
        return true;
    }

    // ---------------------------------------------------------- Enchanting

    public boolean enchanting = false; // frozen (can't move/attack) while true; see GamePanel
    public EnchantCategory enchantCategory = EnchantCategory.HELMET;

    // Each of the 5 gear categories (Helmet/Chest/Legs/Dagger/Bow) has its OWN independent
    // enchant set -- e.g. your helmet and leggings can have different Regen levels.
    public final Map<EnchantCategory, Map<EnchantType, Integer>> enchants = new EnumMap<>(EnchantCategory.class);
    private final Random enchantRng = new Random();

    // The result of the last spin, kept around purely so the UI can show what just happened
    // ("no enchantment reveals" beforehand -- you only see the outcome after spinning).
    public int lastSpinTier = 0; // 0 = no spin yet this session
    public Map<EnchantType, Integer> lastSpinResult = Map.of();
    public EnchantSpinOption lastSpinOption = EnchantSpinOption.DEFAULT; // so the wheel graphic matches what was actually spun
    public double lastSpinLandingFraction = 0.5; // 0-1 position within the winning wedge, randomized per spin so it doesn't always land dead-center

    private static final EnchantCategory[] ARMOR_PIECES =
            {EnchantCategory.HELMET, EnchantCategory.CHESTPLATE, EnchantCategory.LEGGINGS};

    /** Sums this enchant type's value across every category it can appear on -- for armor types
     *  (Regen/Protection/HP Boost) that means summed across all 3 pieces; for Dagger/Bow-specific
     *  types it's just that one category. */
    public double enchantValue(EnchantType type) {
        double sum = 0;
        EnchantCategory[] categories = type.category == null ? ARMOR_PIECES : new EnchantCategory[]{type.category};
        for (EnchantCategory cat : categories) {
            Integer amp = enchants.getOrDefault(cat, Map.of()).get(type);
            if (amp != null) sum += type.amountFor(amp);
        }
        return sum;
    }

    /** How enchanted one gear slot is, as a Tier 1-5 (0 = nothing rolled into it at all) --
     *  matches the same Tier 1-5 the enchant wheel itself rolls (see EnchantSpinOption), for
     *  coloring the background behind that slot's icon in the hotbar/buff bar (HUD). A slot's 3
     *  enchant types each cap at amplifier III, so the raw sum of amplifiers ranges 0-9; that's
     *  bucketed down into the same 5 tiers two amplifier-points at a time (9 -> Tier 5, 7-8 ->
     *  Tier 4, and so on) since there's no single stored "last roll" per slot, only the
     *  cumulative amplifiers from however many spins landed there over time. */
    public int enchantTierFor(EnchantCategory category) {
        int sum = 0;
        for (int amp : enchants.getOrDefault(category, Map.of()).values()) sum += amp;
        return sum <= 0 ? 0 : Math.min(5, (sum + 1) / 2);
    }

    /** Armor pieces are open to everyone; Dagger only makes sense for the Tank, Bow only for the Ranger.
     *  (Internally still EnchantCategory.SWORD -- kept as-is so old saves' enchant slots still
     *  resolve correctly; only the displayed name changed, see EnchantUI.) */
    public boolean canUseEnchantCategory(EnchantCategory category) {
        if (category.isArmorPiece()) return true;
        if (category == EnchantCategory.SWORD) return playerClass == PlayerClass.TANK;
        return playerClass == PlayerClass.RANGER; // BOW
    }

    /**
     * "To automatically switch back to main weapon, they must deselect their current pick" --
     * pressing the same category's key a second time toggles back to the player's one main
     * weapon slot (Dagger for Tank, Bow for Ranger -- "not 2 slots, that is the main weapon
     * itself"), instead of doing nothing.
     */
    public void setEnchantCategory(EnchantCategory category) {
        if (!canUseEnchantCategory(category)) return;
        EnchantCategory weaponCategory = playerClass == PlayerClass.TANK ? EnchantCategory.SWORD : EnchantCategory.BOW;
        if (enchantCategory == category) {
            if (category == weaponCategory) return; // already on the weapon slot; nothing to toggle back to
            category = weaponCategory;
        }
        if (enchantCategory == category) return;
        enchantCategory = category;
        lastSpinTier = 0;
        lastSpinResult = Map.of();
    }

    public boolean trySpendExp(int amount) {
        if (exp < amount) return false;
        exp -= amount;
        return true;
    }

    /** "It must subtract by LEVEL" -- whole levels now, not a fraction of the current EXP bar. */
    public boolean trySpendLevel(int amount) {
        if (level < amount) return false;
        level -= amount;
        refreshPreservedPowerBonus(); // level just went down -- Preserved Power's HP scales with it
        return true;
    }

    // "Spinning animation for 1 second" -- the roll happens immediately (so it's deterministic
    // and can't be re-triggered mid-spin), but the UI hides the result and shows a spin animation
    // until this timestamp. Deliberately stamped and checked against REAL wall-clock time, not
    // the paused-aware world clock threaded everywhere else: this is a short, self-contained UI
    // animation for the menu the player is actively looking at, and pausing it via the same clock
    // that "pause during shop/enchant" freezes would be self-referential -- opening the very menu
    // that spins the wheel would freeze the clock the wheel needs to finish spinning, so with
    // pausing ON the animation would never complete. It always plays out in real time instead.
    public long spinAnimUntil = 0;
    public static final long SPIN_ANIM_MS = 1000;

    public boolean isSpinning() {
        return System.currentTimeMillis() < spinAnimUntil;
    }

    // Which category's animation is currently running, and what that category held right before
    // this spin overwrote it -- lets the UI keep showing the OLD equipped enchant for the full
    // animation instead of spoiling the new roll the instant it's rolled. Null/empty when idle.
    public EnchantCategory spinningCategory = null;
    public Map<EnchantType, Integer> preSpinSnapshot = null;

    // The 1-LVL "Default" spin has no real cost gate (1 LVL is trivial to earn), so it gets its
    // own per-wave usage cap instead -- refilled to the max whenever a wave is cleared (see
    // GamePanel.awardWaveCompletionOrbs()). Priced spins (30/90/270/450) are uncapped; their LVL
    // cost is the only limiter.
    public static final int DEFAULT_SPINS_PER_WAVE = 10;
    public int defaultSpinsUsedThisWave = 0;

    /** Called whenever a wave is cleared -- refills the Default spin's per-wave allowance. */
    public void resetDefaultSpinsForNewWave() {
        defaultSpinsUsedThisWave = 0;
    }

    /**
     * Pays the option's cost in whole LEVELS, spins the wheel for a Tier, then builds a random
     * combo of (type, amplifier) pairs from the current category's 3-type pool whose amplifiers
     * sum to that Tier -- e.g. Tier 3 might be one type at III, or all three types at I each, or
     * one at I plus one at II. The combo REPLACES this category's enchants outright (no stacking
     * with whatever was equipped before). Starts the 1-second spin animation on success.
     */
    public boolean trySpin(EnchantSpinOption option, long nowMs) {
        if (isSpinning()) return false; // can't spin again mid-animation
        if (option == EnchantSpinOption.DEFAULT && defaultSpinsUsedThisWave >= DEFAULT_SPINS_PER_WAVE) {
            return false; // out of Default spins for this wave -- clear a wave to refill
        }
        if (!trySpendLevel(option.levelCost)) return false;
        if (option == EnchantSpinOption.DEFAULT) defaultSpinsUsedThisWave++;

        // Snapshot what this category held BEFORE this roll overwrites it, so the UI can keep
        // showing the old enchant for the full animation instead of revealing the new one early.
        Map<EnchantType, Integer> previous = enchants.get(enchantCategory);
        preSpinSnapshot = previous == null ? null : new EnumMap<>(previous);
        spinningCategory = enchantCategory;

        int tier = option.rollTier(enchantRng);
        int[] partition = randomPartition(tier);

        List<EnchantType> pool = new ArrayList<>(EnchantType.forCategory(enchantCategory));
        Collections.shuffle(pool, enchantRng);

        Map<EnchantType, Integer> result = new EnumMap<>(EnchantType.class);
        for (int i = 0; i < partition.length; i++) {
            result.put(pool.get(i), partition[i]);
        }
        enchants.put(enchantCategory, result);
        lastSpinTier = tier;
        lastSpinResult = result;
        lastSpinOption = option;
        lastSpinLandingFraction = enchantRng.nextDouble();
        spinAnimUntil = System.currentTimeMillis() + SPIN_ANIM_MS;

        if (enchantCategory.isArmorPiece()) {
            // Re-derive HP Boost's contribution to maxHealth -- covers gaining, increasing,
            // decreasing, and losing the enchant, all through the same recompute.
            refreshHpBoostBonus();
        }
        return true;
    }

    /** All ways to split n into at most 3 parts, each 1-3 (matching amplifiers I/II/III and the
     *  3-type pool), picked uniformly at random. E.g. n=3 -> [3], [2,1], or [1,1,1]. */
    private int[] randomPartition(int n) {
        List<int[]> options = new ArrayList<>();
        for (int a = Math.min(3, n); a >= 1; a--) {
            int rem1 = n - a;
            if (rem1 == 0) { options.add(new int[]{a}); continue; }
            for (int b = Math.min(a, rem1); b >= 1; b--) {
                int rem2 = rem1 - b;
                if (rem2 == 0) { options.add(new int[]{a, b}); continue; }
                for (int c = Math.min(b, rem2); c >= 1; c--) {
                    if (rem2 - c == 0) options.add(new int[]{a, b, c});
                }
            }
        }
        return options.get(enchantRng.nextInt(options.size()));
    }

    public double luckyStrikeBonusPercent() { return enchantValue(EnchantType.LUCKY_STRIKE); }
    public double arrowSpeedBonusPercent() { return enchantValue(EnchantType.ARROW_SPEED); }

    // Shield: unlock once for 5 silver orbs, then activate on a 20s cooldown. While active,
    // "all damage nullified" and no knockback, for both contact and projectile hits.
    public boolean shieldUnlocked = false;
    public static final int SHIELD_COST_SILVER = 5;
    public static final long SHIELD_DURATION_MS = 5000; // how long each activation lasts
    public static final long SHIELD_COOLDOWN_MS = 20000; // time between activations
    private long shieldActiveUntil = -1;
    private long shieldLastUsedAt = -999999;

    // Inventory hotbar: 0=weapon, 1=medic kit, 2=shield, 3=secondary (Ranger's manual dagger).
    // P1 selects with 1/2/3/4, P2 with 0/9/8/7; their attack key then "uses" whatever's selected.
    public int selectedSlot = 0;

    // EXP, banked for the upcoming enchantment system. A full 500 bar converts to +1 LVL and
    // rolls over (doesn't just cap and sit there) -- "each bar that is full adds +1 LVL".
    public int exp = 0;
    public int level = 0;
    public static final int EXP_CAP = 50; // "50/50" -- much quicker leveling so LVL 20 (reroll's gate) is reachable
    public static final int REROLL_LEVEL_REQUIREMENT = 20; // player LVL needed to reroll upgrade offers
    public static final int MAX_LEVEL = 500; // "no exceeding, the bar visually looks full" once hit

    public void addExp(int amount) {
        if (level < MAX_LEVEL) {
            exp += amount;
            while (exp >= EXP_CAP && level < MAX_LEVEL) {
                exp -= EXP_CAP;
                level++;
            }
        }
        if (level >= MAX_LEVEL) {
            level = MAX_LEVEL;
            exp = EXP_CAP; // sit visually full forever, instead of cycling 0..49 for no further LVL
        }
        refreshPreservedPowerBonus(); // level may have just gone up -- Preserved Power's HP scales with it
    }

    // Upgrade shop: 9 perks, each levels independently up to UpgradeType.MAX_LEVEL,
    // 1 yellow orb per level. 3 random options offered at a time, rerollable for free.
    // All of this resets to empty/0 on a new game since Player is simply recreated.
    public final Map<UpgradeType, Integer> upgradeLevels = new EnumMap<>(UpgradeType.class);
    public final List<UpgradeType> offeredPerks = new ArrayList<>();

    public int upgradeLevel(UpgradeType type) {
        return upgradeLevels.getOrDefault(type, 0);
    }

    /** Picks 3 upgrades to offer, at random, EXCLUDING anything already at MAX_LEVEL -- a maxed
     *  perk (Lv20/20) must never occupy one of the 3 slots; something still available takes its
     *  place immediately instead. Only falls back to including maxed types if literally every
     *  perk is maxed (nothing else left to offer), so this can never come up short of 3. */
    public void rerollPerks() {
        List<UpgradeType> pool = new ArrayList<>();
        for (UpgradeType type : UpgradeType.values()) {
            if (upgradeLevel(type) < UpgradeType.MAX_LEVEL) pool.add(type);
        }
        if (pool.size() < 3) pool = new ArrayList<>(List.of(UpgradeType.values())); // everything maxed -- nothing else to offer
        Collections.shuffle(pool);
        offeredPerks.clear();
        offeredPerks.addAll(pool.subList(0, 3));
    }

    /** Returns true if the purchase went through (had enough yellow orbs and wasn't maxed). */
    public boolean tryBuyPerk(UpgradeType type, Wallet wallet) {
        int current = upgradeLevel(type);
        if (current >= UpgradeType.MAX_LEVEL) return false;
        if (!wallet.trySpend(Currency.YELLOW, 1)) return false;
        upgradeLevels.put(type, current + 1);
        rerollPerks(); // buying one slot re-randomizes all 3 -- "give way for other upgrades"
        refreshPreservedPowerBonus(); // in case this purchase was Preserved Power itself
        return true;
    }

    // "Armored... +1 Defense per boss defeated (stackable)" -- only accrues for players who've
    // bought at least 1 level of Armored (see GamePanel.onEnemyKilled()); a player with no
    // upgrade never gains Defense just from a boss dying nearby.
    public int bossDefenseStacks = 0;

    // "Reforged... +2 damage per wave completed, per level (stackable)" -- was +1, buffed to
    // +3, now nerfed again to +2 -- a flat, ever-growing
    // damage bonus applied to every attack, replacing Dodge's old evasion role.
    public int reforgedDamageBonus = 0;

    // HP Boost folds straight into Max HP now, instead of a hidden absorb-shield that never
    // showed up on the bar. baseMaxHealth is the "real" max (class base + permanent Grow gains,
    // never includes HP Boost or Preserved Power); maxHealth is always baseMaxHealth + hpBoostBonus
    // + preservedPowerHPBonus, recomputed from scratch whenever any of those change -- see
    // recomputeMaxHealth().
    private int baseMaxHealth;
    private int hpBoostBonus = 0;
    private int preservedPowerHPBonus = 0;

    /**
     * Preserved Power rework -- the old "every 4 LVL" version scaled unbounded and got "too broken".
     * Then reworked again to +2 HP / +1 damage every 10 LVL milestones (still "too broken").
     * Now unified: BOTH HP and damage are +1 per level at each of 25 milestones (20,40,...,500)
     * -- same increment, same interval, for both stats. Both scale by how many levels of the
     * perk itself are purchased (up to UpgradeType.MAX_LEVEL), so the absolute ceiling is now
     * +500 Max HP / +500 damage at Preserved Power level 20 and LVL 500 (down from +2,000/+1,000).
     * Both still rise and fall live as the player's LVL balance changes -- gaining LVL raises
     * them, spending LVL on enchant spins lowers them.
     */
    private static int preservedPowerMilestones(int lvl) {
        return Math.min(lvl, 500) / 20;
    }

    private int preservedPowerDamageBonus() {
        return preservedPowerMilestones(level) * upgradeLevel(UpgradeType.PRESERVED_POWER);
    }

    /** Re-derives preservedPowerHPBonus from the current LVL balance -- called from addExp(),
     *  trySpendLevel(), and tryBuyPerk() (anything that can change level or the perk's own level). */
    private void refreshPreservedPowerBonus() {
        preservedPowerHPBonus = preservedPowerMilestones(level) * upgradeLevel(UpgradeType.PRESERVED_POWER);
        recomputeMaxHealth();
    }

    private static final int THROWN_DAMAGE_CAP = 80; // "until it caps at 80" -- applies to both thrown weapons now

    /** Base weapon damage (tier-scaled) + Preserved Power's dynamic bonus + Dagger Damage enchant +
     *  Reforged, capped at 80. Tank's thrown Dagger -- the old melee sword swing's replacement,
     *  same damage formula, same enchant hook (still under EnchantCategory.SWORD internally, so
     *  old saves/enchant slots keep working -- only the display name changed to "Dagger Damage"). */
    public int daggerDamage() {
        int base = effectiveDamage();
        int bonus = preservedPowerDamageBonus()
                + (int) Math.round(enchantValue(EnchantType.SWORD_DAMAGE))
                + reforgedDamageBonus;
        return Math.min(THROWN_DAMAGE_CAP, base + bonus);
    }

    /** Base weapon damage (tier-scaled) + Preserved Power's dynamic bonus + Arrow Damage enchant +
     *  Reforged. Critical's distance bonus is applied separately at impact, in GamePanel, since it
     *  needs travel distance. */
    public int arrowDamage() {
        int base = effectiveDamage();
        int bonus = preservedPowerDamageBonus()
                + (int) Math.round(enchantValue(EnchantType.ARROW_DAMAGE))
                + reforgedDamageBonus;
        return base + bonus;
    }

    /**
     * "Punitive stats so the enemies only tickle" -- the Combat Pin (formerly the melee dagger
     * fallback, now a tiny fired sidearm) is 20% of the Ranger's *effective* bow damage (tier +
     * Innate Prowess included), not a flat number, so it scales down proportionally as the bow
     * gets stronger instead of falling further behind it. Also gets Reforged, capped at 80 same
     * as the Dagger. Nerfed from 33% to 20% alongside the range buff (0.75 -> 1.75 tiles).
     */
    public int combatPinDamage() {
        int base = (int) Math.round(arrowDamage() * 0.20);
        return Math.min(THROWN_DAMAGE_CAP, base + reforgedDamageBonus);
    }

    /**
     * Total effective Defense: Armored's levels + boss-kill stacks + raw armor gear, all boosted
     * by the Protection enchant's percentage on top ("+10%/25%/50% of total defense... combined
     * with the current weapon & armor, and the upgrades they have").
     */
    public int totalDefense() {
        int base = upgradeLevel(UpgradeType.ARMORED) + bossDefenseStacks + armorDefense(); // was 2x/level
        double protectionPercent = enchantValue(EnchantType.PROTECTION) / 100.0; // summed across all 3 pieces
        return (int) Math.round(base * (1 + protectionPercent));
    }

    /**
     * Central damage entry point for players. Shield (if active) blocks everything outright;
     * otherwise Defense reduces the hit (floor of 1) and comes straight off real HP -- HP Boost
     * no longer needs special handling here since its bonus already lives inside maxHealth.
     */
    public boolean takeDamage(int rawAmount, double fromX, double fromY, double knockbackStrength, long nowMs) {
        if (!alive) return false;
        if (isShieldActive(nowMs)) {
            return false; // Shield: "all damage nullified" and no knockback, contact or projectile
        }
        int finalDamage = Math.max(1, rawAmount - totalDefense()); // Attack - Defense, floor of 1
        damage(finalDamage);
        applyKnockback(fromX, fromY, knockbackStrength);
        return true;
    }

    /** Recomputes maxHealth from baseMaxHealth + the current HP Boost bonus, and clamps current
     *  HP down if it now exceeds the new max (e.g. HP Boost was just rolled away). Current HP is
     *  never raised here -- only ever clamped down, or left untouched. */
    private void recomputeMaxHealth() {
        maxHealth = baseMaxHealth + hpBoostBonus + preservedPowerHPBonus;
        if (health > maxHealth) health = maxHealth;
    }

    /**
     * Re-derives the HP Boost bonus from scratch off baseMaxHealth (the "real" max, ignoring any
     * existing HP Boost) every time an armor piece's enchants change -- so a bigger roll, a
     * smaller roll, or rolling HP Boost off entirely (replaced by a different enchant) all just
     * fall out of enchantValue()'s live sum, rather than stacking or needing separate removal
     * logic. Current HP is left exactly where it was unless the new max is now smaller than it.
     */
    private void refreshHpBoostBonus() {
        double hpBoostPercent = enchantValue(EnchantType.HP_BOOST); // summed across all 3 armor pieces
        hpBoostBonus = (int) Math.round(baseMaxHealth * hpBoostPercent / 100.0);
        recomputeMaxHealth();
    }

    /**
     * Re-derives every dynamic bonus (HP Boost's Max HP contribution, Preserved Power's Max HP
     * contribution) from scratch off whatever level/upgradeLevels/enchants currently hold. Both
     * are normally kept in sync incrementally as those change during play; this is for restoring
     * a save, where level/upgrades/enchants get set directly rather than through the normal
     * gain/spend/purchase paths that would otherwise trigger the refresh automatically.
     */
    public void recomputeAllDerivedStats() {
        refreshPreservedPowerBonus();
        refreshHpBoostBonus();
    }

    /** Grow's per-wave permanent Max HP gain -- adds to the "real" base (so HP Boost's percentage
     *  keeps computing off the correct number) and heals the gain too, same as before. */
    public void grantPermanentMaxHealth(int amount) {
        baseMaxHealth += amount;
        recomputeMaxHealth();
        health = Math.min(maxHealth, health + amount);
    }

    // Default sprite colors: P1 red, P2 blue (per the sprite sheet sketch)
    public static Color colorFor(int playerNumber) {
        return playerNumber == 1 ? new Color(210, 40, 40) : new Color(40, 110, 220);
    }

    public Player(int playerNumber, PlayerClass playerClass, double x, double y) {
        super(x, y, 30, playerClass.maxHealth, colorFor(playerNumber));
        this.playerNumber = playerNumber;
        this.playerClass = playerClass;
        this.weightClass = playerClass.weightClass;
        this.ammo = playerClass.startingAmmo;
        this.baseMaxHealth = playerClass.maxHealth; // "real" max, before any HP Boost bonus
        rerollPerks();
        // "ONLY TANK has STARTER leather armor... otherwise no armor and no defense at all"
        armorTier = playerClass == PlayerClass.TANK ? ArmorTier.LEATHER : ArmorTier.NONE;
    }

    public void update(InputHandler input, Arena arena) {
        if (!alive) return;
        double dx = 0, dy = 0;
        if (playerNumber == 1) {
            if (input.p1Up()) dy -= 1;
            if (input.p1Down()) dy += 1;
            if (input.p1Left()) dx -= 1;
            if (input.p1Right()) dx += 1;
        } else {
            if (input.p2Up()) dy -= 1;
            if (input.p2Down()) dy += 1;
            if (input.p2Left()) dx -= 1;
            if (input.p2Right()) dx += 1;
        }

        if (dx != 0 || dy != 0) {
            double len = Math.hypot(dx, dy);
            dx /= len;
            dy /= len;
            facingX = dx;
            facingY = dy;
            if (facingX > 0.01) facingSide = 1;
            else if (facingX < -0.01) facingSide = -1;
            double speed = playerClass.moveSpeed * (1 + 0.10 * upgradeLevel(UpgradeType.SPEEDY));
            arena.moveWithCollision(this, dx * speed, dy * speed);
        }

        tickKnockback(arena);

        // passive healing -- Tank +3 HP/sec, Ranger +1 HP/sec, plus Regen enchant
        int healPerSec = playerClass.healPerSecond + (int) Math.round(enchantValue(EnchantType.REGEN));
        if (healPerSec > 0 && health < maxHealth) {
            healAccumulator += 1;
            if (healAccumulator >= Constants.FPS) {
                healAccumulator -= Constants.FPS;
                health = Math.min(maxHealth, health + healPerSec);
            }
        }
    }

    private int healAccumulator = 0;

    private boolean isCooldownReady(long nowMs) {
        return alive && (nowMs - lastAttackAt >= effectiveAttackCooldownMs());
    }

    /** Base cooldown reduced by Swing Speed (Tank's Dagger) or Quick Charge (bow) enchants,
     *  floored so it can never hit 0/negative. Also drives the weapon's "ready" color when
     *  drawing. Does NOT apply to the Combat Pin -- see COMBAT_PIN_COOLDOWN_MS, which is its
     *  own fixed, much shorter cooldown, independent of this one. */
    public long effectiveAttackCooldownMs() {
        double reductionPercent = playerClass == PlayerClass.TANK
                ? enchantValue(EnchantType.SWING_SPEED)
                : enchantValue(EnchantType.QUICK_CHARGE);
        double mult = Math.max(0.2, 1 - reductionPercent / 100.0); // floor at 20% of base cooldown
        return Math.max(50, Math.round(playerClass.attackCooldownMs * mult));
    }

    /**
     * Returns true if an attack actually fires. Edge-triggered on purpose --
     * pass true only on the frame the attack key was freshly pressed (not
     * held), so holding the button down can't auto-spam once cooldown is up.
     * Only called when the weapon slot (slot 0) is selected.
     *
     * For a Ranger with no arrows left, this returns false on purpose -- GamePanel routes that
     * case to tryCombatPinAttack() instead, which has its own much faster cooldown. Firing the
     * Dagger (Tank) or an arrow (Ranger, ammo > 0) both still go through this normal gate.
     */
    public boolean tryAttack(boolean attackJustPressed, long nowMs) {
        if (!attackJustPressed || !isCooldownReady(nowMs)) return false;
        if (playerClass.attackType == AttackType.RANGED && ammo <= 0) return false;
        lastAttackAt = nowMs;
        if (playerClass.attackType == AttackType.RANGED) ammo--;
        return true;
    }

    /**
     * The Ranger's Combat Pin: independent of tryAttack's cooldown entirely, so it can fire
     * far faster (COMBAT_PIN_COOLDOWN_MS) than the bow ever could. Used automatically once
     * arrows run out, or on demand from the hotbar even with arrows left.
     */
    public boolean tryCombatPinAttack(boolean actionJustPressed, long nowMs) {
        if (!alive || !actionJustPressed) return false;
        if (nowMs - lastCombatPinAt < COMBAT_PIN_COOLDOWN_MS) return false;
        lastCombatPinAt = nowMs;
        return true;
    }

    /**
     * Same cooldown gate as tryAttack, but without the weapon-specific side
     * effect (ammo consumption) -- used when the medic kit or shield
     * hotbar slot is selected instead. The Combat Pin slot bypasses this
     * entirely now (see tryCombatPinAttack).
     */
    public boolean tryUtilityAction(boolean actionJustPressed, long nowMs) {
        if (!actionJustPressed || !isCooldownReady(nowMs)) return false;
        lastAttackAt = nowMs;
        return true;
    }

    /** Explicit per-tier, per-class damage table now (Bow: 90/105/130/165/220, Dagger:
     *  50/60/75/95/110) -- replaces the old shared multiplier since these don't scale evenly. */
    public int effectiveDamage() {
        return playerClass == PlayerClass.TANK ? weaponTier.swordDamage : weaponTier.bowDamage;
    }

    public void useMedicKit(long nowMs) {
        if (medicKits > 0 && alive && health < maxHealth) {
            medicKits--;
            health = Math.min(maxHealth, health + MEDIC_KIT_HEAL);
            lastHealFlashAt = nowMs; // must share the caller's paused-aware clock, not wall time
        }
    }

    public void activateShield(long nowMs) {
        if (!shieldUnlocked || !alive) return;
        if (nowMs - shieldLastUsedAt < SHIELD_COOLDOWN_MS) return; // still on cooldown
        shieldLastUsedAt = nowMs;
        shieldActiveUntil = nowMs + SHIELD_DURATION_MS;
    }

    public boolean isShieldActive(long nowMs) {
        return nowMs < shieldActiveUntil;
    }

    public long shieldCooldownRemainingMs(long nowMs) {
        return Math.max(0, SHIELD_COOLDOWN_MS - (nowMs - shieldLastUsedAt));
    }

    /** Returns true if the purchase went through (wasn't already unlocked, had enough silver). */
    public boolean tryBuyShield(Wallet wallet) {
        if (shieldUnlocked) return false;
        if (!wallet.trySpend(Currency.SILVER, SHIELD_COST_SILVER)) return false;
        shieldUnlocked = true;
        return true;
    }

    public void respawn(double x, double y) {
        this.x = x;
        this.y = y;
        this.baseMaxHealth = playerClass.maxHealth;
        this.hpBoostBonus = 0;
        this.preservedPowerHPBonus = 0;
        this.maxHealth = playerClass.maxHealth;
        this.health = playerClass.maxHealth;
        this.ammo = playerClass.startingAmmo;
        this.alive = true;
    }

    @Override
    public void draw(Graphics2D g, int camX, int camY) {
        super.draw(g, camX, camY);
        // little facing indicator so players can see aim direction
        int cx = (int) centerX() - camX;
        int cy = (int) centerY() - camY;
        g.setColor(Color.BLACK);
        g.drawLine(cx, cy, cx + (int) (facingX * 18), cy + (int) (facingY * 18));
    }

    /** Full draw including the weapon rig -- call this one from the game loop. */
    public void draw(Graphics2D g, int camX, int camY, long nowMs) {
        draw(g, camX, camY);
        drawWeapon(g, camX, camY, nowMs);

        int sx = (int) x - camX, sy = (int) y - camY;
        if (isShieldActive(nowMs)) {
            // "huge silver-circular forcefield around the player"
            int cx2 = sx + size / 2, cy2 = sy + size / 2;
            int r = size + 14;
            g.setColor(new Color(210, 225, 235, 90));
            g.fillOval(cx2 - r, cy2 - r, r * 2, r * 2);
            g.setColor(new Color(230, 240, 250, 200));
            g.drawOval(cx2 - r, cy2 - r, r * 2, r * 2);
        }
        if (nowMs - lastHealFlashAt < 300) {
            g.setColor(new Color(60, 220, 80, 140));
            g.drawRect(sx - 2, sy - 2, size + 4, size + 4);
        }
    }

    private static final int DISAPPEAR_MS = 90; // Dagger/Combat Pin briefly vanish -- it's in flight now, not in hand
    private static final int BOW_RECOIL_MS = 150;

    // Shared tiny pixel-art glyphs -- also used by ui.HUD for the hotbar icons, so this is the
    // one place to edit them. Row-major, top-to-bottom / left-to-right, weapon-facing-right.
    public static final boolean[][] DAGGER_GLYPH = {
            {false, false, true, false},
            {true, true, true, true},
            {false, false, true, false}
    };
    public static final boolean[][] COMBAT_PIN_GLYPH = {
            {false, false, false, false},
            {true, true, true, true},
            {false, false, false, false}
    };
    public static final boolean[][] BOW_GLYPH = {
            {true, false, true},
            {true, false, true}
    };

    public static void drawPixelGlyph(Graphics2D g, boolean[][] glyph, int originX, int originY, int px, Color color) {
        g.setColor(color);
        for (int row = 0; row < glyph.length; row++) {
            for (int col = 0; col < glyph[row].length; col++) {
                if (glyph[row][col]) g.fillRect(originX + col * px, originY + row * px, px, px);
            }
        }
    }

    /** Weapon rig beside the character: green=ready, red=recharging. The bow still draws back
     *  and looses; the Dagger and Combat Pin are both thrown/fired now (no melee swings), so
     *  instead of a swing OR a recoil-kick, the glyph briefly disappears entirely right after
     *  firing -- it left the hand as the projectile now on screen -- then reappears (infinite
     *  ammo, so there's always another one to draw). */
    private void drawWeapon(Graphics2D g, int camX, int camY, long nowMs) {
        int cx = (int) centerX() - camX;
        int cy = (int) centerY() - camY;
        boolean onCombatPin = playerClass.attackType == AttackType.RANGED && ammo <= 0;
        // Out of arrows -> the ready/recharging color (and disappear timing) should reflect the
        // Combat Pin's own fast cooldown, not the bow's -- they're fully independent now.
        long sinceAttack = onCombatPin ? nowMs - lastCombatPinAt : nowMs - lastAttackAt;
        long cooldownMs = onCombatPin ? COMBAT_PIN_COOLDOWN_MS : effectiveAttackCooldownMs();
        double cooldownRatio = Math.min(1.0, sinceAttack / (double) cooldownMs);
        Color weaponColor = lerpColor(new Color(200, 40, 40), new Color(60, 210, 90), cooldownRatio);
        int baseX = cx + facingSide * (size / 2 + 4);

        boolean showingBow = playerClass.attackType == AttackType.RANGED && ammo > 0;

        Graphics2D wg = (Graphics2D) g.create();
        try {
            if (showingBow) {
                boolean firing = sinceAttack < BOW_RECOIL_MS;
                int kick = firing ? (int) (4 * (1 - sinceAttack / (double) BOW_RECOIL_MS)) : 0;
                wg.translate(baseX - facingSide * kick, cy);
                wg.setStroke(new java.awt.BasicStroke(3));
                wg.setColor(weaponColor);
                int startAngle = facingSide > 0 ? 270 : 90; // was inverted -- flipped to match facing
                wg.drawArc(-9, -15, 18, 30, startAngle, 180);
                wg.setColor(new Color(225, 225, 225));
                wg.drawLine(0, -14, 0, 14);
            } else if (sinceAttack >= DISAPPEAR_MS) {
                // Tank's Dagger, or the Ranger's Combat Pin fallback (out of arrows) -- both
                // thrown, drawn as the same tiny pixel glyphs as the hotbar icon so switching
                // to the Combat Pin (out of arrows) visibly swaps the held weapon, not just its
                // ammo count.
                boolean[][] glyph = onCombatPin ? COMBAT_PIN_GLYPH : DAGGER_GLYPH;
                int px = 3;
                int glyphW = glyph[0].length * px, glyphH = glyph.length * px;
                int originX = facingSide > 0 ? baseX : baseX - glyphW;
                wg.translate(originX, cy - glyphH / 2);
                drawPixelGlyph(wg, glyph, 0, 0, px, weaponColor);
            }
            // else: mid-throw -- draw nothing, it's the projectile on screen now.
        } finally {
            wg.dispose();
        }
    }

    private static Color lerpColor(Color a, Color b, double t) {
        t = Math.max(0, Math.min(1, t));
        int r = (int) (a.getRed() + (b.getRed() - a.getRed()) * t);
        int gr = (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t);
        int bl = (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t);
        return new Color(r, gr, bl);
    }
}
