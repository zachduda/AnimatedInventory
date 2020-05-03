package com.zach_attack.inventory;

import java.io.File;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
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
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import com.zach_attack.inventory.other.Updater;
import com.zach_attack.inventory.other.Metrics;
import com.zach_attack.inventory.Cooldowns;
import com.zach_attack.inventory.api.AnimatedInventoryAPI;


public class Main extends JavaPlugin implements Listener {

    public String outdatedpluginversion;
    public boolean outdatedplugin;

    public AnimatedInventoryAPI api;

    public List <String> disabledclearworld = getConfig().getStringList("features.clearing.disabled-worlds");
    public List <String> disabledfortuneworld = getConfig().getStringList("features.fortunes.disabled-worlds");

    private boolean preventglitch = true;
    private String canceltpmsg = "&c&lSorry. &fYou can't do that while clearing or having a fortune.";

    private boolean debug = false;

    private String version = Bukkit.getVersion().toString().replace("-SNAPSHOT", "");

	public void onEnable() {

        if (!version.contains("1.15") && !version.contains("1.14") && !Bukkit.getVersion().contains("1.13")) {
            getLogger().warning("ERROR: This version of AnimatedInventory ONLY supports 1.15.X, 1.14.X, or 1.13.2. Please use AnimatedInventory v6.4 or below!");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        api = new AnimatedInventoryAPI();
        getConfig().options().copyDefaults(true);
        getConfig().options().header("Thanks for downloading AnimatedInventory! When installing new updates \n to our plugin, check the console to see if you need to reset your config.yml\n\nFor slot numbers see: https://gamepedia.cursecdn.com/minecraft_gamepedia/b/b2/Items_slot_number.png");
        saveConfig();

        configChecks();
        updateConfig();

        try {
            if (getConfig().getInt("config-version") != 17) {
                if (getConfig().getInt("config-version") <= 13) {
                    getLogger().warning("WARNING: Your config is EXTREMELY old. A reset is recommended.");
                    saveDefaultConfig();
                }

                getLogger().info("We have added new features into your configuration.");
                getConfig().set("features.clearing.enable-slot-skipping", false);
                getConfig().set("features.clearing.clear-armor", true);
                getConfig().set("config-version", 17);
                saveConfig();
            }
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

        Clear.purgeCache();

        if (getConfig().getBoolean("options.metrics")) {
            Metrics metrics = new Metrics(this);
            try {
                metrics.addCustomChart(new Metrics.SimplePie("update_notifications", () -> {
                    if (getConfig().getBoolean("options.updates.notify")) {
                        return "Enabled";
                    } else {
                        return "Disabled";
                    }
                }));
                metrics.addCustomChart(new Metrics.SimplePie("download_source", () -> {
                        return "Spigot";   // CHANGE BEFORE RELEASES
                }));
            } catch (Exception e) {
                getLogger().info("Error when setting Metrics, setting to false.");
                getConfig().set("options.metrics", false);
                saveConfig();
                reloadConfig();
            }
        }
	
        if (getConfig().getBoolean("options.updates.notify")) {
            try {
                new Updater(this).checkForUpdate();
            } catch (Exception e) {
                getLogger().warning("There was an issue while trying to check for updates.");
            }
        } else {
            outdatedplugin = false;
            outdatedpluginversion = "0";
        }

        if (debug) {
            Bukkit.getConsoleSender().sendMessage("[AnimatedInventory] [Debug] Using Minecraft Version: §a" + Bukkit.getBukkitVersion().toString());
        }

        disabledclearworld.clear();
        disabledfortuneworld.clear();
        disabledclearworld.addAll(getConfig().getStringList("features.clearing.disabled-worlds"));
        disabledfortuneworld.addAll(getConfig().getStringList("features.fortunes.disabled-worlds"));

        try {
            MC1_15.emergencyRemove();
        } catch (Exception e) {
            getLogger().info("Error when trying to check players inventorys on disable event.");
            if (debug) {
                e.printStackTrace();
            }
        }

        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("Done! Ready to initialize awesome.");
    }

    public void onDisable() {
        disabledclearworld.clear();
        disabledfortuneworld.clear();

        if (!version.contains("1.15") && !version.contains("1.14") && !version.contains("1.13")) {
            getLogger().warning("Disabled because the server is not running 1.15.X, 1.14.X, or 1.13.2...");
            return;
        }

        try {
            MC1_15.emergencyRemove();
        } catch (Exception e) {
            getLogger().info("Error when trying to check players inventorys on disable event.");
            if (debug) {
                e.printStackTrace();
            }
        }

        if (Bukkit.getOnlinePlayers().size() >= 1) {
            for (final Player online: Bukkit.getServer().getOnlinePlayers()) {
                Cooldowns.removeAll(online);
            }
        }

    }

    private void updateConfig() {
        if (getConfig().getBoolean("features.clearing.slot-switching")) {
            MC1_15.moveslots = true;
        } else {
            MC1_15.moveslots = false;
        }

        int worlds = Bukkit.getWorlds().size();
        if (worlds > 1) {
            preventglitch = true;
            getLogger().info("Found " + worlds + " loaded worlds. We'll block TP'ing during fortunes/clearing to prevent inventory glitches.");
        } else {
            preventglitch = false;
        }

        canceltpmsg = getConfig().getString("messages.tp-cancelled");

        debug = getConfig().getBoolean("options.debug");
    }

    private void configChecks() {
        if (debug) {
            getLogger().info("[Debug] Running configChecks()");
        }

        if (!getConfig().getBoolean("features.clearing.animations.Pane_Animation.enabled") &&
            !getConfig().getBoolean("features.clearing.animations.Rainbow_Animation.enabled") &&
            !getConfig().getBoolean("features.clearing.animations.Water_Animation.enabled") &&
            !getConfig().getBoolean("features.clearing.animations.Explode_Animation.enabled") &&
            !getConfig().getBoolean("features.clearing.animations.Fireball_Animation.enabled") &&
            getConfig().getBoolean("features.clearing.enabled")) {

            getConfig().set("features.clearing.enabled", false);
            saveConfig();
            reloadConfig();
            getLogger().info("All clear animations were turned off! Disabling Clearing...");
        }

        if (getConfig().getBoolean("options.cooldowns.enabled") &&
            (getConfig().getInt("options.cooldowns.time") == 0 ||
                getConfig().getString("options.cooldowns.time").equalsIgnoreCase("none") ||
                getConfig().getString("options.cooldowns.time").equalsIgnoreCase("disabled") ||
                getConfig().getString("options.cooldowns.time").equalsIgnoreCase("off") ||
                getConfig().getString("options.cooldowns.time") == null)) {
            getConfig().set("options.cooldowns.enabled", false);
            getConfig().set("options.cooldowns.time", 0);
            saveConfig();
            reloadConfig();
            getLogger().info("Cooldown time was set to 0... Disabling Cooldowns.");
        }

        if (getServer().getPluginManager().isPluginEnabled("Essentials") && (getServer().getPluginManager().getPlugin("Essentials") != null)) {
            if (getConfig().getBoolean("override-clear-cmd")) {
                getLogger().info("Found Essentials. /clear & /ci override enabled in config.");
            } else {
                getLogger().info("Found Essentials. /clear & /ci override disabled in config.");
            }
        }
        if (getConfig().getBoolean("features.clearing.animations.Explode_Animation")) {
            if ((getServer().getPluginManager().isPluginEnabled("ViaVersion") && (getServer().getPluginManager().getPlugin("ViaVersion") != null))) {
                if (getConfig().getBoolean("features.clearing.animations.Explode_Animation")) {
                    getLogger().info("HEADS UP: The TNT Animation has a known bug with ViaVersion. We've disabled this animation for you.");
                    getConfig().set("features.clearing.animations.Explode_Animation", false);
                    saveConfig();
                    reloadConfig();
                }
            } else
            if (getServer().getPluginManager().isPluginEnabled("ProtocolSupport") && (getServer().getPluginManager().getPlugin("ProtocolSupport") != null)) {
                if (getConfig().getBoolean("features.clearing.animations.Explode_Animation")) {
                    getLogger().info("HEADS UP: The TNT Animation has a known bug with ProtocolSupport. This bug can cause players to crash!");
                    getConfig().set("features.clearing.animations.Explode_Animation", false);
                    saveConfig();
                    reloadConfig();
                }
            }
        }

        if (getConfig().getBoolean("features.clearing.enable-slot-skipping")) {
            List < Integer > skipslot = getConfig().getIntegerList("features.clearing.skip-slots");
            if (skipslot.contains(0) || skipslot.contains(1) || skipslot.contains(2) || skipslot.contains(3) || skipslot.contains(4) || skipslot.contains(5) || skipslot.contains(6) || skipslot.contains(7) || skipslot.contains(8)) {
                getLogger().warning("WARNING: You're choosing to skip a slot between 1-8 (Hotbar). These WONT be skipped because they are part of the animaions.");
            }

            if (skipslot.contains(22)) {
                getLogger().warning("WARNING: Slot 22. This is where the AnimatedInventory token is, and WONT be skipped.");
            }
            skipslot.clear();
        }
    }

    void noPermission(CommandSender sender) {
        if (sender instanceof Player) {
            Player p = (Player) sender;
            bass(p);
        }

        Msgs.send(sender, getConfig().getString("messages.no-permission"));
    }

    void clearMessage(CommandSender sender) {
        Player p = (Player) sender;
        Msgs.sendBar(p, getConfig().getString("features.clearing.progress-msg"));
    }

    void saveInv(Player p) {
        ItemStack[] inv = p.getInventory().getContents();
        Cooldowns.inventories.put(p, inv);
        p.updateInventory();
        if (debug) {
            getLogger().info("[Debug] Saving " + p.getName() + "'s inventory in system.");
        }
    }

    void loadInv(Player p) {
        p.getInventory().clear();
        p.getInventory().setContents(Cooldowns.inventories.get(p));
        p.updateInventory();
        if (debug) {
            getLogger().info("[Debug] Loading back " + p.getName() + "'s inventory from system.");
        }
    }

    void deleteInv(Player p) {
        Cooldowns.inventories.remove(p);
        if (debug) {
            getLogger().info("[Debug] Removing system data on " + p.getName() + "'s inventory.");
        }
    }

    void errorMsg(Player p, int v, Exception e) {
        if (debug) {
            getLogger().info("----------------------[ERROR]----------------------");
            getLogger().info("Below is the error that occured:");
            e.printStackTrace();
            getLogger().info("Event was returned as: " + e.getMessage());
            getLogger().info("--------------------[ERROR END]--------------------");
        }
        getLogger().info("Error! Couldn't play Animation #" + v + " to player: " + p.getName());
        p.sendMessage("§c§lError. §fSomething went wrong here. §7Sorry!");
        bass(p);
    }


    void cleardone(CommandSender sender) {
        Player p = (Player) sender;
        Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(this, new Runnable() {
            public void run() {
                doneding(p);
                burp(p);

                // Erasing of Inv
                if (getConfig().getBoolean("features.clearing.enable-slot-skipping")) {

                    List <Integer> skipslot = getConfig().getIntegerList("features.clearing.skip-slots");
                    for (int i = 0; i < 36; i++) {
                        if (!skipslot.contains(i)) {
                            p.getInventory().setItem(i, new ItemStack(Material.AIR));
                        }
                    }

                    p.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
                    p.getInventory().setItemInMainHand(new ItemStack(Material.AIR));

                    if (getConfig().getBoolean("features.clearing.clear-armor")) {
                        p.getInventory().setHelmet(new ItemStack(Material.AIR));
                        p.getInventory().setChestplate(new ItemStack(Material.AIR));
                        p.getInventory().setLeggings(new ItemStack(Material.AIR));
                        p.getInventory().setBoots(new ItemStack(Material.AIR));
                    }
                    skipslot.clear();

                } else { // Slot Skipping OFF

                    if (!getConfig().getBoolean("features.clearing.clear-armor")) {
                        for (int i = 0; i < 36; i++) {
                            p.getInventory().setItem(i, new ItemStack(Material.AIR));
                        }
                    } else {
                        p.getInventory().clear();
                    }
                }


                Cooldowns.removeActive(p);
                Msgs.sendBar(p, getConfig().getString("features.clearing.done-msg"));
            }
        }, 4L);
    }

    void bass(Player sender) {
        Player p = sender;
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 5.0F, 1.3F);
    }

    void despsound(Player sender) {
        Player p = sender;
        p.playSound(p.getLocation(), Sound.BLOCK_DISPENSER_LAUNCH, 5.0F, 0.4F);
    }

    void fireballshootsound(Player sender) {
        Player p = sender;
        p.playSound(p.getLocation(), Sound.ENTITY_GHAST_SHOOT, 5.0F, 0.1F);
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
    	final Player p = e.getPlayer();
        if (getConfig().getBoolean("options.commands.clear-override")) {
            if (e.getMessage().toLowerCase().equalsIgnoreCase("/clear")) {
                e.setCancelled(true);
                Bukkit.dispatchCommand(p, "ai clear");
                return;
            }
            if (e.getMessage().toLowerCase().equalsIgnoreCase("/ci")) {
                e.setCancelled(true);
                Bukkit.dispatchCommand(p, "ai clear");
                return;
            }
            if (e.getMessage().toLowerCase().contains("/ci ")) {
                e.setCancelled(true);
                Bukkit.dispatchCommand(p, "ai clear " + e.getMessage().replace("/ci ", ""));
                return;
            }
            if (e.getMessage().toLowerCase().contains("/clear ")) {
                e.setCancelled(true);
                Bukkit.dispatchCommand(p, "ai clear " + e.getMessage().replace("/clear ", ""));
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPickUp(EntityPickupItemEvent event) {
        if (!getConfig().getBoolean("features.prevent-pickup")) {
            return;
        }

        if (event.getEntity() instanceof Player) {
            Player p = (Player) event.getEntity();
            if (Cooldowns.activefortune.containsKey(p)) {
                event.setCancelled(true);
                return;
            }
            if (Cooldowns.active.containsKey(p)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onWater(PlayerBucketEmptyEvent e) {
        if (!getConfig().getBoolean("features.prevent-place")) {
            return;
        }

        final Player p = e.getPlayer();
        if (Cooldowns.activefortune.containsKey(p)) {
            e.setCancelled(true);
            return;
        }
        if (Cooldowns.active.containsKey(p)) {
            e.setCancelled(true);
            return;
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInv(InventoryClickEvent e) {
        Player p = (Player) e.getWhoClicked();
        if (getConfig().getBoolean("features.prevent-move")) {
            if (Cooldowns.active.containsKey(p) || Cooldowns.activefortune.containsKey(p)) {
                e.setCancelled(true);
            } // end of prevent move config
        } // end of active key
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDropItem(PlayerDropItemEvent e) {
        Player p = (Player) e;
        if (getConfig().getBoolean("features.prevent-drop")) {
            if (Cooldowns.active.containsKey(p) || Cooldowns.activefortune.containsKey(p)) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!getConfig().getBoolean("features.prevent-drop")) {
            return;
        }
        final Player p = event.getEntity();
        if (Cooldowns.activefortune.containsKey(p) || Cooldowns.active.containsKey(p)) {

            if (!event.getKeepInventory()) {
                event.getDrops().clear();
            }

            getLogger().info(p.getName() + " died. Their drops were canceled!");
            Msgs.send(p, getConfig().getString("messages.death"));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSlotChange(PlayerItemHeldEvent e) {
        if (!getConfig().getBoolean("features.prevent-player-slot-changes")) {
            return;
        }
        final Player p = e.getPlayer();
        if (Cooldowns.active.containsKey(p) || Cooldowns.activefortune.containsKey(p)) {
            if (getConfig().getBoolean("features.prevent-player-slot-changes")) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onLeave(PlayerQuitEvent e) {
        final Player player = e.getPlayer();

        if (Cooldowns.active.containsKey(player)) {
            player.getInventory().clear();
        }

        if (Cooldowns.activefortune.containsKey(player)) {
            loadInv(player);
            deleteInv(player);
        }

        Cooldowns.removeAll(player);
    } // end of onPlayerGameLeave

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerPlaceBlock(BlockPlaceEvent e) {
        final Player player = e.getPlayer();
        if (getConfig().getBoolean("features.prevent-place")) {
            if (Cooldowns.active.containsKey(player) || Cooldowns.activefortune.containsKey(player)) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerBucketFill(PlayerBucketFillEvent e) {
        final Player player = e.getPlayer();
        if (Cooldowns.active.containsKey(player) || Cooldowns.activefortune.containsKey(player)) {
            e.setCancelled(true);
        }
    }

    public boolean onCommand(CommandSender sender, Command cmd, String cmdLabel, String[] args) {
        if (cmd.getName().equalsIgnoreCase("animatedinventory") || (cmd.getName().equalsIgnoreCase("ai")) && args.length == 0) {
            sender.sendMessage("");
            sender.sendMessage("§b§lA§r§bnimated §f§lI§r§fnventory");
            sender.sendMessage("§7/ai help §7§ofor commands & links.");
            sender.sendMessage("");
            if (sender instanceof Player) {
                final Player p = (Player) sender;
                pop(p);
            }
        } else

        if (cmd.getName().equalsIgnoreCase("animatedinventory") || (cmd.getName().equalsIgnoreCase("ai")) && args.length == 1) {
            if (args[0].length() > 1) {
                if (args[0].equalsIgnoreCase("help")) {
                    sender.sendMessage("§8§m------------§r §b§lA§r§bnimated §f§lI§r§fnventory §8§m------------");
                    sender.sendMessage("§7/ai help §f- Shows this amazing help menu.");
                    if (getConfig().getBoolean("features.clearing.enabled")) {
                        if (sender.hasPermission("animatedinv.clear.others") || sender.isOp()) {
                            sender.sendMessage("§7/ai clear (player) §f- Shows an animation to clear inventories.");
                        } else {
                            sender.sendMessage("§7/ai clear §f- Shows an animation to clear your inventory.");
                        }
                    } else {
                        sender.sendMessage("§c/ai clear §f- Command has been disabled.");
                    }
                    if (getConfig().getBoolean("features.fortunes.enabled")) {
                        if (sender.hasPermission("animatedinv.fortune.others") || sender.isOp()) {
                            sender.sendMessage("§7/ai fortune (player) §f- Get yes/no answer in an inventory.");
                        } else {
                            sender.sendMessage("§7/ai fortune §f- Get yes/no answer in your inventory.");
                        }
                    } else {
                        sender.sendMessage("§c/ai fortune §f- Command has been disabled.");
                    }
                    if (getConfig().getBoolean("features.clearing.inv-backup.enabled") && (sender.hasPermission("animatedinv.clear.undo") || sender.isOp())) {
                        sender.sendMessage("§7/ai undoclear §f- Restores your inventory after a clear.");
                    }
                    sender.sendMessage("§7/ai version §f- Shows the version of this plugin.");
                    if (sender.hasPermission("animatedinv.admin") || sender.isOp()) {
                        sender.sendMessage("§7/ai reload §f- Reloads the config.yml.");
                        sender.sendMessage("§7/ai toggle §f- Enable/Disable fortune & clearing.");
                        if (getConfig().getBoolean("features.clearing.inv-backup.enabled")) {
                            sender.sendMessage("§7/ai purge §f- Purges any old cache.");
                        }
                    }
                    sender.sendMessage("§8§m------------------------------------------");
                    if (sender instanceof Player) {
                        final Player p = (Player) sender;
                        pop(p);
                    }
                } else if (args[0].equalsIgnoreCase("version")) {
                    Msgs.send(sender, "&7You're currently running &f&lv" + getDescription().getVersion());
                    if (sender instanceof Player) {
                        final Player p = (Player) sender;
                        pop(p);
                    }

                } else if (args[0].equalsIgnoreCase("purge")) {

                    if (sender instanceof Player) {
                        Player p = (Player) sender;

                        if (!sender.hasPermission("animatedinv.admin") && !sender.isOp()) {
                            noPermission(p);
                            return true;
                        }

                        if (Cooldowns.filecooldown.containsKey(p)) {
                            Msgs.send(sender, getConfig().getString("messages.backup-must-wait").replace("%number%", Integer.toString(getConfig().getInt("features.clearing.inv-backup.backup-cooldown"))));
                            bass(p);
                            return true;
                        }
                    }

                    Msgs.send(sender, "&c&lCache Purged. &fAny old cache has been deleted.");
                    if (sender instanceof Player) {
                        final Player p = (Player) sender;
                        pop(p);
                    }
                    Clear.purgeCache();
                } else if (args[0].equalsIgnoreCase("undoclear")) {

                    if (!(sender instanceof Player)) {
                        Msgs.send(sender, getConfig().getString("messages.no-player"));
                        return true;
                    }

                    Player p = (Player) sender;
                    if (!getConfig().getBoolean("features.clearing.inv-backup.enabled")) {
                        bass(p);
                        Msgs.send(sender, getConfig().getString("messages.backups-disabled"));
                        return true;
                    }

                    if (!sender.hasPermission("animatedinv.clear.undo") && !sender.isOp()) {
                        noPermission(p);
                        return true;
                    }

                    if (Cooldowns.active.containsKey(p)) {
                        Msgs.send(sender, getConfig().getString("messages.backup-must-wait-clear"));
                        bass(p);
                        return true;
                    }

                    if (Cooldowns.activefortune.containsKey(p)) {
                        Msgs.send(sender, getConfig().getString("messages.backup-must-wait-fortune"));
                        bass(p);
                        return true;
                    }

                    if (Cooldowns.filecooldown.containsKey(p)) {
                        Msgs.send(sender, getConfig().getString("messages.backup-must-wait").replace("%number%", Integer.toString(getConfig().getInt("features.clearing.inv-backup.backup-cooldown"))));
                        bass(p);
                        return true;
                    }

                    try {
                        File cache = new File(this.getDataFolder(), File.separator + "Cache");
                        File f = new File(cache, File.separator + "" + p.getUniqueId().toString() + ".yml");
                        if (f.exists()) {
                            try {
                                Clear.undoClear(p);
                            } catch (Exception e) {
                                Msgs.send(sender, getConfig().getString("messages.backup-error"));
                                bass(p);
                                getLogger().info("Hm. We were unable to restore " + p.getName() + "'s backup.");
                                if (debug) {
                                    getLogger().info("[Debug] Error below: ------------------------------");
                                    e.printStackTrace();
                                    getLogger().info("[Debug] End of Error ------------------------------");
                                }
                            }
                            FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);
                            long secondsAgo = Math.abs(((setcache.getLong("Last-Backup")) / 1000) - (System.currentTimeMillis() / 1000));
                            if (secondsAgo < 60) {
                                Msgs.send(sender, getConfig().getString("messages.backup-restored").replace("%time%", Long.toString(secondsAgo) + "s"));
                            } else if (secondsAgo < 3600) {
                                Msgs.send(sender, getConfig().getString("messages.backup-restored").replace("%time%", Long.toString(secondsAgo / 60) + "m"));
                            } else if (secondsAgo < 86400) {
                                Msgs.send(sender, getConfig().getString("messages.backup-restored").replace("%time%", Long.toString(secondsAgo / 3600) + "h"));
                            } else {
                                Msgs.send(sender, getConfig().getString("messages.backup-restored").replace("%time%", Long.toString(secondsAgo / 86400) + "d"));
                            }
                            levelup(p);
                        } else {
                            Msgs.send(sender, getConfig().getString("messages.backup-no-file"));
                            bass(p);
                        }
                    } catch (Exception e) {
                        Msgs.send(sender, getConfig().getString("messages.backup-error"));
                        bass(p);
                        getLogger().info("Hm. Something went wrong when trying to get " + p.getName() + "'s cache files.");
                        if (debug) {
                            getLogger().info("[Debug] Error below: ------------------------------");
                            e.printStackTrace();
                            getLogger().info("[Debug] End of Error ------------------------------");
                        }
                    }

                } else if (args[0].equalsIgnoreCase("glitched")) {
                    if (!(sender instanceof Player)) {
                        Msgs.send(sender, "&c&lSorry. &fOnly players can do this.");
                        return true;
                    }

                    if (!sender.isOp()) {
                        noPermission(sender);
                        return true;
                    }

                    final Player p = (Player) sender;
                    pop(p);
                    Cooldowns.activefortune.remove(p);
                    Cooldowns.active.remove(p);
                    Cooldowns.cooldown.remove(p);
                    Msgs.send(sender, "&6&lGlitch Fixed. &fWe have tried to fix your sticky situation.");
                } else if (args[0].equalsIgnoreCase("debug")) {
                    if (!sender.isOp() && !sender.hasPermission("animatedinv.admin") && sender instanceof Player) {
                        noPermission(sender);
                        return true;
                    }
                    if (sender instanceof Player) {
                        Player p = (Player) sender;
                        pop(p);
                    }
                    if (debug) {
                        getConfig().set("options.debug", false);
                        saveConfig();
                        reloadConfig();
                        Msgs.send(sender, "&c&lDebug Off. &fWe have disabled debug mode.");
                    } else {
                        getConfig().set("options.debug", true);
                        saveConfig();
                        reloadConfig();
                        Msgs.send(sender, "&a&lDebug On. &fWe have enabled debug mode.");
                        if (debug) {
                            getLogger().info("[Debug] Now enabled via the /ai debug command.");
                        }
                    }
                } else if (args[0].equalsIgnoreCase("toggle")) {
                    if (!sender.hasPermission("animatedinv.admin") && !sender.isOp() && sender instanceof Player) {
                        Player p = (Player) sender;
                        noPermission(p);
                        return true;
                    }

                    if (getConfig().getBoolean("features.clearing.enabled")) {
                        getConfig().set("features.clearing.enabled", false);
                        getConfig().set("features.fortunes.enabled", false);
                        saveConfig();
                        reloadConfig();
                        configChecks();
                        Msgs.send(sender, "&fYou have &c&lDISABLED &fclearing & fortunes.");
                        if (sender instanceof Player) {
                            Player p = (Player) sender;
                            pop(p);
                        }
                    } else {
                        getConfig().set("features.clearing.enabled", true);
                        getConfig().set("features.fortunes.enabled", true);
                        saveConfig();
                        reloadConfig();
                        configChecks();
                        if (!getConfig().getBoolean("features.clearing.enabled")) {
                            Msgs.send(sender, "&c&lError! &fCouldn't re-enable clearing, are all clearing animations set to false?");
                            if (sender instanceof Player) {
                                Player p = (Player) sender;
                                bass(p);
                            }
                        } else {
                            Msgs.send(sender, "&fYou have &a&lENABLED &fclearing & fortunes.");
                            if (sender instanceof Player) {
                                Player p = (Player) sender;
                                pop(p);
                            }
                        }
                    }
                } else if (args[0].equalsIgnoreCase("reload")) {
                    if (!sender.hasPermission("animatedinv.admin") || !sender.isOp()) {
                        if (sender instanceof Player) {
                            Player p = (Player) sender;
                            noPermission(p);
                            return true;
                        }
                    }

                    reloadConfig();
                    configChecks();
                    disabledclearworld.clear();
                    disabledfortuneworld.clear();
                    disabledclearworld.addAll(getConfig().getStringList("features.clearing.disabled-worlds"));
                    disabledfortuneworld.addAll(getConfig().getStringList("features.fortunes.disabled-worlds"));
                    updateConfig();
                    Msgs.send(sender, getConfig().getString("messages.reload"));
                    if (debug) {
                        getLogger().info("[Debug] Disabled Clear Worlds: " + disabledclearworld.toString());
                        getLogger().info("[Debug] Disabled Fortune Worlds: " + disabledfortuneworld.toString());
                    }
                    if (sender instanceof Player) {
                        Player p = (Player) sender;
                        levelup(p);
                    }

                } else if (args[0].equalsIgnoreCase("fortune")) {
                    if (!(sender instanceof Player)) {
                        Msgs.send(sender, getConfig().getString("messages.no-player"));
                        return true;
                    }
                    Player p = (Player) sender;

                    if (Cooldowns.active.containsKey(p) || Cooldowns.activefortune.containsKey(p)) {
                        return true;
                    }

                    if (!sender.hasPermission("animatedinv.fortune")) {
                        noPermission(p);
                        return true;
                    }

                    if (!getConfig().getBoolean("features.fortunes.enabled")) {
                        bass(p);
                        Msgs.sendBar(p, getConfig().getString("messages.fortune-disabled"));
                        return true;
                    }

                    if (disabledfortuneworld.contains(p.getLocation().getWorld().getName())) {
                        bass(p);
                        Msgs.sendBar(sender, getConfig().getString("messages.fortune-world-disabled"));
                        return true;
                    }

                    if (getConfig().getBoolean("features.fortunes.health-restriction.enabled")) {
                        if (p.getHealth() < getConfig().getDouble("features.fortunes.health-restriction.min")) {
                            bass(p);
                            Msgs.sendBar(sender, getConfig().getString("messages.fortune-need-more-health").replace("%num%", Double.toString(getConfig().getDouble("features.fortunes.health-restriction.min") / 2)));
                        }
                    }

                    if (Cooldowns.cooldown.containsKey(p) && !Cooldowns.active.containsKey(p)) {
                        Msgs.sendBar(p, getConfig().getString("options.cooldowns.msg").replace("%number%", Integer.toString(getConfig().getInt("options.cooldowns.time"))));
                        bass(p);
                        return true;
                    }

                    if (!Cooldowns.notHurt(p)) {
                        Msgs.sendBar(p, getConfig().getString("messages.fortune-while-hurt"));
                        bass(p);
                        return true;
                    }

                    Cooldowns.activefortune.put(p, p.getName());
                    if (debug) {
                        getLogger().info("[Debug] Self induced fortune: " + p.getName());
                    }
                    try {
                        MC1_15.fortune(p);
                    } catch (Exception e) {
                        errorMsg(p, 10, e);
                    }
                } else if (args[0].equalsIgnoreCase("clear")) {
                    if (!(sender instanceof Player)) {
                        Msgs.send(sender, getConfig().getString("messages.no-player"));
                        return true;
                    }

                    Player p = (Player) sender;

                    if (!p.hasPermission("animatedinv.clear")) {
                        noPermission(p);
                        return true;
                    }

                    if (!getConfig().getBoolean("features.clearing.enabled")) {
                        bass(p);
                        Msgs.sendBar(p, getConfig().getString("messages.clear-disabled"));
                        return true;
                    }

                    if (Cooldowns.cooldown.containsKey(p) && !Cooldowns.active.containsKey(p)) {
                        Msgs.sendBar(sender, getConfig().getString("options.cooldowns.msg").replace("%number%", Integer.toString(getConfig().getInt("options.cooldowns.time"))));
                        bass(p);
                        return true;
                    }

                    if (disabledclearworld.contains(p.getLocation().getWorld().getName())) {
                        bass(p);
                        Msgs.sendBar(sender, getConfig().getString("messages.clear-world-disabled"));
                        return true;
                    }

                    if (!Cooldowns.active.containsKey(p) && !Cooldowns.activefortune.containsKey(p)) {
                        Clear.go(p);
                    }

                } else {
                    if (sender instanceof Player) {
                        Player p = (Player) sender;
                        bass(p);
                    }
                    Msgs.send(sender, getConfig().getString("messages.not-a-command").replace("%cmd%", args[0].toString()));
                    if (args[0].contains("undo")) {
                        Msgs.send(sender, getConfig().getString("messages.undo-suggestion"));
                    }
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("undoclear")) {

            if (sender instanceof Player) {
                if (!sender.hasPermission("animatedinv.clear.undo.others") && !sender.isOp()) {
                    Player p = (Player) sender;
                    noPermission(p);
                    return true;
                }
            }

            if (!getConfig().getBoolean("features.clearing.inv-backup.enabled")) {
                if (sender instanceof Player) {
                    Player p = (Player) sender;
                    bass(p);
                }
                Msgs.send(sender, getConfig().getString("messages.backups-disabled"));
                return true;
            }

            Player target = Bukkit.getServer().getPlayer(args[1]);
            if (target == null) {
                if (sender instanceof Player) {
                    Player p = (Player) sender;
                    bass(p);
                }
                Msgs.send(sender, getConfig().getString("messages.not-online").replace("%player%", args[1].toString()));
                return true;
            }

            if (Cooldowns.active.containsKey(target)) {
                if (sender instanceof Player) {
                    Player p = (Player) sender;
                    bass(p);
                }
                Msgs.send(sender, getConfig().getString("messages.already-clearing").replace("%player%", args[1].toString()));
                return true;
            }

            if (Cooldowns.activefortune.containsKey(target)) {
                if (sender instanceof Player) {
                    Player p = (Player) sender;
                    bass(p);
                }
                Msgs.send(sender, getConfig().getString("messages.already-getting-fortune").replace("%player%", args[1].toString()));
                return true;
            }

            try {
                File cache = new File(this.getDataFolder(), File.separator + "Cache");
                File f = new File(cache, File.separator + "" + target.getUniqueId().toString() + ".yml");

                if (!f.exists()) {
                    Msgs.send(sender, getConfig().getString("messages.backup-no-file-other").replace("%player%", target.getName()));
                    if (sender instanceof Player) {
                        Player p = (Player) sender;
                        bass(p);
                    }
                    return true;
                }
                try {
                    Clear.undoClear(target);
                } catch (Exception e) {
                    Msgs.send(sender, getConfig().getString("messages.backup-error"));
                    if (sender instanceof Player) {
                        Player p = (Player) sender;
                        bass(p);
                    }
                    getLogger().info("Hm. We were unable to restore " + target.getName() + "'s backup.");
                    if (debug) {
                        getLogger().info("[Debug] Error below: ------------------------------");
                        e.printStackTrace();
                        getLogger().info("[Debug] End of Error ------------------------------");
                    }
                }
                FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);
                long secondsAgo = Math.abs(((setcache.getLong("Last-Backup")) / 1000) - (System.currentTimeMillis() / 1000));
                if (secondsAgo < 60) {
                    Msgs.send(sender, getConfig().getString("messages.backup-restored-other").replace("%time%", Long.toString(secondsAgo) + "s").replace("%player%", target.getName()));
                    Msgs.send(target, getConfig().getString("messages.backup-restored-target").replace("%time%", Long.toString(secondsAgo) + "s").replace("%sender%", sender.getName()));
                } else if (secondsAgo < 3600) {
                    Msgs.send(sender, getConfig().getString("messages.backup-restored-other").replace("%time%", Long.toString(secondsAgo / 60) + "m").replace("%player%", target.getName()));
                    Msgs.send(target, getConfig().getString("messages.backup-restored-target").replace("%time%", Long.toString(secondsAgo / 60) + "m").replace("%sender%", sender.getName()));
                } else if (secondsAgo < 86400) {
                    Msgs.send(sender, getConfig().getString("messages.backup-restored-other").replace("%time%", Long.toString(secondsAgo / 3600) + "h").replace("%player%", target.getName()));
                    Msgs.send(target, getConfig().getString("messages.backup-restored-target").replace("%time%", Long.toString(secondsAgo / 3600) + "h").replace("%sender%", sender.getName()));
                } else {
                    Msgs.send(sender, getConfig().getString("messages.backup-restored-other").replace("%time%", Long.toString(secondsAgo / 86400) + "d").replace("%player%", target.getName()));
                    Msgs.send(target, getConfig().getString("messages.backup-restored-target").replace("%time%", Long.toString(secondsAgo / 86400) + "d").replace("%sender%", sender.getName()));
                }

                levelup(target);

                if (sender instanceof Player) {
                    Player p = (Player) sender;
                    levelup(p);
                }

            } catch (Exception finalerr) {
                if (sender instanceof Player) {
                    Player p = (Player) sender;
                    bass(p);
                }
                Msgs.send(sender, "&c&lHm. &fWe were not able to do that to &7" + target.getName());
                if (debug) {
                    getLogger().info("[Debug] An error occured. See below: ------------------");
                    finalerr.printStackTrace();
                    getLogger().info("[Debug] End of Error. ---------------------------------");
                }
            }
            return true;
        } else if (args.length == 2 && args[0].equalsIgnoreCase("clear")) {

            if (!sender.hasPermission("animatedinv.clear.others") && !sender.isOp()) {
                if (sender instanceof Player) {
                    Player p = (Player) sender;
                    noPermission(p);
                } else {
                    Msgs.send(sender, "&cSorry, but CONSOLE is not allowed to clear others.");
                }
                return true;
            }

            Player target = Bukkit.getServer().getPlayer(args[1]);
            if (target == null) {
                if (sender instanceof Player) {
                    Player p = (Player) sender;
                    bass(p);
                }
                Msgs.send(sender, getConfig().getString("messages.not-online").replace("%player%", args[1].toString()));
            } else if (Cooldowns.active.containsKey(target)) {
                if (sender instanceof Player) {
                    Player p = (Player) sender;
                    bass(p);
                }
                Msgs.send(sender, getConfig().getString("messages.already-clearing").replace("%player%", args[1].toString()));
            } else if (Cooldowns.activefortune.containsKey(target)) {
                if (sender instanceof Player) {
                    Player p = (Player) sender;
                    bass(p);
                }
                Msgs.send(sender, getConfig().getString("messages.already-getting-fortune").replace("%player%", args[1].toString()));
            } else {
                if (sender instanceof Player) {
                    Player p = (Player) sender;
                    levelup(p);
                }
                Msgs.send(sender, getConfig().getString("messages.clear-other-success").replace("%player%", args[1].toString()));
                Clear.go(target);
            }
            return true;

        } else if (args.length == 2 && args[0].equalsIgnoreCase("fortune")) {
            if (sender instanceof Player) {
                if (!sender.hasPermission("animatedinv.fortune.others") && !sender.isOp()) {
                    Player p = (Player) sender;
                    noPermission(p);
                    return true;
                }
            }

            Player target = Bukkit.getServer().getPlayer(args[1]);
            if (target == null) {
                if (sender instanceof Player) {
                    Player p = (Player) sender;
                    bass(p);
                }
                Msgs.send(sender, getConfig().getString("messages.not-online").replace("%player%", args[1].toString()));
                return true;
            } else if (Cooldowns.active.containsKey(target)) {
                if (sender instanceof Player) {
                    Player p = (Player) sender;
                    bass(p);
                }
                Msgs.send(sender, getConfig().getString("messages.already-clearing").replace("%player%", args[1].toString()));
                return true;
            } else if (Cooldowns.activefortune.containsKey(target)) {
                if (sender instanceof Player) {
                    Player p = (Player) sender;
                    bass(p);
                }
                Msgs.send(sender, getConfig().getString("messages.already-getting-fortune").replace("%player%", args[1].toString()));
                return true;
            } else {
                if (sender instanceof Player) {
                    Player p = (Player) sender;
                    levelup(p);
                }
                Msgs.send(sender, getConfig().getString("messages.fortune-other-success").replace("%player%", args[1].toString()));
                try {
                    MC1_15.fortune(target);
                    Cooldowns.activefortune.put(target, target.getName());
                } catch (Exception e) {
                    errorMsg(target, 10, e);
                }
            }
            return true;
        } else { // too many args & not clear or fortune for players
            if (sender instanceof Player) {
                Player p = (Player) sender;
                bass(p);
            }
            Msgs.send(sender, "&cToo many args for: &f/" + cmd.getName() + " " + args[0]);
        }
        return false;
    }

    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player) {
            if (e.getCause() == DamageCause.FALL) {
                return;
            }

            if (getConfig().getBoolean("features.fortunes.prevent-if-being-hurt")) {
                Player player = (Player) e.getEntity();
                Cooldowns.isBeinghurt.put(player, System.currentTimeMillis());
            }
        }
    }


    @EventHandler(priority = EventPriority.HIGHEST)
    public void onTP(PlayerTeleportEvent e) {
        if (!preventglitch) {
            return;
        }

        final Player p = e.getPlayer();

        if (Cooldowns.active.containsKey(p) || Cooldowns.activefortune.containsKey(p)) {
            bass(p);
            Msgs.sendPrefix(p, canceltpmsg);
            e.setCancelled(true);
            return;
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onWorld(PlayerChangedWorldEvent e) {
        if (!preventglitch) {
            return;
        }

        final Player p = e.getPlayer();

        Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(this, new Runnable() {
            public void run() {
                if (!p.isOnline()) {
                    // Do nada, we missed em' chief
                } else {

                    final ItemStack token = new ItemStack(Material.getMaterial(getConfig().getString("features.clearing.token-item")));
                    ItemMeta tokenm = token.getItemMeta();
                    tokenm.setDisplayName(ChatColor.translateAlternateColorCodes('&', getConfig().getString("features.clearing.token")));
                    token.setItemMeta(tokenm);

                    if (p.getInventory().contains(token) || Cooldowns.active.containsKey(p)) {
                        p.getInventory().clear();
                    }


                    final ItemStack one = new ItemStack(Material.LIME_CONCRETE);
                    ItemMeta onem = one.getItemMeta();
                    onem.setDisplayName(ChatColor.translateAlternateColorCodes('&', getConfig().getString("features.fortunes.yes-block.name")));
                    one.setItemMeta(onem);

                    final ItemStack two = new ItemStack(Material.RED_CONCRETE);
                    ItemMeta twom = two.getItemMeta();
                    twom.setDisplayName(ChatColor.translateAlternateColorCodes('&', getConfig().getString("features.fortunes.no-block.name")));
                    two.setItemMeta(twom);

                    if (p.getInventory().contains(one) && p.getInventory().contains(two) || Cooldowns.activefortune.containsKey(p)) {
                        p.getInventory().clear();
                        getLogger().info(p.getName() + "'s fortune is glitched because they switched inventories. Will clear fortune items if they are stuck!");
                    }
                }
            }
        }, 12L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            final Player player = e.getPlayer();

            // This is a dev-join message sent to me only. It's to help me understand which servers support my work <3
            if (player.getUniqueId().toString().equals("6191ff85-e092-4e9a-94bd-63df409c2079")) {
                player.sendMessage(ChatColor.GRAY + "This server is running " + ChatColor.WHITE + "AnimatedInventory " + ChatColor.GOLD + "v" + getDescription().getVersion() + ChatColor.GRAY + " for " + Bukkit.getBukkitVersion().replace("-SNAPSHOT", ""));
            }
            // I kindly ask you leave the above portion in ANY modification of this plugin. Thank You!

            if (getConfig().getBoolean("options.updates.notify") && (player.isOp()) || (player.hasPermission("animatedinv.admin"))) {
                if (outdatedplugin) {
                    Msgs.sendPrefix(player, "&c&lOutdated Plugin. &7Using v" + getDescription().getVersion() + ", while the latest is &f&l" + outdatedpluginversion);
                }
            }
        });
    }
}