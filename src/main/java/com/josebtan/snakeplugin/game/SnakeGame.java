package com.josebtan.snakeplugin.game;

import com.josebtan.snakeplugin.arena.Arena;
import com.josebtan.snakeplugin.food.FoodManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

/**
 * Representa la partida de un jugador: su serpiente (cabeza + cola), direccion
 * actual y puntuacion.
 *
 * ETAPA 3/4 (comida y crecimiento): el cuerpo entero se guarda en {@link #body}, una cola
 * de bloques con la CABEZA siempre al frente (peekFirst) y la punta de la cola al final
 * (peekLast). Cada llamada a {@link #tick} hace lo siguiente:
 *   1. Calcula la casilla de destino.
 *   2. Le pregunta al FoodManager si esa casilla es la comida actual de la arena.
 *      - Si SI es comida: se come. La serpiente CRECE (no se libera ningun segmento este
 *        tick) y se le pide al FoodManager una comida nueva en otro sitio.
 *      - Si NO es comida pero coincide exactamente con la punta de la propia cola (y la
 *        serpiente no esta creciendo este tick), el movimiento se permite igual: esa
 *        casilla se libera en el mismo instante en que la cabeza avanza (regla estandar
 *        del Snake clasico — si no, cualquier vuelta cerrada del largo exacto de la
 *        serpiente resultaria en un choque "injusto" contra su propia cola que ya se
 *        estaba yendo).
 *      - Cualquier otro bloque no-aire (algo que el jugador construyo a mano, la propia
 *        cola mas alla de ese caso especial, o la cola de otro jugador en la misma arena)
 *        es choque: la partida termina ahi mismo.
 *
 * ETAPA 2 sigue vigente: la partida vive dentro de una Arena (ver
 * com.josebtan.snakeplugin.arena.Arena), que solo define DONDE puede aparecer la
 * serpiente y la comida — no construye nada por su cuenta.
 *
 * La camara del jugador queda LIBRE (sin bloqueo cenital): puede mirar a su
 * alrededor con normalidad mientras viaja, como si estuviera sentado en un
 * carrito de minas.
 *
 * Las teclas WASD se leen con ProtocolLib (ver SnakeSteerPacketListener).
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

    /** Cuantas casillas libres por delante (en la direccion elegida) se exigen al elegir el punto de spawn. */
    private static final int SPAWN_CLEARANCE = 3;

    private final UUID playerId;
    private final SnakeColor color;

    /** Arena (campo de juego) donde vive esta partida. */
    private Arena arena;

    /** Cuerpo completo de la serpiente: cabeza al frente (peekFirst), punta de cola al final (peekLast). */
    private final Deque<Location> body = new ArrayDeque<>();

    /** Direccion en la que se esta moviendo la cabeza actualmente. */
    private Direction currentDirection;

    /**
     * Direccion solicitada por el jugador via WASD (leida del paquete de steering).
     * Se aplica en el siguiente tick de movimiento (no al instante), igual que en
     * el Snake clasico, y se descarta si intenta invertir la direccion actual
     * (giro de 180 grados).
     */
    private Direction requestedDirection;

    /** Asiento invisible (ArmorStand) sobre el que viaja el jugador, pegado al bloque de la cabeza. */
    private ArmorStand seat;

    /** Donde estaba parado el jugador justo antes de unirse a la arena — para devolverlo ahi al salir/morir. */
    private Location returnLocation;

    private boolean active = false;

    /** Cuantas veces comio esta partida. */
    private int score = 0;

    /** true si se eligio "Multijugador" en el menu (afecta que scoreboard se le muestra). */
    private boolean multiplayer;

    /** System.currentTimeMillis() de cuando arranco la partida, para calcular el tiempo jugado. */
    private long startTimeMillis;

    public SnakeGame(UUID playerId, SnakeColor color) {
        this.playerId = playerId;
        this.color = color;
    }

    /**
     * Inicia la partida dentro de la arena dada: elige una posicion Y direccion inicial
     * aleatorias y seguras (con espacio libre por delante en esa direccion — ver
     * Arena#findRandomSpawn, que prueba las 4 direcciones en cada celda, no solo "sur")
     * para la cabeza (bloque de lana real), crea el asiento invisible justo encima, y
     * monta al jugador en el.
     *
     * @param multiplayer si la arena es multijugador (se decide al crearla, ver
     *                     com.josebtan.snakeplugin.game.GameMode): decide que tipo de
     *                     scoreboard se le muestra a ESTE jugador (ver
     *                     GameManager#refreshScoreboards) — no afecta a la logica del juego
     *                     en si.
     * @return false si no se encontro ningun punto de spawn libre en la arena (no se inicia
     *         nada en ese caso); true si la partida arranco con normalidad.
     */
    public boolean start(Player player, Arena arena, boolean multiplayer) {
        this.arena = arena;
        this.returnLocation = player.getLocation().clone();
        this.multiplayer = multiplayer;
        this.startTimeMillis = System.currentTimeMillis();

        Arena.ArenaSpawn spawn = arena.findRandomSpawn(SPAWN_CLEARANCE);
        if (spawn == null) {
            return false;
        }
        this.currentDirection = spawn.direction();

        body.clear();
        body.addFirst(spawn.location());
        this.requestedDirection = currentDirection;
        this.active = true;
        this.score = 0;

        spawn.location().getBlock().setType(color.getWoolMaterial());

        this.seat = spawnSeat(spawn.location());
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
     * Detiene la partida: expulsa al jugador del asiento y lo elimina, limpia TODOS los
     * bloques del cuerpo (cabeza + cola), y devuelve al jugador al sitio exacto donde estaba
     * parado antes de entrar a la arena (ver {@link #returnLocation}) — tanto si sale
     * voluntariamente como si murio chocando. 'active' se pone a false ANTES de expulsarlo
     * para que el listener que evita el desmontaje voluntario sepa que este es intencional.
     */
    public void stop(Player player) {
        active = false;

        if (seat != null) {
            seat.eject();
            seat.remove();
            seat = null;
        }

        if (player != null && returnLocation != null) {
            player.teleport(returnLocation);
        }

        for (Location segment : body) {
            segment.getBlock().setType(Material.AIR);
        }
        body.clear();
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
     * Calcula a que casilla se moveria esta serpiente en el PROXIMO tick, sin aplicar nada
     * todavia (no pinta bloques, no mueve el asiento). Lo usa GameManager para detectar
     * choques de frente ANTES de que nadie se mueva de verdad: si dos serpientes planean
     * llegar a la misma casilla vacia el mismo tick, hay que decidir quien gana antes de
     * que la primera en procesarse "gane por accidente" solo por orden de iteracion.
     *
     * @return la casilla de destino planeada, o null si la partida no esta activa.
     */
    public Location peekNextHead() {
        if (!active || body.isEmpty()) {
            return null;
        }
        return body.peekFirst().clone().add(requestedDirection.getDx(), 0, requestedDirection.getDz());
    }

    /**
     * Marca esta partida como choque INMEDIATO, sin tocar el mundo: se usa cuando esta
     * serpiente pierde un choque de frente contra otra (ver GameManager#tickMovement) — como
     * nunca llego a pintar su nueva cabeza, no hay nada que deshacer aqui, solo terminar la
     * partida.
     */
    public TickResult collideHeadOn() {
        active = false;
        return TickResult.COLLIDED;
    }

    /**
     * Avanza la cabeza una casilla en la direccion solicitada. Ver el comentario de
     * clase para el detalle completo de la logica de comer/crecer/chocar.
     *
     * @return TickResult.COLLIDED si choco (fin de la partida); TickResult.ATE si comio
     *         (creceio, hay que avisar del punto); TickResult.ALIVE en cualquier otro
     *         movimiento normal (incluye el caso "partida ya inactiva", para no romper el
     *         loop del GameManager).
     */
    public TickResult tick(FoodManager foodManager) {
        if (!active || body.isEmpty() || seat == null) {
            return TickResult.ALIVE;
        }

        this.currentDirection = requestedDirection;

        Location head = body.peekFirst();
        Location newHead = head.clone().add(currentDirection.getDx(), 0, currentDirection.getDz());

        boolean isFood = foodManager.isFoodAt(arena, newHead);

        // Caso especial: si la casilla de destino es justo la punta de la propia cola Y no
        // estamos creciendo este tick, se permite el movimiento — esa casilla se libera en
        // el mismo instante en que la cabeza avanza (ver comentario de clase).
        Location tailEnd = body.peekLast();
        boolean vacatingOwnTail = !isFood && body.size() > 1 && sameBlock(newHead, tailEnd);

        if (!isFood && !vacatingOwnTail && newHead.getBlock().getType() != Material.AIR) {
            // Choque: algo que el jugador construyo, la propia cola (fuera del caso de
            // arriba), o la cola de otro jugador en la misma arena.
            active = false;
            return TickResult.COLLIDED;
        }

        // Si no comemos, se libera el ultimo segmento (puede ser exactamente newHead, ver
        // el caso "vacatingOwnTail" de arriba — por eso se limpia ANTES de pintar la
        // cabeza nueva, para no borrar el bloque recien pintado).
        if (!isFood) {
            Location freedSegment = body.removeLast();
            freedSegment.getBlock().setType(Material.AIR);
        }

        newHead.getBlock().setType(color.getWoolMaterial());
        body.addFirst(newHead);

        seat.teleport(seatLocationFor(newHead));

        if (isFood) {
            score++;
            foodManager.consumeAndRespawn(arena);
            return TickResult.ATE;
        }
        return TickResult.ALIVE;
    }

    private static boolean sameBlock(Location a, Location b) {
        return a.getWorld().equals(b.getWorld())
                && a.getBlockX() == b.getBlockX()
                && a.getBlockY() == b.getBlockY()
                && a.getBlockZ() == b.getBlockZ();
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
        return body.peekFirst();
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

    public int getScore() {
        return score;
    }

    /** Largo actual de la serpiente (cabeza incluida). */
    public int getLength() {
        return body.size();
    }

    public boolean isMultiplayer() {
        return multiplayer;
    }

    /** Segundos transcurridos desde que arranco esta partida. */
    public long getElapsedSeconds() {
        return (System.currentTimeMillis() - startTimeMillis) / 1000L;
    }
}
