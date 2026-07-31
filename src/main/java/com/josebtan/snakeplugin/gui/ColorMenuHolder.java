package com.josebtan.snakeplugin.gui;

import com.josebtan.snakeplugin.arena.Arena;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Identifica el inventario del segundo menu (elegir color de lana). Guarda tambien si se
 * entro en modo multijugador, porque eso decide si hay que bloquear los colores ya
 * elegidos por otros jugadores activos en la misma arena (ver SnakeGuiListener).
 */
public class ColorMenuHolder implements InventoryHolder {

    private final Arena arena;
    private final boolean multiplayer;
    private Inventory inventory;

    public ColorMenuHolder(Arena arena, boolean multiplayer) {
        this.arena = arena;
        this.multiplayer = multiplayer;
    }

    public Arena getArena() {
        return arena;
    }

    public boolean isMultiplayer() {
        return multiplayer;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
