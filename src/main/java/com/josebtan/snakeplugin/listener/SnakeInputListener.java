package com.josebtan.snakeplugin.listener;

import com.josebtan.snakeplugin.game.Direction;
import com.josebtan.snakeplugin.game.GameManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Traduce la entrada de teclado del jugador (WASD) en direcciones de movimiento
 * de su serpiente, y protege la partida en curso.
 *
 * Como el jugador esta "anclado" en el aire (SnakeGame lo vuelve a colocar en su
 * posicion fija en cada tick), cualquier intento de moverse con WASD produce un
 * PlayerMoveEvent con un pequeño desplazamiento horizontal: lo usamos para saber
 * que tecla se pulso, y luego cancelamos ese movimiento (su posicion real la
 * decide siempre el juego, no el movimiento libre del jugador).
 */
public class SnakeInputListener implements Listener {

    private final GameManager gameManager;

    public SnakeInputListener(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!gameManager.hasGame(player)) {
            return;
        }

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) {
            return;
        }

        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();

        // Umbral pequeño para ignorar micro-movimientos residuales (no un intento real de tecla).
        if (Math.abs(dx) > 0.02 || Math.abs(dz) > 0.02) {
            Direction requested = Math.abs(dx) > Math.abs(dz)
                    ? (dx > 0 ? Direction.EAST : Direction.WEST)
                    : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
            gameManager.requestDirection(player, requested);
        }

        // La posicion real del jugador la controla siempre SnakeGame (teleport-lock),
        // asi que cancelamos cualquier desplazamiento libre que intente el cliente.
        event.setTo(from);
    }

    /** Si el jugador se desconecta a mitad de partida, limpiamos su serpiente del mundo. */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (gameManager.hasGame(player)) {
            gameManager.stopGame(player);
        }
    }
}
