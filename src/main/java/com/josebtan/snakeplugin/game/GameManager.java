package com.josebtan.snakeplugin.game;

import com.josebtan.snakeplugin.arena.Arena;
import com.josebtan.snakeplugin.food.FoodManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Administra todas las partidas de snake activas en el servidor y el bucle de
 * movimiento que las hace avanzar.
 *
 * A diferencia de versiones anteriores, ya NO hay un bucle aparte "de camara"
 * que reteleporte al jugador cada tick de servidor: al ir sentado en un
 * ArmorStand invisible pegado al bloque de la cabeza (ver SnakeGame), su
 * posicion la resuelve el propio motor de Minecraft de forma gratuita al
 * mover el asiento, y la camara del jugador queda completamente libre (sin
 * bloqueo). Esto reduce bastante la carga sobre el servidor comparado con el
 * primer enfoque (tele-transportar al jugador 20 veces por segundo).
 *
 * ETAPA 2: startGame ahora exige una Arena (campo delimitado), y el bucle de
 * movimiento termina automaticamente la partida de cualquier jugador cuya
 * serpiente choque (ver SnakeGame#tick).
 *
 * ETAPA 3/4: cada Arena tiene su propia comida (ver FoodManager, compartida por todos
 * los jugadores que jueguen ahi), y startGame se asegura de que haya una activa al
 * arrancar una partida. El bucle de movimiento ahora reacciona a los tres resultados
 * posibles de un tick (vivo / comio / choco) en vez de un simple booleano.
 */
public class GameManager {

    /** Cada cuantos ticks de servidor avanza la serpiente una casilla (20 ticks = 1s). */
    private static final long MOVE_INTERVAL_TICKS = 8L; // ~0.4s por movimiento

    private final Plugin plugin;
    private final Map<UUID, SnakeGame> games = new ConcurrentHashMap<>();
    private final FoodManager foodManager = new FoodManager();
    private BukkitTask movementTask;

    public GameManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public FoodManager getFoodManager() {
        return foodManager;
    }

    /**
     * Crea e inicia una partida nueva para el jugador dentro de la arena dada, con el color
     * elegido en el menu (ver com.josebtan.snakeplugin.gui), si no tiene ya una activa.
     * Devuelve null si la arena no tiene ningun punto libre donde aparecer (ver
     * Arena#findRandomSpawn / SnakeGame#start) — en ese caso no se crea nada.
     */
    public SnakeGame startGame(Player player, Arena arena, SnakeColor color) {
        UUID id = player.getUniqueId();
        if (games.containsKey(id)) {
            return games.get(id);
        }

        SnakeGame game = new SnakeGame(id, color);
        if (!game.start(player, arena)) {
            return null;
        }
        games.put(id, game);
        foodManager.ensureFoodSpawned(arena);

        ensureLoopRunning();
        return game;
    }

    /**
     * Colores ya en uso por partidas activas EN ESA MISMA ARENA (para el menu de seleccion
     * de color en modo multijugador: ver com.josebtan.snakeplugin.gui.ColorMenu).
     */
    public Set<SnakeColor> getColorsInUse(Arena arena) {
        Set<SnakeColor> inUse = new HashSet<>();
        for (SnakeGame game : games.values()) {
            Arena gameArena = game.getArena();
            if (gameArena != null && gameArena.getName().equalsIgnoreCase(arena.getName())) {
                inUse.add(game.getColor());
            }
        }
        return inUse;
    }

    /** Detiene y elimina la partida del jugador, si existe (salida voluntaria). */
    public void stopGame(Player player) {
        SnakeGame game = games.remove(player.getUniqueId());
        if (game != null) {
            Arena arena = game.getArena();
            game.stop(player);
            clearFoodIfArenaEmpty(arena);
        }
        if (games.isEmpty()) {
            stopLoop();
        }
    }

    public SnakeGame getGame(Player player) {
        return games.get(player.getUniqueId());
    }

    public boolean hasGame(Player player) {
        return games.containsKey(player.getUniqueId());
    }

    /** Llamado desde el listener de input (WASD) cuando el jugador pide girar. */
    public void requestDirection(Player player, Direction direction) {
        SnakeGame game = games.get(player.getUniqueId());
        if (game != null) {
            game.requestDirection(direction);
        }
    }

    private void ensureLoopRunning() {
        if (movementTask == null) {
            movementTask = Bukkit.getScheduler()
                    .runTaskTimer(plugin, this::tickMovement, MOVE_INTERVAL_TICKS, MOVE_INTERVAL_TICKS);
        }
    }

    private void stopLoop() {
        if (movementTask != null) {
            movementTask.cancel();
            movementTask = null;
        }
    }

    /**
     * Si ya no queda ninguna partida activa en esa arena, limpia su comida (el item se
     * elimina del mundo). Se llama tras cualquier forma de terminar una partida (salida
     * voluntaria o choque) — antes esto no se hacia nunca, por lo que la comida (antes un
     * bloque de glowstone) se quedaba abandonada en el mundo para siempre.
     */
    private void clearFoodIfArenaEmpty(Arena arena) {
        if (arena == null) {
            return;
        }
        boolean stillInUse = games.values().stream()
                .anyMatch(g -> g.getArena() != null && g.getArena().getName().equalsIgnoreCase(arena.getName()));
        if (!stillInUse) {
            foodManager.clear(arena);
        }
    }

    /**
     * Se ejecuta cada MOVE_INTERVAL_TICKS: mueve cada cabeza (y su asiento, con el jugador
     * encima) una casilla. Reacciona al TickResult de SnakeGame#tick:
     *   - COLLIDED: esa serpiente acaba de chocar, se termina su partida aqui mismo.
     *   - ATE: comio y crecio, se avisa del punto (action bar, para no llenar el chat en
     *     partidas donde se come seguido).
     *   - ALIVE: nada especial que hacer.
     */
    private void tickMovement() {
        for (SnakeGame game : games.values()) {
            TickResult result = game.tick(foodManager);
            Player player = Bukkit.getPlayer(game.getPlayerId());

            switch (result) {
                case COLLIDED -> {
                    games.remove(game.getPlayerId());
                    game.stop(player);
                    clearFoodIfArenaEmpty(game.getArena());
                    if (player != null) {
                        player.sendMessage(Component.text(
                                "¡Chocaste! Tu serpiente ha muerto. Puntos: " + game.getScore(),
                                NamedTextColor.RED));
                    }
                }
                case ATE -> {
                    if (player != null) {
                        player.sendActionBar(Component.text(
                                "¡Comiste! Puntos: " + game.getScore() + " | Largo: " + game.getLength(),
                                NamedTextColor.GOLD));
                    }
                }
                case ALIVE -> {
                    // Nada que hacer.
                }
            }
        }
        if (games.isEmpty()) {
            stopLoop();
        }
    }

    /** Detiene todas las partidas activas y limpia toda la comida, por ejemplo al desactivar el plugin. */
    public void stopAll() {
        Set<Arena> arenas = new HashSet<>();
        for (SnakeGame game : games.values()) {
            Player player = Bukkit.getPlayer(game.getPlayerId());
            if (game.getArena() != null) {
                arenas.add(game.getArena());
            }
            game.stop(player);
        }
        games.clear();
        for (Arena arena : arenas) {
            foodManager.clear(arena);
        }
        stopLoop();
    }

    // ==== DIAGNOSTICO: metodos nuevos SIN CONECTAR TODAVIA (dead code a proposito) ====

    private Set<UUID> resolveHeadOnCollisions() {
        Map<Arena, List<SnakeGame>> byArena = new HashMap<>();
        for (SnakeGame game : games.values()) {
            Arena arena = game.getArena();
            if (arena != null) {
                byArena.computeIfAbsent(arena, a -> new ArrayList<>()).add(game);
            }
        }

        Set<UUID> losers = new HashSet<>();
        for (List<SnakeGame> arenaGames : byArena.values()) {
            if (arenaGames.size() < 2) {
                continue;
            }

            Map<String, List<SnakeGame>> targets = new HashMap<>();
            for (SnakeGame game : arenaGames) {
                Location next = game.peekNextHead();
                if (next != null) {
                    targets.computeIfAbsent(locationKey(next), k -> new ArrayList<>()).add(game);
                }
            }
            for (List<SnakeGame> contenders : targets.values()) {
                if (contenders.size() > 1) {
                    markLosers(contenders, losers);
                }
            }

            for (int i = 0; i < arenaGames.size(); i++) {
                for (int j = i + 1; j < arenaGames.size(); j++) {
                    SnakeGame a = arenaGames.get(i);
                    SnakeGame b = arenaGames.get(j);
                    Location aNext = a.peekNextHead();
                    Location bNext = b.peekNextHead();
                    if (aNext == null || bNext == null) {
                        continue;
                    }
                    if (sameLocation(aNext, b.getHeadLocation()) && sameLocation(bNext, a.getHeadLocation())) {
                        markLosers(List.of(a, b), losers);
                    }
                }
            }
        }
        return losers;
    }

    private void markLosers(List<SnakeGame> contenders, Set<UUID> losers) {
        int maxLength = 0;
        for (SnakeGame game : contenders) {
            maxLength = Math.max(maxLength, game.getLength());
        }
        long winnersCount = contenders.stream().filter(g -> g.getLength() == maxLength).count();
        if (winnersCount > 1) {
            for (SnakeGame game : contenders) {
                losers.add(game.getPlayerId());
            }
        } else {
            for (SnakeGame game : contenders) {
                if (game.getLength() != maxLength) {
                    losers.add(game.getPlayerId());
                }
            }
        }
    }

    private void broadcastToArena(Arena arena, Player exclude, Component message) {
        if (arena == null) {
            return;
        }
        for (SnakeGame game : games.values()) {
            Arena gameArena = game.getArena();
            if (gameArena == null || !gameArena.getName().equalsIgnoreCase(arena.getName())) {
                continue;
            }
            Player recipient = Bukkit.getPlayer(game.getPlayerId());
            if (recipient != null && !recipient.equals(exclude)) {
                recipient.sendMessage(message);
            }
        }
    }

    private static String locationKey(Location location) {
        return location.getWorld().getName() + ":" + location.getBlockX() + ":"
                + location.getBlockY() + ":" + location.getBlockZ();
    }

    private static boolean sameLocation(Location a, Location b) {
        return a.getWorld().equals(b.getWorld())
                && a.getBlockX() == b.getBlockX()
                && a.getBlockY() == b.getBlockY()
                && a.getBlockZ() == b.getBlockZ();
    }
}
