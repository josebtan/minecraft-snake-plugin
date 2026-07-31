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
 * NOTA: sideways=indice 0, forward=indice 1 (confirmado contra la propia
 * libreria ProtocolLib). Lo que SI fallaba antes era tratar esos dos floats
 * como si fueran coordenadas absolutas del mundo: en realidad son relativos
 * a hacia donde mira la camara del jugador en ese instante (W siempre es
 * "hacia donde miras", no "hacia el norte"). Por eso directionFromInput()
 * rota el vector (sideways, forward) por el yaw del jugador antes de decidir
 * la direccion de la rejilla — sin eso, el control solo "funcionaba por
 * casualidad" cuando el jugador miraba justo hacia el sur.
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

        // OJO: sideways/forward son RELATIVOS a hacia donde mira la camara del jugador en
        // ese instante, no coordenadas absolutas del mundo (esto es lo que fallaba antes:
        // se estaban tratando como si W siempre fuera "norte" del mundo, cuando en
        // realidad W siempre es "hacia donde miras"). Como la camara es libre, hay que
        // rotar ese vector por el yaw del jugador para saber a que direccion de la
        // rejilla (norte/sur/este/oeste) corresponde realmente.
        Direction requested = directionFromInput(sideways, forward, player.getLocation().getYaw());
        if (requested != null) {
            gameManager.requestDirection(player, requested);
        }
    }

    /**
     * Convierte los valores crudos de "sideways"/"forward" del paquete (relativos a la
     * camara) mas el yaw del jugador, en una de las 4 direcciones ABSOLUTAS de la
     * rejilla del mundo. Solo se atiende el eje dominante del vector resultante, igual
     * que en el Snake clasico (no se puede ir en diagonal).
     */
    private Direction directionFromInput(float sideways, float forward, float yawDegrees) {
        if (forward == 0f && sideways == 0f) {
            return null;
        }

        double yawRad = Math.toRadians(yawDegrees);

        // Vector "hacia adelante" en el mundo, segun hacia donde mira el jugador
        // (misma convencion que Location#getDirection(): yaw 0 = sur, 90 = oeste...).
        double forwardX = -Math.sin(yawRad);
        double forwardZ = Math.cos(yawRad);

        // Vector "hacia la izquierda del jugador" en el mundo (perpendicular al anterior).
        double leftX = Math.cos(yawRad);
        double leftZ = Math.sin(yawRad);

        // "sideways" es positivo hacia la izquierda del jugador (ver documentacion del
        // paquete Steer Vehicle / Player Input).
        double worldX = forward * forwardX + sideways * leftX;
        double worldZ = forward * forwardZ + sideways * leftZ;

        if (Math.abs(worldX) >= Math.abs(worldZ)) {
            return worldX > 0 ? Direction.EAST : Direction.WEST;
        } else {
            return worldZ > 0 ? Direction.SOUTH : Direction.NORTH;
        }
    }
}
