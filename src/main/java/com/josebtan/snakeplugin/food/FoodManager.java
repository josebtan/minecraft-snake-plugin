package com.josebtan.snakeplugin.food;

import com.josebtan.snakeplugin.arena.Arena;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Administra la comida de cada arena: una unidad de comida a la vez por arena (estilo
 * Snake clasico), compartida por todos los jugadores que esten jugando ahi al mismo tiempo
 * (multijugador: el primero que llegue a la comida se la come).
 *
 * ETAPA 3 (v2): la comida ahora es un ITEM real tirado en el suelo (una manzana, zanahoria,
 * etc. al azar), no un bloque — visualmente mucho mas parecido a "comida" que un bloque de
 * glowstone. La deteccion de que la cabeza llego a la comida sigue siendo por COORDENADAS
 * (ver isFoodAt), no por tipo de bloque, asi que esto no afecta a la logica de choque en
 * SnakeGame#tick: un item no bloquea la casilla (el bloque ahi sigue siendo aire), por eso
 * el chequeo de "es comida" tiene que ir SIEMPRE antes que el chequeo de choque.
 *
 * Al item se le desactiva la gravedad (para que no se caiga si no hay suelo solido debajo
 * de la arena) y el envejecimiento (para que no desaparezca solo tras 5 minutos en el
 * suelo, como hace normalmente cualquier item tirado en Minecraft), y se le pone un retraso
 * de recogida altisimo para que ningun jugador se lo lleve por accidente caminando cerca —
 * la unica forma de "comerselo" es que la cabeza de una serpiente llegue exactamente a su
 * casilla.
 */
public class FoodManager {

    /** Items de comida posibles; se elige uno al azar cada vez que aparece comida nueva. */
    private static final Material[] FOOD_ITEMS = {
            Material.APPLE, Material.CARROT, Material.BREAD, Material.MELON_SLICE,
            Material.COOKED_BEEF, Material.GOLDEN_APPLE, Material.SWEET_BERRIES
    };

    /** Registro de la comida activa de una arena: su posicion en la rejilla y el item en si. */
    private record FoodEntry(Location location, Item entity) {
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

    /** Elimina el item de comida actual del mundo y coloca uno nuevo en otro sitio libre. */
    public void consumeAndRespawn(Arena arena) {
        removeCurrent(arena);
        spawn(arena);
    }

    /** Quita la comida de esa arena del registro y elimina el item del mundo si sigue vivo. */
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

        Location dropAt = cell.clone().add(0.5, 0.2, 0.5);
        Material foodType = FOOD_ITEMS[ThreadLocalRandom.current().nextInt(FOOD_ITEMS.length)];
        Item item = dropAt.getWorld().dropItem(dropAt, new ItemStack(foodType));

        item.setVelocity(new Vector(0, 0, 0));
        item.setGravity(false);
        item.setWillAge(false);         // que no desaparezca solo tras un rato en el suelo
        item.setPickupDelay(Short.MAX_VALUE); // que ningun jugador se lo lleve caminando cerca
        item.setInvulnerable(true);
        item.setGlowing(true);          // para que se distinga bien del suelo/decoracion

        foodByArena.put(key(arena), new FoodEntry(cell, item));
    }

    private static String key(Arena arena) {
        return arena.getName().toLowerCase();
    }
}
