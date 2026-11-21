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
        return registry(new TestLookup());
    }

    private AnimationRegistry registry(AnimationLookup lookup) {
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
                name -> AnimationRegistryTest.class.getResourceAsStream("/" + name), lookup);
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

    //______________________________________________________________ Files that used to break the reload

    /**
     * Both of these used to escape reload(), which runs in onEnable: one custom file was enough
     * to leave the whole plugin disabled. SnakeYAML throws its own unchecked exception for the
     * first, and the second overflows the stack.
     */
    @Test
    void aFileThatBreaksTheYamlReaderOnlySkipsThatFile() throws IOException {
        write("loop.yml", "items: &items\n  W: *items\nframes:\n  - delay: 1\n");
        write("nested.yml", "frames:\n  - &frame {delay: 1, again: *frame}\n");
        write("sparkle.yml", SIMPLE);
        final AnimationRegistry registry = registry();

        assertTrue(registry.reload(Map.of()));

        assertNotNull(registry.get("sparkle"));
        assertEquals(AnimationRegistry.BUILT_IN.size() + 1, registry.enabled().size());
        assertNull(registry.get("loop"));
        assertNull(registry.get("nested"));
        assertEquals(2, registry.problems(), log::toString);
        assertTrue(logged("Skipped Animations/loop.yml, since it couldn't be read: StackOverflowError"), log::toString);
        assertTrue(logged("Skipped Animations/nested.yml, since it couldn't be read"), log::toString);
    }

    @Test
    void aLookupThatThrowsOnlySkipsThatFile() throws IOException {
        write("bedrock.yml", "items:\n  B: BEDROCK\nframes:\n  - hotbar: 'B........'\n");
        write("sparkle.yml", SIMPLE);
        final TestLookup normal = new TestLookup();
        final AnimationRegistry registry = registry(new AnimationLookup() {
            @Override
            public org.bukkit.Material item(String name) {
                if (name.equals("BEDROCK")) {
                    throw new IllegalStateException("The server refused it");
                }
                return normal.item(name);
            }

            @Override
            public AnimationSound sound(String name) {
                return normal.sound(name);
            }

            @Override
            public org.bukkit.Particle particle(String name) {
                return normal.particle(name);
            }

            @Override
            public Object blockData(String name) {
                return normal.blockData(name);
            }

            @Override
            public Object itemData(String name) {
                return normal.itemData(name);
            }
        });

        assertTrue(registry.reload(Map.of()));

        assertNull(registry.get("bedrock"));
        assertNotNull(registry.get("sparkle"));
        assertEquals(1, registry.problems(), log::toString);
        assertTrue(logged("IllegalStateException: The server refused it"), log::toString);
    }

    @Test
    void aYamlExtensionIsPointedOutInsteadOfIgnored() throws IOException {
        write("sparkle.yaml", SIMPLE);
        final AnimationRegistry registry = registry();
        registry.reload(Map.of());

        assertNull(registry.get("sparkle"));
        assertEquals(1, registry.problems());
        assertTrue(logged("rename it to sparkle.yml"), log::toString);
    }

    @Test
    void anEmptyFileIsSkippedWithAReason() throws IOException {
        write("empty.yml", "");
        final AnimationRegistry registry = registry();
        registry.reload(Map.of());

        assertNull(registry.get("empty"));
        assertEquals(1, registry.problems());
        assertTrue(logged("'frames' is missing"), log::toString);
    }

    @Test
    void aFileThatIsntUtf8StillLoads() throws IOException {
        Files.createDirectories(folder);
        Files.write(folder.resolve("latin.yml"),
                (SIMPLE.replace("S: GLOWSTONE_DUST", "S: {material: GLOWSTONE_DUST, name: 'Café'}"))
                        .getBytes(StandardCharsets.ISO_8859_1));
        final AnimationRegistry registry = registry();
        registry.reload(Map.of());

        assertNotNull(registry.get("latin"), log::toString);
        assertTrue(logged("isn't saved as UTF-8"), log::toString);
    }

    @Test
    void loadedFilesWithWarningsSayWhatToCheck() throws IOException {
        write("sparkle.yml", SIMPLE + "colour: red\n");
        final AnimationRegistry registry = registry();
        registry.reload(Map.of());

        assertNotNull(registry.get("sparkle"));
        assertEquals(0, registry.problems());
        assertTrue(logged("Animations/sparkle.yml loaded, but check this:"), log::toString);
        assertTrue(logged("'colour' isn't an option here"), log::toString);
    }

    @Test
    void namesCanBeUpTo32Characters() throws IOException {
        final String longest = "a".repeat(32);
        write(longest + ".yml", SIMPLE);
        write("b".repeat(33) + ".yml", SIMPLE);
        final AnimationRegistry registry = registry();
        registry.reload(Map.of());

        assertNotNull(registry.get(longest));
        assertNull(registry.get("b".repeat(33)));
        assertEquals(1, registry.problems());
    }

    //______________________________________________________________ Reloading

    /** An animation that's playing keeps the frames it started with, whatever the reload does. */
    @Test
    void aReloadDropsADeletedFileButNotAnAnimationAlreadyInUse() throws IOException {
        write("sparkle.yml", SIMPLE);
        final AnimationRegistry registry = registry();
        registry.reload(Map.of());
        final ClearAnimation playing = registry.get("sparkle");

        Files.delete(folder.resolve("sparkle.yml"));
        registry.reload(Map.of());

        assertNull(registry.get("sparkle"));
        assertEquals(1, playing.frames().size());
        assertEquals("SSSSSSSSS", playing.frames().get(0).hotbar());
    }

    @Test
    void aReloadPicksUpAnEditedFile() throws IOException {
        write("sparkle.yml", SIMPLE);
        final AnimationRegistry registry = registry();
        registry.reload(Map.of());

        write("sparkle.yml", SIMPLE + "weight: 7\nenabled: false\n");
        registry.reload(Map.of());

        assertEquals(7, registry.get("sparkle").weight());
        assertFalse(registry.enabled().contains(registry.get("sparkle")));
    }

    @Test
    void aFixedFileIsNoLongerAProblem() throws IOException {
        write("sparkle.yml", "frames: 3\n");
        final AnimationRegistry registry = registry();
        registry.reload(Map.of());
        assertEquals(1, registry.problems());

        write("sparkle.yml", SIMPLE);
        registry.reload(Map.of());
        assertEquals(0, registry.problems());
        assertNotNull(registry.get("sparkle"));
    }

    @Test
    void oldSwitchNamesIgnoreCase() {
        final AnimationRegistry registry = registry();
        registry.reload(Map.of("explode_animation", false, "PANE_ANIMATION", false));

        assertFalse(registry.get("explosion").enabled());
        assertFalse(registry.get("panes").enabled());
        assertTrue(registry.get("water").enabled());
    }

    @Test
    void everythingIsListedAlphabetically() throws IOException {
        write("zzz.yml", SIMPLE);
        write("aaa.yml", SIMPLE);
        final AnimationRegistry registry = registry();
        registry.reload(Map.of());

        final List<String> names = registry.all().stream().map(ClearAnimation::name).toList();
        assertEquals(names.stream().sorted().toList(), names);
        assertEquals("aaa", names.get(0));
    }

    @Test
    void lookupsByNameIgnoreCaseAndNull() {
        final AnimationRegistry registry = registry();
        registry.reload(Map.of());

        assertNull(registry.get(null));
        assertNull(registry.get("not-there"));
        assertNotNull(registry.get("PaNeS"));
    }

    @Test
    void pickWithOneAnimationOnAlwaysPicksIt() throws IOException {
        for (String name : AnimationRegistry.BUILT_IN) {
            write(name + ".yml", SIMPLE + "enabled: false\n");
        }
        write("only.yml", SIMPLE + "weight: 1000\n");
        final AnimationRegistry registry = registry();
        registry.reload(Map.of());

        for (int i = 0; i < 200; i++) {
            assertEquals("only", registry.pick().name());
        }
    }

    @Test
    void beforeTheFirstReloadNothingIsLoaded() {
        final AnimationRegistry registry = registry();

        assertNull(registry.pick());
        assertTrue(registry.all().isEmpty());
        assertEquals(0, registry.problems());
    }
}
