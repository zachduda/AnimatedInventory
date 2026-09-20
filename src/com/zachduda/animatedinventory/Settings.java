package com.zachduda.animatedinventory;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * A snapshot of every config value the plugin reads on a hot path.
 *
 * The event handlers and the animation frames used to call getConfig() directly,
 * which walks the YAML tree and splits the path string on every single call.
 * onDamage/onPickUp fire for every entity on the server, so that lookup ran
 * thousands of times a second. Everything read per-tick or per-event now lives
 * here and is refreshed by reload().
 *
 * Message strings are deliberately not cached: they are only read while handling
 * a command, and keeping them in the config means a reload picks them up without
 * having to thread another field through here.
 */
final class Settings {

    private static final Main plugin = Main.getPlugin(Main.class);

    static boolean debug;

    static boolean preventDrop;
    static boolean preventMove;
    static boolean preventPlace;
    static boolean preventPickup;
    static boolean preventSlotChange;
    static boolean useParticles;

    static boolean clearEnabled;
    static boolean confirmPrompt;
    static boolean slotSkipping;
    static boolean clearArmor;
    static boolean slotSwitching;
    static Set<Integer> skipSlots = Collections.emptySet();

    static boolean backupEnabled;
    static boolean backupOneTimeUse;
    static int backupEraseAfter;
    static int backupCooldown;

    static boolean fortuneEnabled;
    static boolean preventIfHurt;
    static boolean healthRestricted;
    static double healthMin;
    static int goodLuck;
    static boolean notifyConsole;

    static boolean cooldownsEnabled;
    static int cooldownTime;

    static boolean clearOverride;
    static boolean updateNotify;

    static Set<String> disabledClearWorlds = Collections.emptySet();
    static Set<String> disabledFortuneWorlds = Collections.emptySet();

    /** Prototype of the marker item parked in slot 22 while a clear runs. */
    static ItemStack token;

    private Settings() {
    }

    static void reload() {
        final FileConfiguration c = plugin.getConfig();

        debug = c.getBoolean("options.debug");

        preventDrop = c.getBoolean("features.prevent-drop");
        preventMove = c.getBoolean("features.prevent-move");
        preventPlace = c.getBoolean("features.prevent-place");
        preventPickup = c.getBoolean("features.prevent-pickup");
        preventSlotChange = c.getBoolean("features.prevent-player-slot-changes");
        useParticles = c.getBoolean("features.use-particles");

        clearEnabled = c.getBoolean("features.clearing.enabled");
        confirmPrompt = c.getBoolean("features.clearing.confirm-prompt");
        slotSkipping = c.getBoolean("features.clearing.enable-slot-skipping");
        clearArmor = c.getBoolean("features.clearing.clear-armor");
        slotSwitching = c.getBoolean("features.clearing.slot-switching");
        skipSlots = new HashSet<>(c.getIntegerList("features.clearing.skip-slots"));

        backupEnabled = c.getBoolean("features.clearing.inv-backup.enabled");
        backupOneTimeUse = c.getBoolean("features.clearing.inv-backup.one-time-use");
        backupEraseAfter = c.getInt("features.clearing.inv-backup.erase-after");
        backupCooldown = c.getInt("features.clearing.inv-backup.backup-cooldown");

        fortuneEnabled = c.getBoolean("features.fortunes.enabled");
        preventIfHurt = c.getBoolean("features.fortunes.prevent-if-being-hurt");
        healthRestricted = c.getBoolean("features.fortunes.health-restriction.enabled");
        healthMin = c.getDouble("features.fortunes.health-restriction.min");
        goodLuck = c.getInt("features.fortunes.result.good-luck");
        notifyConsole = c.getBoolean("features.fortunes.result.notify-console");

        cooldownsEnabled = c.getBoolean("options.cooldowns.enabled");
        cooldownTime = c.getInt("options.cooldowns.time");

        clearOverride = c.getBoolean("options.commands.clear-override");
        updateNotify = c.getBoolean("options.updates.notify");

        disabledClearWorlds = new HashSet<>(c.getStringList("features.clearing.disabled-worlds"));
        disabledFortuneWorlds = new HashSet<>(c.getStringList("features.fortunes.disabled-worlds"));

        token = buildToken(c);
    }

    private static ItemStack buildToken(FileConfiguration c) {
        final String name = c.getString("features.clearing.token-item", "MINECART");
        Material mat = Material.getMaterial(name == null ? "" : name.toUpperCase());
        if (mat == null || mat == Material.AIR) {
            plugin.getLogger().warning("features.clearing.token-item is not a valid material: '"
                    + name + "'. Falling back to MINECART.");
            mat = Material.MINECART;
        }

        final ItemStack item = new ItemStack(mat);
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            final String label = c.getString("features.clearing.token", "&eInventory is being cleared.");
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', label));
            item.setItemMeta(meta);
        }
        return item;
    }

    /** Slots the clear animation must never skip: the hotbar and the token slot. */
    static boolean isSkipped(int slot) {
        return slotSkipping && slot > 8 && slot != 22 && skipSlots.contains(slot);
    }
}
