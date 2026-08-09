package com.josebtan.snakeplugin.command;

import com.josebtan.snakeplugin.arena.Arena;
import com.josebtan.snakeplugin.arena.ArenaManager;
import com.josebtan.snakeplugin.config.SnakeConfig;
import com.josebtan.snakeplugin.game.GameManager;
import com.josebtan.snakeplugin.gui.ArenaCreationFlow;
import com.josebtan.snakeplugin.gui.ArenaListMenuHolder;
import com.josebtan.snakeplugin.gui.SnakeGuiMenus;
import com.josebtan.snakeplugin.skin.SkinManager;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Comando definitivo del plugin (Etapa 2). Reemplaza al temporal /snakedebug de la Etapa 1.
 *
 * Uso:
 *   /snake arena                   -> abre el panel de creacion/administracion (GUI)
 *   /snake arena menu              -> lo mismo, explicito
 *   /snake arena pos1              -> marca la esquina 1 de la arena (donde estas parado)
 *   /snake arena pos2              -> marca la esquina 2
 *   /snake arena create [nombre]   -> construye la arena entre pos1 y pos2. Pide por chat
 *                                     el nombre (si no se dio), el MODO (solo/multi) y, en
 *                                     modo multi, el maximo de jugadores (ver ArenaCreationFlow)
 *   /snake arena delete <nombre>   -> elimina esa arena (se guarda en disco)
 *   /snake arena list              -> lista las arenas creadas (texto)
 *   /snake join                    -> abre el menu para elegir arena (GUI), luego skin.
 *                                     En arenas multijugador, entra en la sala de espera.
 *   /snake join <arena>            -> salta directo al menu de skins para esa arena
 *   /snake leave                   -> te levanta y elimina tu serpiente (o te saca de la
 *                                     sala de espera si todavia no habias empezado)
 *   /snake reload                  -> recarga config.yml (modo de movimiento y skins) sin
 *                                     reiniciar el servidor (permiso snakeplugin.reload)
 *
 * La comida ya funciona (una por arena, compartida) y la cola crece al comer.
 */
public class SnakeCommand implements CommandExecutor, TabCompleter {

    private final GameManager gameManager;
    private final ArenaManager arenaManager;
    private final ArenaCreationFlow creationFlow;
    private final SnakeConfig snakeConfig;
    private final SkinManager skinManager;

    public SnakeCommand(GameManager gameManager, ArenaManager arenaManager, ArenaCreationFlow creationFlow,
                        SnakeConfig snakeConfig, SkinManager skinManager) {
        this.gameManager = gameManager;
        this.arenaManager = arenaManager;
        this.creationFlow = creationFlow;
        this.snakeConfig = snakeConfig;
        this.skinManager = skinManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Este comando solo se puede usar en el juego."));
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(Component.text("Uso: /snake <arena|join|leave|reload>"));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "arena" -> handleArena(player, args);
            case "join" -> handleJoin(player, args);
            case "leave" -> handleLeave(player);
            case "reload" -> handleReload(player);
            default -> player.sendMessage(Component.text("Uso: /snake <arena|join|leave|reload>"));
        }
        return true;
    }

    private void handleReload(Player player) {
        if (!player.hasPermission("snakeplugin.reload")) {
            player.sendMessage(Component.text("No tienes permiso para recargar la configuracion."));
            return;
        }
        snakeConfig.reload();
        skinManager.reload();
        player.sendMessage(Component.text(
                "Configuracion recargada. Modo de movimiento: "
                        + (snakeConfig.isSmoothMovement() ? "suave" : "clasico") + "."));
    }

    private void handleArena(Player player, String[] args) {
        if (!player.hasPermission("snakeplugin.arena.admin")) {
            player.sendMessage(Component.text("No tienes permiso para administrar arenas."));
            return;
        }
        if (args.length < 2) {
            SnakeGuiMenus.openArenaCreateMenu(player, arenaManager);
            return;
        }

        switch (args[1].toLowerCase()) {
            case "menu" -> SnakeGuiMenus.openArenaCreateMenu(player, arenaManager);
            case "pos1" -> {
                arenaManager.setPos1(player, player.getLocation());
                player.sendMessage(Component.text("Esquina 1 marcada en tu posicion."));
            }
            case "pos2" -> {
                arenaManager.setPos2(player, player.getLocation());
                player.sendMessage(Component.text("Esquina 2 marcada en tu posicion."));
            }
            case "create" -> {
                if (args.length < 3) {
                    creationFlow.start(player);
                } else {
                    creationFlow.startWithName(player, args[2]);
                }
            }
            case "delete" -> {
                if (args.length < 3) {
                    player.sendMessage(Component.text("Uso: /snake arena delete <nombre>"));
                    return;
                }
                String name = args[2];
                if (arenaManager.deleteArena(name)) {
                    player.sendMessage(Component.text("Arena '" + name + "' eliminada."));
                } else {
                    player.sendMessage(Component.text("No existe ninguna arena con ese nombre."));
                }
            }
            case "list" -> {
                if (arenaManager.getArenas().isEmpty()) {
                    player.sendMessage(Component.text("No hay arenas creadas todavia."));
                } else {
                    player.sendMessage(Component.text(
                            "Arenas: " + String.join(", ", arenaManager.getArenas().keySet())));
                }
            }
            default -> player.sendMessage(Component.text("Uso: /snake arena <pos1|pos2|create <nombre>|delete <nombre>|list>"));
        }
    }

    private void handleJoin(Player player, String[] args) {
        if (gameManager.hasGame(player) || gameManager.isInLobby(player)) {
            player.sendMessage(Component.text("Ya tienes una serpiente activa o estas esperando. Usa /snake leave primero."));
            return;
        }

        if (args.length < 2) {
            SnakeGuiMenus.openArenaListMenu(player, arenaManager, ArenaListMenuHolder.Mode.JOIN);
            return;
        }

        Arena arena = arenaManager.getArena(args[1]);
        if (arena == null) {
            player.sendMessage(Component.text("No existe ninguna arena con ese nombre. Usa /snake arena list."));
            return;
        }
        if (!arena.getMode().isMultiplayer() && !gameManager.isArenaAvailableForSolo(arena)) {
            player.sendMessage(Component.text(
                    "Esta arena es de un jugador y ya esta en uso. Espera a que termine o usa otra arena."));
            return;
        }
        SnakeGuiMenus.openSkinGroupMenu(player, arena, gameManager, skinManager);
    }

    private void handleLeave(Player player) {
        if (gameManager.hasGame(player)) {
            gameManager.stopGame(player);
            player.sendMessage(Component.text("Serpiente detenida."));
            return;
        }
        if (gameManager.isInLobby(player)) {
            gameManager.leaveLobby(player);
            player.sendMessage(Component.text("Has salido de la sala de espera."));
            return;
        }
        player.sendMessage(Component.text("No tienes ninguna serpiente activa ni estas esperando."));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.add("arena");
            options.add("join");
            options.add("leave");
            options.add("reload");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("arena")) {
            options.add("menu");
            options.add("pos1");
            options.add("pos2");
            options.add("create");
            options.add("delete");
            options.add("list");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("join")) {
            options.addAll(arenaManager.getArenas().keySet());
        } else if (args.length == 3 && args[0].equalsIgnoreCase("arena") && args[1].equalsIgnoreCase("delete")) {
            options.addAll(arenaManager.getArenas().keySet());
        }
        return options;
    }
}
