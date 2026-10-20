package eu.amadeos.games.colony.ui.game;

import eu.amadeos.gameengine.audio.Sound;
import eu.amadeos.gameengine.audio.Sound.Wave;
import eu.amadeos.gameengine.audio.Tune;

/**
 * The music of Colony, written in MML: an easy tune in G major over a bass that walks, looped while a
 * mission runs, and short tunes for a mission won and lost.
 */
final class Music {
    /** The meadow: a tune of the open land, in three-four time. */
    final Sound meadow = Sound.mix(
            Tune.voice(Wave.SQUARE, 0.035, "t108 o4 l8 d4 g a b4 | a4 g e d4 | e4 g a b4 | a2 r4"
                    + " | b4 >d< b a4 | g4 e g a4 | b4 a g e4 | g2 r4"
                    + " | d4 g a b4 | >c4< b a g4 | e4 g a b4 | a2 r4"
                    + " | b4 >d< b a4 | g4 a b >c4< | b4 a f+ a4 | g2 r4"),
            Tune.voice(Wave.TRIANGLE, 0.16, "t108 o2 l4 g >d< g | c >c< c | e b e | d a d"
                    + " | g >d< g | c >c< c | d a d | g d g"
                    + " | g >d< g | a >e< a | e b e | d a d"
                    + " | g >d< g | c >c< c | d a d | g g g"));

    /** A mission won. */
    final Sound won = Sound.mix(
            Tune.voice(Wave.SQUARE, 0.06, "t132 o4 l8 g b >d g4 d g b4 g2"),
            Tune.voice(Wave.TRIANGLE, 0.2, "t132 o2 l8 g >d g4 < g4 >d4 g2"));

    /** A mission lost. */
    final Sound lost = Sound.mix(
            Tune.voice(Wave.SQUARE, 0.05, "t72 o4 l4 d c < b a g2"),
            Tune.voice(Wave.TRIANGLE, 0.2, "t72 o2 l2 g d g1"));
}
