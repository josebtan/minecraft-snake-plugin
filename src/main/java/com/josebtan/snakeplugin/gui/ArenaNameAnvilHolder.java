package com.josebtan.snakeplugin.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Identifica el inventario de tipo YUNQUE (falso: no esta atado a ningun bloque real
 * del mundo) que se usa como truco para dejar al jugador ESCRIBIR el nombre de la
 * arena con el cuadro de texto nativo del yunque, en vez de tener que usar el chat.
 *
 * Como funciona: se abre un inventario de InventoryType.ANVIL con un item (papel) en
 * el slot 0. El cliente de Minecraft ya sabe mostrar el cuadro de "renombrar" para
 * yunques sin que tengamos que hacer nada especial. Cada vez que el jugador escribe
 * algo, el servidor dispara PrepareAnvilEvent (ver SnakeGuiListener): ahi copiamos el
 * nombre tecleado al slot de resultado (slot 2), SIN aplicar ninguna receta real de
 * yunque (no hace falta un segundo item, ni gasta experiencia, ni durabilidad — es
 * pura decoracion visual para conseguir el cuadro de texto). Al hacer clic en ese
 * resultado, leemos el nombre final y creamos la arena (ver SnakeGuiListener).
 */
public class ArenaNameAnvilHolder implements InventoryHolder {

    /** Slot donde aparece el resultado (renombrado) en un yunque: siempre el indice 2. */
    public static final int RESULT_SLOT = 2;

    private Inventory inventory;

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
