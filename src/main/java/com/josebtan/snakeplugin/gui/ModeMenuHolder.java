package com.josebtan.snakeplugin.gui;

import com.josebtan.snakeplugin.arena.Arena;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Identifica el inventario del primer menu (elegir modo: un jugador / multijugador).
 * Usar un InventoryHolder propio es la forma estandar y fiable de reconocer "esto es
 * uno de nuestros menus" en el listener, sin tener que comparar titulos de texto.
 */
public class ModeMenuHolder implements InventoryHolder {

    private final Arena arena;
    private Inventory inventory;

    public ModeMenuHolder(Arena arena) {
        this.arena = arena;
    }

    public Arena getArena() {
        return arena;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
