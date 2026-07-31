package com.josebtan.snakeplugin.game;

import com.josebtan.snakeplugin.arena.Arena;
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
 * ETAPA 2: la partida ahora vive dentro de una Arena (ver com.josebtan.snakeplugin.arena.Arena),
 * que solo define DONDE puede aparecer la serpiente (y, en la Etapa 3, la comida) — ya no
 * construye paredes ni modifica el mundo. Cualquier bloque solido que haya en el camino,
 * puesto por quien sea, cuenta como obstaculo igual (ver #tick).
 *
 * La camara del jugador queda LIBRE (sin bloqueo cenital): puede mirar a su
 * alrededor con normalidad mientras viaja, como si estuviera sentado en un
 * carrito de minas.
 *
 * Las teclas WASD se leen con ProtocolLib (ver SnakeSteerPacketListener).
 *
 * Sigue sin haber comida/puntos (Etapa 3) ni crecimiento de cola (Etapa 4).
 */
public class SnakeGame {

    /**
     * Cuanto se eleva el PUNTO DE SPAWN del asiento (ArmorStand) sobre el bloque de la
     * cabeza. OJO: esto NO es la altura final a la que queda sentado el jugador — un
     * ArmorStand "small" ya engancha a su pasajero bastante por encima de su propio punto
     * de spawn (es la propia geometria del modelo, no algo que controlemos nosotros). Por
     * eso este valor es mucho menor que "1 bloque": hay que restar mentalmente ese offset
     * interno del ArmorStand.
     *
     * Historial de ajuste (a ojo, en base a pruebas en servidor real):
     * - 1.0   -> demasiado alto (version original).
     * - 0.45  -> seguia demasiado alto.
     * - 0.15  -> seguia demasiado alto.
     * - -0.35 -> quedo demasiado bajo (jugador incrustado en el bloque).
     * - 0.0   -> valor actual, a probar.
     */
    private static final double SEAT_HEIGHT = 0.0;

    /** Cuantas casillas libres por delante se exigen al elegir el punto de spawn (ver Arena#findRandomSpawn). */
    private static final int SPAWN_CLEARANCE = 3;

    /** Cuantos intentos de posicion aleatoria se prueban antes de rendirse. */
    private static final int SPAWN_MAX_ATTEMPTS = 40;

    private final UUID playerId;
    private final SnakeColor color;

    /** Arena (campo de juego) donde vive esta partida. */
    private Arena arena;

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
     * Inicia la partida dentro de la arena dada: elige una posicion aleatoria segura (con
     * espacio libre por delante, ver Arena#findRandomSpawn) para la cabeza (bloque de lana
     * real), crea el asiento invisible justo encima, y monta al jugador en el.
     *
     * @return false si no se encontro ningun punto de spawn libre en la arena (no se inicia
     *         nada en ese caso); true si la partida arranco con normalidad.
     */
    public boolean start(Player player, Arena arena) {
        this.arena = arena;
        this.currentDirection = Direction.SOUTH;

        Location spawn = arena.findRandomSpawn(currentDirection, SPAWN_CLEARANCE, SPAWN_MAX_ATTEMPTS);
        if (spawn == null) {
            return false;
        }

        this.headLocation = spawn;
        this.requestedDirection = currentDirection;
        this.active = true;

        headLocation.getBlock().setType(color.getWoolMaterial());

        this.seat = spawnSeat(headLocation);
        seat.addPassenger(player);
        return true;
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
     *
     * DETECCION DE CHOQUES (Etapa 2): antes de mover la cabeza, se comprueba el bloque de
     * destino. Si es AIRE, se puede avanzar con normalidad. Si es cualquier otra cosa —algo
     * que el propio jugador construyo dentro de su arena, la propia cola, o la cola de otro
     * jugador (Etapa 4)— se considera un choque y la partida termina aqui mismo. Este mismo
     * chequeo es el que mas adelante (Etapa 3) habra que ampliar: en vez de tratar CUALQUIER
     * bloque no-aire como choque, habra que revisar primero si es comida o un power-up (y
     * actuar en consecuencia) antes de asumir que es un obstaculo.
     *
     * En la Etapa 4 aqui es tambien donde la cola empezara a "seguir" a la cabeza.
     *
     * @return false si la cabeza choco contra algo (fin de la partida); true si se movio
     *         con normalidad (o si la partida ya no estaba activa, para no romper el loop).
     */
    public boolean tick() {
        if (!active || headLocation == null || seat == null) {
            return true;
        }

        this.currentDirection = requestedDirection;

        Location previousHead = headLocation.clone();
        Location newHead = headLocation.clone().add(currentDirection.getDx(), 0, currentDirection.getDz());

        if (newHead.getBlock().getType() != Material.AIR) {
            // Choque: bloque construido por el jugador, cola propia, o cola de otro jugador.
            active = false;
            return false;
        }

        newHead.getBlock().setType(color.getWoolMaterial());

        // Sin cola todavia (Etapa 4): el bloque anterior se limpia directamente.
        if (tail.isEmpty()) {
            previousHead.getBlock().setType(Material.AIR);
        }

        this.headLocation = newHead;
        seat.teleport(seatLocationFor(newHead));
        return true;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public SnakeColor getColor() {
        return color;
    }

    public Arena getArena() {
        return arena;
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
