package com.josebtan.snakeplugin.gui;

import com.josebtan.snakeplugin.arena.Arena;
import com.josebtan.snakeplugin.skin.Skin;
import com.josebtan.snakeplugin.skin.SkinGroup;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.List;

/**
 * Identifica el menu de SKINS de un grupo (elegir el bloque concreto de la serpiente).
 * Guarda la arena, el grupo y las skins mostradas, en el orden exacto en que se
 * colocaron en el inventario (para poder traducir un clic a una skin).
 */
public class SkinMenuHolder implements InventoryHolder {

    private final Arena arena;
    private final SkinGroup group;
    private final List<Skin> skins;
    private Inventory inventory;

    public SkinMenuHolder(Arena arena, SkinGroup group, List<Skin> skins) {
        this.arena = arena;
        this.group = group;
        this.skins = skins;
    }

    public Arena getArena() {
        return arena;
    }

    public SkinGroup getGroup() {
        return group;
    }

    /** Skin en la posicion 'slot' del inventario, o null si ese slot no tiene ninguna. */
    public Skin getSkinAt(int slot) {
        if (slot < 0 || slot >= skins.size()) {
            return null;
        }
        return skins.get(slot);
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
