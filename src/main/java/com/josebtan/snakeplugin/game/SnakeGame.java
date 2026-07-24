package com.josebtan.snakeplugin.game;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

/**
 * Representa la partida de un jugador: su serpiente, posicion de la cabeza,
 * direccion actual y (en etapas futuras) su cola y puntuacion.
 *
 * ETAPA 1 (version 6): el jugador va montado en un CABALLO invisible, que
 * flota justo encima de la cabeza de la serpiente y viaja con ella.
 *
 * Historial de por que se llego a esta montura concreta:
 *  - Un ArmorStand no es una montura "Steerable": Minecraft no envia ninguna
 *    senal de las teclas WASD para monturas que no sean caballo/burro/mula/
 *    camello/cerdo/strider/bote, sin importar la version del servidor.
 *  - Se probo primero con un CERDO con silla, pero en Minecraft vanilla un
 *    cerdo montado SOLO se mueve si el jinete lleva en la mano una "zanahoria
 *    en un palo" (item especial) — sin ese item, WASD no le hace nada. Por
 *    eso el jugador no podia moverse: no era un bug del plugin, era una
 *    limitacion del propio juego para ese tipo de montura.
 *  - Un CABALLO domado y ensillado SI se mueve directamente con WASD, sin
 *    necesitar ningun item extra en la mano — de ahi el cambio.
 *
 * En cuanto se detecta hacia donde intenta moverse el caballo (VehicleMoveEvent,
 * ver SnakeInputListener), se vuelve a anclar a su casilla en la rejilla: no se
 * le deja moverse libremente, solo en pasos discretos (SnakeGame#tick).
 *
 * La montura se mantiene INVISIBLE a proposito: la identificacion visual de
 * "de quien es cada serpiente" viene del bloque de lana de color que se ve en
 * el tablero (la cabeza), no de la propia montura.
 *
 * Sigue sin haber campo delimitado (Etapa 2), comida/puntos (Etapa 3), ni
 * crecimiento de cola (Etapa 4).
 */
public class SnakeGame {

    /** Cuantos bloques por encima del tablero flota la montura (y por tanto el jugador). */
    private static final double MOUNT_HEIGHT = 10.0;

    /** Yaw fijo mientras dura la partida (el valor exacto no importa, solo que sea constante). */
    public static final float LOCKED_YAW = 0f;

    /** Pitch fijo: en Minecraft, +90 = mirando directamente hacia abajo (vista cenital). */
    public static final float LOCKED_PITCH = 90f;

    private final UUID playerId;
    private final SnakeColor color;

    /** Posicion actual de la cabeza en la rejilla (coordenadas de bloque, Y fija = "boardY"). */
    private Location headLocation;

    /** Direccion en la que se esta moviendo la cabeza actualmente. */
    private Direction currentDirection;

    /**
     * Direccion solicitada por el jugador via WASD (detectada por como intenta
     * mover la montura). Se aplica en el siguiente tick de movimiento (no al
     * instante), igual que en el Snake clasico, y se descarta si intenta
     * invertir la direccion actual (giro de 180 grados).
     */
    private Direction requestedDirection;

    /** Cola de segmentos del cuerpo. Vacia por ahora, se usara en la Etapa 4. */
    private final Deque<Location> tail = new ArrayDeque<>();

    /** Montura invisible (caballo domado y ensillado) sobre la que viaja el jugador. */
    private Horse mount;

    private boolean active = false;

    public SnakeGame(UUID playerId, SnakeColor color) {
        this.playerId = playerId;
        this.color = color;
    }

    /**
     * Inicia la partida: fija la vista del jugador ANTES de montarlo (para evitar
     * tele-transportarlo estando ya montado, lo cual puede provocar que se
     * desmonte en algunas versiones del servidor), coloca la cabeza en el bloque
     * bajo el jugador, crea la montura invisible por encima, y lo monta en ella.
     */
    public void start(Player player) {
        this.headLocation = player.getLocation().getBlock().getLocation();
        this.currentDirection = Direction.SOUTH;
        this.requestedDirection = currentDirection;
        this.active = true;

        // Fijamos la vista MIENTRAS AUN ESTA DE PIE, no despues de montarlo.
        Location fixedView = player.getLocation().clone();
        fixedView.setYaw(LOCKED_YAW);
        fixedView.setPitch(LOCKED_PITCH);
        player.teleport(fixedView);

        headLocation.getBlock().setType(color.getWoolMaterial());

        this.mount = spawnMount(headLocation);
        mount.addPassenger(player);
    }

    /** Crea el caballo invisible, domado y ensillado (para que responda a WASD sin items extra). */
    private Horse spawnMount(Location head) {
        Location spawnAt = mountLocationFor(head);
        return spawnAt.getWorld().spawn(spawnAt, Horse.class, horse -> {
            horse.setAdult();
            horse.setTamed(true);                 // imprescindible: un caballo no domado ignora al jinete
            horse.getInventory().setSaddle(new ItemStack(Material.SADDLE)); // imprescindible para poder dirigirlo
            horse.setInvisible(true);
            horse.setGravity(false);
            horse.setInvulnerable(true);
            horse.setSilent(true);
            horse.setCollidable(false);
            horse.setPersistent(false);
            horse.setJumpStrength(0.0); // evita que salte solo al pulsar espacio
        });
    }

    /** Calcula la posicion de la montura: centrada sobre la casilla y elevada. */
    private Location mountLocationFor(Location boardLocation) {
        return boardLocation.clone().add(0.5, MOUNT_HEIGHT, 0.5);
    }

    /**
     * Vuelve a anclar la montura a su posicion correcta en la rejilla. Se llama
     * desde SnakeInputListener justo despues de detectar hacia donde intento
     * moverse el jugador, para impedir que el caballo se desplace libremente
     * (solo debe moverse en pasos discretos, en el tick de movimiento).
     */
    public void reanchorMount() {
        if (active && mount != null && headLocation != null) {
            mount.teleport(mountLocationFor(headLocation));
        }
    }

    /**
     * Detiene la partida: expulsa al jugador de la montura y la elimina, limpia
     * los bloques del tablero, y deja al jugador de pie a salvo con la vista
     * liberada. 'active' se pone a false ANTES de expulsarlo para que el
     * listener que evita el desmontaje voluntario sepa que este es intencional.
     */
    public void stop(Player player) {
        active = false;

        if (mount != null) {
            mount.eject();
            mount.remove();
            mount = null;
        }

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
     * Registra la direccion pedida por el jugador (detectada por como intenta
     * mover la montura con WASD). Se ignora si supone invertir la direccion
     * actual de golpe.
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
     * Avanza la cabeza una casilla en la direccion solicitada, y mueve la montura;
     * como el jugador es su pasajero, viaja con ella automaticamente.
     * En la Etapa 4 aqui es donde la cola empezara a "seguir" a la cabeza.
     */
    public void tick() {
        if (!active || headLocation == null || mount == null) {
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
        mount.teleport(mountLocationFor(newHead));
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

    public Horse getMount() {
        return mount;
    }

    public boolean isActive() {
        return active;
    }
}
