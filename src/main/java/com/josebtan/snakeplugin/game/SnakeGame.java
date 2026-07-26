package com.josebtan.snakeplugin.game;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

/**
 * Representa la partida de un jugador: su serpiente, posicion de la cabeza,
 * direccion actual y (en etapas futuras) su cola y puntuacion.
 *
 * ETAPA 1 (version 7): de vuelta al diseño original — la cabeza es un bloque de
 * lana REAL y visible que se mueve por el tablero. El jugador se "sienta" justo
 * encima de ese bloque, montado en un ArmorStand invisible que viaja pegado a
 * el en cada movimiento.
 *
 * La camara del jugador queda LIBRE (sin bloqueo cenital): puede mirar a su
 * alrededor con normalidad mientras viaja, como si estuviera sentado en un
 * carrito de minas.
 *
 * Las teclas WASD se leen con ProtocolLib (ver SnakeSteerPacketListener),
 * interceptando el paquete "Steer Vehicle" que el cliente envia SIEMPRE que el
 * jugador esta montado en cualquier entidad (no solo en monturas "oficiales"
 * como caballos): es la unica forma fiable de detectar las teclas sin depender
 * de que la entidad sea "Steerable", y sin apostar por APIs muy recientes de
 * Paper que ya nos dieron problemas antes.
 *
 * Sigue sin haber campo delimitado (Etapa 2), comida/puntos (Etapa 3), ni
 * crecimiento de cola (Etapa 4).
 */
public class SnakeGame {

    /** Cuanto se eleva el asiento sobre el bloque de la cabeza (altura tipica de "sentado"). */
    private static final double SEAT_HEIGHT = 1.0;

    private final UUID playerId;
    private final SnakeColor color;

    /** Posicion actual de la cabeza en la rejilla (coordenadas de bloque, Y fija = "boardY"). */
    private Location headLocation;

    /** Direccion en la que se esta moviendo la cabeza actualmente. */
    private Direction currentDirection;

    /**
     * Direccion solicitada por el jugador via WASD (leida del paquete de steering).
     * Se aplica en el siguiente tick de movimiento (no al instante), igual que en
     * el Snake clasico, y se descarta si intenta invertir la direccion actual
     * (giro de 180 grados).
     */
    private Direction requestedDirection;

    /** Cola de segmentos del cuerpo. Vacia por ahora, se usara en la Etapa 4. */
    private final Deque<Location> tail = new ArrayDeque<>();

    /** Asiento invisible (ArmorStand) sobre el que viaja el jugador, pegado al bloque de la cabeza. */
    private ArmorStand seat;

    private boolean active = false;

    public SnakeGame(UUID playerId, SnakeColor color) {
        this.playerId = playerId;
        this.color = color;
    }

    /**
     * Inicia la partida: coloca la cabeza (bloque de lana real) en el bloque bajo
     * el jugador, crea el asiento invisible justo encima, y lo monta en el.
     */
    public void start(Player player) {
        this.headLocation = player.getLocation().getBlock().getLocation();
        this.currentDirection = Direction.SOUTH;
        this.requestedDirection = currentDirection;
        this.active = true;

        headLocation.getBlock().setType(color.getWoolMaterial());

        this.seat = spawnSeat(headLocation);
        seat.addPassenger(player);
    }

    /** Crea el ArmorStand invisible que sirve de asiento. */
    private ArmorStand spawnSeat(Location head) {
        Location spawnAt = seatLocationFor(head);
        return spawnAt.getWorld().spawn(spawnAt, ArmorStand.class, stand -> {
            stand.setInvisible(true);
            stand.setSmall(true);
            stand.setBasePlate(false);
            stand.setArms(false);
            stand.setMarker(false); // marker=false: necesario para poder llevar pasajeros
            stand.setGravity(false);
            stand.setInvulnerable(true);
            stand.setSilent(true);
            stand.setCollidable(false);
            stand.setPersistent(false);
        });
    }

    /** Calcula la posicion del asiento: centrado sobre la casilla, a altura de "sentado". */
    private Location seatLocationFor(Location boardLocation) {
        return boardLocation.clone().add(0.5, SEAT_HEIGHT, 0.5);
    }

    /**
     * Detiene la partida: expulsa al jugador del asiento y lo elimina, limpia los
     * bloques del tablero, y deja al jugador de pie a salvo. 'active' se pone a
     * false ANTES de expulsarlo para que el listener que evita el desmontaje
     * voluntario sepa que este es intencional.
     */
    public void stop(Player player) {
        active = false;

        if (seat != null) {
            seat.eject();
            seat.remove();
            seat = null;
        }

        if (headLocation != null && player != null) {
            Location landing = headLocation.clone().add(0.5, 1.0, 0.5);
            landing.setYaw(player.getLocation().getYaw());
            landing.setPitch(player.getLocation().getPitch());
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
     * Registra la direccion pedida por el jugador (leida del paquete de steering
     * WASD). Se ignora si supone invertir la direccion actual de golpe.
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
     * Avanza la cabeza una casilla en la direccion solicitada, y mueve el asiento;
     * como el jugador es su pasajero, viaja con el automaticamente.
     * En la Etapa 4 aqui es donde la cola empezara a "seguir" a la cabeza.
     */
    public void tick() {
        if (!active || headLocation == null || seat == null) {
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
        seat.teleport(seatLocationFor(newHead));
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

    public ArmorStand getSeat() {
        return seat;
    }

    public boolean isActive() {
        return active;
    }
}
