package com.josebtan.snakeplugin.game;

import com.josebtan.snakeplugin.arena.Arena;
import com.josebtan.snakeplugin.food.FoodManager;
import com.josebtan.snakeplugin.player.PlayerRecordManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Administra todas las partidas de snake activas en el servidor y los dos bucles que las
 * mantienen: uno de MOVIMIENTO (cada 8 ticks) y otro de MARCADOR (cada 20 ticks / 1s, para
 * que el cronometro del modo un jugador se vea contar en vivo).
 *
 * A diferencia de versiones anteriores, ya NO hay un bucle "de camara" que reteleporte al
 * jugador cada tick de servidor: al ir sentado en un ArmorStand invisible pegado al bloque
 * de la cabeza (ver SnakeGame), su posicion la resuelve el propio motor de Minecraft de
 * forma gratuita al mover el asiento, y la camara del jugador queda completamente libre.
 *
 * ETAPA 2: startGame ahora exige una Arena (campo delimitado), y el bucle de movimiento
 * termina automaticamente la partida de cualquier jugador cuya serpiente choque.
 *
 * ETAPA 3/4: cada Arena tiene su propia comida (ver FoodManager, compartida por todos los
 * jugadores que jueguen ahi), y startGame se asegura de que haya una activa al arrancar.
 *
 * ANALISIS DE FLUJO: se agregaron choque de frente (gana la mas larga), aviso a toda la
 * arena al chocar, y un marcador lateral en vivo — que ahora es DISTINTO segun el modo
 * elegido en el menu (ver SnakeGame#isMultiplayer):
 *   - Un jugador: record personal (ver PlayerRecordManager, persistente en disco),
 *     puntaje actual, y tiempo jugado (con cronometro en vivo).
 *   - Multijugador: puntaje de cada jugador activo en la misma arena.
 * El modo es una preferencia POR JUGADOR, no de la arena: dos jugadores en la misma arena
 * pueden estar cada uno en un modo distinto y ver un marcador distinto.
 */
public class GameManager {

    /** Cada cuantos ticks de servidor avanza la serpiente una casilla (20 ticks = 1s). */
    private static final long MOVE_INTERVAL_TICKS = 8L; // ~0.4s por movimiento

    /** Cada cuantos ticks se refresca el marcador lateral (20 ticks = 1s, para el cronometro). */
    private static final long SCOREBOARD_INTERVAL_TICKS = 20L;

    private final Plugin plugin;
    private final Map<UUID, SnakeGame> games = new ConcurrentHashMap<>();
    private final FoodManager foodManager = new FoodManager();
    private final PlayerRecordManager recordManager;
    private BukkitTask movementTask;
    private BukkitTask scoreboardTask;

    public GameManager(Plugin plugin) {
        this.plugin = plugin;
        this.recordManager = new PlayerRecordManager(plugin);
    }

    public FoodManager getFoodManager() {
        return foodManager;
    }

    /**
     * Crea e inicia una partida nueva para el jugador dentro de la arena dada, con el color
     * elegido en el menu y el modo (un jugador / multijugador, decide que scoreboard vera),
     * si no tiene ya una activa. Devuelve null si la arena no tiene ningun punto libre donde
     * aparecer (ver Arena#findRandomSpawn / SnakeGame#start) — en ese caso no se crea nada.
     */
    public SnakeGame startGame(Player player, Arena arena, SnakeColor color, boolean multiplayer) {
        UUID id = player.getUniqueId();
        if (games.containsKey(id)) {
            return games.get(id);
        }

        SnakeGame game = new SnakeGame(id, color);
        if (!game.start(player, arena, multiplayer)) {
            return null;
        }
        games.put(id, game);
        foodManager.ensureFoodSpawned(arena);
        refreshScoreboards(); // feedback inmediato, no esperar al proximo tick del marcador

        ensureLoopsRunning();
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
            resetScoreboard(player);
            clearFoodIfArenaEmpty(arena);
            refreshScoreboards();
        }
        if (games.isEmpty()) {
            stopLoops();
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

    private void ensureLoopsRunning() {
        if (movementTask == null) {
            movementTask = Bukkit.getScheduler()
                    .runTaskTimer(plugin, this::tickMovement, MOVE_INTERVAL_TICKS, MOVE_INTERVAL_TICKS);
        }
        if (scoreboardTask == null) {
            scoreboardTask = Bukkit.getScheduler()
                    .runTaskTimer(plugin, this::refreshScoreboards, SCOREBOARD_INTERVAL_TICKS, SCOREBOARD_INTERVAL_TICKS);
        }
    }

    private void stopLoops() {
        if (movementTask != null) {
            movementTask.cancel();
            movementTask = null;
        }
        if (scoreboardTask != null) {
            scoreboardTask.cancel();
            scoreboardTask = null;
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
     * Se ejecuta cada MOVE_INTERVAL_TICKS. Primero resuelve los choques de frente entre
     * serpientes de la misma arena (ver resolveHeadOnCollisions), y despues mueve cada
     * cabeza (y su asiento, con el jugador encima) una casilla. Reacciona al TickResult:
     *   - COLLIDED: esa serpiente acaba de chocar, se termina su partida aqui mismo, se
     *     avisa al jugador y al resto de la arena, y se refresca el marcador.
     *   - ATE: comio y crecio, se avisa del punto (action bar) — y si supero su record
     *     personal, tambien se avisa de eso y se guarda en disco.
     *   - ALIVE: nada especial que hacer.
     */
    private void tickMovement() {
        Set<UUID> headOnLosers = resolveHeadOnCollisions();

        for (SnakeGame game : games.values()) {
            boolean forcedCollision = headOnLosers.contains(game.getPlayerId());
            TickResult result = forcedCollision ? game.collideHeadOn() : game.tick(foodManager);
            Player player = Bukkit.getPlayer(game.getPlayerId());
            Arena arena = game.getArena();

            switch (result) {
                case COLLIDED -> {
                    games.remove(game.getPlayerId());
                    game.stop(player);
                    clearFoodIfArenaEmpty(arena);

                    String playerName = player != null ? player.getName() : "Alguien";
                    if (player != null) {
                        player.sendMessage(Component.text(
                                "¡Chocaste! Tu serpiente ha muerto. Puntos: " + game.getScore(),
                                NamedTextColor.RED));
                        resetScoreboard(player);
                    }
                    broadcastToArena(arena, player, Component.text(
                            playerName + " ha chocado (puntos: " + game.getScore() + ").",
                            NamedTextColor.GRAY));
                    refreshScoreboards();
                }
                case ATE -> {
                    boolean newRecord = recordManager.updateIfHigher(game.getPlayerId(), game.getScore());
                    if (player != null) {
                        String text = "¡Comiste! Puntos: " + game.getScore() + " | Largo: " + game.getLength();
                        if (newRecord) {
                            text += " | ¡Nuevo record!";
                        }
                        player.sendActionBar(Component.text(text, NamedTextColor.GOLD));
                    }
                    refreshScoreboards();
                }
                case ALIVE -> {
                    // Nada que hacer.
                }
            }
        }
        if (games.isEmpty()) {
            stopLoops();
        }
    }

    /**
     * Detecta, POR ARENA, choques de frente entre serpientes antes de que nadie se mueva de
     * verdad este tick: dos casos.
     *   1. Dos o mas serpientes planean entrar a la MISMA casilla vacia.
     *   2. Dos serpientes se CRUZAN: cada una entra a la casilla que la otra esta dejando.
     * En ambos casos gana la serpiente mas larga; si hay empate exacto de tamaño, pierden
     * todas las implicadas en ese choque concreto.
     *
     * @return los UUID de los jugadores cuya serpiente pierde el choque este tick.
     */
    private Set<UUID> resolveHeadOnCollisions() {
        Map<Arena, List<SnakeGame>> byArena = groupActiveGamesByArena();

        Set<UUID> losers = new HashSet<>();
        for (List<SnakeGame> arenaGames : byArena.values()) {
            if (arenaGames.size() < 2) {
                continue; // sin otro jugador en la arena, no hay con quien chocar de frente
            }

            // Caso 1: mismo destino.
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

            // Caso 2: cruce (A entra donde estaba B, y B entra donde estaba A).
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

    /** Agrupa las partidas activas por arena — lo usan tanto la deteccion de choques como el marcador. */
    private Map<Arena, List<SnakeGame>> groupActiveGamesByArena() {
        Map<Arena, List<SnakeGame>> byArena = new HashMap<>();
        for (SnakeGame game : games.values()) {
            Arena arena = game.getArena();
            if (arena != null) {
                byArena.computeIfAbsent(arena, a -> new ArrayList<>()).add(game);
            }
        }
        return byArena;
    }

    /** Entre los que compiten por la misma casilla, marca como perdedoras a todas menos la mas larga. */
    private void markLosers(List<SnakeGame> contenders, Set<UUID> losers) {
        int maxLength = 0;
        for (SnakeGame game : contenders) {
            maxLength = Math.max(maxLength, game.getLength());
        }
        final int longest = maxLength; // copia final: hace falta para poder usarla dentro de la lambda de abajo
        long winnersCount = contenders.stream().filter(g -> g.getLength() == longest).count();
        if (winnersCount > 1) {
            // Empate exacto de tamaño: pierden todas las implicadas.
            for (SnakeGame game : contenders) {
                losers.add(game.getPlayerId());
            }
        } else {
            for (SnakeGame game : contenders) {
                if (game.getLength() != longest) {
                    losers.add(game.getPlayerId());
                }
            }
        }
    }

    /** Envia un mensaje a todos los jugadores con partida activa en esa arena, salvo 'exclude' (puede ser null). */
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

    /**
     * Reconstruye y reenvia el marcador lateral a TODOS los jugadores con partida activa,
     * cada uno con el tipo que le corresponde segun su propio modo (ver SnakeGame#isMultiplayer):
     * solo (record/puntos/tiempo, personal) o multijugador (puntaje de toda la arena). Se
     * llama al unirse/salir/comer, y ademas cada segundo (ver SCOREBOARD_INTERVAL_TICKS) para
     * que el cronometro del modo solo se vea avanzar en vivo.
     */
    private void refreshScoreboards() {
        if (games.isEmpty()) {
            return;
        }
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) {
            return;
        }

        Map<Arena, List<SnakeGame>> byArena = groupActiveGamesByArena();

        for (SnakeGame game : games.values()) {
            Player viewer = Bukkit.getPlayer(game.getPlayerId());
            if (viewer == null) {
                continue;
            }
            if (game.isMultiplayer()) {
                List<SnakeGame> arenaGames = game.getArena() != null
                        ? byArena.getOrDefault(game.getArena(), List.of())
                        : List.of();
                showMultiplayerScoreboard(manager, viewer, game, arenaGames);
            } else {
                showSoloScoreboard(manager, viewer, game);
            }
        }
    }

    /** Marcador del modo "un jugador": record personal, puntaje actual, y tiempo jugado. */
    @SuppressWarnings("deprecation")
    private void showSoloScoreboard(ScoreboardManager manager, Player viewer, SnakeGame game) {
        int record = Math.max(recordManager.getRecord(game.getPlayerId()), game.getScore());

        Scoreboard board = manager.getNewScoreboard();
        Objective objective = board.registerNewObjective("snake", "dummy");
        objective.setDisplayName("Snake");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        // El numero de cada linea solo controla el ORDEN (de arriba a abajo, descendente);
        // el valor real que importa va dentro del propio texto de la entrada.
        objective.getScore("Record: " + record).setScore(3);
        objective.getScore("Puntos: " + game.getScore()).setScore(2);
        objective.getScore("Tiempo: " + formatTime(game.getElapsedSeconds())).setScore(1);

        viewer.setScoreboard(board);
    }

    /** Marcador del modo "multijugador": puntaje de cada jugador activo en la misma arena. */
    @SuppressWarnings("deprecation")
    private void showMultiplayerScoreboard(ScoreboardManager manager, Player viewer, SnakeGame game,
                                            List<SnakeGame> arenaGames) {
        Scoreboard board = manager.getNewScoreboard();
        Objective objective = board.registerNewObjective("snake", "dummy");
        objective.setDisplayName("Snake: " + (game.getArena() != null ? game.getArena().getName() : ""));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        for (SnakeGame arenaGame : arenaGames) {
            Player entryPlayer = Bukkit.getPlayer(arenaGame.getPlayerId());
            String entryName = entryPlayer != null ? entryPlayer.getName() : "???";
            objective.getScore(entryName).setScore(arenaGame.getScore());
        }

        viewer.setScoreboard(board);
    }

    private static String formatTime(long totalSeconds) {
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    /** Le devuelve al jugador el marcador normal del servidor (al salir/morir, para no dejarle el sidebar pegado). */
    private void resetScoreboard(Player player) {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager != null) {
            player.setScoreboard(manager.getMainScoreboard());
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

    /** Detiene todas las partidas activas y limpia toda la comida, por ejemplo al desactivar el plugin. */
    public void stopAll() {
        Set<Arena> arenas = new HashSet<>();
        for (SnakeGame game : games.values()) {
            Player player = Bukkit.getPlayer(game.getPlayerId());
            if (game.getArena() != null) {
                arenas.add(game.getArena());
            }
            game.stop(player);
            if (player != null) {
                resetScoreboard(player);
            }
        }
        games.clear();
        for (Arena arena : arenas) {
            foodManager.clear(arena);
        }
        stopLoops();
    }
}
