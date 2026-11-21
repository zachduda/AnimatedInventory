package com.zachduda.animatedinventory.animation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.bukkit.Particle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** The name handling in BukkitLookup that works without a server. */
class BukkitLookupTest {

    @ParameterizedTest
    @ValueSource(strings = {"ENTITY_CHICKEN_EGG", "entity.chicken.egg", "minecraft:entity.chicken.egg",
            "MINECRAFT:Entity.Chicken.Egg", "  entity_chicken_egg  ", "entity-chicken-egg", "Entity Chicken Egg"})
    void soundNamesNormalizeTheSameWay(String name) {
        assertEquals("entity_chicken_egg", BukkitLookup.normalizeSound(name));
    }

    @ParameterizedTest
    @ValueSource(strings = {"myserver:clear.whoosh", "my_pack:a/b/c", "pack-1:x", "a:b"})
    void resourcePackKeys(String key) {
        assertTrue(BukkitLookup.isResourcePackKey(key), key);
    }

    /** "minecraft:" sounds are the server's, so a missing one is a typo, not a resource pack. */
    @ParameterizedTest
    @ValueSource(strings = {"minecraft:entity.chicken.egg", "no_namespace", "my pack:x", "pack:", ":sound",
            "a:b:c", "pack:Upper", ""})
    void notResourcePackKeys(String key) {
        assertFalse(BukkitLookup.isResourcePackKey(key), key);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            minecraft:dripping_water | DRIPPING_WATER
            MINECRAFT:Dripping_Water | DRIPPING_WATER
            Dripping Water           | DRIPPING_WATER
            dripping-water           | DRIPPING_WATER
            dripping.water           | DRIPPING_WATER
            ' flame '                | FLAME
            """)
    void particleNamesBecomeConstants(String name, String constant) {
        assertEquals(constant, BukkitLookup.particleConstant(name));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            HAPPY_VILLAGER    | HAPPY_VILLAGER
            VILLAGER_HAPPY    | HAPPY_VILLAGER
            SPELL_WITCH       | WITCH
            REDSTONE          | DUST
            BLOCK_DUST        | BLOCK
            SPELL_MOB_AMBIENT | ENTITY_EFFECT
            minecraft:flame   | FLAME
            """)
    void particlesAreFoundByOldAndNewNames(String name, String expected) {
        assertEquals(expected, nameOf(BukkitLookup.resolveParticle(name)), name);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "NOT_A_PARTICLE", "class", "$VALUES", "valueOf"})
    void unknownParticlesAreNull(String name) {
        assertNull(BukkitLookup.resolveParticle(name), name);
    }

    /** A typo in the alias table would quietly break an old name, so every entry has to resolve. */
    @Test
    void everyAliasLeadsToARealParticle() {
        for (Map.Entry<String, String> alias : BukkitLookup.PARTICLE_ALIASES.entrySet()) {
            assertNotNull(BukkitLookup.resolveParticle(alias.getKey()),
                    () -> alias.getKey() + " -> " + alias.getValue() + " doesn't find a particle");
        }
    }

    private static String nameOf(Particle particle) {
        assertNotNull(particle);
        for (var field : Particle.class.getFields()) {
            try {
                if (field.get(null) == particle) {
                    return field.getName();
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Not a constant.
            }
        }
        throw new AssertionError("No constant for " + particle);
    }
}
