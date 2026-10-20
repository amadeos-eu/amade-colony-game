package eu.amadeos.games.colony.domain;

import eu.amadeos.gameengine.tile.Cell;

import java.util.List;

/** A wolf from a den, on its way to a pasture, or running back. */
public final class Wolf {
    final int farm;
    final List<Cell> path;
    double along;
    boolean fleeing;

    Wolf(int farm, List<Cell> path, double along) {
        this.farm = farm;
        this.path = List.copyOf(path);
        this.along = along;
    }

    public boolean fleeing() {
        return fleeing;
    }

    public double x() {
        return at(true);
    }

    public double y() {
        return at(false);
    }

    private double at(boolean x) {
        int i = (int) Math.floor(Math.max(0, along));
        int last = path.size() - 1;
        if (i >= last) return centre(path.get(last), x);
        double t = Math.max(0, along) - i;
        return centre(path.get(i), x) * (1 - t) + centre(path.get(i + 1), x) * t;
    }

    private static double centre(Cell cell, boolean x) {
        return ((x ? cell.col() : cell.row()) + 0.5) * Field.TILE;
    }

    public boolean facingLeft() {
        int i = Math.clamp((long) Math.floor(Math.max(0, along)), 0, path.size() - 2);
        int dx = path.get(i + 1).col() - path.get(i).col();
        return fleeing ? dx > 0 : dx < 0;
    }
}
