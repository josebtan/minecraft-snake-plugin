package com.josebtan.snakeplugin.game;

import com.josebtan.snakeplugin.arena.Arena;
import com.josebtan.snakeplugin.food.FoodManager;
import com.josebtan.snakeplugin.skin.Skin;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
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
 * Al entrar a la arena la serpiente espera unos segundos sin moverse (retardo
 * inicial, ver {@link #isInStartGrace}) y el asiento se desliza suavemente entre
 * celdas (ver {@link #advanceSeatAnimation}): la logica de la serpiente sigue
 * siendo de rejilla, solo se suaviza la vista del jugador que viaja en el asiento.
 *
 * VISUALIZACION: en el mundo, cada celda del cuerpo lleva un bloque BARRIER invisible
 * (ver {@link #BODY_BLOCK}) que existe SOLO para las colisiones — la logica de
 * {@link #tick} mira el bloque del mundo y no se toca. Lo que se VE es una display
 * entity por segmento (ver {@link #spawnDisplay}) que se desliza suavemente de celda en
 * celda (ver {@link #advanceDisplayAnimation}).
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

    /**
     * Bloque que se coloca en el mundo en cada celda del cuerpo. Es un BARRIER: invisible
     * y solido, existe solo para que la colision de {@link #tick} (que mira el bloque del
     * mundo) siga funcionando igual que con lana. Lo que se VE es la display entity de
     * cada segmento (ver {@link #spawnDisplay}).
     */
    private static final Material BODY_BLOCK = Material.BARRIER;

    /**
     * Segundos que espera la serpiente SIN MOVERSE tras entrar a la arena, para que el
     * jugador pueda mirar a su alrededor y orientarse antes de que arranque.
     */
    private static final int START_DELAY_SECONDS = 3;

    private static final long START_DELAY_MILLIS = START_DELAY_SECONDS * 1000L;

    /**
     * Pasos de la animacion (asiento y displays del cuerpo): uno por tick de servidor, de
     * modo que en los MOVE_INTERVAL_TICKS que separan dos movimientos el asiento y cada
     * segmento visible recorren la casilla entera y llegan justo a tiempo al siguiente
     * salto (movimiento suave; la rejilla no cambia).
     */
    private static final int SEAT_ANIM_STEPS = 8;

    private final UUID playerId;
    private final Skin skin;

    /**
     * Modo de movimiento (de la config): true = suave (asiento y displays se deslizan),
     * false = clasico (el cuerpo es lana que salta de bloque en bloque y el asiento se
     * teletransporta).
     */
    private final boolean smoothMovement;

    /** Arena (campo de juego) donde vive esta partida. */
    private Arena arena;

    /** Cuerpo completo de la serpiente: cabeza al frente (peekFirst), punta de cola al final (peekLast). */
    private final Deque<Location> body = new ArrayDeque<>();

    /** Segmentos visibles: un BlockDisplay por segmento, alineado con {@link #body} (cabeza primero). */
    private final Deque<BlockDisplay> displaySegments = new ArrayDeque<>();

    /** De donde viene cada display (posicion previa) durante el deslizamiento. */
    private final Deque<Location> displayFrom = new ArrayDeque<>();

    /** Hacia donde va cada display (celda actual de su segmento) durante el deslizamiento. */
    private final Deque<Location> displayTo = new ArrayDeque<>();

    /** Paso actual del deslizamiento de los displays (0 = recien salido, SEAT_ANIM_STEPS = llegado). */
    private int displayStep;

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

    /** Trayecto que esta recorriendo el asiento entre dos celdas (movimiento suave). */
    private Location seatFrom;
    private Location seatTo;

    /** Paso actual de la animacion del asiento (0 = acaba de salir, SEAT_ANIM_STEPS = llegado). */
    private int seatStep;

    /** Donde estaba parado el jugador justo antes de unirse a la arena — para devolverlo ahi al salir/morir. */
    private Location returnLocation;

    private boolean active = false;

    /** Cuantas veces comio esta partida. */
    private int score = 0;

    /** true si se eligio "Multijugador" en el menu (afecta que scoreboard se le muestra). */
    private boolean multiplayer;

    /** System.currentTimeMillis() de cuando arranco la partida, para calcular el tiempo jugado. */
    private long startTimeMillis;

    public SnakeGame(UUID playerId, Skin skin, boolean smoothMovement) {
        this.playerId = playerId;
        this.skin = skin;
        this.smoothMovement = smoothMovement;
    }

    /**
     * Inicia la partida dentro de la arena dada: elige una posicion Y direccion inicial
     * aleatorias y seguras (con espacio libre por delante en esa direccion — ver
     * Arena#findRandomSpawn, que prueba las 4 direcciones en cada celda, no solo "sur")
     * para la cabeza (bloque BARRIER invisible que solo sirve de colision; lo visible son
     * las display entities, ver {@link #spawnDisplay}), crea el asiento invisible justo
     * encima, y monta al jugador en el.
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

        // Si el jugador esta en un mundo distinto al de la arena, hay que teletransportarlo
        // ANTES de montarlo: seat.addPassenger(player) NO lo cruza de mundo por si solo (el
        // "montar" vainilla asume que ambos ya estan en el mismo mundo; si no lo estan, el
        // jugador se queda donde estaba y la partida arranca "coja" — bug real reportado).
        Location seatSpawnLocation = seatLocationFor(spawn.location());
        if (!player.getWorld().equals(seatSpawnLocation.getWorld())) {
            player.teleport(seatSpawnLocation);
        }

        this.seat = spawnSeat(spawn.location());
        seat.addPassenger(player);

        if (smoothMovement) {
            // Modo suave: la celda lleva un BARRIER invisible (solo colision) y lo que se
            // ve es la display entity, que se deslizara de celda en celda.
            spawn.location().getBlock().setType(BODY_BLOCK);

            // Asiento estatico hasta el primer movimiento (sin trayecto pendiente).
            this.seatFrom = seat.getLocation().clone();
            this.seatTo = seatFrom;
            this.seatStep = SEAT_ANIM_STEPS;

            // Primer segmento visible: solo la cabeza, hasta que la serpiente se mueva.
            displaySegments.clear();
            displaySegments.addLast(spawnDisplay(spawn.location()));
        } else {
            // Modo clasico: el bloque de la skin directamente, como siempre.
            spawn.location().getBlock().setType(skin.getMaterial());
        }
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
        seatFrom = null;
        seatTo = null;

        for (BlockDisplay display : displaySegments) {
            display.remove();
        }
        displaySegments.clear();
        displayFrom.clear();
        displayTo.clear();

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
     * true mientras dura el retardo inicial de la partida (ver START_DELAY_SECONDS): la
     * serpiente aun no se mueve, el jugador solo puede mirar a su alrededor (y pedir
     * direccion, que se aplicara al primer movimiento).
     */
    public boolean isInStartGrace() {
        return active && System.currentTimeMillis() - startTimeMillis < START_DELAY_MILLIS;
    }

    /** Segundos que quedan del retardo inicial (para el aviso al jugador). */
    public int getStartGraceRemainingSeconds() {
        long remaining = START_DELAY_MILLIS - (System.currentTimeMillis() - startTimeMillis);
        return (int) Math.max(0, (remaining + 999) / 1000);
    }

    /**
     * Avanza un paso la animacion del asiento hacia la celda destino registrada por el
     * ultimo tick. La llama el bucle de animacion del GameManager cada tick de servidor;
     * la logica de la serpiente sigue siendo de rejilla (solo se suaviza la vista del
     * jugador, que viaja encima del asiento).
     */
    public void advanceSeatAnimation() {
        if (seat == null || seatFrom == null || seatTo == null) {
            return;
        }
        seatStep++;
        if (seatStep >= SEAT_ANIM_STEPS) {
            seat.teleport(seatTo);
            seatFrom = null;
            seatTo = null;
            return;
        }
        double t = (double) seatStep / SEAT_ANIM_STEPS;
        double x = seatFrom.getX() + (seatTo.getX() - seatFrom.getX()) * t;
        double y = seatFrom.getY() + (seatTo.getY() - seatFrom.getY()) * t;
        double z = seatFrom.getZ() + (seatTo.getZ() - seatFrom.getZ()) * t;
        seat.teleport(new Location(seatFrom.getWorld(), x, y, z, seatFrom.getYaw(), seatFrom.getPitch()));
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
        if (!active || isInStartGrace() || body.isEmpty()) {
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
        // Retardo inicial: al entrar a la arena la serpiente espera unos segundos sin
        // moverse para que el jugador pueda orientarse (ver isInStartGrace).
        if (isInStartGrace()) {
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

        body.addFirst(newHead);

        if (smoothMovement) {
            newHead.getBlock().setType(BODY_BLOCK);

            // La parte visible (display entities) se desliza una celda hacia delante; la
            // logica de rejilla y las colisiones (con los BARRIER) NO cambian.
            slideDisplaySegments();

            // El asiento no se salta de golpe: se registra el tramo y lo recorre suavemente
            // el bucle de animacion (ver advanceSeatAnimation).
            this.seatFrom = seat.getLocation().clone();
            this.seatTo = seatLocationFor(newHead);
            this.seatStep = 0;
        } else {
            // Modo clasico: se pinta el bloque de la skin y el asiento salta de golpe.
            newHead.getBlock().setType(skin.getMaterial());
            seat.teleport(seatLocationFor(newHead));
        }

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

    /** Posicion de la display entity para una celda del cuerpo: la esquina inferior del bloque. */
    private Location displayLocationFor(Location cell) {
        return cell.getBlock().getLocation();
    }

    /**
     * Crea el BlockDisplay de un segmento: muestra el bloque de la skin de esta
     * serpiente en la celda dada. Es lo que se VE de la serpiente — el bloque real del
     * mundo en esa celda es un BARRIER invisible (ver {@link #BODY_BLOCK}) que solo
     * existe para las colisiones.
     */
    private BlockDisplay spawnDisplay(Location cell) {
        Location at = displayLocationFor(cell);
        return at.getWorld().spawn(at, BlockDisplay.class, display -> {
            display.setBlock(skin.getMaterial().createBlockData());
            display.setViewRange(32f);
            display.setInvulnerable(true);
            display.setSilent(true);
            display.setPersistent(false);
        });
    }

    /**
     * Tras cada tick de movimiento re-alinea los displays con el cuerpo: cada segmento se
     * desliza una celda hacia delante (el display pasa de su celda anterior a la celda de
     * su segmento actual), lo que produce el movimiento fluido de la serpiente. Si la
     * serpiente crecio este tick, aparece un display nuevo en la punta de la cola. La
     * logica de rejilla NO se toca: esto es solo la parte visible.
     */
    private void slideDisplaySegments() {
        displayFrom.clear();
        displayTo.clear();
        displayStep = 0;

        Iterator<BlockDisplay> dispIt = displaySegments.iterator();
        Iterator<Location> bodyIt = body.iterator();
        while (dispIt.hasNext() && bodyIt.hasNext()) {
            BlockDisplay display = dispIt.next();
            displayFrom.addLast(display.getLocation());
            displayTo.addLast(displayLocationFor(bodyIt.next()));
        }
        while (bodyIt.hasNext()) {
            // La serpiente crecio: aparece un segmento nuevo en la punta de la cola.
            Location cell = bodyIt.next();
            displaySegments.addLast(spawnDisplay(cell));
            Location at = displayLocationFor(cell);
            displayFrom.addLast(at);
            displayTo.addLast(at);
        }
    }

    /**
     * Avanza un paso el deslizamiento de todos los displays hacia su celda destino (ver
     * {@link #slideDisplaySegments}). La llama el bucle de animacion del GameManager cada
     * tick de servidor; los SEAT_ANIM_STEPS pasos coinciden con el intervalo de movimiento.
     */
    public void advanceDisplayAnimation() {
        if (displayFrom.isEmpty() || displayTo.isEmpty() || displaySegments.isEmpty()) {
            return;
        }
        displayStep++;
        double t = Math.min(1.0, (double) displayStep / SEAT_ANIM_STEPS);

        Iterator<BlockDisplay> dispIt = displaySegments.iterator();
        Iterator<Location> fromIt = displayFrom.iterator();
        Iterator<Location> toIt = displayTo.iterator();
        while (dispIt.hasNext() && fromIt.hasNext() && toIt.hasNext()) {
            BlockDisplay display = dispIt.next();
            Location from = fromIt.next();
            Location to = toIt.next();
            double x = from.getX() + (to.getX() - from.getX()) * t;
            double y = from.getY() + (to.getY() - from.getY()) * t;
            double z = from.getZ() + (to.getZ() - from.getZ()) * t;
            display.teleport(new Location(from.getWorld(), x, y, z));
        }

        if (displayStep >= SEAT_ANIM_STEPS) {
            displayStep = 0;
            displayFrom.clear();
            displayTo.clear();
        }
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public Skin getSkin() {
        return skin;
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
