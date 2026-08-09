package com.josebtan.snakeplugin.gui;

import com.josebtan.snakeplugin.arena.Arena;
import com.josebtan.snakeplugin.skin.SkinGroup;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.List;

/**
 * Identifica el menu de GRUPOS de skins (elegir de que grupo de bloques sera la
 * serpiente). Guarda la arena y los grupos mostrados, en el orden exacto en que se
 * colocaron en el inventario (para poder traducir un clic a un grupo).
 */
public class SkinGroupMenuHolder implements InventoryHolder {

    private final Arena arena;
    private final List<SkinGroup> groups;
    private Inventory inventory;

    public SkinGroupMenuHolder(Arena arena, List<SkinGroup> groups) {
        this.arena = arena;
        this.groups = groups;
    }

    public Arena getArena() {
        return arena;
    }

    /** Grupo en la posicion 'slot' del inventario, o null si ese slot no tiene ninguno. */
    public SkinGroup getGroupAt(int slot) {
        if (slot < 0 || slot >= groups.size()) {
            return null;
        }
        return groups.get(slot);
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
