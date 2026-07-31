package com.josebtan.snakeplugin.gui;

import com.josebtan.snakeplugin.arena.Arena;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.List;

/**
 * Identifica el inventario del menu "ver arenas disponibles". Guarda la lista de arenas
 * EN EL ORDEN en que se colocaron en el inventario, para poder saber a que arena
 * corresponde cada slot en el que el jugador haga clic. El mismo menu sirve para dos
 * cosas segun el modo: elegir arena para UNIRSE (JOIN), o elegir arena para ELIMINARLA
 * (DELETE) — el listener decide que hacer segun este campo.
 */
public class ArenaListMenuHolder implements InventoryHolder {

    public enum Mode {
        JOIN,
        DELETE
    }

    private final List<Arena> arenasInOrder;
    private final Mode mode;
    private Inventory inventory;

    public ArenaListMenuHolder(List<Arena> arenasInOrder, Mode mode) {
        this.arenasInOrder = arenasInOrder;
        this.mode = mode;
    }

    public Mode getMode() {
        return mode;
    }

    /** Devuelve la arena correspondiente a ese slot, o null si el slot no tiene ninguna. */
    public Arena getArenaAt(int slot) {
        if (slot < 0 || slot >= arenasInOrder.size()) {
            return null;
        }
        return arenasInOrder.get(slot);
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
