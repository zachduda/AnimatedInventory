package com.zachduda.animatedinventory.animation;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * The Animations folder: one file per clear animation, built-in or custom.
 *
 * Every {@link #reload} reads each file once and swaps in a fresh snapshot, so nothing touches
 * the disk while an animation plays. An animation already playing keeps the frames it started
 * with, so a reload mid-clear can't change it halfway through.
 */
public final class AnimationRegistry {

    public static final String FOLDER = "Animations";

    /** The animations bundled in the jar, under animations/. */
    public static final List<String> BUILT_IN = List.of("panes", "rainbow", "explosion", "water", "fireball");

    /** Each built-in's old on/off switch, under features.clearing.animations in config.yml. */
    public static final Map<String, String> LEGACY_NAMES = Map.of(
            "Pane_Animation", "panes",
            "Rainbow_Animation", "rainbow",
            "Explode_Animation", "explosion",
            "Water_Animation", "water",
            "Fireball_Animation", "fireball");

    private static final String RESOURCES = "animations/";
    private static final String README = "README.txt";

    private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9_-]{1,32}");

    /** Each bundled file has exactly one of these, which is flipped to start it turned off. */
    private static final Pattern ENABLED_LINE = Pattern.compile("(?m)^enabled: true\\b");

    private record Snapshot(Map<String, ClearAnimation> all, List<ClearAnimation> enabled, int totalWeight,
                            int problems) {
    }

    private static final Snapshot EMPTY = new Snapshot(Map.of(), List.of(), 0, 0);

    private final File folder;
    private final Logger logger;
    private final Function<String, InputStream> resources;
    private final AnimationLookup lookup;

    private volatile Snapshot snapshot = EMPTY;

    /** @param resources opens a file bundled in the plugin jar, like JavaPlugin#getResource */
    public AnimationRegistry(File dataFolder, Logger logger, Function<String, InputStream> resources,
                             AnimationLookup lookup) {
        this.folder = new File(dataFolder, FOLDER);
        this.logger = logger;
        this.resources = resources;
        this.lookup = lookup;
    }

    /** Picks an enabled animation at random, by weight. Null when none are enabled. */
    public ClearAnimation pick() {
        final Snapshot current = snapshot;
        if (current.enabled().isEmpty()) {
            return null;
        }
        int roll = ThreadLocalRandom.current().nextInt(current.totalWeight());
        for (ClearAnimation animation : current.enabled()) {
            roll -= animation.weight();
            if (roll < 0) {
                return animation;
            }
        }
        return current.enabled().get(current.enabled().size() - 1);
    }

    /** Any loaded animation by name, turned off or not. Null if there's none by that name. */
    public ClearAnimation get(String name) {
        return name == null ? null : snapshot.all().get(name.toLowerCase(Locale.ROOT));
    }

    /** Every animation that loaded, alphabetically. */
    public Collection<ClearAnimation> all() {
        return snapshot.all().values();
    }

    public List<ClearAnimation> enabled() {
        return snapshot.enabled();
    }

    /** How many files couldn't be loaded on the last reload. */
    public int problems() {
        return snapshot.problems();
    }

    /**
     * Rereads every animation file, writing out any built-in one that's missing first. Does file
     * I/O, but only a handful of small files.
     *
     * @param legacyToggles the old features.clearing.animations switches from config.yml, keyed by
     *                      their old names (ie: "Explode_Animation"). A built-in that gets written out
     *                      now starts turned off if its old switch was off.
     * @return false if the folder couldn't be made, in which case nothing was loaded
     */
    public boolean reload(Map<String, Boolean> legacyToggles) {
        if (!folder.isDirectory() && !folder.mkdirs()) {
            logger.warning("Couldn't create the " + FOLDER + " folder at " + folder.getPath()
                    + ", so no clear animations were loaded.");
            snapshot = EMPTY;
            return false;
        }

        int problems = 0;
        final Map<String, File> files = new TreeMap<>();
        for (File file : listYaml()) {
            final String base = file.getName().substring(0, file.getName().length() - ".yml".length());
            if (!VALID_NAME.matcher(base).matches()) {
                logger.warning("Skipped " + FOLDER + "/" + file.getName() + ". Animation file names can only use"
                        + " letters, numbers, - and _, and be at most 32 characters long.");
                problems++;
                continue;
            }
            final File clash = files.putIfAbsent(base.toLowerCase(Locale.ROOT), file);
            if (clash != null) {
                logger.warning(FOLDER + "/" + clash.getName() + " and " + FOLDER + "/" + file.getName()
                        + " have the same name, so only " + clash.getName() + " was loaded. Rename or remove the other.");
                problems++;
            }
        }

        writeMissingBuiltIns(files, legacyToggles);
        writeReadme();

        final Map<String, ClearAnimation> all = new LinkedHashMap<>();
        for (Map.Entry<String, File> entry : files.entrySet()) {
            final ClearAnimation animation = load(entry.getKey(), entry.getValue());
            if (animation == null) {
                problems++;
            } else {
                all.put(animation.name(), animation);
            }
        }

        final List<ClearAnimation> enabled = all.values().stream().filter(ClearAnimation::enabled).toList();
        final int totalWeight = enabled.stream().mapToInt(ClearAnimation::weight).sum();
        snapshot = new Snapshot(Collections.unmodifiableMap(all), enabled, totalWeight, problems);
        logSummary(all.values(), enabled);
        return true;
    }

    private List<File> listYaml() {
        final File[] found = folder.listFiles((dir, name) -> !name.startsWith(".")
                && name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (found == null) {
            return List.of();
        }
        Arrays.sort(found, Comparator.comparing(File::getName));
        return Arrays.asList(found);
    }

    /**
     * Puts back any built-in animation whose file is missing, the same way the plugin restores
     * any other file it ships. Turning one off is what 'enabled: false' is for.
     */
    private void writeMissingBuiltIns(Map<String, File> files, Map<String, Boolean> legacyToggles) {
        final List<String> written = new ArrayList<>();
        final List<String> startedOff = new ArrayList<>();
        for (String name : BUILT_IN) {
            if (files.containsKey(name)) {
                continue;
            }
            final File file = new File(folder, name + ".yml");
            if (file.exists()) {
                // Only reached if the folder couldn't be listed. It's there, so load it.
                files.put(name, file);
                continue;
            }
            final boolean off = Boolean.FALSE.equals(legacyToggle(name, legacyToggles));
            if (writeBuiltIn(name, file, off)) {
                files.put(name, file);
                written.add(name);
                if (off) {
                    startedOff.add(name);
                }
            }
        }

        if (written.isEmpty()) {
            return;
        }
        if (written.size() == BUILT_IN.size()) {
            logger.info("Wrote the built-in clear animations to the " + FOLDER + " folder: "
                    + String.join(", ", written) + ".");
        } else {
            logger.info("Restored the missing built-in animation file(s) " + String.join(", ", written)
                    + ". To stop an animation from playing, set 'enabled: false' in its file instead of deleting it.");
        }
        if (!startedOff.isEmpty()) {
            logger.info(String.join(", ", startedOff) + (startedOff.size() == 1 ? " starts" : " start")
                    + " turned off, like in your old config.yml.");
        }
    }

    private static Boolean legacyToggle(String builtIn, Map<String, Boolean> legacyToggles) {
        for (Map.Entry<String, Boolean> toggle : legacyToggles.entrySet()) {
            for (Map.Entry<String, String> legacy : LEGACY_NAMES.entrySet()) {
                if (legacy.getValue().equals(builtIn) && legacy.getKey().equalsIgnoreCase(toggle.getKey())) {
                    return toggle.getValue();
                }
            }
        }
        return null;
    }

    private boolean writeBuiltIn(String name, File file, boolean off) {
        final byte[] bundled = bundled(name + ".yml");
        if (bundled == null) {
            return false;
        }
        String text = new String(bundled, StandardCharsets.UTF_8);
        if (off) {
            text = ENABLED_LINE.matcher(text).replaceFirst("enabled: false");
        }
        try {
            // CREATE_NEW: whatever else goes wrong, an admin's animation file is never written over.
            Files.writeString(file.toPath(), text, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            return true;
        } catch (FileAlreadyExistsException alreadyThere) {
            return false;
        } catch (IOException e) {
            logger.warning("Couldn't write " + FOLDER + "/" + file.getName() + ": " + e.getMessage());
            return false;
        }
    }

    /** README.txt documents the file format, so it's kept in step with the plugin version. */
    private void writeReadme() {
        final byte[] bundled = bundled(README);
        if (bundled == null) {
            return;
        }
        final File file = new File(folder, README);
        try {
            if (!file.isFile() || !Arrays.equals(Files.readAllBytes(file.toPath()), bundled)) {
                Files.write(file.toPath(), bundled);
            }
        } catch (IOException e) {
            logger.warning("Couldn't write " + FOLDER + "/" + README + ": " + e.getMessage());
        }
    }

    private byte[] bundled(String fileName) {
        try (InputStream in = resources.apply(RESOURCES + fileName)) {
            if (in == null) {
                logger.warning(RESOURCES + fileName + " is missing from the plugin jar. Was the jar changed?");
                return null;
            }
            return in.readAllBytes();
        } catch (IOException e) {
            logger.warning("Couldn't read " + RESOURCES + fileName + " from the plugin jar: " + e.getMessage());
            return null;
        }
    }

    private ClearAnimation load(String name, File file) {
        final String where = FOLDER + "/" + file.getName();
        final YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(read(file));
        } catch (IOException e) {
            logger.warning("Couldn't read " + where + ": " + e.getMessage());
            return null;
        } catch (InvalidConfigurationException e) {
            logger.warning("Skipped " + where + ", since it isn't valid YAML. Fix it, then run /ai reload:\n"
                    + e.getMessage());
            return null;
        }

        final AnimationParser.Result result = AnimationParser.parse(name, yaml, lookup);
        if (!result.ok()) {
            logger.warning("Skipped " + where + ". Fix " + (result.errors().size() == 1 ? "this" : "these")
                    + ", then run /ai reload:");
            result.errors().forEach(error -> logger.warning("  - " + error));
            result.warnings().forEach(warning -> logger.warning("  - (warning) " + warning));
        } else if (!result.warnings().isEmpty()) {
            logger.warning(where + " loaded, but check " + (result.warnings().size() == 1 ? "this:" : "these:"));
            result.warnings().forEach(warning -> logger.warning("  - " + warning));
        }
        return result.animation();
    }

    /**
     * Reads a file as UTF-8. One saved in another encoding (ie: "ANSI" from a Windows editor)
     * still loads, with the characters that aren't valid UTF-8 replaced.
     */
    private String read(File file) throws IOException {
        final byte[] bytes = Files.readAllBytes(file.toPath());
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException notUtf8) {
            logger.warning(FOLDER + "/" + file.getName() + " isn't saved as UTF-8, so some of its characters"
                    + " may show up wrong. Re-save it as UTF-8 to fix them.");
            text = new String(bytes, StandardCharsets.UTF_8);
        }
        // Windows Notepad starts UTF-8 files with a byte order mark, which YAML chokes on.
        return text.startsWith("﻿") ? text.substring(1) : text;
    }

    private void logSummary(Collection<ClearAnimation> all, List<ClearAnimation> enabled) {
        if (all.isEmpty()) {
            logger.warning("No clear animations could be loaded from the " + FOLDER + " folder.");
            return;
        }
        final List<String> off = all.stream().filter(a -> !a.enabled()).map(ClearAnimation::name).toList();
        logger.info("Loaded " + enabled.size() + " clear animation" + (enabled.size() == 1 ? "" : "s")
                + (enabled.isEmpty() ? "" : ": " + String.join(", ", enabled.stream().map(ClearAnimation::name).toList()))
                + (off.isEmpty() ? "." : " (turned off: " + String.join(", ", off) + ")."));
    }
}
