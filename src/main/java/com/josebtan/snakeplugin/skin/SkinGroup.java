package com.josebtan.snakeplugin.skin;

import org.bukkit.Material;

import java.util.List;

/**
 * Un grupo de skins: un conjunto de bloques que los jugadores pueden usar como "piel"
 * de su serpiente, agrupados bajo un nombre y un icono para el menu (ver
 * com.josebtan.snakeplugin.gui). Un grupo puede ser de acceso por defecto (todas sus
 * skins sin permiso) o exigir el permiso de cada skin (ver Skin#getPermission).
 */
public final class SkinGroup {

    private final String id;
    private final String displayName;
    private final Material icon;
    private final boolean defaultAccess;
    private final List<Skin> skins = new java.util.ArrayList<>();

    public SkinGroup(String id, String displayName, Material icon, boolean defaultAccess) {
        this.id = id;
        this.displayName = displayName;
        this.icon = icon;
        this.defaultAccess = defaultAccess;
    }

    void addSkin(Skin skin) {
        skins.add(skin);
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Material getIcon() {
        return icon;
    }

    /** true si todas las skins del grupo se pueden usar sin permiso (ver config.yml). */
    public boolean isDefaultAccess() {
        return defaultAccess;
    }

    public List<Skin> getSkins() {
        return List.copyOf(skins);
    }
}
