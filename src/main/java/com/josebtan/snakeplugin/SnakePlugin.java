package com.josebtan.snakeplugin;

import com.josebtan.snakeplugin.command.SnakeDebugCommand;
import com.josebtan.snakeplugin.game.GameManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Clase principal del plugin. Se encarga de inicializar el GameManager y
 * registrar los comandos disponibles en cada etapa de desarrollo.
 */
public final class SnakePlugin extends JavaPlugin {

    private GameManager gameManager;

    @Override
    public void onEnable() {
        this.gameManager = new GameManager(this);

        // Etapa 1: comando temporal de pruebas para el movimiento de la cabeza.
        var debugCommand = getCommand("snakedebug");
        if (debugCommand != null) {
            debugCommand.setExecutor(new SnakeDebugCommand(gameManager));
        }

        getLogger().info("SnakePlugin habilitado (Etapa 1: movimiento del jugador y la cabeza).");
    }

    @Override
    public void onDisable() {
        if (gameManager != null) {
            gameManager.stopAll();
        }
        getLogger().info("SnakePlugin deshabilitado.");
    }

    public GameManager getGameManager() {
        return gameManager;
    }
}
