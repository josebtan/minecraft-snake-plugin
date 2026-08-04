package com.josebtan.snakeplugin.player;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Guarda el mejor puntaje historico (record personal) de cada jugador, en "records.yml"
 * dentro de la carpeta de datos del plugin — sobrevive a reinicios del servidor, igual que
 * las arenas (ver ArenaManager).
 *
 * Se usa para el scoreboard del modo "un jugador" (ver GameManager#showSoloScoreboard),
 * que muestra el record junto al puntaje actual y el tiempo jugado.
 */
public class PlayerRecordManager {

    private final Plugin plugin;
    private final File file;
    private final Map<UUID, Integer> records = new ConcurrentHashMap<>();

    public PlayerRecordManager(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "records.yml");
        load();
    }

    private void load() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = config.getConfigurationSection("records");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            try {
                records.put(UUID.fromString(key), section.getInt(key));
            } catch (IllegalArgumentException ignored) {
                // Clave rara en el archivo (no es un UUID valido): se ignora sin romper la carga.
            }
        }
    }

    private void save() {
        YamlConfiguration config = new YamlConfiguration();
        for (Map.Entry<UUID, Integer> entry : records.entrySet()) {
            config.set("records." + entry.getKey(), entry.getValue());
        }
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("SnakePlugin: no se pudo guardar records.yml: " + e.getMessage());
        }
    }

    /** Mejor puntaje historico de ese jugador, o 0 si todavia no tiene ninguno guardado. */
    public int getRecord(UUID playerId) {
        return records.getOrDefault(playerId, 0);
    }

    /**
     * Si 'score' supera el record guardado de ese jugador, lo actualiza y lo persiste al
     * momento (no hace falta esperar a que termine la partida).
     *
     * @return true si esto establecio un record nuevo.
     */
    public boolean updateIfHigher(UUID playerId, int score) {
        Integer current = records.get(playerId);
        if (current == null || score > current) {
            records.put(playerId, score);
            save();
            return true;
        }
        return false;
    }
}
