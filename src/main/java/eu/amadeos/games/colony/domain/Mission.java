package eu.amadeos.games.colony.domain;

import java.util.Map;

/**
 * A mission of {@code missions.txt}: its map, what the settlement starts with, what it must reach, and
 * what makes it hard. Times in seconds.
 *
 * @param settlers how many settlers live in the storehouse at the start
 * @param goal     how many settlers the settlement must have to win
 * @param goods    goods the storehouses must hold at the same time, to win
 * @param years    the goal must be reached before this many years are over; 0 for no limit
 * @param seasons  the lengths of spring, summer, autumn and winter
 * @param fire     whether houses catch fire
 * @param wolves   whether wolves come from their dens in autumn and winter
 */
public record Mission(int number, String map, Map<Good, Integer> start, int settlers, int goal,
                      Map<Good, Integer> goods, int years, double[] seasons, boolean fire, boolean wolves) {
    public Mission {
        start = Map.copyOf(start);
        goods = Map.copyOf(goods);
        seasons = seasons.clone();
    }
}
