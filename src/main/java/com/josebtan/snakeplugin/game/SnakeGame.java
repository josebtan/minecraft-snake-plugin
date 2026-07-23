package com.josebtan.snakeplugin.game;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Bat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

/**
 * Representa la partida de un jugador: su serpiente, posicion de la cabeza,
 * direccion actual y (en etapas futuras) su cola y puntuacion.
 *
 * ETAPA 1 (version 2): el jugador "monta" la cabeza de la serpiente -> se mueve
 * fisicamente junto a ella. La camara queda fija en vista cenital (mirando hacia
 * abajo) y bloqueada mientras dure la partida. El movimiento se controla con las
 * teclas WASD, leidas directamente (no depende de hacia donde mire el jugador).
 *
 * Sigue sin haber campo delimitado (Etapa 2), comida/puntos (Etapa 3), ni
 * crecimiento de cola (Etapa 4).
 */
public class SnakeGame {

    /** Cuantos bloques por encima del tablero "flota" la camara del jugador. */
    private static final double CAMERA_HEIGHT = 12.0;

    /** Yaw fijo de la camara mientras dura la partida (0 = el jugador mira hacia el Sur). */
    private static final float LOCKED_YAW = 0f;

    /** Pitch fijo de la camara: -90 = mirando directamente hacia abajo (vista cenital). */
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

    /** Entidad invisible que el jugador "monta": es lo que hace que se mueva con la cabeza. */
    private Entity cameraVehicle;

    private boolean active = false;

    public SnakeGame(UUID playerId, SnakeColor color) {
        this.playerId = playerId;
        this.color = color;
    }

    /**
     * Inicia la partida: coloca la cabeza en el bloque bajo el jugador, crea la
     * entidad-camara invisible por encima del tablero, monta al jugador en ella,
     * y bloquea su vista mirando hacia abajo.
     */
    public void start(Player player) {
        this.headLocation = player.getLocation().getBlock().getLocation();
        this.currentDirection = Direction.SOUTH;
        this.requestedDirection = currentDirection;
        this.active = true;

        headLocation.getBlock().setType(color.getWoolMaterial());

        this.cameraVehicle = spawnCameraVehicle(headLocation);
        cameraVehicle.addPassenger(player);

        lockCamera(player);
    }

    /**
     * Crea la entidad invisible que sirve de "montura"/camara. Se usa un Bat porque
     * no le afecta la gravedad de forma natural, es pequeno y puede llevar pasajero.
     */
    private Entity spawnCameraVehicle(Location head) {
        Location spawnAt = cameraLocationFor(head);
        return spawnAt.getWorld().spawn(spawnAt, Bat.class, b -> {
            b.setInvisible(true);
            b.setInvulnerable(true);
            b.setSilent(true);
            b.setAI(false);
            b.setGravity(false);
            b.setCollidable(false);
            b.setPersistent(false);
            b.setAwake(true);
        });
    }

    /** Calcula la posicion de la camara: centrada sobre la casilla y elevada. */
    private Location cameraLocationFor(Location boardLocation) {
        return boardLocation.clone().add(0.5, CAMERA_HEIGHT, 0.5);
    }

    /** Fuerza la rotacion del jugador a la vista cenital fija, sin mover su posicion. */
    private void lockCamera(Player player) {
        Location current = player.getLocation();
        current.setYaw(LOCKED_YAW);
        current.setPitch(LOCKED_PITCH);
        player.teleport(current);
    }

    /**
     * Vuelve a aplicar el bloqueo de camara. Se llama en cada tick de servidor
     * (mas frecuente que el movimiento de la serpiente) para que el jugador no
     * pueda mover la vista con el raton entre movimiento y movimiento.
     */
    public void enforceCameraLock(Player player) {
        if (!active) {
            return;
        }
        lockCamera(player);
    }

    /**
     * Detiene la partida: expulsa al jugador de la montura, la elimina, y limpia
     * los bloques del tablero. Importante: 'active' se pone a false ANTES de
     * expulsar al jugador, para que el listener que evita el desmontaje voluntario
     * sepa que este desmontaje es intencional y no lo cancele.
     */
    public void stop(Player player) {
        active = false;

        if (cameraVehicle != null) {
            cameraVehicle.eject();
            cameraVehicle.remove();
            cameraVehicle = null;
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
     * Registra la direccion pedida por el jugador (leida de las teclas WASD).
     * Se ignora si supone invertir la direccion actual de golpe.
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
     * al jugador (al mover la entidad-camara, el pasajero viaja con ella).
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

        if (cameraVehicle != null) {
            cameraVehicle.teleport(cameraLocationFor(newHead));
        }
        enforceCameraLock(player);
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

    public Entity getCameraVehicle() {
        return cameraVehicle;
    }

    public boolean isActive() {
        return active;
    }
}
