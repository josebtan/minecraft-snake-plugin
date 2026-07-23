package com.josebtan.snakeplugin.game;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

/**
 * Representa la partida de un jugador: su serpiente, posicion de la cabeza,
 * direccion actual y (en etapas futuras) su cola y puntuacion.
 *
 * ETAPA 1 (version 2): el jugador viaja junto con la cabeza de la serpiente,
 * como si la montara, flotando por encima del tablero. La camara queda fija en
 * vista cenital (mirando hacia abajo) y bloqueada mientras dure la partida.
 * El movimiento se controla con las teclas WASD.
 *
 * NOTA DE DISEÑO: en vez de usar una entidad-vehiculo (montura real) o APIs muy
 * recientes de Paper para leer el teclado, usamos una tecnica clasica y estable
 * compatible con cualquier version reciente de Paper/Spigot: cada tick se
 * reafirma con un teleport la posicion (flotando sobre la cabeza) y la rotacion
 * (mirando hacia abajo) del jugador. Las teclas WASD se detectan en
 * SnakeInputListener leyendo el pequeño desplazamiento que intenta hacer el
 * jugador (PlayerMoveEvent) y cancelandolo, ya que su posicion real la controla
 * siempre este teleport-lock.
 *
 * Sigue sin haber campo delimitado (Etapa 2), comida/puntos (Etapa 3), ni
 * crecimiento de cola (Etapa 4).
 */
public class SnakeGame {

    /** Cuantos bloques por encima del tablero "flota" el jugador. */
    private static final double HOVER_HEIGHT = 12.0;

    /** Yaw fijo mientras dura la partida (no importa el valor exacto: solo debe ser constante). */
    private static final float LOCKED_YAW = 0f;

    /** Pitch fijo: -90 = mirando directamente hacia abajo (vista cenital). */
    private static final float LOCKED_PITCH = -90f;

    private final UUID playerId;
    private final SnakeColor color;

    /** Posicion actual de la cabeza en la rejilla (coordenadas de bloque, Y fija = "boardY"). */
    private Location headLocation;

    /** Direccion en la que se esta moviendo la cabeza actualmente. */
    private Direction currentDirection;

    /**
     * Direccion solicitada por el jugador via WASD. Se aplica en el siguiente tick
     * de movimiento (no al instante), igual que en el Snake clasico, y se descarta
     * si intenta invertir la direccion actual (giro de 180 grados).
     */
    private Direction requestedDirection;

    /** Cola de segmentos del cuerpo. Vacia por ahora, se usara en la Etapa 4. */
    private final Deque<Location> tail = new ArrayDeque<>();

    private boolean active = false;

    public SnakeGame(UUID playerId, SnakeColor color) {
        this.playerId = playerId;
        this.color = color;
    }

    /**
     * Inicia la partida: coloca la cabeza en el bloque bajo el jugador, y lo
     * "levanta" flotando por encima del tablero con la vista bloqueada hacia abajo.
     */
    public void start(Player player) {
        this.headLocation = player.getLocation().getBlock().getLocation();
        this.currentDirection = Direction.SOUTH;
        this.requestedDirection = currentDirection;
        this.active = true;

        headLocation.getBlock().setType(color.getWoolMaterial());

        lockPlayerAboveHead(player);
    }

    /** Calcula la posicion "flotante" del jugador: centrada sobre la casilla y elevada. */
    private Location hoverLocationFor(Location boardLocation) {
        Location hover = boardLocation.clone().add(0.5, HOVER_HEIGHT, 0.5);
        hover.setYaw(LOCKED_YAW);
        hover.setPitch(LOCKED_PITCH);
        return hover;
    }

    /**
     * Fuerza al jugador a la posicion/rotacion bloqueadas (flotando sobre la cabeza,
     * mirando hacia abajo), y anula cualquier velocidad residual.
     */
    private void lockPlayerAboveHead(Player player) {
        player.teleport(hoverLocationFor(headLocation));
        player.setVelocity(new Vector(0, 0, 0));
        player.setFallDistance(0f);
    }

    /**
     * Vuelve a aplicar el bloqueo de posicion/camara. Se llama en cada tick de
     * servidor (mas frecuente que el movimiento de la serpiente) para que el
     * jugador no pueda desplazarse ni mirar alrededor entre movimiento y
     * movimiento de la serpiente.
     */
    public void enforceLock(Player player) {
        if (!active || headLocation == null) {
            return;
        }
        lockPlayerAboveHead(player);
    }

    /**
     * Detiene la partida: limpia los bloques del tablero y deja al jugador de pie
     * a salvo, cerca de donde estaba la cabeza, con la vista liberada.
     */
    public void stop(Player player) {
        active = false;

        if (headLocation != null && player != null) {
            Location landing = headLocation.clone().add(0.5, 1.0, 0.5);
            landing.setYaw(player.getLocation().getYaw());
            landing.setPitch(0f);
            player.teleport(landing);
        }

        if (headLocation != null) {
            headLocation.getBlock().setType(Material.AIR);
        }
        for (Location segment : tail) {
            segment.getBlock().setType(Material.AIR);
        }
        tail.clear();
    }

    /**
     * Registra la direccion pedida por el jugador (detectada a partir de su
     * intento de movimiento con WASD). Se ignora si supone invertir la
     * direccion actual de golpe.
     */
    public void requestDirection(Direction requested) {
        if (!active) {
            return;
        }
        if (requested != currentDirection.getOpposite()) {
            this.requestedDirection = requested;
        }
    }

    /**
     * Avanza la cabeza una casilla en la direccion solicitada, y mueve con ella
     * al jugador (se le vuelve a "enganchar" encima de la nueva posicion).
     * En la Etapa 4 aqui es donde la cola empezara a "seguir" a la cabeza.
     */
    public void tick(Player player) {
        if (!active || headLocation == null) {
            return;
        }

        this.currentDirection = requestedDirection;

        Location previousHead = headLocation.clone();
        Location newHead = headLocation.clone().add(currentDirection.getDx(), 0, currentDirection.getDz());

        newHead.getBlock().setType(color.getWoolMaterial());

        // Sin cola todavia (Etapa 4): el bloque anterior se limpia directamente.
        if (tail.isEmpty()) {
            previousHead.getBlock().setType(Material.AIR);
        }

        this.headLocation = newHead;

        lockPlayerAboveHead(player);
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public SnakeColor getColor() {
        return color;
    }

    public Location getHeadLocation() {
        return headLocation;
    }

    public Direction getCurrentDirection() {
        return currentDirection;
    }

    public boolean isActive() {
        return active;
    }
}
