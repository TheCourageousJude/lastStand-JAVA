package laststand.wave;

import laststand.entity.Enemy;
import laststand.entity.EnemyType;
import laststand.world.Arena;
import laststand.world.Portal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Portal rule straight from the design doc (wave numbers are 1-indexed):
 *   wave = 1+5n or 3+5n  -> 1 portal open
 *   wave = 2+5n          -> 2 portals open
 *   wave = 4+5n          -> 3 portals open
 *   wave = 5+5n          -> boss round (any monster type, buffed)
 *
 * Wave 6+ (non-boss) rolls a hidden Scenario; boss waves roll a hidden BossScenario.
 *
 * Enemy count per wave isn't specified exactly in the doc beyond "N enemies
 * present" -- using a simple linear ramp here, easy to swap out.
 *
 * Waves 1-15 follow the fixed "beginner curriculum" from the design doc:
 * some waves only ever spawn one type, some mix a handful of types, and the
 * boss waves (5/10/15, which line up with the curriculum's "either A/B/C"
 * entries) pick their single boss from that wave's themed pool. Wave 16+
 * opens back up to the full random pool of all 8 types.
 */
public class WaveManager {

    private final Arena arena;
    private final List<Enemy> enemies = new ArrayList<>();
    private final List<Portal> activePortals = new ArrayList<>();
    private final Map<Portal, EnemyType> portalTypeAssignment = new HashMap<>();
    private final Random rng = new Random();

    public int currentWave = 1;
    public boolean bossWave = false;
    public int wavesCompleted = 0; // used by GamePanel to award dark orbs (+10 each)
    // "+2, +4, +7, +11, +16, +22, +29, +37, +46..." -- triangular-ish growth per boss defeated
    // (game-wide, not per-player), replacing the old flat +4/boss. See bossDamageBonus() for the
    // closed-form formula and the hard +50 cap once wave 51 is reached.
    public int bossKillCount = 0;
    private int enemiesToSpawn;
    private int enemiesSpawned;
    private long lastSpawnAt;
    private static final int SPAWN_INTERVAL_MS = 900; // NORMAL scenario pacing; others derive theirs (see Scenario)
    private int spawnIntervalMs = SPAWN_INTERVAL_MS;
    private List<EnemyType> currentPool; // types allowed to spawn this wave
    /** Scenario in effect for the current wave (NORMAL for waves 1-5 and all boss waves). */
    public Scenario currentScenario = Scenario.NORMAL;
    /** Which portal each upcoming spawn comes out of, pre-rolled at wave start (so quotas are exact). */
    private final java.util.ArrayDeque<Portal> spawnQueue = new java.util.ArrayDeque<>();
    private static final int FIRST_SCENARIO_WAVE = 6;
    /** Types a Savage wave / Savage boss can be: the melee Savage, or a Savage-mutated ranged type
     *  (so purple 75%-faster bullets exist for both skeleton and blaster golem). */
    private static final List<EnemyType> SAVAGE_POOL =
            List.of(EnemyType.SAVAGE, EnemyType.SKELETON, EnemyType.BLASTER_GOLEM);
    /** Boss special in effect (null on non-boss waves). Never shown to the player. */
    public BossScenario currentBossScenario = null;
    private Enemy sorcererBoss;
    private int sorcererStage;
    // Scenarios are meant to stay hidden from players; run with -Dlaststand.debug=true to log them.
    private static final boolean DEBUG = Boolean.getBoolean("laststand.debug");

    public WaveManager(Arena arena, long nowMs) {
        this.arena = arena;
        startWave(1, nowMs);
    }

    public List<Enemy> getEnemies() {
        return enemies;
    }

    public void startWave(int waveNumber, long nowMs) {
        if (waveNumber > 1) wavesCompleted++; // the previous wave was just cleared
        currentWave = waveNumber;
        enemies.clear();
        enemiesSpawned = 0;
        lastSpawnAt = nowMs; // must share the caller's clock -- see GamePanel's paused virtual clock
        currentPool = poolFor(waveNumber);
        sorcererBoss = null;
        sorcererStage = 0;
        configurePortals(); // rolls the scenario, opens portals, assigns one type per open portal
        enemiesToSpawn = bossWave ? 1 : currentScenario.enemyCount(waveNumber, activePortals.size());
        spawnIntervalMs = bossWave ? SPAWN_INTERVAL_MS : currentScenario.spawnIntervalMs(SPAWN_INTERVAL_MS);
        buildSpawnQueue();
        if (DEBUG) System.out.println("[Wave " + waveNumber + "] "
                + (bossWave ? "BOSS/" + currentBossScenario.label : currentScenario.label)
                + " -- " + enemiesToSpawn + " enemies, " + activePortals.size() + " portal(s), 1 per "
                + spawnIntervalMs + " ms");
    }

    /**
     * Scenario roll for wave 6+ (non-boss). Scenarios with a fixed chance (Power Rangers 10%, Savages
     * 40%, Ghastlies 8% -- see Scenario.fixedChance()) are rolled first when eligible; whatever is
     * left is split evenly among the eligible Swarm / Heavy Hitters / Standard.
     */
    private Scenario rollScenario(int wave, int naturalPortals) {
        List<Scenario> base = new ArrayList<>();
        double r = rng.nextDouble();
        for (Scenario sc : Scenario.values()) {
            if (sc == Scenario.NORMAL || !sc.eligible(wave, naturalPortals)) continue;
            double chance = sc.fixedChance();
            if (chance <= 0) { base.add(sc); continue; }
            if (r < chance) return sc;
            r -= chance;
        }
        if (base.isEmpty()) return Scenario.STANDARD; // can't happen (Standard is always eligible), but stay safe
        return base.get(rng.nextInt(base.size()));
    }

    /** Savage is 25% once eligible (wave 25+), 50% from wave 50; the remaining specials split the rest evenly. */
    private BossScenario rollBossScenario(int wave) {
        List<BossScenario> others = new ArrayList<>();
        for (BossScenario bs : BossScenario.values()) if (bs != BossScenario.SAVAGE) others.add(bs);
        if (BossScenario.SAVAGE.eligible(wave) && rng.nextDouble() < BossScenario.savageChance(wave)) {
            return BossScenario.SAVAGE;
        }
        return others.get(rng.nextInt(others.size()));
    }

    private void buildSpawnQueue() {
        spawnQueue.clear();
        if (activePortals.isEmpty()) return;
        List<Portal> picks = new ArrayList<>();
        if (currentScenario == Scenario.POWER_RANGERS) {
            // exactly even split across the 3 portals (30 / 3 = 10 each), interleaved at random
            int perPortal = enemiesToSpawn / activePortals.size();
            for (Portal p : activePortals) for (int i = 0; i < perPortal; i++) picks.add(p);
            Collections.shuffle(picks, rng);
        } else {
            for (int i = 0; i < enemiesToSpawn; i++) picks.add(activePortals.get(rng.nextInt(activePortals.size())));
        }
        spawnQueue.addAll(picks);
    }

    /** Beginner curriculum: fixed pool of allowed enemy types per wave, waves 1-15. */
    private static List<EnemyType> poolFor(int wave) {
        return switch (wave) {
            case 1 -> List.of(EnemyType.ZOMBIE);
            case 2 -> List.of(EnemyType.ZOMBIE, EnemyType.SKELETON);
            case 3 -> List.of(EnemyType.SKELETON);
            case 4 -> List.of(EnemyType.ZOMBIE, EnemyType.SKELETON, EnemyType.WOLF);
            case 5 -> List.of(EnemyType.ZOMBIE, EnemyType.SKELETON, EnemyType.WOLF); // boss wave
            case 6 -> List.of(EnemyType.BEAR);
            case 7 -> List.of(EnemyType.BEAR, EnemyType.DEER);
            case 8 -> List.of(EnemyType.GOLEM);
            case 9 -> List.of(EnemyType.GOLEM, EnemyType.WOLF, EnemyType.BEAR);
            case 10 -> List.of(EnemyType.GOLEM, EnemyType.BEAR, EnemyType.DEER, EnemyType.WOLF); // boss wave
            case 11 -> List.of(EnemyType.ASSASSIN);
            case 12 -> List.of(EnemyType.ASSASSIN, EnemyType.DEER);
            case 13 -> List.of(EnemyType.BLASTER_GOLEM);
            case 14 -> List.of(EnemyType.GOLEM, EnemyType.BLASTER_GOLEM, EnemyType.ASSASSIN);
            case 15 -> List.of(EnemyType.GOLEM, EnemyType.ASSASSIN, EnemyType.BLASTER_GOLEM); // boss wave
            default -> EnemyType.regulars(); // wave 16+: fully random (specials only come via scenarios)
        };
    }

    /** Called when both players are dead: "resets back to 1 if game is over". */
    public void resetToWaveOne(long nowMs) {
        bossKillCount = 0;
        startWave(1, nowMs);
    }

    /** Enemy damage bonus for the current wave: triangular-ish growth per boss defeated
     *  (+2, +4, +7, +11, +16, +22, +29, +37, +46...), hard-capped at a flat +50 once wave 51 is
     *  reached so endless play stays survivable instead of scaling forever. */
    public int bossDamageBonus() {
        if (currentWave >= 51) return 50;
        if (bossKillCount <= 0) return 0;
        return 1 + bossKillCount * (bossKillCount + 1) / 2;
    }

    // "+11% enemy HP per boss defeated" -- compounding, uncapped (was 18% -- "definitely very,
    // very tough"). Players are expected to keep pace through the same boss kills via Armored's
    // boss-defense stacks, Grow's per-wave HP, and their own enchants/upgrades, rather than this
    // being tuned to stay flat on its own.
    private static final double HP_PER_BOSS_KILL = 0.11;

    /** Enemy HP multiplier from bosses defeated so far -- compounds: 1.11^bossKillCount. */
    public double bossKillHpMultiplier() {
        return Math.pow(1 + HP_PER_BOSS_KILL, bossKillCount);
    }

    /**
     * Opens the right number of portals for this wave, and -- fixing the old
     * "2+ types coming out of one portal" inconsistency -- assigns each open
     * portal exactly ONE type from this wave's pool, so a portal is
     * consistent for the whole wave (e.g. wave 24 with 3 portals open might
     * be wolves/assassins/zombies, one type per portal, not a random mix
     * out of every portal).
     */
    private void configurePortals() {
        for (Portal p : arena.portals) p.active = false;
        activePortals.clear();
        portalTypeAssignment.clear();

        int posInCycle = ((currentWave - 1) % 5) + 1; // 1..5
        bossWave = posInCycle == 5;
        int naturalPortals = switch (posInCycle) {
            case 1, 3 -> 1;
            case 2 -> 2;
            case 4 -> 3;
            default -> 1; // boss round: one portal is enough
        };

        // Waves 1-5 and every boss wave stay on the classic rules; wave 6+ rolls a scenario.
        currentScenario = (bossWave || currentWave < FIRST_SCENARIO_WAVE)
                ? Scenario.NORMAL
                : rollScenario(currentWave, naturalPortals);
        currentBossScenario = bossWave ? rollBossScenario(currentWave) : null;
        if (currentBossScenario == BossScenario.SAVAGE) currentPool = SAVAGE_POOL;
        int portalsToOpen = currentScenario.portalsToOpen(naturalPortals);
        switch (currentScenario) {
            case SAVAGES -> currentPool = SAVAGE_POOL;
            case GHASTLIES -> currentPool = List.of(EnemyType.GHASTLY);
            default -> { }
        }

        List<Portal> shuffledPortals = new ArrayList<>(arena.portals);
        Collections.shuffle(shuffledPortals, rng);

        List<EnemyType> shuffledTypes = new ArrayList<>(currentPool);
        Collections.shuffle(shuffledTypes, rng);

        for (int i = 0; i < portalsToOpen && i < shuffledPortals.size(); i++) {
            Portal portal = shuffledPortals.get(i);
            portal.active = true;
            activePortals.add(portal);
            // wrap around if there are fewer types in the pool than portals open
            portalTypeAssignment.put(portal, shuffledTypes.get(i % shuffledTypes.size()));
        }

        if (currentScenario == Scenario.POWER_RANGERS) {
            // 20 skeletons + 10 blaster golems over 3 portals: two skeleton portals, one blaster portal
            for (int i = 0; i < activePortals.size(); i++) {
                portalTypeAssignment.put(activePortals.get(i), i < 2 ? EnemyType.SKELETON : EnemyType.BLASTER_GOLEM);
            }
        }
    }

    public void update(long nowMs) {
        for (Portal p : arena.portals) p.update();

        enemies.removeIf(e -> !e.alive);
        updateSorcerer();

        if (enemiesSpawned < enemiesToSpawn && nowMs - lastSpawnAt >= spawnIntervalMs && !activePortals.isEmpty()) {
            lastSpawnAt = nowMs;
            spawnOne();
        }

        if (enemiesSpawned >= enemiesToSpawn && enemies.isEmpty()) {
            startWave(currentWave + 1, nowMs);
        }
    }

    /** Mods for whatever is spawning right now: boss special for bosses, scenario stats otherwise. */
    private Enemy.Mods currentMods() {
        if (bossWave) return currentBossScenario.mods(bossKillCount);
        Enemy.Mods m = Enemy.Mods.of(currentScenario.hpMult, currentScenario.speedMult, currentScenario.damageMult);
        return currentScenario == Scenario.SAVAGES ? m.withSavage() : m;
    }

    private void spawnOne() {
        Portal portal = spawnQueue.isEmpty()
                ? activePortals.get(rng.nextInt(activePortals.size()))
                : spawnQueue.poll();
        EnemyType type = portalTypeAssignment.get(portal); // consistent per portal for the whole wave
        Enemy.Mods mods = currentMods();
        double[] pos = arena.randomSpawnNear(portal, Enemy.sizeFor(bossWave, mods));
        Enemy e = new Enemy(type, bossWave, pos[0], pos[1], currentWave,
                bossDamageBonus(), bossKillHpMultiplier(), mods);
        enemies.add(e);
        if (bossWave) e.specialLabel = currentBossScenario.label; // shown above the boss (regular scenarios stay hidden)
        if (bossWave && currentBossScenario == BossScenario.SORCERER) sorcererBoss = e;
        enemiesSpawned++;
    }

    /**
     * Sorcerer boss: each time its HP first falls to 75% / 50% / 25% it summons a ring of its own
     * type (5 / 7 / 10). If one hit crosses several thresholds at once, every crossed stage fires.
     * Minions are ordinary (non-boss) enemies, so the wave isn't clear until they're dead too.
     */
    private void updateSorcerer() {
        Enemy boss = sorcererBoss;
        if (boss == null || !boss.alive) return;
        double hpFrac = boss.health / (double) boss.maxHealth;
        while (sorcererStage < BossScenario.SORCERER_THRESHOLDS.length
                && hpFrac <= BossScenario.SORCERER_THRESHOLDS[sorcererStage]) {
            summonRing(boss, BossScenario.SORCERER_SUMMONS[sorcererStage]);
            sorcererStage++;
        }
    }

    private void summonRing(Enemy boss, int count) {
        double startAngle = rng.nextDouble() * Math.PI * 2;
        int size = Enemy.sizeFor(false, BossScenario.SORCERER_MINION);
        for (int i = 0; i < count; i++) {
            double angle = startAngle + (Math.PI * 2 * i) / count;
            double[] pos = arena.openSpotAround(boss.centerX(), boss.centerY(),
                    BossScenario.SORCERER_RING_RADIUS, angle, size);
            enemies.add(new Enemy(boss.type, false, pos[0], pos[1], currentWave,
                    bossDamageBonus(), bossKillHpMultiplier(), BossScenario.SORCERER_MINION));
        }
    }
}
