package eu.amadeos.games.colony.ui.game;

import eu.amadeos.gameengine.audio.Mixer;
import eu.amadeos.gameengine.audio.Sound;
import eu.amadeos.gameengine.camera.Camera;
import eu.amadeos.gameengine.fx.Particles;
import eu.amadeos.gameengine.random.SeededRandom;
import eu.amadeos.gameengine.sprite.Sprite;
import eu.amadeos.games.colony.domain.Building;
import eu.amadeos.games.colony.domain.BuildingType;
import eu.amadeos.games.colony.domain.Event;
import eu.amadeos.games.colony.domain.Field;
import eu.amadeos.games.colony.domain.Game;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What the events of a step look and sound like: smoke from the chimneys, dust where a house goes up or
 * comes down, chips where a tree falls, the stumps it leaves, sparks and smoke of a fire and the water that
 * puts it out, snow falling in winter, little words over the land, and the sounds. The rules know nothing of
 * this. Everything lives in world units and is drawn by the painter.
 *
 * <p>Sounds are heard of what the player can see, and of what concerns the whole settlement, a fire or a
 * wolf, wherever it is.
 */
final class Effects {
    /** Dust, wood chips, sparks, water, snow, wool. */
    static final Color[] PALETTE = {new Color(0xC8A878), new Color(0xE0C090), new Color(0xFFB040),
            new Color(0x8CC8F0), new Color(0xF8FBFF), new Color(0xF4F0E4)};
    static final int DUST = 0;
    static final int CHIPS = 1;
    static final int SPARK = 2;
    static final int WATER = 3;
    static final int SNOW = 4;
    static final int WOOL = 5;
    static final double PUFF_SECONDS = 2.6;
    static final double STUMP_SECONDS = 90;
    static final double TEXT_SECONDS = 1.6;
    static final int MOST_PUFFS = 300;
    static final double SOUND_GAP = 0.08;
    /** Smoke drifts with the wind, to the north east, in units per second. */
    static final double WIND_X = 5;
    static final double WIND_Y = -8;
    /** Snowflakes over the view in deep winter, each second. */
    static final double FLAKES = 70;

    private record Puff(double x, double y, int size, double born, double life, boolean dark) {
    }

    private record Stump(double x, double y, double born) {
    }

    private record Text(double x, double y, String text, Color colour, double born) {
    }

    private final SoundBank sounds = new SoundBank();
    private final Music music = new Music();
    private final Mixer mixer;
    private final Pictures pictures;
    private final SeededRandom random = new SeededRandom(1);
    private final Particles particles = new Particles(1600, PALETTE);
    private final ArrayDeque<Puff> puffs = new ArrayDeque<>();
    private final List<Stump> stumps = new ArrayList<>();
    private final List<Text> texts = new ArrayList<>();
    private final Map<Sound, Double> played = new HashMap<>();
    private final Font font = new Font(Font.SANS_SERIF, Font.BOLD, 8);
    private double time;
    private double smoke;
    private double flakes;

    Effects(Mixer mixer, Pictures pictures) {
        this.mixer = mixer;
        this.pictures = pictures;
    }

    double time() {
        return time;
    }

    /** A new mission, or the title: nothing left from before, no music. */
    void clear() {
        particles.clear();
        puffs.clear();
        stumps.clear();
        texts.clear();
        mixer.stopMusic();
    }

    void music() {
        mixer.playMusic(music.meadow, true);
    }

    void tick() {
        play(sounds.tick);
    }

    void no() {
        play(sounds.no);
    }

    void yes() {
        play(sounds.yes);
    }

    // --8<-- [start:update]
    /** Time goes on: chimneys smoke where the work goes on, burning houses throw sparks, snow falls over the view. */
    void update(double seconds, Game game, Camera camera) {
        time += seconds;
        particles.update(seconds, 6);
        while (!puffs.isEmpty() && time - puffs.peekFirst().born() > PUFF_SECONDS) puffs.removeFirst();
        stumps.removeIf(stump -> time - stump.born() > STUMP_SECONDS);
        texts.removeIf(text -> time - text.born() > TEXT_SECONDS);
        if (game == null) return;
        smoke += seconds;
        boolean puff = smoke >= 0.3;
        if (puff) smoke = 0;
        for (Building b : game.buildings()) {
            if (!b.done()) continue;
            if (b.burning()) {
                if (puff) {
                    for (int i = 0; i < 2; i++) {
                        double x = (b.col() + random.nextDouble() * b.type().width()) * Field.TILE;
                        double y = (b.row() + random.nextDouble() * b.type().height() * 0.5) * Field.TILE - 6;
                        puff(x, y, 3, true);
                    }
                }
                if (random.nextDouble() < seconds * 25) {
                    sparkle(b.x(), b.y() - 8, 1, SPARK, 20, -18);
                }
                if (b.dousing() && random.nextDouble() < seconds * 40) {
                    sparkle(b.x() + (random.nextDouble() - 0.5) * 16, b.y() - 10, 1, WATER, 14, 10);
                }
                continue;
            }
            double[] chimney = Houses.chimneyTop(b.type().id());
            boolean storehouse = b.type().id().equals("storehouse");
            if (puff && chimney != null && (b.working() || storehouse && random.nextDouble() < 0.4)) {
                puff(b.col() * Field.TILE + chimney[0], b.row() * Field.TILE + chimney[1], 0, false);
            }
        }
        double snow = Terrain.snow(game);
        if (camera != null && snow > 0.6) {
            flakes += seconds * FLAKES * snow;
            while (flakes >= 1) {
                flakes--;
                double x = camera.x() + random.nextDouble() * (camera.viewWidth() + 60) - 30;
                double y = camera.y() + random.nextDouble() * camera.viewHeight() * 0.8 - 20;
                particles.spawn(x, y, 4 + random.nextDouble() * 6, 10 + random.nextDouble() * 8, 1.6 + random.nextDouble() * 1.4,
                        0.8 + random.nextDouble() * 0.8, SNOW);
            }
        }
    }
    // --8<-- [end:update]

    // --8<-- [start:react]
    /** Shows what happened in the last step, and plays it if {@code audible}, as far as {@code camera} sees it. */
    void react(Game game, Camera camera, boolean audible) {
        for (Event event : game.events()) {
            switch (event) {
                case Event.Founded(Building b) -> {
                    dust(b, 6);
                    if (audible) play(sounds.founded);
                }
                case Event.Built(Building b) -> {
                    dust(b, 14);
                    if (heard(camera, audible, b.x(), b.y())) play(sounds.built);
                }
                case Event.Paved paved -> {
                    if (audible && !game.computer()) play(sounds.paved);
                }
                case Event.Felled(int col, int row) -> {
                    double x = (col + 0.5) * Field.TILE;
                    double y = (row + 0.5) * Field.TILE;
                    stumps.add(new Stump(x, y + 3, time));
                    sparkle(x, y, 8, CHIPS, 30, 0);
                    if (heard(camera, audible, x, y)) play(sounds.felled);
                }
                case Event.Settled(Building b) -> {
                    say(b.x(), b.y() - 20, "+1", Hud.GREEN);
                    if (heard(camera, audible, b.x(), b.y())) play(sounds.settled);
                }
                case Event.Season season -> {
                    if (audible) play(season.season() == 3 ? sounds.winter : sounds.season);
                }
                case Event.Burning(Building b) -> {
                    if (audible) play(sounds.alarm);
                }
                case Event.Doused(Building b) -> {
                    sparkle(b.x(), b.y() - 6, 20, WATER, 30, 0);
                    puff(b.x(), b.y() - 10, 3, false);
                    if (audible) play(sounds.doused);
                }
                case Event.Burned(Building b) -> {
                    dust(b, 24);
                    for (int i = 0; i < 4; i++) puff(b.x() + (random.nextDouble() - 0.5) * 20, b.y(), 4, true);
                    if (audible) play(sounds.collapse);
                }
                case Event.Demolished(Building b) -> {
                    dust(b, 20);
                    if (audible) play(sounds.collapse);
                }
                case Event.Wolf wolf -> {
                    if (audible) play(sounds.howl);
                }
                case Event.Barked(Building farm, boolean horn) -> {
                    if (audible) play(horn ? sounds.horn : sounds.bark);
                }
                case Event.SheepTaken(Building farm) -> {
                    double[] pen = farm.pasture();
                    sparkle(pen[0] + pen[2] / 2, pen[1] + pen[3] / 2, 16, WOOL, 26, 0);
                    say(pen[0] + pen[2] / 2, pen[1], "-1", Hud.RED);
                    if (audible) play(sounds.bleat);
                }
                case Event.Lamb(Building farm) -> {
                    if (heard(camera, audible, farm.x(), farm.y())) play(sounds.lamb);
                }
                case Event.Over(boolean won) -> {
                    if (audible && !game.computer()) mixer.playMusic(won ? music.won : music.lost, false);
                }
                case Event.Made made -> {
                }
                case Event.NoTools noTools -> {
                }
            }
        }
    }
    // --8<-- [end:react]

    private static boolean heard(Camera camera, boolean audible, double x, double y) {
        return audible && (camera == null || x >= camera.x() - 16 && x <= camera.x() + camera.viewWidth() + 16
                && y >= camera.y() - 16 && y <= camera.y() + camera.viewHeight() + 16);
    }

    private void play(Sound sound) {
        Double last = played.get(sound);
        if (last != null && time - last < SOUND_GAP) return;
        played.put(sound, time);
        mixer.play(sound);
    }

    /** Smoke rising from a point, of size 0 to 4; dark from a fire. */
    private void puff(double x, double y, int size, boolean dark) {
        puffs.addLast(new Puff(x + (random.nextDouble() - 0.5) * 2, y + (random.nextDouble() - 0.5) * 2,
                Math.clamp(size + random.nextInt(2), 0, Pictures.PUFFS.length - 1), time,
                PUFF_SECONDS * (0.6 + random.nextDouble() * 0.4), dark));
        if (puffs.size() > MOST_PUFFS) puffs.removeFirst();
    }

    private void dust(Building b, int count) {
        BuildingType type = b.type();
        for (int i = 0; i < count; i++) {
            double x = (b.col() + random.nextDouble() * type.width()) * Field.TILE;
            double y = (b.row() + type.height()) * Field.TILE - random.nextDouble() * 6;
            double a = random.nextDouble() * 2 * Math.PI;
            double v = 6 + random.nextDouble() * 14;
            particles.spawn(x, y, Math.cos(a) * v, Math.sin(a) * v * 0.5 - 6, 0.5 + random.nextDouble() * 0.6,
                    1.4 + random.nextDouble() * 2, DUST);
        }
    }

    private void sparkle(double x, double y, int count, int colour, double speed, double up) {
        for (int i = 0; i < count; i++) {
            double a = random.nextDouble() * 2 * Math.PI;
            double v = speed * (0.4 + random.nextDouble() * 0.8);
            particles.spawn(x, y, Math.cos(a) * v, Math.sin(a) * v + up, 0.3 + random.nextDouble() * 0.5,
                    0.7 + random.nextDouble() * 1.1, colour);
        }
    }

    private void say(double x, double y, String text, Color colour) {
        texts.add(new Text(x, y, text, colour, time));
    }

    /** On the ground, under everything that stands: the stumps of felled trees. */
    void renderGround(Graphics2D g, Camera camera, double alpha) {
        Composite before = g.getComposite();
        for (Stump stump : stumps) {
            if (!camera.sees(stump.x() - 4, stump.y() - 4, 8, 8, alpha)) continue;
            float a = (float) Math.min(1, (STUMP_SECONDS - (time - stump.born())) / 10);
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0, a)));
            pictures.stump.draw(g, stump.x() - 4, stump.y() - 4);
        }
        g.setComposite(before);
    }

    /** Above everything that stands: smoke, sparks, dust and snow, and the little words. */
    void render(Graphics2D g, double alpha) {
        Composite before = g.getComposite();
        for (Puff puff : puffs) {
            double t = (time - puff.born()) / puff.life();
            if (t >= 1) continue;
            int size = Math.min(Pictures.PUFFS.length - 1, puff.size() + (int) (t * 2.5));
            Sprite sprite = pictures.puffs[size];
            double a = (puff.dark() ? 0.85 : 0.6) * (1 - t) * Math.min(1, t * 6);
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) a));
            double age = time - puff.born();
            double x = puff.x() + age * WIND_X - sprite.width() / 2;
            double y = puff.y() + age * WIND_Y - sprite.height() / 2;
            if (puff.dark()) {
                pictures.darkPuffs[size].draw(g, x, y);
            } else {
                sprite.draw(g, x, y);
            }
        }
        g.setComposite(before);
        particles.render(g, alpha);
        g.setFont(font);
        FontMetrics metrics = g.getFontMetrics();
        for (Text text : texts) {
            double t = (time - text.born()) / TEXT_SECONDS;
            int a = (int) Math.round(255 * Math.min(1, (1 - t) * 3));
            float x = (float) (text.x() - metrics.stringWidth(text.text()) / 2.0);
            float y = (float) (text.y() - 12 * t);
            g.setColor(new Color(0, 0, 0, a / 2));
            g.drawString(text.text(), x + 0.6f, y + 0.6f);
            Color c = text.colour();
            g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), a));
            g.drawString(text.text(), x, y);
        }
    }

    int particles() {
        return particles.count();
    }

    int stumps() {
        return stumps.size();
    }

    int puffs() {
        return puffs.size();
    }
}
