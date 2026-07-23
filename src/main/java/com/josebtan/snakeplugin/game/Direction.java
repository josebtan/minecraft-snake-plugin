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

    /**
     * Convierte el "yaw" (angulo horizontal hacia donde mira el jugador) en una de las
     * 4 direcciones de la rejilla. Se usa para que el jugador controle el snake con
     * la mirada, sin necesidad de moverse fisicamente.
     *
     * En Minecraft, yaw 0/360 = Sur, 90 = Oeste, 180 = Norte, 270 = Este (aprox).
     */
    public static Direction fromYaw(float yaw) {
        float normalized = yaw % 360;
        if (normalized < 0) {
            normalized += 360;
        }
        if (normalized >= 45 && normalized < 135) {
            return WEST;
        } else if (normalized >= 135 && normalized < 225) {
            return NORTH;
        } else if (normalized >= 225 && normalized < 315) {
            return EAST;
        } else {
            return SOUTH;
        }
    }
}
