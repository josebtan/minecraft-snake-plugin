package com.josebtan.snakeplugin.command;

import com.josebtan.snakeplugin.arena.Arena;
import com.josebtan.snakeplugin.arena.ArenaManager;
import com.josebtan.snakeplugin.game.GameManager;
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
 *   /snake arena pos1              -> marca la esquina 1 de la arena (donde estas parado)
 *   /snake arena pos2              -> marca la esquina 2
 *   /snake arena create <nombre>   -> construye la arena entre pos1 y pos2
 *   /snake arena list              -> lista las arenas creadas
 *   /snake join <arena>            -> crea tu serpiente dentro de esa arena
 *   /snake leave                   -> te levanta y elimina tu serpiente
 *
 * Sigue sin haber comida/puntos (Etapa 3) ni crecimiento de cola (Etapa 4).
 */
public class SnakeCommand implements CommandExecutor, TabCompleter {

    private final GameManager gameManager;
    private final ArenaManager arenaManager;

    public SnakeCommand(GameManager gameManager, ArenaManager arenaManager) {
        this.gameManager = gameManager;
        this.arenaManager = arenaManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Este comando solo se puede usar en el juego."));
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(Component.text("Uso: /snake <arena|join|leave>"));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "arena" -> handleArena(player, args);
            case "join" -> handleJoin(player, args);
            case "leave" -> handleLeave(player);
            default -> player.sendMessage(Component.text("Uso: /snake <arena|join|leave>"));
        }
        return true;
    }

    private void handleArena(Player player, String[] args) {
        if (!player.hasPermission("snakeplugin.arena.admin")) {
            player.sendMessage(Component.text("No tienes permiso para administrar arenas."));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(Component.text("Uso: /snake arena <pos1|pos2|create <nombre>|list>"));
            return;
        }

        switch (args[1].toLowerCase()) {
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
                    player.sendMessage(Component.text("Uso: /snake arena create <nombre>"));
                    return;
                }
                String name = args[2];
                Arena arena = arenaManager.createFromPending(player, name);
                if (arena == null) {
                    player.sendMessage(Component.text(
                            "Falta marcar pos1 y pos2 (en el mismo mundo) antes de crear la arena."));
                    return;
                }
                player.sendMessage(Component.text("Arena '" + name + "' creada."));
            }
            case "list" -> {
                if (arenaManager.getArenas().isEmpty()) {
                    player.sendMessage(Component.text("No hay arenas creadas todavia."));
                } else {
                    player.sendMessage(Component.text(
                            "Arenas: " + String.join(", ", arenaManager.getArenas().keySet())));
                }
            }
            default -> player.sendMessage(Component.text("Uso: /snake arena <pos1|pos2|create <nombre>|list>"));
        }
    }

    private void handleJoin(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("Uso: /snake join <arena>"));
            return;
        }
        if (gameManager.hasGame(player)) {
            player.sendMessage(Component.text("Ya tienes una serpiente activa. Usa /snake leave primero."));
            return;
        }
        Arena arena = arenaManager.getArena(args[1]);
        if (arena == null) {
            player.sendMessage(Component.text("No existe ninguna arena con ese nombre. Usa /snake arena list."));
            return;
        }
        gameManager.startGame(player, arena);
        player.sendMessage(Component.text(
                "Serpiente creada en '" + arena.getName() + "'. Usa W/A/S/D para dirigirla."));
    }

    private void handleLeave(Player player) {
        if (!gameManager.hasGame(player)) {
            player.sendMessage(Component.text("No tienes ninguna serpiente activa."));
            return;
        }
        gameManager.stopGame(player);
        player.sendMessage(Component.text("Serpiente detenida."));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.add("arena");
            options.add("join");
            options.add("leave");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("arena")) {
            options.add("pos1");
            options.add("pos2");
            options.add("create");
            options.add("list");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("join")) {
            options.addAll(arenaManager.getArenas().keySet());
        }
        return options;
    }
}
