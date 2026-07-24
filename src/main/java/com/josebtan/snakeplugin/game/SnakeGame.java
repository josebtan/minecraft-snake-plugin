package com.josebtan.snakeplugin.game;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

/**
 * Representa la partida de un jugador: su serpiente, posicion de la cabeza,
 * direccion actual y (en etapas futuras) su cola y puntuacion.
 *
 * ETAPA 1 (version 3): el jugador va montado en un ArmorStand invisible que
 * flota justo encima de la cabeza de la serpiente. Al mover el ArmorStand, el
 * jugador viaja con el automaticamente (mecanica de "montura" real de
 * Minecraft), sin necesidad de tele-transportarlo constantemente: mucho mas
 * ligero para el servidor que la version anterior.
 *
 * El ArmorStand lleva puesta lana del color de la serpiente a modo de "casco",
 * para poder identificarla desde fuera. Se descarto usar un Block Display por
 * simplicidad: un ArmorStand con equipo no necesita ninguna limpieza especial
 * mas alla de eliminarlo (remove()) al terminar la partida.
 *
 * La camara del jugador se bloquea en vista cenital (mirando hacia abajo) desde
 * SnakeInputListener, reaccionando a sus intentos de mover el raton, y el
 * movimiento se lee con las teclas WASD via PlayerInputEvent (un ArmorStand no
 * es una montura "Steerable" como un caballo, asi que sin esa API no habria
 * forma de detectar que tecla se pulsa mientras se esta montado).
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
     * Direccion solicitada por el jugador via WASD. Se aplica en el siguiente tick
     * de movimiento (no al instante), igual que en el Snake clasico, y se descarta
     * si intenta invertir la direccion actual (giro de 180 grados).
     */
    private Direction requestedDirection;

    /** Cola de segmentos del cuerpo. Vacia por ahora, se usara en la Etapa 4. */
    private final Deque<Location> tail = new ArrayDeque<>();

    /** Montura invisible (ArmorStand) sobre la que viaja el jugador. */
    private ArmorStand mount;

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

    /** Crea el ArmorStand invisible decorado con lana del color de la serpiente. */
    private ArmorStand spawnMount(Location head) {
        Location spawnAt = mountLocationFor(head);
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
            stand.getEquipment().setHelmet(new ItemStack(color.getWoolMaterial()));
        });
    }

    /** Calcula la posicion de la montura: centrada sobre la casilla y elevada. */
    private Location mountLocationFor(Location boardLocation) {
        return boardLocation.clone().add(0.5, MOUNT_HEIGHT, 0.5);
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

    public ArmorStand getMount() {
        return mount;
    }

    public boolean isActive() {
        return active;
    }
}
