package eu.amadeos.games.colony.domain;

/** For the tests outside the rules: what only the rules may do, such as reaching the goal at once. */
public final class Cheat {
    private Cheat() {
    }

    /** The settlement has its settlers and the goods of the goal; the next step wins the mission. */
    public static void win(Game game) {
        game.settlers = game.mission().goal();
        game.mission().goods().forEach((good, count) -> game.home().stock.add(good, count));
    }

    /** The mission's last year runs out at the next step. */
    public static void lose(Game game) {
        double year = 0;
        for (double length : game.mission().seasons()) year += length;
        game.seasons.restore(game.mission().years() * year);
    }
}
