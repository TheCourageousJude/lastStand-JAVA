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
    private int enemiesToSpawn;
    private int enemiesSpawned;
    private long lastSpawnAt;
    private static final int SPAWN_INTERVAL_MS = 900;
    private List<EnemyType> currentPool; // types allowed to spawn this wave

    public WaveManager(Arena arena) {
        this.arena = arena;
        startWave(1);
    }

    public List<Enemy> getEnemies() {
        return enemies;
    }

    public void startWave(int waveNumber) {
        if (waveNumber > 1) wavesCompleted++; // the previous wave was just cleared
        currentWave = waveNumber;
        enemies.clear();
        enemiesSpawned = 0;
        lastSpawnAt = System.currentTimeMillis();
        currentPool = poolFor(waveNumber);
        configurePortals(); // needs currentPool to assign one type per open portal
        enemiesToSpawn = bossWave ? 1 : 3 + waveNumber; // tune freely
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
            default -> List.of(EnemyType.values()); // wave 16+: fully random
        };
    }

    /** Called when both players are dead: "resets back to 1 if game is over". */
    public void resetToWaveOne() {
        startWave(1);
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
        int portalsToOpen = switch (posInCycle) {
            case 1, 3 -> 1;
            case 2 -> 2;
            case 4 -> 3;
            default -> 1; // boss round: one portal is enough
        };

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
    }

    public void update(long nowMs) {
        for (Portal p : arena.portals) p.update();

        enemies.removeIf(e -> !e.alive);

        if (enemiesSpawned < enemiesToSpawn && nowMs - lastSpawnAt >= SPAWN_INTERVAL_MS && !activePortals.isEmpty()) {
            lastSpawnAt = nowMs;
            spawnOne();
        }

        if (enemiesSpawned >= enemiesToSpawn && enemies.isEmpty()) {
            startWave(currentWave + 1);
        }
    }

    private void spawnOne() {
        Portal portal = activePortals.get(rng.nextInt(activePortals.size()));
        EnemyType type = portalTypeAssignment.get(portal); // consistent per portal for the whole wave
        int size = bossWave ? 46 : 30;
        double[] pos = arena.randomSpawnNear(portal, size);
        enemies.add(new Enemy(type, bossWave, pos[0], pos[1], currentWave));
        enemiesSpawned++;
    }
}
