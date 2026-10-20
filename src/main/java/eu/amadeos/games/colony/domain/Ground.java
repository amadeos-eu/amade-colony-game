package eu.amadeos.games.colony.domain;

import eu.amadeos.gameengine.data.TextData.DataException;
import eu.amadeos.gameengine.tiled.TiledMap;

/**
 * What a tile of a map is to the rules, by the class of its tile in Tiled's tileset: {@code grass} to build
 * on, {@code forest} (grass with a grown tree at the start), {@code rock} with stone for the quarry,
 * {@code water} with fish, and {@code mountain} that nobody crosses. A tile without a class is grass.
 */
// --8<-- [start:ground]
public enum Ground {
    GRASS, FOREST, ROCK, WATER, MOUNTAIN;

    /** People walk over grass and through forest; rock, water and mountains are in the way. */
    public boolean walkable() {
        return this == GRASS || this == FOREST;
    }

    /** The ground a gid of the map stands for; an unknown class is an error naming the map. */
    static Ground of(TiledMap map, int gid) {
        return switch (map.type(gid)) {
            case "", "grass" -> GRASS;
            case "forest" -> FOREST;
            case "rock" -> ROCK;
            case "water" -> WATER;
            case "mountain" -> MOUNTAIN;
            default -> throw new DataException(map.source() + ": the tile class \"" + map.type(gid)
                    + "\" means nothing to the game; use grass, forest, rock, water or mountain");
        };
    }
}
// --8<-- [end:ground]
