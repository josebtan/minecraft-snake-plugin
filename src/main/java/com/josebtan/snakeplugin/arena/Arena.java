package com.josebtan.snakeplugin.arena;

import com.josebtan.snakeplugin.game.Direction;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

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
     * Busca un punto aleatorio dentro de la arena donde sea seguro aparecer: el bloque de
     * esa casilla debe ser aire, Y ademas debe haber al menos {@code clearance} casillas
     * libres por delante en la direccion en la que la serpiente arrancara a moverse — para
     * no chocar contra algo (por ejemplo una pared que el jugador construyo pegada al borde)
     * justo al entrar. Reintenta hasta {@code maxAttempts} veces antes de rendirse.
     *
     * @return la ubicacion elegida, o null si no se encontro ningun sitio libre.
     */
    public Location findRandomSpawn(Direction initialDirection, int clearance, int maxAttempts) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            int x = minX + random.nextInt(maxX - minX + 1);
            int z = minZ + random.nextInt(maxZ - minZ + 1);
            if (hasClearPath(x, z, initialDirection, clearance)) {
                return new Location(world, x, boardY, z);
            }
        }
        return null;
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
     * Busca una unica casilla aleatoria libre (aire) dentro de la arena — usado para la
     * comida, que no necesita "holgura" en ninguna direccion como el spawn del
     * jugador, solo un sitio vacio. Reintenta hasta {@code maxAttempts} veces.
     *
     * @return la ubicacion elegida, o null si no se encontro ninguna casilla libre.
     */
    public Location findRandomFreeCell(int maxAttempts) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            int x = minX + random.nextInt(maxX - minX + 1);
            int z = minZ + random.nextInt(maxZ - minZ + 1);
            Location candidate = new Location(world, x, boardY, z);
            if (candidate.getBlock().getType() == Material.AIR) {
                return candidate;
            }
        }
        return null;
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
}
