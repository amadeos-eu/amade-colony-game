package eu.amadeos.games.colony.ui.game;

import eu.amadeos.gameengine.audio.Sound;
import eu.amadeos.gameengine.audio.Sound.Wave;

/** The sound effects of Colony, made once at start from tones: no recordings. */
final class SoundBank {
    /** A site is laid out: a peg knocked into the ground. */
    final Sound founded = Sound.mix(Sound.tone(Wave.NOISE, 0, 0, 0.04, 0.08), Sound.tone(Wave.TRIANGLE, 320, 220, 0.08, 0.25));
    /** A house stands: three hammer blows and a rising note. */
    final Sound built = knock(0.12).then(knock(0.11)).then(knock(0.1))
            .then(Sound.tone(Wave.SQUARE, 523, 523, 0.07, 0.04)).then(Sound.tone(Wave.SQUARE, 784, 784, 0.14, 0.04));
    /** A road is laid: a spade in the earth. */
    final Sound paved = Sound.tone(Wave.NOISE, 0, 0, 0.12, 0.06).then(Sound.tone(Wave.NOISE, 0, 0, 0.08, 0.04));
    /** A tree falls: a creak and a thud. */
    final Sound felled = Sound.tone(Wave.SQUARE, 180, 120, 0.12, 0.02)
            .then(Sound.mix(Sound.tone(Wave.NOISE, 0, 0, 0.2, 0.1), Sound.tone(Wave.TRIANGLE, 90, 40, 0.25, 0.3)));
    /** A new settler: a little chime. */
    final Sound settled = Sound.tone(Wave.TRIANGLE, 784, 784, 0.08, 0.2).then(Sound.tone(Wave.TRIANGLE, 988, 988, 0.08, 0.2))
            .then(Sound.tone(Wave.TRIANGLE, 1175, 1175, 0.16, 0.2));
    /** A new season: three soft notes. */
    final Sound season = Sound.tone(Wave.TRIANGLE, 392, 392, 0.12, 0.2).then(Sound.tone(Wave.TRIANGLE, 494, 494, 0.12, 0.2))
            .then(Sound.tone(Wave.TRIANGLE, 587, 587, 0.24, 0.2));
    /** Winter comes: the same notes, falling. */
    final Sound winter = Sound.tone(Wave.TRIANGLE, 587, 587, 0.12, 0.2).then(Sound.tone(Wave.TRIANGLE, 466, 466, 0.12, 0.2))
            .then(Sound.tone(Wave.TRIANGLE, 392, 392, 0.3, 0.2));
    /** Fire: the bell, rung fast. */
    final Sound alarm = bell().then(bell()).then(bell()).then(bell());
    /** The well's water hisses on the fire. */
    final Sound doused = Sound.tone(Wave.NOISE, 0, 0, 0.6, 0.07);
    /** A house falls in, burnt or pulled down: a long rumble. */
    final Sound collapse = Sound.mix(Sound.tone(Wave.NOISE, 0, 0, 0.9, 0.14), Sound.tone(Wave.TRIANGLE, 70, 26, 0.9, 0.35));
    /** A wolf leaves its den: a howl that rises and falls. */
    final Sound howl = Sound.tone(Wave.TRIANGLE, 330, 520, 0.5, 0.2).then(Sound.tone(Wave.TRIANGLE, 520, 300, 0.9, 0.2));
    /** The dog: two short barks. */
    final Sound bark = yap().then(Sound.tone(Wave.NOISE, 0, 0, 0.06, 0)).then(yap());
    /** The watchman's horn. */
    final Sound horn = Sound.mix(Sound.tone(Wave.SQUARE, 147, 147, 0.7, 0.05), Sound.tone(Wave.TRIANGLE, 294, 294, 0.7, 0.15));
    /** A sheep: a bleat, wavering. */
    final Sound bleat = Sound.tone(Wave.SQUARE, 520, 480, 0.1, 0.03).then(Sound.tone(Wave.SQUARE, 500, 460, 0.1, 0.03))
            .then(Sound.tone(Wave.SQUARE, 480, 420, 0.18, 0.03));
    /** A lamb: the same, higher. */
    final Sound lamb = Sound.tone(Wave.SQUARE, 760, 720, 0.08, 0.025).then(Sound.tone(Wave.SQUARE, 740, 680, 0.14, 0.025));
    /** Done as asked. */
    final Sound yes = Sound.tone(Wave.SQUARE, 523, 523, 0.04, 0.04).then(Sound.tone(Wave.SQUARE, 784, 784, 0.06, 0.04));
    /** Not possible: a low buzz. */
    final Sound no = Sound.tone(Wave.SQUARE, 150, 140, 0.15, 0.06);
    /** A button, or a menu choice moves. */
    final Sound tick = Sound.tone(Wave.SQUARE, 880, 880, 0.03, 0.04);

    private static Sound knock(double gap) {
        return Sound.mix(Sound.tone(Wave.NOISE, 0, 0, 0.03, 0.12), Sound.tone(Wave.TRIANGLE, 260, 160, 0.05, 0.3))
                .then(Sound.tone(Wave.NOISE, 0, 0, gap, 0));
    }

    private static Sound bell() {
        return Sound.mix(Sound.tone(Wave.TRIANGLE, 1320, 1300, 0.16, 0.2), Sound.tone(Wave.SQUARE, 660, 650, 0.16, 0.03));
    }

    private static Sound yap() {
        return Sound.mix(Sound.tone(Wave.NOISE, 0, 0, 0.07, 0.08), Sound.tone(Wave.SQUARE, 420, 300, 0.07, 0.05));
    }
}
