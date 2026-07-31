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

import java.util.concurrent.atomic.AtomicBoolean;

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
 * IMPORTANTE — dos formatos distintos segun la version del servidor:
 * - Hasta 1.21.1 aprox: el paquete trae dos floats, "sideways" (indice 0) y
 *   "forward" (indice 1).
 * - Desde 1.21.4: Mojang rehizo este paquete por completo (ver "Input"
 *   record en el propio codigo de Minecraft) y ahora viaja como 7 booleanos
 *   (forward, backward, left, right, jump, shift, sprint), sin ningun campo
 *   float. Si el servidor va en esta version y seguimos leyendo
 *   getFloat().read(0), revienta con FieldAccessException ("length 0") —
 *   fue justo el error que salio en el log de pruebas.
 *
 * Por eso aqui se intenta primero el formato nuevo (booleanos) y, si el
 * paquete no los trae, se cae al formato viejo (floats). Si ninguno de los
 * dos aparece (version todavia mas rara/futura), se deja constancia en el
 * log UNA sola vez con el tamaño de cada modificador, para poder diagnosticar
 * sin tener que adivinar a ciegas.
 *
 * NOTA sobre orientacion: tanto "sideways" como los booleanos left/right son
 * RELATIVOS a hacia donde mira la camara del jugador en ese instante, no
 * coordenadas absolutas del mundo (W siempre es "hacia donde miras", no
 * "hacia el norte"). Por eso directionFromInput() rota el vector resultante
 * por el yaw del jugador antes de decidir la direccion de la rejilla — sin
 * eso, el control solo "funcionaria por casualidad" cuando el jugador mira
 * justo hacia el sur.
 */
public class SnakeSteerPacketListener {

    private final Plugin plugin;
    private final GameManager gameManager;

    /** Para no inundar la consola: el aviso de "no reconozco este paquete" solo sale una vez. */
    private final AtomicBoolean loggedUnknownFormat = new AtomicBoolean(false);

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

        try {
            float sideways;
            float forward;

            int booleanCount = event.getPacket().getBooleans().size();
            int floatCount = event.getPacket().getFloat().size();

            if (booleanCount >= 4) {
                // Formato nuevo (1.21.4+): 7 booleanos en orden
                // forward, backward, left, right, jump, shift, sprint.
                boolean fwd = event.getPacket().getBooleans().read(0);
                boolean bwd = event.getPacket().getBooleans().read(1);
                boolean left = event.getPacket().getBooleans().read(2);
                boolean right = event.getPacket().getBooleans().read(3);

                forward = fwd ? 1f : (bwd ? -1f : 0f);
                // Igual que en el formato viejo: "sideways" positivo = hacia la izquierda.
                sideways = left ? 1f : (right ? -1f : 0f);
            } else if (floatCount >= 2) {
                // Formato viejo (hasta 1.21.1 aprox): dos floats, sideways=0, forward=1.
                sideways = event.getPacket().getFloat().read(0);
                forward = event.getPacket().getFloat().read(1);
            } else {
                if (loggedUnknownFormat.compareAndSet(false, true)) {
                    plugin.getLogger().warning(
                            "SnakePlugin: no reconozco el formato del paquete de input de esta version de "
                                    + "Minecraft (booleans=" + booleanCount + ", floats=" + floatCount
                                    + "). El control WASD no va a funcionar hasta que se actualice "
                                    + "SnakeSteerPacketListener para este formato.");
                }
                return;
            }

            // OJO: sideways/forward son RELATIVOS a hacia donde mira la camara del jugador en
            // ese instante, no coordenadas absolutas del mundo. Como la camara es libre, hay
            // que rotar ese vector por el yaw del jugador para saber a que direccion de la
            // rejilla (norte/sur/este/oeste) corresponde realmente.
            Direction requested = directionFromInput(sideways, forward, player.getLocation().getYaw());
            if (requested != null) {
                gameManager.requestDirection(player, requested);
            }
        } catch (Exception e) {
            if (loggedUnknownFormat.compareAndSet(false, true)) {
                plugin.getLogger().warning("SnakePlugin: error leyendo el paquete de input: " + e);
            }
        }
    }

    /**
     * Convierte los valores crudos de "sideways"/"forward" (relativos a la camara) mas el
     * yaw del jugador, en una de las 4 direcciones ABSOLUTAS de la rejilla del mundo. Solo
     * se atiende el eje dominante del vector resultante, igual que en el Snake clasico (no
     * se puede ir en diagonal).
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

        // "sideways" es positivo hacia la izquierda del jugador.
        double worldX = forward * forwardX + sideways * leftX;
        double worldZ = forward * forwardZ + sideways * leftZ;

        if (Math.abs(worldX) >= Math.abs(worldZ)) {
            return worldX > 0 ? Direction.EAST : Direction.WEST;
        } else {
            return worldZ > 0 ? Direction.SOUTH : Direction.NORTH;
        }
    }
}
