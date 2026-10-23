package eu.amadeos.games.colony.domain;

/**
 * The numbers of the game that are not in the data files: sizes in logical units (a tile is 16), times in
 * seconds. What the buildings cost and make is in {@code buildings.txt}; what each mission gives and asks,
 * in {@code missions.txt}.
 */
public final class Field {
    /** The logical size of the screen: the goods bar on top, the view of the map, the panel on the right. */
    public static final double WIDTH = 640;
    public static final double HEIGHT = 400;
    public static final double PANEL = 128;
    public static final double BAR = 20;
    public static final double VIEW_WIDTH = WIDTH - PANEL;
    /** How wide the screen may grow on a wide monitor: the map gets the extra width, the panel stays. */
    public static final int MAX_WIDTH = 1024;
    public static final double VIEW_HEIGHT = HEIGHT - BAR;
    public static final int TILE = 16;

    /** People walk this many tiles a second; a carrier with a load as fast. */
    public static final double WALK = 2.4;
    /** Wares that wait at a flag, at most. */
    public static final int FLAG_ROOM = 8;
    /** Goods a building keeps at its door before the worker stops. */
    public static final int OUT_ROOM = 4;
    /** How often the storehouses send goods and settlers where they are needed. */
    public static final double DISPATCH = 0.5;
    /** Workers rest while the storehouses hold this many of their good. */
    public static final int ENOUGH = 30;
    /** A flag keeps this many tiles to the next, across and along: no flag next to a flag. */
    public static final int FLAG_GAP = 2;

    /** A tree grows this long, outside winter, from a sapling to a tree that can be felled. */
    public static final double TREE_GROWS = 100;
    /** A field grows this long, outside winter, from sown to ripe. */
    public static final double FIELD_GROWS = 70;
    /** A farm keeps this many fields at most. */
    public static final int FIELDS = 6;
    /** How long the work outside takes: felling, planting, breaking stone, fishing, sowing, harvesting. */
    public static final double FELL = 4;
    public static final double PLANT = 3;
    public static final double BREAK = 5;
    public static final double CATCH = 6;
    public static final double SOW = 2;
    public static final double REAP = 3;
    /** The worker rests this long at home between two trips. */
    public static final double REST = 2;
    /** Stone in a rock tile; a rock broken to the end becomes grass. */
    public static final int STONE = 6;
    /** Fish in a water tile, and how long it takes for one to come back. */
    public static final int FISH = 3;
    public static final double FISH_BACK = 60;

    /** Sheep: at most, at the start of a farm, and how long a lamb takes outside winter. */
    public static final int SHEEP = 5;
    public static final int FIRST_SHEEP = 3;
    public static final double LAMB = 45;

    /** Fire: not before this many seconds, how often in summer and else, and how long a house burns. */
    public static final double FIRE_FIRST = 300;
    public static final double FIRE_SUMMER = 150;
    public static final double FIRE_ELSE = 450;
    public static final double BURNS = 25;
    /** A manned well this close, in tiles, with water at its door, puts out a fire in this many seconds. */
    public static final int WELL_REACH = 8;
    public static final double DOUSE = 5;

    /** Wolves: how often they leave a den in autumn and winter, their speed, how far they look for sheep. */
    public static final double WOLF_EVERY = 50;
    public static final double WOLF_SPEED = 3.2;
    public static final int WOLF_SMELL = 40;
    /** The dog drives a wolf off within this many tiles of the pasture, and rests this long after. */
    public static final double DOG_REACH = 3;
    public static final double DOG_REST = 12;

    private Field() {
    }
}
