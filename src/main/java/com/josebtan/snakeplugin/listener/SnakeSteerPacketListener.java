package com.josebtan.snakeplugin.listener;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.josebtan.snakeplugin.game.Direction;
import com.josebtan.snakeplugin.game.GameManager;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Lee las teclas WASD mientras el jugador esta sentado en su serpiente,
 * usando ProtocolLib para interceptar el paquete "Steer Vehicle" (tambien
 * llamado "Player Input" en versiones mas recientes del protocolo).
 *
 * Por que hace falta esto: el cliente de Minecraft SIEMPRE envia este paquete
 * cuando el jugador esta montado en cualquier entidad, sin importar si esa
 * entidad responde o no al movimiento en el propio juego (un ArmorStand nunca
 * se movera solo, pero el paquete se sigue enviando igualmente). Bukkit/Paper
 * no expone ese paquete de forma nativa y estable en todas las versiones, asi
 * que usamos ProtocolLib, que lo abstrae de forma fiable desde hace años.
 *
 * NOTA: el orden exacto de los campos (sideways/forward) y el signo de cada
 * uno vienen del protocolo de Minecraft y no se pueden verificar sin probar
 * en un servidor real. Si al jugar notas que W te mueve hacia el lado
 * contrario, o que A/D estan invertidos, es cuestion de intercambiar los
 * indices o invertir el signo aqui abajo (esta todo centralizado en este
 * metodo para que sea facil de ajustar).
 */
public class SnakeSteerPacketListener {

    private final Plugin plugin;
    private final GameManager gameManager;

    public SnakeSteerPacketListener(Plugin plugin, GameManager gameManager) {
        this.plugin = plugin;
        this.gameManager = gameManager;
    }

    /** Registra el listener de paquetes en ProtocolLib. Llamar una vez desde onEnable. */
    public void register() {
        ProtocolManager protocolManager = ProtocolLibrary.getProtocolManager();
        protocolManager.addPacketListener(new PacketAdapter(
                plugin, ListenerPriority.NORMAL, PacketType.Play.Client.STEER_VEHICLE) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                handleSteerPacket(event);
            }
        });
    }

    private void handleSteerPacket(PacketEvent event) {
        Player player = event.getPlayer();
        if (!gameManager.hasGame(player)) {
            return;
        }

        // Campos del paquete: dos floats, "sideways" (A/D) y "forward" (W/S), en ese orden.
        float sideways = event.getPacket().getFloat().read(0);
        float forward = event.getPacket().getFloat().read(1);

        Direction requested = directionFromInput(sideways, forward);
        if (requested != null) {
            gameManager.requestDirection(player, requested);
        }
    }

    /**
     * Convierte los valores crudos de "sideways"/"forward" del paquete en una
     * de las 4 direcciones de la rejilla. Solo se atiende la tecla dominante,
     * igual que en el Snake clasico (no se puede ir en diagonal).
     */
    private Direction directionFromInput(float sideways, float forward) {
        if (forward == 0f && sideways == 0f) {
            return null;
        }
        if (Math.abs(forward) >= Math.abs(sideways)) {
            return forward > 0 ? Direction.NORTH : Direction.SOUTH; // W : S
        } else {
            return sideways > 0 ? Direction.WEST : Direction.EAST;  // A : D
        }
    }
}
