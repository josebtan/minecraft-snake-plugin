package com.josebtan.snakeplugin.arena;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Guarda las arenas creadas (por nombre) y las selecciones de esquinas "pos1"/"pos2" que
 * cada jugador va marcando mientras define una nueva arena (al estilo WorldEdit: te paras
 * en una esquina, marcas pos1; te paras en la otra, marcas pos2; luego "create" construye
 * el rectangulo entre ambas).
 *
 * Etapa futura pendiente: persistir las arenas en disco (config.yml o similar) — ahora mismo
 * se pierden al reiniciar el servidor y hay que volver a crearlas.
 */
public class ArenaManager {

    private final Map<String, Arena> arenas = new ConcurrentHashMap<>();
    private final Map<UUID, Location> pendingPos1 = new ConcurrentHashMap<>();
    private final Map<UUID, Location> pendingPos2 = new ConcurrentHashMap<>();

    public void setPos1(Player player, Location location) {
        pendingPos1.put(player.getUniqueId(), location.getBlock().getLocation());
    }

    public void setPos2(Player player, Location location) {
        pendingPos2.put(player.getUniqueId(), location.getBlock().getLocation());
    }

    public Location getPos1(Player player) {
        return pendingPos1.get(player.getUniqueId());
    }

    public Location getPos2(Player player) {
        return pendingPos2.get(player.getUniqueId());
    }

    /**
     * Crea (o reemplaza) la arena con el nombre dado usando las esquinas pos1/pos2 que el
     * jugador tenga marcadas en ese momento. Devuelve null sin hacer nada si falta alguna
     * esquina o si estan en mundos distintos.
     */
    public Arena createFromPending(Player player, String name) {
        Location pos1 = getPos1(player);
        Location pos2 = getPos2(player);
        if (pos1 == null || pos2 == null) {
            return null;
        }
        if (!pos1.getWorld().equals(pos2.getWorld())) {
            return null;
        }

        int minX = Math.min(pos1.getBlockX(), pos2.getBlockX());
        int maxX = Math.max(pos1.getBlockX(), pos2.getBlockX());
        int minZ = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
        int maxZ = Math.max(pos1.getBlockZ(), pos2.getBlockZ());
        // La altura de juego se fija con la Y de pos1 (asumimos que ambas esquinas se
        // marcaron mas o menos al mismo nivel; no se valida mas alla de esto por ahora).
        int boardY = pos1.getBlockY();

        Arena arena = new Arena(name, pos1.getWorld(), minX, maxX, minZ, maxZ, boardY);
        arena.build();
        arenas.put(name.toLowerCase(), arena);
        return arena;
    }

    public Arena getArena(String name) {
        return arenas.get(name.toLowerCase());
    }

    public Map<String, Arena> getArenas() {
        return arenas;
    }
}
