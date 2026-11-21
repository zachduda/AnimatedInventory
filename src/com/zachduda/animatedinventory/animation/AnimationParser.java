package com.zachduda.animatedinventory.animation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

/**
 * Reads one animation file, reporting every problem in it at once.
 *
 * Errors mean the animation can't be used. Warnings, like an option name that isn't
 * recognized, are reported but don't stop it from loading - a misspelled "sound:" is far
 * easier to spot in the console than as a frame that's silently quiet.
 */
public final class AnimationParser {

    /** The longest an animation may run, so a typo can't hold an inventory hostage for an hour. */
    public static final int MAX_TICKS = 1200;

    private static final int MAX_WEIGHT = 1000;
    private static final Set<String> AIR = Set.of("AIR", "CAVE_AIR", "VOID_AIR");
    private static final int MAX_PARTICLE_COUNT = 1000;

    private static final List<String> FILE_OPTIONS = List.of("enabled", "weight", "items", "frames", "finish");
    private static final List<String> FRAME_OPTIONS =
            List.of("delay", "hotbar", "hold", "sounds", "stop-sounds", "particles", "action-bar");
    private static final List<String> FINISH_OPTIONS = List.of("delay", "sounds", "stop-sounds", "particles", "action-bar");
    private static final List<String> ITEM_OPTIONS = List.of("material", "name", "lore", "amount");
    private static final List<String> SOUND_OPTIONS = List.of("sound", "volume", "pitch");
    private static final List<String> STOP_SOUND_OPTIONS = List.of("sound");
    private static final List<String> PARTICLE_OPTIONS =
            List.of("particle", "count", "offset", "speed", "height", "color", "size", "block", "item");
    private static final List<String> PARTICLE_DATA_OPTIONS = List.of("color", "size", "block", "item");

    /** @param animation the parsed animation, or null when there were errors */
    public record Result(ClearAnimation animation, List<String> errors, List<String> warnings) {
        public boolean ok() {
            return animation != null;
        }
    }

    private final AnimationLookup lookup;
    private final List<String> errors = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();

    /** Every symbol 'items' declares, including ones whose item had a problem of its own. */
    private final Set<Character> declared = new HashSet<>();

    private AnimationParser(AnimationLookup lookup) {
        this.lookup = lookup;
    }

    public static Result parse(String name, ConfigurationSection file, AnimationLookup lookup) {
        final AnimationParser parser = new AnimationParser(lookup);
        final ClearAnimation animation = parser.animation(name, file.getValues(false));
        return new Result(parser.errors.isEmpty() ? animation : null,
                List.copyOf(parser.errors), List.copyOf(parser.warnings));
    }

    private ClearAnimation animation(String name, Map<String, Object> file) {
        unknownOptions("", file, FILE_OPTIONS);

        final boolean enabled = bool("", file, "enabled", true);
        final int weight = whole("", file, "weight", 1, 1, MAX_WEIGHT);
        final Map<Character, AnimationItem> items = items(file.get("items"));
        final List<Frame> frames = frames(file.get("frames"), items);
        final Frame finish = finish(file.get("finish"));

        final ClearAnimation animation = new ClearAnimation(name, enabled, weight,
                Collections.unmodifiableMap(items), List.copyOf(frames), finish);
        if (errors.isEmpty() && animation.length() > MAX_TICKS) {
            error("", "It runs for " + animation.length() + " ticks, but an animation can be at most "
                    + MAX_TICKS + " ticks (1 minute) long.");
        }
        return animation;
    }

    //______________________________________________________________ Items

    private Map<Character, AnimationItem> items(Object node) {
        final Map<Character, AnimationItem> items = new LinkedHashMap<>();
        if (node == null) {
            return items;
        }
        final Map<String, Object> symbols = asMap(node);
        if (symbols == null) {
            error("", "'items' must list each symbol with its item, like:  W: WHITE_STAINED_GLASS_PANE");
            return items;
        }
        for (Map.Entry<String, Object> entry : symbols.entrySet()) {
            final String symbol = entry.getKey();
            if (symbol.length() != 1 || !Character.isLetterOrDigit(symbol.charAt(0))) {
                error("items", "'" + symbol + "' can't be a symbol. Use a single letter or digit.");
                continue;
            }
            declared.add(symbol.charAt(0));
            final AnimationItem item = item("item " + symbol, entry.getValue());
            if (item != null) {
                items.put(symbol.charAt(0), item);
            }
        }
        return items;
    }

    private AnimationItem item(String where, Object node) {
        if (node instanceof String materialName) {
            final Material material = material(where, materialName);
            return material == null ? null : new AnimationItem(material, null, List.of(), 1);
        }

        final Map<String, Object> options = asMap(node);
        if (options == null) {
            error(where, "Must be a material name, or a section with a 'material' in it.");
            return null;
        }
        unknownOptions(where, options, ITEM_OPTIONS);

        Material material = null;
        if (options.get("material") instanceof String materialName) {
            material = material(where, materialName);
        } else {
            error(where, "Missing its 'material', like:  material: WHITE_STAINED_GLASS_PANE");
        }
        final String name = text(where, options, "name");
        final List<String> lore = lore(where, options.get("lore"));
        final int amount = whole(where, options, "amount", 1, 1, 64);
        return material == null ? null : new AnimationItem(material, name, lore, amount);
    }

    private Material material(String where, String name) {
        final Material material = lookup.item(name);
        if (material != null) {
            return material;
        }
        if (AIR.contains(name.trim().toUpperCase(Locale.ROOT).replace("MINECRAFT:", ""))) {
            error(where, "Can't be air. To empty a slot, put _ in a frame's hotbar.");
        } else {
            error(where, "'" + name.trim() + "' isn't an item on this server.");
        }
        return null;
    }

    private List<String> lore(String where, Object node) {
        if (node == null) {
            return List.of();
        }
        if (isScalar(node)) {
            return List.of(String.valueOf(node));
        }
        if (node instanceof List<?> lines && lines.stream().allMatch(AnimationParser::isScalar)) {
            return lines.stream().map(String::valueOf).toList();
        }
        error(where, "'lore' must be a line of text, or a list of lines.");
        return List.of();
    }

    //______________________________________________________________ Frames

    private List<Frame> frames(Object node, Map<Character, AnimationItem> items) {
        if (!(node instanceof List<?> list)) {
            error("", node == null ? "'frames' is missing. An animation needs at least one frame."
                    : "'frames' must be a list, with each frame starting with a dash (-).");
            return List.of();
        }
        if (list.isEmpty()) {
            error("", "'frames' is empty. An animation needs at least one frame.");
            return List.of();
        }

        final List<Frame> frames = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            final String where = "frame " + (i + 1);
            final Map<String, Object> options = asMap(list.get(i));
            if (options == null) {
                error(where, "Must be a set of options, like:  - delay: 2");
                continue;
            }
            unknownOptions(where, options, FRAME_OPTIONS);
            frames.add(frame(where, options, items, true));
        }
        return frames;
    }

    private Frame finish(Object node) {
        if (node == null) {
            return new Frame(0, null, Frame.NO_HOLD, List.of(), List.of(), List.of(), null);
        }
        final Map<String, Object> options = asMap(node);
        if (options == null) {
            error("", "'finish' must be a set of options, like:  delay: 4");
            return new Frame(0, null, Frame.NO_HOLD, List.of(), List.of(), List.of(), null);
        }
        for (String drawing : List.of("hotbar", "hold")) {
            if (options.containsKey(drawing)) {
                warn("finish", "'" + drawing + "' was ignored. The finish clears the whole inventory, "
                        + "so draw your last frame in 'frames' instead.");
            }
        }
        final Map<String, Object> rest = new LinkedHashMap<>(options);
        rest.keySet().removeAll(List.of("hotbar", "hold"));
        unknownOptions("finish", rest, FINISH_OPTIONS);
        return frame("finish", rest, Map.of(), false);
    }

    private Frame frame(String where, Map<String, Object> options, Map<Character, AnimationItem> items,
                        boolean draws) {
        final int delay = whole(where, options, "delay", 0, 0, MAX_TICKS);
        final String hotbar = draws ? hotbar(where, options.get("hotbar"), items) : null;
        final int hold = draws ? whole(where, options, "hold", Frame.NO_HOLD, 0, Frame.HOTBAR_SIZE - 1) : Frame.NO_HOLD;
        final List<AnimationSound> sounds = sounds(where, options.get("sounds"), true);
        final List<AnimationSound> stopSounds = sounds(where, options.get("stop-sounds"), false);
        final List<AnimationParticle> particles = particles(where, options.get("particles"));
        final String actionBar = text(where, options, "action-bar");
        return new Frame(delay, hotbar, hold, sounds, stopSounds, particles, actionBar);
    }

    private String hotbar(String where, Object node, Map<Character, AnimationItem> items) {
        if (node == null) {
            return null;
        }
        if (!(node instanceof String pattern)) {
            error(where, "'hotbar' must be 9 symbols in quotes, like:  hotbar: 'WW.......'");
            return null;
        }
        if (pattern.length() != Frame.HOTBAR_SIZE) {
            error(where, "'hotbar' must be exactly 9 symbols, one per slot, but '" + pattern + "' has "
                    + pattern.length() + ".");
            return null;
        }
        for (int slot = 0; slot < pattern.length(); slot++) {
            final char symbol = pattern.charAt(slot);
            if (symbol == Frame.KEEP || symbol == Frame.EMPTY || items.containsKey(symbol)) {
                continue;
            }
            if (symbol == ' ') {
                error(where, "'hotbar' has a space in it. Use . to leave a slot alone, or _ to empty it.");
            } else if (!declared.contains(symbol)) {
                error(where, "'hotbar' uses '" + symbol + "', which isn't one of the symbols in 'items'. "
                        + "Use . to leave a slot alone, or _ to empty it.");
            }
            // A declared symbol whose item had a problem has already been reported.
            return null;
        }
        return pattern;
    }

    //______________________________________________________________ Sounds

    private List<AnimationSound> sounds(String where, Object node, boolean playing) {
        if (node == null) {
            return List.of();
        }
        final List<AnimationSound> sounds = new ArrayList<>();
        for (Object entry : node instanceof List<?> list ? list : List.of(node)) {
            final AnimationSound sound = sound(where, entry, playing);
            if (sound != null) {
                sounds.add(sound);
            }
        }
        return List.copyOf(sounds);
    }

    private AnimationSound sound(String where, Object entry, boolean playing) {
        final String key = playing ? "sounds" : "stop-sounds";
        final String name;
        Map<String, Object> options = Map.of();
        if (entry instanceof String plain) {
            name = plain.trim();
        } else {
            options = asMap(entry);
            if (options == null || !(options.get("sound") instanceof String named)) {
                error(where, "Each of its '" + key + "' needs a name, like:  - ENTITY_CHICKEN_EGG  or  "
                        + "- {sound: ENTITY_CHICKEN_EGG, volume: 1.0, pitch: 1.0}");
                return null;
            }
            name = named.trim();
        }

        final String at = where + ", sound " + name;
        unknownOptions(at, options, playing ? SOUND_OPTIONS : STOP_SOUND_OPTIONS);

        final AnimationSound sound = name.isEmpty() ? null : lookup.sound(name);
        if (sound == null) {
            error(where, "'" + name + "' isn't a sound on this server."
                    + (name.contains(" ") ? " To set its volume or pitch, write it as "
                    + "{sound: NAME, volume: 1.0, pitch: 1.0}" : ""));
            return null;
        }
        if (!playing) {
            return sound;
        }
        final double volume = number(at, options, "volume", 1, 0, 10);
        final double pitch = number(at, options, "pitch", 1, 0, 2);
        return sound.withVolumeAndPitch((float) volume, (float) pitch);
    }

    //______________________________________________________________ Particles

    private List<AnimationParticle> particles(String where, Object node) {
        if (node == null) {
            return List.of();
        }
        final List<AnimationParticle> particles = new ArrayList<>();
        for (Object entry : node instanceof List<?> list ? list : List.of(node)) {
            final AnimationParticle particle = particle(where, entry);
            if (particle != null) {
                particles.add(particle);
            }
        }
        return List.copyOf(particles);
    }

    private AnimationParticle particle(String where, Object entry) {
        final String name;
        Map<String, Object> options = Map.of();
        if (entry instanceof String plain) {
            name = plain.trim();
        } else {
            options = asMap(entry);
            if (options == null || !(options.get("particle") instanceof String named)) {
                error(where, "Each of its 'particles' needs a name, like:  - FLAME  or  "
                        + "- {particle: FLAME, count: 3, offset: [0.4, 0.2, 0.4]}");
                return null;
            }
            name = named.trim();
        }

        final String at = where + ", particle " + name;
        unknownOptions(at, options, PARTICLE_OPTIONS);

        final Particle particle = name.isEmpty() ? null : lookup.particle(name);
        if (particle == null) {
            error(where, "'" + name + "' isn't a particle on this server.");
            return null;
        }
        final int count = whole(at, options, "count", 1, 0, MAX_PARTICLE_COUNT);
        final double[] offset = offset(at, options.get("offset"));
        final double speed = number(at, options, "speed", 0, 0, 100);
        final double height = number(at, options, "height", 1, -16, 16);
        final Object data = particleData(at, particle, options);
        return new AnimationParticle(name, particle, count, offset[0], offset[1], offset[2], speed, height, data);
    }

    private double[] offset(String where, Object node) {
        if (node == null) {
            return new double[3];
        }
        if (node instanceof Number spread && inRange(spread.doubleValue(), -16, 16)) {
            return new double[] {spread.doubleValue(), spread.doubleValue(), spread.doubleValue()};
        }
        if (node instanceof List<?> axes && axes.size() == 3
                && axes.stream().allMatch(axis -> axis instanceof Number n && inRange(n.doubleValue(), -16, 16))) {
            return new double[] {
                    ((Number) axes.get(0)).doubleValue(),
                    ((Number) axes.get(1)).doubleValue(),
                    ((Number) axes.get(2)).doubleValue()};
        }
        error(where, "'offset' must be one number, or three like [0.4, 0.2, 0.4], each from -16 to 16.");
        return new double[3];
    }

    /** The extra data a particle needs, or null when it needs none (or it couldn't be read). */
    private Object particleData(String where, Particle particle, Map<String, Object> options) {
        final Class<?> type = particle.getDataType();
        if (type == null || type == Void.class) {
            for (String option : PARTICLE_DATA_OPTIONS) {
                if (options.containsKey(option)) {
                    warn(where, "'" + option + "' was ignored, since this particle doesn't use it.");
                }
            }
            return null;
        }
        if (type == Particle.DustOptions.class) {
            final Color color = color(where, options.get("color"));
            final double size = number(where, options, "size", 1, 0.01, 4);
            return color == null ? null : new Particle.DustOptions(color, (float) size);
        }
        if (type == Color.class) {
            return color(where, options.get("color"));
        }
        if (BlockData.class.isAssignableFrom(type)) {
            return named(where, options, "block", lookup::blockData, "a block");
        }
        if (type == ItemStack.class) {
            return named(where, options, "item", lookup::itemData, "an item");
        }
        error(where, "This particle needs settings that animation files can't set, so pick a different one.");
        return null;
    }

    private Object named(String where, Map<String, Object> options, String option,
                         Function<String, Object> resolve, String kind) {
        if (!(options.get(option) instanceof String name)) {
            error(where, "This particle needs " + kind + ", like:  " + option + ": STONE");
            return null;
        }
        final Object data = resolve.apply(name);
        if (data == null) {
            error(where, "'" + name.trim() + "' isn't " + kind + " on this server.");
        }
        return data;
    }

    private Color color(String where, Object node) {
        if (node instanceof String text) {
            final String hex = text.trim().startsWith("#") ? text.trim().substring(1) : text.trim();
            if (hex.matches("[0-9a-fA-F]{6}")) {
                return Color.fromRGB(Integer.parseInt(hex, 16));
            }
        } else if (node instanceof List<?> rgb && rgb.size() == 3
                && rgb.stream().allMatch(c -> c instanceof Number n && isWhole(n) && inRange(n.doubleValue(), 0, 255))) {
            return Color.fromRGB(((Number) rgb.get(0)).intValue(), ((Number) rgb.get(1)).intValue(),
                    ((Number) rgb.get(2)).intValue());
        }
        error(where, (node == null ? "This particle needs a 'color'" : "'color' isn't a color")
                + ". Write it in quotes like '#FF0000', or as [255, 0, 0]. "
                + "(Without quotes, everything after a # is ignored.)");
        return null;
    }

    //______________________________________________________________ Values

    private boolean bool(String where, Map<String, Object> options, String key, boolean fallback) {
        final Object value = options.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean flag) {
            return flag;
        }
        error(where, "'" + key + "' must be true or false.");
        return fallback;
    }

    private int whole(String where, Map<String, Object> options, String key, int fallback, int min, int max) {
        final Object value = options.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number && isWhole(number) && inRange(number.doubleValue(), min, max)) {
            return number.intValue();
        }
        error(where, "'" + key + "' must be a whole number from " + min + " to " + max + ".");
        return fallback;
    }

    private double number(String where, Map<String, Object> options, String key, double fallback,
                          double min, double max) {
        final Object value = options.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number && inRange(number.doubleValue(), min, max)) {
            return number.doubleValue();
        }
        error(where, "'" + key + "' must be a number from " + plain(min) + " to " + plain(max) + ".");
        return fallback;
    }

    private String text(String where, Map<String, Object> options, String key) {
        final Object value = options.get(key);
        if (value == null) {
            return null;
        }
        if (isScalar(value)) {
            return String.valueOf(value);
        }
        error(where, "'" + key + "' must be text.");
        return null;
    }

    private void unknownOptions(String where, Map<String, Object> options, List<String> known) {
        for (String option : options.keySet()) {
            if (!known.contains(option)) {
                warn(where, "'" + option + "' isn't an option here, so it was ignored. The options are: "
                        + String.join(", ", known) + ".");
            }
        }
    }

    private void error(String where, String message) {
        errors.add(where.isEmpty() ? message : where + ": " + message);
    }

    private void warn(String where, String message) {
        warnings.add(where.isEmpty() ? message : where + ": " + message);
    }

    /**
     * A YAML mapping as a plain map. Bukkit hands back nested mappings as sections, but the
     * mappings inside a list - every frame - as plain maps, so both have to be accepted.
     */
    private static Map<String, Object> asMap(Object node) {
        if (node instanceof ConfigurationSection section) {
            return section.getValues(false);
        }
        if (node instanceof Map<?, ?> map) {
            final Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                copy.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return copy;
        }
        return null;
    }

    private static boolean isScalar(Object value) {
        return value instanceof String || value instanceof Number || value instanceof Boolean;
    }

    private static boolean isWhole(Number number) {
        final double value = number.doubleValue();
        return value == Math.rint(value) && !Double.isInfinite(value);
    }

    private static boolean inRange(double value, double min, double max) {
        return value >= min && value <= max;
    }

    /** 10.0 as "10", 0.01 as "0.01". */
    private static String plain(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value);
    }
}
