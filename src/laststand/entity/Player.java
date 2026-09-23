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

    // Fired last attack using the Ranger's emergency dagger (out of arrows)? Read by GamePanel.
    public boolean usedDaggerLastAttack = false;
    public static final int DAGGER_RANGE = 65; // bumped up a bit from 50

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

    // Each of the 5 gear categories (Helmet/Chest/Legs/Sword/Bow) has its OWN independent
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
     *  (Regen/Protection/HP Boost) that means summed across all 3 pieces; for Sword/Bow-specific
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

    /** Armor pieces are open to everyone; Sword only makes sense for the Tank, Bow only for the Ranger. */
    public boolean canUseEnchantCategory(EnchantCategory category) {
        if (category.isArmorPiece()) return true;
        if (category == EnchantCategory.SWORD) return playerClass == PlayerClass.TANK;
        return playerClass == PlayerClass.RANGER; // BOW
    }

    /**
     * "To automatically switch back to main weapon, they must deselect their current pick" --
     * pressing the same category's key a second time toggles back to the player's one main
     * weapon slot (Sword for Tank, Bow for Ranger -- "not 2 slots, that is the main weapon
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
        return true;
    }

    // "Spinning animation for 1 second" -- the roll happens immediately (so it's deterministic
    // and can't be re-triggered mid-spin), but the UI hides the result and shows a spin animation
    // until this timestamp.
    public long spinAnimUntil = 0;
    public static final long SPIN_ANIM_MS = 1000;

    public boolean isSpinning(long nowMs) {
        return nowMs < spinAnimUntil;
    }

    /**
     * Pays the option's cost in whole LEVELS, spins the wheel for a Tier, then builds a random
     * combo of (type, amplifier) pairs from the current category's 3-type pool whose amplifiers
     * sum to that Tier -- e.g. Tier 3 might be one type at III, or all three types at I each, or
     * one at I plus one at II. The combo REPLACES this category's enchants outright (no stacking
     * with whatever was equipped before). Starts the 1-second spin animation on success.
     */
    public boolean trySpin(EnchantSpinOption option, long nowMs) {
        if (isSpinning(nowMs)) return false; // can't spin again mid-animation
        if (!trySpendLevel(option.levelCost)) return false;
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
        spinAnimUntil = nowMs + SPIN_ANIM_MS;

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

    public void addExp(int amount) {
        exp += amount;
        while (exp >= EXP_CAP) {
            exp -= EXP_CAP;
            level++;
        }
    }

    // Upgrade shop: 9 perks, each levels independently up to UpgradeType.MAX_LEVEL,
    // 1 yellow orb per level. 3 random options offered at a time, rerollable for free.
    // All of this resets to empty/0 on a new game since Player is simply recreated.
    public final Map<UpgradeType, Integer> upgradeLevels = new EnumMap<>(UpgradeType.class);
    public final List<UpgradeType> offeredPerks = new ArrayList<>();

    public int upgradeLevel(UpgradeType type) {
        return upgradeLevels.getOrDefault(type, 0);
    }

    public void rerollPerks() {
        List<UpgradeType> pool = new ArrayList<>(List.of(UpgradeType.values()));
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
        return true;
    }

    // "Armored... +1 Defense per boss defeated (stackable)" -- both players get credit for a
    // boss kill (matching the shared yellow-orb rule), uncapped, on top of Armored's normal levels.
    public int bossDefenseStacks = 0;

    // "Reforged... +1 damage per wave completed, per level (stackable)" -- a flat, ever-growing
    // damage bonus applied to every attack, replacing Dodge's old evasion role.
    public int reforgedDamageBonus = 0;

    // HP Boost folds straight into Max HP now, instead of a hidden absorb-shield that never
    // showed up on the bar. baseMaxHealth is the "real" max (class base + permanent Grow gains,
    // never includes HP Boost); maxHealth is always baseMaxHealth + the current HP Boost bonus,
    // recomputed from scratch whenever either one changes -- see recomputeMaxHealth().
    private int baseMaxHealth;
    private int hpBoostBonus = 0;

    private static final int MELEE_DAMAGE_CAP = 80; // "until it caps at 80"

    /** Base weapon damage (tier-scaled) + Tank's flat Innate Prowess + Sword Damage enchant +
     *  Reforged, capped at 80. */
    public int swordDamage() {
        int base = effectiveDamage();
        int bonus = 10 * upgradeLevel(UpgradeType.INNATE_PROWESS)
                + (int) Math.round(enchantValue(EnchantType.SWORD_DAMAGE))
                + reforgedDamageBonus;
        return Math.min(MELEE_DAMAGE_CAP, base + bonus);
    }

    /** Base weapon damage (tier-scaled) + Ranger's flat Innate Prowess (now +20/level) + Arrow
     *  Damage enchant + Reforged. Critical's distance bonus is applied separately at impact, in
     *  GamePanel, since it needs travel distance. */
    public int arrowDamage() {
        int base = effectiveDamage();
        int bonus = 20 * upgradeLevel(UpgradeType.INNATE_PROWESS)
                + (int) Math.round(enchantValue(EnchantType.ARROW_DAMAGE))
                + reforgedDamageBonus;
        return base + bonus;
    }

    /**
     * "Punitive stats so the enemies only tickle" -- the dagger is 33% of the Ranger's
     * *effective* bow damage (tier + Innate Prowess included), not a flat number, so it scales
     * down proportionally as the bow gets stronger instead of falling further behind it.
     * Also gets Reforged, capped at 80 same as the sword. (Subject to change -- flagged as a
     * placeholder ahead of a possible throwable-darts rework.)
     */
    public int daggerDamage() {
        int base = (int) Math.round(arrowDamage() * 0.33);
        return Math.min(MELEE_DAMAGE_CAP, base + reforgedDamageBonus);
    }

    /**
     * Total effective Defense: Armored's levels + boss-kill stacks + raw armor gear, all boosted
     * by the Protection enchant's percentage on top ("+10%/25%/50% of total defense... combined
     * with the current weapon & armor, and the upgrades they have").
     */
    public int totalDefense() {
        int base = 2 * upgradeLevel(UpgradeType.ARMORED) + bossDefenseStacks + armorDefense();
        double protectionPercent = enchantValue(EnchantType.PROTECTION) / 100.0; // summed across all 3 pieces
        return (int) Math.round(base * (1 + protectionPercent));
    }

    /**
     * Central damage entry point for players. Shield (if active) blocks everything outright;
     * otherwise Defense reduces the hit (floor of 1) and comes straight off real HP -- HP Boost
     * no longer needs special handling here since its bonus already lives inside maxHealth.
     */
    public boolean takeDamage(int rawAmount, double fromX, double fromY, double knockbackStrength) {
        if (!alive) return false;
        if (isShieldActive(System.currentTimeMillis())) {
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
        maxHealth = baseMaxHealth + hpBoostBonus;
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

    /** Base cooldown reduced by Swing Speed (sword) or Quick Charge (bow) enchants, floored so
     *  it can never hit 0/negative. Also drives the sword/bow "ready" color used when drawing. */
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
     */
    public boolean tryAttack(boolean attackJustPressed, long nowMs) {
        if (!attackJustPressed || !isCooldownReady(nowMs)) return false;
        lastAttackAt = nowMs;
        if (playerClass.attackType == AttackType.RANGED) {
            if (ammo > 0) {
                ammo--;
                usedDaggerLastAttack = false;
            } else {
                usedDaggerLastAttack = true; // out of arrows -> dagger swing instead
            }
        }
        return true;
    }

    /**
     * Same cooldown gate as tryAttack, but without the weapon-specific side
     * effects (ammo consumption / dagger flag) -- used when a non-weapon
     * hotbar slot (medic kit, shield, manual dagger) is selected instead.
     */
    public boolean tryUtilityAction(boolean actionJustPressed, long nowMs) {
        if (!actionJustPressed || !isCooldownReady(nowMs)) return false;
        lastAttackAt = nowMs;
        return true;
    }

    /** Explicit per-tier, per-class damage table now (Bow: 90/105/130/165/220, Sword:
     *  50/60/75/95/110) -- replaces the old shared multiplier since these don't scale evenly. */
    public int effectiveDamage() {
        return playerClass == PlayerClass.TANK ? weaponTier.swordDamage : weaponTier.bowDamage;
    }

    public void useMedicKit() {
        if (medicKits > 0 && alive && health < maxHealth) {
            medicKits--;
            health = Math.min(maxHealth, health + MEDIC_KIT_HEAL);
            lastHealFlashAt = System.currentTimeMillis();
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

    private static final int SWING_DURATION_MS = 220;
    private static final int BOW_RECOIL_MS = 150;

    /** Sword/bow (or dagger fallback) beside the character: green=ready, red=recharging, with a swing/recoil cue. */
    private void drawWeapon(Graphics2D g, int camX, int camY, long nowMs) {
        int cx = (int) centerX() - camX;
        int cy = (int) centerY() - camY;
        long sinceAttack = nowMs - lastAttackAt;
        double cooldownRatio = Math.min(1.0, sinceAttack / (double) effectiveAttackCooldownMs());
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
            } else {
                // sword (Tank) or dagger fallback (Ranger, out of arrows)
                boolean isDagger = playerClass.attackType == AttackType.RANGED;
                int bladeLen = isDagger ? 14 : 24;
                boolean swinging = sinceAttack < SWING_DURATION_MS;
                double swingT = swinging ? sinceAttack / (double) SWING_DURATION_MS : 1.0;
                double angleDeg = swinging ? Math.sin(swingT * Math.PI) * 55 * facingSide : 0;
                wg.translate(baseX, cy);
                wg.rotate(Math.toRadians(angleDeg));
                wg.setColor(new Color(90, 70, 50)); // hilt
                wg.fillRect(facingSide > 0 ? -3 : 0, -4, 3, 8);
                wg.setColor(weaponColor); // blade
                wg.fillRect(facingSide > 0 ? 0 : -bladeLen, -3, bladeLen, 6);
            }
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
