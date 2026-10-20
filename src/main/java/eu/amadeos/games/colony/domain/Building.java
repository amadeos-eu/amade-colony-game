package eu.amadeos.games.colony.domain;

import eu.amadeos.gameengine.economy.Stock;
import eu.amadeos.gameengine.motion.Wander;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * A building of the settlement, from the site to the house that burns. While it is a site, its stock holds
 * the goods brought for it; once it stands, the goods that go in, and the storehouse's stock is the stores.
 * The fields are the rules' to change; the user interface reads them through the methods.
 */
public final class Building {
    private final int id;
    private final BuildingType type;
    private final int col;
    private final int row;
    private final int flag;
    boolean done;
    double built;
    final Stock<Good> stock = new Stock<>(Good.class);
    /** Wares on their way here, by good. */
    final Map<Good, Integer> coming = new EnumMap<>(Good.class);
    /** Goods made that wait in the house for room at the flag. */
    int out;
    /** The builder and the worker, by their person's number; -1 for none. */
    int builder = -1;
    int worker = -1;
    /** Whether the worker is in, or out on the job; the house works only then. */
    boolean staffed;
    /** Seconds of the piece of work in hand; 0 when there is none. */
    double work;
    double fire;
    double douse;
    int sheep;
    double lamb;
    /** The dog's rest after a chase, and where it ran to. */
    double dog;
    double dogX;
    double dogY;
    final List<Wander> herd = new ArrayList<>();

    Building(int id, BuildingType type, int col, int row, int flag) {
        this.id = id;
        this.type = type;
        this.col = col;
        this.row = row;
        this.flag = flag;
    }

    public int id() {
        return id;
    }

    public BuildingType type() {
        return type;
    }

    /** The top left tile. */
    public int col() {
        return col;
    }

    public int row() {
        return row;
    }

    /** The flag at the door. */
    public int flag() {
        return flag;
    }

    /** The middle of the house, in units. */
    public double x() {
        return (col + type.width() / 2.0) * Field.TILE;
    }

    public double y() {
        return (row + type.height() / 2.0) * Field.TILE;
    }

    public boolean contains(int c, int r) {
        return c >= col && r >= row && c < col + type.width() && r < row + type.height();
    }

    /** Whether it stands; false for a site. */
    public boolean done() {
        return done;
    }

    /** How far the site is, 0 to 1. */
    public double built() {
        return built;
    }

    public boolean staffed() {
        return staffed;
    }

    public boolean burning() {
        return fire > 0;
    }

    /** Seconds it has burned. */
    public double fire() {
        return fire;
    }

    public boolean dousing() {
        return douse > 0;
    }

    public int count(Good good) {
        return stock.count(good);
    }

    /** Goods that went in and wait to be used, all kinds together; for a site, the goods brought. */
    public int stocked() {
        return stock.total();
    }

    public int out() {
        return out;
    }

    /** Whether a piece of work is in hand: the mill turns, the chimney smokes. */
    public boolean working() {
        return work > 0 && staffed || work > 0 && type.work() == Work.SETTLE;
    }

    /** How far the work in hand is, 0 to 1. */
    public double progress() {
        if (work <= 0 || type.recipe() == null) return 0;
        return 1 - work / type.recipe().seconds();
    }

    public int sheep() {
        return sheep;
    }

    public List<Wander> herd() {
        return List.copyOf(herd);
    }

    public double dog() {
        return dog;
    }

    public double dogX() {
        return dogX;
    }

    public double dogY() {
        return dogY;
    }

    /** The pasture of a sheep farm, the two left columns, in units: left, top, width, height. */
    public double[] pasture() {
        return new double[]{col * Field.TILE, row * Field.TILE, 2 * Field.TILE, type.height() * Field.TILE};
    }

    int coming(Good good) {
        return coming.getOrDefault(good, 0);
    }

    void come(Good good, int by) {
        int now = coming(good) + by;
        if (now <= 0) coming.remove(good);
        else coming.put(good, now);
    }
}
