package com.josebtan.snakeplugin.game;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Administra todas las partidas de snake activas en el servidor y los dos bucles
 * que las mantienen funcionando:
 *
 *  - Bucle de MOVIMIENTO (cada MOVE_INTERVAL_TICKS): hace avanzar una casilla a
 *    cada cabeza, aplicando la ultima direccion pedida por WASD.
 *  - Bucle de CAMARA (cada tick de servidor): vuelve a fijar la vista cenital del
 *    jugador, para que no pueda moverla libremente con el raton entre movimientos.
 */
public class GameManager {

    /** Cada cuantos ticks de servidor avanza la serpiente una casilla (20 ticks = 1s). */
    private static final long MOVE_INTERVAL_TICKS = 8L; // ~0.4s por movimiento

    private final Plugin plugin;
    private final Map<UUID, SnakeGame> games = new ConcurrentHashMap<>();
    private BukkitTask movementTask;
    private BukkitTask cameraTask;
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
        game.start(player);
        games.put(id, game);

        ensureLoopsRunning();
        return game;
    }

    /** Detiene y elimina la partida del jugador, si existe. */
    public void stopGame(Player player) {
        SnakeGame game = games.remove(player.getUniqueId());
        if (game != null) {
            game.stop(player);
        }
        if (games.isEmpty()) {
            stopLoops();
        }
    }

    public SnakeGame getGame(Player player) {
        return games.get(player.getUniqueId());
    }

    public boolean hasGame(Player player) {
        return games.containsKey(player.getUniqueId());
    }

    /** Llamado desde el listener de input (WASD) cuando el jugador pide girar. */
    public void requestDirection(Player player, Direction direction) {
        SnakeGame game = games.get(player.getUniqueId());
        if (game != null) {
            game.requestDirection(direction);
        }
    }

    private void ensureLoopsRunning() {
        if (movementTask == null) {
            movementTask = Bukkit.getScheduler()
                    .runTaskTimer(plugin, this::tickMovement, MOVE_INTERVAL_TICKS, MOVE_INTERVAL_TICKS);
        }
        if (cameraTask == null) {
            cameraTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickCameras, 1L, 1L);
        }
    }

    private void stopLoops() {
        if (movementTask != null) {
            movementTask.cancel();
            movementTask = null;
        }
        if (cameraTask != null) {
            cameraTask.cancel();
            cameraTask = null;
        }
    }

    /** Se ejecuta cada MOVE_INTERVAL_TICKS: mueve cada cabeza (y a su jugador montado) una casilla. */
    private void tickMovement() {
        for (SnakeGame game : games.values()) {
            Player player = Bukkit.getPlayer(game.getPlayerId());
            if (player == null || !player.isOnline()) {
                continue;
            }
            game.tick(player);
        }
    }

    /** Se ejecuta cada tick: reafirma la vista cenital bloqueada de cada jugador en partida. */
    private void tickCameras() {
        for (SnakeGame game : games.values()) {
            Player player = Bukkit.getPlayer(game.getPlayerId());
            if (player == null || !player.isOnline()) {
                continue;
            }
            game.enforceCameraLock(player);
        }
    }

    /** Detiene todas las partidas activas, por ejemplo al desactivar el plugin. */
    public void stopAll() {
        for (SnakeGame game : games.values()) {
            Player player = Bukkit.getPlayer(game.getPlayerId());
            if (player != null) {
                game.stop(player);
            }
        }
        games.clear();
        stopLoops();
    }
}
