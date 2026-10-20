package eu.amadeos.games.colony.ui.game;

import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.gameengine.audio.Mixer;
import eu.amadeos.gameengine.input.Keyboard;
import eu.amadeos.gameengine.input.Mouse;
import eu.amadeos.gameengine.save.SaveFile;
import eu.amadeos.games.colony.bootstrap.ColonyModule;
import eu.amadeos.games.colony.domain.Field;
import eu.amadeos.games.colony.domain.Game;
import eu.amadeos.games.colony.repository.Progress;
import eu.amadeos.games.colony.repository.SaveSlot;

import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * How long a frame of each mission takes to draw, after eight minutes of the computer building the
 * settlement, at two window sizes: {@code ./gradlew benchmark}. The view drifts slowly over the settlement.
 * The frames are drawn into a compatible image, as the canvas draws, but without waiting for the screen.
 */
public final class Benchmark {
    static final int WARM_UP = 60;
    static final int FRAMES = 300;
    static final double STEP = 1.0 / ColonyGame.UPDATES_PER_SECOND;

    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("colony");
        Messages msg = Messages.load(ColonyModule.class, ColonyModule.MESSAGES, Locale.ENGLISH);
        ColonyGame game = new ColonyGame(new Keyboard(), new Mouse(), msg,
                new Progress(new SaveFile(dir.resolve("progress.save"))),
                new SaveSlot(new SaveFile(dir.resolve("settlement.save"))), new Mixer());
        GraphicsConfiguration gc = GraphicsEnvironment.isHeadless() ? null
                : GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();
        System.out.println("ms per frame               frame  people  wares");
        for (int scale : new int[]{2, 3}) {
            for (int number = 1; number <= game.rules().missions(); number++) {
                ColonyGame.PlayScene play = game.play(new Game(game.level(number), game.rules(), number, true));
                for (int step = 0; step < 480 * ColonyGame.UPDATES_PER_SECOND; step++) play.step(STEP);
                play.camera().jumpTo(play.game().home().x() - 100, play.game().home().y());
                game.settle();
                int w = (int) Field.WIDTH * scale;
                int h = (int) Field.HEIGHT * scale;
                BufferedImage image = gc != null ? gc.createCompatibleImage(w, h) : new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
                long total = 0;
                int people = 0;
                int wares = 0;
                for (int i = 0; i < WARM_UP + FRAMES; i++) {
                    play.step(STEP);
                    play.step(STEP);
                    play.terrain().update(play.game());
                    play.camera().moveBy(0.5, 0);
                    Graphics2D g = image.createGraphics();
                    g.scale(scale, scale);
                    long start = System.nanoTime();
                    game.render(g, 0.5);
                    game.renderOverlay(g, 0.5);
                    if (i >= WARM_UP) {
                        total += System.nanoTime() - start;
                        people += play.game().people().size();
                        wares += play.game().wares().size();
                    }
                    g.dispose();
                }
                System.out.printf(Locale.ROOT, "mission %d at %4d x %4d   %6.2f   %5d  %5d%n", number, w, h,
                        total / 1e6 / FRAMES, people / FRAMES, wares / FRAMES);
            }
        }
        System.exit(0);
    }
}
