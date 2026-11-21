package com.zachduda.animatedinventory.animation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AnimationRegistryTest {

    private static final String SIMPLE = """
            items:
              S: GLOWSTONE_DUST
            frames:
              - hotbar: 'SSSSSSSSS'
            """;

    @TempDir
    File dataFolder;

    private final List<String> log = new ArrayList<>();
    private Path folder;

    @BeforeEach
    void setUp() {
        folder = dataFolder.toPath().resolve(AnimationRegistry.FOLDER);
    }

    private AnimationRegistry registry() {
        final Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                log.add(record.getMessage());
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
        return new AnimationRegistry(dataFolder, logger,
                name -> AnimationRegistryTest.class.getResourceAsStream("/" + name), new TestLookup());
    }

    private void write(String fileName, String text) throws IOException {
        Files.createDirectories(folder);
        Files.writeString(folder.resolve(fileName), text, StandardCharsets.UTF_8);
    }

    private long countFiles(Predicate<String> name) throws IOException {
        try (var files = Files.list(folder)) {
            return files.filter(file -> name.test(file.getFileName().toString())).count();
        }
    }

    private boolean logged(String text) {
        return log.stream().anyMatch(line -> line.contains(text));
    }

    @Test
    void writesTheBuiltInsIntoANewFolder() {
        final AnimationRegistry registry = registry();
        assertTrue(registry.reload(Map.of()));

        for (String name : AnimationRegistry.BUILT_IN) {
            assertTrue(Files.isRegularFile(folder.resolve(name + ".yml")), name);
            assertNotNull(registry.get(name), name);
        }
        assertTrue(Files.isRegularFile(folder.resolve("README.txt")));
        assertEquals(AnimationRegistry.BUILT_IN.size(), registry.enabled().size());
        assertEquals(0, registry.problems());
        assertTrue(logged("Wrote the built-in clear animations"), log::toString);
    }

    @Test
    void oldConfigSwitchesCarryOverIntoTheNewFiles() throws IOException {
        final AnimationRegistry registry = registry();
        registry.reload(Map.of("Explode_Animation", false, "Water_Animation", true));

        assertFalse(registry.get("explosion").enabled());
        assertTrue(registry.get("water").enabled());
        assertTrue(Files.readString(folder.resolve("explosion.yml")).contains("\nenabled: false"));
        assertTrue(logged("explosion starts turned off"), log::toString);

        // Once the files exist, they're what counts: an old switch never rewrites one.
        registry.reload(Map.of("Explode_Animation", true, "Water_Animation", false));
        assertFalse(registry.get("explosion").enabled());
        assertTrue(registry.get("water").enabled());
    }

    @Test
    void neverOverwritesAnAnimationFile() throws IOException {
        final String custom = SIMPLE + "enabled: false\n";
        write("rainbow.yml", custom);

        registry().reload(Map.of("Rainbow_Animation", true));

        assertEquals(custom, Files.readString(folder.resolve("rainbow.yml")));
    }

    @Test
    void putsBackADeletedBuiltIn() throws IOException {
        final AnimationRegistry registry = registry();
        registry.reload(Map.of());
        Files.delete(folder.resolve("fireball.yml"));
        log.clear();

        registry.reload(Map.of());

        assertTrue(Files.isRegularFile(folder.resolve("fireball.yml")));
        assertNotNull(registry.get("fireball"));
        assertTrue(logged("Restored the missing built-in animation file(s) fireball"), log::toString);
    }

    @Test
    void loadsCustomAnimationsAndSkipsBrokenOnes() throws IOException {
        write("sparkle.yml", SIMPLE);
        write("not-yaml.yml", "frames: [\n");
        write("mistakes.yml", "frames:\n  - hotbar: 'Z........'\n");
        write("has space.yml", SIMPLE);
        write(".hidden.yml", "not even yaml: [");
        write("notes.txt", "Not an animation.");

        final AnimationRegistry registry = registry();
        registry.reload(Map.of());

        assertNotNull(registry.get("sparkle"));
        assertNull(registry.get("not-yaml"));
        assertNull(registry.get("mistakes"));
        assertEquals(3, registry.problems(), log::toString);
        assertEquals(AnimationRegistry.BUILT_IN.size() + 1, registry.enabled().size());
        assertTrue(logged("Skipped Animations/not-yaml.yml, since it isn't valid YAML"), log::toString);
        assertTrue(logged("frame 1: 'hotbar' uses 'Z'"), log::toString);
        assertTrue(logged("Animation file names can only use"), log::toString);
        assertFalse(logged(".hidden"), log::toString);
    }

    @Test
    void namesIgnoreCase() throws IOException {
        write("Sparkle.yml", SIMPLE);
        final AnimationRegistry registry = registry();
        registry.reload(Map.of());

        assertNotNull(registry.get("SPARKLE"));
        assertEquals("sparkle", registry.get("sparkle").name());
    }

    @Test
    void twoFilesWithTheSameNameOnlyLoadOnce() throws IOException {
        write("Sparkle.yml", SIMPLE);
        write("sparkle.yml", SIMPLE);
        Assumptions.assumeTrue(countFiles(name -> true) == 2, "This file system ignores case");

        final AnimationRegistry registry = registry();
        registry.reload(Map.of());

        assertEquals(1, registry.problems());
        assertTrue(logged("have the same name"), log::toString);
    }

    @Test
    void doesntRestoreABuiltInThatWasOnlyRenamedInCase() throws IOException {
        write("Explosion.yml", SIMPLE);

        registry().reload(Map.of());

        assertEquals(1, countFiles(name -> name.equalsIgnoreCase("explosion.yml")));
    }

    @Test
    void picksOnlyEnabledAnimationsByWeight() throws IOException {
        for (String name : AnimationRegistry.BUILT_IN) {
            write(name + ".yml", SIMPLE + "enabled: false\n");
        }
        write("common.yml", SIMPLE + "weight: 3\n");
        write("rare.yml", SIMPLE);
        final AnimationRegistry registry = registry();
        registry.reload(Map.of());

        final Map<String, Integer> picks = new HashMap<>();
        for (int i = 0; i < 4000; i++) {
            picks.merge(registry.pick().name(), 1, Integer::sum);
        }
        assertEquals(2, picks.size(), picks::toString);
        assertEquals(0.75, picks.get("common") / 4000.0, 0.05, picks::toString);
    }

    @Test
    void picksNothingWhenEverythingIsOff() throws IOException {
        for (String name : AnimationRegistry.BUILT_IN) {
            write(name + ".yml", SIMPLE + "enabled: false\n");
        }
        final AnimationRegistry registry = registry();
        registry.reload(Map.of());

        assertNull(registry.pick());
        assertTrue(registry.enabled().isEmpty());
        assertNotNull(registry.get("panes"), "Turned-off animations can still be played by name");
    }

    @Test
    void keepsTheReadmeCurrent() throws IOException {
        write("README.txt", "Out of date");
        registry().reload(Map.of());

        assertEquals(BundledAnimationsTest.bundled("README.txt"), Files.readString(folder.resolve("README.txt")));
    }

    @Test
    void readsFilesWithAByteOrderMark() throws IOException {
        write("sparkle.yml", "﻿" + SIMPLE);
        final AnimationRegistry registry = registry();
        registry.reload(Map.of());

        assertNotNull(registry.get("sparkle"), log::toString);
    }

    @Test
    void reportsAFolderItCantCreate() throws IOException {
        Files.writeString(folder, "A file where the folder should be");

        assertFalse(registry().reload(Map.of()));
        assertTrue(logged("Couldn't create the Animations folder"), log::toString);
    }
}
