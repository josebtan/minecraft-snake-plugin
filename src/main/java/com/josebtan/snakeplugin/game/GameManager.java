package com.josebtan.snakeplugin.game;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Administra todas las partidas de snake activas en el servidor y el bucle de
 * movimiento que las hace avanzar.
 *
 * A diferencia de versiones anteriores, ya NO hay un bucle aparte "de camara"
 * que reteleporte al jugador cada tick de servidor: al ir montado en un cerdo
 * invisible (ver SnakeGame), su posicion la resuelve el propio motor de
 * Minecraft de forma gratuita, y el bloqueo de vista se aplica de forma
 * reactiva en SnakeInputListener (solo cuando el jugador intenta mover la
 * camara), no en un bucle constante. Esto reduce bastante la carga sobre el
 * servidor comparado con el enfoque anterior.
 */
public class GameManager {

    /** Cada cuantos ticks de servidor avanza la serpiente una casilla (20 ticks = 1s). */
    private static final long MOVE_INTERVAL_TICKS = 8L; // ~0.4s por movimiento

    private final Plugin plugin;
    private final Map<UUID, SnakeGame> games = new ConcurrentHashMap<>();
    private BukkitTask movementTask;
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

        ensureLoopRunning();
        return game;
    }

    /** Detiene y elimina la partida del jugador, si existe. */
    public void stopGame(Player player) {
        SnakeGame game = games.remove(player.getUniqueId());
        if (game != null) {
            game.stop(player);
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

    /** Llamado desde el listener de input (WASD) cuando el jugador pide girar. */
    public void requestDirection(Player player, Direction direction) {
        SnakeGame game = games.get(player.getUniqueId());
        if (game != null) {
            game.requestDirection(direction);
        }
    }

    private void ensureLoopRunning() {
        if (movementTask == null) {
            movementTask = Bukkit.getScheduler()
                    .runTaskTimer(plugin, this::tickMovement, MOVE_INTERVAL_TICKS, MOVE_INTERVAL_TICKS);
        }
    }

    private void stopLoop() {
        if (movementTask != null) {
            movementTask.cancel();
            movementTask = null;
        }
    }

    /** Se ejecuta cada MOVE_INTERVAL_TICKS: mueve cada cabeza (y su montura, con el jugador encima) una casilla. */
    private void tickMovement() {
        for (SnakeGame game : games.values()) {
            game.tick();
        }
    }

    /** Detiene todas las partidas activas, por ejemplo al desactivar el plugin. */
    public void stopAll() {
        for (SnakeGame game : games.values()) {
            Player player = Bukkit.getPlayer(game.getPlayerId());
            game.stop(player);
        }
        games.clear();
        stopLoop();
    }
}
