package com.josebtan.snakeplugin.listener;

import com.josebtan.snakeplugin.game.Direction;
import com.josebtan.snakeplugin.game.GameManager;
import com.josebtan.snakeplugin.game.SnakeGame;
import io.papermc.paper.event.player.PlayerInputEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Traduce la entrada de teclado del jugador (WASD) en direcciones de movimiento
 * de su serpiente, y protege la partida en curso: evita que el jugador se baje
 * de la montura (Shift) mientras juega, y limpia la partida si se desconecta.
 *
 * Nota: PlayerInputEvent es una API relativamente reciente de Paper (requiere
 * una build moderna de Paper 1.21.x). Si al compilar/ejecutar da error de que
 * la clase o metodo no existe, puede que el servidor de destino use una build
 * de Paper mas antigua sin esta API; en ese caso habria que revisar el nombre
 * exacto segun la version instalada.
 */
public class SnakeInputListener implements Listener {

    private final GameManager gameManager;

    public SnakeInputListener(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @EventHandler
    public void onPlayerInput(PlayerInputEvent event) {
        Player player = event.getPlayer();
        if (!gameManager.hasGame(player)) {
            return;
        }

        var input = event.getInput();

        // Prioridad simple: si pulsa varias teclas a la vez, se atiende una sola
        // (igual que en el Snake clasico, solo se puede ir en una direccion).
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

    /**
     * Evita que el jugador se baje voluntariamente (con Shift) de la montura
     * mientras la partida sigue activa. Cuando SÍ queremos que se baje (al
     * terminar la partida con /snakedebug stop), SnakeGame#stop pone 'active'
     * a false ANTES de expulsarlo, por lo que este listener lo deja pasar.
     */
    @EventHandler
    public void onEntityDismount(EntityDismountEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        SnakeGame game = gameManager.getGame(player);
        if (game != null && game.isActive() && event.getDismounted().equals(game.getCameraVehicle())) {
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
