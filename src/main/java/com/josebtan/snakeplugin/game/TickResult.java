package com.josebtan.snakeplugin.game;

/** Resultado de un tick de movimiento de SnakeGame#tick — ver GameManager#tickMovement. */
public enum TickResult {
    /** Se movio con normalidad, sin comer nada este tick. */
    ALIVE,
    /** Se movio Y comio (la serpiente crecio; hay que avisar del punto). */
    ATE,
    /** Choco contra algo: la partida termino. */
    COLLIDED
}
