package eu.amadeos.games.colony.domain;

import eu.amadeos.gameengine.data.Catalog;
import eu.amadeos.gameengine.data.TextData;
import eu.amadeos.gameengine.data.TextData.DataException;
import eu.amadeos.gameengine.economy.Recipe;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Everything the data files say: the buildings of {@code buildings.txt}, the missions of
 * {@code missions.txt} and the computer's plan in {@code plan.txt}. Read once; a mistake in a file stops the
 * game at the start with the file and the line.
 */
public final class Rules {
    /** Where the data files lie among the classes. */
    public static final String FOLDER = "/eu/amadeos/games/colony/data/";

    private final Map<String, BuildingType> buildings;
    private final Map<Integer, Mission> missions;
    private final Plan plan;

    private Rules(Map<String, BuildingType> buildings, Map<Integer, Mission> missions, Plan plan) {
        this.buildings = buildings;
        this.missions = missions;
        this.plan = plan;
    }

    /** The rules of the game, from its data files. */
    public static Rules load() {
        return read(TextData.load(Rules.class, FOLDER + "buildings.txt"),
                TextData.load(Rules.class, FOLDER + "missions.txt"), TextData.load(Rules.class, FOLDER + "plan.txt"));
    }

    public static Rules read(List<TextData.Line> buildingLines, List<TextData.Line> missionLines,
                             List<TextData.Line> planLines) {
        Map<String, BuildingType> buildings = buildings(buildingLines);
        return new Rules(buildings, missions(missionLines), plan(planLines, buildings));
    }

    // --8<-- [start:buildings]
    private static Map<String, BuildingType> buildings(List<TextData.Line> lines) {
        Map<String, BuildingType> buildings = new LinkedHashMap<>();
        for (Catalog.Entry building : Catalog.read(lines, "building")) {
            TextData.Line size = building.line("size");
            if (size.size() != 3) throw size.error("size takes the width and the height in tiles");
            int width = size.integer(1);
            int height = size.integer(2);
            if (width < 1 || height < 1 || width > 4 || height > 4) throw size.error("a building is 1 to 4 tiles a side");
            Work work = choice(building, "work", Work.class);
            Map<Good, Integer> cost = building.has("cost") ? goods(building.line("cost")) : Map.of();
            double seconds = cost.isEmpty() ? 1 : positive(building, "seconds");
            Recipe<Good> recipe = recipe(building, work);
            int radius = building.integer("radius", 0);
            boolean reaches = work == Work.STORE || work == Work.WATCH || work == Work.FELL || work == Work.PLANT
                    || work == Work.QUARRY || work == Work.FISH || work == Work.FARM;
            if (reaches && radius < 1) throw building.error("a building that works " + work.name().toLowerCase(Locale.ROOT)
                    + " needs a radius");
            BuildingType type = new BuildingType(building.name(), choice(building, "tab", BuildingType.Tab.class),
                    width, height, cost, seconds, work, recipe, radius, yes(building, "tool"),
                    building.integer("keep", 2));
            building.finish();
            buildings.put(type.id(), type);
        }
        if (buildings.values().stream().noneMatch(type -> type.work() == Work.STORE)) {
            throw new DataException("buildings.txt: no building is the storehouse (work store)");
        }
        return buildings;
    }

    /** What goes in and comes out: {@code needs} with a|b for either, {@code makes}, {@code takes}. */
    private static Recipe<Good> recipe(Catalog.Entry building, Work work) {
        List<Set<Good>> inputs = new ArrayList<>();
        if (building.has("needs")) {
            TextData.Line needs = building.line("needs");
            for (int i = 1; i < needs.size(); i++) {
                Set<Good> either = EnumSet.noneOf(Good.class);
                for (String word : needs.word(i).split("\\|")) either.add(good(needs, word));
                inputs.add(either);
            }
        }
        Good makes = building.has("makes") ? good(building.line("makes"), building.text("makes")) : null;
        boolean timed = work == Work.MAKE || work == Work.GRAZE || work == Work.SETTLE;
        if (timed) return new Recipe<>(inputs, makes, positive(building, "takes"));
        if (!inputs.isEmpty()) throw building.line("needs").error("only make, graze and settle take goods in");
        return makes == null ? null : new Recipe<>(List.of(), makes, 1);
    }
    // --8<-- [end:buildings]

    private static Map<Integer, Mission> missions(List<TextData.Line> lines) {
        Map<Integer, Mission> missions = new LinkedHashMap<>();
        for (Catalog.Entry mission : Catalog.read(lines, "mission")) {
            int number;
            try {
                number = Integer.parseInt(mission.name());
            } catch (NumberFormatException e) {
                throw mission.error("a mission is a number");
            }
            if (number != missions.size() + 1) throw mission.error("the missions are numbered 1, 2, 3 and so on");
            TextData.Line seasons = mission.line("seasons");
            if (seasons.size() != 5) throw seasons.error("seasons takes the lengths of spring, summer, autumn and winter");
            double[] lengths = new double[4];
            for (int i = 0; i < 4; i++) {
                lengths[i] = seasons.number(i + 1);
                if (lengths[i] <= 0) throw seasons.error("a season lasts more than 0 seconds");
            }
            int settlers = mission.integer("settlers");
            int goal = mission.integer("goal");
            if (settlers < 1 || goal <= settlers) throw mission.line("goal").error("the goal is more settlers than at the start");
            missions.put(number, new Mission(number, mission.text("map"), goods(mission.line("start")), settlers, goal,
                    mission.has("goods") ? goods(mission.line("goods")) : Map.of(), mission.integer("years", 0),
                    lengths, yes(mission, "fire"), yes(mission, "wolves")));
            mission.finish();
        }
        if (missions.isEmpty()) throw new DataException("missions.txt: no mission");
        return missions;
    }

    private static Plan plan(List<TextData.Line> lines, Map<String, BuildingType> buildings) {
        List<Catalog.Entry> entries = Catalog.read(lines, "plan");
        if (entries.size() != 1) throw new DataException("plan.txt: one plan, please");
        Catalog.Entry plan = entries.getFirst();
        List<BuildingType> build = new ArrayList<>();
        TextData.Line order = plan.line("build");
        for (int i = 1; i < order.size(); i++) {
            BuildingType type = buildings.get(order.word(i));
            if (type == null || !type.buildable()) throw order.error('"' + order.word(i) + "\" is not a building to build");
            build.add(type);
        }
        int roads = plan.integer("roads");
        if (roads < 2) throw plan.line("roads").error("a road has 2 tiles at least");
        Plan result = new Plan(build, roads);
        plan.finish();
        return result;
    }

    /** Pairs of good and count, after the keyword. */
    private static Map<Good, Integer> goods(TextData.Line line) {
        if (line.size() % 2 != 1) throw line.error("goods come in pairs: a good and its count");
        Map<Good, Integer> goods = new EnumMap<>(Good.class);
        for (int i = 1; i < line.size(); i += 2) {
            Good good = good(line, line.word(i));
            int count = line.integer(i + 1);
            if (count < 1) throw line.error("a count is 1 or more");
            goods.merge(good, count, Integer::sum);
        }
        return goods;
    }

    private static Good good(TextData.Line line, String word) {
        Good good = Good.ofId(word);
        if (good == null) throw line.error('"' + word + "\" is not a good");
        return good;
    }

    private static <E extends Enum<E>> E choice(Catalog.Entry entry, String key, Class<E> type) {
        String word = entry.text(key);
        try {
            return Enum.valueOf(type, word.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            List<String> all = new ArrayList<>();
            for (E value : type.getEnumConstants()) all.add(value.name().toLowerCase(Locale.ROOT));
            throw entry.line(key).error('"' + word + "\" is not one of " + String.join(", ", all));
        }
    }

    private static double positive(Catalog.Entry entry, String key) {
        double value = entry.number(key);
        if (value <= 0) throw entry.line(key).error(key + " must be more than 0");
        return value;
    }

    private static boolean yes(Catalog.Entry entry, String key) {
        String word = entry.text(key, "no");
        return switch (word) {
            case "yes" -> true;
            case "no" -> false;
            default -> throw entry.line(key).error(key + " is yes or no");
        };
    }

    public BuildingType building(String id) {
        BuildingType building = buildings.get(id);
        if (building == null) throw new IllegalArgumentException("no building " + id);
        return building;
    }

    public boolean isBuilding(String id) {
        return buildings.containsKey(id);
    }

    /** All buildings, in the order of the file. */
    public List<BuildingType> buildings() {
        return List.copyOf(buildings.values());
    }

    /** The buildings of one tab of the build list, in the order of the file. */
    public List<BuildingType> buildings(BuildingType.Tab tab) {
        return buildings.values().stream().filter(type -> type.buildable() && type.tab() == tab).toList();
    }

    /** The storehouse. */
    public BuildingType storehouse() {
        return buildings.values().stream().filter(type -> type.work() == Work.STORE).findFirst().orElseThrow();
    }

    public Mission mission(int number) {
        Mission mission = missions.get(number);
        if (mission == null) throw new DataException("missions.txt: no mission " + number);
        return mission;
    }

    public int missions() {
        return missions.size();
    }

    public Plan plan() {
        return plan;
    }
}
