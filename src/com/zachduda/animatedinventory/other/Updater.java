package com.zachduda.animatedinventory.other;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
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

            final String vs = ((String) tag).replace("v", "");
            // Only the newest stable release matters, so stop either way.
            if (!localPluginVersion.equalsIgnoreCase(vs) && !vs.equalsIgnoreCase(SKIPPED_TAG)) {
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

    public static boolean isOutdated() {
        return outdated;
    }

    public static String getPostedVersion() {
        return postedver;
    }
}
