package com.zachduda.animatedinventory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

/**
 * Per-player state for anything currently in progress.
 *
 * These maps are keyed by UUID rather than by Player. A Bukkit Player is a live
 * handle onto a connection, an entity and a world, so holding one as a map key
 * pins all of that in memory for as long as the entry survives - and because a
 * reconnecting player gets a brand new Player instance, any entry that outlived
 * a logout could never be looked up again, only leaked.
 */
public class Cooldowns implements Listener {

	private static final Main plugin = Main.getPlugin(Main.class);

	/** How long after taking damage a fortune stays blocked. */
	private static final long HURT_BLOCK_SECONDS = 7L;

	public static Map < UUID, String > cooldown = new HashMap<>();
	public static Map < UUID, String > filecooldown = new HashMap<>();
	public static Map < UUID, String > active = new HashMap<>();
	public static Map < UUID, String > activefortune = new HashMap<>();
	public static Map < UUID, ItemStack[] > inventories = new HashMap<>();
	public static Map < UUID, Long > isBeinghurt = new HashMap<>();

	public static boolean isClearing(final Player p) {
		return active.containsKey(p.getUniqueId());
	}

	public static boolean isFortune(final Player p) {
		return activefortune.containsKey(p.getUniqueId());
	}

	/** True while the plugin is driving this player's inventory for any reason. */
	public static boolean isBusy(final Player p) {
		final UUID id = p.getUniqueId();
		return active.containsKey(id) || activefortune.containsKey(id);
	}

	public static boolean onCooldown(final Player p) {
		return cooldown.containsKey(p.getUniqueId());
	}

	static void removeAll(final Player p) {
		removeAll(p.getUniqueId());
	}

	static void removeAll(final UUID id) {
		Timeline.stop(id);
		cooldown.remove(id);
		active.remove(id);
		activefortune.remove(id);
		inventories.remove(id);
		filecooldown.remove(id);
		isBeinghurt.remove(id);
		if (Settings.debug) {
			plugin.getLogger().info("[Debug] Called removeAll() event under Cooldowns.");
		}
	}

	public static boolean notHurt(final Player p) {
		if (!Settings.preventIfHurt) {
			return true;
		}

		final UUID id = p.getUniqueId();
		final Long lastHurtAt = isBeinghurt.get(id);
		if (lastHurtAt == null) {
			if (Settings.debug) {
				plugin.getLogger().info("[Debug] Player not on isBeingHurt hashmap. notHurt() check passed.");
			}
			return true;
		}

		final long secondsAgo = TimeUnit.MILLISECONDS.toSeconds(
				Math.abs(System.currentTimeMillis() - lastHurtAt));
		if (secondsAgo >= HURT_BLOCK_SECONDS) {
			if (Settings.debug) {
				plugin.getLogger().info("[Debug] Passed notHurt() check with lastHurt @ " + secondsAgo);
			}
			isBeinghurt.remove(id);
			return true;
		}
		return false;
	}

	static void startCooldown(final Player p) {
		if (!Settings.cooldownsEnabled || Settings.cooldownTime <= 0) {
			return;
		}

		final UUID id = p.getUniqueId();
		cooldown.put(id, p.getName());
		Bukkit.getScheduler().runTaskLater(plugin, () -> cooldown.remove(id),
				Settings.cooldownTime * 20L);
	}

	static void startFileCooldown(final Player p) {
		if (Settings.backupCooldown <= 0) {
			return;
		}

		final UUID id = p.getUniqueId();
		filecooldown.put(id, p.getName());
		Bukkit.getScheduler().runTaskLater(plugin, () -> filecooldown.remove(id),
				Settings.backupCooldown * 20L);
	}

	static void removeActive(final Player p) {
		final UUID id = p.getUniqueId();
		Bukkit.getScheduler().runTaskLater(plugin, () -> {
			active.remove(id);
			startCooldown(p);
		}, 10L);
	}

	public static void removeFortune(final Player p) {
		activefortune.remove(p.getUniqueId());
		startCooldown(p);
	}
}
