package eu.amadeos.games.colony.domain;

import eu.amadeos.gameengine.data.TextData;
import eu.amadeos.gameengine.tiled.TiledMap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LevelTest {
    private final Rules rules = Rules.load();

    @Test
    void everyMapHasForestAndRockInsideTheBorderAndWaterForTheFisher() {
        for (int number = 1; number <= rules.missions(); number++) {
            Level level = Level.load(number, rules);
            Game game = new Game(level, rules, 1, false);
            int trees = 0;
            int rock = 0;
            int water = 0;
            for (int row = 0; row < level.height(); row++) {
                for (int col = 0; col < level.width(); col++) {
                    if (level.ground(col, row) == Ground.WATER) water++;
                    if (!game.land().inside(col, row)) continue;
                    if (game.tree(col, row) >= 0) trees++;
                    if (level.ground(col, row) == Ground.ROCK) rock++;
                }
            }
            assertTrue(trees >= 10 && rock >= 3 && water >= 3,
                    "map " + number + ": wood, stone and fish to start with: " + trees + " " + rock + " " + water);
            assertEquals(level.mission().wolves(), !level.dens().isEmpty(), "map " + number + ": dens only with wolves");
        }
    }

    @Test
    void aMapIsCheckedWhenItIsRead() {
        Mission mission = rules.mission(1);
        TiledMap map = TiledMap.load(Level.class, Level.FOLDER + mission.map());
        Level level = Level.read(mission, map, rules);
        assertFalse(level.ground(0, 0).walkable(), "the edge of every map is mountains");
        assertEquals(Ground.MOUNTAIN, level.ground(level.width() - 1, level.height() - 1));
        TextData.DataException error = assertThrows(TextData.DataException.class,
                () -> rules.mission(4));
        assertEquals("missions.txt: no mission 4", error.getMessage());
    }
}
