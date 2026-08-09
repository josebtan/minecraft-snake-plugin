package com.josebtan.snakeplugin.skin;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Carga y administra las skins definidas en config.yml. Cada skin vive en un grupo y
 * requiere su permiso (snakeplugin.skins.&lt;grupo&gt;.&lt;skin&gt;) salvo que su grupo
 * tenga "default: true". {@link #reload} re-construye todo desde disco (lo usa el
 * comando /snake reload).
 */
public class SkinManager {

    private final Plugin plugin;
    private final List<SkinGroup> groups = new ArrayList<>();
    private final Map<String, Skin> skinsById = new LinkedHashMap<>();

    public SkinManager(Plugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        groups.clear();
        skinsById.clear();

        ConfigurationSection root = plugin.getConfig().getConfigurationSection("skins");
        if (root == null) {
            return;
        }

        for (String groupId : root.getKeys(false)) {
            ConfigurationSection groupSection = root.getConfigurationSection(groupId);
            if (groupSection == null) {
                continue;
            }
            String displayName = groupSection.getString("display-name", groupId);
            Material icon = Material.matchMaterial(groupSection.getString("icon", "WHITE_WOOL"));
            if (icon == null) {
                icon = Material.WHITE_WOOL;
            }
            boolean defaultAccess = groupSection.getBoolean("default", false);

            SkinGroup group = new SkinGroup(groupId, displayName, icon, defaultAccess);
            ConfigurationSection skinsSection = groupSection.getConfigurationSection("skins");
            if (skinsSection != null) {
                for (String skinId : skinsSection.getKeys(false)) {
                    ConfigurationSection skinSection = skinsSection.getConfigurationSection(skinId);
                    if (skinSection == null) {
                        continue;
                    }
                    String skinName = skinSection.getString("display-name", skinId);
                    Material material = Material.matchMaterial(skinSection.getString("block", "WHITE_WOOL"));
                    if (material == null) {
                        continue;
                    }
                    Skin skin = new Skin(skinId, skinName, material, group);
                    group.addSkin(skin);
                    skinsById.put(skin.getId(), skin);
                }
            }
            if (!group.getSkins().isEmpty()) {
                groups.add(group);
            }
        }
    }

    /** Todos los grupos cargados (en el orden del config). */
    public List<SkinGroup> getGroups() {
        return List.copyOf(groups);
    }

    /** Skin por id, o null si no existe. */
    public Skin getSkin(String id) {
        return skinsById.get(id);
    }

    /** Cuantas skins hay cargadas en total. */
    public int getTotalSkins() {
        return skinsById.size();
    }

    /** Grupos en los que el jugador puede usar al menos una skin. */
    public List<SkinGroup> getAccessibleGroups(Player player) {
        List<SkinGroup> result = new ArrayList<>();
        for (SkinGroup group : groups) {
            if (!getAccessibleSkins(player, group).isEmpty()) {
                result.add(group);
            }
        }
        return result;
    }

    /** Skins del grupo que el jugador puede usar (grupo con acceso por defecto o con su permiso). */
    public List<Skin> getAccessibleSkins(Player player, SkinGroup group) {
        List<Skin> result = new ArrayList<>();
        for (Skin skin : group.getSkins()) {
            if (group.isDefaultAccess() || player.hasPermission(skin.getPermission())) {
                result.add(skin);
            }
        }
        return result;
    }
}
