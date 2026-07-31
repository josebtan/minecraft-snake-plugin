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

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Lee las teclas WASD mientras el jugador esta sentado en su serpiente,
 * usando ProtocolLib para interceptar el paquete "Steer Vehicle" (tambien
 * llamado "Player Input" en versiones mas recientes del protocolo).
 *
 * Por que hace falta esto: el cliente de Minecraft SIEMPRE envia este paquete
 * cuando el jugador esta montado en cualquier entidad, sin importar si esa
 * entidad responde o no al movimiento en el propio juego. Bukkit/Paper no
 * expone ese paquete de forma nativa y estable en todas las versiones, asi
 * que se usa ProtocolLib para interceptarlo a bajo nivel.
 *
 * HISTORIAL DEL PROBLEMA (importante para no volver a tropezar con esto):
 * - Hasta 1.21.1 aprox: el paquete traia dos floats de toda la vida,
 *   "sideways" (indice 0) y "forward" (indice 1). Esto es lo que leia la
 *   primera version de esta clase.
 * - Desde 1.21.4: Mojang rehizo el paquete. Ahora es un
 *   "ServerboundPlayerInputPacket" que envuelve un unico record "Input" con
 *   7 booleanos (forward, backward, left, right, jump, shift, sprint). Como
 *   ese record va ANIDADO (no son campos sueltos del paquete), ni
 *   packet.getFloat() ni packet.getBooleans() encuentran nada: ambos
 *   devuelven longitud 0 (confirmado en pruebas reales: "booleans=0,
 *   floats=0"). ProtocolLib, al menos en la version usada aqui, todavia no
 *   desempaqueta ese record anidado por si solo.
 *
 * SOLUCION: cuando ninguno de los dos formatos "planos" anteriores aparece,
 * se cae a un tercer nivel: leer el paquete NMS crudo (packet.getHandle())
 * por reflexion pura, buscando un metodo sin argumentos que devuelva un
 * objeto con metodos booleanos "forward"/"backward"/"left"/"right" (el
 * record Input). Esto funciona SIN tener que fijar nombres de paquete/clase
 * de Minecraft a mano (que cambian entre versiones), porque en el Paper
 * moderno los nombres de metodo de estos records ya vienen "deofuscados"
 * (mapeados con Mojang mappings) y coinciden literalmente con
 * forward()/backward()/left()/right(). El resultado de esta busqueda se
 * cachea la primera vez que funciona, para no repetir el escaneo en cada
 * paquete (20 veces por segundo por jugador).
 *
 * NOTA sobre orientacion: sideways/forward (o los booleanos left/right) son
 * RELATIVOS a hacia donde mira la camara del jugador en ese instante, no
 * coordenadas absolutas del mundo. Por eso directionFromInput() rota el
 * vector resultante por el yaw del jugador antes de decidir la direccion de
 * la rejilla.
 */
public class SnakeSteerPacketListener {

    private final Plugin plugin;
    private final GameManager gameManager;

    /** Para no inundar la consola: el aviso de "no reconozco este paquete" solo sale una vez. */
    private final AtomicBoolean loggedUnknownFormat = new AtomicBoolean(false);

    // --- Cache de reflexion para el formato nuevo (1.21.4+) ---
    private final Object reflectionLock = new Object();
    private volatile boolean reflectionAttempted = false;
    private volatile Method inputAccessor;    // metodo del paquete que devuelve el record "Input"
    private volatile Method forwardAccessor;
    private volatile Method backwardAccessor;
    private volatile Method leftAccessor;
    private volatile Method rightAccessor;

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
                // Formato "plano" nuevo, en caso de que ProtocolLib SI lo desempaquete
                // en alguna version: 7 booleanos, forward/backward/left/right/jump/shift/sprint.
                boolean fwd = event.getPacket().getBooleans().read(0);
                boolean bwd = event.getPacket().getBooleans().read(1);
                boolean left = event.getPacket().getBooleans().read(2);
                boolean right = event.getPacket().getBooleans().read(3);
                forward = fwd ? 1f : (bwd ? -1f : 0f);
                sideways = left ? 1f : (right ? -1f : 0f);
            } else if (floatCount >= 2) {
                // Formato viejo (hasta 1.21.1 aprox): dos floats, sideways=0, forward=1.
                sideways = event.getPacket().getFloat().read(0);
                forward = event.getPacket().getFloat().read(1);
            } else {
                // Ni floats ni booleans "planos": probablemente el record Input anidado
                // (1.21.4+). Ultimo recurso: leer el paquete NMS crudo por reflexion.
                Object handle = event.getPacket().getHandle();
                if (handle == null || !setupReflectionIfNeeded(handle)) {
                    if (loggedUnknownFormat.compareAndSet(false, true)) {
                        plugin.getLogger().warning(
                                "SnakePlugin: no reconozco el formato del paquete de input de esta "
                                        + "version de Minecraft (booleans=" + booleanCount + ", floats="
                                        + floatCount + ", y la busqueda por reflexion tampoco encontro "
                                        + "forward/backward/left/right). El control WASD no va a "
                                        + "funcionar hasta que se actualice SnakeSteerPacketListener.");
                    }
                    return;
                }

                Object input = inputAccessor.invoke(handle);
                boolean fwd = (boolean) forwardAccessor.invoke(input);
                boolean bwd = (boolean) backwardAccessor.invoke(input);
                boolean left = (boolean) leftAccessor.invoke(input);
                boolean right = (boolean) rightAccessor.invoke(input);
                forward = fwd ? 1f : (bwd ? -1f : 0f);
                sideways = left ? 1f : (right ? -1f : 0f);
            }

            // sideways/forward son RELATIVOS a hacia donde mira la camara del jugador en ese
            // instante, no coordenadas absolutas del mundo. Como la camara es libre, hay que
            // rotar ese vector por el yaw del jugador para saber a que direccion de la rejilla
            // (norte/sur/este/oeste) corresponde realmente.
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
     * Busca, una sola vez (y cachea el resultado), un metodo sin argumentos en la clase del
     * paquete NMS que devuelva un objeto con metodos booleanos forward()/backward()/left()/
     * right() — es decir, el record "Input" anidado del formato nuevo. No fija nombres de
     * paquete/clase de Minecraft a mano (esos cambian entre versiones); busca por forma,
     * no por nombre de clase.
     */
    private boolean setupReflectionIfNeeded(Object handle) {
        if (reflectionAttempted) {
            return inputAccessor != null;
        }
        synchronized (reflectionLock) {
            if (reflectionAttempted) {
                return inputAccessor != null;
            }
            reflectionAttempted = true;
            try {
                for (Method m : handle.getClass().getMethods()) {
                    if (m.getParameterCount() != 0) {
                        continue;
                    }
                    Class<?> returnType = m.getReturnType();
                    if (returnType.isPrimitive() || returnType == String.class || returnType == Class.class) {
                        continue;
                    }
                    Object candidate;
                    try {
                        candidate = m.invoke(handle);
                    } catch (Exception ex) {
                        continue;
                    }
                    if (candidate == null) {
                        continue;
                    }
                    Method fwd = findBooleanAccessor(candidate.getClass(), "forward");
                    Method bwd = findBooleanAccessor(candidate.getClass(), "backward");
                    Method left = findBooleanAccessor(candidate.getClass(), "left");
                    Method right = findBooleanAccessor(candidate.getClass(), "right");
                    if (fwd != null && bwd != null && left != null && right != null) {
                        inputAccessor = m;
                        forwardAccessor = fwd;
                        backwardAccessor = bwd;
                        leftAccessor = left;
                        rightAccessor = right;
                        plugin.getLogger().info(
                                "SnakePlugin: formato de input detectado por reflexion (" + m.getName()
                                        + "() -> " + candidate.getClass().getSimpleName() + ").");
                        return true;
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("SnakePlugin: fallo inspeccionando el paquete de input por reflexion: " + e);
            }
            return false;
        }
    }

    private static Method findBooleanAccessor(Class<?> type, String name) {
        try {
            Method m = type.getMethod(name);
            return m.getReturnType() == boolean.class ? m : null;
        } catch (NoSuchMethodException e) {
            return null;
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

        double forwardX = -Math.sin(yawRad);
        double forwardZ = Math.cos(yawRad);

        double leftX = Math.cos(yawRad);
        double leftZ = Math.sin(yawRad);

        double worldX = forward * forwardX + sideways * leftX;
        double worldZ = forward * forwardZ + sideways * leftZ;

        if (Math.abs(worldX) >= Math.abs(worldZ)) {
            return worldX > 0 ? Direction.EAST : Direction.WEST;
        } else {
            return worldZ > 0 ? Direction.SOUTH : Direction.NORTH;
        }
    }
}
