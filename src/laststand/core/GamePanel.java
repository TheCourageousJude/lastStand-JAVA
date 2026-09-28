package laststand.core;

import laststand.entity.*;
import laststand.fx.DamagePopup;
import laststand.save.SaveData;
import laststand.save.SaveManager;
import laststand.shop.ArmorTier;
import laststand.shop.Currency;
import laststand.shop.EnchantCategory;
import laststand.shop.EnchantSpinOption;
import laststand.shop.EnchantType;
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
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
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
    // ESC-triggered pause menu -- replaces the old "ESC = instant abandon match" behavior, which
    // was too easy to trigger by accident. Whether it actually freezes the simulation is governed
    // by pauseDuringMenus, same as shop/enchant (see tickPlaying()).
    private boolean pauseMenuOpen = false;
    private int pauseMenuIndex = 0; // 0 = How to Play, 1 = Save Game, 2 = Back to Main Menu
    // Feedback for "Save Game" -- a brief real-time message (same reasoning as the spin
    // animation's clock: a short self-contained UI flash has no business being frozen by its own
    // menu's pause). Not persisted; purely cosmetic.
    private long saveMessageUntil = 0;
    private boolean lastSaveSucceeded = true;
    private boolean lastSaveLocked = false; // last attempt was blocked by the post-load lockout
    // After LOADING a save, "Save Game" is locked out until this wave is reached (load wave + 5).
    // Otherwise a player could load, immediately re-save at essentially the same spot, and still
    // save-scum against that near-identical checkpoint. 0 = never locked (fresh game).
    private int saveLockedUntilWave = 0;
    private static final int SAVE_LOCK_WAVES = 5;

    private boolean isSaveLocked() {
        return waveManager != null && waveManager.currentWave < saveLockedUntilWave;
    }
    private boolean confirmingQuit = false; // "Back to Main Menu" sub-view -- Yes/No
    private boolean confirmQuitYes = false; // defaults to No every time it's opened -- safer default
    private boolean howToPlayOpen = false;
    private int howToPlaySection = 0; // 0 = Player Control, 1 = Shop, 2 = Enchanting
    private int howToPlayScroll = 0; // px, clamped against content height at render time
    private int p1ShopIndex = 0, p2ShopIndex = 0;
    private int lastWavesCompletedSeen = 0;
    private Rectangle gameOverButtonBounds; // recomputed each render; clicked in mousePressed

    // Main menu
    // "Load Save" asks for confirmation first (Yes/No, defaults to No): loading CONSUMES the save
    // (see loadGame()), so an accidental SPACE on the main menu must never burn it.
    private boolean confirmingLoad = false;
    private boolean confirmLoadYes = false;
    private String pendingLoadSummary = "";
    private String[] menuItems = {"Play", "Load Save", "Player 2: OFF", "Options", "Exit"};
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

    // "Cooldown of shield and weapon recharge still goes [during pause]" -- the bug was that all
    // timers (attack cooldown, shield cooldown/duration, wave spawn timing) were stamped and
    // checked against raw System.currentTimeMillis(), which keeps advancing in the real world even
    // while the sim is "frozen" for a paused menu. virtualNow is the single clock every game-timer
    // should use instead: it only advances when the sim isn't paused, so a cooldown genuinely
    // freezes (not just visually) for exactly as long as the menu was open.
    private long virtualNow = System.currentTimeMillis();
    private long lastRealTick = virtualNow;

    /** True while the sim is frozen because a menu is open and "pause during shop/enchant/settings"
     *  is ON -- mirrors the early-return conditions in tickPlaying() exactly. */
    private boolean isSimulationPaused() {
        if (state != GameState.PLAYING || !pauseDuringMenus) return false;
        return shopOpen || enchantingPlayerNumber != 0 || pauseMenuOpen;
    }

    private void tick() {
        long realNow = System.currentTimeMillis();
        long deltaMs = Math.max(0, realNow - lastRealTick);
        lastRealTick = realNow;
        if (!isSimulationPaused()) {
            virtualNow += deltaMs;
        }
        long now = virtualNow;
        try {
            switch (state) {
                case MAIN_MENU -> tickMainMenu();
                case OPTIONS -> tickOptions();
                case CHARACTER_SELECT -> tickCharacterSelect();
                case PLAYING -> tickPlaying(now);
                case GAME_OVER -> tickGameOver();
            }
        } catch (RuntimeException ex) {
            // Last-resort safety net: two known hit-resolution spots are already hardened
            // individually (see meleeAttack()/updateProjectiles()), but this catches anything
            // else too -- one bad frame gets logged to the console instead of the whole game
            // freezing or crashing. If this ever prints, the stack trace right here is exactly
            // what's needed to find and fix the real cause.
            System.err.println("Uncaught exception during tick() in state " + state + " -- game continues:");
            ex.printStackTrace();
        }
    }

    private void tickMainMenu() {
        if (confirmingLoad) {
            if (input.wasJustPressed(KeyEvent.VK_A) || input.wasJustPressed(KeyEvent.VK_LEFT)
                    || input.wasJustPressed(KeyEvent.VK_D) || input.wasJustPressed(KeyEvent.VK_RIGHT)) {
                confirmLoadYes = !confirmLoadYes;
                input.consume(KeyEvent.VK_A); input.consume(KeyEvent.VK_LEFT);
                input.consume(KeyEvent.VK_D); input.consume(KeyEvent.VK_RIGHT);
            }
            if (input.wasJustPressed(KeyEvent.VK_ESCAPE)) {
                input.consume(KeyEvent.VK_ESCAPE);
                confirmingLoad = false;
            }
            if (input.wasJustPressed(KeyEvent.VK_SPACE) || input.wasJustPressed(KeyEvent.VK_ENTER)) {
                input.consume(KeyEvent.VK_SPACE);
                input.consume(KeyEvent.VK_ENTER);
                boolean go = confirmLoadYes;
                confirmingLoad = false;
                if (go) loadGame();
            }
            return;
        }
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
                case 1 -> openLoadConfirmation(); // no-op if there's no save yet
                case 2 -> { p2Enabled = !p2Enabled; menuItems[2] = "Player 2: " + (p2Enabled ? "ON" : "OFF"); }
                case 3 -> state = GameState.OPTIONS;
                case 4 -> System.exit(0);
            }
        }
    }

    /** Opens the Yes/No prompt for "Load Save", showing what's actually in the save. Does nothing
     *  if there's no readable save. Peeking at the save here does NOT consume it. */
    private void openLoadConfirmation() {
        if (!SaveManager.saveExists()) return;
        SaveData peek = SaveManager.load();
        if (peek == null) return; // unreadable/incompatible -- nothing sensible to offer
        StringBuilder sb = new StringBuilder("Wave " + peek.currentWave + "  |  P1 " + peek.p1.playerClass
                + " LVL " + peek.p1.level);
        if (peek.p2Enabled && peek.p2 != null) {
            sb.append("  |  P2 ").append(peek.p2.playerClass).append(" LVL ").append(peek.p2.level);
        }
        pendingLoadSummary = sb.toString();
        confirmingLoad = true;
        confirmLoadYes = false; // always defaults to "No"
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
        waveManager = new WaveManager(arena, virtualNow);
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
        saveLockedUntilWave = 0; // a brand-new run never inherits a previous load's lockout
        state = GameState.PLAYING;
    }

    /**
     * Resumes from a save: same setup as startGame(), but restores each player's progress and
     * fast-forwards the wave manager to the saved wave, instead of starting fresh. Position, HP,
     * ammo, and in-flight enemies/projectiles are NOT restored -- loading always resumes at the
     * start of the saved wave, at full health, same as any normal wave transition (see SaveData's
     * class doc for why). Returns false (and leaves the current state untouched) if there's no
     * save or it couldn't be read.
     */
    private boolean loadGame() {
        SaveData data = SaveManager.load();
        if (data == null) return false;

        theme = data.theme;
        pauseDuringMenus = data.pauseDuringMenus;
        p2Enabled = data.p2Enabled && data.p2 != null;
        menuItems[2] = "Player 2: " + (p2Enabled ? "ON" : "OFF");

        arena = new Arena(theme);
        waveManager = new WaveManager(arena, virtualNow);
        waveManager.startWave(data.currentWave, virtualNow); // reconfigures pool/spawns for that wave
        waveManager.wavesCompleted = data.wavesCompleted; // startWave() above bumped this; override to the real value
        waveManager.bossKillCount = data.bossKillCount;
        lastWavesCompletedSeen = data.wavesCompleted; // must match, or the next frame awards a pile of "missed" orbs

        players.clear();
        projectiles.clear();
        double cx = arena.centerX();
        double cy = arena.centerY();

        Player p1 = new Player(1, data.p1.playerClass, cx - 100, cy + 140);
        applySavedProgress(p1, data.p1, walletP1);
        players.add(p1);

        walletP2.reset();
        if (p2Enabled) {
            Player p2 = new Player(2, data.p2.playerClass, cx + 40, cy + 140);
            applySavedProgress(p2, data.p2, walletP2);
            players.add(p2);
        }

        shopOpen = false;
        p1ShopIndex = 0;
        p2ShopIndex = 0;
        state = GameState.PLAYING;
        // Lock "Save Game" for the next 5 waves -- see saveLockedUntilWave's comment.
        saveLockedUntilWave = data.currentWave + SAVE_LOCK_WAVES;
        // One-time checkpoint: delete the save the moment it's successfully loaded, so a bad
        // enchant roll, a risky fight, or any other mistake can't be undone by just reloading the
        // same save over and over. If they want another checkpoint to fall back on, they need to
        // save again -- from whatever new position they're now committed to.
        SaveManager.delete();
        return true;
    }

    private void applySavedProgress(Player p, SaveData.PlayerSave saved, Wallet wallet) {
        p.level = saved.level;
        p.exp = saved.exp;
        p.weaponTier = saved.weaponTier;
        p.armorTier = saved.armorTier;
        p.medicKits = saved.medicKits;
        p.shieldUnlocked = saved.shieldUnlocked;
        p.upgradeLevels.putAll(saved.upgradeLevels);
        for (Map.Entry<EnchantCategory, Map<EnchantType, Integer>> entry : saved.enchants.entrySet()) {
            p.enchants.put(entry.getKey(), new EnumMap<>(entry.getValue()));
        }
        p.recomputeAllDerivedStats(); // HP Boost + Preserved Power weren't set incrementally, so re-derive now
        p.health = p.maxHealth; // fresh wave start, same as any other wave transition

        wallet.reset();
        wallet.addDarkOrbs(saved.darkOrbs);
        wallet.addSilverOrbs(saved.silverOrbs);
        wallet.addYellowOrbs(saved.yellowOrbs);
    }

    /** Snapshots the current run into a SaveData -- see that class's doc for exactly what is and
     *  isn't captured. */
    private SaveData buildSaveData() {
        SaveData data = new SaveData();
        data.p2Enabled = p2Enabled;
        data.theme = theme;
        data.pauseDuringMenus = pauseDuringMenus;
        data.currentWave = waveManager.currentWave;
        data.wavesCompleted = waveManager.wavesCompleted;
        data.bossKillCount = waveManager.bossKillCount;

        Player p1 = playerByNumber(1);
        data.p1 = toPlayerSave(p1, walletP1);
        Player p2 = playerByNumber(2);
        data.p2 = (p2 != null) ? toPlayerSave(p2, walletP2) : null;
        return data;
    }

    private SaveData.PlayerSave toPlayerSave(Player p, Wallet wallet) {
        SaveData.PlayerSave ps = new SaveData.PlayerSave();
        ps.playerClass = p.playerClass;
        ps.level = p.level;
        ps.exp = p.exp;
        ps.weaponTier = p.weaponTier;
        ps.armorTier = p.armorTier;
        ps.medicKits = p.medicKits;
        ps.shieldUnlocked = p.shieldUnlocked;
        ps.upgradeLevels.putAll(p.upgradeLevels);
        for (Map.Entry<EnchantCategory, Map<EnchantType, Integer>> entry : p.enchants.entrySet()) {
            ps.enchants.put(entry.getKey(), new EnumMap<>(entry.getValue()));
        }
        ps.darkOrbs = wallet.darkOrbs;
        ps.silverOrbs = wallet.silverOrbs;
        ps.yellowOrbs = wallet.yellowOrbs;
        return ps;
    }

    /**
     * "Real change": E/P is now one context-sensitive button per player -- E for P1, P for P2 (so
     * both players can interact independently on a shared keyboard). Near the Enchanting Center
     * (inside the dashed ring) it opens/closes enchanting for the player who pressed their own
     * key; everywhere else it's the regular shop, same as before.
     */
    private void handleInteractKey(int playerNumber) {
        Player presser = playerByNumber(playerNumber);
        if (presser == null) return; // e.g. P pressed with P2 disabled -- nothing to interact as

        if (shopOpen) {
            shopOpen = false;
            return;
        }
        if (enchantingPlayerNumber != 0) {
            // Only the player currently enchanting can exit with their own key -- otherwise P2's
            // key could accidentally kick P1 out of their menu (and vice versa).
            if (enchantingPlayerNumber != playerNumber) return;
            presser.enchanting = false;
            enchantingPlayerNumber = 0;
            return;
        }
        if (arena.isNearEnchantCenter(presser.centerX(), presser.centerY())) {
            enchantingPlayerNumber = presser.playerNumber;
            presser.enchanting = true;
            // "Make the weapon set as default when entering the enchantment area"
            presser.setEnchantCategory(presser.playerClass == PlayerClass.TANK ? EnchantCategory.SWORD : EnchantCategory.BOW);
            return;
        }
        shopOpen = true;
    }

    /**
     * ESC always "backs out one level": How to Play -> pause menu; the quit confirmation -> pause
     * menu; the pause menu itself -> closed (resume). Ignored entirely while shop/enchant is open
     * (E/P already own closing those) so menus never stack ambiguously.
     */
    private void handleEscapeKey() {
        if (howToPlayOpen) {
            howToPlayOpen = false;
            return;
        }
        if (confirmingQuit) {
            confirmingQuit = false; // cancel -- back to the 2-button pause menu, nothing abandoned
            return;
        }
        if (shopOpen || enchantingPlayerNumber != 0) return;
        pauseMenuOpen = !pauseMenuOpen;
        if (pauseMenuOpen) pauseMenuIndex = 0;
    }

    private static final int HOW_TO_PLAY_SCROLL_STEP = 24;

    /** Navigation for the pause menu and its two sub-views (confirmation, How to Play). */
    private void tickPauseMenu() {
        if (confirmingQuit) {
            if (input.wasJustPressed(KeyEvent.VK_A) || input.wasJustPressed(KeyEvent.VK_LEFT)
                    || input.wasJustPressed(KeyEvent.VK_D) || input.wasJustPressed(KeyEvent.VK_RIGHT)) {
                confirmQuitYes = !confirmQuitYes;
                input.consume(KeyEvent.VK_A); input.consume(KeyEvent.VK_LEFT);
                input.consume(KeyEvent.VK_D); input.consume(KeyEvent.VK_RIGHT);
            }
            if (input.wasJustPressed(KeyEvent.VK_SPACE) || input.wasJustPressed(KeyEvent.VK_ENTER)) {
                input.consume(KeyEvent.VK_SPACE);
                input.consume(KeyEvent.VK_ENTER);
                if (confirmQuitYes) {
                    pauseMenuOpen = false;
                    confirmingQuit = false;
                    state = GameState.MAIN_MENU; // the actual "abandon match" action -- now opt-in
                } else {
                    confirmingQuit = false; // back to the pause menu
                }
            }
            return;
        }

        if (howToPlayOpen) {
            tickHowToPlay();
            return;
        }

        // Top-level: 3 buttons (How to Play, Save Game, Back to Main Menu)
        if (input.wasJustPressed(KeyEvent.VK_W) || input.wasJustPressed(KeyEvent.VK_UP)) {
            pauseMenuIndex = (pauseMenuIndex + 2) % 3;
            input.consume(KeyEvent.VK_W); input.consume(KeyEvent.VK_UP);
        }
        if (input.wasJustPressed(KeyEvent.VK_S) || input.wasJustPressed(KeyEvent.VK_DOWN)) {
            pauseMenuIndex = (pauseMenuIndex + 1) % 3;
            input.consume(KeyEvent.VK_S); input.consume(KeyEvent.VK_DOWN);
        }
        if (input.wasJustPressed(KeyEvent.VK_SPACE) || input.wasJustPressed(KeyEvent.VK_ENTER)) {
            input.consume(KeyEvent.VK_SPACE);
            input.consume(KeyEvent.VK_ENTER);
            if (pauseMenuIndex == 0) {
                howToPlayOpen = true;
                howToPlaySection = 0;
                howToPlayScroll = 0;
            } else if (pauseMenuIndex == 1) {
                if (isSaveLocked()) {
                    lastSaveLocked = true; // greyed out -- just explain why, don't touch the disk
                } else {
                    lastSaveLocked = false;
                    lastSaveSucceeded = SaveManager.save(buildSaveData());
                }
                saveMessageUntil = System.currentTimeMillis() + 1500;
            } else {
                confirmingQuit = true;
                confirmQuitYes = false; // always defaults to "No"
            }
        }
    }

    /** A/D or Left/Right cycle the 3 sections (1/2/3 jump straight to one); W/S or Up/Down scroll. */
    private void tickHowToPlay() {
        if (input.wasJustPressed(KeyEvent.VK_1)) { howToPlaySection = 0; howToPlayScroll = 0; input.consume(KeyEvent.VK_1); }
        if (input.wasJustPressed(KeyEvent.VK_2)) { howToPlaySection = 1; howToPlayScroll = 0; input.consume(KeyEvent.VK_2); }
        if (input.wasJustPressed(KeyEvent.VK_3)) { howToPlaySection = 2; howToPlayScroll = 0; input.consume(KeyEvent.VK_3); }
        if (input.wasJustPressed(KeyEvent.VK_A) || input.wasJustPressed(KeyEvent.VK_LEFT)) {
            howToPlaySection = (howToPlaySection + 2) % 3;
            howToPlayScroll = 0;
            input.consume(KeyEvent.VK_A); input.consume(KeyEvent.VK_LEFT);
        }
        if (input.wasJustPressed(KeyEvent.VK_D) || input.wasJustPressed(KeyEvent.VK_RIGHT)) {
            howToPlaySection = (howToPlaySection + 1) % 3;
            howToPlayScroll = 0;
            input.consume(KeyEvent.VK_D); input.consume(KeyEvent.VK_RIGHT);
        }
        if (input.wasJustPressed(KeyEvent.VK_W) || input.wasJustPressed(KeyEvent.VK_UP)) {
            howToPlayScroll = Math.max(0, howToPlayScroll - HOW_TO_PLAY_SCROLL_STEP);
            input.consume(KeyEvent.VK_W); input.consume(KeyEvent.VK_UP);
        }
        if (input.wasJustPressed(KeyEvent.VK_S) || input.wasJustPressed(KeyEvent.VK_DOWN)) {
            howToPlayScroll += HOW_TO_PLAY_SCROLL_STEP; // clamped against content height at render time
            input.consume(KeyEvent.VK_S); input.consume(KeyEvent.VK_DOWN);
        }
    }

    /** Builds the line-by-line content for one How to Play section. Enchant descriptions are
     *  generated straight from EnchantType's own data (amounts + unit) rather than hand-duplicated
     *  text, so this can never drift out of sync with the actual numbers. */
    private List<String> howToPlayLines(int section) {
        List<String> lines = new ArrayList<>();
        switch (section) {
            case 0 -> { // Player Control
                lines.add("MOVEMENT & ATTACK");
                lines.add("P1: [W][A][S][D] move   [SPACE] attack (melee swing / fire arrow)");
                lines.add("P2: [Arrow Keys] move   [ENTER] attack");
                lines.add("");
                lines.add("HOTBAR / INVENTORY SLOTS");
                lines.add("P1: [1][2][3][4] select slot        P2: [0][9][8][7] select slot");
                lines.add("");
                lines.add("INTERACT (shop, or enchanting near the Enchanting Center)");
                lines.add("P1: [E]                              P2: [P]");
                lines.add("");
                lines.add("ENCHANT WHEEL SPINS (while enchanting)");
                lines.add("P1: [1][2][3][4][5]                  P2: [7][8][9][0][-]");
                lines.add("(left-to-right on each side of the number row = cheapest to priciest spin)");
                lines.add("");
                lines.add("MENUS");
                lines.add("[ESC] open/close this Settings menu   [W/S] or [Up/Down] navigate");
                lines.add("[SPACE] or [ENTER] confirm   [A/D] or [Left/Right] switch sections here");
            }
            case 1 -> { // Shop
                lines.add("Open the shop with your interact key (E for P1, P for P2) anywhere");
                lines.add("outside the Enchanting Center's ring. Each player shops independently");
                lines.add("with their own wallet -- P2's panel only appears if Player 2 is ON.");
                lines.add("");
                lines.add("CURRENCIES");
                lines.add("Dark orbs   -- earned per wave, spend on weapon tier upgrades");
                lines.add("Silver orbs -- earned per wave, spend on armor tier upgrades");
                lines.add("Yellow orbs -- earned per boss kill, spend on the 9 permanent perks");
                lines.add("");
                lines.add("WEAPON & ARMOR TIERS");
                lines.add("5 tiers each (Wood -> ... -> Diamond for weapons; similar for armor),");
                lines.add("bought one tier at a time with Dark/Silver orbs respectively.");
                lines.add("");
                lines.add("PERKS (9 total, 1 yellow orb per level, up to level " + UpgradeType.MAX_LEVEL + ")");
                for (UpgradeType type : UpgradeType.values()) {
                    lines.add(type.displayName() + ": " + type.effectFor(PlayerClass.TANK));
                }
            }
            case 2 -> { // Enchanting
                lines.add("Stand inside the dashed ring around the Enchanting Center and press");
                lines.add("your interact key (E for P1, P for P2) to open the enchant wheel.");
                lines.add("Switch which gear slot you're enchanting with [V] Sword / [M] Bow, or");
                lines.add("[Z][X][C] (P1) / [,][.][/](P2) for Helmet/Chest/Legs.");
                lines.add("");
                lines.add("Spin the wheel to roll a random Tier I-V enchant for that slot. Pricier");
                lines.add("spins (paid in LVL, earned from EXP) have better odds at higher tiers.");
                lines.add("The default 1-LVL spin is capped at " + Player.DEFAULT_SPINS_PER_WAVE
                        + " uses per wave, refilling when a wave clears.");
                lines.add("");
                lines.add("THE 9 ENCHANTMENTS (I / II / III)");
                for (EnchantType type : EnchantType.values()) {
                    lines.add(type.displayName + ": +" + fmt(type.amountFor(1)) + " / +" + fmt(type.amountFor(2))
                            + " / +" + fmt(type.amountFor(3)) + " " + type.unit);
                }
            }
        }
        return lines;
    }

    private static String fmt(double v) {
        return (v == Math.rint(v)) ? String.valueOf((int) v) : String.valueOf(v);
    }

    /** Splits one logical line into physical lines that each fit maxWidth, breaking on spaces.
     *  Never returns an empty list -- a blank input line stays a single blank line. */
    private static List<String> wrapLine(String line, java.awt.FontMetrics fm, int maxWidth) {
        List<String> out = new ArrayList<>();
        if (fm.stringWidth(line) <= maxWidth) {
            out.add(line);
            return out;
        }
        StringBuilder cur = new StringBuilder();
        for (String word : line.split(" ")) {
            String candidate = cur.length() == 0 ? word : cur + " " + word;
            if (fm.stringWidth(candidate) > maxWidth && cur.length() > 0) {
                out.add(cur.toString());
                cur = new StringBuilder(word);
            } else {
                cur = new StringBuilder(candidate);
            }
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }

    private void tickPlaying(long now) {
        if (input.wasJustPressed(KeyEvent.VK_ESCAPE)) {
            input.consume(KeyEvent.VK_ESCAPE);
            handleEscapeKey();
            return;
        }

        // E/P (interact) is ignored while the pause menu is up -- it's a modal overlay, same as
        // shop/enchant already being mutually exclusive with each other.
        if (!pauseMenuOpen) {
            if (input.wasJustPressed(KeyEvent.VK_E)) {
                input.consume(KeyEvent.VK_E);
                handleInteractKey(1);
            }
            if (input.wasJustPressed(KeyEvent.VK_P)) {
                input.consume(KeyEvent.VK_P);
                handleInteractKey(2);
            }
        }

        if (shopOpen) {
            tickShop();
            // BUG FIX: this used to unconditionally `return` here regardless of pauseDuringMenus,
            // while enchanting correctly checked the setting -- shop always froze the game even
            // with "Pause during shop/enchant/settings" set to OFF. Now consistent with the other two.
            if (pauseDuringMenus) return;
        }

        if (enchantingPlayerNumber != 0) {
            tickEnchanting(now);
            if (pauseDuringMenus) return; // "Enable pauses..." -- freezes everyone and the waves too
        }

        if (pauseMenuOpen) {
            tickPauseMenu();
            if (pauseDuringMenus) return;
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
            // BUG FIX: shop and the pause/Settings menu are shared, screen-covering modals -- both
            // players must be fully immobilized while either is open, even when "pause during
            // shop/enchant/settings" is OFF and the rest of the world (enemies included) keeps
            // running. Only enchanting was correctly gated before; shop/pause menu let movement
            // and attacks go through underneath the overlay.
            if (shopOpen || pauseMenuOpen) continue;

            p.update(input, arena);
            boolean justPressed = (p.playerNumber == 1) ? input.p1AttackJustPressed() : input.p2AttackJustPressed();

            if (p.selectedSlot == 0) {
                if (p.tryAttack(justPressed, now)) {
                    performPlayerAttack(p, now);
                }
            } else {
                if (p.tryUtilityAction(justPressed, now)) {
                    useSelectedItem(p, now);
                    p.selectedSlot = 0; // back to the weapon slot after using an item
                }
            }
        }

        // Snapshot everyone's HP before enemies act this frame, so a hit landing on someone kicks
        // them straight out of whatever menu is open -- shop and the Settings menu are shared
        // modals (a hit on EITHER player kicks both back to gameplay), enchanting is per-player
        // (only kicks the one player actually enchanting). Only reachable when "pause during
        // shop/enchant/settings" is OFF, since otherwise enemies are frozen along with everything
        // else while a menu is open.
        int p1HealthBefore = p1 != null ? p1.health : 0;
        int p2HealthBefore = p2 != null ? p2.health : 0;

        waveManager.update(now);
        awardWaveCompletionOrbs();

        for (Enemy enemy : waveManager.getEnemies()) {
            // Defensive: never let one enemy's per-frame update (movement, attack, knockback
            // resolution) take the whole game down. Any edge case here now logs to stderr and
            // that enemy just skips a frame, instead of a hard crash for everyone.
            try {
                Projectile fired = enemy.update(players, arena, now);
                if (fired != null) projectiles.add(fired);
            } catch (RuntimeException ex) {
                System.err.println("Enemy.update() threw for " + enemy.type + " at ("
                        + enemy.x + "," + enemy.y + ") -- skipping this frame for it:");
                ex.printStackTrace();
            }
        }

        updateProjectiles(now);
        damagePopups.removeIf(popup -> popup.isExpired(now));

        if (enchantingPlayerNumber != 0) {
            Player enchantingPlayer = playerByNumber(enchantingPlayerNumber);
            int before = enchantingPlayer == p1 ? p1HealthBefore : p2HealthBefore;
            if (enchantingPlayer != null && enchantingPlayer.health < before) {
                enchantingPlayer.enchanting = false;
                enchantingPlayerNumber = 0;
            }
        }
        boolean p1Hit = p1 != null && p1.health < p1HealthBefore;
        boolean p2Hit = p2 != null && p2.health < p2HealthBefore;
        if ((p1Hit || p2Hit) && (shopOpen || pauseMenuOpen)) {
            shopOpen = false;
            pauseMenuOpen = false;
            howToPlayOpen = false;
            confirmingQuit = false;
        }

        boolean anyoneAlive = players.stream().anyMatch(p -> p.alive);
        if (!anyoneAlive) {
            waveManager.resetToWaveOne(now);
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
     * cannot move while enchanting". V/M and Z/X/C (or ,/./) are universal here since only one
     * player can be in this menu at a time; the SPIN price keys and the exit key are still
     * player-specific (see below) so the other player's own keys keep working normally.
     */
    private void tickEnchanting(long now) {
        Player p = playerByNumber(enchantingPlayerNumber);
        if (p == null) { enchantingPlayerNumber = 0; return; }

        // Exit is handled by E (P1) / P (P2) via handleInteractKey(playerNumber), which only
        // lets the currently-enchanting player's own key close their menu.

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

        // "SPIN button" -- prices ascend left to right on the number row for both players, same
        // as the DEFAULT..SPIN_450 order: P1 uses 1,2,3,4,5 (their side of the row); P2 uses
        // 7,8,9,0,- (the mirrored keys on the other side -- also keeps this from colliding with
        // P1's own 1-4 hotbar-slot keys, which stay live for P1 while P2 is the one enchanting).
        EnchantSpinOption[] spinKeys = {
                EnchantSpinOption.DEFAULT, EnchantSpinOption.SPIN_30,
                EnchantSpinOption.SPIN_90, EnchantSpinOption.SPIN_270, EnchantSpinOption.SPIN_450
        };
        int[] keys = (p.playerNumber == 1)
                ? new int[]{KeyEvent.VK_1, KeyEvent.VK_2, KeyEvent.VK_3, KeyEvent.VK_4, KeyEvent.VK_5}
                : new int[]{KeyEvent.VK_7, KeyEvent.VK_8, KeyEvent.VK_9, KeyEvent.VK_0, KeyEvent.VK_MINUS};
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
                if (dsLevel > 0) w.addDarkOrbs(dsLevel + rng.nextInt(3 * dsLevel + 1));   // range [lvl, 4*lvl]
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
        // A wave (or several, if this frame skipped some) was just cleared -- refill everyone's
        // 1-LVL "Default" spin attempts back up to the cap.
        for (Player p : players) {
            p.resetDefaultSpinsForNewWave();
        }
        lastWavesCompletedSeen = waveManager.wavesCompleted;
    }

    /** Slot 0 (weapon) is handled by performPlayerAttack; this covers slots 1-3. */
    private void useSelectedItem(Player p, long now) {
        switch (p.selectedSlot) {
            case 1 -> p.useMedicKit(now);
            case 2 -> p.activateShield(now);
            case 3 -> { // Ranger's manual dagger -- melee on demand even with arrows left
                if (p.playerClass == PlayerClass.RANGER) meleeAttack(p, p.daggerDamage(), Player.DAGGER_RANGE, false, now);
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
            // "Armored... +1 for each boss defeated (stackable)" -- this is an Armored perk, not a
            // free-for-everyone bonus, so it only stacks for players who've actually bought at
            // least 1 level of Armored. A player with no upgrade stays at 0 stacks and gets no
            // Defense from bosses, same as before they ever fight one.
            for (Player p : players) {
                if (p.upgradeLevel(UpgradeType.ARMORED) > 0) p.bossDefenseStacks++;
            }
            // "+4 dmg per boss beaten" instead of the old flat +2/wave -- game-wide, affects every
            // enemy type spawned from now on (see WaveManager.spawnOne()).
            waveManager.bossKillCount++;
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

    private void performPlayerAttack(Player p, long nowMs) {
        if (p.playerClass == PlayerClass.TANK) {
            meleeAttack(p, p.swordDamage(), p.playerClass.range, true, nowMs);
        } else { // RANGER: bow shot, or dagger swing if it just ran out of arrows
            if (p.usedDaggerLastAttack) {
                meleeAttack(p, p.daggerDamage(), Player.DAGGER_RANGE, false, nowMs);
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

    /** "A number appears on top of the hit enemy... fades by 0.5s", Terraria-style. Must be
     *  stamped with the same paused-aware clock used everywhere else (nowMs), not real wall time
     *  -- otherwise once any pause has happened, this timestamp is "in the future" relative to
     *  the virtual clock used when drawing it, producing a negative elapsed time and an
     *  out-of-range alpha (this was a real, reproduced crash). */
    private void spawnDamagePopup(double x, double y, int amount, boolean crit, long nowMs) {
        Color color = crit ? new Color(255, 210, 60) : Color.WHITE; // crit color is animated inside DamagePopup itself
        damagePopups.add(new DamagePopup(x, y - 12, String.valueOf(amount), color, nowMs, crit));
    }

    private void meleeAttack(Player p, int damage, int range, boolean isSwordSwing, long nowMs) {
        for (Enemy enemy : waveManager.getEnemies()) {
            if (!enemy.alive) continue;
            double dist = Math.hypot(enemy.centerX() - p.centerX(), enemy.centerY() - p.centerY());
            if (dist <= range) {
                // Same defensive isolation as the arrow-hit path in updateProjectiles() -- a bad
                // hit logs instead of crashing the whole game.
                try {
                    CritResult result = applyCritical(p, damage, isSwordSwing);
                    enemy.damage(result.damage());
                    enemy.applyKnockback(p.centerX(), p.centerY(), 14.0);
                    spawnDamagePopup(enemy.centerX(), enemy.centerY(), result.damage(), result.crit(), nowMs);
                    if (!enemy.alive) onEnemyKilled(enemy, p);
                } catch (RuntimeException ex) {
                    System.err.println("Melee-hit resolution threw for " + enemy.type
                            + " at (" + enemy.x + "," + enemy.y + "):");
                    ex.printStackTrace();
                }
            }
        }
    }

    private void updateProjectiles(long nowMs) {
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
                        // Defensive: this exact moment (an arrow landing -- damage, knockback,
                        // possible kill) is where a reported crash happened. Isolating it means a
                        // bad hit logs and the arrow is still consumed, instead of taking the
                        // whole game down.
                        try {
                            Player owner = playerByNumber(proj.ownerPlayerNumber);
                            CritResult result = owner != null ? applyCritical(owner, proj.damage, false) : new CritResult(proj.damage, false);
                            double knockbackMult = owner != null ? 1 + owner.arrowSpeedBonusPercent() / 100.0 : 1.0;
                            enemy.damage(result.damage());
                            enemy.applyKnockback(proj.x, proj.y, 8.0 * knockbackMult); // Arrow Speed enchant boosts knockback too
                            spawnDamagePopup(enemy.centerX(), enemy.centerY(), result.damage(), result.crit(), nowMs);
                            if (!enemy.alive) onEnemyKilled(enemy, owner);
                        } catch (RuntimeException ex) {
                            System.err.println("Arrow-hit resolution threw for " + enemy.type
                                    + " at (" + enemy.x + "," + enemy.y + "):");
                            ex.printStackTrace();
                        }
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
                        p.takeDamage(proj.damage, proj.x, proj.y, 10.0, nowMs); // Shield + Defense + Temp HP applied inside
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

        try {
            switch (state) {
                case MAIN_MENU -> renderMainMenu(g);
                case OPTIONS -> renderOptions(g);
                case CHARACTER_SELECT -> renderCharacterSelect(g);
                case PLAYING -> renderPlaying(g);
                case GAME_OVER -> renderGameOver(g);
            }
        } catch (RuntimeException ex) {
            // Same safety net as tick() -- a bad render frame gets logged and skipped (last
            // good frame just stays on screen) instead of tearing down the whole window.
            System.err.println("Uncaught exception during paintComponent() in state " + state + ":");
            ex.printStackTrace();
        }
    }

    private void renderMainMenu(Graphics2D g) {
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, getWidth(), getHeight());
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 48));
        g.drawString("LAST STAND", 300, 150);

        g.setFont(new Font("SansSerif", Font.PLAIN, 26));
        boolean saveExists = SaveManager.saveExists();
        for (int i = 0; i < menuItems.length; i++) {
            boolean disabled = i == 1 && !saveExists; // "Load Save" grayed out with no save yet
            g.setColor(disabled ? new Color(80, 80, 80) : (i == menuIndex ? Color.YELLOW : Color.WHITE));
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

        if (confirmingLoad) drawConfirmLoad(g);
    }

    /** "Load Save" Yes/No prompt. Loading consumes the save, so this defaults to No. */
    private void drawConfirmLoad(Graphics2D g) {
        g.setColor(Color.BLACK); // fully opaque -- translucent let the menu items show through the text
        g.fillRect(0, 0, Constants.SCREEN_WIDTH, Constants.SCREEN_HEIGHT);

        g.setFont(new Font("SansSerif", Font.BOLD, 26));
        g.setColor(Color.WHITE);
        String title = "Load your save and start playing?";
        g.drawString(title, (Constants.SCREEN_WIDTH - g.getFontMetrics().stringWidth(title)) / 2, 220);

        g.setFont(new Font("SansSerif", Font.PLAIN, 17));
        g.setColor(new Color(200, 220, 255));
        g.drawString(pendingLoadSummary, (Constants.SCREEN_WIDTH - g.getFontMetrics().stringWidth(pendingLoadSummary)) / 2, 255);

        g.setFont(new Font("SansSerif", Font.PLAIN, 15));
        g.setColor(Color.LIGHT_GRAY);
        String warn = "Loading uses up this save, and saving is locked for " + SAVE_LOCK_WAVES + " waves afterward.";
        g.drawString(warn, (Constants.SCREEN_WIDTH - g.getFontMetrics().stringWidth(warn)) / 2, 285);

        g.setFont(new Font("SansSerif", Font.BOLD, 22));
        int midX = Constants.SCREEN_WIDTH / 2;
        g.setColor(confirmLoadYes ? Color.YELLOW : Color.LIGHT_GRAY);
        g.drawString(confirmLoadYes ? "> Yes, play" : "  Yes, play", midX - 170, 345);
        g.setColor(!confirmLoadYes ? Color.YELLOW : Color.LIGHT_GRAY);
        g.drawString(!confirmLoadYes ? "> No, go back" : "  No, go back", midX + 40, 345);

        g.setFont(new Font("SansSerif", Font.PLAIN, 14));
        g.setColor(Color.GRAY);
        String footer = "A/D or Left/Right to choose, SPACE/ENTER to confirm, ESC to cancel";
        g.drawString(footer, (Constants.SCREEN_WIDTH - g.getFontMetrics().stringWidth(footer)) / 2, 420);
    }

    private void renderOptions(Graphics2D g) {
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, getWidth(), getHeight());
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 32));
        g.drawString("OPTIONS", 380, 150);
        g.setFont(new Font("SansSerif", Font.PLAIN, 20));
        g.drawString("Arena theme: " + theme.displayName + "   (press T to toggle)", 300, 250);
        g.drawString("Pause game during shop/enchant/settings: " + (pauseDuringMenus ? "ON" : "OFF")
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

        long now = virtualNow; // shared with tick() -- keeps shield/heal-flash visuals in sync with
                                // the actual paused-aware game logic, not raw wall-clock time
        Graphics2D world = (Graphics2D) g.create(0, Constants.HUD_HEIGHT, Constants.SCREEN_WIDTH, viewportH);
        arena.draw(world, camX, camY, Constants.SCREEN_WIDTH, viewportH);
        drawEnchantCenterLabel(world);
        for (Enemy enemy : waveManager.getEnemies()) enemy.draw(world, camX, camY);
        for (Player p : players) if (p.alive) p.draw(world, camX, camY, now);
        for (Projectile proj : projectiles) proj.draw(world, camX, camY);
        for (DamagePopup popup : damagePopups) popup.draw(world, camX, camY, now);
        world.dispose();

        hud.draw(g, players, waveManager, walletP1, walletP2, now);
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

        // "Press ESC to check Settings" hint, top-right -- only shown when nothing else is
        // covering the screen, so it doesn't compete with the shop/enchant UI for attention.
        if (!shopOpen && enchantingPlayerNumber == 0 && !pauseMenuOpen) {
            g.setFont(new Font("SansSerif", Font.PLAIN, 13));
            g.setColor(new Color(255, 255, 255, 160));
            String hint = "Press ESC to check Settings";
            g.drawString(hint, Constants.SCREEN_WIDTH - g.getFontMetrics().stringWidth(hint) - 12, 18);
        }

        if (pauseMenuOpen) {
            drawPauseMenuOverlay(g);
        }
    }

    /** Dark modal overlay for the ESC pause menu and its two sub-views. */
    private void drawPauseMenuOverlay(Graphics2D g) {
        g.setColor(new Color(0, 0, 0, 190));
        g.fillRect(0, 0, Constants.SCREEN_WIDTH, Constants.SCREEN_HEIGHT);

        if (confirmingQuit) {
            drawConfirmQuit(g);
        } else if (howToPlayOpen) {
            drawHowToPlay(g);
        } else {
            drawPauseMenuButtons(g);
        }
    }

    private void drawPauseMenuButtons(Graphics2D g) {
        g.setFont(new Font("SansSerif", Font.BOLD, 32));
        g.setColor(Color.WHITE);
        String title = "SETTINGS";
        g.drawString(title, (Constants.SCREEN_WIDTH - g.getFontMetrics().stringWidth(title)) / 2, 180);

        String[] options = {"How to Play", "Save Game", "Back to Main Menu"};
        g.setFont(new Font("SansSerif", Font.PLAIN, 22));
        int y = 260;
        boolean saveLocked = isSaveLocked();
        int wavesLeft = Math.max(0, saveLockedUntilWave - waveManager.currentWave);
        for (int i = 0; i < options.length; i++) {
            boolean disabled = (i == 1 && saveLocked);
            g.setColor(disabled ? new Color(90, 90, 90) : (i == pauseMenuIndex ? Color.YELLOW : Color.LIGHT_GRAY));
            String label = (i == pauseMenuIndex ? "> " : "  ") + options[i];
            if (disabled) label += "  (unlocks in " + wavesLeft + " wave" + (wavesLeft == 1 ? "" : "s") + ")";
            g.drawString(label, (Constants.SCREEN_WIDTH - 220) / 2, y);
            y += 40;
        }

        if (System.currentTimeMillis() < saveMessageUntil) {
            g.setFont(new Font("SansSerif", Font.BOLD, 16));
            String msg;
            if (lastSaveLocked) {
                g.setColor(new Color(220, 180, 80));
                msg = "Saving is locked after loading a save -- unlocks in " + wavesLeft + " more wave" + (wavesLeft == 1 ? "" : "s");
            } else if (lastSaveSucceeded) {
                g.setColor(new Color(120, 220, 120));
                msg = "Game saved!";
            } else {
                g.setColor(new Color(220, 100, 100));
                msg = "Save failed -- see console for details";
            }
            g.drawString(msg, (Constants.SCREEN_WIDTH - g.getFontMetrics().stringWidth(msg)) / 2, y + 10);
        }

        g.setFont(new Font("SansSerif", Font.PLAIN, 14));
        g.setColor(Color.GRAY);
        String footer = "W/S or Up/Down to navigate, SPACE/ENTER to select, ESC to resume";
        g.drawString(footer, (Constants.SCREEN_WIDTH - g.getFontMetrics().stringWidth(footer)) / 2, 420);
    }

    private void drawConfirmQuit(Graphics2D g) {
        g.setFont(new Font("SansSerif", Font.BOLD, 26));
        g.setColor(Color.WHITE);
        String title = "Leave this match and return to the Main Menu?";
        g.drawString(title, (Constants.SCREEN_WIDTH - g.getFontMetrics().stringWidth(title)) / 2, 220);

        g.setFont(new Font("SansSerif", Font.PLAIN, 15));
        g.setColor(Color.LIGHT_GRAY);
        String warn = "Progress this run is not saved automatically. Don't forget to save!";
        g.drawString(warn, (Constants.SCREEN_WIDTH - g.getFontMetrics().stringWidth(warn)) / 2, 250);

        g.setFont(new Font("SansSerif", Font.BOLD, 22));
        int midX = Constants.SCREEN_WIDTH / 2;
        g.setColor(confirmQuitYes ? Color.YELLOW : Color.LIGHT_GRAY);
        g.drawString(confirmQuitYes ? "> Yes" : "  Yes", midX - 140, 310);
        g.setColor(!confirmQuitYes ? Color.YELLOW : Color.LIGHT_GRAY);
        g.drawString(!confirmQuitYes ? "> No" : "  No", midX + 60, 310);

        g.setFont(new Font("SansSerif", Font.PLAIN, 14));
        g.setColor(Color.GRAY);
        String footer = "A/D or Left/Right to choose, SPACE/ENTER to confirm, ESC to cancel";
        g.drawString(footer, (Constants.SCREEN_WIDTH - g.getFontMetrics().stringWidth(footer)) / 2, 420);
    }

    private static final String[] HOW_TO_PLAY_TABS = {"Player Control", "Shop", "Enchanting"};

    private void drawHowToPlay(Graphics2D g) {
        int panelX = 60, panelY = 40, panelW = Constants.SCREEN_WIDTH - 120, panelH = Constants.SCREEN_HEIGHT - 80;
        g.setColor(new Color(30, 30, 35));
        g.fillRoundRect(panelX, panelY, panelW, panelH, 12, 12);
        g.setColor(Color.WHITE);
        g.drawRoundRect(panelX, panelY, panelW, panelH, 12, 12);

        g.setFont(new Font("SansSerif", Font.BOLD, 24));
        g.drawString("HOW TO PLAY", panelX + 20, panelY + 36);

        // Section tabs
        int tabY = panelY + 60;
        int tabX = panelX + 20;
        g.setFont(new Font("SansSerif", Font.BOLD, 15));
        for (int i = 0; i < HOW_TO_PLAY_TABS.length; i++) {
            String label = "[" + (i + 1) + "] " + HOW_TO_PLAY_TABS[i];
            boolean active = i == howToPlaySection;
            g.setColor(active ? new Color(70, 70, 120) : new Color(45, 45, 50));
            int tw = g.getFontMetrics().stringWidth(label) + 20;
            g.fillRoundRect(tabX, tabY, tw, 26, 6, 6);
            g.setColor(active ? Color.YELLOW : Color.LIGHT_GRAY);
            g.drawString(label, tabX + 10, tabY + 18);
            tabX += tw + 8;
        }

        // Scrollable content, clipped to the panel body. Long generated lines (Shop/Enchanting
        // pull straight from live game data, so their length isn't hand-controlled) are word-
        // wrapped to the panel width instead of being clipped mid-sentence.
        int bodyY = tabY + 40;
        int bodyH = panelH - (bodyY - panelY) - 40;
        int bodyW = panelW - 40;
        Graphics2D body = (Graphics2D) g.create(panelX + 20, bodyY, bodyW, bodyH);
        body.setFont(new Font("Monospaced", Font.PLAIN, 14));
        java.awt.FontMetrics fm = body.getFontMetrics();

        List<String> lines = howToPlayLines(howToPlaySection);
        List<String> wrapped = new ArrayList<>();
        List<Boolean> isHeader = new ArrayList<>();
        for (String line : lines) {
            boolean header = line.equals(line.toUpperCase()) && !line.isBlank();
            List<String> pieces = wrapLine(line, fm, bodyW);
            for (int i = 0; i < pieces.size(); i++) {
                wrapped.add(i == 0 ? pieces.get(i) : "  " + pieces.get(i)); // indent continuations
                isHeader.add(header);
            }
        }

        int lineH = 19;
        int contentH = wrapped.size() * lineH;
        int maxScroll = Math.max(0, contentH - bodyH + lineH);
        howToPlayScroll = Math.min(howToPlayScroll, maxScroll);
        int ly = -howToPlayScroll + lineH;
        for (int i = 0; i < wrapped.size(); i++) {
            if (ly > -lineH && ly < bodyH + lineH) {
                body.setColor(isHeader.get(i) ? Color.YELLOW : Color.LIGHT_GRAY);
                body.drawString(wrapped.get(i), 0, ly);
            }
            ly += lineH;
        }
        body.dispose();

        g.setFont(new Font("SansSerif", Font.PLAIN, 13));
        g.setColor(Color.GRAY);
        String footer = "1/2/3, A/D or Left/Right to switch sections, W/S or Up/Down to scroll, ESC to go back";
        g.drawString(footer, panelX + 20, panelY + panelH - 14);
    }

    /**
     * Label above the Enchanting Center: "Press E/P to enchant" once close enough, drawn in
     * world space so it scrolls with the camera. Shows only the key(s) for whichever player(s)
     * are actually in range, since E (P1) and P (P2) are independent now.
     */
    private void drawEnchantCenterLabel(Graphics2D world) {
        boolean p1Near = false, p2Near = false;
        for (Player p : players) {
            if (arena.isNearEnchantCenter(p.centerX(), p.centerY())) {
                if (p.playerNumber == 1) p1Near = true; else p2Near = true;
            }
        }
        boolean anyoneNear = p1Near || p2Near;

        String label;
        Color color;
        if (enchantingPlayerNumber != 0) {
            label = "Enchanting Center";
            color = new Color(150, 170, 230);
        } else if (anyoneNear) {
            String key = (p1Near && p2Near) ? "E/P" : (p1Near ? "E" : "P");
            label = "Press " + key + " to enchant";
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
