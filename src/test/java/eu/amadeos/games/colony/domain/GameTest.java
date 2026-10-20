package eu.amadeos.games.colony.domain;

import eu.amadeos.gameengine.road.RoadNetwork;
import eu.amadeos.gameengine.tile.Cell;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameTest {
    private static final double STEP = 1.0 / 120;
    private static final Rules RULES = Rules.load();

    private static Game game(int mission, boolean computer) {
        return new Game(Level.load(mission, RULES), RULES, 5, computer);
    }

    private static void run(Game game, double seconds) {
        for (int i = 0; i < Math.round(seconds / STEP) && game.phase() == Game.Phase.PLAYING; i++) game.update(STEP);
    }

    /** The first free spot near the storehouse where {@code type} may stand. */
    private static Cell spot(Game game, BuildingType type) {
        Building home = game.home();
        for (int d = 3; d < 12; d++) {
            for (int dr = -d; dr <= d; dr++) {
                for (int dc = -d; dc <= d; dc++) {
                    int col = home.col() + dc;
                    int row = home.row() + dr;
                    if (game.placement(type, col, row) == Game.Placement.OK
                            && !game.plan(game.roads().flagAt(new Cell(col + type.flagColumn(), row + type.height())) >= 0
                            ? -1 : home.flag(), new Cell(col + type.flagColumn(), row + type.height())).isEmpty()) {
                        return new Cell(col, row);
                    }
                }
            }
        }
        throw new AssertionError("no spot for " + type.id());
    }

    /** Lays out {@code type} near the storehouse and a road from the storehouse's flag to its flag. */
    private static Building build(Game game, String kind) {
        BuildingType type = RULES.building(kind);
        Cell at = spot(game, type);
        Cell door = new Cell(at.col() + type.flagColumn(), at.row() + type.height());
        List<Cell> road = game.plan(game.home().flag(), door);
        assertEquals(Game.Placement.OK, game.found(type, at.col(), at.row()));
        Building site = game.buildingAt(at.col(), at.row());
        assertEquals(Game.Paving.OK, game.pave(game.home().flag(), road));
        return site;
    }

    // --8<-- [start:test-build]
    @Test
    void aBuilderComesTheGoodsAreCarriedAndTheHouseStands() {
        Game game = game(1, false);
        int planks = game.stores(Good.PLANK);
        int logs = game.stores(Good.LOG);
        Building sawmill = build(game, "sawmill");
        assertFalse(sawmill.done());
        run(game, 60);
        assertTrue(sawmill.done(), "built within a minute");
        run(game, 20);
        assertTrue(sawmill.staffed(), "a worker came, with tools");
        run(game, 60);
        assertTrue(game.stores(Good.LOG) < logs, "the logs go to the sawmill");
        assertTrue(game.stores(Good.PLANK) > planks, "and come back as planks");
    }
    // --8<-- [end:test-build]

    @Test
    void everyRoadGetsItsCarrierAndANewFlagSplitsTheRoad() {
        Game game = game(1, false);
        Building home = game.home();
        Cell from = game.roads().cell(home.flag());
        Cell to = new Cell(from.col() + 6, from.row());
        List<Cell> way = game.plan(home.flag(), to);
        assertFalse(way.isEmpty());
        assertEquals(Game.Paving.OK, game.pave(home.flag(), way));
        RoadNetwork.Road road = game.roads().roadAt(way.get(3));
        run(game, 1);
        assertNotNull(game.carrier(road));
        assertTrue(game.placeFlag(way.get(3)));
        assertEquals(2, game.roads().roads().size());
        run(game, 1);
        for (RoadNetwork.Road part : game.roads().roads()) assertNotNull(game.carrier(part), "a carrier on each part");
        assertFalse(game.placeFlag(way.get(4)), "no flag next to a flag");
        assertEquals(Game.Paving.SHORT, game.canPave(home.flag(), way.subList(0, 2)));
    }

    @Test
    void housesStandOnlyInsideTheBorderOnFreeLand() {
        Game game = game(1, false);
        BuildingType woodcutter = RULES.building("woodcutter");
        assertEquals(Game.Placement.OUTSIDE, game.placement(woodcutter, 2, 2));
        Building home = game.home();
        assertEquals(Game.Placement.BLOCKED, game.placement(woodcutter, home.col(), home.row()));
        assertFalse(game.demolish(home), "the storehouse stays");
    }

    // --8<-- [start:test-computer]
    @Test
    void theComputerBuildsASettlementThatGrowsToTheGoal() {
        Game game = game(1, true);
        run(game, 15 * 60);
        assertEquals(Game.Phase.WON, game.phase(), "mission 1 won within fifteen minutes; settlers: " + game.settlers());
        assertTrue(game.settlers() >= game.mission().goal());
    }
    // --8<-- [end:test-computer]

    // --8<-- [start:test-archive]
    @Test
    void aSavedSettlementGoesOnExactlyAsBefore() {
        Game game = game(3, true);
        run(game, 8 * 60);
        Map<String, String> saved = Archive.write(game);
        Game loaded = Archive.read(saved, RULES, number -> Level.load(number, RULES));
        assertEquals(saved, Archive.write(loaded), "written again, the same");
        run(game, 3 * 60);
        run(loaded, 3 * 60);
        assertEquals(Archive.write(game), Archive.write(loaded), "and three minutes on, still the same");
    }
    // --8<-- [end:test-archive]

    @Test
    void winterStopsTheFieldsAndWolvesComeForTheSheep() {
        Game game = game(3, true);
        int wolves = 0;
        int barks = 0;
        for (int i = 0; i < 120 * 60 * 14 && game.phase() == Game.Phase.PLAYING; i++) {
            game.update(STEP);
            for (Event event : game.events()) {
                if (event instanceof Event.Wolf) wolves++;
                if (event instanceof Event.Barked || event instanceof Event.SheepTaken) barks++;
            }
        }
        assertTrue(wolves > 0, "wolves came");
        assertTrue(barks > 0, "and were met at the pasture");
    }
}
