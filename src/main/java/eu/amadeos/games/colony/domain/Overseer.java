package eu.amadeos.games.colony.domain;

import eu.amadeos.gameengine.road.RoadNetwork;
import eu.amadeos.gameengine.tile.Cell;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The computer as a settler, for the title screen and the tests: it builds the buildings of
 * {@code plan.txt} one after the other, where a building of the kind does best (a woodcutter in the forest,
 * a quarry by the rock), and connects each to the roads, with a flag every few tiles. It only uses the
 * player's commands, so what it does the player could do too.
 */
final class Overseer {
    /** It thinks every so many dispatches, and keeps this many sites open at once. */
    static final int EVERY = 4;
    static final int SITES = 2;

    private final Game game;
    /** Buildings to build before the plan goes on: what burned down, and a tower when the land is full. */
    final List<BuildingType> urgent = new ArrayList<>();
    int next;
    int thought;
    int failures;
    /** Towers built for the building {@link #next} because there was no room for it. */
    int towers;

    Overseer(Game game) {
        this.game = game;
    }

    // --8<-- [start:think]
    void think() {
        if (++thought % EVERY != 0 || game.phase != Game.Phase.PLAYING) return;
        long sites = game.buildings.values().stream().filter(b -> !b.done).count();
        if (sites >= SITES) return;
        List<BuildingType> plan = game.rules.plan().build();
        if (next >= plan.size()) next = plan.size() - 4;        // at the end, it builds the last ones again
        boolean first = !urgent.isEmpty();
        BuildingType type = first ? urgent.getFirst() : plan.get(next);
        Building store = game.home();
        if (store == null) return;
        for (var cost : type.cost().entrySet()) {
            if (game.stores(cost.getKey()) < cost.getValue()) return;   // it waits for the goods
        }
        if (place(type, store)) {
            if (first) urgent.removeFirst();
            else {
                next++;
                towers = 0;
            }
            failures = 0;
        } else if (++failures >= 3) {
            failures = 0;
            BuildingType tower = tower();
            if (!first && type != tower && tower != null && towers < 2) {
                urgent.addFirst(tower);                       // no room: more land first
                towers++;
            } else if (first) {
                urgent.removeFirst();
            } else {
                next++;
                towers = 0;
            }
        }
    }

    /** What burned down is built again. */
    void burned(BuildingType type) {
        if (!urgent.contains(type)) urgent.add(type);
    }

    private BuildingType tower() {
        for (BuildingType type : game.rules.buildings()) if (type.work() == Work.WATCH) return type;
        return null;
    }
    // --8<-- [end:think]

    private record Spot(int col, int row, double score) {
    }

    private boolean place(BuildingType type, Building store) {
        List<Spot> spots = new ArrayList<>();
        for (int row = 1; row < game.level.height() - 1; row++) {
            for (int col = 1; col < game.level.width() - 1; col++) {
                if (!game.land.inside(col, row)) continue;
                if (game.placement(type, col, row) != Game.Placement.OK || !margin(type, col, row)) continue;
                double score = score(type, col, row, store);
                if (!Double.isNaN(score)) spots.add(new Spot(col, row, score));
            }
        }
        spots.sort(Comparator.comparingDouble(Spot::score).thenComparingInt(Spot::row).thenComparingInt(Spot::col));
        for (Spot spot : spots.subList(0, Math.min(6, spots.size()))) {
            int before = game.nextId;
            if (game.found(type, spot.col(), spot.row()) != Game.Placement.OK) continue;
            Building site = game.buildings.get(before);
            if (site != null && connect(site.flag(), store)) return true;
            if (site != null) game.demolish(site);
        }
        return false;
    }

    /** A free ring round the house, so that houses never wall in a road. */
    private boolean margin(BuildingType type, int col, int row) {
        for (int r = row - 1; r <= row + type.height(); r++) {
            for (int c = col - 1; c <= col + type.width(); c++) {
                if (game.level.contains(c, r) && game.built[game.index(c, r)] >= 0) return false;
            }
        }
        return true;
    }

    // --8<-- [start:score]
    /** Lower is better: near the storehouse, and near what the worker needs. NaN where it cannot work. */
    private double score(BuildingType type, int col, int row, Building store) {
        Cell door = new Cell(col + type.flagColumn(), row + type.height());
        double home = Math.hypot(door.col() - store.col() - 1, door.row() - store.row() - 2);
        int r = type.radius();
        return switch (type.work()) {
            case FELL -> need(count(door, r, (c, w) -> game.tree(c, w) >= 1), 4, home - 0.8 * count(door, r,
                    (c, w) -> game.tree(c, w) >= 1));
            case PLANT -> need(count(door, r, game::free), 8, 1.5 * nearest(door, Work.FELL) + 0.3 * home);
            case QUARRY -> need(count(door, r, (c, w) -> game.stone(c, w) > 0), 3, home - count(door, r,
                    (c, w) -> game.stone(c, w) > 0));
            case FISH -> need(count(door, r, (c, w) -> game.fish(c, w) > 0), 4, home - 0.5 * count(door, r,
                    (c, w) -> game.fish(c, w) > 0));
            case FARM -> need(count(door, r, game::free), 8, home - 0.3 * count(door, r, game::free)
                    + 0.5 * count(door, r, (c, w) -> game.tree(c, w) >= 0));
            case WATCH -> -home;
            default -> home;
        };
    }
    // --8<-- [end:score]

    private static double need(int have, int least, double score) {
        return have < least ? Double.NaN : score;
    }

    private interface Tile {
        boolean test(int col, int row);
    }

    private int count(Cell door, int r, Tile test) {
        int count = 0;
        for (int dr = -r; dr <= r; dr++) {
            for (int dc = -r; dc <= r; dc++) {
                int c = door.col() + dc;
                int w = door.row() + dr;
                if (dc * dc + dr * dr <= r * r + r && game.level.contains(c, w) && test.test(c, w)) count++;
            }
        }
        return count;
    }

    private double nearest(Cell door, Work work) {
        double best = 30;
        for (Building b : game.buildings.values()) {
            if (b.type().work() == work) best = Math.min(best, Math.hypot(b.x() / Field.TILE - door.col(),
                    b.y() / Field.TILE - door.row()));
        }
        return best;
    }

    // --8<-- [start:connect]
    /** A road from the new flag to the nearest flag that leads to the storehouse, cut into short pieces. */
    private boolean connect(int flag, Building store) {
        RoadNetwork roads = game.roads;
        if (roads.connected(flag, store.flag())) return true;
        Cell from = roads.cell(flag);
        List<Integer> others = new ArrayList<>();
        for (int other : roads.flags()) {
            if (other != flag && roads.connected(other, store.flag())) others.add(other);
        }
        others.sort(Comparator.comparingInt((Integer other) -> manhattan(from, roads.cell(other))).thenComparing(o -> o));
        List<Cell> best = List.of();
        for (int other : others.subList(0, Math.min(8, others.size()))) {
            List<Cell> way = game.plan(flag, roads.cell(other));
            if (!way.isEmpty() && (best.isEmpty() || way.size() < best.size())) best = way;
        }
        if (best.isEmpty() || game.pave(flag, best) != Game.Paving.OK) return false;
        cut(best);
        return true;
    }
    // --8<-- [end:connect]

    /** Flags along a new road, until no piece is longer than the plan allows. */
    private void cut(List<Cell> cells) {
        int most = game.rules.plan().roads();
        boolean again = true;
        while (again) {
            again = false;
            for (Cell cell : cells) {
                RoadNetwork.Road road = game.roads.roadAt(cell);
                if (road == null || road.length() <= most) continue;
                for (int k : new int[]{road.length() / 2, road.length() / 2 - 1, road.length() / 2 + 1}) {
                    if (k < 2 || k > road.length() - 2) continue;
                    if (game.placeFlag(road.cells().get(k))) {
                        again = true;
                        break;
                    }
                }
                if (again) break;
            }
        }
    }

    private static int manhattan(Cell a, Cell b) {
        return Math.abs(a.col() - b.col()) + Math.abs(a.row() - b.row());
    }
}
