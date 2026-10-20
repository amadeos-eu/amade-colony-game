package eu.amadeos.games.colony.domain;

import eu.amadeos.gameengine.data.TextData.DataException;
import eu.amadeos.gameengine.tile.Cell;
import eu.amadeos.gameengine.tile.TileMap;
import eu.amadeos.gameengine.tiled.TiledMap;
import eu.amadeos.gameengine.tiled.TiledObject;

import java.util.ArrayList;
import java.util.List;

/**
 * A mission's map as drawn in Tiled, with the tileset {@code tiles.tsx}. The tile layer {@code ground}
 * holds the grass, forest, rock, water and mountains by their tiles' classes ({@link Ground}); the object
 * layer {@code things} holds the storehouse the settlers start in (an object of the class
 * {@code storehouse}, its top left corner on the house's top left tile) and the wolves' dens (class
 * {@code den}, on its tile). The outermost ring of tiles is a border nobody crosses.
 */
public final class Level {
    /** Where the maps and their tileset lie among the classes. */
    public static final String FOLDER = "/eu/amadeos/games/colony/maps/";

    private final Mission mission;
    private final TiledMap map;
    private final TileMap<Ground> ground;
    private final Cell store;
    private final List<Cell> dens;

    private Level(Mission mission, TiledMap map, TileMap<Ground> ground, Cell store, List<Cell> dens) {
        this.mission = mission;
        this.map = map;
        this.ground = ground;
        this.store = store;
        this.dens = List.copyOf(dens);
    }

    /** Mission {@code number}, counted from 1, with its map. */
    public static Level load(int number, Rules rules) {
        Mission mission = rules.mission(number);
        return read(mission, TiledMap.load(Level.class, FOLDER + mission.map()), rules);
    }

    // --8<-- [start:read]
    public static Level read(Mission mission, TiledMap map, Rules rules) {
        if (map.tileWidth() != Field.TILE || map.tileHeight() != Field.TILE) {
            throw new DataException(map.source() + ": the tiles must be " + Field.TILE + " × " + Field.TILE);
        }
        TileMap<Ground> ground = map.tiles("ground", gid -> Ground.of(map, gid));
        // The engine's maps wrap round at the edges; this one ends in mountains.
        for (int col = 0; col < ground.width(); col++) {
            ground.set(col, 0, Ground.MOUNTAIN);
            ground.set(col, ground.height() - 1, Ground.MOUNTAIN);
        }
        for (int row = 0; row < ground.height(); row++) {
            ground.set(0, row, Ground.MOUNTAIN);
            ground.set(ground.width() - 1, row, Ground.MOUNTAIN);
        }
        Cell store = null;
        List<Cell> dens = new ArrayList<>();
        BuildingType house = rules.storehouse();
        for (TiledObject thing : map.objects("things")) {
            if (thing.type().equals(house.id())) {
                if (store != null) throw error(map, thing, "a map has one storehouse");
                int col = (int) Math.round(thing.x() / Field.TILE);
                int row = (int) Math.round(thing.y() / Field.TILE);
                for (int dy = 0; dy <= house.height(); dy++) {
                    for (int dx = 0; dx < house.width(); dx++) {
                        if (dy == house.height() && dx != house.flagColumn()) continue;
                        if (!ground.contains(col + dx, row + dy)) throw error(map, thing, "reaches outside the map");
                        if (ground.get(col + dx, row + dy) != Ground.GRASS) throw error(map, thing, "stands off the grass");
                    }
                }
                store = new Cell(col, row);
            } else if (thing.type().equals("den")) {
                int col = (int) Math.floor(thing.centerX() / Field.TILE);
                int row = (int) Math.floor(thing.centerY() / Field.TILE);
                if (!ground.contains(col, row) || !ground.get(col, row).walkable()) {
                    throw error(map, thing, "a den lies on grass or in the forest");
                }
                dens.add(new Cell(col, row));
            } else {
                throw error(map, thing, "the class \"" + thing.type() + "\" is neither the storehouse nor a den");
            }
        }
        if (store == null) throw new DataException(map.source() + ": no storehouse");
        return new Level(mission, map, ground, store, dens);
    }
    // --8<-- [end:read]

    private static DataException error(TiledMap map, TiledObject thing, String message) {
        return new DataException(map.source() + ": object " + thing.id() + " at (" + (int) thing.x() + ", "
                + (int) thing.y() + "): " + message);
    }

    public Mission mission() {
        return mission;
    }

    public int number() {
        return mission.number();
    }

    public TiledMap map() {
        return map;
    }

    public Ground ground(int col, int row) {
        return ground.get(col, row);
    }

    /** The ground as a map, for the searches; do not change it. */
    public TileMap<Ground> grounds() {
        return ground;
    }

    public boolean contains(int col, int row) {
        return ground.contains(col, row);
    }

    public int width() {
        return ground.width();
    }

    public int height() {
        return ground.height();
    }

    /** The top left tile of the storehouse. */
    public Cell store() {
        return store;
    }

    public List<Cell> dens() {
        return dens;
    }
}
