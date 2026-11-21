package com.zachduda.animatedinventory.animation;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.inventory.ItemStack;

/**
 * Looks names up on the running server.
 *
 * Sounds and particles are found through reflection on their constants. Sound is an enum on
 * older servers and an interface on newer ones, so calling Sound.valueOf() from a jar built
 * against one breaks on the other. Reading the constants works on both, and a particle that
 * was renamed in 1.20.5 is still found under its other name.
 */
public final class BukkitLookup implements AnimationLookup {

    /** Resource pack sounds, ie: "myserver:clear.whoosh". */
    private static final Pattern RESOURCE_PACK_KEY = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    /** Particles renamed in 1.20.5, as {new name, old name}. Each is tried when the other is missing. */
    private static final String[][] RENAMED_PARTICLES = {
            {"POOF", "EXPLOSION_NORMAL"}, {"EXPLOSION", "EXPLOSION_LARGE"}, {"EXPLOSION_EMITTER", "EXPLOSION_HUGE"},
            {"FIREWORK", "FIREWORKS_SPARK"}, {"BUBBLE", "WATER_BUBBLE"}, {"SPLASH", "WATER_SPLASH"},
            {"FISHING", "WATER_WAKE"}, {"UNDERWATER", "SUSPENDED"}, {"ENCHANTED_HIT", "CRIT_MAGIC"},
            {"SMOKE", "SMOKE_NORMAL"}, {"LARGE_SMOKE", "SMOKE_LARGE"}, {"EFFECT", "SPELL"},
            {"INSTANT_EFFECT", "SPELL_INSTANT"}, {"ENTITY_EFFECT", "SPELL_MOB"}, {"WITCH", "SPELL_WITCH"},
            {"DRIPPING_WATER", "DRIP_WATER"}, {"DRIPPING_LAVA", "DRIP_LAVA"}, {"ANGRY_VILLAGER", "VILLAGER_ANGRY"},
            {"HAPPY_VILLAGER", "VILLAGER_HAPPY"}, {"MYCELIUM", "TOWN_AURA"}, {"ENCHANT", "ENCHANTMENT_TABLE"},
            {"DUST", "REDSTONE"}, {"ITEM_SNOWBALL", "SNOWBALL"}, {"ITEM_SLIME", "SLIME"}, {"ITEM", "ITEM_CRACK"},
            {"BLOCK", "BLOCK_CRACK"}, {"RAIN", "WATER_DROP"}, {"ELDER_GUARDIAN", "MOB_APPEARANCE"},
            {"TOTEM_OF_UNDYING", "TOTEM"},
    };

    /** Old particles that were merged into another one, so they only map forwards. */
    private static final String[][] MERGED_PARTICLES = {
            {"BLOCK", "BLOCK_DUST"}, {"ITEM_SNOWBALL", "SNOW_SHOVEL"}, {"UNDERWATER", "SUSPENDED_DEPTH"},
            {"ENTITY_EFFECT", "SPELL_MOB_AMBIENT"},
    };

    /** Package-private so the tests can check every name in it is a real particle on one side. */
    static final Map<String, String> PARTICLE_ALIASES;

    static {
        final Map<String, String> aliases = new HashMap<>();
        for (String[] pair : RENAMED_PARTICLES) {
            aliases.put(pair[0], pair[1]);
            aliases.put(pair[1], pair[0]);
        }
        for (String[] pair : MERGED_PARTICLES) {
            aliases.put(pair[1], pair[0]);
        }
        PARTICLE_ALIASES = Collections.unmodifiableMap(aliases);
    }

    private volatile Map<String, Sound> sounds;

    @Override
    public Material item(String name) {
        return itemMaterial(name);
    }

    /**
     * A material that can sit in an inventory slot, or null. Accepts "minecart", "MINECART" and
     * "minecraft:minecart". Never air, a legacy material, or a block that has no item form (ie: WATER).
     */
    public static Material itemMaterial(String name) {
        if (name == null) {
            return null;
        }
        final Material material = Material.matchMaterial(name.trim());
        if (material == null || isLegacy(material) || isAir(material)) {
            return null;
        }
        try {
            return material.isItem() ? material : null;
        } catch (LinkageError olderServer) {
            return material;
        }
    }

    @Override
    public AnimationSound sound(String name) {
        final String trimmed = name.trim();
        final Sound sound = sounds().get(normalizeSound(trimmed));
        if (sound != null) {
            return new AnimationSound(trimmed, sound, null, 1f, 1f);
        }
        final String key = trimmed.toLowerCase(Locale.ROOT);
        if (isResourcePackKey(key)) {
            return new AnimationSound(trimmed, null, key, 1f, 1f);
        }
        return null;
    }

    @Override
    public Particle particle(String name) {
        return resolveParticle(name);
    }

    @Override
    public Object blockData(String name) {
        final Material material = Material.matchMaterial(name.trim());
        if (material == null || isLegacy(material) || !material.isBlock() || isAir(material)) {
            return null;
        }
        return Bukkit.createBlockData(material);
    }

    @Override
    public Object itemData(String name) {
        final Material material = item(name);
        return material == null ? null : new ItemStack(material);
    }

    /** Finds a particle by its current name, its pre-1.20.5 name, or its namespaced key. */
    public static Particle resolveParticle(String name) {
        final String constant = particleConstant(name);
        final Particle particle = particleField(constant);
        if (particle != null) {
            return particle;
        }
        final String alias = PARTICLE_ALIASES.get(constant);
        return alias == null ? null : particleField(alias);
    }

    /** "minecraft:dripping_water" and "Dripping Water" both become "DRIPPING_WATER". */
    static String particleConstant(String name) {
        String constant = name.trim();
        if (constant.regionMatches(true, 0, "minecraft:", 0, "minecraft:".length())) {
            constant = constant.substring("minecraft:".length());
        }
        return constant.toUpperCase(Locale.ROOT).replace('.', '_').replace(' ', '_').replace('-', '_');
    }

    /** "minecraft:entity.chicken.egg", "ENTITY_CHICKEN_EGG" and "Entity.Chicken.Egg" all become "entity_chicken_egg". */
    public static String normalizeSound(String name) {
        String normalized = name.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("minecraft:")) {
            normalized = normalized.substring("minecraft:".length());
        }
        return normalized.replace('.', '_').replace(' ', '_').replace('-', '_');
    }

    /** A namespaced key outside "minecraft:", which only a resource pack can provide. */
    public static boolean isResourcePackKey(String lowerCaseKey) {
        return !lowerCaseKey.startsWith("minecraft:") && RESOURCE_PACK_KEY.matcher(lowerCaseKey).matches();
    }

    /** Only reachable by writing "LEGACY_..." out in full, but those are pre-1.13 leftovers. */
    private static boolean isLegacy(Material material) {
        return material.name().startsWith("LEGACY_");
    }

    private static boolean isAir(Material material) {
        return material == Material.AIR || material.name().endsWith("_AIR");
    }

    private static Particle particleField(String constant) {
        try {
            final Object value = Particle.class.getField(constant).get(null);
            return value instanceof Particle particle ? particle : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError missing) {
            return null;
        }
    }

    private Map<String, Sound> sounds() {
        Map<String, Sound> index = sounds;
        if (index == null) {
            index = new HashMap<>();
            for (Field field : Sound.class.getFields()) {
                if (!Modifier.isStatic(field.getModifiers()) || !Sound.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                try {
                    if (field.get(null) instanceof Sound sound) {
                        index.putIfAbsent(normalizeSound(field.getName()), sound);
                    }
                } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
                    // Skip a constant this server can't hand out.
                }
            }
            sounds = index;
        }
        return index;
    }
}
