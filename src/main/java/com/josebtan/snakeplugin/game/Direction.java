package com.josebtan.snakeplugin.game;

/**
 * Las 4 direcciones posibles de movimiento en la rejilla del juego (plano X/Z, Y fija).
 * El snake, al igual que en el juego clasico, se mueve en linea recta y solo puede
 * girar 90 grados: nunca puede invertir su direccion de golpe (ej: de NORTH a SOUTH).
 */
public enum Direction {
    NORTH(0, -1),
    SOUTH(0, 1),
    EAST(1, 0),
    WEST(-1, 0);

    private final int dx;
    private final int dz;

    Direction(int dx, int dz) {
        this.dx = dx;
        this.dz = dz;
    }

    public int getDx() {
        return dx;
    }

    public int getDz() {
        return dz;
    }

    public Direction getOpposite() {
        return switch (this) {
            case NORTH -> SOUTH;
            case SOUTH -> NORTH;
            case EAST -> WEST;
            case WEST -> EAST;
        };
    }
}
