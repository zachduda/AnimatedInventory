package com.zachduda.animatedinventory.other;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

// AnimatedInventory Async Check --> Based off of Benz56's update checker <3
// https://github.com/Benz56/Async-Update-Checker/blob/master/UpdateChecker.java

public class Updater {

    private static final String RELEASES_URL = "https://api.github.com/repos/zachduda/AnimatedInventory/releases";

    private static final long CHECK_INTERVAL = 1_728_000; //In ticks.

    /** Don't nag about this tag; it was published without being a real upgrade. */
    private static final String SKIPPED_TAG = "7.8";

    private static final Pattern VERSION_NUMBERS = Pattern.compile("^[vV]?(\\d+(?:\\.\\d+)*)");

    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 10_000;

    private final JavaPlugin javaPlugin;
    private final String localPluginVersion;

    // Written on an async worker, read from the join handler on the main thread.
    private static volatile String postedver = "???";
    private static volatile boolean outdated = false;

    public Updater(final JavaPlugin javaPlugin) {
        this.javaPlugin = javaPlugin;
        this.localPluginVersion = javaPlugin.getDescription().getVersion();
    }

    public void checkForUpdate() {
        try {
            new BukkitRunnable() {
                @Override
                public void run() {
                    final BukkitRunnable timer = this;
                    Bukkit.getScheduler().runTaskAsynchronously(javaPlugin, () -> {
                        try {
                            if (findNewerRelease()) {
                                Bukkit.getServer().getConsoleSender().sendMessage(
                                        ChatColor.translateAlternateColorCodes('&',
                                                "&r[AnimatedInventory] &e&l&nUpdate Available&r&e&l!&r You're running &7v"
                                                        + localPluginVersion + "&r, while the latest is &av" + postedver));
                                timer.cancel(); //Cancel the runnable as an update has been found.
                            }
                        } catch (final IOException | ParseException e) {
                            Bukkit.getServer().getConsoleSender().sendMessage(ChatColor.RED
                                    + "[AnimatedInventory] Unable to check for updates. Is your server online?");
                            timer.cancel();
                        }
                    });
                }
            }.runTaskTimer(javaPlugin, 0, CHECK_INTERVAL);
        } catch (Exception err) {
            javaPlugin.getLogger().warning("Error. There was a problem checking for updates.");
        }
    }

    private boolean findNewerRelease() throws IOException, ParseException {
        final String body = fetch();
        final JSONArray releases = (JSONArray) new JSONParser().parse(body);

        for (Object o : releases) {
            final JSONObject release = (JSONObject) o;
            if (Boolean.TRUE.equals(release.get("prerelease"))) {
                continue;
            }

            final Object tag = release.get("tag_name");
            if (!(tag instanceof String)) {
                return false;
            }

            final String vs = ((String) tag).replaceFirst("^[vV]", "");
            // Only the newest stable release matters, so stop either way.
            if (isNewer(vs, localPluginVersion) && !vs.equalsIgnoreCase(SKIPPED_TAG)) {
                postedver = vs;
                outdated = true;
                return true;
            }
            return false;
        }
        return false;
    }

    /**
     * Reads the releases feed.
     *
     * The old version opened the stream with no timeouts, never closed the
     * reader, and only took its first line - so a slow endpoint could pin a
     * worker thread indefinitely and a pretty-printed response would fail to
     * parse.
     */
    private String fetch() throws IOException {
        final HttpURLConnection connection =
                (HttpURLConnection) URI.create(RELEASES_URL).toURL().openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("User-Agent", "AnimatedInventory/" + localPluginVersion);

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining());
        } finally {
            connection.disconnect();
        }
    }

    /**
     * Whether {@code latest} is a later version than {@code current}, comparing the numbers
     * part by part ("8.10" is after "8.9", "8.1" is the same as "8.1.0").
     *
     * This used to be any difference at all, so a server running a build newer than the last
     * release was told to "update" to the older one. Versions that don't start with a number
     * still fall back to that.
     */
    static boolean isNewer(String latest, String current) {
        final int[] a = numbers(latest);
        final int[] b = numbers(current);
        if (a == null || b == null) {
            return !latest.trim().equalsIgnoreCase(current.trim());
        }
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            final int x = i < a.length ? a[i] : 0;
            final int y = i < b.length ? b[i] : 0;
            if (x != y) {
                return x > y;
            }
        }
        return false;
    }

    /** "v8.0.1-SNAPSHOT" as {8, 0, 1}, or null when it doesn't start with a number. */
    private static int[] numbers(String version) {
        final Matcher matcher = VERSION_NUMBERS.matcher(version.trim());
        if (!matcher.find()) {
            return null;
        }
        try {
            return Arrays.stream(matcher.group(1).split("\\.")).mapToInt(Integer::parseInt).toArray();
        } catch (NumberFormatException tooLong) {
            return null;
        }
    }

    public static boolean isOutdated() {
        return outdated;
    }

    public static String getPostedVersion() {
        return postedver;
    }
}
