package com.josebtan.snakeplugin.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Identifica el inventario del panel de creacion/administracion de arenas (marcar
 * esquinas, crear, ver, eliminar). No necesita guardar nada mas: todo el estado
 * relevante (pos1/pos2 pendientes, arenas existentes) se lee en vivo desde
 * ArenaManager cada vez que se abre o se refresca el menu.
 */
public class ArenaCreateMenuHolder implements InventoryHolder {

    private Inventory inventory;

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
