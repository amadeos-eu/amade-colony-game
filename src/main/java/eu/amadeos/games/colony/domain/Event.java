package eu.amadeos.games.colony.domain;

/** What happened in a step, for the sounds, the pictures and the messages; the rules have dealt with it. */
// --8<-- [start:event]
public sealed interface Event {
    /** A site was laid out, with its flag. */
    record Founded(Building building) implements Event {
    }

    /** The builder finished a house. */
    record Built(Building building) implements Event {
    }

    /** A worker or builder found nobody to send, or no tools. */
    record NoTools(Building building) implements Event {
    }

    /** A road was laid; {@code cells} long. */
    record Paved(int cells) implements Event {
    }

    /** A worker made a good, or brought it home. */
    record Made(Building building, Good good) implements Event {
    }

    record Felled(int col, int row) implements Event {
    }

    /** Food and cloth brought a new settler into a residence. */
    record Settled(Building building) implements Event {
    }

    /** A new season began: 0 spring, 1 summer, 2 autumn, 3 winter. */
    record Season(int season, int year) implements Event {
    }

    record Burning(Building building) implements Event {
    }

    /** The worker of a well put the fire out. */
    record Doused(Building building) implements Event {
    }

    record Burned(Building building) implements Event {
    }

    record Wolf(double x, double y) implements Event {
    }

    /** The dog drove a wolf off; or a watchman's horn did. */
    record Barked(Building farm, boolean horn) implements Event {
    }

    record SheepTaken(Building farm) implements Event {
    }

    record Lamb(Building farm) implements Event {
    }

    record Demolished(Building building) implements Event {
    }

    /** The mission is over: won, or the years ran out. */
    record Over(boolean won) implements Event {
    }
}
// --8<-- [end:event]
