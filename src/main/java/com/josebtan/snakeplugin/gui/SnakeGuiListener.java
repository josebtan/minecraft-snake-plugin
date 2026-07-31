package com.josebtan.snakeplugin.gui;

import com.josebtan.snakeplugin.arena.Arena;
import com.josebtan.snakeplugin.arena.ArenaManager;
import com.josebtan.snakeplugin.game.GameManager;
import com.josebtan.snakeplugin.game.SnakeColor;
import com.josebtan.snakeplugin.game.SnakeGame;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.Set;

/**
 * Reacciona a los clics dentro de todos los menus de SnakeGuiMenus. Los inventarios
 * estan llenos de items "reales" (lana, cabezas de jugador, barreras, papel), asi que
 * es imprescindible cancelar SIEMPRE cualquier clic o arrastre mientras uno de estos
 * menus este abierto — si no, el jugador podria sacarse literalmente los items del
 * menu al inventario normal.
 */
public class SnakeGuiListener implements Listener {

    private final GameManager gameManager;
    private final ArenaManager arenaManager;

    public SnakeGuiListener(GameManager gameManager, ArenaManager arenaManager) {
        this.gameManager = gameManager;
        this.arenaManager = arenaManager;
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (isOurMenu(event.getView().getTopInventory())) {
            event.setCancelled(true);
        }
    }

    /**
     * Copia el nombre que el jugador va escribiendo en el yunque falso al slot de
     * resultado, sin aplicar ninguna receta real (no hace falta segundo item, no gasta
     * experiencia). Ver ArenaNameAnvilHolder para el detalle completo del truco.
     */
    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        if (!(event.getInventory().getHolder() instanceof ArenaNameAnvilHolder)) {
            return;
        }
        ItemStack typed = event.getInventory().getItem(0);
        event.setResult(typed != null ? typed.clone() : null);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        InventoryHolder holder = top.getHolder();

        if (!isOurHolder(holder)) {
            return;
        }

        if (!(event.getWhoClicked() instanceof Player player)) {
            event.setCancelled(true);
            return;
        }

        // El yunque falso necesita un trato especial: el slot de resultado (2) SI se
        // procesa (para leer el nombre escrito), el resto se cancela sin mas (para que
        // no se pueda sacar el papel base ni meter materiales de verdad).
        if (holder instanceof ArenaNameAnvilHolder) {
            event.setCancelled(true);
            if (event.getClickedInventory() != null && event.getClickedInventory().equals(top)
                    && event.getSlot() == ArenaNameAnvilHolder.RESULT_SLOT) {
                handleArenaNameChosen(player, event.getCurrentItem());
            }
            return;
        }

        // Cancelar SIEMPRE en el resto de menus: evita sacar items, tanto haciendo clic
        // dentro del propio menu como con shift-clic desde el inventario normal.
        event.setCancelled(true);

        // Solo nos interesan los clics dentro del menu en si (no en el inventario del jugador).
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(top)) {
            return;
        }

        if (holder instanceof ModeMenuHolder modeHolder) {
            handleModeClick(player, modeHolder, event.getSlot());
        } else if (holder instanceof ColorMenuHolder colorHolder) {
            handleColorClick(player, colorHolder, event.getSlot());
        } else if (holder instanceof ArenaListMenuHolder listHolder) {
            handleArenaListClick(player, listHolder, event.getSlot());
        } else if (holder instanceof ArenaCreateMenuHolder) {
            handleArenaCreateClick(player, event.getSlot());
        }
    }

    private void handleModeClick(Player player, ModeMenuHolder holder, int slot) {
        boolean multiplayer;
        if (slot == 3) {
            multiplayer = false;
        } else if (slot == 5) {
            multiplayer = true;
        } else {
            return;
        }
        player.closeInventory();
        SnakeGuiMenus.openColorMenu(player, holder.getArena(), multiplayer, gameManager);
    }

    private void handleColorClick(Player player, ColorMenuHolder holder, int slot) {
        SnakeColor[] colors = SnakeColor.values();
        if (slot < 0 || slot >= colors.length) {
            return;
        }
        SnakeColor chosen = colors[slot];
        Arena arena = holder.getArena();

        if (gameManager.hasGame(player)) {
            player.closeInventory();
            player.sendMessage(Component.text("Ya tienes una serpiente activa. Usa /snake leave primero.",
                    NamedTextColor.RED));
            return;
        }

        if (holder.isMultiplayer()) {
            Set<SnakeColor> taken = gameManager.getColorsInUse(arena);
            if (taken.contains(chosen)) {
                player.sendMessage(Component.text(
                        "Ese color ya lo esta usando otro jugador en esta arena. Elige otro.",
                        NamedTextColor.RED));
                // Se refresca el menu para que el jugador vea el estado actual actualizado.
                SnakeGuiMenus.openColorMenu(player, arena, true, gameManager);
                return;
            }
        }

        player.closeInventory();
        SnakeGame game = gameManager.startGame(player, arena, chosen);
        if (game == null) {
            player.sendMessage(Component.text(
                    "No se encontro un sitio libre para aparecer en '" + arena.getName()
                            + "' (esta muy ocupada/decorada). Prueba de nuevo o usa otra arena.",
                    NamedTextColor.RED));
            return;
        }
        player.sendMessage(Component.text(
                "Serpiente " + chosen.getDisplayName().toLowerCase() + " creada en '" + arena.getName()
                        + "'. Usa W/A/S/D para dirigirla.", NamedTextColor.GREEN));
    }

    private void handleArenaListClick(Player player, ArenaListMenuHolder holder, int slot) {
        Arena arena = holder.getArenaAt(slot);
        if (arena == null) {
            return;
        }

        if (holder.getMode() == ArenaListMenuHolder.Mode.DELETE) {
            boolean removed = arenaManager.deleteArena(arena.getName());
            player.sendMessage(removed
                    ? Component.text("Arena '" + arena.getName() + "' eliminada.", NamedTextColor.GREEN)
                    : Component.text("Esa arena ya no existia.", NamedTextColor.RED));
            player.closeInventory();
            // Se reabre la lista (si queda alguna arena) para poder seguir eliminando sin repetir el comando.
            SnakeGuiMenus.openArenaListMenu(player, arenaManager, ArenaListMenuHolder.Mode.DELETE);
            return;
        }

        player.closeInventory();
        SnakeGuiMenus.openModeMenu(player, arena);
    }

    private void handleArenaCreateClick(Player player, int slot) {
        switch (slot) {
            case 0 -> { // Ver arenas
                player.closeInventory();
                SnakeGuiMenus.openArenaListMenu(player, arenaManager, ArenaListMenuHolder.Mode.JOIN);
            }
            case 2 -> { // Marcar esquina 1
                arenaManager.setPos1(player, player.getLocation());
                player.sendMessage(Component.text("Esquina 1 marcada en tu posicion.", NamedTextColor.GREEN));
                SnakeGuiMenus.openArenaCreateMenu(player, arenaManager); // refresca el panel
            }
            case 4 -> { // Marcar esquina 2
                arenaManager.setPos2(player, player.getLocation());
                player.sendMessage(Component.text("Esquina 2 marcada en tu posicion.", NamedTextColor.GREEN));
                SnakeGuiMenus.openArenaCreateMenu(player, arenaManager);
            }
            case 6 -> { // Crear arena
                if (arenaManager.getPos1(player) == null || arenaManager.getPos2(player) == null) {
                    player.sendMessage(Component.text(
                            "Marca las dos esquinas antes de crear la arena.", NamedTextColor.RED));
                    return;
                }
                player.closeInventory();
                String defaultName = "Arena" + (arenaManager.getArenas().size() + 1);
                SnakeGuiMenus.openArenaNameAnvil(player, defaultName);
            }
            case 8 -> { // Eliminar arena
                player.closeInventory();
                SnakeGuiMenus.openArenaListMenu(player, arenaManager, ArenaListMenuHolder.Mode.DELETE);
            }
            default -> {
                // Slots decorativos/vacios: no hacer nada.
            }
        }
    }

    /** El jugador hizo clic en el resultado del yunque falso: ese texto es el nombre elegido. */
    private void handleArenaNameChosen(Player player, ItemStack result) {
        if (result == null || result.getItemMeta() == null || !result.getItemMeta().hasDisplayName()) {
            player.sendMessage(Component.text("Escribe un nombre valido para la arena.", NamedTextColor.RED));
            return;
        }

        String name = PlainTextComponentSerializer.plainText()
                .serialize(result.getItemMeta().displayName())
                .trim();
        if (name.isEmpty()) {
            player.sendMessage(Component.text("Escribe un nombre valido para la arena.", NamedTextColor.RED));
            return;
        }

        player.closeInventory();
        Arena arena = arenaManager.createFromPending(player, name);
        if (arena == null) {
            player.sendMessage(Component.text(
                    "Falta marcar pos1 y pos2 (en el mismo mundo) antes de crear la arena.", NamedTextColor.RED));
            return;
        }
        player.sendMessage(Component.text("Arena '" + name + "' creada y guardada.", NamedTextColor.GREEN));
    }

    private boolean isOurMenu(Inventory inventory) {
        return isOurHolder(inventory.getHolder());
    }

    private boolean isOurHolder(InventoryHolder holder) {
        return holder instanceof ModeMenuHolder
                || holder instanceof ColorMenuHolder
                || holder instanceof ArenaListMenuHolder
                || holder instanceof ArenaCreateMenuHolder
                || holder instanceof ArenaNameAnvilHolder;
    }
}
