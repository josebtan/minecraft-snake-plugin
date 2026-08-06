package com.josebtan.snakeplugin.food;

import com.josebtan.snakeplugin.arena.Arena;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Administra la comida de cada arena: una unidad de comida a la vez por arena (estilo
 * Snake clasico), compartida por todos los jugadores que esten jugando ahi al mismo tiempo
 * (multijugador: el primero que llegue a la comida se la come).
 *
 * ETAPA 5 (comida como ItemDisplay): antes la comida era un ITEM real tirado en el suelo
 * (una manzana, zanahoria, etc. al azar). Tenia dos problemas:
 *   1. Los items sueltos son pequenos y el cliente los deja de renderizar a poca distancia
 *      ("solo se ve la comida estando encima de ella"). Como la camara es libre y la arena
 *      puede ser grande, eso hacia la comida dificil de localizar.
 *   2. Un item suelto necesita trucos (gravedad off, pickup delay, invulnerable...) para no
 *      caerse, desaparecer o ser recogido por accidente.
 * Ahora la comida es una entidad ItemDisplay: un item 3D anclado en su casilla, que se puede
 * ESCALAR (Transformation) para que se vea bien grande, se le puede subir el rango de
 * renderizado (setViewRange) para que se vea desde lejos, y no se puede recoger de ninguna
 * forma (no es un item fisico).
 *
 * La deteccion de que la cabeza llego a la comida sigue siendo por COORDENADAS (ver
 * isFoodAt), no por tipo de bloque, asi que esto no afecta a la logica de choque en
 * SnakeGame#tick: un ItemDisplay no bloquea la casilla (el bloque ahi sigue siendo aire),
 * por eso el chequeo de "es comida" tiene que ir SIEMPRE antes que el chequeo de choque.
 */
public class FoodManager {

    /** Items de comida posibles; se elige uno al azar cada vez que aparece comida nueva. */
    private static final Material[] FOOD_ITEMS = {
            Material.APPLE, Material.CARROT, Material.BREAD, Material.MELON_SLICE,
            Material.COOKED_BEEF, Material.GOLDEN_APPLE, Material.SWEET_BERRIES
    };

    /** Escala de la comida (un item normal mide ~1 bloque; 0.8 lo deja bien grande y visible). */
    private static final float FOOD_SCALE = 0.8f;

    /**
     * Rango de renderizado del ItemDisplay. Un ItemDisplay vanilla usa view_range=1.0, que
     * se culea (deja de renderizar) a muy poca distancia — eso era en gran parte lo que
     * hacia que la comida "solo se viera de cerca". Subirlo a 32 hace que se vea desde
     * cualquier punto de la arena (el corte real lo da el render distance del servidor).
     */
    private static final float FOOD_VIEW_RANGE = 32f;

    /** Cuanto flota la comida sobre su casilla (para que no quede incrustada en el suelo). */
    private static final double FOOD_FLOAT_Y = 0.35;

    /** Registro de la comida activa de una arena: su posicion en la rejilla y el ItemDisplay en si. */
    private record FoodEntry(Location location, ItemDisplay entity) {
    }

    /** Comida actual por arena (clave: nombre de la arena en minusculas). */
    private final Map<String, FoodEntry> foodByArena = new ConcurrentHashMap<>();

    /** true si la casilla dada es exactamente la comida actual de esa arena. */
    public boolean isFoodAt(Arena arena, Location location) {
        if (arena == null || location == null) {
            return false;
        }
        FoodEntry entry = foodByArena.get(key(arena));
        if (entry == null) {
            return false;
        }
        Location food = entry.location();
        return food.getWorld().equals(location.getWorld())
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

    /** Elimina la comida actual del mundo y coloca una nueva en otro sitio libre. */
    public void consumeAndRespawn(Arena arena) {
        removeCurrent(arena);
        spawn(arena);
    }

    /** Quita la comida de esa arena del registro y elimina el ItemDisplay del mundo si sigue vivo. */
    public void clear(Arena arena) {
        removeCurrent(arena);
    }

    private void removeCurrent(Arena arena) {
        FoodEntry entry = foodByArena.remove(key(arena));
        if (entry != null && entry.entity() != null && !entry.entity().isDead()) {
            entry.entity().remove();
        }
    }

    private void spawn(Arena arena) {
        Location cell = arena.findRandomFreeCell();
        if (cell == null) {
            // Arena llena/decorada: no hay sitio libre ahora mismo. Se reintentara la
            // proxima vez que se llame a ensureFoodSpawned/consumeAndRespawn.
            return;
        }

        Material foodType = FOOD_ITEMS[ThreadLocalRandom.current().nextInt(FOOD_ITEMS.length)];
        Location displayAt = cell.clone().add(0.5, FOOD_FLOAT_Y, 0.5);

        ItemDisplay display = displayAt.getWorld().spawn(displayAt, ItemDisplay.class, e -> {
            e.setItemStack(new ItemStack(foodType));
            e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            e.setInvulnerable(true);
            e.setSilent(true);
            e.setPersistent(true);
            e.setGlowing(true);
            e.setGlowColorOverride(Color.YELLOW);
            e.setViewRange(FOOD_VIEW_RANGE);
            e.setTransformation(new Transformation(
                    new Vector3f(),        // sin desplazamiento extra
                    new Quaternionf(),     // sin rotacion izquierda
                    new Vector3f(FOOD_SCALE, FOOD_SCALE, FOOD_SCALE), // escala uniforme
                    new Quaternionf()      // sin rotacion derecha
            ));
        });

        foodByArena.put(key(arena), new FoodEntry(cell, display));
    }

    private static String key(Arena arena) {
        return arena.getName().toLowerCase();
    }
}
