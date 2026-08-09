package com.josebtan.snakeplugin.skin;

import org.bukkit.Material;

/**
 * Una skin de serpiente: el bloque (material) que se usa como cuerpo visible de la
 * serpiente, con un nombre para mostrar y su grupo. La igualdad es por id (dos skins
 * con el mismo id se consideran la misma, da igual el material), que es lo que se usa
 * para saber si una skin ya esta "ocupada" en una arena multijugador.
 */
public final class Skin {

    private final String id;
    private final String displayName;
    private final Material material;
    private final SkinGroup group;

    public Skin(String id, String displayName, Material material, SkinGroup group) {
        this.id = id;
        this.displayName = displayName;
        this.material = material;
        this.group = group;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Material getMaterial() {
        return material;
    }

    public SkinGroup getGroup() {
        return group;
    }

    /** Permiso necesario para usar esta skin: snakeplugin.skins.<grupo>.<skin>. */
    public String getPermission() {
        return "snakeplugin.skins." + group.getId() + "." + id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Skin other)) {
            return false;
        }
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
