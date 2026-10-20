package eu.amadeos.games.colony.domain;

import eu.amadeos.gameengine.data.TextData;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RulesTest {
    private static List<TextData.Line> file(String name) {
        return TextData.load(Rules.class, Rules.FOLDER + name);
    }

    @Test
    void theDataFilesDescribeTheBuildingsMissionsAndThePlan() {
        Rules rules = Rules.load();
        assertEquals("storehouse", rules.storehouse().id());
        assertFalse(rules.storehouse().buildable(), "the map places the storehouse; nobody builds it");
        assertEquals(List.of("woodcutter", "forester", "quarry", "sawmill", "tower"),
                rules.buildings(BuildingType.Tab.BASIC).stream().map(BuildingType::id).toList());
        BuildingType sawmill = rules.building("sawmill");
        assertEquals(Map.of(Good.PLANK, 2, Good.STONE, 2), sawmill.cost());
        assertEquals(List.of(Set.of(Good.LOG)), sawmill.recipe().inputs());
        assertEquals(Good.PLANK, sawmill.recipe().makes());
        assertTrue(rules.building("woodcutter").tool(), "felling takes an axe");
        assertEquals(List.of(Set.of(Good.BREAD, Good.FISH), Set.of(Good.CLOTH)), rules.building("residence").recipe().inputs());
        assertNull(rules.building("forester").recipe(), "the forester makes nothing to carry");
        assertEquals(3, rules.missions());
        assertFalse(rules.mission(1).fire());
        assertTrue(rules.mission(3).wolves());
        assertEquals(Map.of(Good.CLOTH, 4), rules.mission(2).goods());
        assertEquals("woodcutter", rules.plan().build().getFirst().id());
    }

    @Test
    void aMistakeInADataFileNamesItsLine() {
        List<TextData.Line> buildings = TextData.parse("buildings.txt", new StringReader("""
                building storehouse
                  tab basic
                  size 3 2
                  work store
                  radius 12
                  colour red
                """));
        assertEquals("buildings.txt:6: \"colour\" is not a property of a building", assertThrows(TextData.DataException.class,
                () -> Rules.read(buildings, file("missions.txt"), file("plan.txt"))).getMessage());

        List<TextData.Line> plan = TextData.parse("plan.txt", new StringReader("""
                plan computer
                  build woodcutter castle
                  roads 6
                """));
        assertEquals("plan.txt:2: \"castle\" is not a building to build", assertThrows(TextData.DataException.class,
                () -> Rules.read(file("buildings.txt"), file("missions.txt"), plan)).getMessage());
    }
}
