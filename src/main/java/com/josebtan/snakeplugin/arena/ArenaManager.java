package com.josebtan.snakeplugin.arena;

import com.josebtan.snakeplugin.game.GameMode;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Guarda las arenas creadas (por nombre) y las selecciones de esquinas "pos1"/"pos2" que
 * cada jugador va marcando mientras define una nueva arena (al estilo WorldEdit: te paras
 * en una esquina, marcas pos1; te paras en la otra, marcas pos2; luego "create" registra el
 * rectangulo entre ambas como la zona de esa arena). No se construye ni modifica nada del
 * mundo: la arena es solo la zona donde luego apareceran jugadores y comida — el propio
 * jugador decide como decorarla o delimitarla fisicamente.
 *
 * ETAPA 2 (persistencia): las arenas se guardan en "arenas.yml", dentro de la carpeta de
 * datos del plugin, y se recargan automaticamente al arrancar el servidor. Cualquier
 * creacion o borrado de una arena reescribe el archivo entero (son pocos datos, no hace
 * falta nada mas sofisticado).
 */
public class ArenaManager {

    private final Plugin plugin;
    private final File file;
    private final Map<String, Arena> arenas = new ConcurrentHashMap<>();
    private final Map<UUID, Location> pendingPos1 = new ConcurrentHashMap<>();
    private final Map<UUID, Location> pendingPos2 = new ConcurrentHashMap<>();

    public ArenaManager(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "arenas.yml");
        load();
    }

    /** Carga las arenas guardadas desde arenas.yml, si el archivo existe. */
    private void load() {
        if (!file.exists()) {
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = config.getConfigurationSection("arenas");
        if (section == null) {
            return;
        }

        int loaded = 0;
        for (String key : section.getKeys(false)) {
            ConfigurationSection arenaSection = section.getConfigurationSection(key);
            if (arenaSection == null) {
                continue;
            }

            String worldName = arenaSection.getString("world");
            World world = worldName != null ? Bukkit.getWorld(worldName) : null;
            if (world == null) {
                plugin.getLogger().warning("SnakePlugin: no se pudo cargar la arena '" + key
                        + "' (el mundo '" + worldName + "' no existe o no esta cargado).");
                continue;
            }

            int minX = arenaSection.getInt("minX");
            int maxX = arenaSection.getInt("maxX");
            int minZ = arenaSection.getInt("minZ");
            int maxZ = arenaSection.getInt("maxZ");
            int boardY = arenaSection.getInt("boardY");

            // Compatibilidad con arenas guardadas por versiones anteriores (sin modo):
            // se asume "un jugador".
            String modeName = arenaSection.getString("mode");
            GameMode mode = GameMode.SOLO;
            if (modeName != null) {
                try {
                    mode = GameMode.valueOf(modeName.toUpperCase());
                } catch (IllegalArgumentException ignored) {
                    mode = GameMode.SOLO;
                }
            }
            int maxPlayers = Math.max(1, arenaSection.getInt("maxPlayers", mode.isMultiplayer() ? 2 : 1));

            arenas.put(key, new Arena(key, world, minX, maxX, minZ, maxZ, boardY, mode, maxPlayers));
            loaded++;
        }

        if (loaded > 0) {
            plugin.getLogger().info("SnakePlugin: " + loaded + " arena(s) cargada(s) desde arenas.yml.");
        }
    }

    /** Vuelca todas las arenas actuales a arenas.yml (sobreescribe el archivo entero). */
    private void save() {
        YamlConfiguration config = new YamlConfiguration();
        for (Map.Entry<String, Arena> entry : arenas.entrySet()) {
            Arena arena = entry.getValue();
            String path = "arenas." + entry.getKey();
            config.set(path + ".world", arena.getWorld().getName());
            config.set(path + ".minX", arena.getMinX());
            config.set(path + ".maxX", arena.getMaxX());
            config.set(path + ".minZ", arena.getMinZ());
            config.set(path + ".maxZ", arena.getMaxZ());
            config.set(path + ".boardY", arena.getBoardY());
            config.set(path + ".mode", arena.getMode().name());
            config.set(path + ".maxPlayers", arena.getMaxPlayers());
        }

        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("SnakePlugin: no se pudo guardar arenas.yml: " + e.getMessage());
        }
    }

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
     * jugador tenga marcadas en ese momento, el modo de juego elegido y, en modo
     * multijugador, el numero maximo de jugadores por partida. Se guarda en disco. Devuelve
     * null sin hacer nada si falta alguna esquina o si estan en mundos distintos.
     */
    public Arena createFromPending(Player player, String name, GameMode mode, int maxPlayers) {
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

        Arena arena = new Arena(name, pos1.getWorld(), minX, maxX, minZ, maxZ, boardY, mode, maxPlayers);
        arenas.put(name.toLowerCase(), arena);
        save();
        return arena;
    }

    /**
     * Elimina la arena con ese nombre (si existe) y guarda el cambio en disco.
     * No afecta a partidas ya en curso en esa arena (si alguna sigue activa, simplemente
     * deja de aparecer en la lista/menu, pero no se interrumpe a la fuerza).
     *
     * @return true si existia y se elimino; false si no habia ninguna arena con ese nombre.
     */
    public boolean deleteArena(String name) {
        Arena removed = arenas.remove(name.toLowerCase());
        if (removed == null) {
            return false;
        }
        save();
        return true;
    }

    public Arena getArena(String name) {
        return arenas.get(name.toLowerCase());
    }

    public Map<String, Arena> getArenas() {
        return arenas;
    }
}
