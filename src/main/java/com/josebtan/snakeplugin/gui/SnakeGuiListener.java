package com.josebtan.snakeplugin.gui;

import com.josebtan.snakeplugin.arena.Arena;
import com.josebtan.snakeplugin.arena.ArenaManager;
import com.josebtan.snakeplugin.game.GameManager;
import com.josebtan.snakeplugin.game.SnakeColor;
import com.josebtan.snakeplugin.game.SnakeGame;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.Plugin;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reacciona a los clics dentro de todos los menus de SnakeGuiMenus. Los inventarios
 * estan llenos de items "reales" (lana, cabezas de jugador, barreras), asi que es
 * imprescindible cancelar SIEMPRE cualquier clic o arrastre mientras uno de estos
 * menus este abierto — si no, el jugador podria sacarse literalmente los items del
 * menu al inventario normal.
 *
 * Tambien gestiona el "modo espera de nombre": al pulsar "Crear arena" en el panel,
 * se cierra el menu y se le pide escribir el nombre por chat. Se probo primero con un
 * "yunque falso" (InventoryType.ANVIL + PrepareAnvilEvent) para no salir de la GUI,
 * pero resulto ser poco fiable en la practica (Bukkit a veces descarta el resultado
 * segun su propia logica interna de reparacion, aunque se fuerce con el evento) — asi
 * que se cambio a este metodo, mucho mas simple y predecible.
 */
public class SnakeGuiListener implements Listener {

    private final Plugin plugin;
    private final GameManager gameManager;
    private final ArenaManager arenaManager;

    /** Jugadores de los que se espera que el proximo mensaje de chat sea el nombre de una arena. */
    private final Set<UUID> awaitingArenaName = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public SnakeGuiListener(Plugin plugin, GameManager gameManager, ArenaManager arenaManager) {
        this.plugin = plugin;
        this.gameManager = gameManager;
        this.arenaManager = arenaManager;
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

        if (!isOurHolder(holder)) {
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
        } else if (holder instanceof ArenaListMenuHolder listHolder) {
            handleArenaListClick(player, listHolder, event.getSlot());
        } else if (holder instanceof ArenaCreateMenuHolder) {
            handleArenaCreateClick(player, event.getSlot());
        }
    }

    /**
     * Captura el nombre escrito por un jugador que acaba de pulsar "Crear arena". Se
     * cancela el evento para que ese mensaje no se difunda como chat normal.
     *
     * NOTA: AsyncPlayerChatEvent se dispara FUERA del hilo principal del servidor —
     * por eso el trabajo real (crear la arena, tocar el mundo/el archivo) se
     * reprograma con Bukkit.getScheduler().runTask(...) antes de tocar cualquier
     * API de Bukkit que no sea segura para llamar desde otro hilo.
     */
    @EventHandler
    public void onAsyncPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!awaitingArenaName.remove(player.getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        String typed = event.getMessage().trim();

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (typed.isEmpty() || typed.equalsIgnoreCase("cancelar")) {
                player.sendMessage(Component.text("Creacion de arena cancelada.", NamedTextColor.YELLOW));
                return;
            }
            Arena arena = arenaManager.createFromPending(player, typed);
            if (arena == null) {
                player.sendMessage(Component.text(
                        "Falta marcar pos1 y pos2 (en el mismo mundo) antes de crear la arena.",
                        NamedTextColor.RED));
                return;
            }
            player.sendMessage(Component.text("Arena '" + typed + "' creada y guardada.", NamedTextColor.GREEN));
        });
    }

    /** Si el jugador se desconecta con una peticion de nombre pendiente, se olvida sin mas. */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        awaitingArenaName.remove(event.getPlayer().getUniqueId());
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
                awaitingArenaName.add(player.getUniqueId());
                player.sendMessage(Component.text(
                        "Escribe el nombre de la arena en el chat (o 'cancelar').", NamedTextColor.AQUA));
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

    private boolean isOurMenu(Inventory inventory) {
        return isOurHolder(inventory.getHolder());
    }

    private boolean isOurHolder(InventoryHolder holder) {
        return holder instanceof ModeMenuHolder
                || holder instanceof ColorMenuHolder
                || holder instanceof ArenaListMenuHolder
                || holder instanceof ArenaCreateMenuHolder;
    }
}
