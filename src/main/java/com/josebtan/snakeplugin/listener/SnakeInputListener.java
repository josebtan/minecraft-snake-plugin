package com.josebtan.snakeplugin.listener;

import com.josebtan.snakeplugin.game.GameManager;
import com.josebtan.snakeplugin.game.SnakeGame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Protege la partida en curso. La deteccion de WASD ya NO ocurre aqui: eso lo
 * hace SnakeSteerPacketListener via ProtocolLib. Este listener solo se encarga
 * de: evitar que el jugador se baje del asiento a mitad de partida, y limpiar
 * su serpiente si se desconecta. La camara del jugador queda libre (sin
 * bloqueo), asi que tampoco hay nada que hacer aqui al respecto.
 */
public class SnakeInputListener implements Listener {

    private final GameManager gameManager;

    public SnakeInputListener(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    /**
     * Evita que el jugador se baje voluntariamente (con Shift) del asiento
     * mientras la partida sigue activa. Cuando SI queremos que se baje (al
     * terminar la partida con /snake leave), SnakeGame#stop pone 'active'
     * a false ANTES de expulsarlo, por lo que este listener lo deja pasar.
     */
    @EventHandler
    public void onEntityDismount(EntityDismountEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        SnakeGame game = gameManager.getGame(player);
        if (game != null && game.isActive() && event.getDismounted().equals(game.getSeat())) {
            event.setCancelled(true);
        }
    }

    /** Si el jugador se desconecta a mitad de partida, limpiamos su serpiente del mundo (y su sala de espera, si estaba en una). */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (gameManager.hasGame(player)) {
            gameManager.stopGame(player);
        }
        if (gameManager.isInLobby(player)) {
            gameManager.leaveLobby(player);
        }
    }
}
