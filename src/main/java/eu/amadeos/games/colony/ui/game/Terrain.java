package eu.amadeos.games.colony.ui.game;

import eu.amadeos.gameengine.camera.Camera;
import eu.amadeos.gameengine.road.RoadNetwork;
import eu.amadeos.gameengine.sprite.Sprite;
import eu.amadeos.gameengine.tile.Cell;
import eu.amadeos.games.colony.domain.Field;
import eu.amadeos.games.colony.domain.Game;
import eu.amadeos.games.colony.domain.Ground;
import eu.amadeos.games.colony.domain.Level;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The land of a mission: meadows, the darker floor of the forest, grey rock, rivers and lakes with their
 * banks, and mountains with snow on their peaks; over it the roads, trodden into the grass, and the land
 * outside the border, dimmed. In winter snow lies on all of it.
 *
 * <p>Every pixel is worked out on its own, from noise and from the tiles of the map, with light from the
 * top left, which takes a while; so it is done on other threads, in squares of {@value #CHUNK} ×
 * {@value #CHUNK} tiles, and a square shows the tiles' plain colours until its picture is ready. That is
 * the terrain of Sands (chapter 31) with two more layers:
 *
 * <ul>
 *   <li>The ground of a square is drawn once, with the light and how much snow each pixel takes. When the
 *       player lays a road or a tower moves the border, only the roads and the dim outside are mixed in
 *       again, which is quick. Only when a rock is quarried away is the square drawn anew.</li>
 *   <li>Each square has a summer and a winter picture. At the end of autumn the snow fades in, the winter
 *       picture drawn over the summer one ever less transparent, and in spring it fades out again.</li>
 * </ul>
 */
// --8<-- [start:terrain]
final class Terrain {
    static final int CHUNK = 8;
    /** Pixels for each unit of the world: the ground is as sharp on a screen of twice the logical size. */
    static final int PIXELS = 2;
    private static final int SIZE = CHUNK * Field.TILE;
    private static final int SIDE = SIZE * PIXELS;
    /** Pixels around a square that its shadows and slopes look at. */
    private static final int BORDER = 24;
    /** Tiles around a square whose ground its picture depends on, and whose roads. */
    private static final int AROUND = 3;
    /** The bits of a tile in the roads' mask: a road leaves it up, right, down, left; a flag; inside the border. */
    static final int UP = 1;
    static final int RIGHT = 2;
    static final int DOWN = 4;
    static final int LEFT = 8;
    static final int FLAG = 16;
    static final int INSIDE = 32;
    private static final int OUTSIDE_COLOUR = 0x101828;
    private static final ExecutorService WORKERS = Executors.newFixedThreadPool(
            Math.max(1, Runtime.getRuntime().availableProcessors() - 1), task -> {
                Thread thread = new Thread(task, "colony-terrain");
                thread.setDaemon(true);
                thread.setPriority(Thread.MIN_PRIORITY);
                return thread;
            });

    private final Level level;
    private final int across;
    private final Chunk[] chunks;
    private final Rectangle2D.Double rectangle = new Rectangle2D.Double();
    private final Color[] plain = new Color[Ground.values().length];
    private final Color plainWinter = new Color(0xDCE4EC);
    private Game game;
    private int landVersion = -1;
    private int roadsVersion = -1;
    private int borderVersion = -1;
    /** The ground of every tile, by {@link Ground}'s order, and the roads' mask, as last seen. */
    private byte[] ground;
    private byte[] mask;

    /** One square: its ground, lit, and the pictures with the roads and the border as they were last mixed in. */
    private static final class Chunk {
        final int col;
        final int row;
        Future<?> work;
        /** The ground without roads, lit; the light on each pixel and how much snow it takes, of 255. */
        volatile int[] base;
        volatile byte[] light;
        volatile byte[] cover;
        /** The ground and the mask around the square: as drawn and mixed in, and as asked for. */
        byte[] drawn;
        byte[] mixed;
        byte[] wantedGround;
        byte[] wantedMask;
        final AtomicReference<BufferedImage[]> ready = new AtomicReference<>();
        Sprite summer;
        Sprite winter;

        Chunk(int col, int row) {
            this.col = col;
            this.row = row;
        }
    }

    Terrain(Level level) {
        this.level = level;
        this.across = (level.width() + CHUNK - 1) / CHUNK;
        int down = (level.height() + CHUNK - 1) / CHUNK;
        plain[Ground.GRASS.ordinal()] = new Color(0x5E8C34);
        plain[Ground.FOREST.ordinal()] = new Color(0x3E6A26);
        plain[Ground.ROCK.ordinal()] = new Color(0x8C887C);
        plain[Ground.WATER.ordinal()] = new Color(0x3A6E8A);
        plain[Ground.MOUNTAIN.ordinal()] = new Color(0x6E665C);
        chunks = new Chunk[across * down];
        for (int i = 0; i < chunks.length; i++) chunks[i] = new Chunk(i % across * CHUNK, i / across * CHUNK);
    }
    // --8<-- [end:terrain]

    // --8<-- [start:update]
    /**
     * After every step: a square whose ground has changed is drawn anew, one whose roads or border have
     * changed is mixed anew. The same terrain serves every game on its map, so a new game is a change, too.
     */
    void update(Game game) {
        boolean fresh = game != this.game;
        this.game = game;
        boolean groundChanged = fresh || game.landVersion() != landVersion;
        boolean maskChanged = fresh || game.roads().version() != roadsVersion || game.land().version() != borderVersion;
        if (groundChanged) {
            landVersion = game.landVersion();
            byte[] now = grounds(game);
            groundChanged = !Arrays.equals(now, ground);
            ground = now;
        }
        if (maskChanged) {
            roadsVersion = game.roads().version();
            borderVersion = game.land().version();
            mask = mask(game);
        }
        for (Chunk chunk : chunks) {
            if (groundChanged) chunk.wantedGround = around(ground, chunk, AROUND);
            if (maskChanged) chunk.wantedMask = around(mask, chunk, 1);
            boolean idle = chunk.work == null || chunk.work.isDone();
            if (idle && (!Arrays.equals(chunk.drawn, chunk.wantedGround) || !Arrays.equals(chunk.mixed, chunk.wantedMask))) {
                schedule(chunk);
            }
        }
    }
    // --8<-- [end:update]

    private byte[] grounds(Game game) {
        byte[] tiles = new byte[level.width() * level.height()];
        for (int row = 0; row < level.height(); row++) {
            for (int col = 0; col < level.width(); col++) tiles[row * level.width() + col] = (byte) game.ground(col, row).ordinal();
        }
        return tiles;
    }

    // --8<-- [start:mask]
    /** Which way the roads leave each tile, where the flags stand, and which tiles are inside the border. */
    private byte[] mask(Game game) {
        int width = level.width();
        byte[] bits = new byte[width * level.height()];
        for (RoadNetwork.Road road : game.roads().roads()) {
            List<Cell> cells = road.cells();
            for (int i = 1; i < cells.size(); i++) {
                Cell a = cells.get(i - 1);
                Cell b = cells.get(i);
                bits[a.row() * width + a.col()] |= (byte) direction(b.col() - a.col(), b.row() - a.row());
                bits[b.row() * width + b.col()] |= (byte) direction(a.col() - b.col(), a.row() - b.row());
            }
        }
        for (int flag : game.roads().flags()) {
            Cell cell = game.roads().cell(flag);
            bits[cell.row() * width + cell.col()] |= FLAG;
        }
        for (int row = 0; row < level.height(); row++) {
            for (int col = 0; col < width; col++) {
                if (game.land().inside(col, row)) bits[row * width + col] |= INSIDE;
            }
        }
        return bits;
    }
    // --8<-- [end:mask]

    private static int direction(int dc, int dr) {
        if (dr < 0) return UP;
        if (dc > 0) return RIGHT;
        if (dr > 0) return DOWN;
        return LEFT;
    }

    /** The tiles of a square and {@code n} around it, cut out of a map-sized array; off the map, mountains and outside. */
    private byte[] around(byte[] tiles, Chunk chunk, int n) {
        int side = CHUNK + 2 * n;
        byte[] cut = new byte[side * side];
        int i = 0;
        for (int row = chunk.row - n; row < chunk.row + CHUNK + n; row++) {
            for (int col = chunk.col - n; col < chunk.col + CHUNK + n; col++) {
                int c = Math.clamp(col, 0, level.width() - 1);
                int r = Math.clamp(row, 0, level.height() - 1);
                cut[i++] = tiles[r * level.width() + c];
            }
        }
        return cut;
    }

    private void schedule(Chunk chunk) {
        byte[] grounds = chunk.wantedGround;
        byte[] bits = chunk.wantedMask;
        chunk.work = WORKERS.submit(() -> {
            if (chunk.base == null || !Arrays.equals(chunk.drawn, grounds)) {
                draw(chunk, grounds);
                chunk.drawn = grounds;
            }
            BufferedImage[] images = mix(chunk, bits);
            chunk.mixed = bits;
            chunk.ready.set(images);
        });
    }

    /** How much snow lies, 0 to 1: it falls at the end of autumn, lies all winter, and melts early in spring. */
    static double snow(Game game) {
        int season = game.seasons().season();
        double progress = game.seasons().progress();
        return switch (season) {
            case 3 -> 1;
            case 2 -> Art.smooth(0.8, 1, progress);
            case 0 -> 1 - Art.smooth(0, 0.15, progress);
            default -> 0;
        };
    }

    // --8<-- [start:render]
    /**
     * The squares the camera sees: as pictures when they are ready, else in the plain colours of their tiles;
     * the winter picture over the summer one as far as the snow has come.
     */
    void render(Graphics2D g, Camera camera, double alpha) {
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        double snow = game == null ? 0 : snow(game);
        Composite before = g.getComposite();
        for (Chunk chunk : chunks) {
            show(chunk);
            double x = chunk.col * Field.TILE;
            double y = chunk.row * Field.TILE;
            if (!camera.sees(x, y, SIZE, SIZE, alpha)) continue;
            if (chunk.summer == null) {
                plain(g, chunk, snow > 0.5);
                continue;
            }
            if (snow < 1) chunk.summer.drawSeamless(g, x, y);
            if (snow > 0) {
                if (snow < 1) g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) snow));
                chunk.winter.drawSeamless(g, x, y);
                g.setComposite(before);
            }
        }
    }
    // --8<-- [end:render]

    private static void show(Chunk chunk) {
        BufferedImage[] images = chunk.ready.getAndSet(null);
        if (images == null) return;
        double w = images[0].getWidth() / (double) PIXELS;
        double h = images[0].getHeight() / (double) PIXELS;
        chunk.summer = Sprite.of(images[0], w, h);
        chunk.winter = Sprite.of(images[1], w, h);
    }

    private void plain(Graphics2D g, Chunk chunk, boolean winter) {
        for (int row = chunk.row; row < Math.min(level.height(), chunk.row + CHUNK); row++) {
            for (int col = chunk.col; col < Math.min(level.width(), chunk.col + CHUNK); col++) {
                Ground at = game == null ? level.ground(col, row) : game.ground(col, row);
                g.setColor(winter && at != Ground.WATER ? plainWinter : plain[at.ordinal()]);
                rectangle.setRect(col * Field.TILE, row * Field.TILE, Field.TILE, Field.TILE);
                g.fill(rectangle);
            }
        }
    }

    /** Whether every square shows its picture as the land is now: for the tests. */
    boolean settled() {
        for (Chunk chunk : chunks) {
            if (chunk.summer == null || chunk.ready.get() != null || !Arrays.equals(chunk.drawn, chunk.wantedGround)
                    || !Arrays.equals(chunk.mixed, chunk.wantedMask)) {
                return false;
            }
        }
        return true;
    }

    /** Waits until every square's picture is ready, and shows them: for screenshots and tests, which draw one frame. */
    void settle() {
        for (int round = 0; round < 3; round++) {
            for (Chunk chunk : chunks) {
                try {
                    if (chunk.work == null && chunk.wantedGround != null) schedule(chunk);
                    if (chunk.work != null) chunk.work.get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (ExecutionException e) {
                    throw new IllegalStateException(e.getCause());
                }
                show(chunk);
                if (chunk.wantedGround != null && (!Arrays.equals(chunk.drawn, chunk.wantedGround)
                        || !Arrays.equals(chunk.mixed, chunk.wantedMask))) {
                    schedule(chunk);
                }
            }
        }
    }

    int across() {
        return across;
    }

    // ---- the fields of the map, in world units ----------------------------------------------------

    /** The tiles around a square as numbers of 0 or 1: whether each is of {@code kind}. */
    private static float[] tiles(byte[] grounds, Ground kind) {
        float[] tiles = new float[grounds.length];
        for (int i = 0; i < grounds.length; i++) tiles[i] = grounds[i] == kind.ordinal() ? 1 : 0;
        return tiles;
    }

    /**
     * A field of the tiles around a square, smooth between their middles: 0.5 halfway from a tile that has it
     * to one that has not; {@code x}, {@code y} in units from the square's top left corner.
     */
    private static double field(float[] tiles, int n, double x, double y) {
        int side = CHUNK + 2 * n;
        double fx = x / Field.TILE - 0.5 + n;
        double fy = y / Field.TILE - 0.5 + n;
        int c = (int) Math.floor(fx);
        int r = (int) Math.floor(fy);
        double tx = fx - c;
        double ty = fy - r;
        tx = tx * tx * (3 - 2 * tx);
        ty = ty * ty * (3 - 2 * ty);
        int c0 = Math.clamp(c, 0, side - 1);
        int c1 = Math.clamp(c + 1, 0, side - 1);
        int r0 = Math.clamp(r, 0, side - 1);
        int r1 = Math.clamp(r + 1, 0, side - 1);
        double a = tiles[r0 * side + c0];
        double b = tiles[r0 * side + c1];
        double d = tiles[r1 * side + c0];
        double e = tiles[r1 * side + c1];
        double top = a + (b - a) * tx;
        return top + (d + (e - d) * tx - top) * ty;
    }

    /** The edges of rock, water and mountains frayed by noise, so that they do not follow the tiles. */
    private static double warpX(double wx, double wy) {
        return (Art.fbm(wx / 22.0, wy / 22.0, 51) - 0.5) * 16;
    }

    private static double warpY(double wx, double wy) {
        return (Art.fbm(wx / 22.0, wy / 22.0, 53) - 0.5) * 16;
    }

    // --8<-- [start:draw]
    /** A square's ground without roads: its colours, lit, and how much snow each pixel takes. The slow part. */
    private void draw(Chunk chunk, byte[] grounds) {
        float[] rock = tiles(grounds, Ground.ROCK);
        float[] water = tiles(grounds, Ground.WATER);
        float[] mountain = tiles(grounds, Ground.MOUNTAIN);
        float[] forest = tiles(grounds, Ground.FOREST);
        int w = SIDE + 2 * BORDER;
        double[] height = new double[w * w];
        double x0 = chunk.col * Field.TILE - BORDER / (double) PIXELS;
        double y0 = chunk.row * Field.TILE - BORDER / (double) PIXELS;
        double ox = chunk.col * Field.TILE;
        double oy = chunk.row * Field.TILE;
        for (int y = 0; y < w; y++) {
            for (int x = 0; x < w; x++) {
                double wx = x0 + x / (double) PIXELS;
                double wy = y0 + y / (double) PIXELS;
                double sx = wx - ox + warpX(wx, wy);
                double sy = wy - oy + warpY(wx, wy);
                double r = Art.smooth(-0.06, 0.08, field(rock, AROUND, sx, sy) - 0.5);
                double m = Art.smooth(-0.1, 0.25, field(mountain, AROUND, sx, sy) - 0.5);
                double hill = Art.fbm(wx / 50.0, wy / 50.0, 3) * 3;
                double stone = r * (6 + Art.fbm(wx / 9.0, wy / 9.0, 7) * 5 + (Art.fbm(wx / 3.0, wy / 3.0, 8) - 0.5) * 1.5);
                double peak = m * (14 + Art.fbm(wx / 26.0, wy / 26.0, 21) * 22
                        + Math.abs(Art.noise(wx / 14.0, wy / 14.0, 23) - 0.5) * 10);
                height[y * w + x] = hill + Math.max(stone, peak);
            }
        }
        // --8<-- [end:draw]
        BufferedImage image = new BufferedImage(SIDE, SIDE, BufferedImage.TYPE_INT_RGB);
        int[] base = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        byte[] light = new byte[SIDE * SIDE];
        byte[] cover = new byte[SIDE * SIDE];
        boolean[] open = new boolean[SIDE * SIDE];
        Random rnd = new Random(chunk.col * 7919L + chunk.row * 104729L + level.number());
        double lx = -0.55;
        double ly = -0.65;
        double lz = 0.52;
        for (int y = 0; y < SIDE; y++) {
            for (int x = 0; x < SIDE; x++) {
                int hx = x + BORDER;
                int hy = y + BORDER;
                int i = hy * w + hx;
                double wx = x0 + hx / (double) PIXELS;
                double wy = y0 + hy / (double) PIXELS;
                double sx = wx - ox + warpX(wx, wy);
                double sy = wy - oy + warpY(wx, wy);
                double dx = (height[i + 1] - height[i - 1]) * PIXELS / 2;
                double dy = (height[i + w] - height[i - w]) * PIXELS / 2;
                double lit = (-dx * lx - dy * ly + lz) / Math.sqrt(dx * dx + dy * dy + 1) / 0.85;
                // The shadow the mountains cast to the bottom right.
                double shadow = 1;
                for (int d = 2; d < 22; d += 2) {
                    if (height[(hy - (int) (d * 1.15)) * w + hx - d] - d * 0.55 > height[i] + 0.6) {
                        shadow = 0.66;
                        break;
                    }
                }
                double r = Art.smooth(-0.06, 0.08, field(rock, AROUND, sx, sy) - 0.5);
                double m = Art.smooth(-0.1, 0.25, field(mountain, AROUND, sx, sy) - 0.5);
                double n = Art.fbm(wx / 70.0, wy / 70.0, 1);
                double fine = Art.fbm(wx / 6.0, wy / 6.0, 9);
                int c = Art.mix(0x4E7E2C, 0x7EA640, n * 1.4 - 0.2);
                c = Art.mix(c, 0x34601E, (fine - 0.5) * 0.9);
                // Darker, mossy ground where the forest is.
                c = Art.mix(c, 0x2E5A1C, Art.smooth(0.3, 0.7, field(forest, AROUND, sx, sy)) * 0.55);
                c = Art.mix(c, rnd.nextBoolean() ? 0x2A4C18 : 0x92BA58, rnd.nextDouble() * 0.16);
                double snowy = 0.92 - Art.smooth(0.3, 0.7, field(forest, AROUND, sx, sy)) * 0.15;
                if (r > 0) {
                    int stone = Art.mix(0x7C786C, 0xA8A292, Art.fbm(wx / 7.0, wy / 7.0, 12));
                    if (Math.abs(Art.noise(wx / 8.0, wy / 8.0, 15) - 0.5) < 0.02) stone = Art.mix(stone, 0x4A4640, 0.6);
                    stone = Art.mix(stone, rnd.nextBoolean() ? 0x5A564E : 0xC4BEAE, rnd.nextDouble() * 0.16);
                    c = Art.mix(c, stone, r);
                    snowy = snowy * (1 - r) + r * (0.45 + 0.3 * Art.fbm(wx / 5.0, wy / 5.0, 16));
                }
                if (m > 0) {
                    int crag = Art.mix(0x5E564C, 0x8A8274, Art.fbm(wx / 9.0, wy / 9.0, 24));
                    crag = Art.mix(crag, 0x3E3830, Art.smooth(0.55, 0.7, Art.fbm(wx / 30.0, wy / 30.0, 25)) * 0.5);
                    crag = Art.mix(crag, rnd.nextBoolean() ? 0x3A342C : 0xA89E8E, rnd.nextDouble() * 0.14);
                    // Snow on the peaks, in summer too.
                    double peak = Art.smooth(26, 34, height[i] + (Art.fbm(wx / 10.0, wy / 10.0, 26) - 0.5) * 8);
                    crag = Art.mix(crag, 0xEEF2F6, peak);
                    c = Art.mix(c, crag, m);
                    snowy = snowy * (1 - m) + m * 0.9;
                }
                double steep = Math.hypot(dx, dy);
                snowy *= 1 - Art.smooth(1.2, 3.5, steep) * 0.6;
                double wf = field(water, AROUND, sx, sy);
                if (wf > 0.36) {
                    int bank = Art.mix(0x9C8A5E, 0xC2AE7C, Art.fbm(wx / 5.0, wy / 5.0, 17));
                    c = Art.mix(c, bank, Art.smooth(0.36, 0.46, wf));
                    if (wf > 0.48) {
                        double depth = Art.smooth(0.5, 0.95, wf);
                        int deep = Art.mix(0x5E9AA8, 0x23546E, depth);
                        double ripple = Math.sin((wy * 0.9 + Art.fbm(wx / 8.0, wy / 8.0, 19) * 14) * 1.3);
                        deep = Art.mix(deep, 0x9CD0D8, Art.smooth(0.75, 1, ripple) * 0.35 * depth);
                        c = Art.mix(bank, deep, Art.smooth(0.48, 0.53, wf));
                        lit = 1;
                        shadow = Math.max(shadow, 0.85);
                        // Ice along the banks in winter, open water in the middle.
                        snowy = 0.75 * (1 - Art.smooth(0.55, 0.7, wf)) + 0.12;
                    } else {
                        snowy = Math.min(snowy, 0.7);
                    }
                }
                int o = y * SIDE + x;
                double l = (0.55 + 0.55 * Math.max(0, lit)) * shadow;
                base[o] = Art.shade(c, l);
                light[o] = (byte) Math.clamp(Math.round(l * 127), 0, 255);
                cover[o] = (byte) Math.clamp(Math.round(snowy * 255), 0, 255);
                open[o] = r < 0.05 && m < 0.05 && wf < 0.4;
            }
        }
        meadow(image, open, rnd);
        chunk.light = light;
        chunk.cover = cover;
        chunk.base = base;
    }

    /** Grass blades and flowers on the open meadow. */
    private static void meadow(BufferedImage image, boolean[] open, Random rnd) {
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Color[] blades = {new Color(0x2C5220), new Color(0x86B04E)};
        for (int i = 0; i < 1500; i++) {
            int x = rnd.nextInt(SIDE);
            int y = rnd.nextInt(SIDE);
            if (!open[y * SIDE + x]) continue;
            g.setColor(blades[rnd.nextInt(2)]);
            g.drawLine(x, y, x + rnd.nextInt(3) - 1, y - 2 - rnd.nextInt(3));
        }
        Color[] flowers = {new Color(0xF2ECD8), new Color(0xE8C84A), new Color(0xC8A0E0), new Color(0xE87A6A)};
        for (int i = 0; i < 30; i++) {
            int x = rnd.nextInt(SIDE - 3);
            int y = rnd.nextInt(SIDE - 3);
            if (!open[y * SIDE + x]) continue;
            g.setColor(flowers[rnd.nextInt(flowers.length)]);
            g.fillOval(x, y, 3, 3);
        }
        g.dispose();
    }

    // --8<-- [start:mix]
    /**
     * The square's two pictures, summer and winter, with the roads and the border in {@code bits}: the
     * roads as trodden dirt, slush in winter, and the land outside the border dimmed.
     */
    private BufferedImage[] mix(Chunk chunk, byte[] bits) {
        int[] base = chunk.base;
        byte[] light = chunk.light;
        byte[] cover = chunk.cover;
        int width = Math.min(SIDE, (level.width() - chunk.col) * Field.TILE * PIXELS);
        int height = Math.min(SIDE, (level.height() - chunk.row) * Field.TILE * PIXELS);
        BufferedImage summer = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        BufferedImage winter = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        int[] outSummer = ((DataBufferInt) summer.getRaster().getDataBuffer()).getData();
        int[] outWinter = ((DataBufferInt) winter.getRaster().getDataBuffer()).getData();
        float[] inside = new float[bits.length];
        for (int i = 0; i < bits.length; i++) inside[i] = (bits[i] & INSIDE) != 0 ? 1 : 0;
        double ox = chunk.col * Field.TILE;
        double oy = chunk.row * Field.TILE;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int i = y * SIDE + x;
                double lx = x / (double) PIXELS;
                double ly = y / (double) PIXELS;
                double l = (light[i] & 255) / 127.0;
                int c = base[i];
                int snow = Art.shade(Art.mix(0xF4F8FC, 0xC8D8EC, 0.25), Math.min(1.08, l * 0.95 + 0.1));
                int w = Art.mix(c, snow, (cover[i] & 255) / 255.0);
                double road = road(bits, lx, ly);
                if (road > 0.15) {
                    double wx = ox + lx;
                    double wy = oy + ly;
                    int dirt = Art.mix(0x8C6A42, 0xB08B58, Art.fbm(wx / 10.0, wy / 10.0, 13));
                    double grit = Art.hash(chunk.col * SIDE + x, chunk.row * SIDE + y, 27);
                    dirt = Art.mix(dirt, 0x5E4428, grit * 0.25);
                    if (grit > 0.97 && road > 0.6) dirt = 0x9C9282;
                    else if (grit < 0.03 && road > 0.6) dirt = 0x4E4234;
                    double part = (road - 0.15) / 0.4;
                    c = Art.mix(Art.mix(c, 0x5A4A2A, 0.4), Art.shade(dirt, l), part);
                    w = Art.mix(Art.mix(w, 0x6A6458, 0.3), Art.shade(Art.mix(dirt, 0xD8D8D4, 0.45), l), part);
                }
                double dim = Art.smooth(0.6, 0.35, field(inside, 1, lx + warpX(ox + lx, oy + ly) * 0.3,
                        ly + warpY(ox + lx, oy + ly) * 0.3)) * 0.5;
                if (dim > 0) {
                    c = Art.mix(c, OUTSIDE_COLOUR, dim);
                    w = Art.mix(w, OUTSIDE_COLOUR, dim);
                }
                outSummer[y * width + x] = c;
                outWinter[y * width + x] = w;
            }
        }
        return new BufferedImage[]{summer, winter};
    }
    // --8<-- [end:mix]

    /**
     * How much road is at {@code x}, {@code y} units from the square's corner, 0 to 1: near the middle line
     * of a road from tile to tile, or near a flag.
     */
    private static double road(byte[] bits, double x, double y) {
        int side = CHUNK + 2;
        int col = (int) Math.floor(x / Field.TILE);
        int row = (int) Math.floor(y / Field.TILE);
        double best = Double.MAX_VALUE;
        for (int r = row - 1; r <= row + 1; r++) {
            for (int c = col - 1; c <= col + 1; c++) {
                int b = bits[(r + 1) * side + c + 1];
                if ((b & (UP | RIGHT | DOWN | LEFT | FLAG)) == 0) continue;
                double cx = (c + 0.5) * Field.TILE;
                double cy = (r + 0.5) * Field.TILE;
                double half = Field.TILE / 2.0;
                if ((b & FLAG) != 0) best = Math.min(best, Math.hypot(x - cx, y - cy) - 1.5);
                if ((b & UP) != 0) best = Math.min(best, segment(x, y, cx, cy, cx, cy - half));
                if ((b & DOWN) != 0) best = Math.min(best, segment(x, y, cx, cy, cx, cy + half));
                if ((b & LEFT) != 0) best = Math.min(best, segment(x, y, cx, cy, cx - half, cy));
                if ((b & RIGHT) != 0) best = Math.min(best, segment(x, y, cx, cy, cx + half, cy));
            }
        }
        if (best == Double.MAX_VALUE) return 0;
        double wobble = (Art.noise(x / 5.0, y / 5.0, 31) - 0.5) * 1.2;
        return Art.smooth(4.6, 1.2, best + wobble);
    }

    private static double segment(double px, double py, double ax, double ay, double bx, double by) {
        double dx = bx - ax;
        double dy = by - ay;
        double t = Math.clamp(((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy), 0, 1);
        return Math.hypot(px - ax - t * dx, py - ay - t * dy);
    }
}
