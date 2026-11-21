package com.zachduda.animatedinventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.bukkit.Material;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;

/**
 * Checks the config.yml and plugin.yml in the jar against what the code reads.
 *
 * The config.yml in the jar is also the default for every key, so a server whose config is
 * missing a key, or has the wrong kind of value for it, falls back to the one here. That only
 * works for keys that are actually in it.
 */
class BundledConfigTest {

    /** A path the code reads only to move it out of old configs, so it's meant to be absent. */
    private static final Set<String> REMOVED_PATHS = Set.of("features.clearing.animations");

    private static final Pattern CONFIG_PATH =
            Pattern.compile("\"((?:messages|features|options)\\.[A-Za-z0-9.\\-]+|config-version)\"");

    private static final Pattern PERMISSION = Pattern.compile("\"(animatedinv\\.[a-z.]+)\"");

    private static String resource(String name) {
        try (InputStream in = BundledConfigTest.class.getResourceAsStream("/" + name)) {
            assertNotNull(in, name + " isn't in the jar");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private static YamlConfiguration config() {
        final YamlConfiguration config = new YamlConfiguration();
        try {
            config.loadFromString(resource("config.yml"));
        } catch (InvalidConfigurationException e) {
            throw new AssertionError("config.yml isn't valid YAML", e);
        }
        return config;
    }

    /** Every Java source file, as one string each. */
    private static Stream<String> sources() throws IOException {
        final Stream<Path> files = Files.walk(Path.of("src"));
        return files.filter(file -> file.toString().endsWith(".java")).map(file -> {
            try {
                return Files.readString(file, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new AssertionError(e);
            }
        }).onClose(files::close);
    }

    private static Set<String> found(Pattern pattern) throws IOException {
        final Set<String> found = new TreeSet<>();
        try (Stream<String> sources = sources()) {
            sources.forEach(source -> {
                final Matcher matcher = pattern.matcher(source);
                while (matcher.find()) {
                    found.add(matcher.group(1));
                }
            });
        }
        return found;
    }

    @Test
    void everyConfigPathTheCodeReadsHasADefault() throws IOException {
        final YamlConfiguration config = config();
        final Set<String> paths = found(CONFIG_PATH);
        paths.removeAll(REMOVED_PATHS);

        assertTrue(paths.size() > 40, () -> "Expected to find the config paths in the code, found " + paths);
        for (String path : paths) {
            assertTrue(config.contains(path), () -> "The code reads " + path + ", but config.yml has no default for it");
        }
    }

    @Test
    void removedPathsStayRemoved() {
        final YamlConfiguration config = config();
        for (String path : REMOVED_PATHS) {
            assertFalse(config.contains(path), path);
        }
    }

    /** A repeated key quietly replaces the first, which is then dead text in every new config. */
    @Test
    void configHasNoRepeatedKeys() {
        final LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        new Yaml(options).load(resource("config.yml"));
    }

    @Test
    void everyMessageIsText() {
        final YamlConfiguration config = config();
        for (String key : config.getConfigurationSection("messages").getKeys(false)) {
            assertTrue(config.isString("messages." + key), "messages." + key);
        }
    }

    @Test
    void defaultsAreInRange() {
        final YamlConfiguration config = config();

        final int goodLuck = config.getInt("features.fortunes.result.good-luck", -1);
        assertTrue(goodLuck >= 0 && goodLuck <= 100, "good-luck is a percent: " + goodLuck);
        assertTrue(config.getInt("options.cooldowns.time") > 0);
        assertTrue(config.getInt("features.clearing.inv-backup.erase-after") > 0);
        assertNotNull(Material.matchMaterial(config.getString("features.clearing.token-item")), "token-item");
        assertTrue(config.isList("features.clearing.skip-slots"));
        assertTrue(config.isList("features.clearing.disabled-worlds"));
        assertTrue(config.isList("features.fortunes.disabled-worlds"));
    }

    @Test
    void everyPermissionTheCodeChecksIsDeclared() throws IOException {
        final Map<String, Object> permissions = declaredPermissions();
        final Set<String> checked = found(PERMISSION);

        assertTrue(checked.size() >= 8, () -> "Expected to find the permission checks, found " + checked);
        for (String permission : checked) {
            assertTrue(permissions.containsKey(permission),
                    () -> "The code checks " + permission + ", but plugin.yml doesn't declare it");
        }
    }

    /** The two wildcards grant every node, and each child has to be a real one. */
    @Test
    @SuppressWarnings("unchecked")
    void wildcardsGrantEveryDeclaredPermission() {
        final Map<String, Object> permissions = declaredPermissions();
        final Set<String> nodes = new TreeSet<>(permissions.keySet());
        nodes.removeIf(node -> node.endsWith("*"));

        for (String wildcard : Set.of("animatedinv.*", "animatedinventory.*")) {
            final Map<String, Object> children =
                    (Map<String, Object>) ((Map<String, Object>) permissions.get(wildcard)).get("children");
            assertEquals(nodes, new TreeSet<>(children.keySet()), wildcard);
        }
    }

    @Test
    void pluginYmlHasItsVersionFilledIn() {
        final Map<String, Object> plugin = new Yaml().load(resource("plugin.yml"));

        assertEquals("com.zachduda.animatedinventory.Main", plugin.get("main"));
        assertFalse(String.valueOf(plugin.get("version")).contains("${"), "The pom didn't filter plugin.yml");
    }

    /** Read without Bukkit, which would split the dotted node names into sections. */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> declaredPermissions() {
        final Map<String, Object> plugin = new Yaml().load(resource("plugin.yml"));
        return (Map<String, Object>) plugin.get("permissions");
    }
}
