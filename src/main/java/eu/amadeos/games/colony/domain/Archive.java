package eu.amadeos.games.colony.domain;

import eu.amadeos.gameengine.motion.Wander;
import eu.amadeos.gameengine.random.SeededRandom;
import eu.amadeos.gameengine.road.RoadNetwork;
import eu.amadeos.gameengine.save.Snapshot;
import eu.amadeos.gameengine.tile.Cell;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

/**
 * A whole settlement as the values of a save file, and back: the land with its trees, fields, rock and fish,
 * the flags and roads, every building with its stock and its sheep, every person with the way they walk,
 * every ware, the wolves, the seasons, the computer's plan and both random generators where they stood. The
 * map itself is not saved, only its mission's number: the map comes from the game, as at the start.
 *
 * <p>Loaded, the settlement goes on exactly as it would have: {@code GameTest} plays on, saves, loads, plays
 * both on and finds them alike to the last bit.
 */
public final class Archive {
    /** Changes when the values change their meaning; an older file is not read. */
    public static final int VERSION = 1;

    private Archive() {
    }

    // --8<-- [start:write]
    public static Map<String, String> write(Game game) {
        Snapshot.Writer w = Snapshot.writer();
        w.put("mission", game.level.number()).put("computer", game.computer());
        w.put("time", game.time).put("dispatch", game.dispatch).put("turn", game.turn).put("next", game.nextId);
        w.put("fire", game.fireTimer).put("wolf", game.wolfTimer).put("fish", game.fishTimer);
        w.put("phase", game.phase).put("settlers", game.settlers).put("land", game.landVersion);
        w.put("seed", game.random.seed()).put("random", game.random.state());
        w.put("herdSeed", game.herd.seed()).put("herd", game.herd.state());
        w.put("season", game.seasons.time());
        StringBuilder ground = new StringBuilder();
        for (int row = 0; row < game.level.height(); row++) {
            for (int col = 0; col < game.level.width(); col++) ground.append(game.ground.get(col, row).name().charAt(0));
        }
        w.put("ground", ground.toString());
        w.put("trees", doubles(game.tree)).put("fields", doubles(game.field)).put("farms", ints(game.fieldFarm));
        w.put("stone", ints(game.stone)).put("fishes", ints(game.fish));
        w.list("ash");
        for (Map.Entry<Integer, Double> ash : game.ashes.entrySet()) {
            w.add("ash").put("tile", ash.getKey()).put("left", ash.getValue());
        }
        RoadNetwork roads = game.roads;
        w.put("nextFlag", roads.nextFlag()).put("nextRoad", roads.nextRoad());
        w.list("flag");
        for (int flag : roads.flags()) {
            Cell cell = roads.cell(flag);
            w.add("flag").put("id", flag).put("col", cell.col()).put("row", cell.row());
        }
        w.list("road");
        for (RoadNetwork.Road road : roads.roads()) {
            w.add("road").put("id", road.id()).put("from", road.from()).put("to", road.to()).put("cells", cells(road.cells()));
        }
        w.list("building");
        for (Building b : game.buildings.values()) {
            Snapshot.Writer o = w.add("building").put("id", b.id()).put("kind", b.type().id()).put("col", b.col())
                    .put("row", b.row()).put("flag", b.flag()).put("done", b.done).put("built", b.built)
                    .put("out", b.out).put("builder", b.builder).put("worker", b.worker).put("staffed", b.staffed)
                    .put("work", b.work).put("burning", b.fire).put("douse", b.douse).put("lamb", b.lamb)
                    .put("dog", b.dog).put("dogX", b.dogX).put("dogY", b.dogY);
            for (Good good : Good.values()) {
                o.put("stock." + good.id(), b.stock.count(good)).put("coming." + good.id(), b.coming(good));
            }
            o.list("sheep");
            for (Wander sheep : b.herd) o.add("sheep").put("state", doubles(sheep.state()));
        }
        w.list("person");
        for (Person p : game.people.values()) {
            w.add("person").put("id", p.id()).put("role", p.role()).put("job", p.job).put("task", p.task)
                    .put("path", cells(p.path)).put("along", p.along).put("to", p.to)
                    .put("load", p.load == null ? "none" : p.load.id()).put("ware", p.ware)
                    .put("spot", p.spot == null ? "none" : cells(List.of(p.spot))).put("timer", p.timer);
        }
        w.list("ware");
        for (Ware ware : game.wares.values()) {
            w.add("ware").put("id", ware.id()).put("good", ware.good().id()).put("to", ware.to).put("flag", ware.flag);
        }
        w.list("wolf");
        for (Wolf wolf : game.wolves) {
            w.add("wolf").put("farm", wolf.farm).put("path", cells(wolf.path)).put("along", wolf.along)
                    .put("fleeing", wolf.fleeing);
        }
        if (game.overseer != null) {
            Overseer o = game.overseer;
            Snapshot.Writer over = w.child("overseer").put("next", o.next).put("thought", o.thought)
                    .put("failures", o.failures).put("towers", o.towers);
            over.list("urgent");
            for (BuildingType type : o.urgent) over.add("urgent").put("kind", type.id());
        }
        return w.values();
    }
    // --8<-- [end:write]

    // --8<-- [start:read]
    /** The saved settlement; {@code levels} gives the map of a mission number. */
    public static Game read(Map<String, String> values, Rules rules, IntFunction<Level> levels) {
        Snapshot.Reader r = Snapshot.reader(values);
        Level level = levels.apply(r.integer("mission"));
        Game game = new Game(level, rules, r.whole("seed"), r.whole("random"), r.bool("computer"));
        game.herd = SeededRandom.resume(r.whole("herdSeed"), r.whole("herd"));
        game.time = r.number("time");
        game.dispatch = r.number("dispatch");
        game.turn = r.integer("turn");
        game.nextId = r.integer("next");
        game.fireTimer = r.number("fire");
        game.wolfTimer = r.number("wolf");
        game.fishTimer = r.number("fish");
        game.phase = r.choice("phase", Game.Phase.class);
        game.settlers = r.integer("settlers");
        game.landVersion = r.integer("land");
        game.seasons.restore(r.number("season"));
        String ground = r.text("ground");
        if (ground.length() != level.width() * level.height()) throw new Snapshot.SnapshotException("ground: wrong size");
        for (int i = 0; i < ground.length(); i++) {
            Ground g = switch (ground.charAt(i)) {
                case 'G' -> Ground.GRASS;
                case 'F' -> Ground.FOREST;
                case 'R' -> Ground.ROCK;
                case 'W' -> Ground.WATER;
                case 'M' -> Ground.MOUNTAIN;
                default -> throw new Snapshot.SnapshotException("ground: '" + ground.charAt(i) + "'");
            };
            game.ground.set(i % level.width(), i / level.width(), g);
        }
        copy(doubles(r.text("trees")), game.tree);
        copy(doubles(r.text("fields")), game.field);
        copy(ints(r.text("farms")), game.fieldFarm);
        copy(ints(r.text("stone")), game.stone);
        copy(ints(r.text("fishes")), game.fish);
        for (Snapshot.Reader ash : r.list("ash")) game.ashes.put(ash.integer("tile"), ash.number("left"));
        RoadNetwork roads = game.roads;
        for (Snapshot.Reader flag : r.list("flag")) {
            roads.addFlag(flag.integer("id"), new Cell(flag.integer("col"), flag.integer("row")));
        }
        for (Snapshot.Reader road : r.list("road")) {
            roads.connect(road.integer("id"), road.integer("from"), road.integer("to"), cells(road.text("cells")));
        }
        roads.restore(r.integer("nextFlag"), r.integer("nextRoad"));
        for (Snapshot.Reader o : r.list("building")) {
            BuildingType type = rules.building(o.text("kind"));
            Building b = new Building(o.integer("id"), type, o.integer("col"), o.integer("row"), o.integer("flag"));
            b.done = o.bool("done");
            b.built = o.number("built");
            b.out = o.integer("out");
            b.builder = o.integer("builder");
            b.worker = o.integer("worker");
            b.staffed = o.bool("staffed");
            b.work = o.number("work");
            b.fire = o.number("burning");
            b.douse = o.number("douse");
            b.lamb = o.number("lamb");
            b.dog = o.number("dog");
            b.dogX = o.number("dogX");
            b.dogY = o.number("dogY");
            for (Good good : Good.values()) {
                b.stock.set(good, o.integer("stock." + good.id()));
                b.come(good, o.integer("coming." + good.id()));
            }
            double[] pen = b.pasture();
            for (Snapshot.Reader sheep : o.list("sheep")) {
                Wander wander = new Wander(pen[0] + pen[2] / 2, pen[1] + pen[3] / 2, Math.min(pen[2], pen[3]) / 2 - 5, 5);
                wander.restore(doubles(sheep.text("state")));
                b.herd.add(wander);
            }
            b.sheep = b.herd.size();
            game.buildings.put(b.id(), b);
            for (int row = b.row(); row < b.row() + type.height(); row++) {
                for (int col = b.col(); col < b.col() + type.width(); col++) game.built[game.index(col, row)] = b.id();
            }
            if (b.done && type.work() == Work.STORE) game.land.claim(b.id(), b.col() + 1, b.row() + 1, type.radius());
            if (b.done && b.staffed && type.work() == Work.WATCH) game.land.claim(b.id(), b.col(), b.row(), type.radius());
        }
        for (Snapshot.Reader o : r.list("person")) {
            Person p = new Person(o.integer("id"), o.choice("role", Person.Role.class), o.integer("job"));
            p.task = o.choice("task", Person.Task.class);
            p.path = cells(o.text("path"));
            p.along = o.number("along");
            p.to = o.number("to");
            p.load = good(o.text("load"));
            p.ware = o.integer("ware");
            String spot = o.text("spot");
            p.spot = spot.equals("none") ? null : cells(spot).getFirst();
            p.timer = o.number("timer");
            game.people.put(p.id(), p);
            if (p.task == Person.Task.FETCH && p.ware >= 0) game.reserved.add(p.ware);
        }
        for (Snapshot.Reader o : r.list("ware")) {
            Good good = good(o.text("good"));
            if (good == null) throw new Snapshot.SnapshotException("ware: no good");
            Ware ware = new Ware(o.integer("id"), good, o.integer("to"), o.integer("flag"));
            game.wares.put(ware.id(), ware);
        }
        for (Snapshot.Reader o : r.list("wolf")) {
            Wolf wolf = new Wolf(o.integer("farm"), cells(o.text("path")), o.number("along"));
            wolf.fleeing = o.bool("fleeing");
            game.wolves.add(wolf);
        }
        if (game.overseer != null && r.has("overseer.next")) {
            Snapshot.Reader over = r.child("overseer");
            game.overseer.next = over.integer("next");
            game.overseer.thought = over.integer("thought");
            game.overseer.failures = over.integer("failures");
            game.overseer.towers = over.integer("towers");
            for (Snapshot.Reader type : over.list("urgent")) game.overseer.urgent.add(rules.building(type.text("kind")));
        }
        return game;
    }
    // --8<-- [end:read]

    private static Good good(String id) {
        return id.equals("none") ? null : Good.ofId(id);
    }

    private static String cells(List<Cell> cells) {
        StringBuilder text = new StringBuilder();
        for (Cell cell : cells) {
            if (!text.isEmpty()) text.append(';');
            text.append(cell.col()).append(',').append(cell.row());
        }
        return text.toString();
    }

    private static List<Cell> cells(String text) {
        List<Cell> cells = new ArrayList<>();
        if (text.isEmpty()) return cells;
        try {
            for (String pair : text.split(";")) {
                String[] parts = pair.split(",");
                cells.add(new Cell(Integer.parseInt(parts[0]), Integer.parseInt(parts[1])));
            }
        } catch (RuntimeException e) {
            throw new Snapshot.SnapshotException("cells: " + text);
        }
        return cells;
    }

    /** Numbers to the last bit, as Base64. */
    private static String doubles(double[] values) {
        ByteBuffer bytes = ByteBuffer.allocate(values.length * 8);
        for (double value : values) bytes.putDouble(value);
        return Base64.getEncoder().encodeToString(bytes.array());
    }

    private static double[] doubles(String text) {
        ByteBuffer bytes = ByteBuffer.wrap(decode(text));
        double[] values = new double[bytes.remaining() / 8];
        for (int i = 0; i < values.length; i++) values[i] = bytes.getDouble();
        return values;
    }

    private static String ints(int[] values) {
        ByteBuffer bytes = ByteBuffer.allocate(values.length * 4);
        for (int value : values) bytes.putInt(value);
        return Base64.getEncoder().encodeToString(bytes.array());
    }

    private static int[] ints(String text) {
        ByteBuffer bytes = ByteBuffer.wrap(decode(text));
        int[] values = new int[bytes.remaining() / 4];
        for (int i = 0; i < values.length; i++) values[i] = bytes.getInt();
        return values;
    }

    private static byte[] decode(String text) {
        try {
            return Base64.getDecoder().decode(text);
        } catch (IllegalArgumentException e) {
            throw new Snapshot.SnapshotException("not Base64");
        }
    }

    private static void copy(double[] from, double[] to) {
        if (from.length != to.length) throw new Snapshot.SnapshotException("a tile list of the wrong size");
        System.arraycopy(from, 0, to, 0, to.length);
    }

    private static void copy(int[] from, int[] to) {
        if (from.length != to.length) throw new Snapshot.SnapshotException("a tile list of the wrong size");
        System.arraycopy(from, 0, to, 0, to.length);
    }
}
