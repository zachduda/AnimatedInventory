package com.zachduda.animatedinventory;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import com.zachduda.animatedinventory.animation.AnimationRegistry;
import com.zachduda.animatedinventory.animation.BukkitLookup;
import com.zachduda.animatedinventory.animation.ClearAnimation;
import com.zachduda.animatedinventory.other.Updater;
import com.zachduda.animatedinventory.api.AnimatedInventoryAPI;

public class Main extends JavaPlugin implements Listener {

    private static final int CONFIG_VERSION = 17;

    /** Compiled once; getMCVersion() used to rebuild this on every call. */
    private static final Pattern VERSION_PATTERN = Pattern.compile("^(\\d+)\\.(\\d+)(?:\\.(\\d+))?");

    /** Slots a player can actually carry: the 36 main inventory slots. */
    private static final int MAIN_SLOTS = 36;

    private static final int HOTBAR_SLOTS = 9;

    /** Where each clear animation's on/off switch lived before they moved to the Animations folder. */
    private static final String LEGACY_ANIMATIONS = "features.clearing.animations";

    public AnimatedInventoryAPI api;

    private boolean preventglitch = true;
    private String canceltpmsg = "&c&lSorry. &fYou can't do that while clearing or having a fortune.";

    private Metrics metrics;

    private AnimationRegistry animations;
    private FortuneRecovery fortuneRecovery;
    private boolean warnedAboutProtocolPlugins;

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

    AnimationRegistry getAnimations() {
        return animations;
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

    /** Replaces the old public disabledclearworld list. */
    public Set<String> getDisabledClearWorlds() {
        return Collections.unmodifiableSet(Settings.disabledClearWorlds);
    }

    /** Replaces the old public disabledfortuneworld list. */
    public Set<String> getDisabledFortuneWorlds() {
        return Collections.unmodifiableSet(Settings.disabledFortuneWorlds);
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
        animations = new AnimationRegistry(getDataFolder(), getLogger(), this::getResource, new BukkitLookup());
        fortuneRecovery = new FortuneRecovery(getDataFolder(), getLogger());
        reloadAnimations();
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
    void debugError(Throwable e) {
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

    /**
     * Rereads the Animations folder.
     *
     * The clear animations used to be switched on and off under features.clearing.animations
     * in config.yml. The first time the built-in animation files are written, each one starts
     * turned off if its old switch was, and then the old section is dropped from config.yml.
     */
    void reloadAnimations() {
        final ConfigurationSection legacy = getConfig().getConfigurationSection(LEGACY_ANIMATIONS);
        final Map<String, Boolean> toggles = new HashMap<>();
        if (legacy != null) {
            for (String key : legacy.getKeys(false)) {
                toggles.put(key, legacy.isBoolean(key) ? legacy.getBoolean(key)
                        : legacy.getBoolean(key + ".enabled", true));
            }
        }

        MC1_20.forgetBrokenEffects();
        if (animations.reload(toggles) && legacy != null) {
            getConfig().set(LEGACY_ANIMATIONS, null);
            saveConfig();
            getLogger().info("The clear animations have moved out of config.yml and into the "
                    + AnimationRegistry.FOLDER + " folder, one file each. Turn them on or off with 'enabled' in each file.");
        }
    }

    private void configChecks() {
        if (Settings.debug) {
            getLogger().info("[Debug] Running configChecks()");
        }

        // Not saved to config.yml: once an animation is fixed or turned back on, /ai reload
        // should be all it takes to clear again.
        if (Settings.clearEnabled && animations.enabled().isEmpty()) {
            Settings.clearEnabled = false;
            getLogger().warning("No clear animations are turned on, so clearing is off. Set 'enabled: true' in one of"
                    + " the files in the " + AnimationRegistry.FOLDER + " folder, then run /ai reload.");
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

        // This used to switch the explosion animation off in config.yml. Its file is
        // the one place that says whether it plays now, so only point at it - once.
        final ClearAnimation explosion = animations.get("explosion");
        if (!warnedAboutProtocolPlugins && explosion != null && explosion.enabled()) {
            final String clash = getServer().getPluginManager().isPluginEnabled("ViaVersion") ? "ViaVersion"
                    : getServer().getPluginManager().isPluginEnabled("ProtocolSupport") ? "ProtocolSupport" : null;
            if (clash != null) {
                warnedAboutProtocolPlugins = true;
                getLogger().info("HEADS UP: The explosion animation has had a known bug with " + clash
                        + ". If your players run into it, set 'enabled: false' in "
                        + AnimationRegistry.FOLDER + "/explosion.yml.");
            }
        }

        if (Settings.slotSkipping) {
            final List < Integer > skipslot = getConfig().getIntegerList("features.clearing.skip-slots");
            if (skipslot.stream().anyMatch(slot -> slot >= 0 && slot <= 8)) {
                getLogger().warning("WARNING: You're choosing to skip a slot between 0-8 (Hotbar)."
                        + " These WONT be skipped because they are part of the animations.");
            }
            // Armor and the off-hand follow clear-armor instead, so these were silently ignored.
            final List<Integer> outside = skipslot.stream().filter(slot -> slot < 0 || slot >= MAIN_SLOTS).toList();
            if (!outside.isEmpty()) {
                getLogger().warning("WARNING: skip-slots " + outside + " aren't inventory slots a clear empties"
                        + " (0-35), so they do nothing. Armor and the off-hand follow clear-armor instead.");
            }
            if (skipslot.contains(Settings.TOKEN_SLOT)) {
                getLogger().warning("WARNING: Slot " + Settings.TOKEN_SLOT
                        + ". This is where the AnimatedInventory token is, and WONT be skipped.");
            }
        }
    }

    void noPermission(CommandSender sender) {
        if (sender instanceof Player p) {
            bass(p);
        }
        Msgs.send(sender, getConfig().getString("messages.no-permission"));
    }

    /** True when the sender may not run this command; messages them if so. */
    private boolean denied(CommandSender sender, String... permissions) {
        if (!(sender instanceof Player)) {
            return false;
        }
        if (sender.isOp()) {
            return false;
        }
        for (String permission : permissions) {
            if (sender.hasPermission(permission)) {
                return false;
            }
        }
        noPermission(sender);
        return true;
    }

    void clearMessage(Player p) {
        Msgs.sendBar(p, getConfig().getString("features.clearing.progress-msg"));
    }

    FortuneRecovery fortuneRecovery() {
        return fortuneRecovery;
    }

    /**
     * Holds on to the hotbar a fortune is about to draw over, in memory and on disk.
     *
     * Only the hotbar: that's all a fortune changes. This used to keep the whole inventory and
     * put all of it back, which undid anything that happened to the other slots meanwhile. With
     * prevent-move or prevent-drop off, a stack moved into a chest or dropped mid-fortune came
     * back as well (a duplicate), and with prevent-pickup off, a picked-up item was deleted.
     */
    void saveInv(Player p) {
        final ItemStack[] hotbar = new ItemStack[HOTBAR_SLOTS];
        for (int slot = 0; slot < HOTBAR_SLOTS; slot++) {
            final ItemStack item = p.getInventory().getItem(slot);
            hotbar[slot] = item == null ? null : item.clone();
        }
        Cooldowns.stashInventory(p, hotbar);
        if (fortuneRecovery != null) {
            fortuneRecovery.save(p.getUniqueId(), p.getName(), Arrays.asList(hotbar));
        }
        if (Settings.debug) {
            getLogger().info("[Debug] Saving " + p.getName() + "'s hotbar in system.");
        }
    }

    /** Hands back the hotbar a fortune drew over, if it hasn't been already. */
    @SuppressWarnings("UnstableApiUsage")
    void loadInv(Player p) {
        final ItemStack[] saved = Cooldowns.peekInventory(p);
        if (saved == null) {
            // Nothing stored means it was already handed back, e.g. on quit.
            return;
        }
        for (int slot = 0; slot < HOTBAR_SLOTS; slot++) {
            p.getInventory().setItem(slot, slot < saved.length ? saved[slot] : null);
        }
        p.updateInventory(); // Spigot deprecated. Not always needed, but is a good failsafe.
        settleFortune(p);
        if (Settings.debug) {
            getLogger().info("[Debug] Loading back " + p.getName() + "'s hotbar from system.");
        }
    }

    /**
     * Once the real hotbar is back, saves the player's data and deletes the crash copy.
     *
     * Saving first matters: the server's own autosave may have caught the spinner mid-fortune,
     * and deleting the copy before the restored hotbar reached the disk would leave a crash in
     * between with nothing to restore from.
     */
    private void settleFortune(Player p) {
        if (fortuneRecovery == null || !fortuneRecovery.has(p.getUniqueId())) {
            return;
        }
        try {
            p.saveData();
        } catch (RuntimeException e) {
            // Keep the copy: it's still the safe one.
            getLogger().warning("Couldn't save " + p.getName() + "'s data after their fortune, so its crash copy was kept.");
            debugError(e);
            return;
        }
        fortuneRecovery.discard(p.getUniqueId());
    }

    void deleteInv(Player p) {
        Cooldowns.dropInventory(p);
        if (Settings.debug) {
            getLogger().info("[Debug] Removing system data on " + p.getName() + "'s inventory.");
        }
    }

    void errorMsg(Player p, String what, Throwable e) {
        if (Settings.debug) {
            getLogger().info("----------------------[ERROR]----------------------");
            getLogger().info("Below is the error that occured:");
            e.printStackTrace();
            getLogger().info("Event was returned as: " + e.getMessage());
            getLogger().info("--------------------[ERROR END]--------------------");
        }
        getLogger().warning("Error! Couldn't play the " + what + " to player: " + p.getName());
        p.sendMessage("§c§lError. §fSomething went wrong here. §7Sorry!");
        bass(p);
    }

    /** Called when an animation frame throws; the timeline is already stopped. */
    void frameError(Player p, String what, Throwable e) {
        if (Cooldowns.unmarkClearing(p.getUniqueId())) {
            // The animation has already drawn over the hotbar, so finish the clear the
            // player asked for instead of leaving them holding its items.
            wipeInventory(p);
        }
        abortFortune(p);
        errorMsg(p, what, e);
    }

    /**
     * Ends a fortune that is not going to finish on its own.
     *
     * The real inventory is held in memory for the length of the animation, so
     * without this the player is left holding the spinner and their items stay
     * stranded in the map.
     */
    public void abortFortune(Player p) {
        if (!Cooldowns.unmarkFortune(p.getUniqueId())) {
            return;
        }
        loadInv(p);
        deleteInv(p);
        MC1_20.fireFortuneEnd(p);
    }

    /**
     * Ends a clear once its animation has finished playing.
     *
     * @param message shown instead of done-msg, or null for done-msg
     */
    void finishClear(Player p, String message) {
        wipeInventory(p);
        Cooldowns.removeActive(p);
        Msgs.sendBar(p, message != null ? message : getConfig().getString("features.clearing.done-msg"));
    }

    /**
     * Empties everything a clear empties.
     *
     * The animation writes its frames into the hotbar and parks a marker item in
     * slot 22, so those slots always have to be emptied even when the player has
     * configured them as skipped - otherwise the leftover pane or marker stays in
     * their inventory for good.
     */
    private void wipeInventory(Player p) {
        // Slots 0-8 carry the animation and TOKEN_SLOT the marker, so Settings
        // .isSkipped() forces those clear whatever skip-slots asks for. The
        // off-hand follows clear-armor: a shield is gear in the same sense armor
        // is, and whether it survived a clear used to depend on slot skipping - a
        // setting that has nothing to say about the off-hand.
        if (Settings.slotSkipping) {
            for (int i = 0; i < MAIN_SLOTS; i++) {
                if (!Settings.isSkipped(i)) {
                    p.getInventory().setItem(i, null);
                }
            }

            if (Settings.clearArmor) {
                p.getInventory().setItemInOffHand(null);
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
    }

    void bass(Player sender) {
        sender.playSound(sender.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 5.0F, 1.3F);
    }

    void tick(Player p) {
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 5.0F, 2.0F);
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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) // Makes sure to override any /clear or /ci commands
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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickUp(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player p && Cooldowns.isBusy(p) && (Settings.preventPickup || frozen(p))) {
            event.setCancelled(true);
        }
    }

    /**
     * Whether nothing at all may leave the player's inventory right now, whatever the prevent-
     * settings say.
     *
     * True during a clear while inventory backups are on. The backup is taken as the clear
     * starts, so an item that left the inventory before the wipe - dropped, put in a chest,
     * swapped into an off-hand the clear keeps, placed, eaten or thrown - was in the world and in
     * the backup at once, and /ai undoclear handed it out a second time. Picking things up is
     * held off too: the backup doesn't have them, so the wipe would take them for good.
     */
    private static boolean frozen(Player p) {
        return Settings.backupEnabled && Cooldowns.isClearing(p);
    }

    private static boolean holding(ItemStack item) {
        return item != null && item.getType() != Material.AIR;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onWater(PlayerBucketEmptyEvent e) {
        final Player p = e.getPlayer();
        if (Cooldowns.isBusy(p) && (Settings.preventPlace || frozen(p))) {
            e.setCancelled(true);
        }
    }

    /**
     * prevent-move covers the player's own items. The animation's can never be moved, since
     * one moved into a chest, or into a slot the clear skips, would be the player's to keep.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInv(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p) || !Cooldowns.isBusy(p)) {
            return;
        }
        if (Settings.preventMove || frozen(p) || movesAnimationItem(p, e)) {
            e.setCancelled(true);
        }
    }

    /** The clicked item, the one on the cursor, and the hotbar slot a number key swaps with. */
    private static boolean movesAnimationItem(Player p, InventoryClickEvent e) {
        if (MC1_20.isAnimationItem(p, e.getCurrentItem()) || MC1_20.isAnimationItem(p, e.getCursor())) {
            return true;
        }
        final int button = e.getHotbarButton();
        return button >= 0 && MC1_20.isAnimationItem(p, p.getInventory().getItem(button));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent e) {
        if (e.getWhoClicked() instanceof Player p && Cooldowns.isBusy(p)
                && (Settings.preventMove || frozen(p) || MC1_20.isAnimationItem(p, e.getOldCursor()))) {
            e.setCancelled(true);
        }
    }

    /**
     * The swap-hands key moves items too, but nothing used to stop it. With clear-armor off,
     * the off-hand survives a clear, so an animation item swapped into it was kept.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent e) {
        final Player p = e.getPlayer();
        if (Cooldowns.isBusy(p) && (Settings.preventMove || frozen(p) || MC1_20.isAnimationItem(p, e.getMainHandItem())
                || MC1_20.isAnimationItem(p, e.getOffHandItem()))) {
            e.setCancelled(true);
        }
    }

    /**
     * Using an animation item hands it out too: a TNT minecart put on a rail, an item put in a
     * frame, a fire charge, food. None of those go through the events prevent-place covers, so
     * they worked with every prevent- setting on.
     *
     * Not ignoreCancelled: a right-click on the air arrives already cancelled, and still uses
     * the item unless it's denied here.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onUse(PlayerInteractEvent e) {
        final Player p = e.getPlayer();
        // Right-clicks only: a left-click or a pressure plate can't hand out the item.
        final boolean using = e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK;
        if (using && Cooldowns.isBusy(p)
                && (MC1_20.isAnimationItem(p, e.getItem()) || (frozen(p) && holding(e.getItem())))) {
            e.setCancelled(true);
        }
    }

    /** Item frames, allays, animals and the like. Also gets PlayerInteractAtEntityEvent. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onUseOnEntity(PlayerInteractEntityEvent e) {
        final Player p = e.getPlayer();
        if (!Cooldowns.isBusy(p)) {
            return;
        }
        final ItemStack used = e.getHand() == EquipmentSlot.OFF_HAND
                ? p.getInventory().getItemInOffHand() : p.getInventory().getItemInMainHand();
        if (MC1_20.isAnimationItem(p, used) || (frozen(p) && holding(used))) {
            e.setCancelled(true);
        }
    }

    /** Has its own handler list, so onUseOnEntity doesn't see it. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent e) {
        final Player p = e.getPlayer();
        if (Cooldowns.isBusy(p) && (frozen(p) || MC1_20.isAnimationItem(p, e.getPlayerItem()))) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent e) {
        final Player p = e.getPlayer();
        if (Cooldowns.isBusy(p) && (frozen(p) || MC1_20.isAnimationItem(p, e.getItem()))) {
            e.setCancelled(true);
        }
    }

    /** prevent-drop covers the player's own items. The animation's are never theirs to drop. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent e) {
        final Player p = e.getPlayer();
        if (Cooldowns.isBusy(p)
                && (Settings.preventDrop || frozen(p) || MC1_20.isAnimationItem(p, e.getItemDrop().getItemStack()))) {
            e.setCancelled(true);
        }
    }

    /**
     * Someone died mid-animation.
     *
     * With prevent-drop on, or during a clear while inventory backups are on (see frozen()),
     * they keep their inventory, so the animation can finish with it: a
     * clear empties what it always empties, and a fortune puts the hotbar back. This used to
     * cancel the drops and let the inventory be emptied, destroying what a clear would have kept
     * (skipped slots, and armor with clear-armor off).
     *
     * With it off, it's an ordinary death, except the animation's own items never drop. They
     * used to, so dying mid-clear handed out the animation's TNT, water buckets and marker. A
     * fortune drops the real hotbar it was holding in place of its spinner, and ends there.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        final Player p = event.getEntity();
        if (!Cooldowns.isBusy(p)) {
            return;
        }

        if (Settings.preventDrop || frozen(p)) {
            event.setKeepInventory(true);
            event.getDrops().clear();
            getLogger().info(p.getName() + " died. Their drops were canceled!");
            Msgs.send(p, getConfig().getString("messages.death"));
            return;
        }

        event.getDrops().removeIf(drop -> MC1_20.isAnimationItem(p, drop));
        if (Cooldowns.isFortune(p) && !event.getKeepInventory()) {
            final ItemStack[] hotbar = Cooldowns.peekInventory(p);
            if (hotbar != null) {
                for (ItemStack item : hotbar) {
                    if (item != null && item.getType() != Material.AIR) {
                        event.getDrops().add(item);
                    }
                }
            }
            endFortuneUnrestored(p);
        }
    }

    /**
     * Ends a fortune whose hotbar has gone somewhere other than back into the inventory (onto the
     * ground, on death). The copy on disk goes too, so a crash can't hand those items back twice.
     */
    private void endFortuneUnrestored(Player p) {
        Timeline.stop(p);
        Cooldowns.unmarkFortune(p.getUniqueId());
        deleteInv(p);
        if (fortuneRecovery != null) {
            fortuneRecovery.discard(p.getUniqueId());
        }
        MC1_20.fireFortuneEnd(p);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
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

        abortFortune(player);

        Cooldowns.removeAll(player);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerPlaceBlock(BlockPlaceEvent e) {
        final Player p = e.getPlayer();
        if (Cooldowns.isBusy(p) && (Settings.preventPlace || frozen(p) || MC1_20.isAnimationItem(p, e.getItemInHand()))) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerBucketFill(PlayerBucketFillEvent e) {
        final Player p = e.getPlayer();
        if (Cooldowns.isBusy(p) && (Settings.preventPlace || frozen(p))) {
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

        if (args.length == 3 && args[0].equalsIgnoreCase("clear")) {
            clearOther(sender, args[1], args[2]);
            return true;
        }

        if (sender instanceof Player p) {
            bass(p);
        }
        Msgs.send(sender, "&cToo many args for: &f/" + cmd.getName() + " " + args[0]);
        return true;
    }

    private boolean oneArg(CommandSender sender, String sub) {
        switch (sub.toLowerCase(Locale.ROOT)) {
            case "help" -> sendHelp(sender);

            case "version" -> {
                Msgs.send(sender, "&7You're currently running &f&lv" + getDescription().getVersion());
                if (sender instanceof Player p) {
                    pop(p);
                }
            }

            case "purge" -> {
                // plugin.yml declares animatedinv.purge, but only admin was ever
                // checked, so granting the declared node did nothing.
                if (denied(sender, "animatedinv.purge", "animatedinv.admin")) {
                    return true;
                }
                if (sender instanceof Player p && Cooldowns.onFileCooldown(p)) {
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
                abortFortune(p);
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
                reloadAnimations();
                configChecks();
                Msgs.send(sender, getConfig().getString("messages.reload"));
                if (animations.problems() > 0) {
                    Msgs.send(sender, "&e&lHeads up! &f" + animations.problems() + " animation file"
                            + (animations.problems() == 1 ? "" : "s") + " couldn't be loaded. The console says what to fix.");
                }
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
                if (sub.toLowerCase(Locale.ROOT).contains("undo")) {
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
            sender.sendMessage(others ? "§7/ai clear (player) (animation) §f- Shows an animation to clear inventories."
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
            Msgs.send(sender, "&c&lError! &fCouldn't re-enable clearing. Are all the animations in the "
                    + AnimationRegistry.FOLDER + " folder turned off?");
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

        if (Cooldowns.onFileCooldown(p)) {
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

        // MC1_20.fortune marks them, once PlayerFortuneEvent has had its say.
        if (Settings.debug) {
            getLogger().info("[Debug] Self induced fortune: " + p.getName());
        }
        try {
            MC1_20.fortune(p);
        } catch (Exception e) {
            abortFortune(p);
            errorMsg(p, "fortune animation", e);
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
        switch (sub.toLowerCase(Locale.ROOT)) {
            case "undoclear" -> undoClearOther(sender, targetName);
            case "clear" -> clearOther(sender, targetName, null);
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

    /** @param animationName the animation to play, or null for a random one */
    private void clearOther(CommandSender sender, String name, String animationName) {
        if (denied(sender, "animatedinv.clear.others")) {
            return;
        }

        ClearAnimation animation = null;
        if (animationName != null) {
            animation = animations.get(animationName);
            if (animation == null) {
                final String loaded = animations.all().stream().map(ClearAnimation::name)
                        .collect(Collectors.joining(", "));
                fail(sender, "&c&lHm. &fThere's no animation called &7" + animationName + "&f. "
                        + (loaded.isEmpty() ? "None are loaded." : "Try: &7" + loaded));
                return;
            }
        } else if (animations.enabled().isEmpty()) {
            // Clear.go would only log this, after the sender had been told it worked.
            fail(sender, "&c&lHm. &fNo clear animations are turned on. Name one to play it anyway, or set"
                    + " 'enabled: true' in a file in the " + AnimationRegistry.FOLDER + " folder.");
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
                getConfig().getString("messages.clear-other-success")).replace("%player%", target.getName()));
        Clear.go(target, animation);
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
                getConfig().getString("messages.fortune-other-success")).replace("%player%", target.getName()));

        // MC1_20.fortune marks them busy before the first frame can run.
        try {
            MC1_20.fortune(target);
        } catch (Exception e) {
            abortFortune(target);
            errorMsg(target, "fortune animation", e);
        }
    }

    // MONITOR and ignoreCancelled: damage another plugin cancelled (ie: spawn protection)
    // never landed, so it mustn't block a fortune.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!Settings.preventIfHurt || e.getCause() == DamageCause.FALL) {
            return;
        }
        if (e.getEntity() instanceof Player player) {
            Cooldowns.hurt(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
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
        if (!Cooldowns.isBusy(p)) {
            return;
        }

        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!p.isOnline()) {
                return;
            }

            Timeline.stop(id);

            if (Cooldowns.isClearing(p) || MC1_20.hasTokenItem(p)) {
                p.getInventory().clear();
                Cooldowns.unmarkClearing(id);
            }

            if (Cooldowns.isFortune(p)) {
                abortFortune(p);
                getLogger().info(p.getName()
                        + "'s fortune is glitched because they switched inventories. Restoring their items.");
            }
        }, 12L);
    }

    /**
     * Tidies up after an animation a crash cut short, which left its items in the player's
     * saved data. A fortune's hotbar comes back from its copy on disk, and a clear is finished,
     * which is what the player had asked for.
     */
    @EventHandler
    public void onJoinRecover(PlayerJoinEvent e) {
        final Player p = e.getPlayer();
        MC1_20.recoverFortune(p);
        if (!Cooldowns.isBusy(p) && MC1_20.hasTokenItem(p)) {
            wipeInventory(p);
            getLogger().info("Finished clearing " + p.getName() + "'s inventory. Their clear was cut short when the"
                    + " server stopped.");
            Msgs.sendPrefix(p, "§c§lSorry! §fYour clear was cut short, so it's been finished now.");
        }
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
