package eu.amadeos.games.colony.ui.game;

import eu.amadeos.games.colony.domain.Field;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.Random;

import static eu.amadeos.games.colony.ui.game.Art.radial;
import static eu.amadeos.games.colony.ui.game.Art.shade;

/**
 * The buildings of Colony, drawn in code: gabled houses seen from above and a little from the south, the
 * ridge from east to west, the north slope in the light and the front wall with its door and windows; the
 * round windmill, the lookout tower, the well, the pasture with its fence, and the site of a house going up.
 *
 * <p>A building is drawn with the top left corner of its tiles at 0, 0. Its roof rises above its tiles, up
 * to {@value #RISE} units, and its shadow is drawn on its own ({@link #shadow}), before everything that
 * stands, so that no shadow ever falls on a roof. The door of every house is above its flag.
 */
final class Houses {
    /** How far a building may rise above its tiles, and reach beyond them to the sides. */
    static final double RISE = 30;
    static final double SIDE = 8;

    static final int TILES = 0;
    static final int THATCH = 1;
    static final int SHINGLES = 2;

    private Houses() {
    }

    /** Where the door is, above the flag, in units from the left of the tiles. */
    static double door(int width) {
        return (width / 2 + 0.5) * Field.TILE;
    }

    // --8<-- [start:building]
    /** The building of kind {@code id}, {@code width} × {@code height} tiles, without its shadow. */
    static void building(Graphics2D g, String id, int width, int height) {
        double door = door(width);
        switch (id) {
            case "storehouse" -> {
                house(g, 7, -10, 40, 40, 12, 0xC8C0AE, 0xA8482E, TILES, false, new double[]{0.18, 0.72, 0.9}, door);
                chimney(g, 7 + 40 * 0.74, -6);
                tower(g, 0, -12, 15, 11, 31, 0xA8482E);
                banner(g, 13, -12);
                crates(g, 32, 31);
            }
            case "woodcutter" -> {
                house(g, 4, -4, 24, 28, 9, 0xD8C8A6, 0x7A6A58, SHINGLES, true, new double[]{0.3}, door);
                logPile(g, -2, 17, 0.75);
            }
            case "forester" -> {
                house(g, 8, -4, 22, 28, 9, 0xE6D8BA, 0xC8A050, THATCH, true, new double[]{0.3}, door);
                for (int i = 0; i < 3; i++) {
                    double x = 1.5 + i * 2.5;
                    double y = 14 + i * 6;
                    g.setColor(new Color(0x5A4026));
                    g.fill(new Ellipse2D.Double(x - 2, y - 1, 4, 2));
                    Art.tree(g, x, y - 1, false, 0.3, false, false, 40 + i);
                }
            }
            case "quarry" -> {
                house(g, 8, -4, 22, 28, 9, 0xB8B2A2, 0x7A6A58, SHINGLES, false, new double[]{0.3}, door);
                blocks(g, -1, 17, 0.7);
            }
            case "sawmill" -> {
                house(g, 1, -6, 30, 34, 11, 0xE6D8BA, 0x8A7A66, SHINGLES, true, new double[]{0.15}, door);
                saw(g, 1 + 30 * 0.45, 28 - 11);
                planks(g, -3, 30, 0.8);
            }
            case "tower" -> {
                tower(g, 1, -22, 14, 26, 37, 0x7A5A3A);
                banner(g, 8, -24);
            }
            case "well" -> {
                AffineTransform at = g.getTransform();
                g.translate(8, 13);
                g.scale(0.8, 0.8);
                well(g, 0, 0);
                g.setTransform(at);
            }
            case "fisher" -> {
                house(g, 4, -2, 24, 26, 9, 0xD8C8A6, 0xB89048, THATCH, true, new double[]{0.3}, door);
                rack(g, -2, 26);
            }
            case "farm" -> {
                house(g, 4, -4, 40, 30, 10, 0xE6D8BA, 0xD0A858, THATCH, true, new double[]{0.18, 0.75}, door);
                hay(g, 44, 26);
            }
            case "windmill" -> windmill(g, 16, 24);
            case "bakery" -> {
                house(g, 10, -4, 22, 30, 10, 0xEADCC0, 0xB0583A, TILES, true, new double[]{0.25}, door);
                chimney(g, 10 + 22 * 0.2, -1);
                oven(g, 0, 27);
            }
            case "sheepfarm" -> {
                house(g, 35, 6, 27, 38, 10, 0xEADCC0, 0xB89048, THATCH, true, new double[]{0.7}, door);
                fence(g, 0, 0, 32, height * Field.TILE, true);
            }
            case "weaver" -> {
                house(g, 3, -4, 26, 30, 10, 0xE8DDC8, 0x6A5E7A, SHINGLES, true, new double[]{0.28}, door);
                clothLine(g, -3, 14);
            }
            case "toolmaker" -> {
                house(g, 3, -4, 26, 30, 10, 0xA8A090, 0x5A4A42, SHINGLES, false, new double[]{0.28}, door);
                chimney(g, 3 + 26 * 0.15, -2);
                anvil(g, 1, 30);
            }
            case "residence" -> {
                house(g, 2, -6, 28, 32, 12, 0xF0E4C8, 0xB8482E, TILES, true, new double[]{0.2, 0.5}, door);
                chimney(g, 2 + 28 * 0.3, -4);
                garden(g, -3, 22);
            }
            default -> house(g, 2, -4, width * Field.TILE - 4, height * Field.TILE + 2, 10, 0xD8C8A6, 0x8A7A66,
                    SHINGLES, true, new double[]{0.3}, door);
        }
    }
    // --8<-- [end:building]

    /** The ground of a building that lies under everything else: the sheep farm's pasture with its fence. */
    static void yard(Graphics2D g, String id, int width, int height) {
        if (!id.equals("sheepfarm")) return;
        double h = height * Field.TILE;
        pastureGround(g, 0, 0, 32, h);
        fence(g, 0, 0, 32, h, false);
    }

    /** The shadow a building throws, from its tiles to the bottom right. */
    static void shadow(Graphics2D g, String id, int width, int height) {
        switch (id) {
            case "storehouse" -> {
                houseShadow(g, 7, -10, 40, 40, 12);
                houseShadow(g, 0, -4, 15, 35, 22);
            }
            case "woodcutter" -> houseShadow(g, 4, -4, 24, 28, 10);
            case "forester", "quarry" -> houseShadow(g, 8, -4, 22, 28, 10);
            case "sawmill" -> houseShadow(g, 1, -6, 30, 34, 11);
            case "tower" -> houseShadow(g, 1, -6, 14, 21, 26);
            case "well" -> {
                g.setColor(Art.SHADOW);
                g.fill(new Ellipse2D.Double(8 - 5.6 + 2, 13 - 4 + 2, 11.2, 8));
            }
            case "fisher" -> houseShadow(g, 4, -2, 24, 26, 10);
            case "farm" -> houseShadow(g, 4, -4, 40, 30, 10);
            case "windmill" -> {
                g.setColor(new Color(10, 20, 5, 85));
                g.fill(new Ellipse2D.Double(16 - 9 + 5, 24 - 7 + 5, 21, 16));
            }
            case "bakery" -> houseShadow(g, 10, -4, 22, 30, 10);
            case "sheepfarm" -> houseShadow(g, 35, 6, 27, 38, 10);
            case "weaver", "toolmaker" -> houseShadow(g, 3, -4, 26, 30, 10);
            case "residence" -> houseShadow(g, 2, -6, 28, 32, 12);
            default -> houseShadow(g, 2, -4, width * Field.TILE - 4, height * Field.TILE + 2, 10);
        }
    }

    // --8<-- [start:house]
    /**
     * A gabled house: the front wall {@code wall} high at the bottom of {@code x}, {@code y}, {@code w},
     * {@code h}, the roof above it; {@code roofKind} {@link #TILES}, {@link #THATCH} or {@link #SHINGLES};
     * {@code timber} for a timber frame, else dressed stone; the windows at parts of the width, the door at
     * {@code doorX} units from the left of the tiles.
     */
    static void house(Graphics2D g, double x, double y, double w, double h, double wall, int wallC, int roofC,
                      int roofKind, boolean timber, double[] windows, double doorX) {
        Random rnd = new Random((long) (x * 131 + y * 17 + w * 7 + roofC));
        double rh = h - wall;
        double wy = y + rh;
        g.setPaint(new GradientPaint((float) x, (float) wy, new Color(wallC), (float) x, (float) (y + h),
                new Color(shade(wallC, 0.72))));
        g.fill(new Rectangle2D.Double(x, wy, w, wall));
        if (timber) {
            g.setColor(new Color(0x5A3A22));
            g.fill(new Rectangle2D.Double(x, wy, 1.4, wall));
            g.fill(new Rectangle2D.Double(x + w - 1.4, wy, 1.4, wall));
            g.fill(new Rectangle2D.Double(x, wy + wall * 0.45, w, 0.9));
            g.fill(new Rectangle2D.Double(x, y + h - 1.2, w, 1.2));
            g.setStroke(new BasicStroke(0.8f));
            for (double bx = x + 7; bx < x + w - 5; bx += 9) {
                g.draw(new Line2D.Double(bx, wy + wall * 0.45, bx + 3.5, wy));
                g.draw(new Line2D.Double(bx + 3.5, y + h - 1, bx, wy + wall * 0.45 + 0.6));
            }
        } else {
            g.setStroke(new BasicStroke(0.3f));
            for (double sy = wy + 2.2; sy < y + h; sy += 2.4) {
                g.setColor(new Color(0, 0, 0, 50));
                g.draw(new Line2D.Double(x, sy, x + w, sy));
                double off = ((int) ((sy - wy) / 2.4) % 2) * 2.2;
                for (double sx = x + off; sx < x + w; sx += 4.4) g.draw(new Line2D.Double(sx, sy - 2.4, sx, sy));
            }
            for (int i = 0; i < w * wall / 10; i++) {
                g.setColor(new Color(255, 255, 255, 30 + rnd.nextInt(30)));
                g.fill(new Rectangle2D.Double(x + rnd.nextDouble() * (w - 3), wy + rnd.nextDouble() * (wall - 2), 2.6, 1.4));
            }
        }
        for (double fx : windows) window(g, x + w * fx, wy + wall * 0.3 + 0.5, timber);
        double dx = doorX;
        double dw = Math.min(6, w * 0.2);
        g.setColor(new Color(0x3A2412));
        g.fill(new RoundRectangle2D.Double(dx - dw / 2 - 0.6, y + h - wall * 0.8 - 0.6, dw + 1.2, wall * 0.8 + 0.6, 2.4, 2.4));
        g.setPaint(new GradientPaint((float) dx, (float) (y + h - wall * 0.8), new Color(0x8A5A30), (float) dx,
                (float) (y + h), new Color(0x5A3818)));
        g.fill(new RoundRectangle2D.Double(dx - dw / 2, y + h - wall * 0.8, dw, wall * 0.8, 2, 2));
        g.setColor(new Color(0xD8B060));
        g.fill(new Ellipse2D.Double(dx + dw * 0.22, y + h - wall * 0.4, 0.8, 0.8));
        // The roof shades the top of the wall.
        g.setPaint(new GradientPaint(0, (float) wy, new Color(0, 0, 0, 110), 0, (float) (wy + 2.5), new Color(0, 0, 0, 0)));
        g.fill(new Rectangle2D.Double(x, wy, w, 2.5));

        // The roof, its eaves over the walls.
        double rx = x - 1.5;
        double rw = w + 3;
        double ry = y - 0.5;
        double rr = rh + 1;
        double ridge = ry + rr * 0.4;
        Path2D north = new Path2D.Double(new Rectangle2D.Double(rx, ry, rw, ridge - ry));
        Path2D south = new Path2D.Double(new Rectangle2D.Double(rx, ridge, rw, ry + rr - ridge));
        g.setPaint(new GradientPaint(0, (float) ry, new Color(shade(roofC, 1.05)), 0, (float) ridge, new Color(shade(roofC, 1.28))));
        g.fill(north);
        g.setPaint(new GradientPaint(0, (float) ridge, new Color(shade(roofC, 0.95)), 0, (float) (ry + rr),
                new Color(shade(roofC, 0.74))));
        g.fill(south);
        Shape old = g.getClip();
        for (int side = 0; side < 2; side++) {
            g.setClip(old);
            g.clip(side == 0 ? north : south);
            double y0 = side == 0 ? ry : ridge;
            double y1 = side == 0 ? ridge : ry + rr;
            roof(g, roofKind, rx, rw, y0, y1, side, rnd);
            // Moss and weathering.
            for (int i = 0; i < rw * (y1 - y0) / 40; i++) {
                double mx = rx + rnd.nextDouble() * rw;
                double my = y0 + rnd.nextDouble() * (y1 - y0);
                double mr = 1 + rnd.nextDouble() * 2.5;
                g.setPaint(radial(mx, my, mr, new float[]{0, 1}, new Color(60, 80, 30, 50), new Color(60, 80, 30, 0)));
                g.fill(new Ellipse2D.Double(mx - mr, my - mr, mr * 2, mr * 2));
            }
        }
        g.setClip(old);
        g.setColor(new Color(shade(roofC, 0.55)));
        g.setStroke(new BasicStroke(1.2f));
        g.draw(new Line2D.Double(rx + 0.6, ridge, rx + rw - 0.6, ridge));
        g.setColor(new Color(255, 255, 255, 90));
        g.setStroke(new BasicStroke(0.4f));
        g.draw(new Line2D.Double(rx + 0.6, ridge - 0.7, rx + rw - 0.6, ridge - 0.7));
        g.setColor(new Color(shade(roofC, 0.5)));
        g.setStroke(new BasicStroke(0.7f));
        g.draw(new Line2D.Double(rx + 0.3, ry, rx + 0.3, ry + rr));
        g.draw(new Line2D.Double(rx + rw - 0.3, ry, rx + rw - 0.3, ry + rr));
        g.setColor(new Color(shade(roofC, 0.45)));
        g.setStroke(new BasicStroke(0.9f));
        g.draw(new Line2D.Double(rx, ry + rr - 0.4, rx + rw, ry + rr - 0.4));
        g.setColor(new Color(255, 255, 255, 70));
        g.setStroke(new BasicStroke(0.5f));
        g.draw(new Line2D.Double(rx, ry + 0.3, rx + rw, ry + 0.3));
    }
    // --8<-- [end:house]

    /** One slope of a roof: rows of clay tiles, thatch, or wooden shingles. */
    private static void roof(Graphics2D g, int kind, double rx, double rw, double y0, double y1, int side, Random rnd) {
        if (kind == TILES) {
            for (double ty = y0; ty < y1; ty += 2.2) {
                double off = ((int) ((ty - y0) / 2.2) % 2) * 1.5;
                for (double tx = rx - off; tx < rx + rw; tx += 3) {
                    g.setColor(new Color(0, 0, 0, 55));
                    g.setStroke(new BasicStroke(0.3f));
                    g.draw(new Arc2D.Double(tx, ty - 1.2, 3, 3, 180, 180, Arc2D.OPEN));
                    g.setColor(new Color(255, 230, 200, 40));
                    g.fill(new Rectangle2D.Double(tx + 0.5, ty, 0.8, 1.2));
                }
            }
        } else if (kind == THATCH) {
            g.setStroke(new BasicStroke(0.3f));
            for (int i = 0; i < rw * (y1 - y0) * 1.2; i++) {
                double tx = rx + rnd.nextDouble() * rw;
                double ty = y0 + rnd.nextDouble() * (y1 - y0);
                g.setColor(rnd.nextBoolean() ? new Color(255, 240, 180, 70) : new Color(80, 50, 10, 60));
                g.draw(new Line2D.Double(tx, ty, tx + (rnd.nextDouble() - 0.5) * 0.6, ty + (side == 0 ? -1.6 : 1.6)));
            }
            g.setStroke(new BasicStroke(0.5f));
            for (double ty = y0 + 3; ty < y1; ty += 3.5) {
                g.setColor(new Color(60, 35, 5, 60));
                g.draw(new Line2D.Double(rx, ty, rx + rw, ty));
            }
        } else {
            for (double ty = y0; ty < y1; ty += 2) {
                double off = rnd.nextDouble() * 2;
                g.setColor(new Color(0, 0, 0, 60));
                g.setStroke(new BasicStroke(0.3f));
                g.draw(new Line2D.Double(rx, ty, rx + rw, ty));
                for (double tx = rx + off; tx < rx + rw; tx += 1.6 + rnd.nextDouble() * 1.4) {
                    g.draw(new Line2D.Double(tx, ty, tx, ty + 2));
                    if (rnd.nextDouble() < 0.15) {
                        g.setColor(new Color(255, 255, 255, 25));
                        g.fill(new Rectangle2D.Double(tx, ty, 1.5, 2));
                        g.setColor(new Color(0, 0, 0, 60));
                    }
                }
            }
        }
    }

    /** The shadow of a house {@code hgt} high whose roof and wall fill {@code x}, {@code y}, {@code w}, {@code h}. */
    static void houseShadow(Graphics2D g, double x, double y, double w, double h, double hgt) {
        Path2D sh = new Path2D.Double();
        sh.moveTo(x + w, y + 2);
        sh.lineTo(x + w + hgt * 0.8, y + 2 + hgt * 0.9);
        sh.lineTo(x + w + hgt * 0.8, y + h + hgt * 0.5);
        sh.lineTo(x + hgt * 0.4, y + h + hgt * 0.5);
        sh.lineTo(x, y + h);
        sh.lineTo(x + w, y + h);
        sh.closePath();
        g.setColor(Art.SHADOW);
        g.fill(sh);
        for (int i = 3; i >= 1; i--) {
            g.setColor(new Color(10, 15, 5, 22));
            g.fill(new RoundRectangle2D.Double(x - i * 0.8, y + h - 6 - i * 0.6, w + i * 1.6, 6 + i * 1.6, 3, 3));
        }
    }

    static void window(Graphics2D g, double cx, double y, boolean timber) {
        double w = 3.6;
        double h = 3.4;
        g.setColor(new Color(0x4A3020));
        g.fill(new Rectangle2D.Double(cx - w / 2 - 0.6, y - 0.6, w + 1.2, h + 1.2));
        g.setPaint(new GradientPaint((float) cx, (float) y, new Color(0x2A3A48), (float) cx, (float) (y + h), new Color(0x6A8AA0)));
        g.fill(new Rectangle2D.Double(cx - w / 2, y, w, h));
        g.setColor(new Color(0xE8DCC0));
        g.setStroke(new BasicStroke(0.4f));
        g.draw(new Line2D.Double(cx, y, cx, y + h));
        g.draw(new Line2D.Double(cx - w / 2, y + h / 2, cx + w / 2, y + h / 2));
        if (timber) {
            g.setColor(new Color(0x3A6A3A));
            g.fill(new Rectangle2D.Double(cx - w / 2 - 2, y - 0.3, 1.4, h + 0.6));
            g.fill(new Rectangle2D.Double(cx + w / 2 + 0.6, y - 0.3, 1.4, h + 0.6));
        }
        g.setColor(new Color(0x6A4A2A));
        g.fill(new Rectangle2D.Double(cx - w / 2 - 0.4, y + h + 0.4, w + 0.8, 0.9));
        g.setColor(new Color(0xE84A4A));
        g.fill(new Ellipse2D.Double(cx - 1.2, y + h - 0.2, 0.9, 0.9));
        g.setColor(new Color(0xF2D04A));
        g.fill(new Ellipse2D.Double(cx + 0.4, y + h - 0.1, 0.8, 0.8));
    }

    static void chimney(Graphics2D g, double x, double y) {
        g.setColor(new Color(0, 0, 0, 80));
        g.fill(new Rectangle2D.Double(x + 1, y + 1.5, 4, 5));
        g.setPaint(new GradientPaint((float) x, (float) y, new Color(0xB0624A), (float) (x + 4), (float) y, new Color(0x6A3424)));
        g.fill(new Rectangle2D.Double(x, y, 4, 5));
        g.setColor(new Color(0x1A1410));
        g.fill(new Rectangle2D.Double(x + 0.8, y + 0.6, 2.4, 1.8));
        g.setColor(new Color(0x5A3020));
        g.setStroke(new BasicStroke(0.25f));
        g.draw(new Line2D.Double(x, y + 3.2, x + 4, y + 3.2));
    }

    /** Where the smoke of a building's chimney rises, in units from the top left of its tiles; null without one. */
    static double[] chimneyTop(String id) {
        return switch (id) {
            case "storehouse" -> new double[]{7 + 40 * 0.74 + 2, -6};
            case "bakery" -> new double[]{10 + 22 * 0.2 + 2, -1};
            case "toolmaker" -> new double[]{3 + 26 * 0.15 + 2, -2};
            case "residence" -> new double[]{2 + 28 * 0.3 + 2, -4};
            default -> null;
        };
    }

    /**
     * A square stone tower {@code s} wide whose roof, a pyramid in {@code roof}, sits on walls {@code wall}
     * high; its foot at {@code y + hgt}.
     */
    static void tower(Graphics2D g, double x, double y, double s, double wall, double hgt, int roof) {
        double top = y + hgt - wall - s;
        g.setPaint(new GradientPaint((float) x, 0, new Color(0xD0C8B6), (float) (x + s), 0, new Color(0x8A8474)));
        g.fill(new Rectangle2D.Double(x, top + s, s, wall));
        g.setColor(new Color(0, 0, 0, 50));
        g.setStroke(new BasicStroke(0.3f));
        for (double sy = top + s + 2.4; sy < top + s + wall; sy += 2.4) g.draw(new Line2D.Double(x, sy, x + s, sy));
        g.setColor(new Color(0x2A2018));
        g.fill(new RoundRectangle2D.Double(x + s / 2 - 1.4, top + s + wall - 5.5, 2.8, 5.5, 2.6, 2.6));
        if (wall > 16) {
            g.fill(new Rectangle2D.Double(x + s / 2 - 0.8, top + s + 3, 1.6, 2.6));
        }
        double cx = x + s / 2;
        double cy = top + s / 2;
        double[][] tri = {{x, top, x + s, top}, {x + s, top, x + s, top + s}, {x + s, top + s, x, top + s}, {x, top + s, x, top}};
        double[] lit = {1.3, 0.9, 0.75, 1.1};
        for (int i = 0; i < 4; i++) {
            Path2D p = new Path2D.Double();
            p.moveTo(tri[i][0], tri[i][1]);
            p.lineTo(tri[i][2], tri[i][3]);
            p.lineTo(cx, cy);
            p.closePath();
            g.setColor(new Color(shade(roof, lit[i])));
            g.fill(p);
            g.setColor(new Color(0, 0, 0, 40));
            g.setStroke(new BasicStroke(0.3f));
            for (double t = 0.2; t < 1; t += 0.2) {
                g.draw(new Line2D.Double(tri[i][0] + (cx - tri[i][0]) * t, tri[i][1] + (cy - tri[i][1]) * t,
                        tri[i][2] + (cx - tri[i][2]) * t, tri[i][3] + (cy - tri[i][3]) * t));
            }
        }
        g.setColor(new Color(shade(roof, 0.5)));
        g.setStroke(new BasicStroke(0.6f));
        g.draw(new Line2D.Double(x, top, x + s, top + s));
        g.draw(new Line2D.Double(x + s, top, x, top + s));
        g.setColor(new Color(0xE8C860));
        g.fill(new Ellipse2D.Double(cx - 1.2, cy - 1.2, 2.4, 2.4));
    }

    static void banner(Graphics2D g, double x, double y) {
        g.setColor(new Color(0, 0, 0, 70));
        g.setStroke(new BasicStroke(0.8f));
        g.draw(new Line2D.Double(x + 1.5, y + 22, x + 9, y + 26));
        g.setColor(new Color(0x4A3A2A));
        g.draw(new Line2D.Double(x, y + 16, x, y - 6));
        Path2D f = new Path2D.Double();
        f.moveTo(x + 0.4, y - 6);
        f.curveTo(x + 5, y - 7.5, x + 8, y - 4, x + 12, y - 5.5);
        f.lineTo(x + 12, y + 0.5);
        f.curveTo(x + 8, y + 2, x + 5, y - 1.5, x + 0.4, y);
        f.closePath();
        g.setPaint(new GradientPaint((float) x, (float) y, Art.BLUE[1], (float) (x + 12), (float) y, Art.BLUE[2]));
        g.fill(f);
        g.setColor(new Color(0xF2E8D4));
        g.fill(new Ellipse2D.Double(x + 4.5, y - 4.4, 3, 3));
    }

    /** The sawmill's big door stands open, a blade glints inside. */
    static void saw(Graphics2D g, double cx, double y) {
        g.setColor(new Color(0x2A1C10));
        g.fill(new Rectangle2D.Double(cx - 5, y + 3, 10, 8));
        g.setPaint(radial(cx - 1, y + 7, 3, new float[]{0, 1}, new Color(0xE8ECF0), new Color(0x6A7078)));
        g.fill(new Ellipse2D.Double(cx - 2.8, y + 4.2, 5.6, 5.6));
        g.setColor(new Color(0x3A3A3A));
        g.fill(new Ellipse2D.Double(cx - 0.8, y + 6.2, 1.6, 1.6));
    }

    static void crates(Graphics2D g, double x, double y) {
        for (double[] c : new double[][]{{0, 0}, {6, 1}}) {
            double cx = x + c[0];
            double cy = y + c[1];
            g.setColor(new Color(20, 15, 5, 90));
            g.fill(new Rectangle2D.Double(cx + 1.2, cy + 1.4, 5, 5));
            g.setPaint(new GradientPaint((float) cx, (float) cy, new Color(0xC8A06A), (float) (cx + 5), (float) (cy + 5),
                    new Color(0x8A6234)));
            g.fill(new Rectangle2D.Double(cx, cy, 5, 5));
            g.setColor(new Color(0x5A3E20));
            g.setStroke(new BasicStroke(0.4f));
            g.draw(new Rectangle2D.Double(cx + 0.2, cy + 0.2, 4.6, 4.6));
            g.draw(new Line2D.Double(cx, cy, cx + 5, cy + 5));
        }
    }

    static void logPile(Graphics2D g, double x, double y, double s) {
        AffineTransform at = g.getTransform();
        g.translate(x, y);
        g.scale(s, s);
        g.setColor(new Color(20, 15, 5, 90));
        g.fill(new RoundRectangle2D.Double(1.5, 1.5, 13, 13, 3, 3));
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 2; c++) {
                double lx = c * 0.5;
                double ly = r * 3.4;
                g.setPaint(new GradientPaint(0, (float) ly, new Color(0x9A7048), 0, (float) (ly + 3), new Color(0x5A3A20)));
                g.fill(new RoundRectangle2D.Double(lx, ly, 13, 3.2, 2, 2));
                g.setColor(new Color(0xE0C090));
                g.fill(new Ellipse2D.Double(lx + 11.4, ly, 2.2, 3.2));
                g.setColor(new Color(0xA07848));
                g.setStroke(new BasicStroke(0.25f));
                g.draw(new Ellipse2D.Double(lx + 12, ly + 0.9, 1, 1.4));
            }
        }
        g.setTransform(at);
    }

    static void planks(Graphics2D g, double x, double y, double s) {
        AffineTransform at = g.getTransform();
        g.translate(x, y);
        g.scale(s, s);
        g.setColor(new Color(20, 15, 5, 90));
        g.fill(new Rectangle2D.Double(1.5, 1.5, 12, 7));
        for (int i = 0; i < 3; i++) {
            double py = i * 2.3;
            g.setPaint(new GradientPaint(0, (float) py, new Color(0xE8C890), 0, (float) (py + 2.3), new Color(0xB89060)));
            g.fill(new Rectangle2D.Double(0, py, 12, 2.2));
            g.setColor(new Color(0x7A5A36));
            g.setStroke(new BasicStroke(0.25f));
            g.draw(new Line2D.Double(0, py + 2.2, 12, py + 2.2));
        }
        g.setTransform(at);
    }

    static void blocks(Graphics2D g, double x, double y, double s) {
        AffineTransform at = g.getTransform();
        g.translate(x, y);
        g.scale(s, s);
        for (double[] b : new double[][]{{2.5, -3}, {7.5, -3}, {0, 0}, {5, 0}, {10, 0}, {0, 4}, {5, 4}}) {
            double bx = b[0];
            double by = b[1];
            g.setColor(new Color(20, 15, 5, 70));
            g.fill(new Rectangle2D.Double(bx + 1, by + 1.2, 4.6, 4));
            g.setPaint(new GradientPaint((float) bx, (float) by, new Color(0xDCD6C8), (float) (bx + 4.5), (float) (by + 4),
                    new Color(0x8A867A)));
            g.fill(new Rectangle2D.Double(bx, by, 4.6, 4));
            g.setColor(new Color(0x5A564E));
            g.setStroke(new BasicStroke(0.3f));
            g.draw(new Rectangle2D.Double(bx, by, 4.6, 4));
        }
        g.setTransform(at);
    }

    /** The fisher's drying rack. */
    static void rack(Graphics2D g, double x, double y) {
        g.setColor(new Color(20, 15, 5, 80));
        g.fill(new Rectangle2D.Double(x + 1.5, y + 1.5, 12, 2));
        g.setColor(new Color(0x6A4A2A));
        g.setStroke(new BasicStroke(0.8f));
        g.draw(new Line2D.Double(x, y, x + 12, y));
        for (int i = 0; i < 4; i++) {
            double fx = x + 1.5 + i * 3;
            g.setPaint(new GradientPaint((float) fx, (float) y, new Color(0xD8E0E4), (float) (fx + 2), (float) y, new Color(0x7A8A94)));
            g.fill(new Ellipse2D.Double(fx - 0.8, y + 0.2, 1.8, 4.2));
        }
    }

    static void hay(Graphics2D g, double x, double y) {
        g.setColor(new Color(30, 20, 5, 90));
        g.fill(new Ellipse2D.Double(x - 5 + 2, y - 4 + 2, 10, 8));
        g.setPaint(radial(x - 2, y - 2, 6, new float[]{0, 1}, new Color(0xF2D890), new Color(0xA88030)));
        g.fill(new Ellipse2D.Double(x - 5, y - 4, 10, 8));
        g.setColor(new Color(0x8A6A24));
        g.setStroke(new BasicStroke(0.3f));
        Random rnd = new Random(2);
        for (int i = 0; i < 12; i++) {
            double a = rnd.nextDouble() * Math.PI * 2;
            double r = 1 + rnd.nextDouble() * 3;
            g.draw(new Line2D.Double(x + Math.cos(a) * r, y + Math.sin(a) * r * 0.8, x + Math.cos(a) * (r + 1.2),
                    y + Math.sin(a) * (r + 1.2) * 0.8));
        }
    }

    /** The bakery's dome oven, by its wall. */
    static void oven(Graphics2D g, double x, double y) {
        g.setColor(new Color(20, 15, 5, 90));
        g.fill(new Ellipse2D.Double(x - 1, y - 6 + 2, 14, 12));
        g.setPaint(radial(x + 3, y - 3, 9, new float[]{0, 0.6f, 1}, new Color(0xE8A070), new Color(0xB8603A), new Color(0x6A3018)));
        g.fill(new Ellipse2D.Double(x - 2, y - 7, 13, 12));
        g.setColor(new Color(0x7A3A1E));
        g.setStroke(new BasicStroke(0.3f));
        for (int i = 1; i < 4; i++) {
            g.draw(new Arc2D.Double(x - 2 + i * 1.5, y - 7 + i * 1.4, 13 - i * 3, 12 - i * 2.8, 0, 180, Arc2D.OPEN));
        }
        g.setPaint(radial(x + 4.5, y + 3, 3, new float[]{0, 1}, new Color(0xFFD070), new Color(0x401808)));
        g.fill(new Arc2D.Double(x + 2.5, y + 1, 4, 5, 0, 180, Arc2D.PIE));
    }

    /** The weaver's cloth, hung out to dry between two posts. */
    static void clothLine(Graphics2D g, double x, double y) {
        g.setColor(new Color(0x5A3A22));
        g.setStroke(new BasicStroke(0.8f));
        g.draw(new Line2D.Double(x, y, x, y + 12));
        g.draw(new Line2D.Double(x + 7, y - 1, x + 7, y + 11));
        g.setStroke(new BasicStroke(0.3f));
        g.draw(new Line2D.Double(x, y + 0.5, x + 7, y - 0.5));
        int[] colours = {0x3A5A8A, 0xB84A3A, 0xE8DCC0};
        for (int i = 0; i < 3; i++) {
            double cx = x + 0.6 + i * 2.1;
            g.setPaint(new GradientPaint((float) cx, 0, new Color(shade(colours[i], 1.2)), (float) (cx + 2), 0,
                    new Color(shade(colours[i], 0.75))));
            g.fill(new Rectangle2D.Double(cx, y + 0.4 - i * 0.3, 1.9, 5 + (i % 2)));
        }
    }

    /** The toolmaker's anvil on its block. */
    static void anvil(Graphics2D g, double x, double y) {
        g.setColor(new Color(20, 15, 5, 90));
        g.fill(new Ellipse2D.Double(x - 1, y - 2, 9, 4));
        g.setColor(new Color(0x6A4A2A));
        g.fill(new Rectangle2D.Double(x + 1.5, y - 4, 4, 4));
        Path2D a = new Path2D.Double();
        a.moveTo(x - 1, y - 6.5);
        a.lineTo(x + 7.5, y - 6.5);
        a.lineTo(x + 6, y - 4.6);
        a.lineTo(x + 1, y - 4.6);
        a.closePath();
        g.setPaint(new GradientPaint((float) x, (float) (y - 7), new Color(0x9AA0A8), (float) x, (float) (y - 4), new Color(0x3A3E44)));
        g.fill(a);
    }

    /** A little garden by the residence: a low fence and flowers. */
    static void garden(Graphics2D g, double x, double y) {
        Random rnd = new Random(8);
        g.setColor(new Color(0x4A7A2A));
        g.fill(new RoundRectangle2D.Double(x, y, 7, 9, 2, 2));
        Color[] flowers = {new Color(0xF2ECD8), new Color(0xE8C84A), new Color(0xC8A0E0), new Color(0xE87A6A)};
        for (int i = 0; i < 10; i++) {
            g.setColor(flowers[rnd.nextInt(flowers.length)]);
            g.fill(new Ellipse2D.Double(x + 0.6 + rnd.nextDouble() * 5.4, y + 0.6 + rnd.nextDouble() * 7.4, 1.4, 1.4));
        }
        g.setColor(new Color(0x8A6A40));
        g.setStroke(new BasicStroke(0.4f));
        g.draw(new Line2D.Double(x - 0.5, y + 9.4, x + 7.5, y + 9.4));
        for (double px = x - 0.5; px <= x + 7.5; px += 2) g.draw(new Line2D.Double(px, y + 7.6, px, y + 9.6));
    }

    // ---- the well, the windmill, the pasture -------------------------------------------------------

    static void well(Graphics2D g, double x, double y) {
        g.setPaint(radial(x - 2, y - 2, 9, new float[]{0, 1}, new Color(0xD8D2C4), new Color(0x7A766A)));
        g.fill(new Ellipse2D.Double(x - 7, y - 5, 14, 10));
        g.setColor(new Color(0x1E3A4A));
        g.fill(new Ellipse2D.Double(x - 4.4, y - 3.2, 8.8, 6.4));
        g.setColor(new Color(0x4A8AA8));
        g.fill(new Ellipse2D.Double(x - 3.4, y - 2, 6.8, 4));
        g.setColor(new Color(0x5A564E));
        g.setStroke(new BasicStroke(0.3f));
        for (int i = 0; i < 10; i++) {
            double a = i * Math.PI / 5;
            g.draw(new Line2D.Double(x + Math.cos(a) * 4.6, y + Math.sin(a) * 3.4, x + Math.cos(a) * 7, y + Math.sin(a) * 5));
        }
        g.setColor(new Color(0x6A4628));
        g.fill(new Rectangle2D.Double(x - 7, y - 10, 1.6, 10));
        g.fill(new Rectangle2D.Double(x + 5.4, y - 10, 1.6, 10));
        Path2D roof = new Path2D.Double();
        roof.moveTo(x - 9, y - 9);
        roof.lineTo(x + 9, y - 9);
        roof.lineTo(x + 8, y - 14);
        roof.lineTo(x - 8, y - 14);
        roof.closePath();
        g.setPaint(new GradientPaint(0, (float) (y - 14), new Color(0xB0583A), 0, (float) (y - 9), new Color(0x7A3420)));
        g.fill(roof);
        g.setColor(new Color(0x4A2014));
        g.setStroke(new BasicStroke(0.5f));
        g.draw(new Line2D.Double(x - 9, y - 9, x + 9, y - 9));
        g.setColor(new Color(0x3A2A1A));
        g.setStroke(new BasicStroke(0.3f));
        g.draw(new Line2D.Double(x + 1, y - 9, x + 1, y - 4));
        g.setColor(new Color(0x8A6A40));
        g.fill(new Rectangle2D.Double(x, y - 4.4, 2, 2));
    }

    /** Where the windmill's sails turn, in units from the top left of its tiles. */
    static final double HUB_X = 16;
    static final double HUB_Y = 24 - 9 * 0.8 + 10 * 0.8;

    /** The windmill without its sails: a round stone tower with a thatched cap, its foot at {@code x}, {@code y}. */
    static void windmill(Graphics2D g, double x, double y) {
        AffineTransform at = g.getTransform();
        g.translate(x, y);
        g.scale(0.8, 0.8);
        double r = 11;
        double body = 9;
        g.setPaint(new GradientPaint((float) -r, 0, new Color(0xF0E8D8), (float) r, 0, new Color(0x9A9282)));
        g.fill(new Rectangle2D.Double(-r, -body, r * 2, body));
        g.fill(new Ellipse2D.Double(-r, -r * 0.5, r * 2, r));
        g.setColor(new Color(0, 0, 0, 40));
        g.setStroke(new BasicStroke(0.3f));
        for (double sy = -body + 2; sy < r * 0.4; sy += 2.4) g.draw(new Line2D.Double(-r + 0.5, sy, r - 0.5, sy));
        g.setColor(new Color(0x3A2412));
        g.fill(new RoundRectangle2D.Double(-2, -1, 4, 5.5, 3, 3));
        g.setColor(new Color(0x2A3A48));
        g.fill(new Rectangle2D.Double(-7, -6, 2, 2.4));
        double cy = -body;
        g.setPaint(radial(-4, cy - 4, r * 1.5, new float[]{0, 0.6f, 1}, new Color(0xE8C878), new Color(0xB08838), new Color(0x6A4A1A)));
        g.fill(new Ellipse2D.Double(-r - 1, cy - r - 1, r * 2 + 2, r * 2 + 2));
        Random rnd = new Random(4);
        g.setStroke(new BasicStroke(0.3f));
        for (int i = 0; i < 48; i++) {
            double a = i * Math.PI * 2 / 48 + rnd.nextDouble() * 0.05;
            g.setColor(rnd.nextBoolean() ? new Color(80, 50, 10, 70) : new Color(255, 240, 180, 60));
            g.draw(new Line2D.Double(Math.cos(a) * 2, cy + Math.sin(a) * 2, Math.cos(a) * (r + 0.5), cy + Math.sin(a) * (r + 0.5)));
        }
        g.setColor(new Color(0x5A3A1A));
        g.fill(new Ellipse2D.Double(-1.6, cy - 1.6, 3.2, 3.2));
        g.setTransform(at);
    }

    // --8<-- [start:sails]
    /** The four sails around 0, 0, the first pointing right; they turn as the mill grinds. */
    static void sails(Graphics2D g) {
        AffineTransform at = g.getTransform();
        g.scale(0.8, 0.8);
        for (int i = 0; i < 4; i++) {
            g.setColor(new Color(0x5A3A1E));
            g.fill(new Rectangle2D.Double(1, -0.6, 25, 1.2));
            g.setColor(new Color(0xF2E8D0));
            g.fill(new Rectangle2D.Double(6, 0.6, 19, 4.2));
            g.setColor(new Color(0x6A4A2A));
            g.setStroke(new BasicStroke(0.35f));
            for (double sx = 6; sx <= 25; sx += 2.7) g.draw(new Line2D.Double(sx, 0.6, sx, 4.8));
            g.draw(new Line2D.Double(6, 2.7, 25, 2.7));
            g.draw(new Rectangle2D.Double(6, 0.6, 19, 4.2));
            g.rotate(Math.PI / 2);
        }
        g.setPaint(radial(-0.6, -0.6, 2.4, new float[]{0, 1}, new Color(0x9A7A50), new Color(0x3A2412)));
        g.fill(new Ellipse2D.Double(-2, -2, 4, 4));
        g.setTransform(at);
    }
    // --8<-- [end:sails]

    /** The shadow of the sails, thrown far to the bottom right. */
    static void sailsShadow(Graphics2D g) {
        g.setColor(new Color(10, 20, 5, 70));
        g.setStroke(new BasicStroke(2.6f));
        for (int i = 0; i < 4; i++) {
            g.draw(new Line2D.Double(2.4, 0, 19, 0));
            g.rotate(Math.PI / 2);
        }
    }

    /** Grazed grass: paler, with trodden patches and a trough of water. */
    static void pastureGround(Graphics2D g, double x, double y, double w, double h) {
        Random rnd = new Random(31);
        g.setColor(new Color(170, 200, 110, 45));
        g.fill(new Rectangle2D.Double(x, y, w, h));
        for (int i = 0; i < 10; i++) {
            double px = x + 4 + rnd.nextDouble() * (w - 8);
            double py = y + 4 + rnd.nextDouble() * (h - 8);
            double r = 2 + rnd.nextDouble() * 4;
            g.setPaint(radial(px, py, r, new float[]{0, 1}, new Color(140, 110, 60, 90), new Color(140, 110, 60, 0)));
            g.fill(new Ellipse2D.Double(px - r, py - r * 0.7, r * 2, r * 1.4));
        }
        double tx = x + w - 13;
        double ty = y + 6;
        g.setColor(new Color(10, 20, 5, 80));
        g.fill(new Rectangle2D.Double(tx + 1, ty + 1.2, 10, 3.6));
        g.setColor(new Color(0x6A4A2A));
        g.fill(new Rectangle2D.Double(tx, ty, 10, 3.6));
        g.setColor(new Color(0x4A8AA8));
        g.fill(new Rectangle2D.Double(tx + 0.8, ty + 0.7, 8.4, 2.2));
        g.setColor(new Color(255, 255, 255, 90));
        g.fill(new Rectangle2D.Double(tx + 1.4, ty + 0.9, 3, 0.5));
    }

    /**
     * The fence round the pasture {@code x}, {@code y}, {@code w}, {@code h}: with {@code front} its near
     * side, with a gate towards the house, else the other three.
     */
    static void fence(Graphics2D g, double x, double y, double w, double h, boolean front) {
        double[][][] sides = front
                ? new double[][][]{{{x + 1, y + h - 1}, {x + w * 0.62, y + h - 1}}}
                : new double[][][]{{{x + 1, y + 1}, {x + w - 1, y + 1}}, {{x + 1, y + 1}, {x + 1, y + h - 1}},
                {{x + w - 1, y + 1}, {x + w - 1, y + h * 0.55}}};
        for (double[][] sd : sides) {
            double len = Math.hypot(sd[1][0] - sd[0][0], sd[1][1] - sd[0][1]);
            int n = Math.max(1, (int) Math.round(len / 6));
            g.setColor(new Color(10, 20, 5, 60));
            g.setStroke(new BasicStroke(0.6f));
            for (int rail = 0; rail < 2; rail++) {
                g.draw(new Line2D.Double(sd[0][0] + 1.5, sd[0][1] + 1.5 + rail, sd[1][0] + 1.5, sd[1][1] + 1.5 + rail));
            }
            for (int i = 0; i <= n; i++) {
                double px = sd[0][0] + (sd[1][0] - sd[0][0]) * i / n;
                double py = sd[0][1] + (sd[1][1] - sd[0][1]) * i / n;
                g.setColor(new Color(0x4A3420));
                g.fill(new Rectangle2D.Double(px - 0.5, py - 5, 1, 5));
                g.setColor(new Color(0x9A7A50));
                g.fill(new Rectangle2D.Double(px - 0.5, py - 5, 0.4, 5));
            }
            for (int rail = 0; rail < 2; rail++) {
                double ry = -1.8 - rail * 2;
                g.setColor(new Color(0x8A6A40));
                g.setStroke(new BasicStroke(0.7f));
                g.draw(new Line2D.Double(sd[0][0], sd[0][1] + ry, sd[1][0], sd[1][1] + ry));
                g.setColor(new Color(255, 230, 180, 70));
                g.setStroke(new BasicStroke(0.25f));
                g.draw(new Line2D.Double(sd[0][0], sd[0][1] + ry - 0.3, sd[1][0], sd[1][1] + ry - 0.3));
            }
        }
    }

    // --8<-- [start:site]
    /** A site of {@code width} × {@code height} tiles: the foundation, its stones, and a timber frame going up. */
    static void site(Graphics2D g, int width, int height) {
        double w = width * Field.TILE - 4;
        double h = height * Field.TILE - 4;
        double x = 2;
        double y = 2;
        g.setColor(new Color(10, 20, 5, 70));
        g.fill(new Rectangle2D.Double(x + 2, y + 2, w + 2, h));
        g.setPaint(new GradientPaint((float) x, (float) y, new Color(0xB8B2A2), (float) (x + w), (float) (y + h), new Color(0x847E70)));
        g.fill(new Rectangle2D.Double(x, y, w, h));
        g.setColor(new Color(0x9A7A56));
        g.fill(new Rectangle2D.Double(x + 2, y + 2, w - 4, h - 4));
        g.setColor(new Color(0x5A564E));
        g.setStroke(new BasicStroke(0.4f));
        for (double sx = x + 4; sx < x + w; sx += 4) {
            g.draw(new Line2D.Double(sx, y, sx, y + 2));
            g.draw(new Line2D.Double(sx, y + h - 2, sx, y + h));
        }
        g.setColor(new Color(0x6A4628));
        g.setStroke(new BasicStroke(1.4f));
        double fy = y + h - 2;
        for (double px : new double[]{x + 2, x + w * 0.5, x + w - 2}) g.draw(new Line2D.Double(px, fy, px, fy - 9));
        g.draw(new Line2D.Double(x + 2, fy - 9, x + w - 2, fy - 9));
        g.setStroke(new BasicStroke(0.9f));
        g.draw(new Line2D.Double(x + 2, fy - 4.5, x + w * 0.5, fy - 9));
        g.setColor(new Color(0xA07040));
        g.setStroke(new BasicStroke(0.8f));
        g.draw(new Line2D.Double(x + 2, y + 2, x + 2, fy - 9));
        g.draw(new Line2D.Double(x + w - 2, y + 2, x + w - 2, fy - 9));
        g.draw(new Line2D.Double(x + 2, y + 2, x + w - 2, y + 2));
    }
    // --8<-- [end:site]
}
