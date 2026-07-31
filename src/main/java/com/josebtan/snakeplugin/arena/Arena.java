package com.josebtan.snakeplugin.arena;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

/**
 * Zona de juego delimitada: un rectangulo en el plano X/Z, a una altura fija ("boardY"),
 * rodeado por paredes solidas.
 *
 * La deteccion de choques de la serpiente (ver SnakeGame#tick) es deliberadamente simple:
 * "¿el bloque de destino es AIRE?" — si no lo es, hay choque. Estas paredes existen
 * precisamente para que ese unico chequeo sirva tambien como limite del campo de juego, sin
 * necesitar logica aparte: son solo mas bloques solidos, igual que la cola propia de la
 * serpiente o la de otro jugador (Etapa 4) o, mas adelante, la comida/power-ups (Etapa 3),
 * que tendran que tratarse como caso especial de ese mismo chequeo en vez de como choque.
 */
public class Arena {

    /** Material solido usado para las paredes perimetrales. */
    private static final Material WALL_MATERIAL = Material.STONE_BRICKS;

    /** Cuantos bloques de alto tiene la pared por encima del nivel de juego (solo visual/contencion). */
    private static final int WALL_HEIGHT = 3;

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
     * Construye la arena en el mundo: limpia el interior a aire (nivel de juego y el bloque
     * de encima, para que quepa la cabeza del jugador) y levanta las paredes perimetrales.
     * Se puede volver a llamar para "reconstruir" la arena si algo la destruyo.
     */
    public void build() {
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                boolean isWall = (x == minX || x == maxX || z == minZ || z == maxZ);
                if (isWall) {
                    for (int y = boardY; y < boardY + WALL_HEIGHT; y++) {
                        world.getBlockAt(x, y, z).setType(WALL_MATERIAL);
                    }
                } else {
                    world.getBlockAt(x, boardY, z).setType(Material.AIR);
                    world.getBlockAt(x, boardY + 1, z).setType(Material.AIR);
                }
            }
        }
    }

    /** Punto central de la arena, a nivel de juego — ahi aparece cada serpiente al unirse. */
    public Location centerBlockLocation() {
        int centerX = minX + (maxX - minX) / 2;
        int centerZ = minZ + (maxZ - minZ) / 2;
        return new Location(world, centerX, boardY, centerZ);
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
