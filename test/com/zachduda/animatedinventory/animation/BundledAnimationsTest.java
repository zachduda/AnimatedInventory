package com.zachduda.animatedinventory.animation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/** Checks the animation files the plugin ships, which are also the examples admins copy. */
class BundledAnimationsTest {

    /** The tick each animation used to wipe the inventory on, back when they were hard-coded. */
    private static final Map<String, Integer> ORIGINAL_LENGTHS = Map.of(
            "panes", 21, "rainbow", 39, "explosion", 49, "water", 89, "fireball", 40);

    static String bundled(String fileName) {
        try (InputStream in = BundledAnimationsTest.class.getResourceAsStream("/animations/" + fileName)) {
            assertNotNull(in, "animations/" + fileName + " isn't in the jar");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void everyBuiltInLoadsWithoutErrorsOrWarnings() {
        for (String name : AnimationRegistry.BUILT_IN) {
            final AnimationParser.Result result = AnimationParserTest.parse(bundled(name + ".yml"));
            assertTrue(result.ok(), () -> name + ".yml: " + result.errors());
            assertEquals(List.of(), result.warnings(), name + ".yml");
            assertTrue(result.animation().enabled(), name + ".yml");
        }
    }

    @Test
    void builtInsKeepTheirOriginalTiming() {
        assertEquals(new HashSet<>(AnimationRegistry.BUILT_IN), ORIGINAL_LENGTHS.keySet());
        for (String name : AnimationRegistry.BUILT_IN) {
            final ClearAnimation animation = AnimationParserTest.parse(bundled(name + ".yml")).animation();
            assertEquals(ORIGINAL_LENGTHS.get(name), animation.length(), name + ".yml");
        }
    }

    /** The registry turns a built-in off for its first write by flipping this one line. */
    @Test
    void eachBuiltInHasOneEnabledLineToFlip() {
        final Pattern enabledLine = Pattern.compile("(?m)^enabled: true\\b");
        for (String name : AnimationRegistry.BUILT_IN) {
            assertEquals(1, enabledLine.matcher(bundled(name + ".yml")).results().count(), name + ".yml");
        }
    }

    @Test
    void everyOldConfigSwitchHasAFile() {
        assertEquals(new HashSet<>(AnimationRegistry.BUILT_IN), new HashSet<>(AnimationRegistry.LEGACY_NAMES.values()));
    }

    @Test
    void theReadmeShips() {
        assertTrue(bundled("README.txt").contains("hotbar"));
    }

    /**
     * The pom filters resources, so a ${...} or an @...@ in these files would be replaced at
     * build time. This reads the sources, since by now the built copies are already filtered.
     */
    @Test
    void sourcesAvoidMavenFilterDelimiters() throws IOException {
        try (var files = Files.list(Path.of("src", "animations"))) {
            for (Path file : files.toList()) {
                final String text = Files.readString(file, StandardCharsets.UTF_8);
                assertFalse(text.contains("${"), file + " contains ${");
                assertFalse(text.contains("@"), file + " contains @");
            }
        }
    }
}
