package eu.amadeos.games.colony.ui.game;

import eu.amadeos.gameengine.camera.Camera;
import eu.amadeos.gameengine.motion.Wander;
import eu.amadeos.gameengine.road.RoadNetwork;
import eu.amadeos.gameengine.sprite.Sprite;
import eu.amadeos.gameengine.tile.Cell;
import eu.amadeos.games.colony.domain.Building;
import eu.amadeos.games.colony.domain.BuildingType;
import eu.amadeos.games.colony.domain.Field;
import eu.amadeos.games.colony.domain.Game;
import eu.amadeos.games.colony.domain.Good;
import eu.amadeos.games.colony.domain.Ground;
import eu.amadeos.games.colony.domain.Person;
import eu.amadeos.games.colony.domain.Rules;
import eu.amadeos.games.colony.domain.Ware;
import eu.amadeos.games.colony.domain.Wolf;
import eu.amadeos.games.colony.domain.Work;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Draws a settlement from the ground up: the land, the pasture and the fields, the shadows, then everything
 * that stands, sorted by where it touches the ground, so that the nearer covers the farther: trees, rocks,
 * houses and sites, flags with the goods waiting at them, people, sheep, the dog and the wolves, the posts of
 * the border. Over it the smoke, and the marks of what the player is doing. Nearly all of it is a copy of a
 * sprite.
 */
final class ColonyPainter {
    /**
     * What the player is doing, for the marks on the map; all of it may be {@code null}. {@code plan} is the
     * road that would be laid, {@code planOk} whether it may.
     */
    record Marks(Building building, int flag, RoadNetwork.Road road, BuildingType placing, Cell place, Game.Placement result,
                 List<Cell> plan, boolean planOk, int roadFrom) {
        static final Marks NONE = new Marks(null, -1, null, null, null, null, List.of(), false, -1);
    }

    static final Color OK = new Color(120, 240, 110, 80);
    static final Color BAD = new Color(240, 80, 60, 100);
    static final Color REACH = new Color(255, 255, 255, 120);
    static final Color PLAN = new Color(255, 255, 255, 220);
    static final Color PLAN_BAD = new Color(255, 110, 90, 230);
    static final Color BAR = new Color(0, 0, 0, 160);
    static final Color BAR_FILL = new Color(0xE8C83A);
    /** Every step, a moving thing's feet are drawn this far below its middle. */
    static final double FEET = 5;

    /** Something that stands, to be drawn in the order of {@code y}, where it touches the ground. */
    private record Thing(double y, double x, int kind, Object what) {
    }

    private static final int TREE = 0;
    private static final int ROCK = 1;
    private static final int HOUSE = 2;
    private static final int FLAG = 3;
    private static final int PERSON = 4;
    private static final int SHEEP = 5;
    private static final int DOG = 6;
    private static final int WOLF = 7;
    private static final int POST = 8;
    private static final int SHEPHERD = 9;

    private final Pictures pictures;
    private final List<Thing> things = new ArrayList<>();
    private final Rectangle2D.Double rectangle = new Rectangle2D.Double();
    private final BasicStroke dots = new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1,
            new float[]{0.1f, 3.4f}, 0);
    private final BasicStroke thin = new BasicStroke(0.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1,
            new float[]{2f, 2.5f}, 0);
    /** The windmills' sails: how far each has turned, and when it was last drawn. */
    private final Map<Integer, double[]> sails = new HashMap<>();
    /** The posts along the border, worked out again when the border moves. */
    private List<double[]> posts = List.of();
    private Game postsGame;
    private int postsVersion = -1;

    ColonyPainter(Rules rules) {
        this.pictures = new Pictures(rules);
    }

    Pictures pictures() {
        return pictures;
    }

    // --8<-- [start:paint]
    /** The settlement as the camera sees it, and the marks of what the player is doing. */
    void paint(Graphics2D g, Game game, Terrain terrain, Camera camera, Effects effects, Marks marks, double alpha) {
        Shape clip = g.getClip();
        AffineTransform screen = g.getTransform();
        g.clip(new Rectangle2D.Double(camera.screenLeft(), camera.screenTop(), camera.viewWidth(), camera.viewHeight()));
        g.translate(camera.screenLeft() - camera.x(alpha), camera.screenTop() - camera.y(alpha));
        int fromCol = Math.max(0, (int) Math.floor(camera.x(alpha) / Field.TILE) - 2);
        int fromRow = Math.max(0, (int) Math.floor(camera.y(alpha) / Field.TILE) - 1);
        int toCol = Math.min(game.level().width() - 1, (int) ((camera.x(alpha) + camera.viewWidth()) / Field.TILE) + 2);
        int toRow = Math.min(game.level().height() - 1, (int) ((camera.y(alpha) + camera.viewHeight()) / Field.TILE) + 4);
        boolean winter = Terrain.snow(game) > 0.5;
        double time = effects.time();

        terrain.render(g, camera, alpha);
        ground(g, game, camera, alpha, fromCol, fromRow, toCol, toRow, winter);
        effects.renderGround(g, camera, alpha);
        shadows(g, game, camera, alpha, fromCol, fromRow, toCol, toRow, time);
        things.clear();
        collect(game, camera, alpha, fromCol, fromRow, toCol, toRow);
        things.sort(Comparator.comparingDouble(Thing::y).thenComparingDouble(Thing::x));
        for (Thing thing : things) draw(g, game, thing, winter, time);
        effects.render(g, alpha);
        marks(g, game, marks, camera, alpha, fromCol, fromRow, toCol, toRow);
        g.setTransform(screen);
        g.setClip(clip);
    }
    // --8<-- [end:paint]

    /** What lies flat: the pastures, the fields, the ashes of burnt houses. */
    private void ground(Graphics2D g, Game game, Camera camera, double alpha, int fromCol, int fromRow, int toCol,
                        int toRow, boolean winter) {
        for (Building b : game.buildings()) {
            Sprite yard = pictures.yard(b.type());
            if (yard == null || !b.done() || !sees(camera, b, alpha)) continue;
            yard.draw(g, b.col() * Field.TILE - Houses.SIDE, b.row() * Field.TILE - Houses.RISE);
        }
        for (int row = fromRow; row <= toRow; row++) {
            for (int col = fromCol; col <= toCol; col++) {
                double field = game.field(col, row);
                if (field >= 0) {
                    boolean outside = !game.land().inside(col, row);
                    pictures.field(field, winter, outside, (col * 7 + row * 3) & 1).draw(g, col * Field.TILE, row * Field.TILE);
                }
                if (game.ash(col, row)) pictures.ashes[(col + row * 2) % pictures.ashes.length].draw(g, col * Field.TILE, row * Field.TILE);
            }
        }
    }

    /** Shadows first, so that nothing throws a shadow over a roof. */
    private void shadows(Graphics2D g, Game game, Camera camera, double alpha, int fromCol, int fromRow, int toCol,
                         int toRow, double time) {
        for (int row = fromRow; row <= toRow; row++) {
            for (int col = fromCol; col <= toCol; col++) {
                double tree = game.tree(col, row);
                if (tree < 0) continue;
                double[] at = treeAt(col, row);
                pictures.treeShadow(tree).draw(g, at[0] - 12, at[1] - 12);
            }
        }
        for (Building b : game.buildings()) {
            if (!b.done() || !sees(camera, b, alpha)) continue;
            pictures.shadow(b.type()).draw(g, b.col() * Field.TILE - Houses.SIDE, b.row() * Field.TILE - Houses.RISE);
            if (b.type().id().equals("windmill")) {
                pictures.sailsShadow.draw(g, b.col() * Field.TILE + Houses.HUB_X + 8, b.row() * Field.TILE + Houses.HUB_Y + 9,
                        sails(b, time));
            }
        }
    }

    /** Where the tree of a tile stands: near its middle, a little off, the same every time. */
    static double[] treeAt(int col, int row) {
        return new double[]{(col + 0.5) * Field.TILE + (Art.hash(col, row, 3) - 0.5) * 6,
                (row + 0.5) * Field.TILE + (Art.hash(col, row, 4) - 0.5) * 6};
    }

    private static boolean conifer(int col, int row) {
        return Art.noise(col / 5.0, row / 5.0, 41) + (Art.hash(col, row, 5) - 0.5) * 0.3 > 0.55;
    }

    private static boolean sees(Camera camera, Building b, double alpha) {
        BuildingType type = b.type();
        return camera.sees(b.col() * Field.TILE - Houses.SIDE - 20, b.row() * Field.TILE - Houses.RISE,
                type.width() * Field.TILE + 2 * Houses.SIDE + 40, type.height() * Field.TILE + Houses.RISE + 30, alpha);
    }

    // --8<-- [start:collect]
    /** Everything that stands in view, each with the line where it touches the ground. */
    private void collect(Game game, Camera camera, double alpha, int fromCol, int fromRow, int toCol, int toRow) {
        for (int row = fromRow; row <= toRow; row++) {
            for (int col = fromCol; col <= toCol; col++) {
                if (game.tree(col, row) >= 0) {
                    double[] at = treeAt(col, row);
                    things.add(new Thing(at[1] + 5, at[0], TREE, new int[]{col, row}));
                } else if (game.ground(col, row) == Ground.ROCK && game.stone(col, row) > 0) {
                    things.add(new Thing((row + 0.9) * Field.TILE, col * Field.TILE, ROCK, new int[]{col, row}));
                }
            }
        }
        for (Building b : game.buildings()) {
            if (!sees(camera, b, alpha)) continue;
            things.add(new Thing((b.row() + b.type().height()) * Field.TILE, b.x(), HOUSE, b));
            if (b.done() && b.type().work() == Work.GRAZE) {
                for (Wander sheep : b.herd()) things.add(new Thing(sheep.y() + 3, sheep.x(), SHEEP, sheep));
                if (b.staffed()) {
                    things.add(new Thing(b.row() * Field.TILE + 30, b.col() * Field.TILE + 12, SHEPHERD, b));
                    double[] dog = dog(b);
                    things.add(new Thing(dog[1] + 2, dog[0], DOG, b));
                }
            }
        }
        RoadNetwork roads = game.roads();
        for (int flag : roads.flags()) {
            Cell cell = roads.cell(flag);
            if (cell.col() < fromCol || cell.col() > toCol || cell.row() < fromRow || cell.row() > toRow) continue;
            things.add(new Thing((cell.row() + 0.5) * Field.TILE + FEET, (cell.col() + 0.5) * Field.TILE, FLAG, flag));
        }
        double left = camera.x(alpha) - 30;
        double top = camera.y(alpha) - 30;
        double right = camera.x(alpha) + camera.viewWidth() + 30;
        double bottom = camera.y(alpha) + camera.viewHeight() + 40;
        for (Person p : game.people()) {
            if (!p.visible()) continue;
            double x = p.x();
            double y = p.y() + FEET;
            if (x < left || x > right || y < top || y > bottom) continue;
            things.add(new Thing(y, x, PERSON, p));
        }
        for (Wolf wolf : game.wolves()) things.add(new Thing(wolf.y() + FEET, wolf.x(), WOLF, wolf));
        for (double[] post : posts(game)) {
            if (post[0] < left || post[0] > right || post[1] < top || post[1] > bottom) continue;
            things.add(new Thing(post[1], post[0], POST, post));
        }
    }
    // --8<-- [end:collect]

    private void draw(Graphics2D g, Game game, Thing thing, boolean winter, double time) {
        switch (thing.kind()) {
            case TREE -> {
                int[] at = (int[]) thing.what();
                double[] xy = treeAt(at[0], at[1]);
                boolean outside = !game.land().inside(at[0], at[1]);
                int variant = (int) (Art.hash(at[0], at[1], 6) * Pictures.VARIANTS);
                pictures.tree(conifer(at[0], at[1]), game.tree(at[0], at[1]), winter, outside, variant)
                        .draw(g, xy[0] - 14, xy[1] - 14);
            }
            case ROCK -> {
                int[] at = (int[]) thing.what();
                boolean outside = !game.land().inside(at[0], at[1]);
                pictures.boulders(game.stone(at[0], at[1]), winter, outside).draw(g, at[0] * Field.TILE - 4, at[1] * Field.TILE - 2);
            }
            case HOUSE -> house(g, (Building) thing.what(), time);
            case FLAG -> flag(g, game, (Integer) thing.what());
            case PERSON -> person(g, game, (Person) thing.what(), time);
            case SHEEP -> {
                Wander sheep = (Wander) thing.what();
                pictures.sheep[sheep.facingLeft() ? 1 : 0][winter ? 1 : 0].draw(g, sheep.x() - 6, sheep.y() - 5);
            }
            case SHEPHERD -> {
                Building farm = (Building) thing.what();
                Folk.Look look = Folk.worker(Work.GRAZE, Good.WOOL, farm.id());
                Sprite sprite = pictures.person(new Pictures.Pose(look, false, false, 0, false, Folk.Tool.CROOK, 0));
                sprite.draw(g, thing.x() - sprite.width() / 2, thing.y() - Pictures.FEET_Y);
            }
            case DOG -> {
                Building farm = (Building) thing.what();
                double[] dog = dog(farm);
                pictures.dog[dog[2] > 0 ? 1 : 0].draw(g, dog[0] - 6, dog[1] - 4);
            }
            case WOLF -> {
                Wolf wolf = (Wolf) thing.what();
                int stride = (int) (time * 8) % 2;
                pictures.wolf[wolf.facingLeft() ? 1 : 0][stride].draw(g, wolf.x() - 13, wolf.y() - 7);
            }
            case POST -> {
                double[] post = (double[]) thing.what();
                pictures.post.draw(g, post[0] - 2, post[1] - 7);
            }
            default -> {
            }
        }
    }

    /**
     * Where the dog is, and whether it faces left: by the shepherd, or for the first seconds after it drove
     * a wolf off, running out to where the wolf was and back.
     */
    private static double[] dog(Building farm) {
        double homeX = farm.col() * Field.TILE + 20;
        double homeY = farm.row() * Field.TILE + 31;
        double chase = Field.DOG_REST - farm.dog();
        if (farm.dog() <= 0 || chase > 5) return new double[]{homeX, homeY, 0};
        double out = chase < 1.5 ? chase / 1.5 : chase < 3 ? 1 : 1 - (chase - 3) / 2;
        double x = homeX + (farm.dogX() - homeX) * out;
        double y = homeY + (farm.dogY() - homeY) * out;
        boolean left = chase < 3 ? farm.dogX() < homeX : farm.dogX() > homeX;
        return new double[]{x, y, left ? 1 : 0};
    }

    // --8<-- [start:house]
    /**
     * A house; a site with the house rising out of it as far as it is built, the goods brought for it and how
     * far it is; the windmill's sails; and flames on a house that burns.
     */
    private void house(Graphics2D g, Building b, double time) {
        BuildingType type = b.type();
        double x = b.col() * Field.TILE;
        double y = b.row() * Field.TILE;
        double w = type.width() * Field.TILE;
        double h = type.height() * Field.TILE;
        Sprite picture = pictures.building(type);
        if (!b.done()) {
            pictures.site(type).draw(g, x, y);
            if (b.built() > 0) {
                Shape clip = g.getClip();
                double rise = (h + Houses.RISE) * b.built();
                g.clip(new Rectangle2D.Double(x - Houses.SIDE, y + h - rise, w + 2 * Houses.SIDE, rise + 8));
                picture.draw(g, x - Houses.SIDE, y - Houses.RISE);
                g.setClip(clip);
            }
            siteGoods(g, b, x, y + h);
            rectangle.setRect(x + 3.5, y - 6.5, w - 7, 3);
            g.setColor(BAR);
            g.fill(rectangle);
            rectangle.setRect(x + 4, y - 6, (w - 8) * b.built(), 2);
            g.setColor(BAR_FILL);
            g.fill(rectangle);
            return;
        }
        picture.draw(g, x - Houses.SIDE, y - Houses.RISE);
        if (type.id().equals("windmill")) {
            pictures.sails.draw(g, x + Houses.HUB_X, y + Houses.HUB_Y, sails(b, time));
        }
        if (b.burning()) {
            double part = Math.min(1, b.fire() / Field.BURNS + 0.25);
            int count = (int) Math.round((type.width() + 1) * part * 2);
            for (int i = 0; i < count; i++) {
                double fx = x + w * (i + 0.5) / count;
                double fy = y + h * 0.35 + Art.hash(b.id(), i, 9) * h * 0.3;
                int frame = (int) (time * 10 + i * 2) % Pictures.FLAMES;
                Sprite flame = pictures.flames[frame];
                flame.draw(g, fx - flame.width() / 2, fy - flame.height());
            }
        }
    }
    // --8<-- [end:house]

    /** The goods brought for a site, laid out in front of it. */
    private void siteGoods(Graphics2D g, Building b, double x, double bottom) {
        int n = 0;
        for (Good good : List.of(Good.PLANK, Good.STONE)) {
            for (int i = 0; i < b.count(good); i++) {
                Sprite sprite = pictures.good(good);
                sprite.draw(g, x + 1 + (n % 4) * 7, bottom - 9 - (n / 4) * 3.5);
                n++;
            }
        }
    }

    /** How far the sails of a windmill have turned: they turn while the mill grinds. */
    private double sails(Building mill, double time) {
        double[] state = sails.computeIfAbsent(mill.id(), id -> new double[]{Art.hash(id, 0, 7) * 3, time});
        double passed = Math.clamp(time - state[1], 0, 0.5);
        state[1] = time;
        if (mill.working()) state[0] += passed * 1.4;
        return state[0];
    }

    // --8<-- [start:flag]
    /** A flag, and the goods that wait at it, set down around its foot. */
    private void flag(Graphics2D g, Game game, int flag) {
        Cell cell = game.roads().cell(flag);
        double x = (cell.col() + 0.5) * Field.TILE;
        double y = (cell.row() + 0.5) * Field.TILE + FEET;
        List<Ware> waiting = game.waitingAt(flag);
        for (int i = 0; i < waiting.size(); i++) {
            Sprite sprite = pictures.good(waiting.get(i).good());
            double gx = x + (i % 2 == 0 ? -6 - (i / 2 % 2) * 1.5 : 4 + (i / 2 % 2) * 1.5);
            double gy = y - 2 - (i / 2) * 2.6;
            sprite.draw(g, gx - 4.5, gy - 3.5);
        }
        pictures.flag.draw(g, x - 3, y - 12);
    }
    // --8<-- [end:flag]

    // --8<-- [start:person]
    /**
     * A person: in their job's clothes, walking with the step of the clock, the load held up over the head,
     * from behind when walking away, swinging the tool while at work.
     */
    private void person(Graphics2D g, Game game, Person p, double time) {
        Folk.Look look;
        Folk.Tool tool = Folk.Tool.NONE;
        switch (p.role()) {
            case CARRIER -> look = Folk.carrier(p.id() % Folk.CARRIER_LOOKS);
            case BUILDER -> {
                look = Folk.builder(p.id());
                if (p.working()) tool = Folk.Tool.HAMMER;
            }
            default -> {
                Building b = game.building(p.job());
                Work work = b == null ? Work.MAKE : b.type().work();
                Good makes = b == null || b.type().recipe() == null ? null : b.type().recipe().makes();
                look = Folk.worker(work, makes, p.id());
                if (p.working()) tool = Folk.tool(work);
            }
        }
        boolean walking = p.walking();
        int step = walking ? new int[]{0, 1, 0, -1}[(int) (time * 8 + p.id()) & 3] : 0;
        Good load = p.load();
        boolean armsUp = load != null && (p.task() == Person.Task.CARRY || p.task() == Person.Task.BACK);
        int frame = tool == Folk.Tool.NONE ? 0 : (int) (time * 3 + p.id()) & 1;
        Pictures.Pose pose = new Pictures.Pose(look, armsUp, p.facingUp(), step, p.facingLeft(), tool, frame);
        Sprite sprite = pictures.person(pose);
        double x = p.x();
        double y = p.y() + FEET;
        sprite.draw(g, x - sprite.width() / 2, y - Pictures.FEET_Y);
        if (armsUp) {
            Sprite good = pictures.good(load);
            good.draw(g, x - good.width() / 2, y - 16.4 * Folk.SCALE / 1.3 - 4.5);
        }
    }
    // --8<-- [end:person]

    /** Posts along the border, where a tile inside meets one outside: on land, not in water or on mountains. */
    private List<double[]> posts(Game game) {
        if (game == postsGame && game.land().version() == postsVersion) return posts;
        postsGame = game;
        postsVersion = game.land().version();
        List<double[]> list = new ArrayList<>();
        int[][] sides = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        for (Cell cell : game.land().border()) {
            if (!game.ground(cell.col(), cell.row()).walkable() && game.ground(cell.col(), cell.row()) != Ground.ROCK) continue;
            for (int[] side : sides) {
                int c = cell.col() + side[0];
                int r = cell.row() + side[1];
                if (!game.contains(c, r) || game.land().inside(c, r)) continue;
                if ((cell.col() + cell.row()) % 2 != 0 && side[1] == 0 || (cell.col() + cell.row()) % 2 != 0 && side[0] == 0) {
                    continue;
                }
                list.add(new double[]{(cell.col() + 0.5 + side[0] * 0.5) * Field.TILE, (cell.row() + 0.5 + side[1] * 0.5) * Field.TILE + 2});
            }
        }
        posts = list;
        return posts;
    }

    // --8<-- [start:marks]
    /**
     * The chosen building, flag or road; the building that waits for its place, and where it would fit; the
     * road the player is laying.
     */
    private void marks(Graphics2D g, Game game, Marks marks, Camera camera, double alpha, int fromCol, int fromRow,
                       int toCol, int toRow) {
        Building b = marks.building();
        if (b != null && game.building(b.id()) != null) {
            BuildingType type = b.type();
            double x = b.col() * Field.TILE;
            double y = b.row() * Field.TILE;
            Art.brackets(g, x - 2, y - Houses.RISE * 0.5, type.width() * Field.TILE + 4, type.height() * Field.TILE + Houses.RISE * 0.5 + 2);
            if (type.radius() > 0 && type.work() != Work.STORE) reach(g, b, type);
        }
        if (marks.flag() >= 0 && game.roads().hasFlag(marks.flag())) {
            Cell cell = game.roads().cell(marks.flag());
            Art.brackets(g, cell.col() * Field.TILE + 1, cell.row() * Field.TILE - 6, Field.TILE - 2, Field.TILE + 6);
        }
        RoadNetwork.Road road = marks.road();
        if (road != null && game.roads().road(road.id()) != null) {
            g.setColor(PLAN);
            g.setStroke(dots);
            line(g, road.cells());
        }
        if (marks.placing() != null) placing(g, game, marks, fromCol, fromRow, toCol, toRow);
        if (!marks.plan().isEmpty()) {
            g.setColor(marks.planOk() ? PLAN : PLAN_BAD);
            g.setStroke(dots);
            line(g, marks.plan());
            Cell end = marks.plan().getLast();
            pictures.markers[marks.planOk() ? 1 : 0].draw(g, (end.col() + 0.5) * Field.TILE - 4, (end.row() + 0.5) * Field.TILE - 4);
        }
        if (marks.roadFrom() >= 0 && game.roads().hasFlag(marks.roadFrom())) {
            Cell cell = game.roads().cell(marks.roadFrom());
            g.setColor(PLAN);
            g.setStroke(new BasicStroke(1f));
            g.draw(new Ellipse2D.Double(cell.col() * Field.TILE + 2, cell.row() * Field.TILE + 4, Field.TILE - 4, Field.TILE - 4));
        }
    }
    // --8<-- [end:marks]

    private void line(Graphics2D g, List<Cell> cells) {
        for (int i = 1; i < cells.size(); i++) {
            Cell a = cells.get(i - 1);
            Cell c = cells.get(i);
            g.draw(new Line2D.Double((a.col() + 0.5) * Field.TILE, (a.row() + 0.5) * Field.TILE + 2,
                    (c.col() + 0.5) * Field.TILE, (c.row() + 0.5) * Field.TILE + 2));
        }
    }

    /** How far the worker goes, or the land a tower claims: a dotted ring round the flag. */
    private void reach(Graphics2D g, Building b, BuildingType type) {
        Cell door = new Cell(b.col() + type.flagColumn(), b.row() + type.height());
        double cx = (door.col() + 0.5) * Field.TILE;
        double cy = (door.row() + 0.5) * Field.TILE;
        if (type.work() == Work.WATCH) {
            cx = b.x();
            cy = b.y();
        }
        double r = (type.radius() + 0.5) * Field.TILE;
        g.setColor(REACH);
        g.setStroke(thin);
        g.draw(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
    }

    // --8<-- [start:placing]
    /**
     * The building the player places: little houses on every other tile where it would fit, then the house
     * itself under the mouse, faint, on tiles green where it may stand and red where not, and its flag.
     */
    private void placing(Graphics2D g, Game game, Marks marks, int fromCol, int fromRow, int toCol, int toRow) {
        BuildingType type = marks.placing();
        for (int row = fromRow; row <= toRow; row++) {
            for (int col = fromCol; col <= toCol; col++) {
                if (((col + row) & 1) != 0 || game.placement(type, col, row) != Game.Placement.OK) continue;
                double x = (col + type.width() / 2.0) * Field.TILE;
                double y = (row + type.height() / 2.0) * Field.TILE;
                pictures.markers[1].draw(g, x - 4, y - 4);
            }
        }
        Cell at = marks.place();
        if (at == null) return;
        Composite before = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.6f));
        pictures.building(type).draw(g, at.col() * Field.TILE - Houses.SIDE, at.row() * Field.TILE - Houses.RISE);
        g.setComposite(before);
        boolean ok = marks.result() == Game.Placement.OK;
        g.setColor(ok ? OK : BAD);
        for (int dy = 0; dy < type.height(); dy++) {
            for (int dx = 0; dx < type.width(); dx++) {
                rectangle.setRect((at.col() + dx) * Field.TILE + 0.5, (at.row() + dy) * Field.TILE + 0.5, Field.TILE - 1, Field.TILE - 1);
                g.fill(rectangle);
            }
        }
        double fx = (at.col() + type.flagColumn() + 0.5) * Field.TILE;
        double fy = (at.row() + type.height() + 0.5) * Field.TILE + FEET;
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.8f));
        pictures.flag.draw(g, fx - 3, fy - 12);
        g.setComposite(before);
        if (type.work() == Work.WATCH) {
            double r = (type.radius() + 0.5) * Field.TILE;
            double cx = (at.col() + 0.5) * Field.TILE;
            double cy = (at.row() + 0.5) * Field.TILE;
            g.setColor(REACH);
            g.setStroke(thin);
            g.draw(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
        }
    }
    // --8<-- [end:placing]
}
