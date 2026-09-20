package com.zachduda.animatedinventory;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import com.zachduda.animatedinventory.other.Updater;
import com.zachduda.animatedinventory.api.AnimatedInventoryAPI;

public class Main extends JavaPlugin implements Listener {

    private static final int CONFIG_VERSION = 17;

    /** Compiled once; getMCVersion() used to rebuild this on every call. */
    private static final Pattern VERSION_PATTERN = Pattern.compile("^(\\d+)\\.(\\d+)(?:\\.(\\d+))?");

    /** The slot the clear animation parks its marker item in. */
    private static final int TOKEN_SLOT = 22;

    /** Slots a player can actually carry: the 36 main inventory slots. */
    private static final int MAIN_SLOTS = 36;

    public AnimatedInventoryAPI api;

    private boolean preventglitch = true;
    private String canceltpmsg = "&c&lSorry. &fYou can't do that while clearing or having a fortune.";

    private Metrics metrics;

    private int mcMajorVersion;
    private int mcMinorVersion;
    private String mcVersion;
    private boolean supported;

    /**
     * Reads the server's Minecraft version as "major.minor".
     *
     * The result is memoised: this used to recompile the regex and rematch on
     * every call, including from the async join handler.
     */
    public String getMCVersion() {
        if (mcVersion != null) {
            return mcVersion;
        }

        final String raw = Bukkit.getBukkitVersion().toUpperCase().replaceAll("-.+$", "");
        final Matcher version = VERSION_PATTERN.matcher(raw);

        if (!version.find()) {
            mcVersion = "X.XX";
            return mcVersion;
        }

        mcMajorVersion = Integer.parseInt(version.group(1));
        mcMinorVersion = Integer.parseInt(version.group(2));
        mcVersion = mcMajorVersion + "." + mcMinorVersion;
        return mcVersion;
    }

    public boolean isSupported() {
        return supported;
    }

    public boolean isClearEnabled() {
        return Settings.clearEnabled;
    }

    public boolean isFortuneEnabled() {
        return Settings.fortuneEnabled;
    }

    public boolean isClearDisabledIn(String world) {
        return Settings.disabledClearWorlds.contains(world);
    }

    public boolean isFortuneDisabledIn(String world) {
        return Settings.disabledFortuneWorlds.contains(world);
    }

    private boolean checkSupported() {
        getMCVersion();
        if (mcMajorVersion == 1) {
            if (mcMinorVersion >= 13) {
                return true;
            }
            getLogger().warning("AnimatedInventory cannot run on 1.12 or lower.");
            return false;
        }
        return mcMajorVersion >= 26;
    }

    @SuppressWarnings("deprecation")
    public void onEnable() {
        supported = checkSupported();
        if (!supported) {
            getLogger().warning("> This plugin may not work for this version of Minecraft. (Supports 1.13 and up)");
        }

        api = new AnimatedInventoryAPI();
        getConfig().options().copyDefaults(true);
        getConfig().options().header("Thanks for downloading AnimatedInventory! When installing new updates \n to our plugin, check the console to see if you need to reset your config.yml\n\nFor slot numbers see: https://gamepedia.cursecdn.com/minecraft_gamepedia/b/b2/Items_slot_number.png");
        saveConfig();

        upgradeConfig();
        reloadSettings();
        configChecks();

        Clear.purgeCache();

        if (getConfig().getBoolean("options.metrics")) {
            try {
                metrics = new Metrics(this, 3079);
            } catch (Exception e) {
                getLogger().info("Error when setting Metrics, setting to false.");
                getConfig().set("options.metrics", false);
                saveConfig();
            }
        }

        if (Settings.updateNotify) {
            try {
                new Updater(this).checkForUpdate();
            } catch (Exception e) {
                getLogger().warning("There was an issue while trying to check for updates.");
            }
        }

        if (Settings.debug) {
            getLogger().info("[Debug] Using Minecraft Version: " + getMCVersion());
        }

        safely("check players inventories on enable", MC1_20::emergencyRemove);

        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new GUI(), this);
        getLogger().info("Done! Ready to initialize awesome.");
    }

    public void onDisable() {
        // Kill every in-flight animation before anything else, so no queued frame
        // can write into an inventory we are about to hand back to the player.
        Timeline.stopAll();

        safely("check players inventories on disable", MC1_20::emergencyRemove);

        if (metrics != null) {
            metrics.shutdown();
        }

        for (final Player online : Bukkit.getServer().getOnlinePlayers()) {
            Cooldowns.removeAll(online);
        }
    }

    private void safely(String what, Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            getLogger().warning("Error when trying to " + what + ".");
            debugError(e);
        }
    }

    /** Prints a stack trace only when debug mode is on. */
    void debugError(Exception e) {
        if (Settings.debug) {
            getLogger().info("[Debug] Error below: ------------------------------");
            e.printStackTrace();
            getLogger().info("[Debug] End of Error ------------------------------");
        }
    }

    private void upgradeConfig() {
        try {
            if (getConfig().getInt("config-version") == CONFIG_VERSION) {
                return;
            }

            if (getConfig().getInt("config-version") <= 13) {
                getLogger().warning("WARNING: Your config is EXTREMELY old. A reset is recommended.");
            }

            getLogger().info("We have added new features into your configuration.");
            getConfig().set("features.clearing.enable-slot-skipping", false);
            getConfig().set("features.clearing.clear-armor", true);
            getConfig().set("config-version", CONFIG_VERSION);
            saveConfig();
        } catch (Exception e) {
            try {
                getConfig().options().copyDefaults(true);
                saveConfig();
                getLogger().warning("Unable to check config-version... Restoring Missing Values...");
                reloadConfig();
            } catch (Exception e1) {
                getLogger().severe("Config version checking/updating FAILED.");
            }
        }
    }

    /** Refreshes the cached config snapshot and the values derived from it. */
    void reloadSettings() {
        Settings.reload();

        final int worlds = Bukkit.getWorlds().size();
        preventglitch = worlds > 1;
        if (preventglitch) {
            getLogger().info("Found " + worlds
                    + " loaded worlds. We'll block TP'ing during fortunes/clearing to prevent inventory glitches.");
        }

        final String tpmsg = getConfig().getString("messages.tp-cancelled");
        if (tpmsg != null) {
            canceltpmsg = tpmsg;
        }
    }

    private void configChecks() {
        if (Settings.debug) {
            getLogger().info("[Debug] Running configChecks()");
        }

        if (Settings.clearEnabled && noAnimationsEnabled()) {
            getConfig().set("features.clearing.enabled", false);
            saveConfig();
            Settings.clearEnabled = false;
            getLogger().info("All clear animations were turned off! Disabling Clearing...");
        }

        if (Settings.cooldownsEnabled && Settings.cooldownTime <= 0) {
            getConfig().set("options.cooldowns.enabled", false);
            getConfig().set("options.cooldowns.time", 0);
            saveConfig();
            Settings.cooldownsEnabled = false;
            getLogger().info("Cooldown time was set to 0... Disabling Cooldowns.");
        }

        if (getServer().getPluginManager().isPluginEnabled("Essentials")) {
            getLogger().info("Found Essentials. /clear & /ci override "
                    + (Settings.clearOverride ? "enabled" : "disabled") + " in config.");
        }

        // The key here was missing its ".enabled" suffix, so this guard never ran
        // and the set() below would have replaced the whole animation section with
        // a bare boolean if it ever had.
        if (getConfig().getBoolean("features.clearing.animations.Explode_Animation.enabled")) {
            final String clash = getServer().getPluginManager().isPluginEnabled("ViaVersion") ? "ViaVersion"
                    : getServer().getPluginManager().isPluginEnabled("ProtocolSupport") ? "ProtocolSupport" : null;
            if (clash != null) {
                getLogger().info("HEADS UP: The TNT Animation has a known bug with " + clash
                        + ". We've disabled this animation for you.");
                getConfig().set("features.clearing.animations.Explode_Animation.enabled", false);
                saveConfig();
            }
        }

        if (Settings.slotSkipping) {
            final List < Integer > skipslot = getConfig().getIntegerList("features.clearing.skip-slots");
            if (skipslot.stream().anyMatch(slot -> slot >= 0 && slot <= 8)) {
                getLogger().warning("WARNING: You're choosing to skip a slot between 1-8 (Hotbar)."
                        + " These WONT be skipped because they are part of the animations.");
            }
            if (skipslot.contains(TOKEN_SLOT)) {
                getLogger().warning("WARNING: Slot " + TOKEN_SLOT
                        + ". This is where the AnimatedInventory token is, and WONT be skipped.");
            }
        }
    }

    private boolean noAnimationsEnabled() {
        for (String name : new String[] {"Pane_Animation", "Rainbow_Animation", "Water_Animation",
                "Explode_Animation", "Fireball_Animation"}) {
            if (getConfig().getBoolean("features.clearing.animations." + name + ".enabled")) {
                return false;
            }
        }
        return true;
    }

    void noPermission(CommandSender sender) {
        if (sender instanceof Player p) {
            bass(p);
        }
        Msgs.send(sender, getConfig().getString("messages.no-permission"));
    }

    /** True when the sender may not run this command; messages them if so. */
    private boolean denied(CommandSender sender, String permission) {
        if (!(sender instanceof Player)) {
            return false;
        }
        if (sender.hasPermission(permission) || sender.isOp()) {
            return false;
        }
        noPermission(sender);
        return true;
    }

    void clearMessage(Player p) {
        Msgs.sendBar(p, getConfig().getString("features.clearing.progress-msg"));
    }

    @SuppressWarnings("UnstableApiUsage")
    void saveInv(Player p) {
        Cooldowns.inventories.put(p.getUniqueId(), p.getInventory().getContents());
        p.updateInventory(); // Spigot deprecated. Not always needed, but is a good failsafe.
        if (Settings.debug) {
            getLogger().info("[Debug] Saving " + p.getName() + "'s inventory in system.");
        }
    }

    @SuppressWarnings("UnstableApiUsage")
    void loadInv(Player p) {
        final ItemStack[] saved = Cooldowns.inventories.get(p.getUniqueId());
        if (saved == null) {
            // Nothing stored means it was already handed back, e.g. on quit.
            return;
        }
        p.getInventory().clear();
        p.getInventory().setContents(saved);
        p.updateInventory(); // Spigot deprecated. Not always needed, but is a good failsafe.
        if (Settings.debug) {
            getLogger().info("[Debug] Loading back " + p.getName() + "'s inventory from system.");
        }
    }

    void deleteInv(Player p) {
        Cooldowns.inventories.remove(p.getUniqueId());
        if (Settings.debug) {
            getLogger().info("[Debug] Removing system data on " + p.getName() + "'s inventory.");
        }
    }

    void errorMsg(Player p, int v, Exception e) {
        if (Settings.debug) {
            getLogger().info("----------------------[ERROR]----------------------");
            getLogger().info("Below is the error that occured:");
            e.printStackTrace();
            getLogger().info("Event was returned as: " + e.getMessage());
            getLogger().info("--------------------[ERROR END]--------------------");
        }
        getLogger().warning("Error! Couldn't play Animation #" + v + " to player: " + p.getName());
        p.sendMessage("§c§lError. §fSomething went wrong here. §7Sorry!");
        bass(p);
    }

    /** Called when an animation frame throws; the timeline is already stopped. */
    void frameError(Player p, Exception e) {
        Cooldowns.active.remove(p.getUniqueId());
        Cooldowns.activefortune.remove(p.getUniqueId());
        errorMsg(p, 0, e);
    }

    /**
     * Wipes the inventory once the animation has finished playing.
     *
     * The animation writes its frames into the hotbar and parks a marker item in
     * slot 22, so those slots always have to be emptied even when the player has
     * configured them as skipped - otherwise the leftover pane or marker stays in
     * their inventory for good.
     */
    void finishClear(Player p) {
        doneding(p);
        burp(p);

        if (Settings.slotSkipping) {
            for (int i = 0; i < MAIN_SLOTS; i++) {
                if (!Settings.isSkipped(i)) {
                    p.getInventory().setItem(i, null);
                }
            }
            p.getInventory().setItemInOffHand(null);
            p.getInventory().setItemInMainHand(null);

            if (Settings.clearArmor) {
                p.getInventory().setHelmet(null);
                p.getInventory().setChestplate(null);
                p.getInventory().setLeggings(null);
                p.getInventory().setBoots(null);
            }
        } else if (Settings.clearArmor) {
            p.getInventory().clear();
        } else {
            for (int i = 0; i < MAIN_SLOTS; i++) {
                p.getInventory().setItem(i, null);
            }
        }

        Cooldowns.removeActive(p);
        Msgs.sendBar(p, getConfig().getString("features.clearing.done-msg"));
    }

    void bass(Player sender) {
        sender.playSound(sender.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 5.0F, 1.3F);
    }

    void despsound(Player sender) {
        sender.playSound(sender.getLocation(), Sound.BLOCK_DISPENSER_LAUNCH, 5.0F, 0.4F);
    }

    void fireballshootsound(Player sender) {
        sender.playSound(sender.getLocation(), Sound.ENTITY_GHAST_SHOOT, 5.0F, 0.1F);
    }

    void tick(Player p) {
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 5.0F, 2.0F);
    }

    void doneding(Player p) {
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0F, 2.0F);
    }

    void clearingsound(Player p) {
        p.playSound(p.getLocation(), Sound.ENTITY_DOLPHIN_EAT, 1.0F, 0.1F);
    }

    void burp(Player p) {
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_BURP, 1.0F, 0.9F);
    }

    void pop(Player p) {
        p.playSound(p.getLocation(), Sound.ENTITY_CHICKEN_EGG, 2.0F, 2.0F);
    }

    void levelup(Player p) {
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 2.0F, 2.0F);
    }

    void tntmovesound(Player p) {
        p.playSound(p.getLocation(), Sound.ENTITY_MINECART_INSIDE, 1.0F, 1.4F);
    }

    void tntmovesoundstop(Player p) {
        p.stopSound(Sound.ENTITY_MINECART_INSIDE);
    }

    void tntplacesound(Player p) {
        p.playSound(p.getLocation(), Sound.BLOCK_GRASS_PLACE, 2.0F, 2.0F);
    }

    void boomsound(Player p) {
        p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.0F, 2.0F);
    }

    void waterAmb(Player p) {
        p.playSound(p.getLocation(), Sound.BLOCK_WATER_AMBIENT, 5.0F, 1.0F);
    }

    @EventHandler(priority = EventPriority.HIGHEST) // Make's sure to override any /clear or /ci commands
    public void onCommandPreProcess(PlayerCommandPreprocessEvent e) {
        if (!Settings.clearOverride || !Settings.clearEnabled) {
            return;
        }

        final String message = e.getMessage();
        // These were matched with contains(), so any chat command that merely
        // mentioned "/clear " somewhere in its text got eaten and re-dispatched.
        String args = overrideArgs(message, "/clear");
        if (args == null) {
            args = overrideArgs(message, "/ci");
        }
        if (args == null) {
            return;
        }

        e.setCancelled(true);
        Bukkit.dispatchCommand(e.getPlayer(), args.isEmpty() ? "ai clear" : "ai clear " + args);
    }

    /** Returns the arguments if {@code message} is this command, else null. */
    private String overrideArgs(String message, String command) {
        if (message.equalsIgnoreCase(command)) {
            return "";
        }
        if (message.length() > command.length()
                && message.regionMatches(true, 0, command, 0, command.length())
                && message.charAt(command.length()) == ' ') {
            return message.substring(command.length() + 1).trim();
        }
        return null;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPickUp(EntityPickupItemEvent event) {
        if (!Settings.preventPickup || !(event.getEntity() instanceof Player p)) {
            return;
        }
        if (Cooldowns.isBusy(p)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onWater(PlayerBucketEmptyEvent e) {
        if (Settings.preventPlace && Cooldowns.isBusy(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInv(InventoryClickEvent e) {
        if (!Settings.preventMove || !(e.getWhoClicked() instanceof Player p)) {
            return;
        }
        if (Cooldowns.isBusy(p)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDropItem(PlayerDropItemEvent e) {
        if (Settings.preventDrop && Cooldowns.isBusy(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!Settings.preventDrop) {
            return;
        }
        final Player p = event.getEntity();
        if (!Cooldowns.isBusy(p)) {
            return;
        }

        if (!event.getKeepInventory()) {
            event.getDrops().clear();
        }

        getLogger().info(p.getName() + " died. Their drops were canceled!");
        Msgs.send(p, getConfig().getString("messages.death"));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSlotChange(PlayerItemHeldEvent e) {
        if (Settings.preventSlotChange && Cooldowns.isBusy(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onLeave(PlayerQuitEvent e) {
        final Player player = e.getPlayer();

        // Stop the animation first: its remaining frames would otherwise keep
        // writing panes into the inventory we are about to settle.
        Timeline.stop(player);

        if (Cooldowns.isClearing(player)) {
            player.getInventory().clear();
        }

        if (Cooldowns.isFortune(player)) {
            loadInv(player);
            deleteInv(player);
        }

        Cooldowns.removeAll(player);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerPlaceBlock(BlockPlaceEvent e) {
        if (Settings.preventPlace && Cooldowns.isBusy(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerBucketFill(PlayerBucketFillEvent e) {
        if (Settings.preventPlace && Cooldowns.isBusy(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @SuppressWarnings("NullableProblems")
    public boolean onCommand(CommandSender sender, Command cmd, String cmdLabel, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("");
            sender.sendMessage("§b§lA§r§bnimated §f§lI§r§fnventory");
            sender.sendMessage("§7/ai help §7§ofor commands & links.");
            sender.sendMessage("");
            if (sender instanceof Player p) {
                pop(p);
            }
            return true;
        }

        if (args.length == 1) {
            return oneArg(sender, args[0]);
        }

        if (args.length == 2) {
            return twoArgs(sender, args[0], args[1]);
        }

        if (sender instanceof Player p) {
            bass(p);
        }
        Msgs.send(sender, "&cToo many args for: &f/" + cmd.getName() + " " + args[0]);
        return true;
    }

    private boolean oneArg(CommandSender sender, String sub) {
        switch (sub.toLowerCase()) {
            case "help" -> sendHelp(sender);

            case "version" -> {
                Msgs.send(sender, "&7You're currently running &f&lv" + getDescription().getVersion());
                if (sender instanceof Player p) {
                    pop(p);
                }
            }

            case "purge" -> {
                if (denied(sender, "animatedinv.admin")) {
                    return true;
                }
                if (sender instanceof Player p && Cooldowns.filecooldown.containsKey(p.getUniqueId())) {
                    Msgs.send(sender, backupWaitMsg());
                    bass(p);
                    return true;
                }
                Msgs.send(sender, "&c&lCache Purged. &fAny old cache has been deleted.");
                if (sender instanceof Player p) {
                    pop(p);
                }
                Clear.purgeCache();
            }

            case "undoclear" -> undoClear(sender);

            case "glitched" -> {
                if (!(sender instanceof Player p)) {
                    Msgs.send(sender, "&c&lSorry. &fOnly players can do this.");
                    return true;
                }
                if (denied(sender, "animatedinv.admin")) {
                    return true;
                }
                pop(p);
                Cooldowns.removeAll(p);
                Msgs.send(sender, "&6&lGlitch Fixed. &fWe have tried to fix your sticky situation.");
            }

            case "debug" -> {
                if (denied(sender, "animatedinv.admin")) {
                    return true;
                }
                if (sender instanceof Player p) {
                    pop(p);
                }
                final boolean on = !Settings.debug;
                getConfig().set("options.debug", on);
                saveConfig();
                Settings.debug = on;
                Msgs.send(sender, on ? "&a&lDebug On. &fWe have enabled debug mode."
                        : "&c&lDebug Off. &fWe have disabled debug mode.");
            }

            case "toggle" -> {
                if (denied(sender, "animatedinv.admin")) {
                    return true;
                }
                toggleFeatures(sender);
            }

            case "reload" -> {
                if (denied(sender, "animatedinv.admin")) {
                    return true;
                }
                reloadConfig();
                reloadSettings();
                configChecks();
                Msgs.send(sender, getConfig().getString("messages.reload"));
                if (Settings.debug) {
                    getLogger().info("[Debug] Disabled Clear Worlds: " + Settings.disabledClearWorlds);
                    getLogger().info("[Debug] Disabled Fortune Worlds: " + Settings.disabledFortuneWorlds);
                }
                if (sender instanceof Player p) {
                    levelup(p);
                }
            }

            case "fortune" -> selfFortune(sender);

            case "clear" -> selfClear(sender);

            default -> {
                if (sender instanceof Player p) {
                    bass(p);
                }
                Msgs.send(sender, Objects.requireNonNull(
                        getConfig().getString("messages.not-a-command")).replace("%cmd%", sub));
                if (sub.toLowerCase().contains("undo")) {
                    Msgs.send(sender, getConfig().getString("messages.undo-suggestion"));
                }
            }
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        final boolean others = sender.hasPermission("animatedinv.clear.others") || sender.isOp();
        sender.sendMessage("§8§m------------§r §b§lA§r§bnimated §f§lI§r§fnventory §8§m------------");
        sender.sendMessage("§7/ai help §f- Shows this amazing help menu.");
        if (Settings.clearEnabled) {
            sender.sendMessage(others ? "§7/ai clear (player) §f- Shows an animation to clear inventories."
                    : "§7/ai clear §f- Shows an animation to clear your inventory.");
        } else {
            sender.sendMessage("§c/ai clear §f- Command has been disabled.");
        }
        if (Settings.fortuneEnabled) {
            sender.sendMessage(sender.hasPermission("animatedinv.fortune.others") || sender.isOp()
                    ? "§7/ai fortune (player) §f- Get yes/no answer in an inventory."
                    : "§7/ai fortune §f- Get yes/no answer in your inventory.");
        } else {
            sender.sendMessage("§c/ai fortune §f- Command has been disabled.");
        }
        if (Settings.backupEnabled && (sender.hasPermission("animatedinv.clear.undo") || sender.isOp())) {
            sender.sendMessage("§7/ai undoclear §f- Restores your inventory after a clear.");
        }
        sender.sendMessage("§7/ai version §f- Shows the version of this plugin.");
        if (sender.hasPermission("animatedinv.admin") || sender.isOp()) {
            sender.sendMessage("§7/ai reload §f- Reloads the config.yml.");
            sender.sendMessage("§7/ai toggle §f- Enable/Disable fortune & clearing.");
            if (Settings.backupEnabled) {
                sender.sendMessage("§7/ai purge §f- Purges any old cache.");
            }
        }
        sender.sendMessage("§8§m------------------------------------------");
        if (sender instanceof Player p) {
            pop(p);
        }
    }

    private void toggleFeatures(CommandSender sender) {
        final boolean turningOff = Settings.clearEnabled;
        getConfig().set("features.clearing.enabled", !turningOff);
        getConfig().set("features.fortunes.enabled", !turningOff);
        saveConfig();
        reloadSettings();
        configChecks();

        if (turningOff) {
            Msgs.send(sender, "&fYou have &c&lDISABLED &fclearing & fortunes.");
            if (sender instanceof Player p) {
                pop(p);
            }
            return;
        }

        if (!Settings.clearEnabled) {
            Msgs.send(sender, "&c&lError! &fCouldn't re-enable clearing, are all clearing animations set to false?");
            if (sender instanceof Player p) {
                bass(p);
            }
        } else {
            Msgs.send(sender, "&fYou have &a&lENABLED &fclearing & fortunes.");
            if (sender instanceof Player p) {
                pop(p);
            }
        }
    }

    private String backupWaitMsg() {
        return Objects.requireNonNull(getConfig().getString("messages.backup-must-wait"))
                .replace("%number%", Integer.toString(Settings.backupCooldown));
    }

    /** Renders a backup age the way the restore messages expect it. */
    private static String ago(long secondsAgo) {
        if (secondsAgo < 60) {
            return secondsAgo + "s";
        }
        if (secondsAgo < TimeUnit.HOURS.toSeconds(1)) {
            return (secondsAgo / 60) + "m";
        }
        if (secondsAgo < TimeUnit.DAYS.toSeconds(1)) {
            return (secondsAgo / TimeUnit.HOURS.toSeconds(1)) + "h";
        }
        return (secondsAgo / TimeUnit.DAYS.toSeconds(1)) + "d";
    }

    private void undoClear(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            Msgs.send(sender, getConfig().getString("messages.no-player"));
            return;
        }

        if (!Settings.backupEnabled) {
            bass(p);
            Msgs.send(sender, getConfig().getString("messages.backups-disabled"));
            return;
        }

        if (denied(sender, "animatedinv.clear.undo")) {
            return;
        }

        if (Cooldowns.isClearing(p)) {
            Msgs.send(sender, getConfig().getString("messages.backup-must-wait-clear"));
            bass(p);
            return;
        }

        if (Cooldowns.isFortune(p)) {
            Msgs.send(sender, getConfig().getString("messages.backup-must-wait-fortune"));
            bass(p);
            return;
        }

        if (Cooldowns.filecooldown.containsKey(p.getUniqueId())) {
            Msgs.send(sender, backupWaitMsg());
            bass(p);
            return;
        }

        final Clear.UndoOutcome outcome = Clear.undoClear(p);
        switch (outcome.result()) {
            case RESTORED -> {
                Msgs.send(sender, Objects.requireNonNull(getConfig().getString("messages.backup-restored"))
                        .replace("%time%", ago(outcome.secondsAgo())));
                levelup(p);
            }
            case ALREADY_USED -> {
                Msgs.send(sender, getConfig().getString("messages.backup-already-used"));
                bass(p);
            }
            case NO_FILE -> {
                Msgs.send(sender, getConfig().getString("messages.backup-no-file"));
                bass(p);
            }
            default -> {
                Msgs.send(sender, getConfig().getString("messages.backup-error"));
                bass(p);
            }
        }
    }

    private void selfFortune(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            Msgs.send(sender, getConfig().getString("messages.no-player"));
            return;
        }

        if (Cooldowns.isBusy(p)) {
            return;
        }

        if (denied(sender, "animatedinv.fortune")) {
            return;
        }

        if (!Settings.fortuneEnabled) {
            bass(p);
            Msgs.sendBar(p, getConfig().getString("messages.fortune-disabled"));
            return;
        }

        if (Settings.disabledFortuneWorlds.contains(p.getWorld().getName())) {
            bass(p);
            Msgs.sendBar(sender, getConfig().getString("messages.fortune-world-disabled"));
            return;
        }

        // This used to message the player and then start the fortune anyway.
        if (Settings.healthRestricted && p.getHealth() < Settings.healthMin) {
            bass(p);
            Msgs.sendBar(sender, Objects.requireNonNull(
                            getConfig().getString("messages.fortune-need-more-health"))
                    .replace("%num%", Double.toString(Settings.healthMin / 2)));
            return;
        }

        if (Cooldowns.onCooldown(p)) {
            Msgs.sendBar(p, cooldownMsg());
            bass(p);
            return;
        }

        if (!Cooldowns.notHurt(p)) {
            Msgs.sendBar(p, getConfig().getString("messages.fortune-while-hurt"));
            bass(p);
            return;
        }

        Cooldowns.activefortune.put(p.getUniqueId(), p.getName());
        if (Settings.debug) {
            getLogger().info("[Debug] Self induced fortune: " + p.getName());
        }
        try {
            MC1_20.fortune(p);
        } catch (Exception e) {
            Cooldowns.activefortune.remove(p.getUniqueId());
            errorMsg(p, 10, e);
        }
    }

    private void selfClear(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            Msgs.send(sender, getConfig().getString("messages.no-player"));
            return;
        }

        if (denied(sender, "animatedinv.clear")) {
            return;
        }

        if (!canClear(p)) {
            return;
        }

        if (Settings.confirmPrompt) {
            GUI.confirmGUI(p);
        } else {
            Clear.go(p);
        }
    }

    /**
     * Checks everything except permissions before a clear starts, messaging the
     * player about whatever is in the way.
     *
     * The confirm menu calls this again on YES, because the config could have
     * been reloaded or a cooldown could have started while the prompt sat open.
     */
    boolean canClear(Player p) {
        if (!Settings.clearEnabled) {
            bass(p);
            Msgs.sendBar(p, getConfig().getString("messages.clear-disabled"));
            return false;
        }

        if (Cooldowns.isBusy(p)) {
            return false;
        }

        if (Cooldowns.onCooldown(p)) {
            Msgs.sendBar(p, cooldownMsg());
            bass(p);
            return false;
        }

        if (Settings.disabledClearWorlds.contains(p.getWorld().getName())) {
            bass(p);
            Msgs.sendBar(p, getConfig().getString("messages.clear-world-disabled"));
            return false;
        }

        return true;
    }

    private String cooldownMsg() {
        return Objects.requireNonNull(getConfig().getString("options.cooldowns.msg"))
                .replace("%number%", Integer.toString(Settings.cooldownTime));
    }

    private boolean twoArgs(CommandSender sender, String sub, String targetName) {
        switch (sub.toLowerCase()) {
            case "undoclear" -> undoClearOther(sender, targetName);
            case "clear" -> clearOther(sender, targetName);
            case "fortune" -> fortuneOther(sender, targetName);
            default -> {
                if (sender instanceof Player p) {
                    bass(p);
                }
                Msgs.send(sender, Objects.requireNonNull(
                        getConfig().getString("messages.not-a-command")).replace("%cmd%", sub));
            }
        }
        return true;
    }

    /** Resolves a target, messaging the sender when they can't be acted on. */
    private Player resolveTarget(CommandSender sender, String name) {
        final Player target = Bukkit.getServer().getPlayer(name);
        if (target == null) {
            fail(sender, Objects.requireNonNull(
                    getConfig().getString("messages.not-online")).replace("%player%", name));
            return null;
        }
        if (Cooldowns.isClearing(target)) {
            fail(sender, Objects.requireNonNull(
                    getConfig().getString("messages.already-clearing")).replace("%player%", name));
            return null;
        }
        if (Cooldowns.isFortune(target)) {
            fail(sender, Objects.requireNonNull(
                    getConfig().getString("messages.already-getting-fortune")).replace("%player%", name));
            return null;
        }
        return target;
    }

    private void fail(CommandSender sender, String message) {
        if (sender instanceof Player p) {
            bass(p);
        }
        Msgs.send(sender, message);
    }

    private void undoClearOther(CommandSender sender, String name) {
        if (denied(sender, "animatedinv.clear.undo.others")) {
            return;
        }

        if (!Settings.backupEnabled) {
            fail(sender, getConfig().getString("messages.backups-disabled"));
            return;
        }

        final Player target = resolveTarget(sender, name);
        if (target == null) {
            return;
        }

        final Clear.UndoOutcome outcome = Clear.undoClear(target);
        switch (outcome.result()) {
            case RESTORED -> {
                final String when = ago(outcome.secondsAgo());
                Msgs.send(sender, Objects.requireNonNull(getConfig().getString("messages.backup-restored-other"))
                        .replace("%time%", when).replace("%player%", target.getName()));
                Msgs.send(target, Objects.requireNonNull(getConfig().getString("messages.backup-restored-target"))
                        .replace("%time%", when).replace("%sender%", sender.getName()));
                levelup(target);
                if (sender instanceof Player p) {
                    levelup(p);
                }
            }
            case ALREADY_USED -> fail(sender, getConfig().getString("messages.backup-already-used"));
            case NO_FILE -> fail(sender, Objects.requireNonNull(
                            getConfig().getString("messages.backup-no-file-other"))
                    .replace("%player%", target.getName()));
            default -> fail(sender, "&c&lHm. &fWe were not able to do that to &7" + target.getName());
        }
    }

    private void clearOther(CommandSender sender, String name) {
        if (denied(sender, "animatedinv.clear.others")) {
            return;
        }

        final Player target = resolveTarget(sender, name);
        if (target == null) {
            return;
        }

        if (sender instanceof Player p) {
            levelup(p);
        }
        Msgs.send(sender, Objects.requireNonNull(
                getConfig().getString("messages.clear-other-success")).replace("%player%", name));
        Clear.go(target);
    }

    private void fortuneOther(CommandSender sender, String name) {
        if (denied(sender, "animatedinv.fortune.others")) {
            return;
        }

        final Player target = resolveTarget(sender, name);
        if (target == null) {
            return;
        }

        if (sender instanceof Player p) {
            levelup(p);
        }
        Msgs.send(sender, Objects.requireNonNull(
                getConfig().getString("messages.fortune-other-success")).replace("%player%", name));

        // Mark them busy before the animation starts, so anything the first frame
        // triggers already sees the fortune as running.
        Cooldowns.activefortune.put(target.getUniqueId(), target.getName());
        try {
            MC1_20.fortune(target);
        } catch (Exception e) {
            Cooldowns.activefortune.remove(target.getUniqueId());
            errorMsg(target, 10, e);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        if (!Settings.preventIfHurt || e.getCause() == DamageCause.FALL) {
            return;
        }
        if (e.getEntity() instanceof Player player) {
            Cooldowns.isBeinghurt.put(player.getUniqueId(), System.currentTimeMillis());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onTP(PlayerTeleportEvent e) {
        if (!preventglitch) {
            return;
        }

        final Player p = e.getPlayer();
        if (Cooldowns.isBusy(p)) {
            bass(p);
            Msgs.sendPrefix(p, canceltpmsg);
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onWorld(PlayerChangedWorldEvent e) {
        if (!preventglitch) {
            return;
        }

        final Player p = e.getPlayer();
        final UUID id = p.getUniqueId();

        // Only players the plugin is actually driving can be left in a glitched
        // state. This used to build three item stacks and scan the inventory
        // twice for every world change by every player on the server.
        if (!Cooldowns.active.containsKey(id) && !Cooldowns.activefortune.containsKey(id)) {
            return;
        }

        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!p.isOnline()) {
                return;
            }

            Timeline.stop(id);

            if (Cooldowns.active.containsKey(id) || MC1_20.hasTokenItem(p)) {
                p.getInventory().clear();
                Cooldowns.active.remove(id);
            }

            if (Cooldowns.activefortune.containsKey(id)) {
                p.getInventory().clear();
                loadInv(p);
                deleteInv(p);
                Cooldowns.activefortune.remove(id);
                getLogger().info(p.getName()
                        + "'s fortune is glitched because they switched inventories. Restoring their items.");
            }
        }, 12L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        final Player p = e.getPlayer();

        // This is a dev-join message sent to me only. It's to help me understand which servers support my work <3
        if (p.getUniqueId().toString().equals("6191ff85-e092-4e9a-94bd-63df409c2079")) {
            p.sendMessage(ChatColor.GRAY + "This server is running " + ChatColor.WHITE + "AnimatedInventory "
                    + ChatColor.GOLD + "v" + getDescription().getVersion() + ChatColor.GRAY + " for " + getMCVersion());
        }
        // I kindly ask you leave the above portion in ANY modification of this plugin. Thank You!

        // Permission lookups used to run on an async task, which is not safe with
        // most permission plugins. The update state is already cached, so there is
        // nothing here worth going off-thread for.
        if (Settings.updateNotify && Updater.isOutdated()
                && (p.hasPermission("animatedinv.admin") || p.isOp())) {
            Msgs.sendPrefix(p, "&c&lOutdated Plugin! &7Running v" + getDescription().getVersion()
                    + " while the latest is &f&l" + Updater.getPostedVersion());
            pop(p);
        }
    }
}
