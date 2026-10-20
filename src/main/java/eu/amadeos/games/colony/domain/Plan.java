package eu.amadeos.games.colony.domain;

import java.util.List;

/**
 * How the computer builds a settlement, from {@code plan.txt}: the buildings in the order it builds them,
 * for the title screen and the tests. A line may come back to a building; the overseer builds another.
 */
public record Plan(List<BuildingType> build, int roads) {
    public Plan {
        build = List.copyOf(build);
    }
}
