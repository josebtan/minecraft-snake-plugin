package com.josebtan.snakeplugin.food;

import com.josebtan.snakeplugin.arena.Arena;
import org.bukkit.Location;
import org.bukkit.Material;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Administra la comida de cada arena: una unidad de comida a la vez por arena (estilo
 * Snake clasico), compartida por todos los jugadores que esten jugando ahi al mismo tiempo
 * (multijugador: el primero que llegue a la comida se la come).
 *
 * La comida es un bloque real y visible en el mundo (ver FOOD_MATERIAL) — por eso la
 * deteccion de choques de SnakeGame#tick puede reconocerla con el mismo chequeo de
 * siempre ("¿que bloque hay en la casilla de destino?"), solo que en este caso el
 * resultado es "comer" en vez de "chocar". Ver SnakeGame#tick para el detalle exacto de
 * como se combinan ambos chequeos.
 */
public class FoodManager {

    /** Material del bloque de comida. Distinto de cualquier lana de serpiente, para que se note a simple vista. */
    private static final Material FOOD_MATERIAL = Material.GLOWSTONE;

    /** Cuantos intentos de posicion aleatoria se prueban antes de rendirse al buscar sitio para la comida. */
    private static final int SPAWN_MAX_ATTEMPTS = 40;

    /** Comida actual por arena (clave: nombre de la arena en minusculas). */
    private final Map<String, Location> foodByArena = new ConcurrentHashMap<>();

    public Material getFoodMaterial() {
        return FOOD_MATERIAL;
    }

    /** true si la casilla dada es exactamente la comida actual de esa arena. */
    public boolean isFoodAt(Arena arena, Location location) {
        if (arena == null || location == null) {
            return false;
        }
        Location food = foodByArena.get(key(arena));
        return food != null
                && food.getWorld().equals(location.getWorld())
                && food.getBlockX() == location.getBlockX()
                && food.getBlockY() == location.getBlockY()
                && food.getBlockZ() == location.getBlockZ();
    }

    /** Si esa arena todavia no tiene comida activa, busca un sitio libre y la coloca. */
    public void ensureFoodSpawned(Arena arena) {
        if (!foodByArena.containsKey(key(arena))) {
            spawn(arena);
        }
    }

    /**
     * Quita el registro de la comida actual (el bloque en si lo sobreescribe la propia
     * cabeza de la serpiente que la comio, no hace falta tocarlo aqui) y coloca una nueva
     * en otro sitio libre de la misma arena.
     */
    public void consumeAndRespawn(Arena arena) {
        foodByArena.remove(key(arena));
        spawn(arena);
    }

    /** Quita la comida de esa arena del registro y, si sigue siendo el bloque de comida, del mundo. */
    public void clear(Arena arena) {
        Location food = foodByArena.remove(key(arena));
        if (food != null && food.getBlock().getType() == FOOD_MATERIAL) {
            food.getBlock().setType(Material.AIR);
        }
    }

    private void spawn(Arena arena) {
        Location location = arena.findRandomFreeCell(SPAWN_MAX_ATTEMPTS);
        if (location == null) {
            // Arena llena/decorada: no hay sitio libre ahora mismo. Se reintentara la
            // proxima vez que se llame a ensureFoodSpawned/consumeAndRespawn.
            return;
        }
        location.getBlock().setType(FOOD_MATERIAL);
        foodByArena.put(key(arena), location);
    }

    private static String key(Arena arena) {
        return arena.getName().toLowerCase();
    }
}
