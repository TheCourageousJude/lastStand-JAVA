package laststand.world;

public enum TileType {
    PATH,       // walkable
    OBSTACLE,   // solid corner block inside the playable area (grass / cactus)
    ROCK,       // solid, textured rock making up the irregular border around the arena
    VOID        // nothing here -- open sky beyond the island's outline (also solid: you can't walk off)
}
