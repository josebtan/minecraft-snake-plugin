package com.josebtan.snakeplugin.game;

/**
 * Modo de juego de una arena. A diferencia de versiones anteriores (donde el modo era
 * una preferencia POR JUGADOR, y una misma arena podia tener partidas de ambos tipos a
 * la vez), ahora el modo se elige al CREAR la arena y es unico para ella: una arena
 * siempre es "Un jugador" O "Multijugador", nunca ambos.
 *
 * En modo MULTIPLAYER la arena ademas guarda cuantos jugadores maximo admite por
 * partida (ver com.josebtan.snakeplugin.arena.Arena#getMaxPlayers), y al unirse los
 * jugadores pasan por una sala de espera hasta llenarse o caducar el temporizador.
 */
public enum GameMode {
    /** Un solo jugador contra su propio record. */
    SOLO,
    /** Varios jugadores en la misma arena, todos juntos en una sala de espera. */
    MULTIPLAYER;

    public boolean isMultiplayer() {
        return this == MULTIPLAYER;
    }

    public String getDisplayName() {
        return this == MULTIPLAYER ? "Multijugador" : "Un jugador";
    }
}