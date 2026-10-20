package eu.amadeos.games.colony.domain;

/**
 * What the worker of a building does, the word after {@code work} in {@code buildings.txt}. The building's
 * recipe says what goes in and what comes out; the work says where the worker gets it.
 */
public enum Work {
    /** The storehouse: keeps the goods and the settlers who have no job. */
    STORE,
    /** Makes its recipe at home: the sawmill, the mill, the bakery, the weaver, the toolmaker, the well. */
    MAKE,
    /** Walks to a grown tree, fells it and brings the log. */
    FELL,
    /** Plants young trees around the house. */
    PLANT,
    /** Breaks stone from the rock nearby. */
    QUARRY,
    /** Fishes at the water nearby. */
    FISH,
    /** Sows fields around the farm and harvests them when they are ripe. */
    FARM,
    /** Keeps sheep in the pasture: grain and water become wool, as fast as the sheep allow. */
    GRAZE,
    /** No worker: food and cloth bring a new settler. */
    SETTLE,
    /** A watchman on the tower: the land around it belongs to the settlement. */
    WATCH
}
