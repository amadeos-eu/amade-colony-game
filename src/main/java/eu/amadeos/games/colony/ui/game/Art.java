package eu.amadeos.games.colony.ui.game;

import eu.amadeos.games.colony.domain.Good;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.QuadCurve2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.Random;

/**
 * The pictures of the land and what lives on it, drawn in code: trees in summer and winter, rocks, fields,
 * the goods, the sheep, the dog and the wolf, flags and border posts, smoke and flames. {@link Houses} draws
 * the buildings and {@link Folk} the people. Nothing here is drawn in a frame; {@link Pictures} draws each
 * picture once into a sprite. The light falls from the top left, so every shadow falls to the bottom right.
 */
final class Art {
    /** Where shadows fall, from the light in the top left. */
    static final double LX = 2.0;
    static final double LY = 2.4;
    /** The settlement's colour, on flags, border posts and the carriers' caps: light, middle and dark. */
    static final Color[] BLUE = {new Color(0x8FB4F0), new Color(0x3E6FB8), new Color(0x1C355E)};
    static final Color SHADOW = new Color(10, 20, 5, 90);
    static final Color SNOW = new Color(0xF4F8FC);

    private Art() {
    }

    static void smooth(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    }

    // ---- noise and colour ------------------------------------------------------------------------

    static double hash(int x, int y, int seed) {
        long h = x * 374761393L + y * 668265263L + seed * 1442695040888963407L;
        h = (h ^ (h >>> 13)) * 1274126177L;
        h ^= h >>> 16;
        return (h & 0xFFFFFF) / (double) 0xFFFFFF;
    }

    /** Value noise: smooth hills of 0 to 1, one a unit apart. */
    static double noise(double x, double y, int seed) {
        int xi = (int) Math.floor(x);
        int yi = (int) Math.floor(y);
        double fx = x - xi;
        double fy = y - yi;
        fx = fx * fx * (3 - 2 * fx);
        fy = fy * fy * (3 - 2 * fy);
        double a = hash(xi, yi, seed);
        double b = hash(xi + 1, yi, seed);
        double c = hash(xi, yi + 1, seed);
        double d = hash(xi + 1, yi + 1, seed);
        double top = a + (b - a) * fx;
        return top + (c + (d - c) * fx - top) * fy;
    }

    /** Four octaves of noise, large hills with smaller ones on them. */
    static double fbm(double x, double y, int seed) {
        double v = 0;
        double amp = 0.5;
        double f = 1;
        for (int i = 0; i < 4; i++) {
            v += amp * noise(x * f, y * f, seed + i);
            amp *= 0.5;
            f *= 2;
        }
        return v;
    }

    static int mix(int c1, int c2, double t) {
        t = Math.clamp(t, 0, 1);
        int r = (int) (((c1 >> 16) & 255) * (1 - t) + ((c2 >> 16) & 255) * t);
        int g = (int) (((c1 >> 8) & 255) * (1 - t) + ((c2 >> 8) & 255) * t);
        int b = (int) ((c1 & 255) * (1 - t) + (c2 & 255) * t);
        return (r << 16) | (g << 8) | b;
    }

    static int shade(int c, double f) {
        int r = (int) Math.min(255, ((c >> 16) & 255) * f);
        int g = (int) Math.min(255, ((c >> 8) & 255) * f);
        int b = (int) Math.min(255, (c & 255) * f);
        return (r << 16) | (g << 8) | b;
    }

    static double smooth(double e0, double e1, double x) {
        double t = Math.clamp((x - e0) / (e1 - e0), 0, 1);
        return t * t * (3 - 2 * t);
    }

    static RadialGradientPaint radial(double x, double y, double r, float[] stops, Color... colours) {
        return new RadialGradientPaint(new Point2D.Double(x, y), (float) r, stops, colours);
    }

    static Color colour(int rgb) {
        return new Color(rgb);
    }

    /** {@code rgb} darkened for what lies outside the border, as the ground there is. */
    static Color dim(int rgb, boolean outside) {
        return new Color(outside ? mix(rgb, 0x101828, 0.5) : rgb);
    }

    // ---- trees -----------------------------------------------------------------------------------

    /** A tree's shadow, a soft round patch to the bottom right. */
    static void treeShadow(Graphics2D g, double x, double y, double s) {
        g.setColor(new Color(10, 25, 5, 80));
        double r = 10 * s;
        g.fill(new Ellipse2D.Double(x - r + LX * 2.2 * s, y - r * 0.9 + LY * 2.2 * s, r * 2, r * 1.8));
    }

    // --8<-- [start:tree]
    /**
     * A tree seen from above at {@code x}, {@code y}, {@code s} its size (1 grown): a broadleaf as a cluster
     * of lit balls of leaves, a conifer as three stars of needles. In winter the broadleaf is bare and both
     * carry snow. Outside the border it is as dim as the ground there.
     */
    static void tree(Graphics2D g, double x, double y, boolean conifer, double s, boolean winter, boolean outside,
                     long seed) {
        Random rnd = new Random(seed);
        if (conifer) {
            for (int layer = 0; layer < 3; layer++) {
                double big = (8.5 - layer * 2.5) * s;
                Path2D star = new Path2D.Double();
                int n = 9;
                double rot = rnd.nextDouble();
                for (int i = 0; i < n * 2; i++) {
                    double a = rot + i * Math.PI / n;
                    double rr = i % 2 == 0 ? big : big * 0.55;
                    if (i == 0) star.moveTo(x + Math.cos(a) * rr, y + Math.sin(a) * rr);
                    else star.lineTo(x + Math.cos(a) * rr, y + Math.sin(a) * rr);
                }
                star.closePath();
                Color c0 = dim(winter ? 0x123020 : 0x163A22, outside);
                Color c1 = dim(winter ? 0x24543A : 0x2F6B3C, outside);
                Color c2 = dim(winter ? 0x4A7A5C : 0x5E9A5A, outside);
                g.setPaint(radial(x - big * 0.35, y - big * 0.35, big * 1.2, new float[]{0, 0.6f, 1},
                        layer == 2 ? c2 : c1, c1, c0));
                g.fill(star);
            }
            if (winter) snow(g, x, y, 7 * s, rnd, outside);
            return;
        }
        if (winter) {
            bare(g, x, y, s, rnd, outside);
            return;
        }
        Color dark = dim(0x1F4418, outside);
        Color mid = dim(rnd.nextBoolean() ? 0x3C7A2A : 0x4E8A2E, outside);
        Color lite = dim(0x8CC05A, outside);
        if (rnd.nextDouble() < 0.15) {
            mid = dim(0x7A8A2A, outside);
            lite = dim(0xC8C060, outside);
        }
        for (int i = 0; i < 6; i++) {
            double a = rnd.nextDouble() * Math.PI * 2;
            double d = (3 + rnd.nextDouble() * 2.5) * s;
            double r = (5 + rnd.nextDouble() * 2.5) * s;
            double bx = x + Math.cos(a) * d;
            double by = y + Math.sin(a) * d;
            g.setPaint(radial(bx - r * 0.4, by - r * 0.45, r * 1.1, new float[]{0, 0.55f, 1}, lite, mid, dark));
            g.fill(new Ellipse2D.Double(bx - r, by - r, r * 2, r * 2));
        }
        double r = 6 * s;
        g.setPaint(radial(x - 2.5 * s, y - 2.5 * s, r * 1.25, new float[]{0, 0.6f, 1}, lite, mid, dark));
        g.fill(new Ellipse2D.Double(x - r, y - r, r * 2, r * 2));
        g.setColor(new Color(255, 255, 220, outside ? 25 : 50));
        for (int i = 0; i < 5; i++) {
            g.fill(new Ellipse2D.Double(x - 5 * s + rnd.nextDouble() * 5 * s, y - 5 * s + rnd.nextDouble() * 5 * s, 1.6, 1.6));
        }
    }
    // --8<-- [end:tree]

    /** A broadleaf in winter: its branches, from the trunk outwards, with snow on them. */
    private static void bare(Graphics2D g, double x, double y, double s, Random rnd, boolean outside) {
        Color wood = dim(0x4A3424, outside);
        Color twig = dim(0x6A5040, outside);
        g.setColor(new Color(60, 50, 40, 60));
        g.fill(new Ellipse2D.Double(x - 8 * s, y - 7 * s, 16 * s, 14 * s));
        int branches = 7;
        double rot = rnd.nextDouble() * Math.PI;
        for (int i = 0; i < branches; i++) {
            double a = rot + i * Math.PI * 2 / branches + (rnd.nextDouble() - 0.5) * 0.5;
            double len = (6 + rnd.nextDouble() * 3) * s;
            double ex = x + Math.cos(a) * len;
            double ey = y + Math.sin(a) * len * 0.9;
            g.setColor(wood);
            g.setStroke(new BasicStroke((float) (1.3 * s), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(new Line2D.Double(x, y, ex, ey));
            g.setColor(twig);
            g.setStroke(new BasicStroke((float) (0.6 * s), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (int k = -1; k <= 1; k += 2) {
                double b = a + k * (0.5 + rnd.nextDouble() * 0.3);
                double mx = x + Math.cos(a) * len * 0.6;
                double my = y + Math.sin(a) * len * 0.6 * 0.9;
                g.draw(new Line2D.Double(mx, my, mx + Math.cos(b) * len * 0.45, my + Math.sin(b) * len * 0.4));
            }
        }
        g.setColor(wood);
        g.fill(new Ellipse2D.Double(x - 1.6 * s, y - 1.6 * s, 3.2 * s, 3.2 * s));
        snow(g, x, y, 6 * s, rnd, outside);
    }

    /** Snow lying on the lit side of a tree. */
    private static void snow(Graphics2D g, double x, double y, double r, Random rnd, boolean outside) {
        g.setColor(outside ? new Color(0xA8B0BC) : SNOW);
        for (int i = 0; i < 9; i++) {
            double a = Math.PI * 1.25 + (rnd.nextDouble() - 0.5) * 2.4;
            double d = rnd.nextDouble() * r;
            double sr = 0.8 + rnd.nextDouble() * 1.4;
            g.fill(new Ellipse2D.Double(x + Math.cos(a) * d - sr, y + Math.sin(a) * d - sr, sr * 2, sr * 1.6));
        }
    }

    /** What a woodcutter leaves of a tree: a stump with its rings. */
    static void stump(Graphics2D g, double x, double y) {
        g.setColor(new Color(20, 15, 5, 80));
        g.fill(new Ellipse2D.Double(x - 2.2 + 0.8, y - 1.8 + 0.8, 4.4, 3.6));
        g.setColor(new Color(0x6A4A2A));
        g.fill(new Ellipse2D.Double(x - 2.2, y - 1.8, 4.4, 3.6));
        g.setColor(new Color(0xD8B888));
        g.fill(new Ellipse2D.Double(x - 1.5, y - 1.3, 3, 2.4));
        g.setColor(new Color(0xA0784A));
        g.setStroke(new BasicStroke(0.3f));
        g.draw(new Ellipse2D.Double(x - 0.8, y - 0.6, 1.6, 1.2));
    }

    // ---- rock and fields -------------------------------------------------------------------------

    /** A boulder of the quarry's rock, lit from the top left. */
    static void boulder(Graphics2D g, double x, double y, double r, long seed, boolean winter, boolean outside) {
        Random rnd = new Random(seed);
        Path2D p = new Path2D.Double();
        for (int i = 0; i < 9; i++) {
            double a = i * Math.PI * 2 / 9;
            double rr = r * (0.75 + rnd.nextDouble() * 0.3);
            double px = x + Math.cos(a) * rr;
            double py = y + Math.sin(a) * rr * 0.8;
            if (i == 0) p.moveTo(px, py);
            else p.lineTo(px, py);
        }
        p.closePath();
        AffineTransform at = g.getTransform();
        g.translate(LX * 0.9, LY * 0.9);
        g.setColor(new Color(20, 20, 10, 90));
        g.fill(p);
        g.setTransform(at);
        g.setPaint(radial(x - r * 0.4, y - r * 0.4, r * 1.3, new float[]{0, 0.5f, 1}, dim(0xD8D2C4, outside),
                dim(0x9A9486, outside), dim(0x5A564E, outside)));
        g.fill(p);
        g.setColor(dim(0x4A463E, outside));
        g.setStroke(new BasicStroke(0.35f));
        g.draw(new Line2D.Double(x - r * 0.2, y - r * 0.5, x + r * 0.1, y + r * 0.2));
        g.draw(new Line2D.Double(x + r * 0.1, y + r * 0.2, x + r * 0.5, y + r * 0.3));
        if (winter) {
            g.setColor(outside ? new Color(0xA8B0BC) : SNOW);
            g.fill(new Ellipse2D.Double(x - r * 0.7, y - r * 0.75, r * 1.1, r * 0.6));
        }
    }

    // --8<-- [start:field]
    /**
     * One tile of a field, 16 × 16: furrows, and on them what grows, {@code stage} 0 just sown, 1 sprouting,
     * 2 green, 3 ripe and golden. In winter snow lies in the furrows.
     */
    static void field(Graphics2D g, int stage, boolean winter, boolean outside, long seed) {
        Random rnd = new Random(seed);
        double x = 0.5;
        double y = 0.5;
        double w = 15;
        double h = 15;
        g.setPaint(new GradientPaint((float) x, (float) y, dim(0x7A5A34, outside), (float) (x + w), (float) (y + h),
                dim(0x5E4428, outside)));
        g.fill(new RoundRectangle2D.Double(x, y, w, h, 2, 2));
        g.setStroke(new BasicStroke(0.6f));
        for (double fy = y + 2; fy < y + h - 1; fy += 3) {
            g.setColor(new Color(40, 25, 10, 90));
            g.draw(new Line2D.Double(x + 1, fy, x + w - 1, fy));
            g.setColor(new Color(170, 130, 80, 60));
            g.draw(new Line2D.Double(x + 1, fy + 0.8, x + w - 1, fy + 0.8));
        }
        for (double fy = y + 2.5; fy < y + h - 1; fy += 3) {
            for (double fx = x + 1.5; fx < x + w - 1; fx += 1.4 + rnd.nextDouble() * 0.6) {
                switch (stage) {
                    case 0 -> {
                        if (rnd.nextDouble() < 0.25) {
                            g.setColor(dim(0xC8B070, outside));
                            g.fill(new Rectangle2D.Double(fx, fy - 0.4, 0.5, 0.5));
                        }
                    }
                    case 1 -> {
                        g.setColor(dim(rnd.nextBoolean() ? 0x7AB84A : 0x5A9A3A, outside));
                        g.fill(new Rectangle2D.Double(fx, fy - 0.8, 0.5, 0.9));
                    }
                    case 2 -> {
                        g.setColor(dim(rnd.nextBoolean() ? 0x6AA83A : 0x4A8A2A, outside));
                        g.setStroke(new BasicStroke(0.5f));
                        g.draw(new Line2D.Double(fx, fy, fx + (rnd.nextDouble() - 0.5), fy - 1.6 - rnd.nextDouble()));
                    }
                    default -> {
                        double tall = 2.4 + rnd.nextDouble() * 1.2;
                        g.setColor(dim(0x9A7A2A, outside));
                        g.setStroke(new BasicStroke(0.5f));
                        g.draw(new Line2D.Double(fx + 0.4, fy + 0.3, fx + 0.6 + (rnd.nextDouble() - 0.5), fy - tall + 0.6));
                        g.setColor(dim(rnd.nextDouble() < 0.3 ? 0xF8E08A : 0xE0B848, outside));
                        g.fill(new Ellipse2D.Double(fx - 0.4 + (rnd.nextDouble() - 0.5) * 0.6, fy - tall - 0.4, 1.2, 1.8));
                    }
                }
            }
            if (winter) {
                g.setColor(new Color(244, 248, 252, 210));
                g.fill(new Rectangle2D.Double(x + 0.5, fy - 1.6, w - 1, 1.5));
            }
        }
    }
    // --8<-- [end:field]

    /** Where a house burned down: ashes and charred beams on a tile. */
    static void ashes(Graphics2D g, long seed) {
        Random rnd = new Random(seed);
        g.setPaint(radial(8, 8, 10, new float[]{0, 1}, new Color(30, 26, 22, 200), new Color(30, 26, 22, 0)));
        g.fill(new Ellipse2D.Double(-2, -2, 20, 20));
        g.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < 3; i++) {
            double x = 3 + rnd.nextDouble() * 10;
            double y = 3 + rnd.nextDouble() * 10;
            double a = rnd.nextDouble() * Math.PI;
            g.setColor(new Color(0x2A1E16));
            g.draw(new Line2D.Double(x, y, x + Math.cos(a) * 5, y + Math.sin(a) * 5));
        }
        g.setColor(new Color(0x8A8278));
        for (int i = 0; i < 14; i++) g.fill(new Ellipse2D.Double(2 + rnd.nextDouble() * 12, 2 + rnd.nextDouble() * 12, 0.9, 0.9));
    }

    // ---- flags and the border --------------------------------------------------------------------

    /** A flag of the roads at its foot {@code x}, {@code y}: a pole with a blue pennant. */
    static void flag(Graphics2D g, double x, double y) {
        g.setColor(SHADOW);
        g.fill(new Ellipse2D.Double(x - 2, y - 1, 5, 2.4));
        g.setColor(new Color(0, 0, 0, 60));
        g.setStroke(new BasicStroke(0.7f));
        g.draw(new Line2D.Double(x + 0.6, y, x + 5, y + 3));
        g.setColor(new Color(0x4A3A2A));
        g.setStroke(new BasicStroke(0.8f));
        g.draw(new Line2D.Double(x, y, x, y - 9));
        Path2D f = new Path2D.Double();
        f.moveTo(x + 0.3, y - 9);
        f.lineTo(x + 6, y - 7.6);
        f.lineTo(x + 0.3, y - 5.6);
        f.closePath();
        g.setPaint(new GradientPaint((float) x, (float) y, BLUE[0], (float) (x + 6), (float) y, BLUE[1]));
        g.fill(f);
        g.setColor(new Color(0xE8C860));
        g.fill(new Ellipse2D.Double(x - 0.7, y - 10, 1.4, 1.4));
    }

    /** A post of the border, white over blue, at its foot. */
    static void borderPost(Graphics2D g, double x, double y) {
        g.setColor(new Color(0, 0, 0, 80));
        g.fill(new Ellipse2D.Double(x - 1, y - 0.6, 3.6, 1.8));
        g.setColor(new Color(0x2A2420));
        g.fill(new Rectangle2D.Double(x - 1, y - 6, 2, 6.4));
        g.setColor(new Color(0xF2ECDC));
        g.fill(new Rectangle2D.Double(x - 0.7, y - 5.8, 1.4, 2.6));
        g.setColor(BLUE[1]);
        g.fill(new Rectangle2D.Double(x - 0.7, y - 3.2, 1.4, 2.8));
    }

    // ---- goods -----------------------------------------------------------------------------------

    // --8<-- [start:good]
    /** A good as it is carried or waits at a flag, about 7 × 5 units around {@code x}, {@code y}. */
    static void good(Graphics2D g, Good good, double x, double y, double ang) {
        AffineTransform at = g.getTransform();
        g.translate(x, y);
        g.rotate(ang);
        g.setColor(new Color(0, 0, 0, 60));
        switch (good) {
            case LOG -> {
                g.fill(new RoundRectangle2D.Double(-3.5 + 0.5, -1.2 + 0.6, 7, 2.6, 2, 2));
                g.setPaint(new GradientPaint(0, -1.2f, new Color(0xA07850), 0, 1.4f, new Color(0x5A3A20)));
                g.fill(new RoundRectangle2D.Double(-3.5, -1.2, 7, 2.6, 2, 2));
                g.setColor(new Color(0xE8C898));
                g.fill(new Ellipse2D.Double(2.4, -1.2, 1.4, 2.6));
            }
            case PLANK -> {
                g.fill(new Rectangle2D.Double(-3.5 + 0.5, -1 + 0.6, 7, 2));
                g.setPaint(new GradientPaint(0, -1, new Color(0xF0D8A8), 0, 1, new Color(0xB89060)));
                g.fill(new Rectangle2D.Double(-3.5, -1, 7, 2));
                g.setColor(new Color(0x8A6A40));
                g.setStroke(new BasicStroke(0.25f));
                g.draw(new Line2D.Double(-3.5, 0, 3.5, 0));
            }
            case STONE -> {
                g.fill(new RoundRectangle2D.Double(-2 + 0.5, -1.6 + 0.6, 4, 3.2, 1, 1));
                g.setPaint(new GradientPaint(-2, -1.6f, new Color(0xE0DACC), 2, 1.6f, new Color(0x7A766A)));
                g.fill(new RoundRectangle2D.Double(-2, -1.6, 4, 3.2, 1, 1));
            }
            case GRAIN -> {
                g.setColor(new Color(0x9A7A2A));
                g.setStroke(new BasicStroke(0.4f));
                for (int i = -2; i <= 2; i++) g.draw(new Line2D.Double(0, 2, i * 0.9, -1.6));
                g.setColor(new Color(0xF0C850));
                for (int i = -2; i <= 2; i++) g.fill(new Ellipse2D.Double(i * 0.9 - 0.6, -2.6, 1.2, 1.8));
                g.setColor(new Color(0x7A5A20));
                g.fill(new Rectangle2D.Double(-1, 0.4, 2, 0.6));
            }
            case FLOUR -> {
                g.fill(new RoundRectangle2D.Double(-2.2 + 0.5, -2 + 0.6, 4.4, 4, 2, 2));
                g.setPaint(new GradientPaint(-2, -2, new Color(0xFAF6EA), 2, 2, new Color(0xB8B0A0)));
                g.fill(new RoundRectangle2D.Double(-2.2, -2, 4.4, 4, 2, 2));
                g.setColor(new Color(0x8A7A60));
                g.fill(new Rectangle2D.Double(-1, -2.4, 2, 0.8));
            }
            case BREAD -> {
                g.fill(new Ellipse2D.Double(-2.6 + 0.5, -1.5 + 0.6, 5.2, 3));
                g.setPaint(new GradientPaint(0, -1.5f, new Color(0xE8A85A), 0, 1.5f, new Color(0x8A4A1A)));
                g.fill(new Ellipse2D.Double(-2.6, -1.5, 5.2, 3));
                g.setColor(new Color(0xF8D8A0));
                g.setStroke(new BasicStroke(0.3f));
                for (int i = -1; i <= 1; i++) g.draw(new Line2D.Double(i * 1.3 - 0.4, -0.8, i * 1.3 + 0.4, 0.6));
            }
            case FISH -> {
                Path2D f = new Path2D.Double();
                f.moveTo(-3, 0);
                f.curveTo(-1.5, -1.6, 1.5, -1.6, 2.2, 0);
                f.lineTo(3.4, -1.2);
                f.lineTo(3.4, 1.2);
                f.lineTo(2.2, 0);
                f.curveTo(1.5, 1.6, -1.5, 1.6, -3, 0);
                f.closePath();
                g.setPaint(new GradientPaint(0, -1.4f, new Color(0xE0E8EC), 0, 1.4f, new Color(0x5A7A8A)));
                g.fill(f);
                g.setColor(new Color(0x1A2228));
                g.fill(new Ellipse2D.Double(-2.2, -0.5, 0.7, 0.7));
            }
            case WATER -> {
                g.setPaint(new GradientPaint(-2, 0, new Color(0xA07848), 2, 0, new Color(0x5A3A20)));
                g.fill(new RoundRectangle2D.Double(-2.2, -2, 4.4, 4.2, 1.2, 1.2));
                g.setColor(new Color(0x6AB0D8));
                g.fill(new Ellipse2D.Double(-1.7, -2, 3.4, 1.4));
                g.setColor(new Color(0x3A3A3A));
                g.setStroke(new BasicStroke(0.35f));
                g.draw(new Line2D.Double(-2.2, -0.2, 2.2, -0.2));
                g.draw(new Line2D.Double(-2.2, 1.2, 2.2, 1.2));
            }
            case WOOL -> {
                g.fill(new Ellipse2D.Double(-2.4 + 0.5, -2 + 0.6, 4.8, 4));
                for (double[] b : new double[][]{{-1.2, -0.8}, {1, -0.9}, {0, 0.6}, {-1.4, 0.8}, {1.3, 0.7}, {0, -1.2}}) {
                    g.setPaint(radial(b[0] - 0.3, b[1] - 0.3, 1.4, new float[]{0, 1}, new Color(0xFFFCF4), new Color(0xC8C0AE)));
                    g.fill(new Ellipse2D.Double(b[0] - 1.2, b[1] - 1.2, 2.4, 2.4));
                }
            }
            case CLOTH -> {
                g.fill(new RoundRectangle2D.Double(-3 + 0.5, -1.8 + 0.6, 6, 3.6, 1, 1));
                g.setPaint(new GradientPaint(0, -1.8f, new Color(0x8AA8D0), 0, 1.8f, new Color(0x3A5A8A)));
                g.fill(new RoundRectangle2D.Double(-3, -1.8, 6, 3.6, 1, 1));
                g.setColor(new Color(0xE8DCC0));
                g.fill(new Ellipse2D.Double(2.2, -1.8, 1.4, 3.6));
                g.setColor(new Color(0x2A4060));
                g.setStroke(new BasicStroke(0.25f));
                g.draw(new Line2D.Double(-3, -0.4, 2.6, -0.4));
                g.draw(new Line2D.Double(-3, 0.8, 2.6, 0.8));
            }
            case TOOLS -> {
                g.setColor(new Color(0x8A6A40));
                g.setStroke(new BasicStroke(0.8f));
                g.draw(new Line2D.Double(-2, 2, 1.4, -1.4));
                g.setColor(new Color(0x9AA0A8));
                g.fill(new Rectangle2D.Double(0, -2.6, 3, 1.6));
                g.setColor(new Color(0x6A4A2A));
                g.draw(new Line2D.Double(2, 2, -1.2, -1.2));
                g.setColor(new Color(0xC8CCD0));
                g.fill(new Ellipse2D.Double(-2.6, -2.6, 2.2, 2.2));
            }
        }
        g.setTransform(at);
    }
    // --8<-- [end:good]

    // ---- animals ---------------------------------------------------------------------------------

    /** A sheep around 0, 0, facing right: a woolly back with a dark head. */
    static void sheep(Graphics2D g, double s, boolean winter) {
        AffineTransform at = g.getTransform();
        g.scale(s, s);
        g.setColor(new Color(10, 20, 5, 85));
        g.fill(new Ellipse2D.Double(-3.2 + 1.2, -2 + 1.3, 6.8, 4.2));
        g.setColor(new Color(0x2A2420));
        for (double[] p : new double[][]{{-2, -1.8}, {-2, 1.4}, {1.6, -1.8}, {1.6, 1.4}}) {
            g.fill(new Rectangle2D.Double(p[0], p[1], 0.7, 0.5));
        }
        Random rnd = new Random(5);
        int light = winter ? 0xF4EEE0 : 0xFCF8EE;
        g.setPaint(radial(-0.8, -0.8, 3.8, new float[]{0, 1}, new Color(light), new Color(0xC8C0AE)));
        g.fill(new Ellipse2D.Double(-3.2, -2.1, 6.2, 4.2));
        for (int i = 0; i < 9; i++) {
            double a = i * Math.PI * 2 / 9;
            double bx = Math.cos(a) * 2.5;
            double by = Math.sin(a) * 1.6;
            g.setPaint(radial(bx - 0.4, by - 0.4, 1.3, new float[]{0, 1}, new Color(0xFFFCF4), new Color(0xD0C8B4)));
            g.fill(new Ellipse2D.Double(bx - 1.1 - 0.2, by - 1.1, 2.2, 2.2));
        }
        g.setColor(new Color(150, 140, 120, 90));
        g.setStroke(new BasicStroke(0.25f));
        for (int i = 0; i < 5; i++) {
            g.draw(new Arc2D.Double(-2 + rnd.nextDouble() * 3, -1.3 + rnd.nextDouble() * 1.8, 1.1, 1.1, 0, 200, Arc2D.OPEN));
        }
        g.setPaint(radial(3.4, -0.4, 1.6, new float[]{0, 1}, new Color(0x5A504A), new Color(0x1E1A18)));
        g.fill(new Ellipse2D.Double(2.4, -1.05, 2.6, 2.1));
        g.setColor(new Color(0x2A2420));
        g.fill(new Ellipse2D.Double(2.4, -1.9, 1, 1));
        g.fill(new Ellipse2D.Double(2.4, 0.9, 1, 1));
        g.setTransform(at);
    }

    /** The shepherd's dog around 0, 0, facing right: black with a white chest and muzzle. */
    static void dog(Graphics2D g) {
        g.setColor(new Color(10, 20, 5, 85));
        g.fill(new Ellipse2D.Double(-2.6 + 1, -1.2 + 1.1, 5.6, 2.6));
        g.setColor(new Color(0x1E1A18));
        g.setStroke(new BasicStroke(0.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new QuadCurve2D.Double(-2.4, 0, -3.6, -0.6, -4.2, 0.6));
        g.setPaint(radial(-0.4, -0.5, 2.6, new float[]{0, 1}, new Color(0x4A4440), new Color(0x141210)));
        g.fill(new Ellipse2D.Double(-2.6, -1.1, 5, 2.2));
        g.setColor(new Color(0xF2ECDC));
        g.fill(new Ellipse2D.Double(1, -0.9, 1.6, 1.8));
        g.setColor(new Color(0x1E1A18));
        g.fill(new Ellipse2D.Double(2, -0.85, 2, 1.7));
        g.setColor(new Color(0xF2ECDC));
        g.fill(new Ellipse2D.Double(3.3, -0.35, 1, 0.7));
        g.setColor(new Color(0x2A2420));
        g.fill(new Ellipse2D.Double(2, -1.3, 0.8, 0.7));
        g.fill(new Ellipse2D.Double(2, 0.6, 0.8, 0.7));
    }

    // --8<-- [start:wolf]
    /**
     * A wolf around 0, 0, facing right, at a run: longer and leaner than the dog, grey with a pale belly,
     * a bushy tail, pointed ears and yellow eyes; {@code stride} 0 or 1 moves its legs.
     */
    static void wolf(Graphics2D g, int stride) {
        g.setColor(new Color(10, 20, 5, 85));
        g.fill(new Ellipse2D.Double(-4.4 + 1.2, -1.6 + 1.3, 9.4, 3.4));
        g.setColor(new Color(0x3A3632));
        double reach = stride == 0 ? 1.4 : -1.4;
        g.setStroke(new BasicStroke(0.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int side = -1; side <= 1; side += 2) {
            g.draw(new Line2D.Double(2.2, side * 1.1, 2.2 + reach, side * 1.9));
            g.draw(new Line2D.Double(-2.4, side * 1.1, -2.4 - reach, side * 1.9));
        }
        Path2D tail = new Path2D.Double();
        tail.moveTo(-3.6, -0.6);
        tail.quadTo(-6.4, -1.2 + stride * 0.6, -7.4, 0.2);
        tail.quadTo(-6.2, 0.9, -3.6, 0.7);
        tail.closePath();
        g.setPaint(new GradientPaint(-7, 0, new Color(0x5A5650), -3, 0, new Color(0x8A847A)));
        g.fill(tail);
        g.setPaint(radial(-0.6, -0.9, 4.6, new float[]{0, 0.6f, 1}, new Color(0xB8B0A2), new Color(0x7A746A),
                new Color(0x3E3A34)));
        g.fill(new Ellipse2D.Double(-4.2, -1.6, 8.2, 3.2));
        g.setColor(new Color(0xD8D0C0));
        g.fill(new Ellipse2D.Double(-1.6, 0.4, 4, 1));
        Path2D head = new Path2D.Double();
        head.moveTo(3, -1.3);
        head.lineTo(5.2, -0.8);
        head.lineTo(6.6, 0);
        head.lineTo(5.2, 0.8);
        head.lineTo(3, 1.3);
        head.closePath();
        g.setPaint(new GradientPaint(3, -1, new Color(0x9A9488), 6, 1, new Color(0x4A4640)));
        g.fill(head);
        g.setColor(new Color(0x2E2A26));
        g.fill(new Ellipse2D.Double(6.1, -0.35, 0.8, 0.7));
        Path2D ears = new Path2D.Double();
        ears.moveTo(3.4, -1.1);
        ears.lineTo(3.0, -2.4);
        ears.lineTo(4.2, -1.2);
        ears.moveTo(3.4, 1.1);
        ears.lineTo(3.0, 2.4);
        ears.lineTo(4.2, 1.2);
        g.setColor(new Color(0x4A4640));
        g.fill(ears);
        g.setColor(new Color(0xF0C040));
        g.fill(new Ellipse2D.Double(4.6, -0.75, 0.6, 0.45));
        g.fill(new Ellipse2D.Double(4.6, 0.3, 0.6, 0.45));
    }
    // --8<-- [end:wolf]

    // ---- smoke and fire --------------------------------------------------------------------------

    /** A puff of smoke around 0, 0, of radius {@code r}: pale from a chimney, dark from a fire. */
    static void puff(Graphics2D g, double r, int a, boolean dark) {
        Color inner = dark ? new Color(70, 62, 56, a) : new Color(220, 220, 215, a);
        Color outer = dark ? new Color(40, 36, 32, 0) : new Color(160, 160, 160, 0);
        g.setPaint(radial(-r * 0.3, -r * 0.3, r, new float[]{0, 1}, inner, outer));
        g.fill(new Ellipse2D.Double(-r, -r, r * 2, r * 2));
    }

    /** A tongue of flame around 0, its foot at 0, {@code h} high, flickering by {@code phase}. */
    static void flame(Graphics2D g, double h, double phase) {
        double w = h * 0.45;
        double lean = Math.sin(phase * 2.1) * w * 0.4;
        Path2D f = new Path2D.Double();
        f.moveTo(-w, 0);
        f.quadTo(-w * 1.1, -h * 0.5, lean, -h);
        f.quadTo(w * 1.1, -h * 0.5, w, 0);
        f.closePath();
        g.setPaint(new GradientPaint(0, 0, new Color(255, 220, 90, 230), 0, (float) -h, new Color(220, 60, 20, 160)));
        g.fill(f);
        Path2D inner = new Path2D.Double();
        inner.moveTo(-w * 0.45, 0);
        inner.quadTo(-w * 0.5, -h * 0.35, lean * 0.6, -h * 0.6);
        inner.quadTo(w * 0.5, -h * 0.35, w * 0.45, 0);
        inner.closePath();
        g.setColor(new Color(255, 250, 200, 220));
        g.fill(inner);
    }

    // ---- the seasons -----------------------------------------------------------------------------

    /** The sign of a season around 0, 0, about 10 units: a blossom, the sun, a leaf, a snowflake. */
    static void season(Graphics2D g, int season) {
        switch (season) {
            case 0 -> {
                g.setColor(new Color(0xF2A8C8));
                for (int i = 0; i < 5; i++) {
                    double a = i * Math.PI * 2 / 5 - Math.PI / 2;
                    g.fill(new Ellipse2D.Double(Math.cos(a) * 2.2 - 1.8, Math.sin(a) * 2.2 - 1.8, 3.6, 3.6));
                }
                g.setColor(new Color(0xF8D84A));
                g.fill(new Ellipse2D.Double(-1.3, -1.3, 2.6, 2.6));
            }
            case 1 -> {
                g.setColor(new Color(0xF8C838));
                g.setStroke(new BasicStroke(0.9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                for (int i = 0; i < 8; i++) {
                    double a = i * Math.PI / 4;
                    g.draw(new Line2D.Double(Math.cos(a) * 3.2, Math.sin(a) * 3.2, Math.cos(a) * 4.8, Math.sin(a) * 4.8));
                }
                g.setPaint(radial(-0.8, -0.8, 3, new float[]{0, 1}, new Color(0xFFF2A0), new Color(0xF0A020)));
                g.fill(new Ellipse2D.Double(-2.6, -2.6, 5.2, 5.2));
            }
            case 2 -> {
                Path2D leaf = new Path2D.Double();
                leaf.moveTo(-4, 3.4);
                leaf.quadTo(-4, -3.6, 4, -4);
                leaf.quadTo(3.6, 3.6, -4, 3.4);
                leaf.closePath();
                g.setPaint(new GradientPaint(-4, -4, new Color(0xF0A040), 4, 4, new Color(0xA8401E)));
                g.fill(leaf);
                g.setColor(new Color(0x6A2A10));
                g.setStroke(new BasicStroke(0.5f));
                g.draw(new Line2D.Double(-4.6, 4, 3, -3));
            }
            default -> {
                g.setColor(new Color(0xD8ECFA));
                g.setStroke(new BasicStroke(0.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                for (int i = 0; i < 6; i++) {
                    double a = i * Math.PI / 3;
                    double cx = Math.cos(a);
                    double cy = Math.sin(a);
                    g.draw(new Line2D.Double(0, 0, cx * 4.4, cy * 4.4));
                    double b = a + 0.6;
                    double c = a - 0.6;
                    g.draw(new Line2D.Double(cx * 2.6, cy * 2.6, cx * 2.6 + Math.cos(b) * 1.4, cy * 2.6 + Math.sin(b) * 1.4));
                    g.draw(new Line2D.Double(cx * 2.6, cy * 2.6, cx * 2.6 + Math.cos(c) * 1.4, cy * 2.6 + Math.sin(c) * 1.4));
                }
            }
        }
    }

    // ---- marks -----------------------------------------------------------------------------------

    /** Corners round a chosen thing, its box {@code x}, {@code y}, {@code w}, {@code h}. */
    static void brackets(Graphics2D g, double x, double y, double w, double h) {
        double k = Math.min(5, Math.min(w, h) / 3);
        g.setStroke(new BasicStroke(1f));
        for (int pass = 0; pass < 2; pass++) {
            g.setColor(pass == 0 ? new Color(0, 0, 0, 120) : Color.WHITE);
            double o = pass == 0 ? 0.6 : 0;
            double[][] c = {{x, y, 1, 1}, {x + w, y, -1, 1}, {x, y + h, 1, -1}, {x + w, y + h, -1, -1}};
            for (double[] p : c) {
                g.draw(new Line2D.Double(p[0] + o, p[1] + o, p[0] + o + p[2] * k, p[1] + o));
                g.draw(new Line2D.Double(p[0] + o, p[1] + o, p[0] + o, p[1] + o + p[3] * k));
            }
        }
    }

    /** Where a building would stand: a small house, green where it may, red where it may not. */
    static void marker(Graphics2D g, double x, double y, boolean ok) {
        Path2D h = new Path2D.Double();
        h.moveTo(x - 3, y);
        h.lineTo(x - 3, y - 3);
        h.lineTo(x, y - 5.6);
        h.lineTo(x + 3, y - 3);
        h.lineTo(x + 3, y);
        h.closePath();
        AffineTransform at = g.getTransform();
        g.translate(0.6, 0.6);
        g.setColor(new Color(0, 0, 0, 120));
        g.fill(h);
        g.setTransform(at);
        g.setColor(ok ? new Color(0x8AE070) : new Color(0xF07058));
        g.fill(h);
        g.setColor(new Color(0x1A3010));
        g.setStroke(new BasicStroke(0.4f));
        g.draw(h);
    }
}
