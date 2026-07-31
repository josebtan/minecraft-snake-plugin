package com.josebtan.snakeplugin.gui;

import com.josebtan.snakeplugin.arena.Arena;
import com.josebtan.snakeplugin.arena.ArenaManager;
import com.josebtan.snakeplugin.game.GameManager;
import com.josebtan.snakeplugin.game.SnakeColor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Construye y abre todos los menus del plugin:
 *
 *  1. Menu de MODO: "Un jugador" o "Multijugador" (paso 1 de unirse a una arena).
 *  2. Menu de COLOR: uno de los 8 colores de lana (paso 2). En modo multijugador, los
 *     colores que ya este usando otro jugador activo EN ESA MISMA ARENA aparecen
 *     bloqueados (bloque de barrera, no seleccionables). En modo "un jugador" no se
 *     bloquea nada.
 *  3. Menu de LISTA DE ARENAS: para elegir una arena (a la que unirse, o para
 *     eliminarla, segun el modo).
 *  4. Panel de CREACION/ADMINISTRACION de arenas: marcar esquinas, crear (via el
 *     "yunque falso" para escribir el nombre), ver y eliminar.
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

    /**
     * Abre el listado de arenas existentes. En modo JOIN, hacer clic en una abre el
     * menu de modo para unirse a ella. En modo DELETE, hacer clic en una la elimina
     * (de inmediato: no hay paso de confirmacion, ver aviso en el chat al usarlo).
     */
    public static boolean openArenaListMenu(Player player, ArenaManager arenaManager, ArenaListMenuHolder.Mode mode) {
        List<Arena> arenas = new ArrayList<>(arenaManager.getArenas().values());
        if (arenas.isEmpty()) {
            player.sendMessage(Component.text("No hay arenas creadas todavia.", NamedTextColor.RED));
            return false;
        }
        arenas.sort(Comparator.comparing(Arena::getName, String.CASE_INSENSITIVE_ORDER));

        int size = Math.min(54, ((arenas.size() - 1) / 9 + 1) * 9);
        boolean deleteMode = mode == ArenaListMenuHolder.Mode.DELETE;

        ArenaListMenuHolder holder = new ArenaListMenuHolder(arenas, mode);
        Inventory inventory = Bukkit.createInventory(holder, size,
                Component.text(deleteMode ? "Elige una arena para ELIMINAR" : "Elige una arena",
                        deleteMode ? NamedTextColor.RED : NamedTextColor.DARK_GREEN));
        holder.setInventory(inventory);

        for (int i = 0; i < arenas.size(); i++) {
            Arena arena = arenas.get(i);
            int width = arena.getMaxX() - arena.getMinX() + 1;
            int depth = arena.getMaxZ() - arena.getMinZ() + 1;
            List<String> lore = List.of(
                    "Mundo: " + arena.getWorld().getName(),
                    "Tamano: " + width + " x " + depth,
                    deleteMode ? "Haz clic para ELIMINARLA" : "Haz clic para unirte");
            Material icon = deleteMode ? Material.BARRIER : Material.GRASS_BLOCK;
            inventory.setItem(i, buildItem(icon, deleteMode ? NamedTextColor.RED : NamedTextColor.WHITE,
                    arena.getName(), lore));
        }

        player.openInventory(inventory);
        return true;
    }

    /**
     * Abre el panel de creacion/administracion de arenas: marcar esquinas, crear
     * (via yunque falso), ver la lista, o eliminar una.
     */
    public static void openArenaCreateMenu(Player player, ArenaManager arenaManager) {
        ArenaCreateMenuHolder holder = new ArenaCreateMenuHolder();
        Inventory inventory = Bukkit.createInventory(holder, 9,
                Component.text("Crear / administrar arenas", NamedTextColor.DARK_GREEN));
        holder.setInventory(inventory);

        Location pos1 = arenaManager.getPos1(player);
        Location pos2 = arenaManager.getPos2(player);

        inventory.setItem(2, buildItem(pos1 != null ? Material.LIME_WOOL : Material.RED_WOOL,
                pos1 != null ? NamedTextColor.GREEN : NamedTextColor.RED, "Marcar esquina 1",
                pos1 != null
                        ? List.of("Marcada en: " + formatLocation(pos1), "Haz clic para volver a marcarla aqui.")
                        : List.of("Sin marcar.", "Haz clic para marcarla en tu posicion actual.")));

        inventory.setItem(4, buildItem(pos2 != null ? Material.LIME_WOOL : Material.RED_WOOL,
                pos2 != null ? NamedTextColor.GREEN : NamedTextColor.RED, "Marcar esquina 2",
                pos2 != null
                        ? List.of("Marcada en: " + formatLocation(pos2), "Haz clic para volver a marcarla aqui.")
                        : List.of("Sin marcar.", "Haz clic para marcarla en tu posicion actual.")));

        boolean readyToCreate = pos1 != null && pos2 != null;
        inventory.setItem(6, buildItem(readyToCreate ? Material.EMERALD : Material.GRAY_DYE,
                readyToCreate ? NamedTextColor.GREEN : NamedTextColor.GRAY, "Crear arena",
                readyToCreate
                        ? List.of("Te pedira el nombre.")
                        : List.of("Marca antes las dos esquinas.")));

        inventory.setItem(0, buildItem(Material.MAP, NamedTextColor.AQUA, "Ver arenas",
                List.of("Lista las arenas ya creadas.")));
        inventory.setItem(8, buildItem(Material.BARRIER, NamedTextColor.RED, "Eliminar arena",
                List.of("Elige una arena existente", "para eliminarla.")));

        player.openInventory(inventory);
    }

    /**
     * Abre el "yunque falso" para que el jugador escriba el nombre de la nueva arena
     * (ver ArenaNameAnvilHolder para el detalle de como funciona el truco).
     */
    public static void openArenaNameAnvil(Player player, String defaultName) {
        ArenaNameAnvilHolder holder = new ArenaNameAnvilHolder();
        Inventory inventory = Bukkit.createInventory(holder, InventoryType.ANVIL,
                Component.text("Nombre de la arena"));
        holder.setInventory(inventory);

        ItemStack nameItem = new ItemStack(Material.PAPER);
        ItemMeta meta = nameItem.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(defaultName));
            nameItem.setItemMeta(meta);
        }
        inventory.setItem(0, nameItem);

        player.openInventory(inventory);
    }

    private static String formatLocation(Location location) {
        return location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ();
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
