package com.josebtan.snakeplugin.gui;

import com.josebtan.snakeplugin.arena.Arena;
import com.josebtan.snakeplugin.arena.ArenaManager;
import com.josebtan.snakeplugin.game.GameManager;
import com.josebtan.snakeplugin.game.GameMode;
import com.josebtan.snakeplugin.skin.Skin;
import com.josebtan.snakeplugin.skin.SkinGroup;
import com.josebtan.snakeplugin.skin.SkinManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Construye y abre todos los menus del plugin:
 *
 *  1. Menu de GRUPOS de SKINS: los grupos de bloques que puede usar el jugador como
 *     "piel" de la serpiente (solo los grupos en los que tenga al menos una skin, ver
 *     com.josebtan.snakeplugin.skin.SkinManager). El modo de la arena ya se decidio al
 *     crearla (ver com.josebtan.snakeplugin.game.GameMode).
 *  2. Menu de SKINS de un grupo: un item por bloque. En una arena multijugador, las
 *     skins ya reservadas por otros jugadores (en partida o en la sala de espera)
 *     aparecen bloqueadas (bloque de barrera). En una arena "un jugador" no se
 *     bloquea nada.
 *  3. Menu de LISTA DE ARENAS: para elegir una arena (a la que unirse, o para
 *     eliminarla, segun el modo). Muestra el modo y, en multijugador, cuantos jugadores
 *     admite.
 *  4. Panel de CREACION/ADMINISTRACION de arenas: marcar esquinas, crear (pide nombre,
 *     modo y, si toca, maximo de jugadores por chat), ver y eliminar.
 *
 * SnakeGuiListener es quien reacciona a los clics dentro de estos inventarios.
 */
public final class SnakeGuiMenus {

    private SnakeGuiMenus() {
    }

    /**
     * Abre el menu de GRUPOS de skins que el jugador puede usar. Si solo hay un grupo
     * disponible, salta directo a sus skins (ahorra un clic en el caso mas comun).
     */
    public static void openSkinGroupMenu(Player player, Arena arena, GameManager gameManager,
                                         SkinManager skinManager) {
        List<SkinGroup> accessible = skinManager.getAccessibleGroups(player);
        if (accessible.isEmpty()) {
            player.sendMessage(Component.text(
                    "No tienes ninguna skin disponible. Habla con un administrador para que te de permisos.",
                    NamedTextColor.RED));
            return;
        }
        if (accessible.size() == 1) {
            openSkinMenu(player, arena, accessible.get(0), gameManager, skinManager);
            return;
        }

        SkinGroupMenuHolder holder = new SkinGroupMenuHolder(arena, accessible);
        int size = Math.max(9, ((accessible.size() - 1) / 9 + 1) * 9);
        Inventory inventory = Bukkit.createInventory(holder, size,
                Component.text("Elige un grupo de skins", NamedTextColor.DARK_GREEN));
        holder.setInventory(inventory);

        for (int i = 0; i < accessible.size(); i++) {
            SkinGroup group = accessible.get(i);
            inventory.setItem(i, buildItem(group.getIcon(), NamedTextColor.WHITE, group.getDisplayName(),
                    List.of(skinManager.getAccessibleSkins(player, group).size() + " skins disponibles",
                            "Haz clic para verlas.")));
        }

        player.openInventory(inventory);
    }

    /**
     * Abre el menu de SKINS de un grupo: un item por bloque que el jugador puede usar.
     * En una arena multijugador, las skins ya reservadas se bloquean (bloque de barrera).
     */
    public static void openSkinMenu(Player player, Arena arena, SkinGroup group,
                                    GameManager gameManager, SkinManager skinManager) {
        List<Skin> accessible = skinManager.getAccessibleSkins(player, group);
        if (accessible.isEmpty()) {
            player.sendMessage(Component.text("No tienes skins disponibles en ese grupo.",
                    NamedTextColor.RED));
            return;
        }
        boolean multiplayer = arena.getMode().isMultiplayer();
        Set<Skin> taken = multiplayer ? gameManager.getSkinsInUse(arena) : Set.of();

        SkinMenuHolder holder = new SkinMenuHolder(arena, group, accessible);
        int size = Math.max(9, ((accessible.size() - 1) / 9 + 1) * 9);
        Inventory inventory = Bukkit.createInventory(holder, size,
                Component.text("Elige la skin de tu serpiente", NamedTextColor.DARK_GREEN));
        holder.setInventory(inventory);

        for (int i = 0; i < accessible.size(); i++) {
            Skin skin = accessible.get(i);
            boolean available = !taken.contains(skin);
            if (available) {
                inventory.setItem(i, buildItem(skin.getMaterial(), NamedTextColor.WHITE,
                        skin.getDisplayName(),
                        List.of("Grupo: " + group.getDisplayName(), "Haz clic para empezar a jugar.")));
            } else {
                inventory.setItem(i, buildItem(Material.BARRIER, NamedTextColor.RED,
                        skin.getDisplayName() + " (ocupado)",
                        List.of("Otro jugador ya esta usando", "esta skin en esta arena.")));
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
            GameMode arenaMode = arena.getMode();
            String modeText = arenaMode.isMultiplayer()
                    ? arenaMode.getDisplayName() + " (hasta " + arena.getMaxPlayers() + " jugadores)"
                    : arenaMode.getDisplayName();
            List<String> lore = List.of(
                    "Modo: " + modeText,
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
     * (pide el nombre por chat), ver la lista, o eliminar una.
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
