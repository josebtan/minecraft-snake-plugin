package com.josebtan.snakeplugin;

import com.josebtan.snakeplugin.arena.ArenaManager;
import com.josebtan.snakeplugin.command.SnakeCommand;
import com.josebtan.snakeplugin.config.SnakeConfig;
import com.josebtan.snakeplugin.game.GameManager;
import com.josebtan.snakeplugin.gui.ArenaCreationFlow;
import com.josebtan.snakeplugin.gui.SnakeGuiListener;
import com.josebtan.snakeplugin.listener.SnakeInputListener;
import com.josebtan.snakeplugin.listener.SnakeSteerPacketListener;
import com.josebtan.snakeplugin.skin.SkinManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Clase principal del plugin. Se encarga de inicializar el GameManager, el
 * ArenaManager y registrar los comandos disponibles en cada etapa de desarrollo.
 */
public final class SnakePlugin extends JavaPlugin {

    private GameManager gameManager;
    private ArenaManager arenaManager;
    private SnakeConfig snakeConfig;
    private SkinManager skinManager;

    @Override
    public void onEnable() {
        // Copia config.yml (el empaquetado en el jar) a la carpeta plugins/ en el
        // primer arranque; si el archivo ya existe lo deja intacto.
        saveDefaultConfig();

        this.snakeConfig = new SnakeConfig(this);
        this.skinManager = new SkinManager(this);
        this.gameManager = new GameManager(this, snakeConfig);
        this.arenaManager = new ArenaManager(this);

        // El flujo de creacion de arenas por chat (nombre -> modo -> maximo de jugadores)
        // lo comparten el comando /snake y el listener de la GUI.
        ArenaCreationFlow creationFlow = new ArenaCreationFlow(arenaManager);

        // Etapa 2: comando definitivo /snake (reemplaza al /snakedebug de la Etapa 1).
        var snakeCommand = getCommand("snake");
        if (snakeCommand != null) {
            SnakeCommand executor = new SnakeCommand(gameManager, arenaManager, creationFlow, snakeConfig, skinManager);
            snakeCommand.setExecutor(executor);
            snakeCommand.setTabCompleter(executor);
        }

        getServer().getPluginManager().registerEvents(new SnakeInputListener(gameManager), this);
        getServer().getPluginManager().registerEvents(new SnakeGuiListener(this, gameManager, arenaManager, creationFlow, skinManager), this);
        new SnakeSteerPacketListener(this, gameManager).register();

        logBanner();
    }

    private void logBanner() {
        String version = getDescription().getVersion();
        getLogger().info("""
                ══════════════════════════════════════════════════════
                   ███████╗███╗   ██╗ █████╗ ██╗  ██╗███████╗
                   ██╔════╝████╗  ██║██╔══██╗██║ ██╔╝██╔════╝
                   ███████╗██╔██╗ ██║███████║█████╔╝ █████╗
                   ╚════██║██║╚██╗██║██╔══██║██╔═██╗ ██╔══╝
                   ███████║██║ ╚████║██║  ██║██║  ██╗███████╗
                   ╚══════╝╚═╝  ╚═══╝╚═╝  ╚═╝╚═╝  ╚═╝╚══════╝
                            SnakePlugin %s
                     Plugin enabled successfully! Created by josebtan.
                ══════════════════════════════════════════════════════
                """.formatted(version));
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
