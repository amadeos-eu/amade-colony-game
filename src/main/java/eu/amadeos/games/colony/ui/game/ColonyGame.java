package eu.amadeos.games.colony.ui.game;

import com.google.inject.Inject;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.gameengine.audio.Mixer;
import eu.amadeos.gameengine.camera.Camera;
import eu.amadeos.gameengine.input.Controls;
import eu.amadeos.gameengine.input.Keyboard;
import eu.amadeos.gameengine.input.Mouse;
import eu.amadeos.gameengine.loop.GameLoop;
import eu.amadeos.gameengine.minimap.Minimap;
import eu.amadeos.gameengine.road.RoadNetwork;
import eu.amadeos.gameengine.scene.Scene;
import eu.amadeos.gameengine.scene.SceneStack;
import eu.amadeos.gameengine.swing.GameCanvas;
import eu.amadeos.gameengine.tile.Cell;
import eu.amadeos.games.colony.domain.Building;
import eu.amadeos.games.colony.domain.BuildingType;
import eu.amadeos.games.colony.domain.Event;
import eu.amadeos.games.colony.domain.Field;
import eu.amadeos.games.colony.domain.Game;
import eu.amadeos.games.colony.domain.Level;
import eu.amadeos.games.colony.domain.Rules;
import eu.amadeos.games.colony.repository.Progress;
import eu.amadeos.games.colony.repository.SaveSlot;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The running game: the loop, the mouse and the keys, the effects and the scenes (the title with the
 * computer building a settlement, the choice of a mission, a mission, the pause and its end). Everything
 * here runs on the game loop thread, except the {@link Arena} methods, which only post commands to it.
 */
public final class ColonyGame implements Arena {
    enum Action { LEFT, RIGHT, UP, DOWN, PAUSE, BACK, OK, SAVE, LOAD, ROAD, FLAG, TEAR_DOWN, FASTER, SLOWER, COUNTS }

    static final int UPDATES_PER_SECOND = 120;
    static final int FRAMES_PER_SECOND = 60;
    /** How fast the keys and the edge of the screen move the map, in units per second. */
    static final double PAN_SPEED = 360;
    static final double EDGE = 3;
    /** How long a mission goes on behind its end before the result shows. */
    static final double END_SECONDS = 2;
    static final double TITLE_SPEED = 10;
    /** The title's settlement grows this many times as fast, and starts again after this long. */
    static final int TITLE_STEPS = 3;
    static final double TITLE_SECONDS = 1500;
    static final int[] SPEEDS = {1, 2, 4};
    /** The minimap is painted again at most this often while the land changes. */
    static final double MINIMAP_SECONDS = 0.5;
    /** The choice shows each map this long after the start, when the snow has melted. */
    static final double PREVIEW_SECONDS = 40;
    static final double PAUSE_TOP = 170;
    static final double PAUSE_ROW = 24;
    /** The same message about missing tools is not repeated for this long. */
    static final double NAG_SECONDS = 20;

    private final Keyboard keyboard;
    private final Mouse mouse;
    private final Messages msg;
    private final Progress progress;
    private final SaveSlot slot;
    private final Mixer mixer;
    private final Rules rules;
    private final ColonyPainter painter;
    private final Hud hud;
    private final Effects effects;
    private final SceneStack scenes = new SceneStack();
    private final Controls<Action> controls;
    private final List<Level> levels = new ArrayList<>();
    /** The ground of each map as it is at the start, for the choice screen; drawn once, on other threads. */
    private final Map<Integer, Terrain> previews = new HashMap<>();
    private final List<Terrain> terrains = new ArrayList<>();
    private int chosen = 1;
    private long seed = System.nanoTime();
    private GameLoop loop;
    private GameCanvas canvas;
    /** The logical width of the screen: {@link Field#WIDTH}, or more on a wide screen. */
    private double width = Field.WIDTH;

    @Inject
    public ColonyGame(Keyboard keyboard, Mouse mouse, Messages msg, Progress progress, SaveSlot slot, Mixer mixer) {
        this.keyboard = keyboard;
        this.mouse = mouse;
        this.msg = msg;
        this.progress = progress;
        this.slot = slot;
        this.mixer = mixer;
        this.rules = Rules.load();
        for (int i = 1; i <= rules.missions(); i++) levels.add(Level.load(i, rules));
        this.painter = new ColonyPainter(rules);
        this.hud = new Hud(msg, painter.pictures());
        this.effects = new Effects(mixer, painter.pictures());
        // --8<-- [start:controls]
        controls = new Controls<>(Action.class, keyboard)
                .bind(Action.LEFT, KeyEvent.VK_LEFT)
                .bind(Action.RIGHT, KeyEvent.VK_RIGHT)
                .bind(Action.UP, KeyEvent.VK_UP)
                .bind(Action.DOWN, KeyEvent.VK_DOWN)
                .bind(Action.PAUSE, KeyEvent.VK_P)
                .bind(Action.BACK, KeyEvent.VK_ESCAPE)
                .bind(Action.OK, KeyEvent.VK_ENTER, KeyEvent.VK_SPACE)
                .bind(Action.SAVE, KeyEvent.VK_F5)
                .bind(Action.LOAD, KeyEvent.VK_F9)
                .bind(Action.ROAD, KeyEvent.VK_R)
                .bind(Action.FLAG, KeyEvent.VK_F)
                .bind(Action.TEAR_DOWN, KeyEvent.VK_DELETE)
                .bind(Action.FASTER, KeyEvent.VK_PLUS, KeyEvent.VK_ADD, KeyEvent.VK_EQUALS)
                .bind(Action.SLOWER, KeyEvent.VK_MINUS, KeyEvent.VK_SUBTRACT)
                .bind(Action.COUNTS, KeyEvent.VK_F3);
        // --8<-- [end:controls]
        scenes.push(new TitleScene());
    }

    /** Starts the loop; the canvas draws the scenes' worlds and then their overlays. */
    public void start(GameCanvas canvas) {
        this.canvas = canvas;
        canvas.setMaxLogicalWidth(Field.MAX_WIDTH);
        loop = new GameLoop(UPDATES_PER_SECOND, FRAMES_PER_SECOND, this::update, canvas::render);
        loop.start();
    }

    public void stop() {
        if (loop != null) loop.stop();
    }

    void update(double seconds) {
        keyboard.poll();
        mouse.poll();
        if (canvas != null) resize(canvas.logicalWidth());
        scenes.update(seconds);
    }

    public void render(Graphics2D g, double alpha) {
        scenes.render(g, alpha);
    }

    public void renderOverlay(Graphics2D g, double alpha) {
        scenes.renderOverlay(g, alpha);
    }

    // ---- Arena: called on the Swing thread, carried out on the loop thread -----------------------

    @Override
    public void showTitle() {
        run(() -> scenes.replace(new TitleScene()));
    }

    @Override
    public void chooseLevel() {
        run(() -> scenes.replace(new ChoiceScene()));
    }

    @Override
    public void pause() {
        run(() -> {
            if (scenes.top() instanceof PlayScene play) scenes.push(new PauseScene(play));
        });
    }

    @Override
    public void setSound(boolean on) {
        mixer.setMuted(!on);
    }

    private void run(Runnable command) {
        if (loop != null && loop.running()) loop.post(command);
        else command.run();
    }

    // ---- for tests and screenshots ---------------------------------------------------------------

    Scene top() {
        return scenes.top();
    }

    int sceneCount() {
        return scenes.size();
    }

    Effects effects() {
        return effects;
    }

    Rules rules() {
        return rules;
    }

    Level level(int number) {
        return levels.get(number - 1);
    }

    /** Every ground drawn at once: for screenshots, which draw a single frame. */
    void settle() {
        previews.values().forEach(Terrain::settle);
        terrains.forEach(Terrain::settle);
    }

    /** Plays mission {@code number} at once, without the title and the choice. */
    PlayScene play(int number) {
        return play(new Game(levels.get(number - 1), rules, seed++, false));
    }

    /** Plays a settlement prepared by a test. */
    PlayScene play(Game settlement) {
        PlayScene play = new PlayScene(settlement);
        scenes.replace(play);
        return play;
    }

    // ---- the scenes ------------------------------------------------------------------------------

    /** A new screen width: the map shows more or less, the panel moves to the right edge. */
    void resize(double width) {
        this.width = width;
        hud.resize(width);
    }

    /** How much wider than {@link Field#WIDTH} the screen is: the panel sits that much further right. */
    private double extra() {
        return width - Field.WIDTH;
    }

    /** A new ground for a game; the old ones are let go, so their pictures do not pile up. */
    private Terrain terrain(Game game) {
        terrains.clear();
        Terrain terrain = new Terrain(game.level());
        terrain.update(game);
        terrains.add(terrain);
        return terrain;
    }

    private Camera camera(Level level) {
        return new Camera(Field.VIEW_WIDTH + extra(), Field.VIEW_HEIGHT, level.width() * Field.TILE,
                level.height() * Field.TILE).placedAt(0, Field.BAR);
    }

    /** The view of the whole screen, behind the title and the choice. */
    private Camera wideCamera(Level level) {
        return new Camera(width, Field.HEIGHT, level.width() * Field.TILE, level.height() * Field.TILE);
    }

    private Game newGame(int number) {
        return new Game(levels.get(number - 1), rules, seed++, false);
    }

    /** The first map, with the computer building the settlement; the view drifts across it. */
    final class TitleScene implements Scene {
        private Game demo;
        private Terrain terrain;
        private final Camera camera = wideCamera(levels.getFirst());
        private double time;

        TitleScene() {
            effects.clear();
            restart();
        }

        private void restart() {
            demo = new Game(levels.getFirst(), rules, 7, true);
            terrain = terrain(demo);
            time = 0;
        }

        @Override
        public void update(double seconds) {
            if (controls.wasPressed(Action.OK) || mouse.wasPressed(MouseEvent.BUTTON1)) {
                scenes.replace(new ChoiceScene());
                return;
            }
            if (controls.wasPressed(Action.LOAD) && load()) return;
            time += seconds;
            for (int i = 0; i < TITLE_STEPS; i++) {
                demo.update(seconds);
                effects.react(demo, camera, false);
            }
            effects.update(seconds, demo, camera);
            terrain.update(demo);
            if (demo.phase() != Game.Phase.PLAYING || time > TITLE_SECONDS) restart();
            Building home = demo.home();
            double cx = home == null ? camera.worldWidth() / 2 : home.x();
            double cy = home == null ? camera.worldHeight() / 2 : home.y();
            camera.resize(width, Field.HEIGHT);
            camera.jumpTo(cx + Math.sin(time * 0.05) * 140, cy + Math.sin(time * 0.037) * 80);
        }

        @Override
        public void render(Graphics2D g, double alpha) {
            painter.paint(g, demo, terrain, camera, effects, ColonyPainter.Marks.NONE, alpha);
        }

        @Override
        public void renderOverlay(Graphics2D g, double alpha) {
            hud.veil(g);
            hud.text(g, msg.get("window.title"), 120, 64, true, Hud.AMBER);
            hud.text(g, msg.get("title.subtitle"), 150, 14, false, Hud.CHALK);
            hud.text(g, msg.get("title.start"), 210, 18, false, Hud.CHALK);
            hud.text(g, msg.get("title.mouse"), 262, 12, false, Hud.CHALK);
            hud.text(g, msg.get("title.keys"), 282, 12, false, Hud.CHALK);
            if (slot.exists()) hud.text(g, msg.get("title.load"), 302, 12, false, Hud.CHALK);
            hud.text(g, msg.get("title.screen"), 330, 11, false, Hud.GREY);
        }

        Game demo() {
            return demo;
        }
    }

    /** Loads the saved settlement and plays it; false, with a buzz, when there is none. */
    private boolean load() {
        Optional<Game> saved = slot.load(rules);
        if (saved.isEmpty()) {
            effects.no();
            return false;
        }
        scenes.replace(new PlayScene(saved.get()));
        return true;
    }

    // --8<-- [start:choice]
    /** Three cards, one for each mission; one opens when the one before it is won. */
    final class ChoiceScene implements Scene {
        private final Map<Integer, Game> fields = new HashMap<>();
        private final Map<Integer, Camera> cameras = new HashMap<>();
        private double time;

        ChoiceScene() {
            effects.clear();
            chosen = Math.min(chosen, progress.open());
        }

        @Override
        public void update(double seconds) {
            time += seconds;
            if (controls.wasPressed(Action.BACK)) {
                scenes.replace(new TitleScene());
                return;
            }
            if (controls.wasPressed(Action.LOAD) && load()) return;
            int before = chosen;
            int open = Math.min(progress.open(), levels.size());
            if (controls.wasPressed(Action.LEFT)) chosen = Math.max(1, chosen - 1);
            if (controls.wasPressed(Action.RIGHT)) chosen = Math.min(open, chosen + 1);
            int card = cardAt(mouse.x(), mouse.y());
            if (mouse.wasMoved() && card > 0 && card <= open) chosen = card;
            if (chosen != before) effects.tick();
            boolean clicked = mouse.wasPressed(MouseEvent.BUTTON1) && card > 0 && card <= open;
            if (controls.wasPressed(Action.OK) || clicked) {
                scenes.replace(new PlayScene(newGame(chosen)));
                return;
            }
            terrain(chosen);
        }

        /** The map of a mission as it is when the snow of the first spring has gone, and its ground. */
        private Game field(int number) {
            return fields.computeIfAbsent(number, n -> {
                Game field = new Game(levels.get(n - 1), rules, 1, false);
                for (int i = 0; i < PREVIEW_SECONDS * UPDATES_PER_SECOND; i++) field.update(1.0 / UPDATES_PER_SECOND);
                return field;
            });
        }

        private Terrain terrain(int number) {
            return previews.computeIfAbsent(number, n -> {
                Terrain terrain = new Terrain(levels.get(n - 1));
                terrain.update(field(n));
                return terrain;
            });
        }
        // --8<-- [end:choice]

        int cardAt(double x, double y) {
            if (Double.isNaN(x) || y < Hud.CARD_TOP || y > Hud.CARD_TOP + Hud.CARD_HEIGHT) return 0;
            for (int i = 0; i < levels.size(); i++) {
                double left = hud.cardX(i, levels.size());
                if (x >= left && x < left + Hud.CARD_WIDTH) return i + 1;
            }
            return 0;
        }

        @Override
        public void render(Graphics2D g, double alpha) {
            Level level = levels.get(chosen - 1);
            Game field = field(chosen);
            Terrain terrain = terrain(chosen);
            Camera camera = cameras.computeIfAbsent(chosen, n -> wideCamera(level));
            camera.resize(width, Field.HEIGHT);
            double wayX = camera.worldWidth() - camera.viewWidth();
            double along = wayX <= 0 ? 0 : (time * TITLE_SPEED) % (2 * wayX);
            camera.jumpTo(camera.viewWidth() / 2 + (along < wayX ? along : 2 * wayX - along), field.home().y());
            painter.paint(g, field, terrain, camera, effects, ColonyPainter.Marks.NONE, alpha);
        }

        @Override
        public void renderOverlay(Graphics2D g, double alpha) {
            hud.veil(g);
            hud.text(g, msg.get("select.title"), 70, 30, true, Hud.AMBER);
            for (int i = 0; i < levels.size(); i++) {
                int number = i + 1;
                hud.card(g, hud.cardX(i, levels.size()), Hud.CARD_TOP, rules.mission(number), number <= progress.open(),
                        number == chosen, progress);
            }
            hud.text(g, msg.get("select.keys"), 330, 12, false, Hud.CHALK);
        }

        int chosen() {
            return chosen;
        }
    }

    // --8<-- [start:play]
    /**
     * A mission. The panel lays out buildings; a click on the map places them, and then the road from the
     * new flag is laid, tile by tile as the mouse shows it, a click for each piece. A click on a house, a
     * flag or a road chooses it. The map moves with the arrow keys, the middle mouse button held down, the
     * mouse at the edge of the screen, or a click on the minimap.
     */
    final class PlayScene implements Scene {
        private final Game game;
        private final Camera camera;
        private final Terrain terrain;
        private final Minimap minimap;
        private BuildingType.Tab tab = BuildingType.Tab.BASIC;
        private Building building;
        private int flag = -1;
        private RoadNetwork.Road road;
        /** The building the player looks for a place for, where it would stand, and whether it may. */
        private BuildingType placing;
        private Cell place;
        private Game.Placement placement;
        /** Laying roads: from which flag, and the way the mouse shows. */
        private boolean roads;
        private int roadFrom = -1;
        private List<Cell> plan = List.of();
        private boolean planOk;
        private int speed = 1;
        private boolean counts;
        private double dragX = Double.NaN;
        private double dragY = Double.NaN;
        private boolean minimapDrag;
        private String message;
        private Color messageColour = Hud.CHALK;
        private double messageShown = Double.MAX_VALUE;
        private final Map<String, Double> nagged = new HashMap<>();
        private int minimapSignature = Integer.MIN_VALUE;
        private double minimapAge = Double.MAX_VALUE;
        private double ended;
        private boolean best;
        private int built;
        private int made;
        private int paved;
        private int burned;
        private int sheepTaken;

        PlayScene(Game settlement) {
            game = settlement;
            Level level = settlement.level();
            camera = ColonyGame.this.camera(level);
            terrain = ColonyGame.this.terrain(settlement);
            minimap = new Minimap(Hud.MINIMAP.x, Hud.MINIMAP.y, Hud.MINIMAP.width, Hud.MINIMAP.height, level.width(),
                    level.height(), Field.TILE);
            Building home = settlement.home();
            if (home != null) camera.jumpTo(home.x(), home.y());
            effects.clear();
            effects.music();
            say(msg.format("play.mission", level.number(), hud.name(level.number())), Hud.AMBER);
            paintMinimap();
        }

        @Override
        public void update(double seconds) {
            if (game.phase() != Game.Phase.PLAYING) {
                ended += seconds;
                if (ended >= END_SECONDS) {
                    scenes.push(new ResultScene(this));
                    return;
                }
            }
            if (controls.wasPressed(Action.PAUSE)
                    || controls.wasPressed(Action.BACK) && placing == null && !roads && building == null && flag < 0
                    && road == null) {
                scenes.push(new PauseScene(this));
                return;
            }
            if (controls.wasPressed(Action.LOAD) && load()) return;
            camera.resize(Field.VIEW_WIDTH + extra(), Field.VIEW_HEIGHT);
            input(seconds);
            for (int i = 0; i < speed; i++) step(seconds);
            effects.update(seconds, game, camera);
            terrain.update(game);
            minimapAge += seconds;
            if (minimapAge >= MINIMAP_SECONDS) paintMinimap();
            messageShown += seconds;
            for (Map.Entry<String, Double> nag : nagged.entrySet()) nag.setValue(nag.getValue() + seconds);
        }

        /** One step of the rules, what it did, and the choice after it. */
        void step(double seconds) {
            game.update(seconds);
            effects.react(game, camera, true);
            for (Event event : game.events()) tell(event);
            if (building != null && game.building(building.id()) == null) building = null;
            if (flag >= 0 && !game.roads().hasFlag(flag)) flag = -1;
            if (road != null) road = game.roads().road(road.id());
            if (roadFrom >= 0 && !game.roads().hasFlag(roadFrom)) roadFrom = -1;
        }
        // --8<-- [end:play]

        /** The minimap again, when the land, the roads or the border changed, or the snow came or went. */
        private void paintMinimap() {
            double snow = Math.round(Terrain.snow(game) * 5) / 5.0;
            int signature = game.landVersion() * 31 + game.roads().version() * 17 + game.land().version() * 7 + (int) (snow * 5);
            minimapAge = 0;
            if (signature == minimapSignature) return;
            minimapSignature = signature;
            for (int row = 0; row < game.level().height(); row++) {
                for (int col = 0; col < game.level().width(); col++) {
                    minimap.paint(col, row, Hud.minimapPixel(game, col, row, snow));
                }
            }
        }

        // --8<-- [start:tell]
        /** What the player hears of: the message at the bottom of the map, and what the end shows. */
        private void tell(Event event) {
            switch (event) {
                case Event.Built(Building b) -> {
                    built++;
                    say(msg.format("message.built", name(b)), Hud.GREEN);
                }
                case Event.NoTools(Building b) -> nag("tools", msg.format(b.done() ? "message.tools" : "message.builders", name(b)));
                case Event.Made(Building b, var good) -> made++;
                case Event.Paved(int cells) -> paved += cells;
                case Event.Settled(Building b) -> say(msg.get("message.settled"), Hud.GREEN);
                case Event.Season(int season, int year) -> say(msg.format("message.season." + season, year + 1),
                        season == 3 ? Hud.AMBER : Hud.CHALK);
                case Event.Burning(Building b) -> say(msg.format("message.fire", name(b)), Hud.RED);
                case Event.Doused(Building b) -> say(msg.format("message.doused", name(b)), Hud.GREEN);
                case Event.Burned(Building b) -> {
                    burned++;
                    say(msg.format("message.burned", name(b)), Hud.RED);
                }
                case Event.Wolf wolf -> say(msg.get("message.wolf"), Hud.RED);
                case Event.Barked(Building farm, boolean horn) -> say(msg.get(horn ? "message.horn" : "message.dog"), Hud.GREEN);
                case Event.SheepTaken(Building farm) -> {
                    sheepTaken++;
                    say(msg.get("message.sheep"), Hud.RED);
                }
                case Event.Lamb(Building farm) -> say(msg.get("message.lamb"), Hud.CHALK);
                case Event.Over(boolean won) -> {
                    if (won) {
                        best = progress.won(game.level().number(), (int) game.time(), levels.size());
                        chosen = Math.min(levels.size(), game.level().number() + 1);
                    }
                }
                default -> {
                }
            }
        }
        // --8<-- [end:tell]

        private String name(Building b) {
            return msg.get("building." + b.type().id());
        }

        private void say(String text, Color colour) {
            message = text;
            messageColour = colour;
            messageShown = 0;
        }

        /** A message that would come again and again: once, and again only after a while. */
        private void nag(String key, String text) {
            Double since = nagged.get(key);
            if (since != null && since < NAG_SECONDS) return;
            nagged.put(key, 0.0);
            say(text, Hud.AMBER);
        }

        // --8<-- [start:input]
        private void input(double seconds) {
            double mx = mouse.x();
            double my = mouse.y();
            boolean onMap = !Double.isNaN(mx) && mx < Field.VIEW_WIDTH + extra() && my >= Field.BAR;
            // The panel is laid out for the narrowest screen and moved to the right edge: so is the mouse.
            double px = mx - extra();
            double wx = camera.worldX(mx);
            double wy = camera.worldY(my);
            Cell hover = onMap ? new Cell((int) Math.floor(wx / Field.TILE), (int) Math.floor(wy / Field.TILE)) : null;
            if (hover != null && !game.contains(hover.col(), hover.row())) hover = null;

            if (controls.wasPressed(Action.BACK)) back();
            if (controls.wasPressed(Action.SAVE)) {
                slot.save(game);
                say(msg.get("message.saved"), Hud.CHALK);
                effects.tick();
            }
            if (controls.wasPressed(Action.FASTER)) faster(1);
            if (controls.wasPressed(Action.SLOWER)) faster(-1);
            if (controls.wasPressed(Action.COUNTS)) counts = !counts;
            if (controls.wasPressed(Action.ROAD)) roadMode(!roads);
            if (controls.wasPressed(Action.TEAR_DOWN)) tearDown();
            if (controls.wasPressed(Action.FLAG) && hover != null) {
                if (game.placeFlag(hover)) effects.tick();
                else effects.no();
            }

            placing(onMap, wx, wy);
            planning(hover);
            if (mouse.wasPressed(MouseEvent.BUTTON1)) {
                if (onMap && hover != null) leftClick(hover);
                else if (!onMap && px >= Field.VIEW_WIDTH) panelClick(px, my);
            }
            if (mouse.wasPressed(MouseEvent.BUTTON3) && onMap) back();
            if (minimapDrag && mouse.isDown(MouseEvent.BUTTON1) && !Double.isNaN(mx)) {
                camera.jumpTo(minimap.worldX(Math.clamp(px, minimap.x(), minimap.x() + minimap.width())),
                        minimap.worldY(Math.clamp(my, minimap.y(), minimap.y() + minimap.height())));
            }
            if (!mouse.isDown(MouseEvent.BUTTON1)) minimapDrag = false;
            pan(seconds, mx, my);
        }
        // --8<-- [end:input]

        /** Escape or the right button: one step back, from placing or a road to the choice, from the choice to nothing. */
        private void back() {
            if (placing != null) placing = null;
            else if (roadFrom >= 0) roadFrom = -1;
            else if (roads) roadMode(false);
            else choose(null, -1, null);
        }

        private void faster(int by) {
            int i = 0;
            while (SPEEDS[i] != speed) i++;
            int next = SPEEDS[Math.clamp(i + by, 0, SPEEDS.length - 1)];
            if (next != speed) effects.tick();
            speed = next;
        }

        private void roadMode(boolean on) {
            roads = on;
            placing = null;
            roadFrom = on && flag >= 0 ? flag : on && building != null ? building.flag() : -1;
            plan = List.of();
            if (on) choose(null, -1, null);
            effects.tick();
        }

        private void choose(Building b, int f, RoadNetwork.Road r) {
            building = b;
            flag = f;
            road = r;
        }

        /** Where the building would stand, its middle under the mouse. */
        private void placing(boolean onMap, double wx, double wy) {
            if (placing == null || !onMap) {
                place = null;
                return;
            }
            int col = (int) Math.floor(wx / Field.TILE - (placing.width() - 1) / 2.0);
            int row = (int) Math.floor(wy / Field.TILE - (placing.height() - 1) / 2.0);
            place = new Cell(col, row);
            placement = game.placement(placing, col, row);
        }

        // --8<-- [start:planning]
        /** The road the mouse shows: the shortest way from the flag to the tile under it. */
        private void planning(Cell hover) {
            if (!roads || roadFrom < 0 || hover == null) {
                plan = List.of();
                return;
            }
            if (!plan.isEmpty() && plan.getLast().equals(hover)) return;
            plan = game.plan(roadFrom, hover);
            planOk = !plan.isEmpty() && game.canPave(roadFrom, plan) == Game.Paving.OK;
        }
        // --8<-- [end:planning]

        // --8<-- [start:click]
        /**
         * The left button on the map. Placing: the site is laid out, and the road from its flag begins.
         * Laying roads: the first click picks the flag (or puts one down), each next one lays the road shown
         * and goes on from its end, until it meets the roads there are. Else it chooses what is there.
         */
        private void leftClick(Cell cell) {
            if (placing != null) {
                if (place == null) return;
                Game.Placement result = game.found(placing, place.col(), place.row());
                if (result != Game.Placement.OK) {
                    refuse(result);
                    return;
                }
                Building site = game.buildingAt(place.col(), place.row());
                placing = null;
                if (site != null && game.roads().roads(site.flag()).isEmpty()) {
                    roads = true;
                    roadFrom = site.flag();
                    choose(null, -1, null);
                    say(msg.get("message.road"), Hud.CHALK);
                }
                return;
            }
            if (roads) {
                road(cell);
                return;
            }
            Building there = game.buildingAt(cell.col(), cell.row());
            int at = game.roads().flagAt(cell);
            RoadNetwork.Road on = game.roads().roadAt(cell);
            if (there != null) choose(there, -1, null);
            else if (at >= 0) choose(null, at, null);
            else if (on != null) choose(null, -1, on);
            else choose(null, -1, null);
            if (there != null || at >= 0 || on != null) effects.tick();
        }

        private void road(Cell cell) {
            if (roadFrom < 0) {
                int at = game.roads().flagAt(cell);
                Building there = game.buildingAt(cell.col(), cell.row());
                if (at < 0 && there != null) at = there.flag();
                if (at < 0 && game.placeFlag(cell)) at = game.roads().flagAt(cell);
                if (at >= 0) {
                    roadFrom = at;
                    effects.tick();
                } else {
                    effects.no();
                }
                return;
            }
            if (plan.isEmpty()) {
                effects.no();
                return;
            }
            Cell end = plan.getLast();
            boolean joins = game.roads().flagAt(end) >= 0 || game.roads().roadAt(end) != null;
            Game.Paving paving = game.pave(roadFrom, plan);
            if (paving != Game.Paving.OK) {
                refuse(paving);
                return;
            }
            plan = List.of();
            roadFrom = joins ? -1 : game.roads().flagAt(end);
        }
        // --8<-- [end:click]

        // --8<-- [start:panel]
        /** A click on the panel: the minimap, the tabs, the build list, the buttons. */
        private void panelClick(double mx, double my) {
            if (minimap.contains(mx, my)) {
                camera.jumpTo(minimap.worldX(mx), minimap.worldY(my));
                minimapDrag = true;
                return;
            }
            BuildingType.Tab clicked = Hud.tabAt(mx, my);
            if (clicked != null) {
                if (clicked != tab) effects.tick();
                tab = clicked;
            } else if (Hud.ROAD.contains(mx, my)) {
                roadMode(!roads);
            } else if (Hud.TEAR_DOWN.contains(mx, my)) {
                tearDown();
            } else if (Hud.MENU.contains(mx, my)) {
                scenes.push(new PauseScene(this));
            } else {
                int cell = Hud.cellAt(mx, my);
                List<BuildingType> types = Hud.buildings(game, tab);
                if (cell >= 0 && cell < types.size()) {
                    placing = placing == types.get(cell) ? null : types.get(cell);
                    roads = false;
                    roadFrom = -1;
                    choose(null, -1, null);
                    effects.tick();
                }
            }
        }
        // --8<-- [end:panel]

        /** Pulls down the chosen building, or takes away the chosen flag or road. */
        private void tearDown() {
            boolean done = false;
            if (building != null) done = game.demolish(building);
            else if (flag >= 0) done = game.removeFlag(flag);
            else if (road != null) done = game.removeRoad(road);
            if (done) {
                choose(null, -1, null);
                effects.tick();
            } else {
                effects.no();
            }
        }

        private void refuse(Game.Placement why) {
            effects.no();
            say(msg.get("place." + why.name().toLowerCase()), Hud.RED);
        }

        private void refuse(Game.Paving why) {
            effects.no();
            say(msg.get("pave." + why.name().toLowerCase()), Hud.RED);
        }

        /** The map moves by the keys, by the edge of the screen, and by dragging it with the middle button. */
        private void pan(double seconds, double mx, double my) {
            boolean dragging = mouse.isDown(MouseEvent.BUTTON2);
            double dx = controls.axis(Action.LEFT, Action.RIGHT) * PAN_SPEED * seconds;
            double dy = controls.axis(Action.UP, Action.DOWN) * PAN_SPEED * seconds;
            if (mouse.isInside() && !dragging && !minimapDrag && !Double.isNaN(mx)) {
                if (mx < EDGE) dx -= PAN_SPEED * seconds;
                if (mx > width - EDGE) dx += PAN_SPEED * seconds;
                if (my < EDGE) dy -= PAN_SPEED * seconds;
                if (my > Field.HEIGHT - EDGE) dy += PAN_SPEED * seconds;
            }
            if (dragging && !Double.isNaN(dragX)) {
                dx += dragX - mx;
                dy += dragY - my;
            }
            dragX = dragging ? mx : Double.NaN;
            dragY = dragging ? my : Double.NaN;
            camera.moveBy(dx, dy);
        }

        ColonyPainter.Marks marks() {
            return new ColonyPainter.Marks(building, flag, road, placing, place, placement, plan, planOk,
                    roads ? roadFrom : -1);
        }

        @Override
        public void render(Graphics2D g, double alpha) {
            painter.paint(g, game, terrain, camera, effects, marks(), alpha);
        }

        @Override
        public void renderOverlay(Graphics2D g, double alpha) {
            hud.bar(g, game, speed);
            hud.panel(g, game, minimap, camera,
                    new Hud.View(tab, building, flag, road, placing, roads, speed, mouse.x() - extra(),
                            mouse.y()), alpha);
            if (counts) {
                hud.corner(g, msg.format("play.counts", game.people().size(), game.wares().size(),
                        game.roads().flags().size(), effects.particles()), Field.BAR + 12);
            }
            hud.message(g, message, messageShown, messageColour);
        }

        Game game() {
            return game;
        }

        Camera camera() {
            return camera;
        }

        Terrain terrain() {
            return terrain;
        }

        Minimap minimap() {
            return minimap;
        }

        BuildingType placing() {
            return placing;
        }

        BuildingType.Tab tab() {
            return tab;
        }

        boolean roads() {
            return roads;
        }

        int roadFrom() {
            return roadFrom;
        }

        List<Cell> plan() {
            return plan;
        }

        Building building() {
            return building;
        }

        int flag() {
            return flag;
        }

        RoadNetwork.Road road() {
            return road;
        }

        int speed() {
            return speed;
        }

        String message() {
            return messageShown < Hud.MESSAGE_SECONDS ? message : null;
        }

        Hud.Tally tally() {
            return new Hud.Tally(game.settlers(), built, made, paved, burned, sheepTaken);
        }
    }

    // --8<-- [start:pause]
    /** The settlement stands still: go on, save it, load the saved one, or leave it. */
    final class PauseScene implements Scene {
        enum Choice { CONTINUE, SAVE, LOAD, LEAVE }

        private final PlayScene play;
        private int highlighted;
        private String note;

        PauseScene(PlayScene play) {
            this.play = play;
        }

        @Override
        public void update(double seconds) {
            if (controls.wasPressed(Action.PAUSE)) {
                scenes.pop();
                return;
            }
            if (controls.wasPressed(Action.BACK)) {
                choose(Choice.LEAVE);
                return;
            }
            if (controls.wasPressed(Action.SAVE)) choose(Choice.SAVE);
            if (controls.wasPressed(Action.LOAD)) {
                choose(Choice.LOAD);
                return;
            }
            int before = highlighted;
            if (controls.wasPressed(Action.UP)) highlighted = Math.max(0, highlighted - 1);
            if (controls.wasPressed(Action.DOWN)) highlighted = Math.min(Choice.values().length - 1, highlighted + 1);
            int row = rowAt(mouse.x(), mouse.y());
            if (mouse.wasMoved() && row >= 0) highlighted = row;
            if (highlighted != before) effects.tick();
            if (controls.wasPressed(Action.OK) || mouse.wasPressed(MouseEvent.BUTTON1) && row >= 0) {
                choose(Choice.values()[highlighted]);
            }
        }
        // --8<-- [end:pause]

        private void choose(Choice choice) {
            switch (choice) {
                case CONTINUE -> scenes.pop();
                case SAVE -> {
                    slot.save(play.game);
                    note = msg.get("message.saved");
                    effects.tick();
                }
                case LOAD -> {
                    if (!load()) note = msg.get("pause.nothing");
                }
                case LEAVE -> scenes.replace(new ChoiceScene());
            }
        }

        int rowAt(double x, double y) {
            if (Double.isNaN(x) || Math.abs(x - width / 2) > 90) return -1;
            int row = (int) Math.floor((y - PAUSE_TOP + PAUSE_ROW * 0.7) / PAUSE_ROW);
            return row >= 0 && row < Choice.values().length ? row : -1;
        }

        @Override
        public void render(Graphics2D g, double alpha) {
        }

        @Override
        public void renderOverlay(Graphics2D g, double alpha) {
            hud.veil(g);
            hud.text(g, msg.get("pause.title"), 120, 36, true, Hud.CHALK);
            String[] keys = {"pause.continue", "pause.save", "pause.load", "pause.leave"};
            for (int i = 0; i < keys.length; i++) {
                hud.text(g, msg.get(keys[i]), PAUSE_TOP + i * PAUSE_ROW, 15, i == highlighted,
                        i == highlighted ? Hud.AMBER : Hud.CHALK);
            }
            if (note != null) hud.text(g, note, PAUSE_TOP + 4 * PAUSE_ROW + 10, 12, false, Hud.GREEN);
        }

        int highlighted() {
            return highlighted;
        }
    }

    /** The end of a mission: the settlement stays behind the result; Enter goes on or tries again. */
    final class ResultScene implements Scene {
        private final PlayScene play;
        private double shown;

        ResultScene(PlayScene play) {
            this.play = play;
        }

        @Override
        public void update(double seconds) {
            effects.update(seconds, null, null);
            shown += seconds;
            if (shown < 0.6) return;     // a short wait, so a click meant for the settlement does not skip the result
            if (controls.wasPressed(Action.BACK)) {
                scenes.replace(new ChoiceScene());
                return;
            }
            if (controls.wasPressed(Action.OK) || mouse.wasPressed(MouseEvent.BUTTON1)) {
                int number = play.game.level().number();
                boolean won = play.game.phase() == Game.Phase.WON;
                if (won && number == levels.size()) scenes.replace(new ChoiceScene());
                else scenes.replace(new PlayScene(newGame(won ? number + 1 : number)));
            }
        }

        @Override
        public void render(Graphics2D g, double alpha) {
        }

        @Override
        public void renderOverlay(Graphics2D g, double alpha) {
            hud.result(g, play.game, play.tally(), play.best, play.game.level().number() == levels.size());
        }
    }
}
