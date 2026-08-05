package com.josebtan.snakeplugin.gui;

import com.josebtan.snakeplugin.arena.Arena;
import com.josebtan.snakeplugin.arena.ArenaManager;
import com.josebtan.snakeplugin.game.GameMode;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Flujo de creacion de una arena "por chat", paso a paso. Ahora crear una arena NO es
 * solo darle un nombre: el modo de juego (un jugador / multijugador) se elige aqui,
 * tambien al crearla — una arena tiene UN modo, fijo, para siempre (no es una
 * preferencia del jugador como antes). En modo multijugador se pregunta ademas cuantos
 * jugadores maximo tendra cada partida en esa arena.
 *
 * Pasos:
 *   1. NOMBRE: "Escribe el nombre de la arena en el chat (o 'cancelar')".
 *   2. MODO: "Escribe 'solo' o 'multi'".
 *   3. MAX_JUGADORES (solo si es 'multi'): "Escribe el maximo de jugadores (2-8)".
 *
 * Lo llaman tanto el boton "Crear arena" del panel (SnakeGuiListener) como el comando
 * de texto /snake arena create [nombre] (SnakeCommand). El chat se captura en
 * SnakeGuiListener (AsyncPlayerChatEvent) y se delega aqui — SIEMPRE en el hilo
 * principal del servidor, porque esta clase acaba tocando el mundo y el archivo de disco.
 */
public class ArenaCreationFlow {

    private static final int MIN_PLAYERS = 2;
    private static final int MAX_PLAYERS = 8;

    private enum Step {
        NAME,
        MODE,
        MAX_PLAYERS
    }

    private final ArenaManager arenaManager;
    private final Map<UUID, Step> steps = new ConcurrentHashMap<>();
    private final Map<UUID, String> pendingNames = new ConcurrentHashMap<>();
    private final Map<UUID, GameMode> pendingModes = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> pendingMaxPlayers = new ConcurrentHashMap<>();

    public ArenaCreationFlow(ArenaManager arenaManager) {
        this.arenaManager = arenaManager;
    }

    /** Empieza el flujo desde cero (se pedira el nombre primero). */
    public void start(Player player) {
        clearPending(player);
        steps.put(player.getUniqueId(), Step.NAME);
        askName(player);
    }

    /** Empieza el flujo saltandose el nombre (ya lo conocemos, ej: /snake arena create <nombre>). */
    public void startWithName(Player player, String name) {
        clearPending(player);
        pendingNames.put(player.getUniqueId(), name.trim());
        askMode(player);
    }

    /**
     * Procesa un mensaje de chat de un jugador que esta en medio del flujo de creacion.
     * Debe llamarse desde el hilo principal del servidor. Devuelve true si el jugador
     * estaba en medio del flujo (y por tanto el mensaje se consumio, sin mostrarse como
     * chat normal).
     */
    public boolean onChat(Player player, String message) {
        UUID id = player.getUniqueId();
        Step step = steps.get(id);
        if (step == null) {
            return false;
        }

        if (message.equalsIgnoreCase("cancelar")) {
            clearPending(player);
            player.sendMessage(Component.text("Creacion de arena cancelada.", NamedTextColor.YELLOW));
            return true;
        }

        switch (step) {
            case NAME -> {
                pendingNames.put(id, message.trim());
                askMode(player);
            }
            case MODE -> {
                String lower = message.trim().toLowerCase();
                if (lower.startsWith("s")) {
                    pendingModes.put(id, GameMode.SOLO);
                    finish(player);
                } else if (lower.startsWith("m")) {
                    pendingModes.put(id, GameMode.MULTIPLAYER);
                    askMaxPlayers(player);
                } else {
                    player.sendMessage(Component.text(
                            "No entendi ese modo. Escribe 'solo' o 'multi'.", NamedTextColor.RED));
                    steps.put(id, Step.MODE);
                }
            }
            case MAX_PLAYERS -> {
                int parsed;
                try {
                    parsed = Integer.parseInt(message.trim());
                } catch (NumberFormatException e) {
                    player.sendMessage(Component.text(
                            "Eso no parece un numero. Escribe el maximo de jugadores ("
                                    + MIN_PLAYERS + "-" + MAX_PLAYERS + ").", NamedTextColor.RED));
                    steps.put(id, Step.MAX_PLAYERS);
                    return true;
                }
                int clamped = Math.max(MIN_PLAYERS, Math.min(MAX_PLAYERS, parsed));
                if (clamped != parsed) {
                    player.sendMessage(Component.text(
                            "Se ajusto el maximo a " + clamped + " (limite " + MIN_PLAYERS
                                    + "-" + MAX_PLAYERS + ").", NamedTextColor.YELLOW));
                }
                pendingMaxPlayers.put(id, clamped);
                finish(player);
            }
        }
        return true;
    }

    private void finish(Player player) {
        UUID id = player.getUniqueId();
        String name = pendingNames.get(id);
        GameMode mode = pendingModes.getOrDefault(id, GameMode.SOLO);
        Integer maxPlayers = pendingMaxPlayers.get(id);

        Arena arena = arenaManager.createFromPending(player, name, mode,
                mode.isMultiplayer() && maxPlayers != null ? maxPlayers : 1);
        clearPending(player);

        if (arena == null) {
            player.sendMessage(Component.text(
                    "Falta marcar pos1 y pos2 (en el mismo mundo) antes de crear la arena.",
                    NamedTextColor.RED));
            return;
        }

        String extra = mode.isMultiplayer()
                ? " (multijugador, hasta " + arena.getMaxPlayers() + " jugadores)"
                : " (un jugador)";
        player.sendMessage(Component.text(
                "Arena '" + arena.getName() + "' creada y guardada" + extra + ".",
                NamedTextColor.GREEN));
    }

    private void askName(Player player) {
        steps.put(player.getUniqueId(), Step.NAME);
        player.sendMessage(Component.text(
                "Escribe el nombre de la arena en el chat (o 'cancelar').", NamedTextColor.AQUA));
    }

    private void askMode(Player player) {
        steps.put(player.getUniqueId(), Step.MODE);
        player.sendMessage(Component.text(
                "Escribe el modo de la arena: 'solo' (un jugador) o 'multi' (multijugador).",
                NamedTextColor.AQUA));
    }

    private void askMaxPlayers(Player player) {
        steps.put(player.getUniqueId(), Step.MAX_PLAYERS);
        player.sendMessage(Component.text(
                "Escribe el maximo de jugadores para esta arena (" + MIN_PLAYERS + "-"
                        + MAX_PLAYERS + ").", NamedTextColor.AQUA));
    }

    /** true si ese jugador esta en medio del flujo de creacion (esperando un mensaje de chat). */
    public boolean isAwaiting(Player player) {
        return steps.containsKey(player.getUniqueId());
    }

    /** Limpia cualquier creacion pendiente de ese jugador (por si se desconecta a mitad). */
    public void clearPending(Player player) {
        UUID id = player.getUniqueId();
        steps.remove(id);
        pendingNames.remove(id);
        pendingModes.remove(id);
        pendingMaxPlayers.remove(id);
    }
}