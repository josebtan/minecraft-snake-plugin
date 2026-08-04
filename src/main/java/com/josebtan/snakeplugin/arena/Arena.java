package com.josebtan.snakeplugin.arena;

import com.josebtan.snakeplugin.game.Direction;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Zona de juego: un rectangulo en el plano X/Z, a una altura fija ("boardY"). Define DONDE
 * pueden aparecer los jugadores y la comida — nada mas.
 *
 * IMPORTANTE: la arena ya NO construye paredes ni modifica el mundo de ninguna forma. Eso
 * es cosa del propio jugador: puede decorar/delimitar su arena como quiera (paredes, fosos,
 * decoracion tematica, lo que sea), y esos bloques funcionaran igual como obstaculos porque
 * la deteccion de choques de la serpiente (ver SnakeGame#tick) es deliberadamente simple:
 * "¿el bloque de destino es AIRE?" — si no lo es, hay choque, sea lo que sea ese bloque
 * (pared construida a mano, cola propia, cola de otro jugador) es choque, salvo la
 * comida (ver com.josebtan.snakeplugin.food.FoodManager y SnakeGame#tick), que se
 * reconoce como caso especial antes de llegar a ese chequeo.
 */
public class Arena {

    private final String name;
    private final World world;
    private final int minX;
    private final int maxX;
    private final int minZ;
    private final int maxZ;
    private final int boardY;

    public Arena(String name, World world, int minX, int maxX, int minZ, int maxZ, int boardY) {
        this.name = name;
        this.world = world;
        this.minX = minX;
        this.maxX = maxX;
        this.minZ = minZ;
        this.maxZ = maxZ;
        this.boardY = boardY;
    }

    /**
     * Busca un punto y una direccion inicial validos para aparecer: la celda debe ser
     * aire, Y ademas debe haber al menos {@code clearance} casillas libres por delante en
     * ESA direccion — para no arrancar mirando de frente contra algo (una pared que el
     * jugador construyo, el borde de un pasillo angosto, etc.) y chocar en el primer paso.
     *
     * A diferencia de la version anterior (direccion siempre fija a "sur", con un numero
     * limitado de intentos al azar), esto escanea TODAS las celdas de la arena Y las 4
     * direcciones posibles en cada una, y elige al azar entre TODAS las combinaciones
     * validas que encuentre. Esto hace falta para arenas con formas irregulares: un
     * pasillo angosto puede no tener NINGUNA celda con espacio libre hacia el sur, pero si
     * tener de sobra mirando hacia el este, por ejemplo — con una direccion fija eso se
     * perdia por completo aunque la arena tuviera sitio de sobra.
     *
     * @return la celda y direccion elegidas, o null si la arena no tiene NINGUNA
     *         combinacion (celda, direccion) valida ahora mismo.
     */
    public ArenaSpawn findRandomSpawn(int clearance) {
        List<ArenaSpawn> candidates = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (world.getBlockAt(x, boardY, z).getType() != Material.AIR) {
                    continue;
                }
                for (Direction direction : Direction.values()) {
                    if (hasClearPath(x, z, direction, clearance)) {
                        candidates.add(new ArenaSpawn(new Location(world, x, boardY, z), direction));
                    }
                }
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }
        return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
    }

    /** Comprueba que la casilla de partida y las siguientes {@code clearance} en esa direccion sean aire. */
    private boolean hasClearPath(int x, int z, Direction direction, int clearance) {
        for (int i = 0; i <= clearance; i++) {
            int checkX = x + direction.getDx() * i;
            int checkZ = z + direction.getDz() * i;
            if (world.getBlockAt(checkX, boardY, checkZ).getType() != Material.AIR) {
                return false;
            }
        }
        return true;
    }

    /**
     * Busca una casilla libre (aire) al azar dentro de la arena — usado para la comida, que
     * no necesita "holgura" en ninguna direccion como el spawn del jugador, solo un sitio
     * vacio.
     *
     * IMPORTANTE: a diferencia de la version anterior (que probaba un numero limitado de
     * posiciones al azar y se rendia si no acertaba), esto ahora escanea TODAS las celdas
     * de la arena y elige una libre al azar entre las que encuentre. Es mas caro por
     * llamada, pero solo se usa al (re)aparecer la comida (no en cada tick), y evita que en
     * arenas con varias serpientes (mas casillas ocupadas) la comida simplemente no
     * apareciera por mala suerte con pocos intentos — que es justo lo que pasaba antes en
     * partidas con varios jugadores.
     *
     * @return una ubicacion libre elegida al azar, o null si la arena esta completamente llena.
     */
    public Location findRandomFreeCell() {
        List<Location> free = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (world.getBlockAt(x, boardY, z).getType() == Material.AIR) {
                    free.add(new Location(world, x, boardY, z));
                }
            }
        }
        if (free.isEmpty()) {
            return null;
        }
        return free.get(ThreadLocalRandom.current().nextInt(free.size()));
    }

    public String getName() {
        return name;
    }

    public World getWorld() {
        return world;
    }

    public int getBoardY() {
        return boardY;
    }

    public int getMinX() {
        return minX;
    }

    public int getMaxX() {
        return maxX;
    }

    public int getMinZ() {
        return minZ;
    }

    public int getMaxZ() {
        return maxZ;
    }

    /** Resultado de findRandomSpawn: la celda elegida junto con la direccion que tenia despejada. */
    public record ArenaSpawn(Location location, Direction direction) {
    }
}
