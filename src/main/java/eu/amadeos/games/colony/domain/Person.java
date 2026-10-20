package eu.amadeos.games.colony.domain;

import eu.amadeos.gameengine.tile.Cell;

import java.util.List;

/**
 * A settler with a job: a carrier on a road, a builder on a site, a worker in the house or out on the
 * job. Settlers without a job live in the storehouse and are only counted. A person walks along a list of
 * tiles, from {@code along} towards {@code to}, both counted in steps from the first tile.
 */
public final class Person {
    public enum Role { CARRIER, BUILDER, WORKER }

    /** What the person is doing. */
    public enum Task {
        /** On the way from the storehouse to the job. */
        GOING,
        /** A carrier waiting in the middle of the road, or walking there. */
        IDLE,
        /** A carrier walking to a flag to fetch a ware. */
        FETCH,
        /** A carrier with a ware, walking to the other flag. */
        CARRY,
        /** A builder at work on the site. */
        BUILD,
        /** A worker in the house. */
        HOME,
        /** A worker walking out to a tree, a rock, the water, a field. */
        OUT,
        /** A worker at work out there. */
        AWAY,
        /** A worker walking home. */
        BACK
    }

    private final int id;
    private final Role role;
    /** The building of a builder or worker, the road of a carrier. */
    final int job;
    Task task = Task.GOING;
    List<Cell> path = List.of();
    double along;
    double to;
    /** What a worker carries home, or the good of the ware a carrier carries. */
    Good load;
    int ware = -1;
    /** Where a worker works out there, and how long it still takes. */
    Cell spot;
    double timer;

    Person(int id, Role role, int job) {
        this.id = id;
        this.role = role;
        this.job = job;
    }

    public int id() {
        return id;
    }

    public Role role() {
        return role;
    }

    public int job() {
        return job;
    }

    public Task task() {
        return task;
    }

    public Good load() {
        return load;
    }

    /** Whether it can be seen: a worker in the house cannot. */
    public boolean visible() {
        return task != Task.HOME && !path.isEmpty();
    }

    public boolean walking() {
        return Math.abs(to - along) > 1e-9;
    }

    /** Walks {@code steps} towards {@code to}; true when there. */
    boolean walk(double steps) {
        if (to > along) along = Math.min(to, along + steps);
        else along = Math.max(to, along - steps);
        return Math.abs(to - along) < 1e-9;
    }

    /** Sets out along {@code cells} from the first tile to the last. */
    void walkAlong(List<Cell> cells) {
        path = List.copyOf(cells);
        along = 0;
        to = cells.size() - 1;
    }

    /** Where it is, in units: between two tiles of the path, in their middle. */
    public double x() {
        return at(true);
    }

    public double y() {
        return at(false);
    }

    private double at(boolean x) {
        if (path.isEmpty()) return 0;
        int i = (int) Math.floor(along);
        int last = path.size() - 1;
        if (i >= last) return centre(path.get(last), x);
        double t = along - i;
        return centre(path.get(i), x) * (1 - t) + centre(path.get(i + 1), x) * t;
    }

    private static double centre(Cell cell, boolean x) {
        return ((x ? cell.col() : cell.row()) + 0.5) * Field.TILE;
    }

    /** Whether it walks to the left, for the picture. */
    public boolean facingLeft() {
        if (path.size() < 2) return false;
        int i = Math.clamp((long) Math.floor(along), 0, path.size() - 2);
        int dx = path.get(i + 1).col() - path.get(i).col();
        return to < along ? dx > 0 : dx < 0;
    }

    /** Whether it walks away from the viewer, up the map: the picture shows its back. */
    public boolean facingUp() {
        if (path.size() < 2 || !walking()) return false;
        int i = Math.clamp((long) Math.floor(along), 0, path.size() - 2);
        int dy = path.get(i + 1).row() - path.get(i).row();
        return to < along ? dy > 0 : dy < 0;
    }

    public boolean working() {
        return task == Task.AWAY || task == Task.BUILD;
    }
}
