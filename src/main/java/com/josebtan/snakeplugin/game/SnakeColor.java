package com.josebtan.snakeplugin.game;

import org.bukkit.Material;

/**
 * Colores disponibles para las serpientes. Cada jugador elige su color en el menu
 * (ver com.josebtan.snakeplugin.gui) — ya no se asigna automaticamente. En modo
 * multijugador, un color ya elegido por otro jugador activo en la misma arena no
 * se puede volver a elegir (ver GameManager#getColorsInUse).
 */
public enum SnakeColor {
    ROJO(Material.RED_WOOL, "Rojo"),
    AZUL(Material.BLUE_WOOL, "Azul"),
    VERDE(Material.LIME_WOOL, "Verde"),
    AMARILLO(Material.YELLOW_WOOL, "Amarillo"),
    NARANJA(Material.ORANGE_WOOL, "Naranja"),
    MORADO(Material.PURPLE_WOOL, "Morado"),
    CIAN(Material.CYAN_WOOL, "Cian"),
    ROSA(Material.PINK_WOOL, "Rosa");

    private final Material woolMaterial;
    private final String displayName;

    SnakeColor(Material woolMaterial, String displayName) {
        this.woolMaterial = woolMaterial;
        this.displayName = displayName;
    }

    public Material getWoolMaterial() {
        return woolMaterial;
    }

    public String getDisplayName() {
        return displayName;
    }
}
