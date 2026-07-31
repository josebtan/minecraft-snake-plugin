package com.josebtan.snakeplugin.gui;

import com.josebtan.snakeplugin.arena.Arena;
import com.josebtan.snakeplugin.game.GameManager;
import com.josebtan.snakeplugin.game.SnakeColor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Set;

/**
 * Construye y abre los dos menus del flujo de "unirse a una arena":
 *
 *  1. Menu de MODO: "Un jugador" o "Multijugador".
 *  2. Menu de COLOR: uno de los 8 colores de lana. En modo multijugador, los colores
 *     que ya este usando otro jugador activo EN ESA MISMA ARENA aparecen bloqueados
 *     (bloque de barrera, no seleccionables). En modo "un jugador" no se bloquea nada.
 *
 * SnakeGuiListener es quien reacciona a los clics dentro de estos inventarios.
 */
public final class SnakeGuiMenus {

    private SnakeGuiMenus() {
    }

    /** Abre el menu de modo (paso 1) para la arena a la que el jugador quiere unirse. */
    public static void openModeMenu(Player player, Arena arena) {
        ModeMenuHolder holder = new ModeMenuHolder(arena);
        Inventory inventory = Bukkit.createInventory(holder, 9,
                Component.text("Snake: " + arena.getName(), NamedTextColor.DARK_GREEN));
        holder.setInventory(inventory);

        inventory.setItem(3, buildItem(Material.PLAYER_HEAD, NamedTextColor.AQUA, "Un jugador",
                List.of("Juega tu solo.", "Todos los colores estan disponibles.")));
        inventory.setItem(5, buildItem(Material.PLAYER_HEAD, NamedTextColor.GOLD, "Multijugador",
                List.of("Juega junto a otros.", "Los colores ya elegidos", "no estaran disponibles.")));

        player.openInventory(inventory);
    }

    /** Abre el menu de color (paso 2), ya sabiendo el modo elegido en el paso 1. */
    public static void openColorMenu(Player player, Arena arena, boolean multiplayer, GameManager gameManager) {
        ColorMenuHolder holder = new ColorMenuHolder(arena, multiplayer);
        Inventory inventory = Bukkit.createInventory(holder, 9,
                Component.text("Elige el color de tu serpiente", NamedTextColor.DARK_GREEN));
        holder.setInventory(inventory);

        Set<SnakeColor> taken = multiplayer ? gameManager.getColorsInUse(arena) : Set.of();

        SnakeColor[] colors = SnakeColor.values();
        for (int i = 0; i < colors.length; i++) {
            SnakeColor color = colors[i];
            boolean available = !taken.contains(color);
            if (available) {
                inventory.setItem(i, buildItem(color.getWoolMaterial(), NamedTextColor.WHITE,
                        color.getDisplayName(), List.of("Haz clic para empezar a jugar.")));
            } else {
                inventory.setItem(i, buildItem(Material.BARRIER, NamedTextColor.RED,
                        color.getDisplayName() + " (ocupado)",
                        List.of("Otro jugador ya esta usando", "este color en esta arena.")));
            }
        }

        player.openInventory(inventory);
    }

    private static ItemStack buildItem(Material material, NamedTextColor nameColor, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name, nameColor));
            meta.lore(lore.stream().map(line -> Component.text(line, NamedTextColor.GRAY)).toList());
            item.setItemMeta(meta);
        }
        return item;
    }
}
