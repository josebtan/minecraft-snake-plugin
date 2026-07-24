package com.josebtan.snakeplugin.listener;

import com.josebtan.snakeplugin.game.Direction;
import com.josebtan.snakeplugin.game.GameManager;
import com.josebtan.snakeplugin.game.SnakeGame;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Traduce la entrada de teclado del jugador (WASD) en direcciones de
 * movimiento de su serpiente, y protege la partida en curso.
 *
 * Nota tecnica: PlayerInputEvent es una API relativamente reciente de Paper.
 * Si el build de GitHub Actions falla al compilar esta clase, significa que la
 * version de Paper indicada en el pom.xml todavia no la incluye.
 */
public class SnakeInputListener implements Listener {

    private final GameManager gameManager;

    public SnakeInputListener(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    /* DIAGNOSTICO TEMPORAL: deshabilitado para aislar si PlayerInputEvent es la causa del fallo de build.
    @EventHandler
    public void onPlayerInput(PlayerInputEvent event) {
        Player player = event.getPlayer();
        if (!gameManager.hasGame(player)) {
            return;
        }

        var input = event.getInput();

        Direction requested = null;
        if (input.isForward()) {
            requested = Direction.NORTH; // W
        } else if (input.isBackward()) {
            requested = Direction.SOUTH; // S
        } else if (input.isLeft()) {
            requested = Direction.WEST;  // A
        } else if (input.isRight()) {
            requested = Direction.EAST;  // D
        }

        if (requested != null) {
            gameManager.requestDirection(player, requested);
        }
    }
    */

    /**
     * Mientras el jugador esta montado, su posicion la controla la montura, pero
     * puede seguir moviendo la camara con el raton. Aqui la forzamos de vuelta a
     * la vista cenital fija cada vez que intenta cambiarla (no usamos un bucle
     * constante: solo actua cuando el propio cliente envia un cambio de vista).
     */
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        SnakeGame game = gameManager.getGame(player);
        if (game == null || !game.isActive()) {
            return;
        }

        Location to = event.getTo();
        if (to == null) {
            return;
        }

        if (to.getYaw() != SnakeGame.LOCKED_YAW || to.getPitch() != SnakeGame.LOCKED_PITCH) {
            Location corrected = to.clone();
            corrected.setYaw(SnakeGame.LOCKED_YAW);
            corrected.setPitch(SnakeGame.LOCKED_PITCH);
            event.setTo(corrected);
        }
    }

    /**
     * Evita que el jugador se baje voluntariamente (con Shift) de la montura
     * mientras la partida sigue activa. Cuando SI queremos que se baje (al
     * terminar la partida con /snakedebug stop), SnakeGame#stop pone 'active'
     * a false ANTES de expulsarlo, por lo que este listener lo deja pasar.
     */
    @EventHandler
    public void onEntityDismount(EntityDismountEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        SnakeGame game = gameManager.getGame(player);
        if (game != null && game.isActive() && event.getDismounted().equals(game.getMount())) {
            event.setCancelled(true);
        }
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
