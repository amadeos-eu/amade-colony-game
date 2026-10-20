package eu.amadeos.games.colony.ui.game;

import eu.amadeos.gameengine.sprite.Rotations;
import eu.amadeos.gameengine.sprite.Sprite;
import eu.amadeos.games.colony.domain.BuildingType;
import eu.amadeos.games.colony.domain.Field;
import eu.amadeos.games.colony.domain.Good;
import eu.amadeos.games.colony.domain.Rules;

import java.awt.Graphics2D;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * The pictures of {@link Art}, {@link Houses} and {@link Folk} as sprites, each drawn once at its size on the
 * screen and then only copied. The buildings, the goods and the land's pictures are drawn when the game
 * starts; a person's picture the first time someone in those clothes takes that step, for there are many
 * looks, poses and steps, and most are never seen.
 */
// --8<-- [start:pictures]
final class Pictures {
    /** A tree is drawn at four sizes as it grows, in three shapes of each, for summer and winter, inside and outside. */
    static final double[] TREE_SIZES = {0.38, 0.55, 0.75, 1.0};
    static final int VARIANTS = 3;
    /** A field at its four stages: just sown, sprouting, green, ripe. */
    static final int STAGES = 4;
    /** The windmill's sails in this many turns of a quarter circle, and the flames' flicker. */
    static final int SAILS = 32;
    static final int FLAMES = 6;
    static final double[] PUFFS = {2, 3, 4, 5.5, 7};
    /** A person's picture, wider with a tool; their feet in the middle, {@link #FEET_Y} from the top. */
    static final double PERSON_WIDTH = 16;
    static final double WORKER_WIDTH = 46;
    static final double PERSON_HEIGHT = 30;
    static final double FEET_Y = 24;

    /** One pose of a person: their look, the load held up, from behind, the step, mirrored, the tool and its swing. */
    record Pose(Folk.Look look, boolean armsUp, boolean back, int step, boolean left, Folk.Tool tool, int frame) {
    }

    private final Map<String, Sprite> buildings = new HashMap<>();
    private final Map<String, Sprite> yards = new HashMap<>();
    private final Map<String, Sprite> shadows = new HashMap<>();
    private final Map<String, Sprite> sites = new HashMap<>();
    private final Map<String, Sprite> icons = new HashMap<>();
    private final Map<Good, Sprite> goods = new EnumMap<>(Good.class);
    private final Map<Good, Sprite> bigGoods = new EnumMap<>(Good.class);
    private final Map<Pose, Sprite> people = new HashMap<>();
    /** By conifer, size, winter, outside and shape. */
    private final Sprite[][][][][] trees = new Sprite[2][TREE_SIZES.length][2][2][VARIANTS];
    private final Sprite[] treeShadows = new Sprite[TREE_SIZES.length];
    /** By stage, winter, outside and shape. */
    private final Sprite[][][][] fields = new Sprite[STAGES][2][2][2];
    /** By stone left, 1 to {@link Field#STONE}, winter and outside. */
    private final Sprite[][][] boulders = new Sprite[Field.STONE + 1][2][2];
    final Rotations sails = Rotations.of(SAILS, 44, (g, w, h) -> {
        g.translate(w / 2, h / 2);
        Houses.sails(g);
    });
    final Rotations sailsShadow = Rotations.of(SAILS, 44, (g, w, h) -> {
        g.translate(w / 2, h / 2);
        Houses.sailsShadow(g);
    });
    final Sprite flag = Sprite.drawn(12, 14, (g, w, h) -> {
        Art.smooth(g);
        Art.flag(g, 3, 12);
    });
    final Sprite post = Sprite.drawn(6, 8, (g, w, h) -> {
        Art.smooth(g);
        Art.borderPost(g, 2, 7);
    });
    final Sprite stump = Sprite.drawn(8, 8, (g, w, h) -> {
        Art.smooth(g);
        Art.stump(g, 4, 4);
    });
    final Sprite[] ashes = new Sprite[3];
    /** The sheep facing right and left, in summer and winter; the dog; the wolf at two strides. */
    final Sprite[][] sheep = new Sprite[2][2];
    final Sprite[] dog = new Sprite[2];
    final Sprite[][] wolf = new Sprite[2][2];
    final Sprite[] flames = new Sprite[FLAMES];
    final Sprite[] puffs = new Sprite[PUFFS.length];
    final Sprite[] darkPuffs = new Sprite[PUFFS.length];
    final Sprite[] seasons = new Sprite[4];
    final Sprite[] markers = new Sprite[2];
    // --8<-- [end:pictures]

    Pictures(Rules rules) {
        for (BuildingType type : rules.buildings()) {
            String id = type.id();
            int w = type.width();
            int h = type.height();
            double width = w * Field.TILE + 2 * Houses.SIDE;
            double height = h * Field.TILE + Houses.RISE + 6;
            buildings.put(id, Sprite.drawn(width, height, (g, sw, sh) -> {
                Art.smooth(g);
                g.translate(Houses.SIDE, Houses.RISE);
                Houses.building(g, id, w, h);
            }));
            if (id.equals("sheepfarm")) {
                yards.put(id, Sprite.drawn(width, height, (g, sw, sh) -> {
                    Art.smooth(g);
                    g.translate(Houses.SIDE, Houses.RISE);
                    Houses.yard(g, id, w, h);
                }));
            }
            shadows.put(id, Sprite.drawn(width + 30, height + 16, (g, sw, sh) -> {
                Art.smooth(g);
                g.translate(Houses.SIDE, Houses.RISE);
                Houses.shadow(g, id, w, h);
            }));
            sites.put(id, Sprite.drawn(w * Field.TILE + 6, h * Field.TILE + 6, (g, sw, sh) -> {
                Art.smooth(g);
                Houses.site(g, w, h);
            }));
            icons.put(id, Sprite.drawn(53, 31, (g, sw, sh) -> icon(g, type)));
        }
        for (Good good : Good.values()) {
            goods.put(good, Sprite.drawn(9, 7, (g, w, h) -> {
                Art.smooth(g);
                Art.good(g, good, w / 2, h / 2, 0);
            }));
            bigGoods.put(good, Sprite.drawn(13, 11, (g, w, h) -> {
                Art.smooth(g);
                g.translate(w / 2, h / 2);
                g.scale(1.45, 1.45);
                Art.good(g, good, 0, 0, good == Good.LOG || good == Good.PLANK ? -0.35 : 0);
            }));
        }
        for (int size = 0; size < TREE_SIZES.length; size++) {
            double s = TREE_SIZES[size];
            treeShadows[size] = Sprite.drawn(32, 32, (g, w, h) -> {
                Art.smooth(g);
                Art.treeShadow(g, 12, 12, s);
            });
            for (int conifer = 0; conifer < 2; conifer++) {
                for (int winter = 0; winter < 2; winter++) {
                    for (int outside = 0; outside < 2; outside++) {
                        for (int v = 0; v < VARIANTS; v++) {
                            boolean c = conifer == 1;
                            boolean w = winter == 1;
                            boolean o = outside == 1;
                            long seed = v * 977L + size * 31L + conifer * 7L;
                            trees[conifer][size][winter][outside][v] = Sprite.drawn(28, 28, (g, sw, sh) -> {
                                Art.smooth(g);
                                Art.tree(g, 14, 14, c, s, w, o, seed);
                            });
                        }
                    }
                }
            }
        }
        for (int stage = 0; stage < STAGES; stage++) {
            for (int winter = 0; winter < 2; winter++) {
                for (int outside = 0; outside < 2; outside++) {
                    for (int v = 0; v < 2; v++) {
                        int st = stage;
                        boolean w = winter == 1;
                        boolean o = outside == 1;
                        long seed = v * 131L + stage;
                        fields[stage][winter][outside][v] = Sprite.drawn(16, 16, (g, sw, sh) -> {
                            Art.smooth(g);
                            Art.field(g, st, w, o, seed);
                        });
                    }
                }
            }
        }
        for (int stone = 1; stone <= Field.STONE; stone++) {
            int n = stone;
            for (int winter = 0; winter < 2; winter++) {
                for (int outside = 0; outside < 2; outside++) {
                    boolean w = winter == 1;
                    boolean o = outside == 1;
                    boulders[stone][winter][outside] = Sprite.drawn(24, 24, (g, sw, sh) -> {
                        Art.smooth(g);
                        boulders(g, n, w, o);
                    });
                }
            }
        }
        for (int i = 0; i < ashes.length; i++) {
            long seed = i * 17L;
            ashes[i] = Sprite.drawn(16, 16, (g, w, h) -> {
                Art.smooth(g);
                Art.ashes(g, seed);
            });
        }
        for (int left = 0; left < 2; left++) {
            boolean mirrored = left == 1;
            for (int winter = 0; winter < 2; winter++) {
                boolean w = winter == 1;
                sheep[left][winter] = Sprite.drawn(12, 10, (g, sw, sh) -> {
                    Art.smooth(g);
                    turn(g, sw, mirrored);
                    g.translate(sw / 2, sh / 2);
                    Art.sheep(g, 1.2, w);
                });
            }
            dog[left] = Sprite.drawn(12, 8, (g, sw, sh) -> {
                Art.smooth(g);
                turn(g, sw, mirrored);
                g.translate(sw / 2 + 0.5, sh / 2);
                Art.dog(g);
            });
            for (int stride = 0; stride < 2; stride++) {
                int st = stride;
                wolf[left][stride] = Sprite.drawn(26, 14, (g, sw, sh) -> {
                    Art.smooth(g);
                    turn(g, sw, mirrored);
                    g.translate(sw / 2 + 0.5, sh / 2);
                    g.scale(1.75, 1.75);
                    Art.wolf(g, st);
                });
            }
        }
        for (int i = 0; i < FLAMES; i++) {
            double phase = i * Math.PI * 2 / FLAMES;
            flames[i] = Sprite.drawn(10, 14, (g, w, h) -> {
                Art.smooth(g);
                g.translate(w / 2, h);
                Art.flame(g, 12, phase);
            });
        }
        for (int i = 0; i < PUFFS.length; i++) {
            double r = PUFFS[i];
            puffs[i] = Sprite.drawn(2 * r, 2 * r, (g, w, h) -> {
                Art.smooth(g);
                g.translate(w / 2, h / 2);
                Art.puff(g, r, 150, false);
            });
            darkPuffs[i] = Sprite.drawn(2 * r, 2 * r, (g, w, h) -> {
                Art.smooth(g);
                g.translate(w / 2, h / 2);
                Art.puff(g, r, 190, true);
            });
        }
        for (int i = 0; i < seasons.length; i++) {
            int season = i;
            seasons[i] = Sprite.drawn(11, 11, (g, w, h) -> {
                Art.smooth(g);
                g.translate(w / 2, h / 2);
                Art.season(g, season);
            });
        }
        for (int i = 0; i < 2; i++) {
            boolean ok = i == 1;
            markers[i] = Sprite.drawn(8, 8, (g, w, h) -> {
                Art.smooth(g);
                Art.marker(g, 4, 7, ok);
            });
        }
    }

    /** Mirrors what follows, for a picture facing left. */
    private static void turn(Graphics2D g, double width, boolean mirrored) {
        if (!mirrored) return;
        g.translate(width, 0);
        g.scale(-1, 1);
    }

    /** The boulders of a rock tile with {@code stone} stone left: fewer and smaller as the quarry breaks it. */
    private static void boulders(Graphics2D g, int stone, boolean winter, boolean outside) {
        double[][] places = {{9, 10, 5.5}, {15, 13, 4.5}, {10, 16, 4}, {16, 8, 3.5}, {6, 14, 3}, {13, 18, 3}};
        for (int i = Math.min(stone, places.length) - 1; i >= 0; i--) {
            double[] p = places[i];
            Art.boulder(g, p[0] + 4, p[1] + 2, p[2], i * 53L + 11, winter, outside);
        }
    }

    /** A building in the panel, made smaller to fit its card. */
    private static void icon(Graphics2D g, BuildingType type) {
        Art.smooth(g);
        double w = type.width() * Field.TILE + 8;
        double h = type.height() * Field.TILE + Houses.RISE * 0.7;
        double k = Math.min(1.3, Math.min(50 / w, 29 / h));
        g.translate((53 - type.width() * Field.TILE * k) / 2, 31 - type.height() * Field.TILE * k - 1);
        g.scale(k, k);
        Houses.yard(g, type.id(), type.width(), type.height());
        Houses.building(g, type.id(), type.width(), type.height());
        if (type.id().equals("windmill")) {
            g.translate(Houses.HUB_X, Houses.HUB_Y);
            g.rotate(0.4);
            Houses.sails(g);
        }
    }

    /** A building, its top left at the top left of its tiles less {@link Houses#SIDE} and {@link Houses#RISE}. */
    Sprite building(BuildingType type) {
        return buildings.get(type.id());
    }

    /** The ground of a building drawn under everything else, or {@code null}. */
    Sprite yard(BuildingType type) {
        return yards.get(type.id());
    }

    Sprite shadow(BuildingType type) {
        return shadows.get(type.id());
    }

    /** A site, at the top left of its tiles. */
    Sprite site(BuildingType type) {
        return sites.get(type.id());
    }

    /** What the panel shows for a building, 53 × 31. */
    Sprite icon(BuildingType type) {
        return icons.get(type.id());
    }

    /** A good, 9 × 7 around its middle; or the larger one of the goods bar, 13 × 11. */
    Sprite good(Good good) {
        return goods.get(good);
    }

    Sprite bigGood(Good good) {
        return bigGoods.get(good);
    }

    /** A tree grown to {@code age}, 0 to 1, in its 28 × 28 picture, the tree at its middle. */
    Sprite tree(boolean conifer, double age, boolean winter, boolean outside, int variant) {
        return trees[conifer ? 1 : 0][size(age)][winter ? 1 : 0][outside ? 1 : 0][variant % VARIANTS];
    }

    /** A tree's shadow, the tree at 12, 12 of its picture. */
    Sprite treeShadow(double age) {
        return treeShadows[size(age)];
    }

    static int size(double age) {
        if (age >= 1) return 3;
        if (age >= 0.6) return 2;
        return age >= 0.25 ? 1 : 0;
    }

    /** A tile of field grown to {@code age}, 0 to 1. */
    Sprite field(double age, boolean winter, boolean outside, int variant) {
        int stage = age >= 1 ? 3 : age >= 0.45 ? 2 : age >= 0.15 ? 1 : 0;
        return fields[stage][winter ? 1 : 0][outside ? 1 : 0][variant & 1];
    }

    /** The boulders of a rock tile, 24 × 24, the tile at 4, 2. */
    Sprite boulders(int stone, boolean winter, boolean outside) {
        return boulders[Math.clamp(stone, 1, Field.STONE)][winter ? 1 : 0][outside ? 1 : 0];
    }

    // --8<-- [start:person]
    /** A person in a pose, drawn the first time it is asked for; their feet in the middle, {@link #FEET_Y} down. */
    Sprite person(Pose pose) {
        double width = pose.tool() == Folk.Tool.NONE ? PERSON_WIDTH : WORKER_WIDTH;
        return people.computeIfAbsent(pose, p -> Sprite.drawn(width, PERSON_HEIGHT, (g, w, h) -> {
            Art.smooth(g);
            turn(g, w, p.left());
            g.translate(w / 2, FEET_Y);
            Folk.person(g, p.look(), p.armsUp(), p.back(), p.step());
            if (p.tool() != Folk.Tool.NONE) Folk.tool(g, p.tool(), p.frame());
        }));
    }
    // --8<-- [end:person]

    int poses() {
        return people.size();
    }
}
