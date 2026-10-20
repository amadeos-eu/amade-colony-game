package eu.amadeos.games.colony.domain;

import eu.amadeos.gameengine.motion.Wander;
import eu.amadeos.gameengine.path.AStar;
import eu.amadeos.gameengine.random.SeededRandom;
import eu.amadeos.gameengine.road.RoadNetwork;
import eu.amadeos.gameengine.tile.Cell;
import eu.amadeos.gameengine.tile.Territory;
import eu.amadeos.gameengine.tile.TileMap;
import eu.amadeos.gameengine.time.Seasons;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * A settlement in one mission: the rules of the game, without pictures, sounds or a clock of its own. The
 * caller lets time pass with {@link #update}, in small steps, and asks what there is.
 *
 * <p>Everything a step does depends only on what was before it and on the game's own random numbers, so two
 * games with the same seed and the same commands are alike to the last bit. Lists are walked by number, the
 * order things were made in, never by a hash.
 *
 * <p>The economy in short: settlers without a job live in a storehouse. A building the player lays out is a
 * site; a builder comes, the goods of its cost are carried there, and it stands. Then a worker comes and
 * works: in the house (a recipe), or out in the land (trees, rock, water, fields). What a worker makes waits
 * at the flag of the house until a carrier takes it along the roads, flag by flag, to a building that needs
 * it, or to the nearest storehouse. Every road has its own carrier.
 */
public final class Game {
    public enum Phase { PLAYING, WON, LOST }

    /** Why a building cannot stand somewhere. */
    public enum Placement { OK, OUTSIDE, BLOCKED, FLAG }

    /** Why a road cannot be laid. */
    public enum Paving { OK, OUTSIDE, BLOCKED, FLAG, SHORT }

    final Level level;
    final Rules rules;
    final Mission mission;
    final SeededRandom random;
    /** The sheep's own random numbers, so that their grazing never changes what happens to the settlement. */
    SeededRandom herd;
    final RoadNetwork roads = new RoadNetwork();
    final Territory land;
    final Seasons seasons;
    final TileMap<Ground> ground;
    /** Per tile: the age of its tree, or -1; the age of its field, or -1, and the farm it belongs to. */
    final double[] tree;
    final double[] field;
    final int[] fieldFarm;
    final int[] stone;
    final int[] fish;
    /** The building on each tile, by number; -1 for none. */
    final int[] built;
    /** Tiles where a house burned down, and how long the ashes stay. */
    final Map<Integer, Double> ashes = new TreeMap<>();
    final Map<Integer, Building> buildings = new TreeMap<>();
    final Map<Integer, Person> people = new TreeMap<>();
    final Map<Integer, Ware> wares = new TreeMap<>();
    final List<Wolf> wolves = new ArrayList<>();
    final List<Event> events = new ArrayList<>();
    int settlers;
    double time;
    double dispatch;
    int turn;
    double fireTimer;
    double wolfTimer;
    double fishTimer;
    int nextId = 1;
    Phase phase = Phase.PLAYING;
    /** Counts every change of trees, fields, rock and ashes, for the pictures of the ground. */
    int landVersion;
    final Overseer overseer;

    /** A new game of {@code level}; with {@code computer}, the overseer builds the settlement. */
    public Game(Level level, Rules rules, long seed, boolean computer) {
        this.level = level;
        this.rules = rules;
        this.mission = level.mission();
        this.random = new SeededRandom(seed);
        this.herd = random.fork();
        this.land = new Territory(level.width(), level.height());
        this.seasons = new Seasons(mission.seasons());
        this.ground = level.grounds().copy();
        int size = level.width() * level.height();
        tree = new double[size];
        field = new double[size];
        fieldFarm = new int[size];
        stone = new int[size];
        fish = new int[size];
        built = new int[size];
        Arrays.fill(field, -1);
        Arrays.fill(fieldFarm, -1);
        Arrays.fill(built, -1);
        for (int row = 0; row < level.height(); row++) {
            for (int col = 0; col < level.width(); col++) {
                int i = index(col, row);
                Ground g = ground.get(col, row);
                tree[i] = g == Ground.FOREST ? Field.TREE_GROWS : -1;
                stone[i] = g == Ground.ROCK ? Field.STONE : 0;
                fish[i] = g == Ground.WATER ? Field.FISH : 0;
            }
        }
        BuildingType store = rules.storehouse();
        Cell at = level.store();
        Building house = put(store, at.col(), at.row());
        complete(house);
        for (Map.Entry<Good, Integer> entry : mission.start().entrySet()) house.stock.add(entry.getKey(), entry.getValue());
        settlers = mission.settlers();
        fireTimer = Field.FIRE_FIRST;
        wolfTimer = Field.WOLF_EVERY / 2;
        overseer = computer ? new Overseer(this) : null;
        events.clear();
    }

    /** For the archive: an empty game, filled from a save file. */
    Game(Level level, Rules rules, long seed, long state, boolean computer) {
        this.level = level;
        this.rules = rules;
        this.mission = level.mission();
        this.random = SeededRandom.resume(seed, state);
        this.herd = null;                       // the archive puts it where it stood
        this.land = new Territory(level.width(), level.height());
        this.seasons = new Seasons(mission.seasons());
        this.ground = level.grounds().copy();
        int size = level.width() * level.height();
        tree = new double[size];
        field = new double[size];
        fieldFarm = new int[size];
        stone = new int[size];
        fish = new int[size];
        built = new int[size];
        Arrays.fill(built, -1);
        overseer = computer ? new Overseer(this) : null;
    }

    int index(int col, int row) {
        return row * level.width() + col;
    }

    // ---- one step ----------------------------------------------------------------------------------

    // --8<-- [start:update]
    /** Lets {@code seconds} pass: the seasons, the land, the people, the houses, the storehouses' orders. */
    public void update(double seconds) {
        events.clear();
        if (phase != Phase.PLAYING) return;
        time += seconds;
        if (seasons.update(seconds) > 0) events.add(new Event.Season(seasons.season(), seasons.year()));
        boolean winter = winter();
        grow(seconds, winter);
        for (Person person : List.copyOf(people.values())) step(person, seconds);
        for (Building building : List.copyOf(buildings.values())) {
            if (buildings.containsKey(building.id())) step(building, seconds, winter);
        }
        dispatch -= seconds;
        if (dispatch <= 0) {
            dispatch += Field.DISPATCH;
            dispatch();
            if (overseer != null) overseer.think();
        }
        if (mission.fire()) fire(seconds);
        if (mission.wolves()) wolves(seconds);
        judge();
    }
    // --8<-- [end:update]

    public boolean winter() {
        return seasons.season() == 3;
    }

    /** Trees and fields grow outside winter; fish come back; ashes are blown away. */
    private void grow(double seconds, boolean winter) {
        if (!winter) {
            for (int i = 0; i < tree.length; i++) {
                if (tree[i] >= 0 && tree[i] < Field.TREE_GROWS) {
                    tree[i] = Math.min(Field.TREE_GROWS, tree[i] + seconds);
                    if (tree[i] >= Field.TREE_GROWS) landVersion++;
                }
                if (field[i] >= 0 && field[i] < Field.FIELD_GROWS) {
                    double before = field[i];
                    field[i] = Math.min(Field.FIELD_GROWS, field[i] + seconds);
                    if ((int) (before * 4 / Field.FIELD_GROWS) != (int) (field[i] * 4 / Field.FIELD_GROWS)) landVersion++;
                }
            }
        }
        fishTimer += seconds;
        if (fishTimer >= Field.FISH_BACK) {
            fishTimer -= Field.FISH_BACK;
            for (int i = 0; i < fish.length; i++) {
                if (ground.get(i % level.width(), i / level.width()) == Ground.WATER && fish[i] < Field.FISH) fish[i]++;
            }
        }
        if (!ashes.isEmpty()) {
            for (Map.Entry<Integer, Double> ash : List.copyOf(ashes.entrySet())) {
                double left = ash.getValue() - seconds;
                if (left <= 0) {
                    ashes.remove(ash.getKey());
                    landVersion++;
                } else {
                    ashes.put(ash.getKey(), left);
                }
            }
        }
    }

    // ---- people ------------------------------------------------------------------------------------

    private void step(Person person, double seconds) {
        switch (person.role()) {
            case CARRIER -> carrier(person, seconds);
            case BUILDER -> builder(person, seconds);
            case WORKER -> worker(person, seconds);
        }
    }

    // --8<-- [start:carrier]
    /**
     * A carrier and the road: wait in the middle, walk to a flag where a ware wants this road, carry it to
     * the other flag, and put it down there; into the house when the house's door is that flag. When the
     * other flag is full, a ware that waits there for this road is taken back in exchange.
     */
    private void carrier(Person p, double seconds) {
        RoadNetwork.Road road = roads.road(p.job);
        if (road == null) {
            dismiss(p);
            return;
        }
        double steps = Field.WALK * seconds;
        switch (p.task) {
            case GOING -> {
                if (p.walk(steps)) {
                    int end = roads.flagAt(p.path.getLast());
                    p.path = road.cells();
                    p.along = end == road.to() ? road.length() : 0;
                    p.to = p.along;
                    p.task = Person.Task.IDLE;
                }
            }
            case IDLE -> {
                Ware ware = waiting(road);
                if (ware != null) {
                    p.ware = ware.id();
                    p.to = ware.flag() == road.from() ? 0 : road.length();
                    p.task = Person.Task.FETCH;
                    reserved.add(ware.id());
                } else {
                    p.to = road.length() / 2.0;
                    p.walk(steps);
                }
            }
            case FETCH -> {
                if (!p.walk(steps)) return;
                int flag = p.to == 0 ? road.from() : road.to();
                Ware ware = wares.get(p.ware);
                reserved.remove(p.ware);
                if (ware != null && ware.flag == flag && wants(ware, road)) {
                    ware.flag = -1;
                    p.load = ware.good();
                    p.to = p.to == 0 ? road.length() : 0;
                    p.task = Person.Task.CARRY;
                } else {
                    p.ware = -1;
                    p.task = Person.Task.IDLE;
                }
            }
            case CARRY -> {
                if (!p.walk(steps)) return;
                int flag = p.to == 0 ? road.from() : road.to();
                Ware ware = wares.get(p.ware);
                Building to = ware == null ? null : buildings.get(ware.to);
                if (ware == null) {
                    drop(p);
                } else if (to != null && to.flag() == flag) {
                    deliver(ware, to);
                    drop(p);
                } else if (waitingAt(flag).size() < Field.FLAG_ROOM) {
                    ware.flag = flag;
                    drop(p);
                } else {
                    Ware back = waitingFor(flag, road);
                    if (back != null) {
                        ware.flag = flag;
                        back.flag = -1;
                        p.ware = back.id();
                        p.load = back.good();
                        p.to = p.to == 0 ? road.length() : 0;
                    }
                }
            }
            default -> {
            }
        }
    }
    // --8<-- [end:carrier]

    /** Wares a carrier is on the way to fetch: no other carrier takes them meanwhile. */
    final Set<Integer> reserved = new HashSet<>();

    private void drop(Person p) {
        p.ware = -1;
        p.load = null;
        p.task = Person.Task.IDLE;
    }

    /** The oldest ware at either end of {@code road} whose way leads over it. */
    private Ware waiting(RoadNetwork.Road road) {
        for (Ware ware : wares.values()) {
            if (ware.flag != road.from() && ware.flag != road.to()) continue;
            if (!reserved.contains(ware.id()) && wants(ware, road)) return ware;
        }
        return null;
    }

    private Ware waitingFor(int flag, RoadNetwork.Road road) {
        for (Ware ware : wares.values()) {
            if (ware.flag == flag && !reserved.contains(ware.id()) && wants(ware, road)) return ware;
        }
        return null;
    }

    /** Whether the next road of a ware at its flag is {@code road}. */
    private boolean wants(Ware ware, RoadNetwork.Road road) {
        Building to = buildings.get(ware.to);
        if (to == null || ware.flag < 0) return false;
        RoadNetwork.Road next = roads.next(ware.flag, to.flag());
        return next != null && next.id() == road.id();
    }

    /** The wares that wait at a flag, oldest first. */
    public List<Ware> waitingAt(int flag) {
        List<Ware> list = new ArrayList<>();
        for (Ware ware : wares.values()) if (ware.flag == flag) list.add(ware);
        return list;
    }

    private void deliver(Ware ware, Building to) {
        wares.remove(ware.id());
        reserved.remove(ware.id());
        to.come(ware.good(), -1);
        to.stock.add(ware.good(), 1);
    }

    // --8<-- [start:builder]
    /** A builder works as far as the goods brought allow: half the goods, half the house. */
    private void builder(Person p, double seconds) {
        Building site = buildings.get(p.job);
        if (site == null || site.done) {
            dismiss(p);
            return;
        }
        if (p.task == Person.Task.GOING) {
            if (p.walk(Field.WALK * seconds)) p.task = Person.Task.BUILD;
            return;
        }
        double brought = (double) site.stock.total() / site.type().costTotal();
        if (site.built < brought) {
            site.built = Math.min(brought, site.built + seconds / site.type().seconds());
            p.timer = 1;
        } else {
            p.timer = 0;
        }
        if (site.built >= 1 - 1e-9) {
            complete(site);
            dismiss(p);
            events.add(new Event.Built(site));
        }
    }
    // --8<-- [end:builder]

    /** A site becomes a house: the goods are used up, the house starts with its sheep or its land. */
    private void complete(Building b) {
        b.done = true;
        b.built = 1;
        for (Good good : Good.values()) b.stock.set(good, 0);
        b.coming.clear();
        b.builder = -1;
        if (b.type().work() == Work.STORE) land.claim(b.id(), b.col() + 1, b.row() + 1, b.type().radius());
        if (b.type().work() == Work.GRAZE) {
            for (int i = 0; i < Field.FIRST_SHEEP; i++) lamb(b);
        }
    }

    private void lamb(Building farm) {
        double[] pen = farm.pasture();
        Wander sheep = new Wander(pen[0] + pen[2] / 2, pen[1] + pen[3] / 2, Math.min(pen[2], pen[3]) / 2 - 5, 5);
        farm.herd.add(sheep);
        farm.sheep = farm.herd.size();
    }

    private void worker(Person p, double seconds) {
        Building b = buildings.get(p.job);
        if (b == null || !b.done) {
            dismiss(p);
            return;
        }
        double steps = Field.WALK * seconds;
        switch (p.task) {
            case GOING -> {
                if (p.walk(steps)) {
                    p.task = Person.Task.HOME;
                    b.staffed = true;
                    if (b.type().work() == Work.WATCH && !land.has(b.id())) {
                        land.claim(b.id(), b.col(), b.row(), b.type().radius());
                    }
                }
            }
            case HOME -> {
                if (!gatherer(b.type().work())) return;
                p.timer -= seconds;
                if (p.timer > 0 || b.out >= Field.OUT_ROOM || b.burning()) return;
                if (b.type().recipe() != null && enough(b.type().recipe().makes())) {
                    p.timer = 3;
                    return;
                }
                if (!setOut(p, b)) p.timer = 3;
            }
            case OUT -> {
                if (p.walk(steps)) {
                    p.task = Person.Task.AWAY;
                    p.timer = switch (b.type().work()) {
                        case FELL -> Field.FELL;
                        case PLANT -> Field.PLANT;
                        case QUARRY -> Field.BREAK;
                        case FISH -> Field.CATCH;
                        default -> field[index(p.spot.col(), p.spot.row())] >= Field.FIELD_GROWS ? Field.REAP : Field.SOW;
                    };
                }
            }
            case AWAY -> {
                p.timer -= seconds;
                if (p.timer > 0) return;
                p.load = gather(b, p.spot);
                p.spot = null;
                p.to = 0;
                p.task = Person.Task.BACK;
            }
            case BACK -> {
                if (p.walk(steps)) {
                    p.task = Person.Task.HOME;
                    p.timer = Field.REST;
                    if (p.load != null) {
                        b.out++;
                        events.add(new Event.Made(b, p.load));
                        p.load = null;
                    }
                }
            }
            default -> {
            }
        }
    }

    private static boolean gatherer(Work work) {
        return work == Work.FELL || work == Work.PLANT || work == Work.QUARRY || work == Work.FISH || work == Work.FARM;
    }

    // --8<-- [start:set-out]
    /**
     * A worker looks for the nearest work within the radius of the house that nobody else is going to, and
     * walks there: a grown tree, a free tile for a sapling, a rock with stone, water with fish, a ripe field
     * or one to sow. False when there is none.
     */
    private boolean setOut(Person p, Building b) {
        Set<Cell> taken = new HashSet<>();
        for (Person other : people.values()) if (other.spot != null) taken.add(other.spot);
        Cell door = roads.cell(b.flag());
        int r = b.type().radius();
        List<Cell> near = new ArrayList<>();
        for (int dr = -r; dr <= r; dr++) {
            for (int dc = -r; dc <= r; dc++) {
                if (dc * dc + dr * dr > r * r + r) continue;
                int col = door.col() + dc;
                int row = door.row() + dr;
                if (level.contains(col, row) && target(b, col, row) && !taken.contains(new Cell(col, row))) {
                    near.add(new Cell(col, row));
                }
            }
        }
        near.sort((a, c) -> {
            int da = dist2(a, door);
            int dc = dist2(c, door);
            return da != dc ? Integer.compare(da, dc) : a.row() != c.row() ? Integer.compare(a.row(), c.row())
                    : Integer.compare(a.col(), c.col());
        });
        for (Cell target : near.subList(0, Math.min(4, near.size()))) {
            Cell stand = standFor(b.type().work(), target);
            if (stand == null) continue;
            AStar.Path path = AStar.find(ground, door, stand, this::walkCost);
            if (!path.found()) continue;
            p.walkAlong(path.cells());
            p.spot = target;
            p.task = Person.Task.OUT;
            return true;
        }
        return false;
    }
    // --8<-- [end:set-out]

    private static int dist2(Cell a, Cell b) {
        int dc = a.col() - b.col();
        int dr = a.row() - b.row();
        return dc * dc + dr * dr;
    }

    /** Whether the tile is work for the house. */
    private boolean target(Building b, int col, int row) {
        int i = index(col, row);
        return switch (b.type().work()) {
            case FELL -> tree[i] >= Field.TREE_GROWS;
            case PLANT -> !winter() && free(col, row) && !nearDoor(col, row);
            case QUARRY -> stone[i] > 0;
            case FISH -> fish[i] > 0;
            case FARM -> fieldFarm[i] == b.id() && field[i] >= Field.FIELD_GROWS
                    || !winter() && fields(b) < Field.FIELDS && free(col, row) && !nearDoor(col, row)
                    && !ripeField(b);
            default -> false;
        };
    }

    /** A farmer reaps before sowing. */
    private boolean ripeField(Building farm) {
        for (int i = 0; i < field.length; i++) {
            if (fieldFarm[i] == farm.id() && field[i] >= Field.FIELD_GROWS) return true;
        }
        return false;
    }

    private int fields(Building farm) {
        int count = 0;
        for (int farmOf : fieldFarm) if (farmOf == farm.id()) count++;
        return count;
    }

    /** Next to a flag or a road: kept free for the way. */
    private boolean nearDoor(int col, int row) {
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                Cell cell = new Cell(col + dc, row + dr);
                if (roads.flagAt(cell) >= 0 || roads.roadAt(cell) != null) return true;
            }
        }
        return false;
    }

    /** Where the worker stands for the work: on the tile, or next to the rock or the water. */
    private Cell standFor(Work work, Cell target) {
        if (work != Work.QUARRY && work != Work.FISH) return target;
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                if (Math.abs(dr) + Math.abs(dc) != 1) continue;
                int col = target.col() + dc;
                int row = target.row() + dr;
                if (walkCost(col, row) < Double.POSITIVE_INFINITY) return new Cell(col, row);
            }
        }
        return null;
    }

    /** People walk over grass and forest, roads and flags, but not through houses. */
    double walkCost(int col, int row) {
        if (!level.contains(col, row)) return Double.POSITIVE_INFINITY;
        if (!ground.get(col, row).walkable()) return Double.POSITIVE_INFINITY;
        if (built[index(col, row)] >= 0) return Double.POSITIVE_INFINITY;
        return roads.roadAt(new Cell(col, row)) != null || roads.flagAt(new Cell(col, row)) >= 0 ? 1 : 1.5;
    }

    /** The work done out there; what the worker carries home. */
    private Good gather(Building b, Cell spot) {
        int i = index(spot.col(), spot.row());
        landVersion++;
        switch (b.type().work()) {
            case FELL -> {
                if (tree[i] < Field.TREE_GROWS) return null;
                tree[i] = -1;
                events.add(new Event.Felled(spot.col(), spot.row()));
                return Good.LOG;
            }
            case PLANT -> {
                if (free(spot.col(), spot.row())) tree[i] = 0;
                return null;
            }
            case QUARRY -> {
                if (stone[i] <= 0) return null;
                if (--stone[i] == 0) ground.set(spot.col(), spot.row(), Ground.GRASS);
                return Good.STONE;
            }
            case FISH -> {
                if (fish[i] <= 0) return null;
                fish[i]--;
                return Good.FISH;
            }
            case FARM -> {
                if (fieldFarm[i] == b.id() && field[i] >= Field.FIELD_GROWS) {
                    field[i] = -1;
                    fieldFarm[i] = -1;
                    return Good.GRAIN;
                }
                if (free(spot.col(), spot.row())) {
                    field[i] = 0;
                    fieldFarm[i] = b.id();
                }
                return null;
            }
            default -> {
                return null;
            }
        }
    }

    /** Back to the storehouse: the person has no job any more and is only counted there. */
    private void dismiss(Person p) {
        people.remove(p.id());
        if (p.ware >= 0) {
            Ware ware = wares.get(p.ware);
            reserved.remove(p.ware);
            RoadNetwork.Road road = roads.road(p.job);
            if (ware != null && ware.flag < 0) {
                // The road is gone: the ware lies at the nearer flag that is left, or is lost.
                int flag = road == null ? -1 : p.along <= road.length() / 2.0 ? road.from() : road.to();
                if (flag < 0) flag = nearestFlag(p.x(), p.y());
                if (flag >= 0) ware.flag = flag;
                else lose(ware);
            }
        }
        Building b = buildings.get(p.job);
        if (b != null && p.role() == Person.Role.BUILDER && b.builder == p.id()) b.builder = -1;
        if (b != null && p.role() == Person.Role.WORKER && b.worker == p.id()) {
            b.worker = -1;
            b.staffed = false;
        }
    }

    private int nearestFlag(double x, double y) {
        int best = -1;
        double bestD = 2.5 * Field.TILE;
        for (int flag : roads.flags()) {
            Cell cell = roads.cell(flag);
            double d = Math.hypot((cell.col() + 0.5) * Field.TILE - x, (cell.row() + 0.5) * Field.TILE - y);
            if (d < bestD) {
                bestD = d;
                best = flag;
            }
        }
        return best;
    }

    private void lose(Ware ware) {
        wares.remove(ware.id());
        reserved.remove(ware.id());
        Building to = buildings.get(ware.to);
        if (to != null) to.come(ware.good(), -1);
    }

    // ---- houses ------------------------------------------------------------------------------------

    // --8<-- [start:house]
    /** A house works its recipe, keeps its sheep, and puts what it made out at its flag. */
    private void step(Building b, double seconds, boolean winter) {
        if (!b.done) return;
        Work work = b.type().work();
        if (work == Work.GRAZE) graze(b, seconds, winter);
        boolean timed = work == Work.MAKE || work == Work.GRAZE || work == Work.SETTLE;
        if (timed && !b.burning() && (b.staffed || work == Work.SETTLE)) {
            if (b.work > 0) {
                double pace = work == Work.GRAZE ? (double) b.sheep / Field.SHEEP : 1;
                b.work -= seconds * pace;
                if (b.work <= 0) {
                    b.work = 0;
                    if (work == Work.SETTLE) {
                        settlers++;
                        events.add(new Event.Settled(b));
                    } else {
                        b.out++;
                        events.add(new Event.Made(b, b.type().recipe().makes()));
                    }
                }
            }
            boolean room = work == Work.SETTLE || b.out < Field.OUT_ROOM && !enough(b.type().recipe().makes());
            if (b.work == 0 && room && (work != Work.GRAZE || b.sheep > 0) && b.type().recipe().take(b.stock) != null) {
                b.work = b.type().recipe().seconds();
            }
        }
        if (b.out > 0 && waitingAt(b.flag()).size() < Field.FLAG_ROOM) {
            b.out--;
            Ware ware = new Ware(nextId++, b.type().recipe().makes(), -1, b.flag());
            wares.put(ware.id(), ware);
            route(ware, b);
        }
    }
    // --8<-- [end:house]

    private void graze(Building farm, double seconds, boolean winter) {
        for (Wander sheep : farm.herd) sheep.update(seconds, herd);
        if (farm.dog > 0) farm.dog = Math.max(0, farm.dog - seconds);
        if (!winter && farm.sheep < Field.SHEEP && farm.staffed) {
            farm.lamb += seconds;
            if (farm.lamb >= Field.LAMB) {
                farm.lamb = 0;
                lamb(farm);
                events.add(new Event.Lamb(farm));
            }
        }
    }

    // ---- the storehouses' orders -------------------------------------------------------------------

    /** Whether the storehouses hold plenty of {@code good}: its makers rest, the stores serve who needs it. */
    public boolean enough(Good good) {
        return stores(good) >= Field.ENOUGH;
    }

    // --8<-- [start:demand]
    /** How many more of {@code good} a building wants brought: a site its cost, a house its inputs. */
    public int want(Building b, Good good) {
        if (b.burning()) return 0;
        if (!b.done) return b.type().cost().getOrDefault(good, 0) - b.stock.count(good) - b.coming(good);
        if (b.type().recipe() == null || !b.type().recipe().needs().contains(good)) return 0;
        return b.type().keep() - b.stock.count(good) - b.coming(good);
    }
    // --8<-- [end:demand]

    // --8<-- [start:route]
    /**
     * Where a ware goes: to the nearest building on the roads that wants it, else to the nearest storehouse;
     * nowhere while no road leads to one.
     */
    private void route(Ware ware, Building from) {
        Building best = null;
        int bestD = Integer.MAX_VALUE;
        for (Building b : buildings.values()) {
            if (b == from || b.type().work() == Work.STORE || want(b, ware.good()) <= 0) continue;
            int d = roads.distance(ware.flag, b.flag());
            if (d < bestD) {
                bestD = d;
                best = b;
            }
        }
        if (best == null) best = nearestStore(ware.flag, null);
        ware.to = best == null ? -1 : best.id();
        if (best != null) best.come(ware.good(), 1);
    }
    // --8<-- [end:route]

    /** The nearest storehouse on the roads from {@code flag}; with {@code good}, one that has some. */
    private Building nearestStore(int flag, Good good) {
        Building best = null;
        int bestD = Integer.MAX_VALUE;
        for (Building b : buildings.values()) {
            if (b.type().work() != Work.STORE || !b.done || good != null && b.stock.count(good) == 0) continue;
            int d = roads.distance(flag, b.flag());
            if (d < bestD) {
                bestD = d;
                best = b;
            }
        }
        return best;
    }

    // --8<-- [start:dispatch]
    /**
     * Twice a second the storehouses look at what is missing: wares that lost their way get a new one, the
     * buildings that want goods get them from the nearest store that has them, and roads, sites and houses
     * without a person get a settler. The buildings take turns, so none is always served first.
     */
    void dispatch() {
        for (Ware ware : List.copyOf(wares.values())) {
            if (ware.flag < 0) continue;
            Building to = buildings.get(ware.to);
            if (to != null && to.flag() == ware.flag) {
                deliver(ware, to);
            } else if (to == null || !roads.connected(ware.flag, to.flag())) {
                if (to != null) to.come(ware.good(), -1);
                route(ware, null);
            }
        }
        List<Building> all = new ArrayList<>(buildings.values());
        int start = all.isEmpty() ? 0 : turn++ % all.size();
        for (int k = 0; k < all.size(); k++) {
            Building b = all.get((start + k) % all.size());
            if (b.type().work() == Work.STORE) continue;
            for (Good good : Good.values()) {
                if (want(b, good) <= 0) continue;
                Building store = nearestStore(b.flag(), good);
                if (store == null || waitingAt(store.flag()).size() >= Field.FLAG_ROOM) continue;
                store.stock.take(good, 1);
                Ware ware = new Ware(nextId++, good, b.id(), store.flag());
                wares.put(ware.id(), ware);
                b.come(good, 1);
            }
        }
        staff();
    }
    // --8<-- [end:dispatch]

    /** Settlers without a job take one: a road first, then a site, then a house. */
    private void staff() {
        Set<Integer> carried = new HashSet<>();
        for (Person p : people.values()) if (p.role() == Person.Role.CARRIER) carried.add(p.job);
        for (RoadNetwork.Road road : roads.roads()) {
            if (idle() == 0) return;
            if (carried.contains(road.id())) continue;
            Building store = nearestStore(road.from(), null);
            if (store == null) continue;
            int end = roads.distance(store.flag(), road.from()) <= roads.distance(store.flag(), road.to())
                    ? road.from() : road.to();
            send(Person.Role.CARRIER, road.id(), store, end);
        }
        for (Building b : buildings.values()) {
            if (idle() == 0) return;
            Building store = nearestStore(b.flag(), null);
            if (store == null || b == store) continue;
            if (!b.done && b.builder < 0) {
                b.builder = send(Person.Role.BUILDER, b.id(), store, b.flag()).id();
            } else if (b.done && b.type().manned() && b.worker < 0) {
                if (b.type().tool()) {
                    Building tools = nearestStore(b.flag(), Good.TOOLS);
                    if (tools == null) continue;
                    tools.stock.take(Good.TOOLS, 1);
                    store = tools;
                }
                b.worker = send(Person.Role.WORKER, b.id(), store, b.flag()).id();
            }
        }
    }

    private Person send(Person.Role role, int job, Building store, int flag) {
        Person p = new Person(nextId++, role, job);
        p.walkAlong(cellsAlong(store.flag(), flag));
        people.put(p.id(), p);
        return p;
    }

    /** The tiles from one flag to another along the roads. */
    List<Cell> cellsAlong(int from, int to) {
        List<Cell> cells = new ArrayList<>();
        cells.add(roads.cell(from));
        int at = from;
        for (RoadNetwork.Road road : roads.way(from, to)) {
            List<Cell> part = road.from(at);
            cells.addAll(part.subList(1, part.size()));
            at = road.other(at);
        }
        return cells;
    }

    /** Settlers without a job, who live in the storehouses. */
    public int idle() {
        return settlers - people.size();
    }

    // ---- fire and wolves ---------------------------------------------------------------------------

    // --8<-- [start:fire]
    /**
     * Now and then a house catches fire, more often in summer. A manned well within reach puts it out; else
     * the house burns down after a while, and what was in it with it.
     */
    private void fire(double seconds) {
        fireTimer -= seconds;
        if (fireTimer <= 0) {
            double every = seasons.season() == 1 ? Field.FIRE_SUMMER : Field.FIRE_ELSE;
            fireTimer = every * (0.5 + random.nextDouble());
            List<Building> houses = new ArrayList<>();
            for (Building b : buildings.values()) {
                if (b.done && !b.burning() && b.type().work() != Work.STORE && b.type().work() != Work.WATCH) houses.add(b);
            }
            if (!houses.isEmpty()) {
                Building b = houses.get(random.nextInt(houses.size()));
                b.fire = 1e-6;
                events.add(new Event.Burning(b));
            }
        }
        for (Building b : List.copyOf(buildings.values())) {
            if (!b.burning()) continue;
            b.fire += seconds;
            if (b.douse > 0) {
                b.douse -= seconds;
                if (b.douse <= 0) {
                    b.douse = 0;
                    b.fire = 0;
                    events.add(new Event.Doused(b));
                }
            } else if (well(b) != null) {
                b.douse = Field.DOUSE;
            } else if (b.fire >= Field.BURNS) {
                remove(b);
                for (int r = b.row(); r < b.row() + b.type().height(); r++) {
                    for (int c = b.col(); c < b.col() + b.type().width(); c++) ashes.put(index(c, r), 60.0);
                }
                landVersion++;
                events.add(new Event.Burned(b));
                if (overseer != null) overseer.burned(b.type());
            }
        }
    }
    // --8<-- [end:fire]

    /** A manned well within reach of a house. */
    public Building well(Building house) {
        for (Building b : buildings.values()) {
            if (b == house || !b.done || !b.staffed || b.burning()) continue;
            if (b.type().work() != Work.MAKE || b.type().recipe().makes() != Good.WATER) continue;
            if (Math.hypot(b.x() - house.x(), b.y() - house.y()) <= Field.WELL_REACH * Field.TILE) return b;
        }
        return null;
    }

    // --8<-- [start:wolves]
    /**
     * In autumn and winter wolves leave their dens for the nearest pasture; in winter two at a time. The dog
     * drives one off, then needs a rest; the watchman of a tower nearby blows the horn at the next. A wolf
     * that gets through takes a sheep.
     */
    private void wolves(double seconds) {
        int season = seasons.season();
        if (season >= 2) {
            wolfTimer -= seconds;
            if (wolfTimer <= 0) {
                wolfTimer = Field.WOLF_EVERY * (0.7 + 0.6 * random.nextDouble());
                hunt(season == 3 ? 2 : 1);
            }
        }
        for (Wolf wolf : List.copyOf(wolves)) {
            Building farm = buildings.get(wolf.farm);
            double speed = Field.WOLF_SPEED * seconds * (wolf.fleeing ? 1.4 : 1);
            if (wolf.fleeing || farm == null) {
                wolf.fleeing = true;
                wolf.along -= speed;
                if (wolf.along <= 0) wolves.remove(wolf);
                continue;
            }
            wolf.along = Math.min(wolf.path.size() - 1, wolf.along + speed);
            double[] pen = farm.pasture();
            double dx = wolf.x() - (pen[0] + pen[2] / 2);
            double dy = wolf.y() - (pen[1] + pen[3] / 2);
            if (Math.hypot(dx, dy) > Field.DOG_REACH * Field.TILE && wolf.along < wolf.path.size() - 1) continue;
            wolf.fleeing = true;
            if (farm.staffed && farm.dog <= 0) {
                farm.dog = Field.DOG_REST;
                farm.dogX = wolf.x();
                farm.dogY = wolf.y();
                for (Wander sheep : farm.herd) sheep.flee(wolf.x(), wolf.y());
                events.add(new Event.Barked(farm, false));
            } else if (watched(farm)) {
                events.add(new Event.Barked(farm, true));
            } else if (!farm.herd.isEmpty()) {
                farm.herd.removeLast();
                farm.sheep = farm.herd.size();
                for (Wander sheep : farm.herd) sheep.flee(wolf.x(), wolf.y());
                events.add(new Event.SheepTaken(farm));
            }
        }
    }
    // --8<-- [end:wolves]

    private void hunt(int count) {
        Building target = null;
        Cell from = null;
        int best = Field.WOLF_SMELL * Field.WOLF_SMELL + 1;
        for (Cell den : level.dens()) {
            for (Building b : buildings.values()) {
                if (!b.done || b.type().work() != Work.GRAZE || b.sheep == 0) continue;
                int d = dist2(den, new Cell(b.col(), b.row()));
                if (d < best) {
                    best = d;
                    target = b;
                    from = den;
                }
            }
        }
        if (target == null) return;
        Cell pen = new Cell(target.col() + 1, target.row() + target.type().height() - 1);
        Cell outside = standOutside(target);
        if (outside == null) return;
        AStar.Path path = AStar.find(ground, from, outside, (c, r) -> level.contains(c, r) && ground.get(c, r).walkable()
                && (built[index(c, r)] < 0) ? 1 : Double.POSITIVE_INFINITY);
        if (!path.found()) return;
        List<Cell> cells = new ArrayList<>(path.cells());
        cells.add(pen);
        for (int i = 0; i < count; i++) wolves.add(new Wolf(target.id(), cells, -1.2 * i));
        events.add(new Event.Wolf((from.col() + 0.5) * Field.TILE, (from.row() + 0.5) * Field.TILE));
    }

    /** A walkable tile by the fence of the pasture, the nearest to its top left. */
    private Cell standOutside(Building farm) {
        for (int r = farm.row() - 1; r <= farm.row() + farm.type().height(); r++) {
            for (int c = farm.col() - 1; c <= farm.col() + 2; c++) {
                if (farm.contains(c, r)) continue;
                if (walkCost(c, r) < Double.POSITIVE_INFINITY) return new Cell(c, r);
            }
        }
        return null;
    }

    /** A manned tower close to the pasture. */
    private boolean watched(Building farm) {
        for (Building b : buildings.values()) {
            if (b.done && b.staffed && b.type().work() == Work.WATCH
                    && Math.hypot(b.x() - farm.x(), b.y() - farm.y()) <= 6 * Field.TILE) return true;
        }
        return false;
    }

    // ---- winning -----------------------------------------------------------------------------------

    private void judge() {
        if (phase != Phase.PLAYING) return;
        if (settlers >= mission.goal() && stored()) {
            phase = Phase.WON;
            events.add(new Event.Over(true));
        } else if (mission.years() > 0 && seasons.year() >= mission.years()) {
            phase = Phase.LOST;
            events.add(new Event.Over(false));
        }
    }

    private boolean stored() {
        for (Map.Entry<Good, Integer> goal : mission.goods().entrySet()) {
            if (stores(goal.getKey()) < goal.getValue()) return false;
        }
        return true;
    }

    /** All of {@code good} in the storehouses. */
    public int stores(Good good) {
        int count = 0;
        for (Building b : buildings.values()) if (b.type().work() == Work.STORE) count += b.stock.count(good);
        return count;
    }

    // ---- the player's commands ---------------------------------------------------------------------

    // --8<-- [start:placement]
    /** Whether {@code type} may stand with its top left tile at {@code col}, {@code row}. */
    public Placement placement(BuildingType type, int col, int row) {
        for (int r = row; r < row + type.height(); r++) {
            for (int c = col; c < col + type.width(); c++) {
                if (!level.contains(c, r) || !land.inside(c, r)) return Placement.OUTSIDE;
                if (!free(c, r)) return Placement.BLOCKED;
            }
        }
        Cell door = new Cell(col + type.flagColumn(), row + type.height());
        if (!level.contains(door.col(), door.row()) || !land.inside(door.col(), door.row())) return Placement.OUTSIDE;
        int flag = roads.flagAt(door);
        if (flag >= 0) {
            for (Building b : buildings.values()) if (b.flag() == flag) return Placement.FLAG;
            return Placement.OK;
        }
        if (roads.roadAt(door) != null) return flagSpace(door) ? Placement.OK : Placement.FLAG;
        return free(door.col(), door.row()) && flagSpace(door) ? Placement.OK : Placement.FLAG;
    }
    // --8<-- [end:placement]

    /** Lays out a site with its flag; the storehouses send a builder and the goods. */
    public Placement found(BuildingType type, int col, int row) {
        if (!type.buildable()) throw new IllegalArgumentException(type.id() + " is not built");
        Placement placement = placement(type, col, row);
        if (placement != Placement.OK) return placement;
        Building b = put(type, col, row);
        events.add(new Event.Founded(b));
        return Placement.OK;
    }

    private Building put(BuildingType type, int col, int row) {
        Cell door = new Cell(col + type.flagColumn(), row + type.height());
        int flag = roads.flagAt(door);
        if (flag < 0) flag = roads.roadAt(door) != null ? roads.split(door) : roads.addFlag(door);
        Building b = new Building(nextId++, type, col, row, flag);
        buildings.put(b.id(), b);
        for (int r = row; r < row + type.height(); r++) {
            for (int c = col; c < col + type.width(); c++) {
                built[index(c, r)] = b.id();
                ashes.remove(index(c, r));
            }
        }
        landVersion++;
        return b;
    }

    /** Free to build on, or to lay a road over: grass or forest without a tree, a field, a house, a flag or a road. */
    public boolean free(int col, int row) {
        if (!level.contains(col, row) || !ground.get(col, row).walkable()) return false;
        int i = index(col, row);
        if (tree[i] >= 0 || field[i] >= 0 || built[i] >= 0) return false;
        Cell cell = new Cell(col, row);
        return roads.flagAt(cell) < 0 && roads.roadAt(cell) == null;
    }

    /** No other flag on the tiles around: a road between two flags is two steps at least. */
    public boolean flagSpace(Cell cell) {
        for (int dr = -Field.FLAG_GAP + 1; dr < Field.FLAG_GAP; dr++) {
            for (int dc = -Field.FLAG_GAP + 1; dc < Field.FLAG_GAP; dc++) {
                if ((dr != 0 || dc != 0) && roads.flagAt(new Cell(cell.col() + dc, cell.row() + dr)) >= 0) return false;
            }
        }
        return true;
    }

    // --8<-- [start:pave]
    /**
     * Whether a road may run from flag {@code from} over {@code cells}: one step at a time over free land
     * inside the border, to a flag, to a road that gets a flag there, or to free land where a flag can stand.
     */
    public Paving canPave(int from, List<Cell> cells) {
        if (!roads.hasFlag(from) || cells.isEmpty() || !cells.getFirst().equals(roads.cell(from))) return Paving.BLOCKED;
        if (cells.size() < 3) return Paving.SHORT;
        Set<Cell> seen = new HashSet<>();
        for (int i = 0; i < cells.size(); i++) {
            Cell cell = cells.get(i);
            if (!seen.add(cell)) return Paving.BLOCKED;
            if (i > 0) {
                Cell before = cells.get(i - 1);
                if (Math.abs(before.col() - cell.col()) + Math.abs(before.row() - cell.row()) != 1) return Paving.BLOCKED;
            }
            if (!level.contains(cell.col(), cell.row()) || !land.inside(cell.col(), cell.row())) return Paving.OUTSIDE;
            if (i > 0 && i < cells.size() - 1 && !free(cell.col(), cell.row())) return Paving.BLOCKED;
        }
        return end(from, cells.getLast()) ? Paving.OK : Paving.FLAG;
    }

    public Paving pave(int from, List<Cell> cells) {
        Paving paving = canPave(from, cells);
        if (paving != Paving.OK) return paving;
        Cell last = cells.getLast();
        int to = roads.flagAt(last);
        if (to < 0) to = roads.roadAt(last) != null ? roads.split(last) : roads.addFlag(last);
        roads.connect(from, to, cells);
        events.add(new Event.Paved(cells.size() - 1));
        return Paving.OK;
    }
    // --8<-- [end:pave]

    /** Whether a road from {@code from} may end on {@code cell}. */
    private boolean end(int from, Cell cell) {
        int flag = roads.flagAt(cell);
        if (flag >= 0) return flag != from;
        if (!land.inside(cell.col(), cell.row())) return false;
        if (roads.roadAt(cell) != null) return flagSpace(cell);
        return free(cell.col(), cell.row()) && flagSpace(cell);
    }

    // --8<-- [start:route-road]
    /** The shortest road the player could lay from a flag to a tile; empty when there is none. */
    public List<Cell> plan(int from, Cell to) {
        if (!roads.hasFlag(from) || !level.contains(to.col(), to.row()) || !end(from, to)) return List.of();
        Cell start = roads.cell(from);
        AStar.Path path = AStar.find(ground, start, to, (c, r) -> {
            if (c == to.col() && r == to.row()) return 1;
            return land.inside(c, r) && free(c, r) ? 1 : Double.POSITIVE_INFINITY;
        });
        return path.found() && path.cells().size() >= 3 ? path.cells() : List.of();
    }
    // --8<-- [end:route-road]

    /** Puts a flag on a tile: on a road, which becomes two; or on free land, to start a road from. */
    public boolean placeFlag(Cell cell) {
        if (!level.contains(cell.col(), cell.row()) || !land.inside(cell.col(), cell.row()) || !flagSpace(cell)) return false;
        if (roads.flagAt(cell) >= 0) return false;
        if (roads.roadAt(cell) != null) {
            RoadNetwork.Road road = roads.roadAt(cell);
            int at = road.cells().indexOf(cell);
            List<Person> carriers = new ArrayList<>();
            for (Person p : people.values()) if (p.role() == Person.Role.CARRIER && p.job == road.id()) carriers.add(p);
            int flag = roads.split(cell);
            // The road's carrier goes home; a ware in hand is put down at the nearest of the three flags.
            // The storehouse sends a carrier to each part.
            for (Person carrier : carriers) {
                Ware ware = carrier.ware >= 0 ? wares.get(carrier.ware) : null;
                if (ware != null && ware.flag < 0) {
                    double a = carrier.task == Person.Task.GOING ? 0 : carrier.along;
                    ware.flag = a < at / 2.0 ? road.from() : a > (at + road.length()) / 2.0 ? road.to() : flag;
                }
                reserved.remove(carrier.ware);
                carrier.ware = -1;
                people.remove(carrier.id());
            }
        } else if (free(cell.col(), cell.row())) {
            roads.addFlag(cell);
        } else {
            return false;
        }
        landVersion++;
        return true;
    }

    /** Pulls a building down; its flag stays. The storehouse cannot be pulled down. */
    public boolean demolish(Building b) {
        if (b == null || b.type().work() == Work.STORE || !buildings.containsKey(b.id())) return false;
        remove(b);
        events.add(new Event.Demolished(b));
        return true;
    }

    private void remove(Building b) {
        buildings.remove(b.id());
        for (int r = b.row(); r < b.row() + b.type().height(); r++) {
            for (int c = b.col(); c < b.col() + b.type().width(); c++) built[index(c, r)] = -1;
        }
        for (int i = 0; i < fieldFarm.length; i++) {
            if (fieldFarm[i] == b.id()) {
                fieldFarm[i] = -1;
                field[i] = -1;
            }
        }
        for (Person p : List.copyOf(people.values())) {
            if (p.role() != Person.Role.CARRIER && p.job == b.id()) dismiss(p);
        }
        if (land.has(b.id())) land.release(b.id());
        landVersion++;
    }

    /** Takes a road away; its carrier goes home and puts the load down at the nearer flag. */
    public boolean removeRoad(RoadNetwork.Road road) {
        if (road == null || roads.road(road.id()) == null) return false;
        for (Person p : List.copyOf(people.values())) {
            if (p.role() == Person.Role.CARRIER && p.job == road.id()) dismiss(p);
        }
        roads.remove(road.id());
        landVersion++;
        return true;
    }

    /** Takes a flag away with its roads and the wares at it; not the flag of a building. */
    public boolean removeFlag(int flag) {
        if (!roads.hasFlag(flag)) return false;
        for (Building b : buildings.values()) if (b.flag() == flag) return false;
        for (RoadNetwork.Road road : roads.roads(flag)) removeRoad(road);
        for (Ware ware : waitingAt(flag)) lose(ware);
        roads.removeFlag(flag);
        landVersion++;
        return true;
    }

    // ---- what there is -----------------------------------------------------------------------------

    public Level level() {
        return level;
    }

    public Rules rules() {
        return rules;
    }

    public Mission mission() {
        return mission;
    }

    public RoadNetwork roads() {
        return roads;
    }

    public Territory land() {
        return land;
    }

    public Seasons seasons() {
        return seasons;
    }

    public Ground ground(int col, int row) {
        return ground.get(col, row);
    }

    public boolean contains(int col, int row) {
        return level.contains(col, row);
    }

    /** How far the tree on a tile has grown, 0 to 1; -1 without a tree. */
    public double tree(int col, int row) {
        double age = tree[index(col, row)];
        return age < 0 ? -1 : age / Field.TREE_GROWS;
    }

    /** How far the field on a tile has grown, 0 to 1; -1 without a field. */
    public double field(int col, int row) {
        double age = field[index(col, row)];
        return age < 0 ? -1 : age / Field.FIELD_GROWS;
    }

    public int stone(int col, int row) {
        return stone[index(col, row)];
    }

    public int fish(int col, int row) {
        return fish[index(col, row)];
    }

    public boolean ash(int col, int row) {
        return ashes.containsKey(index(col, row));
    }

    public int landVersion() {
        return landVersion;
    }

    public List<Building> buildings() {
        return List.copyOf(buildings.values());
    }

    public Building building(int id) {
        return buildings.get(id);
    }

    /** The building on a tile, or {@code null}. */
    public Building buildingAt(int col, int row) {
        if (!level.contains(col, row)) return null;
        return buildings.get(built[index(col, row)]);
    }

    public List<Person> people() {
        return List.copyOf(people.values());
    }

    public Person person(int id) {
        return people.get(id);
    }

    public List<Ware> wares() {
        return List.copyOf(wares.values());
    }

    public Ware ware(int id) {
        return wares.get(id);
    }

    public List<Wolf> wolves() {
        return List.copyOf(wolves);
    }

    /** What happened in the last step. */
    public List<Event> events() {
        return List.copyOf(events);
    }

    /** Everyone: in the storehouses, on the roads, at work. */
    public int settlers() {
        return settlers;
    }

    public double time() {
        return time;
    }

    public Phase phase() {
        return phase;
    }

    public boolean computer() {
        return overseer != null;
    }

    /** The first storehouse: where the camera starts. */
    public Building home() {
        for (Building b : buildings.values()) if (b.type().work() == Work.STORE) return b;
        return null;
    }

    /** The carrier of a road, or {@code null}. */
    public Person carrier(RoadNetwork.Road road) {
        for (Person p : people.values()) {
            if (p.role() == Person.Role.CARRIER && p.job == road.id()) return p;
        }
        return null;
    }
}
