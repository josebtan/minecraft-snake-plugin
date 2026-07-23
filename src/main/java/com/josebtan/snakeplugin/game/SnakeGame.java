package com.josebtan.snakeplugin.game;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

/**
 * Representa la partida de un jugador: su serpiente, posicion de la cabeza,
 * direccion actual y (en etapas futuras) su cola y puntuacion.
 *
 * ETAPA 1: solo se implementa el movimiento de la cabeza sobre una rejilla,
 * controlado por hacia donde mira el jugador. No hay todavia campo delimitado
 * (Etapa 2), ni comida/puntos (Etapa 3), ni crecimiento de cola (Etapa 4).
 */
public class SnakeGame {

    private final UUID playerId;
    private final SnakeColor color;

    /** Posicion actual de la cabeza en la rejilla (coordenadas de bloque, Y fija). */
    private Location headLocation;

    /** Direccion en la que se movera la cabeza en el proximo tick de juego. */
    private Direction currentDirection;

    /** Cola de segmentos del cuerpo. Vacia por ahora, se usara en la Etapa 4. */
    private final Deque<Location> tail = new ArrayDeque<>();

    private boolean active = false;

    public SnakeGame(UUID playerId, SnakeColor color) {
        this.playerId = playerId;
        this.color = color;
    }

    /**
     * Inicia la partida colocando la cabeza en el bloque indicado (normalmente
     * los pies del jugador redondeados a bloque) y arrancando mirando al Sur.
     */
    public void start(Location origin) {
        this.headLocation = origin.getBlock().getLocation();
        this.currentDirection = Direction.SOUTH;
        this.active = true;
        headLocation.getBlock().setType(color.getWoolMaterial());
    }

    /** Detiene la partida y limpia el bloque de la cabeza del mundo. */
    public void stop() {
        if (headLocation != null) {
            headLocation.getBlock().setType(org.bukkit.Material.AIR);
        }
        for (Location segment : tail) {
            segment.getBlock().setType(org.bukkit.Material.AIR);
        }
        tail.clear();
        active = false;
    }

    /**
     * Actualiza la direccion deseada segun hacia donde mira actualmente el jugador,
     * impidiendo que la serpiente se invierta sobre si misma (giro de 180 grados).
     */
    public void updateDirectionFromPlayerLook(Player player) {
        if (!active) {
            return;
        }
        Direction requested = Direction.fromYaw(player.getLocation().getYaw());
        if (requested != currentDirection.getOpposite()) {
            currentDirection = requested;
        }
    }

    /**
     * Avanza la cabeza una casilla en la direccion actual.
     * Por ahora simplemente mueve el bloque (borra el anterior, coloca el nuevo);
     * en la Etapa 4 aqui es donde la cola empezara a "seguir" a la cabeza.
     */
    public void tick() {
        if (!active || headLocation == null) {
            return;
        }

        Location previousHead = headLocation.clone();
        Location newHead = headLocation.clone().add(currentDirection.getDx(), 0, currentDirection.getDz());

        newHead.getBlock().setType(color.getWoolMaterial());

        // Sin cola todavia (Etapa 4): el bloque anterior se limpia directamente.
        if (tail.isEmpty()) {
            previousHead.getBlock().setType(org.bukkit.Material.AIR);
        }

        this.headLocation = newHead;
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
