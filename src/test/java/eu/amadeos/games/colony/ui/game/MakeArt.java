package eu.amadeos.games.colony.ui.game;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Draws the files made from the game's own art, which are part of the repository: the tileset picture
 * that Tiled shows ({@code maps/tiles.png}: grass, forest, rock, water, mountain) and the program icon, a
 * house with a tree on a round meadow, at 48 and 256 pixels. {@code ./gradlew art}, then for Windows:
 *
 * <pre>
 * python3 -c "from PIL import Image; Image.open('build/icon-256.png').save('packaging/windows/amade-colony-game.ico',
 *     sizes=[(16,16),(24,24),(32,32),(48,48),(64,64),(128,128),(256,256)])"
 * </pre>
 */
public final class MakeArt {
    private static final int[] GROUND = {0x6E9A40, 0x2E5E26, 0x9A968A, 0x3A6E8A, 0x6A6058};

    private MakeArt() {
    }

    public static void main(String[] args) throws Exception {
        ImageIO.write(tiles(), "png", new File("src/main/resources/eu/amadeos/games/colony/maps/tiles.png"));
        new File("src/main/resources/icons").mkdirs();
        ImageIO.write(icon(48), "png", new File("src/main/resources/icons/amade-colony-game.png"));
        new File("build").mkdirs();
        ImageIO.write(icon(256), "png", new File("build/icon-256.png"));
    }

    /** Five tiles of 16 × 16 pixels in a row, as the tileset counts them from 0: plain, with a grain. */
    static BufferedImage tiles() {
        BufferedImage image = new BufferedImage(16 * GROUND.length, 16, BufferedImage.TYPE_INT_ARGB);
        for (int kind = 0; kind < GROUND.length; kind++) {
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    double grain = Art.fbm(x / 4.0, y / 4.0, kind + 1) - 0.5;
                    if (kind == 3) grain += 0.2 * Math.sin((x * 0.3 + y) * 1.1);   // the ripples of the water
                    image.setRGB(kind * 16 + x, y, 0xFF000000 | Art.shade(GROUND[kind], 1 + 0.35 * grain));
                }
            }
        }
        return image;
    }

    /** A residence and a tree, seen from the front and a little above, on a round patch of meadow. */
    static BufferedImage icon(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.scale(size / 24.0, size / 24.0);
        g.setPaint(new RadialGradientPaint(9, 8, 16, new float[]{0, 0.6f, 1},
                new Color[]{new Color(0xA8D070), new Color(0x5E9A38), new Color(0x2E5A1E)}));
        g.fill(new Ellipse2D.Double(0.5, 0.5, 23, 23));
        Graphics2D house = (Graphics2D) g.create();
        house.translate(3.4, 9.4);
        house.scale(0.4, 0.4);
        Houses.building(house, "residence", 2, 2);
        house.dispose();
        Graphics2D tree = (Graphics2D) g.create();
        tree.translate(19, 12.5);
        tree.scale(0.34, 0.34);
        Art.tree(tree, 0, 0, false, 1, false, false, 3);
        tree.dispose();
        g.dispose();
        return image;
    }
}
