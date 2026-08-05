package com.josebtan.snakeplugin.gui;

import com.josebtan.snakeplugin.arena.Arena;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Identifica el inventario del menu de color (elegir color de lana). El modo de la arena
 * ya NO se elige aqui (se fijo al crearla), asi que este holder solo guarda la arena: el
 * modo se lee en vivo de la propia arena (ver com.josebtan.snakeplugin.game.GameMode) y
 * decide si hay que bloquear los colores ya reservados por otros jugadores (en partida o
 * en la sala de espera) de esa misma arena.
 */
public class ColorMenuHolder implements InventoryHolder {

    private final Arena arena;
    private Inventory inventory;

    public ColorMenuHolder(Arena arena) {
        this.arena = arena;
    }

    public Arena getArena() {
        return arena;
    }

    /** true si la arena es multijugador (los colores reservados se bloquean en el menu). */
    public boolean isMultiplayer() {
        return arena.getMode().isMultiplayer();
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}