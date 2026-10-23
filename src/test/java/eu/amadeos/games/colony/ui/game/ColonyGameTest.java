package eu.amadeos.games.colony.ui.game;

import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.gameengine.audio.Mixer;
import eu.amadeos.gameengine.input.Keyboard;
import eu.amadeos.gameengine.input.Mouse;
import eu.amadeos.gameengine.save.SaveFile;
import eu.amadeos.gameengine.tile.Cell;
import eu.amadeos.games.colony.bootstrap.ColonyModule;
import eu.amadeos.games.colony.domain.Building;
import eu.amadeos.games.colony.domain.BuildingType;
import eu.amadeos.games.colony.domain.Cheat;
import eu.amadeos.games.colony.domain.Field;
import eu.amadeos.games.colony.domain.Game;
import eu.amadeos.games.colony.repository.Progress;
import eu.amadeos.games.colony.repository.SaveSlot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The game without a window and without the loop thread: the test moves the mouse, clicks, presses keys and
 * calls the steps itself, so everything happens in order on the test thread.
 */
class ColonyGameTest {
    private static final double STEP = 1.0 / ColonyGame.UPDATES_PER_SECOND;

    @TempDir
    Path dir;

    private final long[] now = {0};
    private final Keyboard keyboard = new Keyboard(() -> now[0]);
    private final Mouse mouse = new Mouse();
    private Progress progress;
    private SaveSlot slot;
    private ColonyGame game;

    @BeforeEach
    void create() {
        Messages msg = Messages.load(ColonyModule.class, ColonyModule.MESSAGES, Locale.ENGLISH);
        progress = new Progress(new SaveFile(dir.resolve("progress.save")));
        slot = new SaveSlot(new SaveFile(dir.resolve("settlement.save")));
        game = new ColonyGame(keyboard, mouse, msg, progress, slot, new Mixer());
    }

    private void tap(int key) {
        keyboard.pressed(key, now[0]);
        keyboard.released(key, now[0] + 1_000_000);
        steps(5);
    }

    private void steps(int n) {
        for (int i = 0; i < n; i++) {
            now[0] += (long) (STEP * 1e9);
            game.update(STEP);
        }
    }

    private void seconds(double seconds) {
        steps((int) Math.round(seconds * ColonyGame.UPDATES_PER_SECOND));
    }

    private void moveTo(double x, double y) {
        mouse.moved(x, y);
        steps(1);
    }

    /** The mouse over the middle of a tile, with the view moved there first. */
    private void over(ColonyGame.PlayScene play, Cell cell) {
        double x = (cell.col() + 0.5) * Field.TILE;
        double y = (cell.row() + 0.5) * Field.TILE;
        if (!play.camera().shows(play.camera().screenX(x, 1), play.camera().screenY(y, 1))) play.camera().jumpTo(x, y);
        moveTo(play.camera().screenX(x, 1), play.camera().screenY(y, 1));
    }

    private void click(int button) {
        mouse.pressed(button);
        steps(1);
        mouse.released(button);
        steps(1);
    }

    private void clickOn(java.awt.geom.Rectangle2D box) {
        moveTo(box.getCenterX(), box.getCenterY());
        click(MouseEvent.BUTTON1);
    }

    /** A free spot near the storehouse for {@code type}, from whose flag a road to the storehouse can be laid. */
    private static Cell spot(Game g, BuildingType type) {
        Building home = g.home();
        for (int d = 3; d < 12; d++) {
            for (int dr = -d; dr <= d; dr++) {
                for (int dc = -d; dc <= d; dc++) {
                    int col = home.col() + dc;
                    int row = home.row() + dr;
                    Cell door = new Cell(col + type.flagColumn(), row + type.height());
                    if (g.placement(type, col, row) == Game.Placement.OK && g.roads().flagAt(door) < 0
                            && !g.plan(home.flag(), door).isEmpty()) {
                        return new Cell(col, row);
                    }
                }
            }
        }
        throw new AssertionError("no spot for " + type.id());
    }

    @Test
    void fromTheTitleToTheFirstMission() {
        assertInstanceOf(ColonyGame.TitleScene.class, game.top());
        tap(KeyEvent.VK_ENTER);
        ColonyGame.ChoiceScene choice = assertInstanceOf(ColonyGame.ChoiceScene.class, game.top());
        tap(KeyEvent.VK_RIGHT);
        assertEquals(1, choice.chosen(), "mission 2 is not open yet");
        tap(KeyEvent.VK_ENTER);
        ColonyGame.PlayScene play = assertInstanceOf(ColonyGame.PlayScene.class, game.top());
        assertEquals(1, play.game().level().number());
        assertEquals("Mission 1 · The Green Valley", play.message());
    }

    // --8<-- [start:test-lay-out]
    @Test
    void thePanelLaysOutABuildingAndTheRoadFromItsFlagFollows() {
        ColonyGame.PlayScene play = game.play(1);
        BuildingType woodcutter = game.rules().building("woodcutter");
        assertSame(woodcutter, Hud.buildings(play.game(), BuildingType.Tab.BASIC).getFirst());
        clickOn(Hud.cell(0));
        assertSame(woodcutter, play.placing());

        Cell at = spot(play.game(), woodcutter);
        over(play, new Cell(at.col() + (woodcutter.width() - 1) / 2, at.row() + (woodcutter.height() - 1) / 2));
        click(MouseEvent.BUTTON1);
        assertNull(play.placing());
        Building site = play.game().buildingAt(at.col(), at.row());
        assertNotNull(site, "the site is laid out");
        assertTrue(play.roads(), "and the road from its flag is next");
        assertEquals(site.flag(), play.roadFrom());

        over(play, play.game().roads().cell(play.game().home().flag()));
        assertFalse(play.plan().isEmpty(), "the mouse shows the way to the storehouse");
        click(MouseEvent.BUTTON1);
        assertEquals(1, play.game().roads().roads(site.flag()).size(), "the road is laid");
        assertEquals(-1, play.roadFrom(), "it met the roads there are, so laying stops");
        seconds(1);
        assertNotNull(play.game().carrier(play.game().roads().roads(site.flag()).getFirst()), "and a carrier is coming");
    }
    // --8<-- [end:test-lay-out]

    @Test
    void aClickChoosesAHouseOrAFlagAndDeleteTakesAFlagAway() {
        ColonyGame.PlayScene play = game.play(1);
        Building home = play.game().home();
        over(play, new Cell(home.col() + 1, home.row()));
        click(MouseEvent.BUTTON1);
        assertSame(home, play.building());
        tap(KeyEvent.VK_DELETE);
        assertNotNull(play.game().building(home.id()), "the storehouse stays");

        Cell free = new Cell(home.col() + home.type().width() + 3, home.row() + home.type().height() + 2);
        while (!play.game().free(free.col(), free.row()) || !play.game().flagSpace(free)) free = new Cell(free.col() + 1, free.row());
        over(play, free);
        tap(KeyEvent.VK_F);
        int flag = play.game().roads().flagAt(free);
        assertTrue(flag >= 0, "F puts a flag down");
        click(MouseEvent.BUTTON1);
        assertEquals(flag, play.flag());
        assertNull(play.building());
        tap(KeyEvent.VK_DELETE);
        assertFalse(play.game().roads().hasFlag(flag));
        assertEquals(-1, play.flag());
    }

    @Test
    void theTabsChangeTheBuildList() {
        ColonyGame.PlayScene play = game.play(1);
        clickOn(Hud.tab(1));
        assertEquals(BuildingType.Tab.FOOD, play.tab());
        clickOn(Hud.cell(0));
        assertEquals("well", play.placing().id());
        click(MouseEvent.BUTTON3);
        tap(KeyEvent.VK_ESCAPE);
        assertNull(play.placing(), "Esc puts it back");
        clickOn(Hud.tab(2));
        assertEquals("sheepfarm", Hud.buildings(play.game(), play.tab()).getFirst().id());
    }

    @Test
    void theRoadButtonStartsLayingRoadsFromAFlag() {
        ColonyGame.PlayScene play = game.play(1);
        clickOn(Hud.ROAD);
        assertTrue(play.roads());
        assertEquals(-1, play.roadFrom());
        over(play, play.game().roads().cell(play.game().home().flag()));
        click(MouseEvent.BUTTON1);
        assertEquals(play.game().home().flag(), play.roadFrom(), "the first click picks the flag");
        tap(KeyEvent.VK_ESCAPE);
        assertEquals(-1, play.roadFrom());
        tap(KeyEvent.VK_ESCAPE);
        assertFalse(play.roads(), "the second Esc stops laying roads");
    }

    @Test
    void theMinimapMovesTheView() {
        ColonyGame.PlayScene play = game.play(1);
        double before = play.camera().x();
        moveTo(play.minimap().x() + 2, play.minimap().y() + 2);
        click(MouseEvent.BUTTON1);
        assertNotEquals(before, play.camera().x());
        assertEquals(0, play.camera().x(), 1e-9, "the top left corner of the map");
    }

    // --8<-- [start:test-wide]
    @Test
    void aWideScreenShowsMoreMapAndMovesThePanelToTheRightEdge() {
        game.resize(711);                       // 16:9: 711 × 400
        ColonyGame.PlayScene play = game.play(1);
        steps(2);
        assertEquals(Field.VIEW_WIDTH + 71, play.camera().viewWidth(), 1e-9, "the map gets the extra width");
        double before = play.camera().x();
        moveTo(play.minimap().x() + 71 + 2, play.minimap().y() + 2);   // the minimap, 71 units further right
        click(MouseEvent.BUTTON1);
        assertNotEquals(before, play.camera().x());
        assertEquals(0, play.camera().x(), 1e-9, "the top left corner of the map");
        moveTo(710, 200);                       // the real right edge of the screen moves the map
        steps(10);
        assertTrue(play.camera().x() > 0);
    }
    // --8<-- [end:test-wide]

    @Test
    void plusAndMinusChangeTheSpeed() {
        ColonyGame.PlayScene play = game.play(1);
        tap(KeyEvent.VK_PLUS);
        tap(KeyEvent.VK_PLUS);
        tap(KeyEvent.VK_PLUS);
        assertEquals(4, play.speed());
        double time = play.game().time();
        seconds(1);
        assertEquals(time + 4, play.game().time(), 0.05);
        tap(KeyEvent.VK_MINUS);
        assertEquals(2, play.speed());
    }

    @Test
    void f5SavesAndF9LoadsTheSettlement() {
        ColonyGame.PlayScene play = game.play(1);
        seconds(3);
        tap(KeyEvent.VK_F5);
        double saved = play.game().time();
        assertTrue(slot.exists());
        seconds(2);
        tap(KeyEvent.VK_F9);
        ColonyGame.PlayScene loaded = assertInstanceOf(ColonyGame.PlayScene.class, game.top());
        assertNotEquals(play, loaded);
        assertEquals(saved, loaded.game().time(), 0.1);
    }

    @Test
    void pausingStopsTheSettlementAndLeavingGoesBackToTheChoice() {
        ColonyGame.PlayScene play = game.play(1);
        steps(2);
        tap(KeyEvent.VK_P);
        assertInstanceOf(ColonyGame.PauseScene.class, game.top());
        double time = play.game().time();
        seconds(1);
        assertEquals(time, play.game().time());
        tap(KeyEvent.VK_DOWN);
        tap(KeyEvent.VK_ENTER);
        assertTrue(slot.exists(), "the second line saves");
        tap(KeyEvent.VK_ESCAPE);
        assertInstanceOf(ColonyGame.ChoiceScene.class, game.top());
    }

    @Test
    void aWonMissionShowsTheResultAndOpensTheNext() {
        ColonyGame.PlayScene play = game.play(1);
        steps(2);
        Cheat.win(play.game());
        seconds(ColonyGame.END_SECONDS + 0.5);
        assertInstanceOf(ColonyGame.ResultScene.class, game.top());
        assertEquals(2, progress.open());
        seconds(1);
        tap(KeyEvent.VK_ENTER);
        ColonyGame.PlayScene next = assertInstanceOf(ColonyGame.PlayScene.class, game.top());
        assertEquals(2, next.game().level().number());
    }

    @Test
    void whenTheYearsRunOutTheMissionIsTriedAgain() {
        ColonyGame.PlayScene play = game.play(2);
        steps(2);
        Cheat.lose(play.game());
        seconds(ColonyGame.END_SECONDS + 0.5);
        assertEquals(Game.Phase.LOST, play.game().phase());
        assertInstanceOf(ColonyGame.ResultScene.class, game.top());
        assertEquals(1, progress.open());
        seconds(1);
        tap(KeyEvent.VK_ENTER);
        assertEquals(2, assertInstanceOf(ColonyGame.PlayScene.class, game.top()).game().level().number());
    }

    @Test
    void theTitleShowsTheComputerBuildingASettlement() {
        ColonyGame.TitleScene title = assertInstanceOf(ColonyGame.TitleScene.class, game.top());
        int houses = title.demo().buildings().size();
        seconds(30);
        assertTrue(title.demo().buildings().size() > houses, "the computer lays out houses");
        assertFalse(title.demo().roads().roads().isEmpty(), "and roads");
    }

    @Test
    void everySceneDraws() {
        BufferedImage image = new BufferedImage((int) Field.WIDTH, (int) Field.HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        steps(10);
        game.render(g, 1);
        game.renderOverlay(g, 1);
        tap(KeyEvent.VK_ENTER);
        game.render(g, 1);
        game.renderOverlay(g, 1);
        ColonyGame.PlayScene play = game.play(3);
        seconds(1);
        game.settle();
        assertTrue(play.terrain().settled(), "the ground is drawn in the background, and all of it is ready");
        tap(KeyEvent.VK_F3);
        clickOn(Hud.cell(3));
        over(play, play.game().roads().cell(play.game().home().flag()));
        game.render(g, 0.5);
        game.renderOverlay(g, 0.5);
        tap(KeyEvent.VK_ESCAPE);
        tap(KeyEvent.VK_R);
        game.render(g, 1);
        game.renderOverlay(g, 1);
        tap(KeyEvent.VK_P);
        game.render(g, 1);
        game.renderOverlay(g, 1);
        tap(KeyEvent.VK_P);
        Cheat.win(play.game());
        seconds(ColonyGame.END_SECONDS + 0.5);
        game.render(g, 1);
        game.renderOverlay(g, 1);
        g.dispose();
        assertNotEquals(0, image.getRGB((int) Field.VIEW_WIDTH / 2, (int) Field.HEIGHT / 2) & 0xFFFFFF);
    }
}
