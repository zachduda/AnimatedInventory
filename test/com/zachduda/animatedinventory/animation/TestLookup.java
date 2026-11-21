package com.zachduda.animatedinventory.animation;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;

/**
 * Stands in for {@link BukkitLookup}, which needs a running server for sounds, items and
 * block data. Materials and particles are the real enums; sounds are checked against the real
 * constant names, read without initializing Sound.
 */
final class TestLookup implements AnimationLookup {

    static final Set<String> SOUNDS = Arrays.stream(Sound.class.getFields())
            .filter(field -> Modifier.isStatic(field.getModifiers()) && Sound.class.isAssignableFrom(field.getType()))
            .map(field -> BukkitLookup.normalizeSound(field.getName()))
            .collect(Collectors.toUnmodifiableSet());

    @Override
    public Material item(String name) {
        final Material material = Material.matchMaterial(name.trim());
        return material == null || material.name().startsWith("LEGACY_") || material.name().endsWith("AIR")
                ? null : material;
    }

    @Override
    public AnimationSound sound(String name) {
        final String trimmed = name.trim();
        if (SOUNDS.contains(BukkitLookup.normalizeSound(trimmed))) {
            return new AnimationSound(trimmed, null, "test:" + BukkitLookup.normalizeSound(trimmed), 1f, 1f);
        }
        final String key = trimmed.toLowerCase(Locale.ROOT);
        return BukkitLookup.isResourcePackKey(key) ? new AnimationSound(trimmed, null, key, 1f, 1f) : null;
    }

    @Override
    public Particle particle(String name) {
        return BukkitLookup.resolveParticle(name);
    }

    @Override
    public Object blockData(String name) {
        final Material material = item(name);
        return material == null ? null : "block data for " + material;
    }

    @Override
    public Object itemData(String name) {
        final Material material = item(name);
        return material == null ? null : "item stack of " + material;
    }
}
