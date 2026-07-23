package com.josebtan.snakeplugin.game;

import org.bukkit.Material;

/**
 * Colores disponibles para las serpientes. Cada jugador recibe un color distinto
 * (bloque de lana) para poder distinguir su serpiente de las de otros jugadores
 * en partidas multijugador. En etapas futuras esto tambien se podra usar para
 * tenir ovejas del mismo color si se decide usar mobs en vez de bloques.
 */
public enum SnakeColor {
    ROJO(Material.RED_WOOL),
    AZUL(Material.BLUE_WOOL),
    VERDE(Material.LIME_WOOL),
    AMARILLO(Material.YELLOW_WOOL),
    NARANJA(Material.ORANGE_WOOL),
    MORADO(Material.PURPLE_WOOL),
    CIAN(Material.CYAN_WOOL),
    ROSA(Material.PINK_WOOL);

    private final Material woolMaterial;

    SnakeColor(Material woolMaterial) {
        this.woolMaterial = woolMaterial;
    }

    public Material getWoolMaterial() {
        return woolMaterial;
    }

    private static final SnakeColor[] VALUES = values();

    /**
     * Asigna un color de forma ciclica segun el numero de jugadores ya en partida,
     * para que dos serpientes activas nunca compartan color mientras haya colores libres.
     */
    public static SnakeColor byIndex(int index) {
        return VALUES[Math.floorMod(index, VALUES.length)];
    }
}
