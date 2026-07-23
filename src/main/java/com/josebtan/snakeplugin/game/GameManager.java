package com.josebtan.snakeplugin.game;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Administra todas las partidas de snake activas en el servidor y el bucle de juego
 * (game loop) que hace avanzar a todas las cabezas al mismo ritmo, tick tras tick.
 *
 * En la Etapa 1 el "ritmo de juego" esta fijo (MOVE_INTERVAL_TICKS). En etapas
 * futuras esto podria configurarse por partida (dificultad, velocidad, etc).
 */
public class GameManager {

    /** Cada cuantos ticks de servidor avanza la serpiente una casilla (20 ticks = 1s). */
    private static final long MOVE_INTERVAL_TICKS = 8L; // ~0.4s por movimiento

    private final Plugin plugin;
    private final Map<UUID, SnakeGame> games = new ConcurrentHashMap<>();
    private BukkitTask loopTask;
    private int nextColorIndex = 0;

    public GameManager(Plugin plugin) {
        this.plugin = plugin;
    }

    /** Crea e inicia una partida nueva para el jugador, si no tiene ya una activa. */
    public SnakeGame startGame(Player player) {
        UUID id = player.getUniqueId();
        if (games.containsKey(id)) {
            return games.get(id);
        }

        SnakeColor color = SnakeColor.byIndex(nextColorIndex++);
        SnakeGame game = new SnakeGame(id, color);
        game.start(player.getLocation());
        games.put(id, game);

        ensureLoopRunning();
        return game;
    }

    /** Detiene y elimina la partida del jugador, si existe. */
    public void stopGame(Player player) {
        SnakeGame game = games.remove(player.getUniqueId());
        if (game != null) {
            game.stop();
        }
        if (games.isEmpty()) {
            stopLoop();
        }
    }

    public SnakeGame getGame(Player player) {
        return games.get(player.getUniqueId());
    }

    public boolean hasGame(Player player) {
        return games.containsKey(player.getUniqueId());
    }

    /** Arranca el bucle de juego si no estaba corriendo ya. */
    private void ensureLoopRunning() {
        if (loopTask != null) {
            return;
        }
        loopTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickAll, MOVE_INTERVAL_TICKS, MOVE_INTERVAL_TICKS);
    }

    private void stopLoop() {
        if (loopTask != null) {
            loopTask.cancel();
            loopTask = null;
        }
    }

    /** Se ejecuta cada MOVE_INTERVAL_TICKS: actualiza direccion y mueve cada serpiente activa. */
    private void tickAll() {
        for (SnakeGame game : games.values()) {
            Player player = Bukkit.getPlayer(game.getPlayerId());
            if (player == null || !player.isOnline()) {
                continue;
            }
            game.updateDirectionFromPlayerLook(player);
            game.tick();
        }
    }

    /** Detiene todas las partidas activas, por ejemplo al desactivar el plugin. */
    public void stopAll() {
        for (SnakeGame game : games.values()) {
            game.stop();
        }
        games.clear();
        stopLoop();
    }
}
