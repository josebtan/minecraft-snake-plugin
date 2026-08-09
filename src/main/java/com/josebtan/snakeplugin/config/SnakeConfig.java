package com.josebtan.snakeplugin.config;

import org.bukkit.plugin.Plugin;

/**
 * Lee la configuracion del plugin (config.yml). De momento solo expone el modo de
 * movimiento de la serpiente (suave o clasico). {@link #reload} vuelve a cargar el
 * archivo desde disco sin reiniciar el servidor (lo usa el comando /snake reload).
 */
public class SnakeConfig {

    private final Plugin plugin;
    private boolean smoothMovement;

    public SnakeConfig(Plugin plugin) {
        this.plugin = plugin;
        reload();
    }

    /** Recarga config.yml desde disco y re-lee todos los valores. */
    public void reload() {
        plugin.reloadConfig();
        String mode = plugin.getConfig().getString("movement-mode", "smooth");
        this.smoothMovement = !"classic".equalsIgnoreCase(mode == null ? "" : mode.trim());
    }

    /** true si la serpiente se desliza suavemente; false si avanza a saltos (classic). */
    public boolean isSmoothMovement() {
        return smoothMovement;
    }
}
