package com.josebtan.snakeplugin.listener;

import com.josebtan.snakeplugin.game.Direction;
import com.josebtan.snakeplugin.game.GameManager;
import com.josebtan.snakeplugin.game.SnakeGame;
import org.bukkit.Location;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;

/**
 * Traduce la entrada de teclado del jugador (WASD) en direcciones de
 * movimiento de su serpiente, y protege la partida en curso.
 *
 * Como el jugador va montado en un caballo domado y ensillado (Steerable), al
 * pulsar WASD el propio Minecraft mueve un poco al caballo en esa direccion:
 * eso es lo que detectamos via VehicleMoveEvent (API estable, sin necesidad de
 * ninguna version reciente de Paper). En cuanto detectamos la direccion,
 * "anclamos" de nuevo el caballo a su casilla, para que no se desplace
 * libremente: solo se mueve en pasos discretos, a cargo de SnakeGame#tick.
 */
public class SnakeInputListener implements Listener {

    private final GameManager gameManager;

    public SnakeInputListener(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @EventHandler
    public void onVehicleMove(VehicleMoveEvent event) {
        if (!(event.getVehicle() instanceof Horse horse) || horse.getPassengers().isEmpty()) {
            return;
        }
        if (!(horse.getPassengers().get(0) instanceof Player player)) {
            return;
        }
        SnakeGame game = gameManager.getGame(player);
        if (game == null || !game.isActive() || !horse.equals(game.getMount())) {
            return;
        }

        double dx = event.getTo().getX() - event.getFrom().getX();
        double dz = event.getTo().getZ() - event.getFrom().getZ();

        if (Math.abs(dx) > 0.01 || Math.abs(dz) > 0.01) {
            Direction requested = Math.abs(dx) > Math.abs(dz)
                    ? (dx > 0 ? Direction.EAST : Direction.WEST)
                    : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
            gameManager.requestDirection(player, requested);
        }

        // Evitamos que el caballo se desplace libremente: siempre vuelve a su casilla.
        game.reanchorMount();
    }

    /**
     * Mientras el jugador esta montado, puede seguir moviendo la camara con el
     * raton. Aqui la forzamos de vuelta a la vista cenital fija cada vez que
     * intenta cambiarla (no usamos un bucle constante: solo actua cuando el
     * propio cliente envia un cambio de vista).
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
