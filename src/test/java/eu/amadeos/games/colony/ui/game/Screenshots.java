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
import eu.amadeos.games.colony.domain.Field;
import eu.amadeos.games.colony.domain.Game;
import eu.amadeos.games.colony.domain.Wolf;
import eu.amadeos.games.colony.repository.Progress;
import eu.amadeos.games.colony.repository.SaveSlot;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * The screenshots of chapter 32, drawn without a screen: the title, the choice of a mission, a settlement
 * the computer built, a building looking for its place, a road being laid, the sheep farm, winter, a fire
 * and wolves. {@code ./gradlew screenshots -Pshots=<folder>}.
 */
public final class Screenshots {
    private static final double STEP = 1.0 / ColonyGame.UPDATES_PER_SECOND;

    private final long[] now = {0};
    private final Keyboard keyboard = new Keyboard(() -> now[0]);
    private final Mouse mouse = new Mouse();
    private final ColonyGame game;
    private double mouseX;
    private double mouseY;

    private Screenshots() throws Exception {
        Messages msg = Messages.load(ColonyModule.class, ColonyModule.MESSAGES, Locale.ENGLISH);
        Path dir = Files.createTempDirectory("colony");
        Progress progress = new Progress(new SaveFile(dir.resolve("progress.save")));
        progress.won(1, 1934, 3);      // one mission won, so the choice shows a time and a lock
        game = new ColonyGame(keyboard, mouse, msg, progress, new SaveSlot(new SaveFile(dir.resolve("settlement.save"))),
                new Mixer());
    }

    public static void main(String[] args) throws Exception {
        File dir = new File(args.length > 0 ? args[0] : "screenshots");
        dir.mkdirs();
        Screenshots shots = new Screenshots();
        shots.seconds(90);
        shots.save(new File(dir, "title.png"), false);
        shots.tap(KeyEvent.VK_ENTER);
        shots.steps(60);
        shots.save(new File(dir, "choice.png"), false);

        // Mission 1, built by the computer for a while: the storehouse, roads, carriers and the first chains.
        ColonyGame.PlayScene play = shots.settlement(1, 11, 650);
        Building home = play.game().home();
        play.camera().jumpTo(home.x() + 40, home.y() + 20);
        shots.steps(2);
        shots.save(new File(dir, "settlement.png"), false);

        shots.placing(play, "windmill");
        shots.save(new File(dir, "placing.png"), true);
        shots.tap(KeyEvent.VK_ESCAPE);

        shots.road(play);
        shots.save(new File(dir, "road.png"), true);
        shots.tap(KeyEvent.VK_ESCAPE);
        shots.tap(KeyEvent.VK_ESCAPE);

        shots.until(play, g -> g.buildings().stream().anyMatch(b -> b.type().id().equals("sheepfarm") && b.done()
                && b.sheep() >= 3), 1200);
        Building farm = play.game().buildings().stream().filter(b -> b.type().id().equals("sheepfarm") && b.done())
                .findFirst().orElseThrow();
        shots.choose(play, farm);
        shots.save(new File(dir, "sheep.png"), true);
        shots.tap(KeyEvent.VK_ESCAPE);

        ColonyGame.PlayScene fire = shots.settlement(2, 5, 300);
        shots.until(fire, g -> g.buildings().stream().anyMatch(b -> b.burning() && b.fire() > 4), 1500);
        Building burning = fire.game().buildings().stream().filter(Building::burning).findFirst().orElseThrow();
        fire.camera().jumpTo(burning.x(), burning.y());
        shots.steps(2);
        shots.save(new File(dir, "fire.png"), false);

        ColonyGame.PlayScene wolves = shots.settlement(3, 3, 430);
        Building store = wolves.game().home();
        wolves.camera().jumpTo(store.x() + 20, store.y() + 30);
        shots.steps(2);
        shots.save(new File(dir, "winter.png"), false);
        shots.until(wolves, g -> g.wolves().stream().anyMatch(w -> !w.fleeing() && near(g, w)), 2400);
        Wolf wolf = wolves.game().wolves().stream().filter(w -> !w.fleeing() && near(wolves.game(), w)).findFirst()
                .orElseThrow();
        wolves.camera().jumpTo(wolf.x() - 30, wolf.y());
        shots.steps(2);
        shots.save(new File(dir, "wolves.png"), false);
        System.exit(0);
    }

    /** A wolf in the open, close to a pasture with sheep. */
    private static boolean near(Game game, Wolf wolf) {
        for (Building b : game.buildings()) {
            if (!b.type().id().equals("sheepfarm") || b.sheep() == 0) continue;
            double[] pen = b.pasture();
            boolean open = game.tree((int) (wolf.x() / Field.TILE), (int) (wolf.y() / Field.TILE)) < 0
                    && game.tree((int) (wolf.x() / Field.TILE), (int) (wolf.y() / Field.TILE) + 1) < 0;
            if (open && Math.hypot(wolf.x() - (pen[0] + pen[2] / 2), wolf.y() - (pen[1] + pen[3] / 2)) < 56) return true;
        }
        return false;
    }

    /** Mission {@code number} built by the computer for {@code seconds}. */
    private ColonyGame.PlayScene settlement(int number, long seed, double seconds) {
        Game settlement = new Game(game.level(number), game.rules(), seed, true);
        ColonyGame.PlayScene play = game.play(settlement);
        seconds(seconds);
        return play;
    }

    /** Goes on until {@code done}, at most {@code seconds}. */
    private void until(ColonyGame.PlayScene play, Predicate<Game> done, double seconds) {
        for (int i = 0; i < seconds * ColonyGame.UPDATES_PER_SECOND; i++) {
            if (done.test(play.game())) return;
            steps(1);
        }
        throw new IllegalStateException("not reached by " + play.game().time());
    }

    /** A building from the panel, looking for its place near the storehouse. */
    private void placing(ColonyGame.PlayScene play, String id) {
        BuildingType type = game.rules().building(id);
        BuildingType.Tab tab = type.tab();
        moveTo(Hud.tab(tab.ordinal()).getCenterX(), Hud.tab(tab.ordinal()).getCenterY());
        click();
        int cell = Hud.buildings(play.game(), tab).indexOf(type);
        moveTo(Hud.cell(cell).getCenterX(), Hud.cell(cell).getCenterY());
        click();
        Building home = play.game().home();
        for (int d = 3; d < 14; d++) {
            for (int dy = -d; dy <= d; dy++) {
                for (int dx = -d; dx <= d; dx++) {
                    int col = home.col() + dx;
                    int row = home.row() + dy;
                    if (play.game().placement(type, col, row) == Game.Placement.OK) {
                        play.camera().jumpTo((col + 1) * Field.TILE + 30, (row + 1) * Field.TILE + 10);
                        steps(1);
                        point(play, (col + type.width() / 2.0) * Field.TILE, (row + type.height() / 2.0) * Field.TILE);
                        return;
                    }
                }
            }
        }
        throw new IllegalStateException("no place for a " + id);
    }

    /** Road mode from a flag at the edge of the settlement, the mouse a few tiles away where a road could end. */
    private void road(ColonyGame.PlayScene play) {
        Game g = play.game();
        Building home = g.home();
        List<Integer> flags = g.roads().flags().stream().sorted((a, b) -> Double.compare(distance(g, home, a),
                distance(g, home, b))).toList();
        for (int flag : flags) {
            Cell from = g.roads().cell(flag);
            for (int d = 4; d < 9; d++) {
                for (int dy = -d; dy <= d; dy++) {
                    for (int dx = -d; dx <= d; dx++) {
                        if (Math.max(Math.abs(dx), Math.abs(dy)) != d) continue;
                        Cell to = new Cell(from.col() + dx, from.row() + dy);
                        if (!g.contains(to.col(), to.row())) continue;
                        List<Cell> plan = g.plan(flag, to);
                        if (plan.size() < 7 || g.canPave(flag, plan) != Game.Paving.OK) continue;
                        play.camera().jumpTo((from.col() + to.col()) / 2.0 * Field.TILE + 40,
                                (from.row() + to.row()) / 2.0 * Field.TILE);
                        steps(1);
                        tap(KeyEvent.VK_R);
                        point(play, (from.col() + 0.5) * Field.TILE, (from.row() + 0.5) * Field.TILE);
                        click();
                        point(play, (to.col() + 0.5) * Field.TILE, (to.row() + 0.5) * Field.TILE);
                        return;
                    }
                }
            }
        }
        throw new IllegalStateException("no road to show");
    }

    private static double distance(Game g, Building home, int flag) {
        Cell cell = g.roads().cell(flag);
        return Math.hypot(cell.col() - home.col(), cell.row() - home.row());
    }

    private void choose(ColonyGame.PlayScene play, Building b) {
        play.camera().jumpTo(b.x() + 40, b.y());
        steps(1);
        point(play, b.x() + Field.TILE / 2.0, b.y());
        click();
        steps(30);
    }

    private void moveTo(double x, double y) {
        mouseX = x;
        mouseY = y;
        mouse.moved(x, y);
        steps(2);
    }

    private void point(ColonyGame.PlayScene play, double worldX, double worldY) {
        moveTo(play.camera().screenX(worldX, 1), play.camera().screenY(worldY, 1));
    }

    private void click() {
        mouse.pressed(MouseEvent.BUTTON1);
        steps(1);
        mouse.released(MouseEvent.BUTTON1);
        steps(1);
    }

    private void tap(int key) {
        keyboard.pressed(key, now[0]);
        keyboard.released(key, now[0] + 1_000_000);
        steps(5);
    }

    private void seconds(double seconds) {
        steps((int) Math.round(seconds * ColonyGame.UPDATES_PER_SECOND));
    }

    private void steps(int n) {
        for (int i = 0; i < n; i++) {
            now[0] += (long) (STEP * 1e9);
            game.update(STEP);
        }
    }

    /** World and overlay at twice the logical size, with the mouse pointer if {@code pointer}. */
    private void save(File file, boolean pointer) throws Exception {
        game.settle();
        write(file, g -> {
            game.render(g, 1);
            game.renderOverlay(g, 1);
            if (pointer) pointer(g, mouseX, mouseY);
        });
        System.out.println("[screenshots] " + file);
    }

    private static void pointer(Graphics2D g, double x, double y) {
        Path2D p = new Path2D.Double();
        p.moveTo(x, y);
        p.lineTo(x, y + 13);
        p.lineTo(x + 3.5, y + 10);
        p.lineTo(x + 6, y + 15);
        p.lineTo(x + 8, y + 14);
        p.lineTo(x + 5.5, y + 9.5);
        p.lineTo(x + 9.5, y + 9.5);
        p.closePath();
        g.setColor(Color.WHITE);
        g.fill(p);
        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke(1f));
        g.draw(p);
    }

    private static void write(File file, Consumer<Graphics2D> draw) throws Exception {
        int scale = 2;
        BufferedImage image = new BufferedImage((int) Field.WIDTH * scale, (int) Field.HEIGHT * scale,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.scale(scale, scale);
        draw.accept(g);
        g.dispose();
        ImageIO.write(image, "png", file);
    }
}
