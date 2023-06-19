package com.zachduda.animatedinventory;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Objects;

public class Cooldowns implements Listener {

	private static final Main plugin = Main.getPlugin(Main.class);

	public static HashMap < Player, String > cooldown = new HashMap<>();
	public static HashMap < Player, String > filecooldown = new HashMap<>();
	public static HashMap < Player, String > active = new HashMap<>();
	public static HashMap < Player, String > activefortune = new HashMap<>();
	public static HashMap < Player, ItemStack[] > inventories = new HashMap<>();
	public static HashMap < Player, Long > isBeinghurt = new HashMap<>();

	static void removeAll(final Player p) {
		boolean debug = plugin.getConfig().getBoolean("options.debug");
		cooldown.remove(p);
		active.remove(p);
		activefortune.remove(p);
		inventories.remove(p);
		filecooldown.remove(p);
		isBeinghurt.remove(p);
		inventories.remove(p.getPlayer());
		if (debug) {
			plugin.getLogger().info("[Debug] Called removeAll() event under Cooldowns.");
		}
	}

	public static boolean notHurt(final Player p) {
		if (!plugin.getConfig().getBoolean("features.fortunes.prevent-if-being-hurt")) {
			return true;
		}

		boolean debug = plugin.getConfig().getBoolean("options.debug");
		if (!isBeinghurt.containsKey(p.getPlayer())) {
			if (debug) {
				plugin.getLogger().info("[Debug] Player not on isBeingHurt hashmap. notHurt() check passed.");
			}
			return true;
		}

		long lastHurt = Math.abs((isBeinghurt.get(p.getPlayer()) / 1000) - (System.currentTimeMillis() / 1000));
		if (lastHurt >= 7) {
			if (debug) {
				plugin.getLogger().info("[Debug] Passed notHurt() check with lastHurt @ " + lastHurt);
			}
			isBeinghurt.remove(p.getPlayer());
			return true;
		}
		return false;
	}

	static void startCooldown(final Player p) {

		if (plugin.getConfig().getBoolean("options.cooldowns.enabled")) {
			try {
				if (Objects.requireNonNull(plugin.getConfig().getString("options.cooldowns.time")).equalsIgnoreCase("none") ||
						plugin.getConfig().getInt("options.cooldowns.time") == 0) {

					Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, () -> cooldown.remove(p.getPlayer()), 20L);
					return;
				} // end of if time set to 0

				Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, () -> {

					if (!Objects.requireNonNull(plugin.getConfig().getString("options.cooldowns.time")).equalsIgnoreCase("none") &&
							!(plugin.getConfig().getInt("options.cooldowns.time") == 0)) {
						cooldown.remove(p.getPlayer());
					}
				}, plugin.getConfig().getInt("options.cooldowns.time") * 20L);

			} catch (Exception e) {
				System.out.print("[AnimatedInventory] Error! Couldn't exceute the cooldown timer properly.");
				if (plugin.getConfig().getBoolean("options.debug")) {
					plugin.getLogger().info("[Debug] Error is below:");
					e.printStackTrace();
				}
			}
		}
	}

	static void startFileCooldown(final Player p) {
		try {
			if (Objects.requireNonNull(plugin.getConfig().getString("features.clearing.inv-backup.backup-cooldown")).equalsIgnoreCase("none") ||
					plugin.getConfig().getInt("features.clearing.inv-backup.backup-cooldown") == 0) {
				return;
			} // end of if time set to 0

			filecooldown.put(p.getPlayer(), p.getName());

			Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, () -> {

				if (!Objects.requireNonNull(plugin.getConfig().getString("features.clearing.inv-backup.backup-cooldown")).equalsIgnoreCase("none") &&
						!(plugin.getConfig().getInt("features.clearing.inv-backup.backup-cooldown") == 0)) {
					filecooldown.remove(p.getPlayer());
				}
			}, plugin.getConfig().getInt("features.clearing.inv-backup.backup-cooldown") * 22L);

		} catch (Exception e) {
			System.out.print("[AnimatedInventory] Error! Couldn't exceute the file cooldown timer properly.");
			if (plugin.getConfig().getBoolean("options.debug")) {
				plugin.getLogger().info("[Debug] Error is below:");
				e.printStackTrace();
			}
		}
	}

	static void removeActive(final Player p) {

		try {

			Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, () -> {
				active.remove(p.getPlayer());

				if (Objects.requireNonNull(plugin.getConfig().getString("options.cooldowns.time")).equalsIgnoreCase("none") ||
						plugin.getConfig().getInt("options.cooldowns.time") == 0 || !plugin.getConfig().getBoolean("options.cooldowns.enabled")) {
					return;
				}
				cooldown.put(p, p.getName());
				startCooldown(p);
			}, 10L);
		} catch (Exception e) {
			System.out.print("[AnimatedInventory] Error! Couldn't exceute the active timer properly.");
		}
	}

	public static void removeFortune(final Player p) {

		try {
			activefortune.remove(p.getPlayer());

			if (Objects.requireNonNull(plugin.getConfig().getString("options.cooldowns.time")).equalsIgnoreCase("none") ||
					plugin.getConfig().getInt("options.cooldowns.time") == 0 || !plugin.getConfig().getBoolean("options.cooldowns.enabled")) {
				return;
			}
			cooldown.put(p, p.getName());
			startCooldown(p);
		} catch (Exception e) {
			System.out.print("[AnimatedInventory] Error! Couldn't exceute the active timer properly.");
		}
	}

}