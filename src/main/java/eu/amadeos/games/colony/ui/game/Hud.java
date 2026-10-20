package eu.amadeos.games.colony.ui.game;

import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.gameengine.camera.Camera;
import eu.amadeos.gameengine.economy.Recipe;
import eu.amadeos.gameengine.minimap.Minimap;
import eu.amadeos.gameengine.road.RoadNetwork;
import eu.amadeos.gameengine.sprite.Sprite;
import eu.amadeos.gameengine.tile.Cell;
import eu.amadeos.games.colony.domain.Building;
import eu.amadeos.games.colony.domain.BuildingType;
import eu.amadeos.games.colony.domain.Field;
import eu.amadeos.games.colony.domain.Game;
import eu.amadeos.games.colony.domain.Good;
import eu.amadeos.games.colony.domain.Ground;
import eu.amadeos.games.colony.domain.Mission;
import eu.amadeos.games.colony.domain.Person;
import eu.amadeos.games.colony.domain.Ware;
import eu.amadeos.games.colony.domain.Work;
import eu.amadeos.games.colony.repository.Progress;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * The overlay: the goods bar along the top, the panel on the right with the minimap, the build list, what
 * is chosen and the buttons; the messages over the map; and the screens of the title, the choice of a
 * mission, the pause and the end. The canvas draws it after the world, at the full size of the screen, so
 * the letters are sharp in every render mode.
 */
final class Hud {
    static final Color CHALK = new Color(0xF2E8D4);
    static final Color AMBER = new Color(0xF8C878);
    static final Color LABEL = new Color(0xC8A870);
    static final Color GREEN = new Color(0x7AE070);
    static final Color RED = new Color(0xF0705A);
    static final Color GREY = new Color(0xA89878);
    static final Color BRASS = new Color(0x8A6A3A);
    static final Color DARK = new Color(0x14100C);
    static final Color BOX = new Color(0x16120E);
    static final Color VEIL = new Color(0, 0, 0, 150);
    static final Color SHADOW = new Color(0, 0, 0, 150);
    static final Color VIEW = new Color(255, 255, 255, 220);
    static final double MESSAGE_SECONDS = 4;
    static final double LEFT = Field.VIEW_WIDTH;
    static final double TOP = Field.BAR;
    /** The goods bar: a box for each good, then the settlers, the season, the speed and the clock. */
    static final double GOODS_LEFT = 6;
    static final double GOODS_STEP = 43;
    static final double SETTLERS_X = GOODS_LEFT + Good.values().length * GOODS_STEP + 4;
    static final double SEASON_X = SETTLERS_X + 54;
    static final double SPEED_X = SEASON_X + 38;
    /** The parts of the panel, in logical coordinates. */
    static final Rectangle2D.Double MINIMAP = new Rectangle2D.Double(LEFT + 8, TOP + 6, 112, 83);
    static final double TAB_TOP = 116;
    static final double TAB_WIDTH = 36;
    static final double TAB_STEP = 38;
    static final double GRID_TOP = 131;
    static final double CELL_WIDTH = 55;
    static final double CELL_HEIGHT = 44;
    static final int CELLS = 6;
    static final Rectangle2D.Double INFO = new Rectangle2D.Double(LEFT + 8, 270, 112, 96);
    static final Rectangle2D.Double ROAD = new Rectangle2D.Double(LEFT + 8, 372, 55, 12);
    static final Rectangle2D.Double TEAR_DOWN = new Rectangle2D.Double(LEFT + 65, 372, 55, 12);
    static final Rectangle2D.Double MENU = new Rectangle2D.Double(LEFT + 8, 386, 112, 12);
    static final double CARD_WIDTH = 176;
    static final double CARD_HEIGHT = 150;
    static final double CARD_GAP = 16;
    static final double CARD_TOP = 110;
    static final Color HOUSE_DOT = new Color(0x8FB4F0);
    static final Color ROAD_DOT = new Color(0xD8B888);

    private final Messages msg;
    private final Pictures pictures;
    private final Map<Integer, Font> fonts = new HashMap<>();
    private final Rectangle2D.Double rectangle = new Rectangle2D.Double();
    private final float[] grainY = new float[300];
    private final int[] grainAlpha = new int[300];
    /** What stays the same from frame to frame, drawn once: the bar, the panel, the faces of tabs, buttons and cells. */
    private final Map<String, Sprite> faces = new HashMap<>();
    private final Sprite settler;

    Hud(Messages msg, Pictures pictures) {
        this.msg = msg;
        this.pictures = pictures;
        Random grain = new Random(6);
        for (int i = 0; i < grainY.length; i++) {
            grainY[i] = (float) (TOP + grain.nextDouble() * (Field.HEIGHT - TOP));
            grainAlpha[i] = grain.nextInt(10);
        }
        settler = Sprite.drawn(12, 16, (g, w, h) -> {
            smooth(g);
            g.translate(w / 2, 14.5);
            g.scale(0.6, 0.6);
            Folk.person(g, Folk.carrier(0), false, false, 0);
        });
    }

    /**
     * What the panel shows that the game does not know: the tab, the chosen building, flag or road, the
     * building looking for its place, whether roads are being laid, the speed and where the mouse is.
     */
    record View(BuildingType.Tab tab, Building building, int flag, RoadNetwork.Road road, BuildingType placing,
                boolean roads, int speed, double mouseX, double mouseY) {
        boolean over(Rectangle2D.Double box) {
            return box.contains(mouseX, mouseY);
        }
    }

    // ---- the goods bar ---------------------------------------------------------------------------

    // --8<-- [start:bar]
    /** The bar along the top: the goods in the storehouses, the settlers, the season and year, the speed, the clock. */
    void bar(Graphics2D g, Game game, int speed) {
        smooth(g);
        face(g, "bar", 0, 0, Field.WIDTH, TOP, (sg, w, h) -> {
            sg.setPaint(new GradientPaint(0, 0, new Color(0x3A3128), 0, (float) TOP, new Color(0x241E18)));
            sg.fill(new Rectangle2D.Double(0, 0, Field.WIDTH, TOP));
            sg.setColor(BRASS);
            sg.fill(new Rectangle2D.Double(0, TOP - 1, Field.WIDTH, 1));
            for (int i = 0; i < Good.values().length; i++) {
                sg.setColor(BOX);
                sg.fill(new RoundRectangle2D.Double(GOODS_LEFT + i * GOODS_STEP, 3.5, 40, 13, 4, 4));
            }
            sg.fill(new RoundRectangle2D.Double(SETTLERS_X, 3.5, 50, 13, 4, 4));
            sg.fill(new RoundRectangle2D.Double(SEASON_X, 3.5, 34, 13, 4, 4));
        });
        g.setFont(font(9, true));
        Mission mission = game.mission();
        for (Good good : Good.values()) {
            double x = GOODS_LEFT + good.ordinal() * GOODS_STEP;
            pictures.bigGood(good).draw(g, x + 2, 4.5);
            int count = game.stores(good);
            int wanted = mission.goods().getOrDefault(good, 0);
            g.setColor(count == 0 ? RED : wanted > 0 && count >= wanted ? GREEN : CHALK);
            String text = Integer.toString(count);
            g.drawString(text, (float) (x + 37 - g.getFontMetrics().stringWidth(text)), 13.5f);
            if (wanted > 0) {
                g.setColor(AMBER);
                g.fill(new Ellipse2D.Double(x + 37.5, 2.5, 3, 3));
            }
        }
        settler.draw(g, SETTLERS_X + 2, 2);
        g.setFont(font(8, true));
        g.setColor(game.settlers() >= mission.goal() ? GREEN : CHALK);
        String settlers = game.settlers() + "/" + mission.goal();
        g.drawString(settlers, (float) (SETTLERS_X + 47 - g.getFontMetrics().stringWidth(settlers)), 13f);
        int season = game.seasons().season();
        pictures.seasons[season].draw(g, SEASON_X + 2, 4.5);
        int year = game.seasons().year() + 1;
        String years = mission.years() > 0 ? year + "/" + mission.years() : Integer.toString(year);
        g.setColor(mission.years() > 0 && year == mission.years() ? AMBER : CHALK);
        g.drawString(years, (float) (SEASON_X + 31 - g.getFontMetrics().stringWidth(years)), 13f);
        if (speed > 1) {
            g.setColor(BOX);
            g.fill(new RoundRectangle2D.Double(SPEED_X, 4.5, 20, 11, 4, 4));
            g.setColor(new Color(0xB8E08A));
            centred(g, "x" + speed, SPEED_X + 10, 12.8);
        }
        g.setColor(LABEL);
        g.setFont(font(9, true));
        String clock = clock((int) game.time());
        g.drawString(clock, (float) (Field.WIDTH - 6 - g.getFontMetrics().stringWidth(clock)), 13.5f);
    }
    // --8<-- [end:bar]

    // ---- the panel -------------------------------------------------------------------------------

    // --8<-- [start:panel]
    /** The panel on the right: the minimap, the build list, what is chosen and the buttons. */
    void panel(Graphics2D g, Game game, Minimap minimap, Camera camera, View view, double alpha) {
        smooth(g);
        face(g, "panel", LEFT, TOP, Field.PANEL, Field.HEIGHT - TOP, (sg, w, h) -> paintBackground(sg));
        minimap(g, game, minimap, camera, alpha);
        BuildingType.Tab[] tabs = BuildingType.Tab.values();
        for (int i = 0; i < tabs.length; i++) {
            Rectangle2D.Double box = tab(i);
            tab(g, box, msg.get("tab." + tabs[i].name().toLowerCase()), tabs[i] == view.tab(), view.over(box));
        }
        List<BuildingType> types = buildings(game, view.tab());
        for (int i = 0; i < CELLS; i++) {
            Rectangle2D.Double cell = cell(i);
            if (i >= types.size()) empty(g, cell);
            else item(g, cell, types.get(i), types.get(i) == view.placing(), view.over(cell));
        }
        info(g, game, view);
        boolean tear = view.building() != null && view.building().type().buildable() || view.road() != null
                || view.flag() >= 0 && game.roads().hasFlag(view.flag());
        button(g, ROAD, msg.get("panel.road"), true, view.roads(), view.over(ROAD));
        button(g, TEAR_DOWN, msg.get("panel.tear"), tear, false, tear && view.over(TEAR_DOWN));
        button(g, MENU, msg.get("panel.menu"), true, false, view.over(MENU));
    }
    // --8<-- [end:panel]

    /** The buildings of a tab that the player may build, in the order of the rules. */
    static List<BuildingType> buildings(Game game, BuildingType.Tab tab) {
        return game.rules().buildings(tab).stream().filter(BuildingType::buildable).limit(CELLS).toList();
    }

    static Rectangle2D.Double tab(int i) {
        return new Rectangle2D.Double(LEFT + 8 + i * TAB_STEP, TAB_TOP, TAB_WIDTH, 12);
    }

    /** The tab under a point, or {@code null}. */
    static BuildingType.Tab tabAt(double x, double y) {
        for (int i = 0; i < BuildingType.Tab.values().length; i++) if (tab(i).contains(x, y)) return BuildingType.Tab.values()[i];
        return null;
    }

    static Rectangle2D.Double cell(int i) {
        return new Rectangle2D.Double(LEFT + 8 + (i % 2) * 57, GRID_TOP + (i / 2) * 46, CELL_WIDTH, CELL_HEIGHT);
    }

    /** The cell of the build list under a point, or -1. */
    static int cellAt(double x, double y) {
        for (int i = 0; i < CELLS; i++) if (cell(i).contains(x, y)) return i;
        return -1;
    }

    /**
     * A part of the screen drawn once and then only copied: its gradients cost more than the rest of the
     * frame together. {@code drawing} draws at {@code x}, {@code y} as if on the screen.
     */
    private void face(Graphics2D g, String key, double x, double y, double w, double h, Sprite.Drawing drawing) {
        faces.computeIfAbsent(key, k -> Sprite.drawn(w, h, (sg, sw, sh) -> {
            smooth(sg);
            sg.translate(-x, -y);
            drawing.draw(sg, sw, sh);
        })).draw(g, x, y);
    }

    private void paintBackground(Graphics2D g) {
        g.setPaint(new GradientPaint((float) LEFT, 0, new Color(0x3A3128), (float) Field.WIDTH, 0, new Color(0x241E18)));
        rectangle.setRect(LEFT, TOP, Field.PANEL, Field.HEIGHT - TOP);
        g.fill(rectangle);
        g.setStroke(new BasicStroke(0.4f));
        for (int i = 0; i < grainY.length; i++) {
            g.setColor(new Color(255, 230, 190, grainAlpha[i]));
            g.draw(new Line2D.Double(LEFT, grainY[i], Field.WIDTH, grainY[i]));
        }
        g.setColor(BRASS);
        rectangle.setRect(LEFT, TOP, 1, Field.HEIGHT - TOP);
        g.fill(rectangle);
        g.setColor(new Color(0x120E0A));
        rectangle.setRect(LEFT + 1, TOP, 1, Field.HEIGHT - TOP);
        g.fill(rectangle);
    }

    // --8<-- [start:minimap]
    /** The map, small: the land, the roads and the houses, and the view. */
    private void minimap(Graphics2D g, Game game, Minimap minimap, Camera camera, double alpha) {
        g.setColor(new Color(0x050302));
        g.fill(MINIMAP);
        minimap.render(g);
        for (Building b : game.buildings()) {
            minimap.box(g, b.col() * Field.TILE, b.row() * Field.TILE, b.type().width() * Field.TILE,
                    b.type().height() * Field.TILE, b.burning() ? RED : HOUSE_DOT);
        }
        minimap.view(g, camera, alpha, VIEW);
        bevel(g, MINIMAP.x - 1, MINIMAP.y - 1, MINIMAP.width + 2, MINIMAP.height + 2, true);
    }
    // --8<-- [end:minimap]

    /** The colour of a tile on the minimap: its ground, tree, field or road, dim outside the border, white with snow. */
    static int minimapPixel(Game game, int col, int row, double snow) {
        Ground ground = game.ground(col, row);
        int colour = switch (ground) {
            case ROCK -> game.stone(col, row) > 0 ? 0x9A968A : 0x7A766A;
            case WATER -> 0x3A6E8A;
            case MOUNTAIN -> 0x6A6058;
            default -> 0x6E9A40;
        };
        if (game.tree(col, row) >= 0) colour = 0x2E5E26;
        else if (game.field(col, row) >= 0) colour = 0xC8B060;
        if (ground != Ground.WATER && snow > 0) colour = Art.mix(colour, game.tree(col, row) >= 0 ? 0x9AAAB0 : 0xE8ECF0, snow * 0.8);
        Cell cell = new Cell(col, row);
        if (game.roads().roadAt(cell) != null || game.roads().flagAt(cell) >= 0) colour = ROAD_DOT.getRGB() & 0xFFFFFF;
        return game.land().inside(col, row) ? colour : Art.shade(colour, 0.5);
    }

    private void tab(Graphics2D g, Rectangle2D.Double box, String label, boolean active, boolean hover) {
        face(g, "tab " + box.x + active + hover, box.x, box.y, box.width, box.height, (sg, w, h) -> {
            sg.setPaint(new GradientPaint(0, (float) box.y, active ? new Color(0x6A5A44) : hover ? new Color(0x4A3E30)
                    : new Color(0x3A3026), 0, (float) (box.y + box.height), active ? new Color(0x4A3E2E) : new Color(0x2A231C)));
            sg.fill(box);
            bevel(sg, box.x, box.y, box.width, box.height, false);
        });
        g.setFont(font(7, true));
        g.setColor(active ? CHALK : GREY);
        centred(g, label, box.getCenterX(), box.y + 9);
    }

    private void empty(Graphics2D g, Rectangle2D.Double cell) {
        g.setColor(new Color(0x1E1914));
        g.fill(cell);
        bevel(g, cell.x, cell.y, cell.width, cell.height, true);
    }

    // --8<-- [start:item]
    /** A building to lay out: its picture, its name and what it costs in planks and stone. */
    private void item(Graphics2D g, Rectangle2D.Double cell, BuildingType type, boolean chosen, boolean hover) {
        face(g, "cell " + cell.x + " " + cell.y + " " + type.id() + hover, cell.x, cell.y, cell.width, cell.height,
                (sg, w, h) -> {
                    sg.setColor(new Color(0x1A1612));
                    sg.fill(cell);
                    sg.clip(new Rectangle2D.Double(cell.x + 1, cell.y + 1, cell.width - 2, cell.height - 2));
                    sg.setPaint(new GradientPaint((float) cell.x, (float) cell.y, hover ? new Color(0x92BA60)
                            : new Color(0x7EA650), (float) (cell.x + cell.width), (float) (cell.y + cell.height),
                            new Color(0x4E7A30)));
                    sg.fill(cell);
                    pictures.icon(type).draw(sg, cell.x + 1, cell.y + 2);
                    sg.setColor(new Color(0, 0, 0, 150));
                    sg.fill(new Rectangle2D.Double(cell.x, cell.y + cell.height - 11, cell.width, 11));
                    sg.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    sg.setFont(font(7, true));
                    sg.setColor(CHALK);
                    sg.drawString(msg.get("building." + type.id()), (float) cell.x + 3, (float) (cell.y + cell.height - 3));
                    cost(sg, type, cell.x + cell.width - 1, cell.y + 1);
                });
        bevel(g, cell.x, cell.y, cell.width, cell.height, false);
        if (chosen) {
            g.setColor(GREEN);
            g.setStroke(new BasicStroke(1f));
            g.draw(new Rectangle2D.Double(cell.x + 0.5, cell.y + 0.5, cell.width - 1, cell.height - 1));
        }
    }
    // --8<-- [end:item]

    /** What a building costs, as a pill of goods and counts with its right edge at {@code right}. */
    private void cost(Graphics2D g, BuildingType type, double right, double y) {
        List<Good> goods = type.cost().keySet().stream().sorted().toList();
        double width = goods.size() * 15;
        double x = right - width;
        g.setColor(new Color(0, 0, 0, 140));
        g.fill(new RoundRectangle2D.Double(x, y, width, 10, 4, 4));
        g.setFont(font(7, true));
        for (int i = 0; i < goods.size(); i++) {
            pictures.good(goods.get(i)).draw(g, x + 1 + i * 15, y + 1.5);
            g.setColor(AMBER);
            g.drawString(Integer.toString(type.cost().get(goods.get(i))), (float) (x + 10 + i * 15), (float) y + 8);
        }
    }

    // --8<-- [start:info]
    /** The box under the build list: the chosen building, flag or road; the building being placed; or the mission. */
    private void info(Graphics2D g, Game game, View view) {
        g.setColor(DARK);
        g.fill(INFO);
        bevel(g, INFO.x, INFO.y, INFO.width, INFO.height, true);
        Shape clip = g.getClip();
        g.clip(INFO);
        if (view.placing() != null) placing(g, view.placing());
        else if (view.building() != null) building(g, game, view.building());
        else if (view.flag() >= 0 && game.roads().hasFlag(view.flag())) flag(g, game, view.flag());
        else if (view.road() != null) road(g, game, view.road());
        else if (view.roads()) help(g, msg.get("info.roads"), msg.get("info.roads.text"));
        else mission(g, game);
        g.setClip(clip);
    }
    // --8<-- [end:info]

    private double title(Graphics2D g, String title, String status, Color colour) {
        double x = INFO.x + 5;
        double y = INFO.y + 13;
        g.setFont(font(9, true));
        g.setColor(CHALK);
        g.drawString(title, (float) x, (float) y);
        if (status != null) {
            g.setFont(font(7, true));
            g.setColor(colour);
            g.drawString(status, (float) (INFO.x + INFO.width - 5 - g.getFontMetrics().stringWidth(status)), (float) y);
        }
        g.setColor(new Color(0x2A241E));
        rectangle.setRect(x, y + 5, INFO.width - 10, 0.8);
        g.fill(rectangle);
        return y + 18;
    }

    /** A line of the info box: a label on the left, a value on the right. */
    private void row(Graphics2D g, double y, String label, String value, Color colour) {
        g.setFont(font(7, false));
        g.setColor(GREY);
        g.drawString(label, (float) INFO.x + 5, (float) y);
        if (value == null) return;
        g.setColor(colour);
        g.drawString(value, (float) (INFO.x + INFO.width - 5 - g.getFontMetrics().stringWidth(value)), (float) y);
    }

    private void bar(Graphics2D g, double y, double part, Color from, Color to) {
        double x = INFO.x + 5;
        double w = INFO.width - 10;
        g.setColor(new Color(0x2A241E));
        rectangle.setRect(x, y, w, 6);
        g.fill(rectangle);
        g.setPaint(new GradientPaint((float) x, 0, from, (float) (x + w), 0, to));
        rectangle.setRect(x + 1, y + 1, (w - 2) * Math.clamp(part, 0, 1), 4);
        g.fill(rectangle);
    }

    /** A slot with a good and how many there are. */
    private void slot(Graphics2D g, double x, double y, Good good, String count, Color colour) {
        g.setColor(new Color(0x2A241E));
        rectangle.setRect(x, y, 13, 11);
        g.fill(rectangle);
        pictures.bigGood(good).draw(g, x, y);
        if (count == null) return;
        g.setFont(font(7, true));
        g.setColor(colour);
        g.drawString(count, (float) x + 14, (float) y + 9);
    }

    private void arrow(Graphics2D g, double x, double y) {
        g.setColor(LABEL);
        Path2D arrow = new Path2D.Double();
        arrow.moveTo(x, y - 1);
        arrow.lineTo(x + 6, y - 1);
        arrow.lineTo(x + 6, y - 3.5);
        arrow.lineTo(x + 10, y);
        arrow.lineTo(x + 6, y + 3.5);
        arrow.lineTo(x + 6, y + 1);
        arrow.lineTo(x, y + 1);
        arrow.closePath();
        g.fill(arrow);
    }

    // --8<-- [start:building-info]
    /** A building: how far its site is, or what goes in and comes out, whether it works and what holds it up. */
    private void building(Graphics2D g, Game game, Building b) {
        BuildingType type = b.type();
        String name = msg.get("building." + type.id());
        if (!b.done()) {
            double y = title(g, name, msg.get("info.site"), AMBER);
            List<Good> goods = type.cost().keySet().stream().sorted().toList();
            for (int i = 0; i < goods.size(); i++) {
                Good good = goods.get(i);
                int needed = type.cost().get(good);
                slot(g, INFO.x + 5 + i * 34, y - 4, good, Math.min(b.count(good), needed) + "/" + needed,
                        b.count(good) >= needed ? GREEN : CHALK);
            }
            row(g, y + 20, msg.get("info.built"), (int) Math.floor(b.built() * 100) + "%", CHALK);
            bar(g, y + 25, b.built(), new Color(0xC89040), new Color(0xF8D080));
            boolean builder = game.people().stream().anyMatch(p -> p.role() == Person.Role.BUILDER && p.job() == b.id());
            row(g, y + 45, msg.get("info.builder"), msg.get(builder ? "info.coming" : "info.nobody"), builder ? CHALK : AMBER);
            return;
        }
        String status;
        Color colour;
        boolean worker = !type.manned() || game.people().stream()
                .anyMatch(p -> p.role() == Person.Role.WORKER && p.job() == b.id());
        if (b.burning()) {
            status = msg.get("info.burning");
            colour = RED;
        } else if (!worker) {
            status = msg.get(type.tool() ? "info.notools" : "info.noworker");
            colour = AMBER;
        } else if (b.working() || type.work() == Work.STORE) {
            status = msg.get(type.work() == Work.STORE ? "info.open" : "info.working");
            colour = GREEN;
        } else {
            status = msg.get("info.waiting");
            colour = GREY;
        }
        double y = title(g, name, status, colour);
        switch (type.work()) {
            case STORE -> {
                row(g, y, msg.get("info.settlers"), Integer.toString(game.settlers()), CHALK);
                row(g, y + 11, msg.get("info.idle"), Integer.toString(game.idle()), CHALK);
                row(g, y + 22, msg.get("info.goods"), Integer.toString(b.stocked()), CHALK);
                text(g, msg.get("info.store.text"), y + 40);
                return;
            }
            case WATCH -> {
                row(g, y, msg.get("info.watch"), msg.get(worker ? "info.yes" : "info.no"), worker ? GREEN : AMBER);
                text(g, msg.get("info.watch.text"), y + 18);
                return;
            }
            default -> {
            }
        }
        Recipe<Good> recipe = type.recipe();
        if (recipe != null) {
            double x = INFO.x + 5;
            for (Set<Good> input : recipe.inputs()) {
                Good good = input.stream().filter(gd -> b.count(gd) > 0).findFirst().orElse(input.iterator().next());
                int count = 0;
                for (Good each : input) count += b.count(each);
                slot(g, x, y - 4, good, Integer.toString(count), count > 0 ? CHALK : RED);
                x += 25;
            }
            if (!recipe.inputs().isEmpty()) {
                arrow(g, x, y + 1.5);
                x += 13;
            }
            slot(g, x, y - 4, recipe.makes(), b.out() > 0 ? Integer.toString(b.out()) : null, CHALK);
            bar(g, y + 12, b.progress(), new Color(0xC89040), new Color(0xF8D080));
            y += 28;
        }
        if (type.work() == Work.GRAZE) {
            row(g, y, msg.get("info.sheep"), b.sheep() + "/" + Field.SHEEP, b.sheep() > 0 ? CHALK : RED);
            row(g, y + 11, msg.get("info.dog"), msg.get(b.dog() > 0 ? "info.dog.resting" : "info.dog.watching"), CHALK);
            y += 22;
        }
        if (type.work() == Work.SETTLE) {
            text(g, msg.get("info.residence.text"), y + 2);
            return;
        }
        if (type.manned()) row(g, y, msg.get("info.worker." + type.work().name().toLowerCase()),
                msg.get(worker ? "info.yes" : "info.no"), worker ? CHALK : AMBER);
        Building well = game.well(b);
        row(g, y + 11, msg.get("info.well"), msg.get(well != null ? "info.yes" : "info.no"), well != null ? CHALK : GREY);
    }
    // --8<-- [end:building-info]

    private void flag(Graphics2D g, Game game, int flag) {
        List<Ware> wares = game.waitingAt(flag);
        double y = title(g, msg.get("info.flag"), wares.size() + "/" + Field.FLAG_ROOM,
                wares.size() >= Field.FLAG_ROOM ? RED : CHALK);
        for (int i = 0; i < wares.size(); i++) {
            pictures.bigGood(wares.get(i).good()).draw(g, INFO.x + 5 + (i % 7) * 14.5, y - 4 + (i / 7) * 13);
        }
        text(g, msg.get("info.flag.text"), y + 26);
    }

    private void road(Graphics2D g, Game game, RoadNetwork.Road road) {
        Person carrier = game.carrier(road);
        double y = title(g, msg.get("info.road"), msg.format("info.length", road.length()), CHALK);
        String state = carrier == null ? msg.get("info.nobody")
                : carrier.task() == Person.Task.GOING ? msg.get("info.coming")
                : carrier.load() != null ? msg.get("info.carrying") : msg.get("info.ready");
        row(g, y, msg.get("info.carrier"), state, carrier == null ? AMBER : CHALK);
        if (carrier != null && carrier.load() != null) pictures.bigGood(carrier.load()).draw(g, INFO.x + INFO.width - 18, y + 4);
        text(g, msg.get("info.road.text"), y + 26);
    }

    private void placing(Graphics2D g, BuildingType type) {
        double y = title(g, msg.get("building." + type.id()), null, CHALK);
        cost(g, type, INFO.x + INFO.width - 5, INFO.y + 4);
        text(g, msg.get("about." + type.id()), y - 2);
    }

    private void help(Graphics2D g, String title, String text) {
        double y = title(g, title, null, CHALK);
        text(g, text, y - 2);
    }

    /** No choice: what the mission asks and how far the settlement has come. */
    private void mission(Graphics2D g, Game game) {
        Mission mission = game.mission();
        double y = title(g, msg.format("info.mission", mission.number()), null, CHALK);
        row(g, y, msg.get("info.settlers"), game.settlers() + "/" + mission.goal(),
                game.settlers() >= mission.goal() ? GREEN : CHALK);
        y += 11;
        for (Map.Entry<Good, Integer> goal : mission.goods().entrySet()) {
            int have = game.stores(goal.getKey());
            row(g, y, msg.get("good." + goal.getKey().id()), have + "/" + goal.getValue(), have >= goal.getValue() ? GREEN : CHALK);
            y += 11;
        }
        if (mission.years() > 0) {
            int left = mission.years() - game.seasons().year();
            row(g, y, msg.get("info.years"), Integer.toString(left), left <= 1 ? AMBER : CHALK);
            y += 11;
        }
        text(g, msg.get("info.mission.text"), y + 6);
    }

    /** A few lines of plain text, broken at spaces to fit the box. */
    private void text(Graphics2D g, String text, double y) {
        g.setFont(font(7, false));
        g.setColor(GREY);
        FontMetrics fm = g.getFontMetrics();
        double width = INFO.width - 10;
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (!line.isEmpty() && fm.stringWidth(line + " " + word) > width) {
                g.drawString(line.toString(), (float) INFO.x + 5, (float) y);
                y += 9;
                line.setLength(0);
            }
            if (!line.isEmpty()) line.append(' ');
            line.append(word);
        }
        if (!line.isEmpty()) g.drawString(line.toString(), (float) INFO.x + 5, (float) y);
    }

    private void button(Graphics2D g, Rectangle2D.Double box, String text, boolean enabled, boolean active, boolean hover) {
        face(g, "button " + box.x + " " + box.y + active + hover, box.x, box.y, box.width, box.height, (sg, w, h) -> {
            sg.setPaint(new GradientPaint(0, (float) box.y, active ? new Color(0x5A7A3A) : hover ? new Color(0x6A5A44)
                    : new Color(0x5A4C3A), 0, (float) (box.y + box.height), active ? new Color(0x34501E) : new Color(0x3A3026)));
            sg.fill(box);
            bevel(sg, box.x, box.y, box.width, box.height, active);
        });
        g.setFont(font(7, true));
        g.setColor(enabled ? CHALK : GREY);
        centred(g, text, box.getCenterX(), box.y + 9);
    }

    static void bevel(Graphics2D g, double x, double y, double w, double h, boolean sunk) {
        g.setStroke(new BasicStroke(1f));
        g.setColor(sunk ? DARK : new Color(0x6A5C4A));
        g.draw(new Line2D.Double(x + 0.5, y + 0.5, x + w - 0.5, y + 0.5));
        g.draw(new Line2D.Double(x + 0.5, y + 0.5, x + 0.5, y + h - 0.5));
        g.setColor(sunk ? new Color(0x6A5C4A) : DARK);
        g.draw(new Line2D.Double(x + 0.5, y + h - 0.5, x + w - 0.5, y + h - 0.5));
        g.draw(new Line2D.Double(x + w - 0.5, y + 0.5, x + w - 0.5, y + h - 0.5));
    }

    // ---- over the map ----------------------------------------------------------------------------

    /** The newest message at the bottom left of the map, with its badge, fading out in its last second. */
    void message(Graphics2D g, String line, double shown, Color colour) {
        if (line == null || shown >= MESSAGE_SECONDS) return;
        double a = Math.clamp(MESSAGE_SECONDS - shown, 0, 1);
        g.setFont(font(9, true));
        int width = g.getFontMetrics().stringWidth(line);
        g.setColor(new Color(0, 0, 0, (int) (120 * a)));
        g.fill(new RoundRectangle2D.Double(4, Field.HEIGHT - 21, width + 22, 15, 5, 5));
        g.setColor(new Color(colour.getRed(), colour.getGreen(), colour.getBlue(), (int) (255 * a)));
        g.fill(new Ellipse2D.Double(8.5, Field.HEIGHT - 17, 7, 7));
        g.setColor(new Color(0x3A2410));
        g.setFont(font(7, true));
        centred(g, "!", 12, Field.HEIGHT - 11.3);
        g.setFont(font(9, true));
        g.setColor(new Color(CHALK.getRed(), CHALK.getGreen(), CHALK.getBlue(), (int) (255 * a)));
        g.drawString(line, 20, (float) Field.HEIGHT - 10);
    }

    /** A line at the top left of the map, such as what F3 shows. */
    void corner(Graphics2D g, String line, double y) {
        g.setFont(font(8, true));
        g.setColor(SHADOW);
        g.drawString(line, 9, (float) y + 1);
        g.setColor(CHALK);
        g.drawString(line, 8, (float) y);
    }

    // ---- the screens -----------------------------------------------------------------------------

    // --8<-- [start:card]
    /** One mission on the choice screen: its number and name, what it asks, the fastest win, or a lock. */
    void card(Graphics2D g, double x, double y, Mission mission, boolean open, boolean chosen, Progress progress) {
        int number = mission.number();
        RoundRectangle2D.Double card = new RoundRectangle2D.Double(x, y, CARD_WIDTH, CARD_HEIGHT, 10, 10);
        g.setPaint(new GradientPaint((float) x, (float) y, chosen ? new Color(0x4E6A30) : new Color(0x3A3128), (float) x,
                (float) (y + CARD_HEIGHT), new Color(0x1E1812)));
        g.fill(card);
        g.setColor(chosen ? AMBER : BRASS);
        g.setStroke(new BasicStroke(chosen ? 1.6f : 1f));
        g.draw(card);
        double cx = x + CARD_WIDTH / 2;
        g.setFont(font(34, true));
        g.setColor(open ? AMBER : GREY);
        centred(g, Integer.toString(number), cx, y + 44);
        g.setFont(font(15, true));
        g.setColor(open ? CHALK : GREY);
        centred(g, name(number), cx, y + 68);
        g.setFont(font(10, false));
        if (!open) {
            lock(g, cx, y + 88);
            g.setColor(GREY);
            centred(g, msg.get("select.locked"), cx, y + 134);
            return;
        }
        g.setColor(GREY);
        centred(g, msg.get("mission." + number + ".text"), cx, y + 88);
        g.setColor(CHALK);
        centred(g, msg.format("select.goal", mission.goal()), cx, y + 104);
        String hard = (mission.years() > 0 ? msg.format("select.years", mission.years()) : msg.get("select.free"))
                + (mission.fire() ? " · " + msg.get("select.fire") : "") + (mission.wolves() ? " · " + msg.get("select.wolves") : "");
        g.setFont(font(9, false));
        g.setColor(GREY);
        centred(g, hard, cx, y + 118);
        int best = progress.best(number);
        g.setFont(font(10, false));
        g.setColor(best > 0 ? CHALK : GREY);
        centred(g, best > 0 ? msg.format("select.best", clock(best)) : msg.get("select.none"), cx, y + 138);
    }
    // --8<-- [end:card]

    static double cardX(int index, int count) {
        double left = (Field.WIDTH - count * CARD_WIDTH - (count - 1) * CARD_GAP) / 2;
        return left + index * (CARD_WIDTH + CARD_GAP);
    }

    private void lock(Graphics2D g, double cx, double y) {
        g.setColor(GREY);
        g.setStroke(new BasicStroke(3));
        g.draw(new RoundRectangle2D.Double(cx - 7, y - 2, 14, 16, 12, 12));
        rectangle.setRect(cx - 11, y + 7, 22, 16);
        g.fill(rectangle);
        g.setStroke(new BasicStroke(1));
    }

    /** What a mission's end shows besides won or lost. */
    record Tally(int settlers, int built, int made, int roads, int burned, int sheepTaken) {
    }

    /** The end of a mission: won or lost, how long it took, and what the settlement did. */
    void result(Graphics2D g, Game game, Tally tally, boolean best, boolean last) {
        veil(g);
        boolean won = game.phase() == Game.Phase.WON;
        text(g, msg.get(won ? "won.title" : "lost.title"), 100, 32, true, won ? AMBER : RED);
        text(g, msg.format("result.time", clock((int) game.time()), game.seasons().year() + 1), 135, 14, false, CHALK);
        if (won && best) text(g, msg.get("won.best"), 155, 12, true, GREEN);
        String[] rows = {"result.settlers", "result.built", "result.made", "result.roads", "result.burned", "result.sheep"};
        int[] values = {tally.settlers(), tally.built(), tally.made(), tally.roads(), tally.burned(), tally.sheepTaken()};
        double top = 185;
        for (int i = 0; i < rows.length; i++) {
            double y = top + i * 18;
            g.setFont(font(11, false));
            g.setColor(CHALK);
            String label = msg.get(rows[i]);
            g.drawString(label, (float) (Field.WIDTH / 2 + 10 - g.getFontMetrics().stringWidth(label)), (float) y);
            g.setFont(font(11, true));
            g.drawString(Integer.toString(values[i]), (float) Field.WIDTH / 2 + 30, (float) y);
        }
        text(g, msg.get(won ? (last ? "won.last" : "won.next") : "lost.again"), 320, 13, false, CHALK);
    }

    /** Minutes and seconds. */
    static String clock(int seconds) {
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    String name(int number) {
        return msg.get("mission." + number);
    }

    static void centred(Graphics2D g, String text, double x, double y) {
        FontMetrics fm = g.getFontMetrics();
        g.drawString(text, (float) (x - fm.stringWidth(text) / 2.0), (float) y);
    }

    private static void smooth(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }

    /** Darkens the screen so text over it stays readable. */
    void veil(Graphics2D g) {
        g.setColor(VEIL);
        rectangle.setRect(0, 0, Field.WIDTH, Field.HEIGHT);
        g.fill(rectangle);
    }

    /** A line of text centred on the screen, its baseline at {@code y}, with a shadow. */
    void text(Graphics2D g, String line, double y, int size, boolean bold, Color colour) {
        text(g, line, Field.WIDTH / 2, y, size, bold, colour);
    }

    void text(Graphics2D g, String line, double x, double y, int size, boolean bold, Color colour) {
        smooth(g);
        g.setFont(font(size, bold));
        float left = (float) (x - g.getFontMetrics().stringWidth(line) / 2.0);
        g.setColor(SHADOW);
        g.drawString(line, left + 1, (float) y + 1);
        g.setColor(colour);
        g.drawString(line, left, (float) y);
    }

    Font font(int size, boolean bold) {
        return fonts.computeIfAbsent(2 * size + (bold ? 1 : 0),
                key -> new Font(Font.SANS_SERIF, bold ? Font.BOLD : Font.PLAIN, size));
    }
}
