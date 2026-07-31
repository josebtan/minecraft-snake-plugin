package com.josebtan.snakeplugin;

import com.josebtan.snakeplugin.arena.ArenaManager;
import com.josebtan.snakeplugin.command.SnakeCommand;
import com.josebtan.snakeplugin.game.GameManager;
import com.josebtan.snakeplugin.listener.SnakeInputListener;
import com.josebtan.snakeplugin.listener.SnakeSteerPacketListener;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Clase principal del plugin. Se encarga de inicializar el GameManager, el
 * ArenaManager y registrar los comandos disponibles en cada etapa de desarrollo.
 */
public final class SnakePlugin extends JavaPlugin {

    private GameManager gameManager;
    private ArenaManager arenaManager;

    @Override
    public void onEnable() {
        this.gameManager = new GameManager(this);
        this.arenaManager = new ArenaManager();

        // Etapa 2: comando definitivo /snake (reemplaza al /snakedebug de la Etapa 1).
        var snakeCommand = getCommand("snake");
        if (snakeCommand != null) {
            SnakeCommand executor = new SnakeCommand(gameManager, arenaManager);
            snakeCommand.setExecutor(executor);
            snakeCommand.setTabCompleter(executor);
        }

        getServer().getPluginManager().registerEvents(new SnakeInputListener(gameManager), this);
        new SnakeSteerPacketListener(this, gameManager).register();

        getLogger().info("SnakePlugin habilitado (Etapa 2: arenas delimitadas + comando /snake + deteccion de choques).");
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

    public ArenaManager getArenaManager() {
        return arenaManager;
    }
}
