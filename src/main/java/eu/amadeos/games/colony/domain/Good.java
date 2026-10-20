package eu.amadeos.games.colony.domain;

import java.util.Locale;

/** The goods of the settlement, in the order of the goods bar. Their names in the data files are in lower case. */
// --8<-- [start:good]
public enum Good {
    LOG, PLANK, STONE, GRAIN, FLOUR, WATER, BREAD, FISH, WOOL, CLOTH, TOOLS;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The good a word of a data file names, or {@code null}. */
    public static Good ofId(String id) {
        for (Good good : values()) {
            if (good.id().equals(id)) return good;
        }
        return null;
    }
}
// --8<-- [end:good]
