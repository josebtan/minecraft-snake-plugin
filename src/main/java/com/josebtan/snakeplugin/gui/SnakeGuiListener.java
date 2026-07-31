package com.josebtan.snakeplugin.gui;

import com.josebtan.snakeplugin.arena.Arena;
import com.josebtan.snakeplugin.game.GameManager;
import com.josebtan.snakeplugin.game.SnakeColor;
import com.josebtan.snakeplugin.game.SnakeGame;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.Set;

/**
 * Reacciona a los clics dentro de los menus de SnakeGuiMenus. Ambos inventarios estan
 * llenos de items "reales" (lana, cabezas de jugador, barreras), asi que es imprescindible
 * cancelar SIEMPRE cualquier clic o arrastre mientras uno de estos menus este abierto —
 * si no, el jugador podria sacarse literalmente los items del menu al inventario normal.
 */
public class SnakeGuiListener implements Listener {

    private final GameManager gameManager;

    public SnakeGuiListener(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (isOurMenu(event.getView().getTopInventory())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        InventoryHolder holder = top.getHolder();

        if (!(holder instanceof ModeMenuHolder) && !(holder instanceof ColorMenuHolder)) {
            return;
        }

        // Cancelar SIEMPRE: evita sacar items del menu, tanto haciendo clic dentro del
        // propio menu como con shift-clic desde el inventario normal del jugador.
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        // Solo nos interesan los clics dentro del menu en si (no en el inventario del jugador).
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(top)) {
            return;
        }

        if (holder instanceof ModeMenuHolder modeHolder) {
            handleModeClick(player, modeHolder, event.getSlot());
        } else if (holder instanceof ColorMenuHolder colorHolder) {
            handleColorClick(player, colorHolder, event.getSlot());
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

    private boolean isOurMenu(Inventory inventory) {
        InventoryHolder holder = inventory.getHolder();
        return holder instanceof ModeMenuHolder || holder instanceof ColorMenuHolder;
    }
}
