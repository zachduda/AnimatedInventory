package com.zachduda.animatedinventory;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Keeps the hotbar a fortune draws over on disk, so a server crash mid-fortune can't lose it.
 *
 * A fortune holds the real hotbar in memory for about ten seconds. If the server dies in that
 * time, and it saved the player's data during it, they come back holding the spinner with no
 * way to get their items back. The file is written as the fortune starts and deleted once the
 * real hotbar has been handed back and saved with the player's data.
 *
 * On join, {@link #decide} says what to do with a file that's still there. It only restores when
 * the player came back holding the spinner: their data was saved mid-fortune, so the file is the
 * only copy of those items. Without the spinner the file is stale and is thrown away, which keeps
 * a crash from handing back items the player still has.
 */
final class FortuneRecovery {

    static final String FOLDER = "Fortunes";

    /** What a player's join, or a plugin restart, should do about a fortune it finds. */
    enum Action {
        /** Nothing to recover. */
        NOTHING,
        /** Put the hotbar in the file back over the spinner. */
        RESTORE,
        /** The player is holding the spinner but there's no file, so just take the spinner away. */
        REMOVE_SPINNER,
        /** A leftover file from a fortune that ended fine. Delete it. */
        DISCARD
    }

    static Action decide(boolean fortuneRunning, boolean holdingSpinner, boolean hasFile) {
        if (fortuneRunning) {
            // Its own copy in memory is the one to trust.
            return Action.NOTHING;
        }
        if (holdingSpinner) {
            return hasFile ? Action.RESTORE : Action.REMOVE_SPINNER;
        }
        return hasFile ? Action.DISCARD : Action.NOTHING;
    }

    private final File folder;
    private final Logger logger;

    FortuneRecovery(File dataFolder, Logger logger) {
        this.folder = new File(dataFolder, FOLDER);
        this.logger = logger;
    }

    File file(UUID id) {
        return new File(folder, id + ".yml");
    }

    boolean has(UUID id) {
        return file(id).isFile();
    }

    /**
     * Writes the hotbar a fortune is about to draw over. The fortune still plays if this fails;
     * it just isn't covered by a crash any more.
     */
    boolean save(UUID id, String playerName, List<?> hotbar) {
        if (!folder.isDirectory() && !folder.mkdirs()) {
            logger.warning("Couldn't create the " + FOLDER + " folder, so " + playerName
                    + "'s hotbar isn't safe from a crash during this fortune.");
            return false;
        }
        final YamlConfiguration file = new YamlConfiguration();
        file.set("Name", playerName);
        file.set("Started", System.currentTimeMillis());
        file.set("Hotbar", hotbar);
        try {
            file.save(file(id));
            return true;
        } catch (IOException | RuntimeException e) {
            logger.warning("Couldn't save " + playerName + "'s hotbar to " + FOLDER + "/" + id
                    + ".yml, so it isn't safe from a crash during this fortune: " + e.getMessage());
            return false;
        }
    }

    /**
     * The hotbar saved for this player, or null when there's no file or it can't be read. A file
     * that can't be read is renamed to .broken, so an admin can still get the items out of it and
     * the player isn't warned about it on every join.
     */
    List<?> read(UUID id) {
        final File source = file(id);
        if (!source.isFile()) {
            return null;
        }
        final YamlConfiguration file = new YamlConfiguration();
        try {
            file.load(source);
        } catch (IOException | InvalidConfigurationException | RuntimeException e) {
            quarantine(source, e.getMessage());
            return null;
        }
        final Object hotbar = file.get("Hotbar");
        if (!(hotbar instanceof List<?> list)) {
            quarantine(source, "it has no hotbar in it");
            return null;
        }
        return list;
    }

    void discard(UUID id) {
        final File file = file(id);
        if (file.exists() && !file.delete()) {
            logger.warning("Couldn't delete " + FOLDER + "/" + file.getName() + ". Delete it by hand.");
        }
    }

    /** Moves a file that can't be used out of the way, keeping it for an admin. */
    void setAside(UUID id, String why) {
        final File file = file(id);
        if (file.isFile()) {
            quarantine(file, why);
        }
    }

    /** Never deletes it: it may be the only copy of the items. */
    private void quarantine(File file, String why) {
        final File broken = new File(folder, file.getName() + ".broken-" + System.currentTimeMillis());
        final boolean moved = file.renameTo(broken);
        logger.warning("Couldn't read " + FOLDER + "/" + file.getName() + " (" + why + "), so that player's hotbar"
                + " from before a crash wasn't put back. " + (moved ? "It's been kept as " + broken.getName()
                : "It's been left where it is") + " in case the items need to be given back by hand.");
    }
}
