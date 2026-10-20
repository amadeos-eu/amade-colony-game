package eu.amadeos.games.colony.domain;

/** A good on its way: at a flag, or in a carrier's hands, to a building or a storehouse. */
public final class Ware {
    private final int id;
    private final Good good;
    /** The building it goes to; -1 while nothing takes it. */
    int to;
    /** The flag it waits at; -1 while it is carried. */
    int flag;

    Ware(int id, Good good, int to, int flag) {
        this.id = id;
        this.good = good;
        this.to = to;
        this.flag = flag;
    }

    public int id() {
        return id;
    }

    public Good good() {
        return good;
    }

    public int to() {
        return to;
    }

    public int flag() {
        return flag;
    }
}
