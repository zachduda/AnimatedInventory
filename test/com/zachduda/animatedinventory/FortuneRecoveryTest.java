package com.zachduda.animatedinventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The crash copy of a fortune's hotbar. Real items need a running server to save, so these use
 * text in their place; the file handling and the rules about when to restore are the same.
 */
class FortuneRecoveryTest {

    private static final UUID STEVE = UUID.randomUUID();

    @TempDir
    File dataFolder;

    private final List<String> log = new ArrayList<>();
    private FortuneRecovery recovery;

    @BeforeEach
    void setUp() {
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
        recovery = new FortuneRecovery(dataFolder, logger);
    }

    private Path folder() {
        return dataFolder.toPath().resolve(FortuneRecovery.FOLDER);
    }

    private boolean logged(String text) {
        return log.stream().anyMatch(line -> line.contains(text));
    }

    /**
     * The whole rule. Only a player who came back holding the spinner gets the copy: their data
     * was saved mid-fortune, so it's the only place those items are. Anyone else already has
     * their hotbar, and handing it over again would duplicate it.
     */
    @ParameterizedTest(name = "running={0} spinner={1} file={2} -> {3}")
    @CsvSource(textBlock = """
            true,  true,  true,  NOTHING
            true,  true,  false, NOTHING
            true,  false, true,  NOTHING
            true,  false, false, NOTHING
            false, true,  true,  RESTORE
            false, true,  false, REMOVE_SPINNER
            false, false, true,  DISCARD
            false, false, false, NOTHING
            """)
    void decidesWhatToDoAfterACrash(boolean running, boolean spinner, boolean file, FortuneRecovery.Action action) {
        assertEquals(action, FortuneRecovery.decide(running, spinner, file));
    }

    @Test
    void savesAndReadsBackAHotbarWithEmptySlots() {
        final List<String> hotbar = Arrays.asList("sword", null, "torches", null, null, null, null, null, "bread");

        assertTrue(recovery.save(STEVE, "Steve", hotbar));

        assertTrue(recovery.has(STEVE));
        assertEquals(hotbar, recovery.read(STEVE));
        assertTrue(Files.isRegularFile(folder().resolve(STEVE + ".yml")));
    }

    @Test
    void aLaterFortuneReplacesTheCopy() {
        recovery.save(STEVE, "Steve", List.of("old"));
        recovery.save(STEVE, "Steve", List.of("new"));

        assertEquals(List.of("new"), recovery.read(STEVE));
    }

    @Test
    void readingDoesntDeleteTheCopy() {
        recovery.save(STEVE, "Steve", List.of("sword"));
        recovery.read(STEVE);

        assertTrue(recovery.has(STEVE), "Only discard() deletes it, once the restored hotbar is saved");
    }

    @Test
    void discardingRemovesIt() {
        recovery.save(STEVE, "Steve", List.of("sword"));

        recovery.discard(STEVE);

        assertFalse(recovery.has(STEVE));
        assertNull(recovery.read(STEVE));
    }

    @Test
    void noCopyIsNull() {
        assertFalse(recovery.has(STEVE));
        assertNull(recovery.read(STEVE));
        recovery.discard(STEVE);
        assertTrue(log.isEmpty(), log::toString);
    }

    @Test
    void playersHaveTheirOwnCopies() {
        final UUID alex = UUID.randomUUID();
        recovery.save(STEVE, "Steve", List.of("sword"));
        recovery.save(alex, "Alex", List.of("bow"));

        recovery.discard(STEVE);

        assertEquals(List.of("bow"), recovery.read(alex));
    }

    /** It may be the only copy of the items, so it's set aside for an admin, never deleted. */
    @Test
    void aCopyThatCantBeReadIsKeptAside() throws IOException {
        Files.createDirectories(folder());
        Files.writeString(folder().resolve(STEVE + ".yml"), "Hotbar: [unclosed\n");

        assertNull(recovery.read(STEVE));

        assertFalse(recovery.has(STEVE), "Moved aside, so the player isn't warned on every join");
        try (var files = Files.list(folder())) {
            assertTrue(files.anyMatch(file -> file.getFileName().toString().startsWith(STEVE + ".yml.broken-")));
        }
        assertTrue(logged("in case the items need to be given back by hand"), log::toString);
    }

    @Test
    void aCopyWithNoHotbarIsKeptAsideToo() throws IOException {
        Files.createDirectories(folder());
        Files.writeString(folder().resolve(STEVE + ".yml"), "Name: Steve\n");

        assertNull(recovery.read(STEVE));
        assertFalse(recovery.has(STEVE));
        assertTrue(logged("it has no hotbar in it"), log::toString);
    }

    @Test
    void aCopyCanBeSetAsideByTheCaller() {
        recovery.save(STEVE, "Steve", List.of("not an item"));

        recovery.setAside(STEVE, "it holds something that isn't an item on this server");

        assertFalse(recovery.has(STEVE));
        assertTrue(logged("isn't an item on this server"), log::toString);
    }

    /** The fortune still plays without it; it just says it isn't covered. */
    @Test
    void aFolderThatCantBeMadeIsReported() throws IOException {
        Files.writeString(folder(), "A file where the folder should be");

        assertFalse(recovery.save(STEVE, "Steve", List.of("sword")));
        assertTrue(logged("isn't safe from a crash during this fortune"), log::toString);
    }
}
