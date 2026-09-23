package laststand.core;

import laststand.entity.*;
import laststand.fx.DamagePopup;
import laststand.shop.ArmorTier;
import laststand.shop.Currency;
import laststand.shop.EnchantCategory;
import laststand.shop.EnchantSpinOption;
import laststand.shop.EnchantUI;
import laststand.shop.ShopItem;
import laststand.shop.ShopUI;
import laststand.shop.UpgradeType;
import laststand.shop.Wallet;
import laststand.shop.WeaponTier;
import laststand.ui.HUD;
import laststand.wave.WaveManager;
import laststand.world.Arena;
import laststand.world.ArenaTheme;

import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class GamePanel extends JPanel {

    private final InputHandler input = new InputHandler();
    private GameState state = GameState.MAIN_MENU;
    private final HUD hud = new HUD();
    private final ShopUI shopUI = new ShopUI();
    private final EnchantUI enchantUI = new EnchantUI();

    // Per-player wallets: "Player 1 and player 2 has their own respective orbs"
    private final Wallet walletP1 = new Wallet();
    private final Wallet walletP2 = new Wallet();
    private final Random rng = new Random();
    private boolean shopOpen = false;
    private int enchantingPlayerNumber = 0; // 0 = nobody; only one player can enchant at a time
    private int p1ShopIndex = 0, p2ShopIndex = 0;
    private int lastWavesCompletedSeen = 0;
    private Rectangle gameOverButtonBounds; // recomputed each render; clicked in mousePressed

    // Main menu
    private final String[] menuItems = {"Play", "Player 2: OFF", "Options", "Exit"};
    private int menuIndex = 0;
    private boolean p2Enabled = false;
    private ArenaTheme theme = ArenaTheme.FOREST;
    private boolean pauseDuringMenus = false; // "Enable pauses during shop and enchanting"

    // Character select
    private PlayerClass p1Choice = PlayerClass.TANK;
    private PlayerClass p2Choice = PlayerClass.TANK;
    private boolean p1Ready = false;
    private boolean p2Ready = false;

    // Playing state
    private Arena arena;
    private WaveManager waveManager;
    private final List<Player> players = new ArrayList<>();
    private final List<Projectile> projectiles = new ArrayList<>();
    private final List<DamagePopup> damagePopups = new ArrayList<>();
    private int camX, camY;
    private final int viewportH = Constants.SCREEN_HEIGHT - Constants.HUD_HEIGHT - Constants.BUFF_BAR_HEIGHT;

    public GamePanel() {
        setPreferredSize(new java.awt.Dimension(Constants.SCREEN_WIDTH, Constants.SCREEN_HEIGHT));
        setBackground(Color.BLACK);
        setFocusable(true);
        addKeyListener(input);
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow(); // clicking the panel should also restore keyboard focus
                if (state == GameState.GAME_OVER && gameOverButtonBounds != null
                        && gameOverButtonBounds.contains(e.getX(), e.getY())) {
                    returnToMenuFromGameOver();
                }
            }
        });

        Timer timer = new Timer(1000 / Constants.FPS, e -> {
            tick();
            repaint();
        });
        timer.start();
    }

    private Player playerByNumber(int number) {
        for (Player p : players) if (p.playerNumber == number) return p;
        return null;
    }

    // ---------------------------------------------------------------- tick

    private void tick() {
        long now = System.currentTimeMillis();
        switch (state) {
            case MAIN_MENU -> tickMainMenu();
            case OPTIONS -> tickOptions();
            case CHARACTER_SELECT -> tickCharacterSelect();
            case PLAYING -> tickPlaying(now);
            case GAME_OVER -> tickGameOver();
        }
    }

    private void tickMainMenu() {
        if (input.wasJustPressed(KeyEvent.VK_W) || input.wasJustPressed(KeyEvent.VK_UP)) {
            menuIndex = (menuIndex + menuItems.length - 1) % menuItems.length;
            input.consume(KeyEvent.VK_W);
            input.consume(KeyEvent.VK_UP);
        }
        if (input.wasJustPressed(KeyEvent.VK_S) || input.wasJustPressed(KeyEvent.VK_DOWN)) {
            menuIndex = (menuIndex + 1) % menuItems.length;
            input.consume(KeyEvent.VK_S);
            input.consume(KeyEvent.VK_DOWN);
        }
        if (input.wasJustPressed(KeyEvent.VK_SPACE) || input.wasJustPressed(KeyEvent.VK_ENTER)) {
            input.consume(KeyEvent.VK_SPACE);
            input.consume(KeyEvent.VK_ENTER);
            switch (menuIndex) {
                case 0 -> { state = GameState.CHARACTER_SELECT; p1Ready = false; p2Ready = false; }
                case 1 -> { p2Enabled = !p2Enabled; menuItems[1] = "Player 2: " + (p2Enabled ? "ON" : "OFF"); }
                case 2 -> state = GameState.OPTIONS;
                case 3 -> System.exit(0);
            }
        }
    }

    private void tickOptions() {
        if (input.wasJustPressed(KeyEvent.VK_T)) {
            theme = (theme == ArenaTheme.FOREST) ? ArenaTheme.DESERT : ArenaTheme.FOREST;
            input.consume(KeyEvent.VK_T);
        }
        if (input.wasJustPressed(KeyEvent.VK_P)) {
            pauseDuringMenus = !pauseDuringMenus;
            input.consume(KeyEvent.VK_P);
        }
        if (input.wasJustPressed(KeyEvent.VK_ESCAPE)) {
            input.consume(KeyEvent.VK_ESCAPE);
            state = GameState.MAIN_MENU;
        }
    }

    private void tickCharacterSelect() {
        if (!p1Ready) {
            if (input.wasJustPressed(KeyEvent.VK_A) || input.wasJustPressed(KeyEvent.VK_D)) {
                p1Choice = (p1Choice == PlayerClass.TANK) ? PlayerClass.RANGER : PlayerClass.TANK;
                input.consume(KeyEvent.VK_A);
                input.consume(KeyEvent.VK_D);
            }
            if (input.wasJustPressed(KeyEvent.VK_SPACE)) {
                p1Ready = true;
                input.consume(KeyEvent.VK_SPACE);
            }
        }
        if (p2Enabled && !p2Ready) {
            if (input.wasJustPressed(KeyEvent.VK_LEFT) || input.wasJustPressed(KeyEvent.VK_RIGHT)) {
                p2Choice = (p2Choice == PlayerClass.TANK) ? PlayerClass.RANGER : PlayerClass.TANK;
                input.consume(KeyEvent.VK_LEFT);
                input.consume(KeyEvent.VK_RIGHT);
            }
            if (input.wasJustPressed(KeyEvent.VK_ENTER)) {
                p2Ready = true;
                input.consume(KeyEvent.VK_ENTER);
            }
        }
        if (input.wasJustPressed(KeyEvent.VK_ESCAPE)) {
            input.consume(KeyEvent.VK_ESCAPE);
            state = GameState.MAIN_MENU;
        }
        if (p1Ready && (!p2Enabled || p2Ready)) {
            startGame();
        }
    }

    private void startGame() {
        arena = new Arena(theme);
        waveManager = new WaveManager(arena);
        players.clear();
        projectiles.clear();

        double cx = arena.centerX();
        double cy = arena.centerY();
        // Spawn clear of the Enchanting Center's interact radius (it's passable now, but
        // spawning right on top of the boundary would be an awkward first frame)
        Player p1 = new Player(1, p1Choice, cx - 100, cy + 140);
        players.add(p1);
        if (p2Enabled) {
            Player p2 = new Player(2, p2Choice, cx + 40, cy + 140);
            players.add(p2);
        }
        walletP1.reset();
        walletP2.reset();
        shopOpen = false;
        p1ShopIndex = 0;
        p2ShopIndex = 0;
        lastWavesCompletedSeen = 0;
        state = GameState.PLAYING;
    }

    /**
     * "Real change": E is now one context-sensitive button. Near the Enchanting Center (inside
     * the dashed ring) it opens/closes enchanting for whichever player pressed it; everywhere
     * else it's the regular shop, same as before.
     */
    private void handleInteractKey() {
        if (shopOpen) {
            shopOpen = false;
            return;
        }
        if (enchantingPlayerNumber != 0) {
            Player active = playerByNumber(enchantingPlayerNumber);
            if (active != null) active.enchanting = false;
            enchantingPlayerNumber = 0;
            return;
        }
        for (Player p : players) {
            if (arena.isNearEnchantCenter(p.centerX(), p.centerY())) {
                enchantingPlayerNumber = p.playerNumber;
                p.enchanting = true;
                // "Make the weapon set as default when entering the enchantment area"
                p.setEnchantCategory(p.playerClass == PlayerClass.TANK ? EnchantCategory.SWORD : EnchantCategory.BOW);
                return;
            }
        }
        shopOpen = true;
    }

    private void tickPlaying(long now) {
        if (input.wasJustPressed(KeyEvent.VK_ESCAPE)) {
            input.consume(KeyEvent.VK_ESCAPE);
            state = GameState.MAIN_MENU;
            return;
        }

        if (input.wasJustPressed(KeyEvent.VK_E)) {
            input.consume(KeyEvent.VK_E);
            handleInteractKey();
        }

        if (shopOpen) {
            tickShop();
            return; // pause the simulation while shopping
        }

        if (enchantingPlayerNumber != 0) {
            tickEnchanting(now);
            if (pauseDuringMenus) return; // "Enable pauses..." -- freezes everyone and the waves too
        }

        // inventory hotbar selection (does not use the game's pause -- switching slots is instant).
        // Skipped for whoever is currently enchanting -- those number keys become socket-selection instead.
        Player p1 = playerByNumber(1);
        if (p1 != null && enchantingPlayerNumber != 1) {
            int slot = input.p1SlotJustPressed();
            if (slot >= 0) p1.selectedSlot = slot;
        }
        Player p2 = playerByNumber(2);
        if (p2 != null && enchantingPlayerNumber != 2) {
            int slot = input.p2SlotJustPressed();
            if (slot >= 0) p2.selectedSlot = slot;
        }

        for (Player p : players) {
            if (enchantingPlayerNumber == p.playerNumber) continue; // frozen; handled by tickEnchanting()

            p.update(input, arena);
            boolean justPressed = (p.playerNumber == 1) ? input.p1AttackJustPressed() : input.p2AttackJustPressed();

            if (p.selectedSlot == 0) {
                if (p.tryAttack(justPressed, now)) {
                    performPlayerAttack(p);
                }
            } else {
                if (p.tryUtilityAction(justPressed, now)) {
                    useSelectedItem(p, now);
                    p.selectedSlot = 0; // back to the weapon slot after using an item
                }
            }
        }

        // Snapshot the enchanting player's HP before enemies act, so a hit landing on them this
        // frame (only possible when "Pause during shop/enchanting" is off) kicks them out of the
        // menu immediately, per "promptly kick out the player when they get hit".
        Player enchantingPlayerSnapshot = enchantingPlayerNumber != 0 ? playerByNumber(enchantingPlayerNumber) : null;
        int healthBeforeEnemyPhase = enchantingPlayerSnapshot != null ? enchantingPlayerSnapshot.health : 0;

        waveManager.update(now);
        awardWaveCompletionOrbs();

        for (Enemy enemy : waveManager.getEnemies()) {
            Projectile fired = enemy.update(players, arena, now);
            if (fired != null) projectiles.add(fired);
        }

        updateProjectiles();
        damagePopups.removeIf(popup -> popup.isExpired(now));

        if (enchantingPlayerSnapshot != null && enchantingPlayerSnapshot.health < healthBeforeEnemyPhase) {
            enchantingPlayerSnapshot.enchanting = false;
            enchantingPlayerNumber = 0;
        }

        boolean anyoneAlive = players.stream().anyMatch(p -> p.alive);
        if (!anyoneAlive) {
            waveManager.resetToWaveOne();
            walletP1.reset(); // "reset all resources back to zero each time the game is over"
            walletP2.reset();
            lastWavesCompletedSeen = waveManager.wavesCompleted; // resync so no stale orbs get awarded later
            state = GameState.GAME_OVER;
            requestFocusInWindow(); // make sure SPACE/click both work immediately on this screen
        }

        updateCamera();
    }

    // ---------------------------------------------------------------- shop

    private void tickShop() {
        Player p1 = playerByNumber(1);
        Player p2 = playerByNumber(2);
        List<ShopItem> p1Items = p1 != null ? buildShopItems(p1, walletP1) : List.of();
        List<ShopItem> p2Items = (p2Enabled && p2 != null) ? buildShopItems(p2, walletP2) : List.of();

        if (!p1Items.isEmpty()) {
            if (input.wasJustPressed(KeyEvent.VK_W)) {
                input.consume(KeyEvent.VK_W);
                p1ShopIndex = Math.max(0, p1ShopIndex - 1);
            }
            if (input.wasJustPressed(KeyEvent.VK_S)) {
                input.consume(KeyEvent.VK_S);
                p1ShopIndex = Math.min(p1Items.size() - 1, p1ShopIndex + 1);
            }
            if (input.p1AttackJustPressed() && p1ShopIndex < p1Items.size()) {
                p1Items.get(p1ShopIndex).activate();
            }
        }

        if (p2Enabled && !p2Items.isEmpty()) {
            if (input.wasJustPressed(KeyEvent.VK_UP)) {
                input.consume(KeyEvent.VK_UP);
                p2ShopIndex = Math.max(0, p2ShopIndex - 1);
            }
            if (input.wasJustPressed(KeyEvent.VK_DOWN)) {
                input.consume(KeyEvent.VK_DOWN);
                p2ShopIndex = Math.min(p2Items.size() - 1, p2ShopIndex + 1);
            }
            if (input.p2AttackJustPressed() && p2ShopIndex < p2Items.size()) {
                p2Items.get(p2ShopIndex).activate();
            }
        }

        // Upgrade row: same hotbar-style keys as the inventory (1-4 / 0,9,8,7), but a direct
        // one-press buy/reroll here rather than select-then-use, since the shop is already paused.
        if (p1 != null) handlePerkKey(p1, walletP1, input.p1SlotJustPressed());
        if (p2Enabled && p2 != null) handlePerkKey(p2, walletP2, input.p2SlotJustPressed());

        // Z/X/C and comma/period/slash are intentionally NOT bound here -- reserved for the
        // upcoming enchanting system. Armor purchase lives in the regular W/S+SPACE shop list now.
    }

    private void handlePerkKey(Player p, Wallet w, int slot) {
        if (slot < 0) return;
        if (slot == 3) {
            // "Each reroll reduces 20 LVLs" -- e.g. LVL 25 spent becomes LVL 5
            if (p.level >= Player.REROLL_LEVEL_REQUIREMENT) {
                p.level -= Player.REROLL_LEVEL_REQUIREMENT;
                p.rerollPerks();
            }
        } else if (slot < p.offeredPerks.size()) {
            p.tryBuyPerk(p.offeredPerks.get(slot), w);
        }
    }

    // ---------------------------------------------------------------- enchanting

    /**
     * Handles input for whichever single player is currently enchanting. Movement/attack for
     * that player are skipped entirely in the main loop while this is active -- "the character
     * cannot move while enchanting". V/M/Z/X/C (or ,/./) and 1-5 are universal here since only
     * one player can be in this menu at a time.
     */
    private void tickEnchanting(long now) {
        Player p = playerByNumber(enchantingPlayerNumber);
        if (p == null) { enchantingPlayerNumber = 0; return; }

        // Exit is now handled by E (handleInteractKey()) for a single consistent toggle button.

        if (input.wasJustPressed(KeyEvent.VK_V)) { input.consume(KeyEvent.VK_V); p.setEnchantCategory(EnchantCategory.SWORD); }
        if (input.wasJustPressed(KeyEvent.VK_M)) { input.consume(KeyEvent.VK_M); p.setEnchantCategory(EnchantCategory.BOW); }

        // Z/X/C and ,/./  are kept strictly separate per player -- P1 only responds to Z/X/C,
        // P2 only to ,/./, matching the P1/P2 hotbar key convention used everywhere else.
        if (p.playerNumber == 1) {
            if (input.wasJustPressed(KeyEvent.VK_Z)) { input.consume(KeyEvent.VK_Z); p.setEnchantCategory(EnchantCategory.HELMET); }
            if (input.wasJustPressed(KeyEvent.VK_X)) { input.consume(KeyEvent.VK_X); p.setEnchantCategory(EnchantCategory.CHESTPLATE); }
            if (input.wasJustPressed(KeyEvent.VK_C)) { input.consume(KeyEvent.VK_C); p.setEnchantCategory(EnchantCategory.LEGGINGS); }
        } else {
            if (input.wasJustPressed(KeyEvent.VK_COMMA)) { input.consume(KeyEvent.VK_COMMA); p.setEnchantCategory(EnchantCategory.HELMET); }
            if (input.wasJustPressed(KeyEvent.VK_PERIOD)) { input.consume(KeyEvent.VK_PERIOD); p.setEnchantCategory(EnchantCategory.CHESTPLATE); }
            if (input.wasJustPressed(KeyEvent.VK_SLASH)) { input.consume(KeyEvent.VK_SLASH); p.setEnchantCategory(EnchantCategory.LEGGINGS); }
        }

        // "SPIN button" -- 1 is the default 1-level spin, 2-5 are the pricier/better-odds spins
        EnchantSpinOption[] spinKeys = {
                EnchantSpinOption.DEFAULT, EnchantSpinOption.SPIN_30,
                EnchantSpinOption.SPIN_90, EnchantSpinOption.SPIN_270, EnchantSpinOption.SPIN_450
        };
        int[] keys = {KeyEvent.VK_1, KeyEvent.VK_2, KeyEvent.VK_3, KeyEvent.VK_4, KeyEvent.VK_5};
        for (int i = 0; i < keys.length; i++) {
            if (input.wasJustPressed(keys[i])) {
                input.consume(keys[i]);
                p.trySpin(spinKeys[i], now);
            }
        }
    }

    /** Same catalog/prices for both classes; only the weapon word and arrow row change. */
    private List<ShopItem> buildShopItems(Player p, Wallet w) {
        List<ShopItem> items = new ArrayList<>();
        String weaponWord = p.playerClass == PlayerClass.TANK ? "Sword" : "Bow";

        WeaponTier next = p.weaponTier.next();
        if (next != null) {
            // "+5 dark orb in all weapon purchases for the tank for rebalancing" -- Tank-only,
            // and only on the dark-orb tiers (Diamond is priced in silver, untouched).
            boolean tankSurcharge = p.playerClass == PlayerClass.TANK && next.currency == Currency.DARK;
            final int price = tankSurcharge ? next.cost + 5 : next.cost;
            String cost = price + " " + (next.currency == Currency.DARK ? "Dark Orbs" : "Silver Orbs");
            int nextDamage = p.playerClass == PlayerClass.TANK ? next.swordDamage : next.bowDamage;
            String dmgLabel = "-> " + nextDamage + " dmg";
            items.add(new ShopItem("Upgrade to " + next.displayName + " " + weaponWord + " (" + dmgLabel + ")", cost, true,
                    () -> { if (w.trySpend(next.currency, price)) p.weaponTier = next; }));
        } else {
            items.add(new ShopItem("Max tier owned: " + p.weaponTier.displayName + " " + weaponWord, "", false, null));
        }
        items.add(new ShopItem("Enchanting is at the Enchanting Center, not here", "", false, null));

        if (p.playerClass == PlayerClass.RANGER) {
            items.add(new ShopItem("Buy 30 Arrows", "1 Dark Orb", true,
                    () -> { if (w.trySpend(Currency.DARK, 1)) p.ammo += 30; }));
        }

        items.add(new ShopItem("Medic Kit (+" + Player.MEDIC_KIT_HEAL + " HP) x" + p.medicKits, "1 Silver Orb", true,
                () -> { if (w.trySpend(Currency.SILVER, 1)) p.medicKits++; }));
        items.add(new ShopItem(p.shieldUnlocked ? "Shield (unlocked, 20s cooldown)" : "Unlock Shield",
                p.shieldUnlocked ? "" : Player.SHIELD_COST_SILVER + " Silver Orbs", !p.shieldUnlocked,
                () -> p.tryBuyShield(w)));
        items.add(new ShopItem("Potion of Experience -- COMING SOON", "", false, null));
        items.add(armorShopItem(p, w));

        items.add(new ShopItem("Trade 100 Dark -> 1 Yellow", "", true, w::tryTradeDarkForYellow));
        items.add(new ShopItem("Trade 20 Silver -> 1 Yellow", "", true, w::tryTradeSilverForYellow));
        return items;
    }

    /**
     * "Upgrade Armor [type] - [cost]" -- one purchase upgrades the whole set (Helmet + Chest +
     * Legs) at once, rather than the frustrating per-piece purchases from the previous pass.
     */
    private ShopItem armorShopItem(Player p, Wallet w) {
        ArmorTier next = p.armorTier.next();
        if (next == null) {
            return new ShopItem("Armor maxed: " + p.armorTier.name(), "", false, null);
        }
        if (next == ArmorTier.DIAMOND && p.playerClass != PlayerClass.TANK) {
            return new ShopItem("Armor maxed for Ranger: " + p.armorTier.name() + " (Diamond is Tank-only)", "", false, null);
        }
        int defenseGain = (next.defensePerPiece - p.armorTier.defensePerPiece) * 3;
        String cost = next.cost + " " + (next.currency == Currency.DARK ? "Dark Orbs" : "Silver Orbs");
        return new ShopItem("Upgrade Armor: " + next.name() + " (+" + defenseGain + " DEF, all pieces)", cost, true,
                () -> p.tryUpgradeArmor(w));
    }

    // ------------------------------------------------------------- economy

    private void awardWaveCompletionOrbs() {
        int delta = waveManager.wavesCompleted - lastWavesCompletedSeen;
        if (delta <= 0) return;
        for (int i = 0; i < delta; i++) {
            // "winning the 10 coal is won for both players" -- flat base, both wallets get the full +10
            walletP1.addDarkOrbs(Wallet.DARK_ORBS_PER_WAVE);
            if (p2Enabled) walletP2.addDarkOrbs(Wallet.DARK_ORBS_PER_WAVE);
            // Dark Sorcery / Silver Bank / Grow / Reforged are all personal, per-wave-cleared upgrades
            for (Player p : players) {
                Wallet w = p.playerNumber == 1 ? walletP1 : walletP2;
                int dsLevel = p.upgradeLevel(UpgradeType.DARK_SORCERY);
                if (dsLevel > 0) w.addDarkOrbs(dsLevel + rng.nextInt(2 * dsLevel + 1));   // range [lvl, 3*lvl]
                int sbLevel = p.upgradeLevel(UpgradeType.SILVER_BANK);
                if (sbLevel > 0) w.addSilverOrbs(sbLevel + rng.nextInt(sbLevel + 1));      // range [lvl, 2*lvl]

                // Grow: "+2 max HP per wave completed, per level (stackable)" -- heals the gain too.
                // Routed through grantPermanentMaxHealth() so it adds to the "real" base rather
                // than the HP-Boost-inclusive total (keeps HP Boost's % computed off the right number).
                int growLevel = p.upgradeLevel(UpgradeType.GROW);
                if (growLevel > 0) {
                    p.grantPermanentMaxHealth(2 * growLevel);
                }
                // Reforged: "+1 damage per wave completed, per level (stackable)"
                int reforgedLevel = p.upgradeLevel(UpgradeType.REFORGED);
                if (reforgedLevel > 0) p.reforgedDamageBonus += reforgedLevel;
            }
        }
        lastWavesCompletedSeen = waveManager.wavesCompleted;
    }

    /** Slot 0 (weapon) is handled by performPlayerAttack; this covers slots 1-3. */
    private void useSelectedItem(Player p, long now) {
        switch (p.selectedSlot) {
            case 1 -> p.useMedicKit();
            case 2 -> p.activateShield(now);
            case 3 -> { // Ranger's manual dagger -- melee on demand even with arrows left
                if (p.playerClass == PlayerClass.RANGER) meleeAttack(p, p.daggerDamage(), Player.DAGGER_RANGE, false);
            }
        }
    }

    /**
     * "Both players should get yellow orb if either of them has the last kill of the boss" --
     * so the boss reward goes to both wallets regardless of who landed it. Silver orbs and EXP
     * stay individual, credited only to whoever actually got the kill.
     */
    private void onEnemyKilled(Enemy enemy, Player killer) {
        if (enemy.boss) {
            walletP1.addYellowOrbs(1);
            if (p2Enabled) walletP2.addYellowOrbs(1);
            // "Armored... +1 for each boss defeated (stackable)" -- both players get credit,
            // same as the yellow orb rule, uncapped and separate from Armored's purchased levels.
            for (Player p : players) p.bossDefenseStacks++;
        }
        if (killer == null) return;
        Wallet w = killer.playerNumber == 1 ? walletP1 : walletP2;
        if (rng.nextDouble() < Wallet.SILVER_ORB_DROP_CHANCE) w.addSilverOrbs(1);
        // 30 EXP per kill at wave 1, +5 for every wave progressed since -- resets to 30 whenever
        // the game (and wave counter) resets, since this is computed off the live wave number.
        killer.addExp(30 + 5 * (waveManager.currentWave - 1));

        // Recovery: on-kill sustain -- Tank heals HP, Ranger recovers arrows, both per level.
        int recoveryLevel = killer.upgradeLevel(UpgradeType.RECOVERY);
        if (recoveryLevel > 0) {
            if (killer.playerClass == PlayerClass.TANK) {
                killer.health = Math.min(killer.maxHealth, killer.health + 2 * recoveryLevel);
            } else {
                killer.ammo += recoveryLevel;
            }
        }
    }

    // ------------------------------------------------------------- combat

    private void performPlayerAttack(Player p) {
        if (p.playerClass == PlayerClass.TANK) {
            meleeAttack(p, p.swordDamage(), p.playerClass.range, true);
        } else { // RANGER: bow shot, or dagger swing if it just ran out of arrows
            if (p.usedDaggerLastAttack) {
                meleeAttack(p, p.daggerDamage(), Player.DAGGER_RANGE, false);
            } else {
                double speedMult = 1 + p.arrowSpeedBonusPercent() / 100.0; // Arrow Speed enchant
                Projectile proj = new Projectile(
                        p.centerX(), p.centerY(),
                        p.facingX * 9.0 * speedMult, p.facingY * 9.0 * speedMult,
                        p.arrowDamage(), 0, true);
                proj.ownerPlayerNumber = p.playerNumber;
                proj.originX = p.centerX();
                proj.originY = p.centerY();
                projectiles.add(proj);
            }
        }
    }

    /** Unified Critical: flat 3%/level chance to double the hit, same mechanic for both classes now
     *  (the Ranger's old distance-based bonus barely moved the needle, so it's gone). Lucky Strike
     *  (a sword-only enchant) stacks its own chance on top, but only for genuine sword swings. */
    private record CritResult(int damage, boolean crit) {}

    private CritResult applyCritical(Player p, int baseDamage, boolean isSwordSwing) {
        int critLevel = p.upgradeLevel(UpgradeType.CRITICAL);
        double chance = critLevel * 0.03;
        if (isSwordSwing) chance += p.luckyStrikeBonusPercent() / 100.0;
        if (chance > 0 && rng.nextDouble() < chance) {
            return new CritResult(baseDamage * 2, true);
        }
        return new CritResult(baseDamage, false);
    }

    /** "A number appears on top of the hit enemy... fades by 0.5s", Terraria-style. */
    private void spawnDamagePopup(double x, double y, int amount, boolean crit) {
        Color color = crit ? new Color(255, 210, 60) : Color.WHITE; // crit color is animated inside DamagePopup itself
        damagePopups.add(new DamagePopup(x, y - 12, String.valueOf(amount), color, System.currentTimeMillis(), crit));
    }

    private void meleeAttack(Player p, int damage, int range, boolean isSwordSwing) {
        for (Enemy enemy : waveManager.getEnemies()) {
            if (!enemy.alive) continue;
            double dist = Math.hypot(enemy.centerX() - p.centerX(), enemy.centerY() - p.centerY());
            if (dist <= range) {
                CritResult result = applyCritical(p, damage, isSwordSwing);
                enemy.damage(result.damage());
                enemy.applyKnockback(p.centerX(), p.centerY(), 14.0);
                spawnDamagePopup(enemy.centerX(), enemy.centerY(), result.damage(), result.crit());
                if (!enemy.alive) onEnemyKilled(enemy, p);
            }
        }
    }

    private void updateProjectiles() {
        List<Projectile> toRemove = new ArrayList<>();
        for (Projectile proj : projectiles) {
            proj.update();
            if (arena.isObstacleAtWorld(proj.x, proj.y) || proj.x < 0 || proj.y < 0
                    || proj.x > arena.worldWidth || proj.y > arena.worldHeight) {
                toRemove.add(proj);
                continue;
            }
            if (proj.fromPlayer) {
                for (Enemy enemy : waveManager.getEnemies()) {
                    if (!enemy.alive) continue;
                    if (proj.bounds().intersects(enemy.bounds())) {
                        Player owner = playerByNumber(proj.ownerPlayerNumber);
                        CritResult result = owner != null ? applyCritical(owner, proj.damage, false) : new CritResult(proj.damage, false);
                        double knockbackMult = owner != null ? 1 + owner.arrowSpeedBonusPercent() / 100.0 : 1.0;
                        enemy.damage(result.damage());
                        enemy.applyKnockback(proj.x, proj.y, 8.0 * knockbackMult); // Arrow Speed enchant boosts knockback too
                        spawnDamagePopup(enemy.centerX(), enemy.centerY(), result.damage(), result.crit());
                        if (!enemy.alive) onEnemyKilled(enemy, owner);
                        toRemove.add(proj);
                        break;
                    }
                }
            } else {
                for (Player p : players) {
                    if (!p.alive) continue;
                    boolean hit = proj.bounds().intersects(p.bounds());
                    boolean inAoe = proj.aoeRadius > 0
                            && Math.hypot(p.centerX() - proj.x, p.centerY() - proj.y) <= proj.aoeRadius;
                    if (hit || inAoe) {
                        p.takeDamage(proj.damage, proj.x, proj.y, 10.0); // Shield + Defense + Temp HP applied inside
                        toRemove.add(proj);
                        break;
                    }
                }
            }
        }
        projectiles.removeAll(toRemove);
    }

    private void updateCamera() {
        List<Player> alive = players.stream().filter(p -> p.alive).toList();
        double fx, fy;
        if (alive.isEmpty()) {
            fx = arena.centerX();
            fy = arena.centerY();
        } else {
            fx = alive.stream().mapToDouble(Player::centerX).average().orElse(arena.centerX());
            fy = alive.stream().mapToDouble(Player::centerY).average().orElse(arena.centerY());
        }
        int[] cam = arena.clampCamera(fx, fy, Constants.SCREEN_WIDTH, viewportH);
        camX = cam[0];
        camY = cam[1];
    }

    private void tickGameOver() {
        if (input.wasJustPressed(KeyEvent.VK_SPACE) || input.wasJustPressed(KeyEvent.VK_ENTER)) {
            input.consume(KeyEvent.VK_SPACE);
            input.consume(KeyEvent.VK_ENTER);
            returnToMenuFromGameOver();
        }
    }

    private void returnToMenuFromGameOver() {
        p1Ready = false;
        p2Ready = false;
        state = GameState.MAIN_MENU;
    }

    // -------------------------------------------------------------- render

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        switch (state) {
            case MAIN_MENU -> renderMainMenu(g);
            case OPTIONS -> renderOptions(g);
            case CHARACTER_SELECT -> renderCharacterSelect(g);
            case PLAYING -> renderPlaying(g);
            case GAME_OVER -> renderGameOver(g);
        }
    }

    private void renderMainMenu(Graphics2D g) {
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, getWidth(), getHeight());
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 48));
        g.drawString("LAST STAND", 300, 150);

        g.setFont(new Font("SansSerif", Font.PLAIN, 26));
        for (int i = 0; i < menuItems.length; i++) {
            g.setColor(i == menuIndex ? Color.YELLOW : Color.WHITE);
            String prefix = (i == menuIndex) ? "> " : "  ";
            g.drawString(prefix + menuItems[i], 380, 250 + i * 45);
        }
        g.setFont(new Font("SansSerif", Font.PLAIN, 14));
        g.setColor(Color.GRAY);
        g.drawString("W/S select, SPACE confirm", 380, 480);

        g.setFont(new Font("SansSerif", Font.PLAIN, 12));
        g.setColor(new Color(90, 90, 90));
        String version = "v" + Constants.VERSION;
        g.drawString(version, Constants.SCREEN_WIDTH - g.getFontMetrics().stringWidth(version) - 12,
                Constants.SCREEN_HEIGHT - 12);
    }

    private void renderOptions(Graphics2D g) {
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, getWidth(), getHeight());
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 32));
        g.drawString("OPTIONS", 380, 150);
        g.setFont(new Font("SansSerif", Font.PLAIN, 20));
        g.drawString("Arena theme: " + theme.displayName + "   (press T to toggle)", 300, 250);
        g.drawString("Pause game during shop/enchanting: " + (pauseDuringMenus ? "ON" : "OFF")
                + "   (press P to toggle)", 300, 290);
        g.setColor(Color.GRAY);
        g.drawString("ESC to go back", 300, 330);
    }

    private void renderCharacterSelect(Graphics2D g) {
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, getWidth(), getHeight());
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 32));
        g.drawString("CHARACTER SELECT", 260, 100);

        drawSelectPanel(g, 120, "Player 1", p1Choice, p1Ready, Player.colorFor(1));
        if (p2Enabled) {
            drawSelectPanel(g, 520, "Player 2", p2Choice, p2Ready, Player.colorFor(2));
        }

        g.setFont(new Font("SansSerif", Font.PLAIN, 14));
        g.setColor(Color.GRAY);
        g.drawString("P1: A/D to switch, SPACE to lock in", 120, 550);
        if (p2Enabled) g.drawString("P2: Left/Right to switch, ENTER to lock in", 520, 570);
    }

    private void drawSelectPanel(Graphics2D g, int px, String label, PlayerClass choice, boolean ready, Color color) {
        g.setFont(new Font("SansSerif", Font.BOLD, 22));
        g.setColor(color);
        g.drawString(label + (ready ? " - READY" : ""), px, 160);

        g.setColor(color);
        g.fillRect(px, 190, 60, 60);

        g.setFont(new Font("SansSerif", Font.PLAIN, 18));
        g.setColor(Color.WHITE);
        g.drawString("Class: " + choice.name(), px, 280);
        g.drawString("HP: " + choice.maxHealth, px, 305);
        g.drawString(choice.attackType == AttackType.MELEE ? "Melee (sword)" : "Ranged (bow)", px, 330);
    }

    private void renderPlaying(Graphics2D g) {
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, getWidth(), getHeight());

        long now = System.currentTimeMillis();
        Graphics2D world = (Graphics2D) g.create(0, Constants.HUD_HEIGHT, Constants.SCREEN_WIDTH, viewportH);
        arena.draw(world, camX, camY, Constants.SCREEN_WIDTH, viewportH);
        drawEnchantCenterLabel(world);
        for (Enemy enemy : waveManager.getEnemies()) enemy.draw(world, camX, camY);
        for (Player p : players) if (p.alive) p.draw(world, camX, camY, now);
        for (Projectile proj : projectiles) proj.draw(world, camX, camY);
        for (DamagePopup popup : damagePopups) popup.draw(world, camX, camY, now);
        world.dispose();

        hud.draw(g, players, waveManager, walletP1, walletP2);
        hud.drawBuffBar(g, players);

        if (shopOpen) {
            Player p1 = playerByNumber(1);
            Player p2 = playerByNumber(2);
            List<ShopItem> p1Items = p1 != null ? buildShopItems(p1, walletP1) : List.of();
            List<ShopItem> p2Items = (p2Enabled && p2 != null) ? buildShopItems(p2, walletP2) : List.of();
            shopUI.draw(g, p1, walletP1, p1Items, p1ShopIndex, p2, walletP2, p2Items, p2ShopIndex,
                    p2Enabled && p2 != null);
        }

        if (enchantingPlayerNumber != 0) {
            Player active = playerByNumber(enchantingPlayerNumber);
            if (active != null) enchantUI.draw(g, active, Constants.SCREEN_WIDTH, Constants.SCREEN_HEIGHT, now);
        }
    }

    /**
     * Label above the Enchanting Center: "Press E to enchant" once close enough, drawn in
     * world space so it scrolls with the camera. E is a single shared key now (see
     * handleInteractKey()), so this doesn't need to distinguish which player is near.
     */
    private void drawEnchantCenterLabel(Graphics2D world) {
        boolean anyoneNear = false;
        for (Player p : players) {
            if (arena.isNearEnchantCenter(p.centerX(), p.centerY())) { anyoneNear = true; break; }
        }

        String label;
        Color color;
        if (enchantingPlayerNumber != 0) {
            label = "Enchanting Center";
            color = new Color(150, 170, 230);
        } else if (anyoneNear) {
            label = "Press E to enchant";
            color = Color.YELLOW;
        } else {
            label = "Enchanting Center";
            color = new Color(150, 170, 230);
        }

        world.setFont(new Font("SansSerif", Font.BOLD, 13));
        int tw = world.getFontMetrics().stringWidth(label);
        int sx = (int) arena.centerX() - camX - tw / 2;
        int sy = (int) arena.centerY() - camY - (Constants.SENTRY_HALF_SIZE * Constants.TILE_SIZE) - 16;
        world.setColor(new Color(0, 0, 0, 180));
        world.fillRect(sx - 5, sy - 13, tw + 10, 17);
        world.setColor(color);
        world.drawString(label, sx, sy);
    }

    private void renderGameOver(Graphics2D g) {
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, getWidth(), getHeight());
        g.setColor(Color.RED);
        g.setFont(new Font("SansSerif", Font.BOLD, 48));
        g.drawString("GAME OVER", 350, 260);
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.PLAIN, 18));
        g.drawString("Wave reset to 1. All orbs reset to 0.", 340, 300);

        int btnW = 220, btnH = 46;
        int btnX = (Constants.SCREEN_WIDTH - btnW) / 2;
        int btnY = 340;
        gameOverButtonBounds = new Rectangle(btnX, btnY, btnW, btnH);

        g.setColor(new Color(60, 60, 60));
        g.fillRoundRect(btnX, btnY, btnW, btnH, 10, 10);
        g.setColor(Color.WHITE);
        g.drawRoundRect(btnX, btnY, btnW, btnH, 10, 10);
        g.setFont(new Font("SansSerif", Font.BOLD, 18));
        String label = "BACK TO MENU";
        int tw = g.getFontMetrics().stringWidth(label);
        g.drawString(label, btnX + (btnW - tw) / 2, btnY + 29);

        g.setFont(new Font("SansSerif", Font.PLAIN, 13));
        g.setColor(Color.GRAY);
        String hint = "Click the button, or press SPACE / ENTER";
        g.drawString(hint, (Constants.SCREEN_WIDTH - g.getFontMetrics().stringWidth(hint)) / 2, btnY + btnH + 30);
    }
}
