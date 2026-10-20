package eu.amadeos.games.colony.domain;

import eu.amadeos.gameengine.economy.Recipe;

import java.util.Map;

/**
 * A kind of building, from {@code buildings.txt}: its size in tiles, what it costs, how long it takes to
 * build, what its worker does and with what.
 *
 * @param tab    the tab of the panel it is built from
 * @param cost   the goods a site needs before it stands
 * @param recipe what goes in and comes out, {@code null} for a building that makes nothing
 * @param radius how far the worker goes from the house, in tiles; for a tower and the storehouse, the land
 *               they claim
 * @param tool   whether the worker needs tools before taking the job
 * @param keep   how many of each input the building keeps at its door
 */
public record BuildingType(String id, Tab tab, int width, int height, Map<Good, Integer> cost, double seconds,
                           Work work, Recipe<Good> recipe, int radius, boolean tool, int keep) {
    /** The three tabs of the build list. */
    public enum Tab { BASIC, FOOD, CRAFTS }

    public BuildingType {
        cost = Map.copyOf(cost);
    }

    /** Whether the player can build it: the storehouse at the start of a mission is given by the map. */
    public boolean buildable() {
        return !cost.isEmpty();
    }

    /** Whether someone works in it. */
    public boolean manned() {
        return work != Work.STORE && work != Work.SETTLE;
    }

    /** All goods of its cost, counted together. */
    public int costTotal() {
        int total = 0;
        for (int count : cost.values()) total += count;
        return total;
    }

    /** The column of the flag, below the middle of the house; the row is the one below it. */
    public int flagColumn() {
        return width / 2;
    }
}
