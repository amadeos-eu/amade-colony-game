package eu.amadeos.games.colony.ui.game;

import eu.amadeos.games.colony.domain.Good;
import eu.amadeos.games.colony.domain.Work;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.QuadCurve2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;

import static eu.amadeos.games.colony.ui.game.Art.radial;
import static eu.amadeos.games.colony.ui.game.Art.shade;

/**
 * The people of Colony, drawn in code: each with a face, hair and clothes of their own, and headwear that
 * shows the job, a blue cap or scarf for the carriers, a knitted cap for the woodcutter, a straw hat for the
 * farmer. A person is drawn standing at their feet, 0, 0, a little larger than the land around them, as
 * the classic economy games did, so that they can be told apart.
 */
final class Folk {
    /** People are drawn this much larger than the land. */
    static final double SCALE = 1.3;

    /** Headwear. */
    enum Hat { NONE, CAP, STRAW, SCARF, HOOD, FELT, TOQUE, KNIT }

    /** The tool a worker works with out on the land. */
    enum Tool { NONE, AXE, SPADE, PICK, ROD, SCYTHE, HAMMER, CROOK }

    // --8<-- [start:look]
    /** How a person looks: shirt, legs or skirt, skin, hair, headwear and its colour, an apron, a beard. */
    record Look(int shirt, int legs, int skin, int hair, Hat hat, int hatColour, boolean skirt, int apron, boolean beard) {
    }

    static final int[] SKIN = {0xF0C8A0, 0xE0A880, 0xC08860, 0x8A5A3A};
    static final int[] HAIR = {0x3A2414, 0x6A4424, 0xC89850, 0x1A1410, 0x9A9890, 0xA0482A};
    static final int CARRIER_LOOKS = 12;

    /** The carriers: everyday clothes in many colours; the blue cap or scarf shows they work for the colony. */
    static Look carrier(int i) {
        int[] shirts = {0xB8A27A, 0x8A5A3A, 0xA8B4A0, 0xC88A4A, 0x7A6A9A, 0x9AA27A, 0xD8C8A0, 0x6A7A8A, 0xB86A5A};
        int[] legs = {0x5A4A3A, 0x4A4E5A, 0x6A5638, 0x3A3A3A};
        boolean skirt = i % 3 == 1;
        int blue = Art.BLUE[1].getRGB() & 0xFFFFFF;
        return new Look(shirts[(i * 5) % shirts.length], legs[i % legs.length], SKIN[(i * 3) % SKIN.length],
                HAIR[(i * 7) % HAIR.length], skirt ? Hat.SCARF : i % 4 == 3 ? Hat.NONE : Hat.CAP, blue, skirt,
                skirt && i % 2 == 0 ? 0xE8E0CC : 0, !skirt && i % 5 == 2);
    }

    /** A builder: a leather apron, sleeves rolled up. */
    static Look builder(int i) {
        return new Look(0xC86A3A, 0x4A4E5A, SKIN[i % SKIN.length], HAIR[(i * 5) % HAIR.length], Hat.NONE, 0, false,
                0x9A7A50, i % 2 == 0);
    }

    /** The worker of a building, by what the building does and makes. */
    static Look worker(Work work, Good makes, int i) {
        int skin = SKIN[i % SKIN.length];
        int hair = HAIR[(i * 5 + 1) % HAIR.length];
        return switch (work) {
            case FELL -> new Look(0xA83A2A, 0x4A3A2A, skin, hair, Hat.KNIT, 0x6A2A1A, false, 0, true);
            case PLANT -> new Look(0x4E7A3A, 0x5A4A3A, skin, hair, Hat.HOOD, 0x3A6A2E, false, 0, false);
            case QUARRY -> new Look(0x8A8A82, 0x4A4038, skin, hair, Hat.CAP, 0x6A6A6A, false, 0x7A5A3A, false);
            case FISH -> new Look(0x4A6A8A, 0x3A3A3A, skin, hair, Hat.FELT, 0xD8B040, false, 0, true);
            case FARM -> new Look(0xF2ECDC, 0x8A4A3A, skin, hair, Hat.STRAW, 0xE8C878, true, 0xD8C8A0, false);
            case GRAZE -> new Look(0x7A5A3A, 0x4A4038, skin, hair, Hat.FELT, 0x5A4632, false, 0, true);
            case WATCH -> new Look(0x3E5A8A, 0x3A3A3A, skin, hair, Hat.HOOD, 0x2A3E64, false, 0, false);
            default -> switch (makes == null ? Good.WATER : makes) {
                case PLANK -> new Look(0xC89A5A, 0x4A4038, skin, hair, Hat.CAP, 0x7A5A36, false, 0x8A6A40, false);
                case FLOUR -> new Look(0xF0ECE0, 0x8A8070, skin, hair, Hat.CAP, 0xF0ECE0, false, 0, false);
                case BREAD -> new Look(0xF2EEE4, 0x6A5A4A, skin, hair, Hat.TOQUE, 0xFFFFFF, false, 0xF8F4EA, false);
                case CLOTH -> new Look(0x8A5A9A, 0x5A4A6A, skin, hair, Hat.SCARF, 0xD8A040, true, 0, false);
                case TOOLS -> new Look(0x6A6A6A, 0x3A3A3A, skin, hair, Hat.NONE, 0, false, 0x5A3A22, true);
                default -> new Look(0x6A8AA8, 0x5A4A3A, skin, hair, Hat.SCARF, 0x4A7AA8, true, 0xE8E0CC, false);
            };
        };
    }
    // --8<-- [end:look]

    static Tool tool(Work work) {
        return switch (work) {
            case FELL -> Tool.AXE;
            case PLANT -> Tool.SPADE;
            case FARM -> Tool.SCYTHE;
            case QUARRY -> Tool.PICK;
            case FISH -> Tool.ROD;
            case GRAZE -> Tool.CROOK;
            default -> Tool.NONE;
        };
    }

    // --8<-- [start:person]
    /**
     * A person standing at 0, 0, seen from above and the south, facing the viewer or, with {@code back},
     * walking away; {@code step} -1, 0 or 1 moves the legs. With {@code armsUp} both hands are raised to hold
     * a load over the head; the load is drawn on its own.
     */
    static void person(Graphics2D g, Look l, boolean armsUp, boolean back, int step) {
        AffineTransform at = g.getTransform();
        g.scale(SCALE, SCALE);
        g.setColor(new Color(10, 20, 5, 90));
        g.fill(new Ellipse2D.Double(-2.2 + 1.3, -1.1 + 0.9, 5.4, 2.6));
        if (l.skirt()) {
            Path2D sk = new Path2D.Double();
            sk.moveTo(-2, -4.2);
            sk.lineTo(2, -4.2);
            sk.lineTo(2.8, -0.9);
            sk.quadTo(0, -0.3, -2.8, -0.9);
            sk.closePath();
            g.setPaint(new GradientPaint(-2.5f, 0, new Color(shade(l.legs(), 1.25)), 2.5f, 0, new Color(shade(l.legs(), 0.7))));
            g.fill(sk);
            g.setColor(new Color(0, 0, 0, 50));
            g.setStroke(new BasicStroke(0.25f));
            g.draw(new Line2D.Double(-0.8, -3.8, -1.2, -1));
            g.draw(new Line2D.Double(0.9, -3.8, 1.3, -1));
            g.setColor(new Color(0x2A2018));
            g.fill(new Ellipse2D.Double(-1.8, -1.1 + step * 0.3, 1.5, 1.1));
            g.fill(new Ellipse2D.Double(0.4, -1.2 - step * 0.3, 1.5, 1.1));
        } else {
            for (int s = -1; s <= 1; s += 2) {
                double lx = s * 0.95 - 0.65;
                double ly = -3.6 + s * step * 0.45;
                g.setPaint(new GradientPaint((float) lx, 0, new Color(shade(l.legs(), s < 0 ? 1.15 : 0.85)), (float) (lx + 1.3), 0,
                        new Color(shade(l.legs(), 0.65))));
                g.fill(new Rectangle2D.Double(lx, ly, 1.35, 2.9));
                g.setColor(new Color(0x2A2018));
                g.fill(new RoundRectangle2D.Double(lx - 0.1, ly + 2.4, 1.6, 1.1, 0.6, 0.6));
            }
        }
        Path2D body = new Path2D.Double();
        body.moveTo(-2.6, -7);
        body.quadTo(0, -7.7, 2.6, -7);
        body.lineTo(2.1, -3.6);
        body.quadTo(0, -3.2, -2.1, -3.6);
        body.closePath();
        g.setPaint(new GradientPaint(-2.6f, 0, new Color(shade(l.shirt(), 1.25)), 2.6f, 0, new Color(shade(l.shirt(), 0.68))));
        g.fill(body);
        g.setColor(new Color(0, 0, 0, 45));
        g.setStroke(new BasicStroke(0.25f));
        g.draw(new Line2D.Double(0, -7.3, 0, -3.4));
        if (l.apron() != 0 && !back) {
            Path2D ap = new Path2D.Double();
            ap.moveTo(-1.5, -5.8);
            ap.lineTo(1.5, -5.8);
            ap.lineTo(1.9, -1.6);
            ap.lineTo(-1.9, -1.6);
            ap.closePath();
            g.setPaint(new GradientPaint(-2f, 0, new Color(shade(l.apron(), 1.1)), 2f, 0, new Color(shade(l.apron(), 0.75))));
            g.fill(ap);
        }
        g.setColor(new Color(shade(l.legs(), 0.5)));
        g.fill(new Rectangle2D.Double(-2.1, -4.1, 4.2, 0.55));
        if (!back) {
            g.setColor(new Color(0xC8A050));
            g.fill(new Rectangle2D.Double(-0.35, -4.15, 0.7, 0.65));
        }
        for (int s = -1; s <= 1; s += 2) {
            double sx = s * 2.4;
            double sy = -6.6;
            double hx = armsUp ? s * 1.9 : s * 2.7;
            double hy = armsUp ? -11.4 : -3.9 - (armsUp ? 0 : s * step * 0.4);
            g.setColor(new Color(shade(l.shirt(), s < 0 ? 1.1 : 0.62)));
            g.setStroke(new BasicStroke(1.25f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(new Line2D.Double(sx, sy, hx, hy + (armsUp ? 0.9 : -0.6)));
            g.setColor(new Color(l.skin()));
            g.fill(new Ellipse2D.Double(hx - 0.6, hy - 0.6, 1.2, 1.2));
        }
        head(g, l, back);
        g.setTransform(at);
    }
    // --8<-- [end:person]

    /** The head with hair, face and headwear. */
    private static void head(Graphics2D g, Look l, boolean back) {
        double hy = -8.9;
        g.setColor(new Color(shade(l.skin(), 0.8)));
        g.fill(new Rectangle2D.Double(-0.6, hy + 1, 1.2, 1.2));
        g.setPaint(radial(-0.6, hy - 0.6, 2.2, new float[]{0, 1}, new Color(shade(l.skin(), 1.1)), new Color(shade(l.skin(), 0.78))));
        g.fill(new Ellipse2D.Double(-1.7, hy - 1.8, 3.4, 3.6));
        g.setColor(new Color(l.hair()));
        if (back) {
            g.fill(new Ellipse2D.Double(-1.8, hy - 1.9, 3.6, 3.5));
        } else {
            g.fill(new Arc2D.Double(-1.85, hy - 2, 3.7, 3.2, 0, 180, Arc2D.CHORD));
            if (l.skirt() && l.hat() != Hat.SCARF) g.fill(new RoundRectangle2D.Double(-2, hy - 1, 1, 3.2, 1, 1));
            g.setColor(new Color(0x2A1A10));
            g.fill(new Ellipse2D.Double(-0.95, hy - 0.2, 0.55, 0.55));
            g.fill(new Ellipse2D.Double(0.4, hy - 0.2, 0.55, 0.55));
            if (l.beard()) {
                g.setColor(new Color(l.hair()));
                g.fill(new Arc2D.Double(-1.5, hy - 0.4, 3, 3, 180, 180, Arc2D.CHORD));
            }
        }
        Color hc = new Color(l.hatColour());
        switch (l.hat()) {
            case CAP -> {
                g.setColor(new Color(shade(l.hatColour(), 0.7)));
                g.fill(new Ellipse2D.Double(-1.7, hy - 1.7, 3.4, 1));
                g.setPaint(new GradientPaint(-2f, 0, new Color(shade(l.hatColour(), 1.3)), 2f, 0, hc));
                g.fill(new Arc2D.Double(-1.9, hy - 2.5, 3.8, 3, 0, 180, Arc2D.CHORD));
            }
            case STRAW, FELT -> {
                double r = l.hat() == Hat.STRAW ? 3.6 : 3;
                g.setColor(new Color(0, 0, 0, 50));
                g.fill(new Ellipse2D.Double(-r + 0.5, hy - 1.6 + 0.6, r * 2, 2.6));
                g.setPaint(radial(-1, hy - 1.4, r, new float[]{0, 1}, new Color(shade(l.hatColour(), 1.2)),
                        new Color(shade(l.hatColour(), 0.8))));
                g.fill(new Ellipse2D.Double(-r, hy - 1.6, r * 2, 2.6));
                g.setColor(new Color(shade(l.hatColour(), l.hat() == Hat.STRAW ? 1.05 : 0.85)));
                g.fill(new Arc2D.Double(-1.6, hy - 3.2, 3.2, 3.4, 0, 180, Arc2D.CHORD));
                g.setColor(new Color(l.hat() == Hat.STRAW ? 0xA0402A : 0x2A2018));
                g.fill(new Rectangle2D.Double(-1.6, hy - 1.7, 3.2, 0.5));
            }
            case SCARF -> {
                g.setPaint(new GradientPaint(-2f, 0, new Color(shade(l.hatColour(), 1.3)), 2f, 0, hc));
                g.fill(new Arc2D.Double(-2, hy - 2.3, 4, 3.2, 0, 180, Arc2D.CHORD));
                g.setColor(new Color(0xF2ECDC));
                for (int i = 0; i < 3; i++) g.fill(new Ellipse2D.Double(-1.2 + i * 0.9, hy - 1.6 + (i % 2) * 0.5, 0.45, 0.45));
            }
            case HOOD -> {
                g.setPaint(new GradientPaint(-2f, 0, new Color(shade(l.hatColour(), 1.2)), 2f, 0, new Color(shade(l.hatColour(), 0.7))));
                Area hood = new Area(new Ellipse2D.Double(-2.2, hy - 2.4, 4.4, 4.6));
                if (!back) hood.subtract(new Area(new Ellipse2D.Double(-1.3, hy - 1, 2.6, 2.8)));
                g.fill(hood);
            }
            case TOQUE -> {
                g.setPaint(new GradientPaint(-2f, 0, Color.WHITE, 2f, 0, new Color(0xC8C4BA)));
                g.fill(new RoundRectangle2D.Double(-1.6, hy - 4, 3.2, 3, 1, 1));
                g.fill(new Ellipse2D.Double(-2.1, hy - 4.8, 4.2, 2.4));
            }
            case KNIT -> {
                g.setPaint(new GradientPaint(-2f, 0, new Color(shade(l.hatColour(), 1.3)), 2f, 0, hc));
                g.fill(new Arc2D.Double(-1.9, hy - 2.6, 3.8, 3.8, 0, 180, Arc2D.CHORD));
                g.fill(new Ellipse2D.Double(-0.6, hy - 3.1, 1.2, 1.2));
            }
            default -> {
            }
        }
    }

    // --8<-- [start:tool]
    /**
     * A worker's tool, held by a person at 0, 0; {@code frame} 0 or 1, the two ends of a swing. A person
     * facing left is the same picture mirrored.
     */
    static void tool(Graphics2D g, Tool tool, int frame) {
        AffineTransform at = g.getTransform();
        g.scale(SCALE, SCALE);
        switch (tool) {
            case AXE -> {
                swing(g, -1.5, -5, frame == 0 ? 0 : -0.9);
                g.setColor(new Color(0x7A5A36));
                g.setStroke(new BasicStroke(0.7f));
                g.draw(new Line2D.Double(-1.5, -5, -6, -9));
                Path2D head = new Path2D.Double();
                head.moveTo(-6.4, -9.6);
                head.lineTo(-4.6, -11);
                head.lineTo(-6.8, -11.6);
                head.lineTo(-8, -10);
                head.closePath();
                g.setColor(new Color(0xC8CCD0));
                g.fill(head);
                if (frame == 0) {
                    g.setTransform(at);
                    g.scale(SCALE, SCALE);
                    g.setColor(new Color(0xE0C090));
                    for (double[] c : new double[][]{{-9, -6}, {-10, -8.5}, {-8, -4}}) g.fill(new Rectangle2D.Double(c[0], c[1], 0.8, 0.6));
                }
            }
            case SPADE -> {
                g.translate(0, frame == 0 ? 0 : -1.2);
                g.setColor(new Color(0x7A5A36));
                g.setStroke(new BasicStroke(0.6f));
                g.draw(new Line2D.Double(2, -5, 4, 1));
                g.setColor(new Color(0xA0A4A8));
                g.fill(new Ellipse2D.Double(3, 0, 2.4, 2.4));
                g.setTransform(at);
                g.scale(SCALE, SCALE);
                g.setColor(new Color(0x5A4026));
                g.fill(new Ellipse2D.Double(3, 2, 4, 2));
            }
            case SCYTHE -> {
                swing(g, 1.5, -5, frame == 0 ? 0 : 0.7);
                g.setColor(new Color(0x7A5A36));
                g.setStroke(new BasicStroke(0.6f));
                g.draw(new Line2D.Double(1.5, -5, -4, 1));
                g.setColor(new Color(0xD8DCE0));
                g.setStroke(new BasicStroke(0.9f));
                g.draw(new Arc2D.Double(-9, -1, 8, 6, 0, 120, Arc2D.OPEN));
            }
            case ROD -> {
                swing(g, 1.5, -5, frame == 0 ? 0 : -0.12);
                g.setColor(new Color(0x7A5A36));
                g.setStroke(new BasicStroke(0.5f));
                g.draw(new Line2D.Double(1.5, -5, 12, -12));
                g.setColor(new Color(255, 255, 255, 140));
                g.setStroke(new BasicStroke(0.25f));
                g.draw(new QuadCurve2D.Double(12, -12, 14, -6, 16, -3));
                g.setColor(new Color(0xE84A3A));
                g.fill(new Ellipse2D.Double(15.4, -3.6, 1.4, 1.4));
                g.setColor(new Color(255, 255, 255, 110));
                g.draw(new Ellipse2D.Double(14, -4.4, 4, 2.6));
            }
            case PICK -> {
                swing(g, -1.5, -5, frame == 0 ? 0 : 1.1);
                g.setColor(new Color(0x7A5A36));
                g.setStroke(new BasicStroke(0.7f));
                g.draw(new Line2D.Double(-1.5, -5, -5, -1));
                g.setColor(new Color(0xA0A4A8));
                g.setStroke(new BasicStroke(0.9f));
                g.draw(new QuadCurve2D.Double(-7, -3, -5, -1.6, -3.4, 1));
                if (frame == 0) {
                    g.setColor(new Color(0xF0F0E0));
                    for (double[] c : new double[][]{{-8, 0}, {-7, 2}, {-9, -2}}) g.fill(new Rectangle2D.Double(c[0], c[1], 0.7, 0.7));
                }
            }
            case HAMMER -> {
                swing(g, 1.5, -5, frame == 0 ? 0 : 1.0);
                g.setColor(new Color(0x7A5A36));
                g.setStroke(new BasicStroke(0.6f));
                g.draw(new Line2D.Double(1.5, -5, 5, -8));
                g.setColor(new Color(0x8A9098));
                g.fill(new Rectangle2D.Double(4, -9.6, 2.6, 1.6));
            }
            case CROOK -> {
                g.setColor(new Color(0x7A5A36));
                g.setStroke(new BasicStroke(0.55f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(new Line2D.Double(-2.8, -3.6, -3.6, -13));
                g.draw(new Arc2D.Double(-3.6, -14.6, 2.4, 2.6, 180, -200, Arc2D.OPEN));
            }
            default -> {
            }
        }
        g.setTransform(at);
    }
    // --8<-- [end:tool]

    /** Turns what follows around the hand at {@code x}, {@code y}. */
    private static void swing(Graphics2D g, double x, double y, double angle) {
        g.rotate(angle, x, y);
    }
}
