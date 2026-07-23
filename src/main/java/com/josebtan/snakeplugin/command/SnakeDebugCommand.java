package com.josebtan.snakeplugin.command;

import com.josebtan.snakeplugin.game.GameManager;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Comando TEMPORAL solo para probar el movimiento de la cabeza durante la Etapa 1.
 * En la Etapa 2 se reemplazara por un sistema de comandos "/snake ..." completo,
 * con creacion de campo de juego, union a partidas, etc.
 *
 * Uso:
 *   /snakedebug start  -> crea tu serpiente (cabeza) en tu posicion y empieza a moverse
 *                         segun hacia donde mires (Norte/Sur/Este/Oeste)
 *   /snakedebug stop   -> detiene y elimina tu serpiente
 */
public class SnakeDebugCommand implements CommandExecutor {

    private final GameManager gameManager;

    public SnakeDebugCommand(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Este comando solo se puede usar en el juego."));
            return true;
        }

        if (args.length != 1) {
            player.sendMessage(Component.text("Uso: /snakedebug <start|stop>"));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "start" -> {
                if (gameManager.hasGame(player)) {
                    player.sendMessage(Component.text("Ya tienes una serpiente activa. Usa /snakedebug stop primero."));
                    return true;
                }
                gameManager.startGame(player);
                player.sendMessage(Component.text("Serpiente creada. Mira hacia Norte/Sur/Este/Oeste para dirigirla."));
            }
            case "stop" -> {
                if (!gameManager.hasGame(player)) {
                    player.sendMessage(Component.text("No tienes ninguna serpiente activa."));
                    return true;
                }
                gameManager.stopGame(player);
                player.sendMessage(Component.text("Serpiente detenida."));
            }
            default -> player.sendMessage(Component.text("Uso: /snakedebug <start|stop>"));
        }
        return true;
    }
}
