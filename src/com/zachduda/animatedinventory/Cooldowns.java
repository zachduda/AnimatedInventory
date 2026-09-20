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
 * The maps are keyed by UUID rather than by Player. A Bukkit Player is a live
 * handle onto a connection, an entity and a world, so holding one as a map key
 * pins all of that in memory for as long as the entry survives - and because a
 * reconnecting player gets a brand new Player instance, any entry that outlived
 * a logout could never be looked up again, only leaked.
 *
 * They are also private now. They used to be public and were read and mutated
 * from every other class, which is how a player could end up marked as clearing
 * with no animation running, or marked twice by two different call paths. Go
 * through the methods below; AnimatedInventoryAPI is the supported surface.
 */
public class Cooldowns implements Listener {

	private static final Main plugin = Main.getPlugin(Main.class);

	/** How long after taking damage a fortune stays blocked. */
	private static final long HURT_BLOCK_SECONDS = 7L;

	private static final Map < UUID, String > cooldown = new HashMap<>();
	private static final Map < UUID, String > filecooldown = new HashMap<>();
	private static final Map < UUID, String > active = new HashMap<>();
	private static final Map < UUID, String > activefortune = new HashMap<>();
	private static final Map < UUID, ItemStack[] > inventories = new HashMap<>();
	private static final Map < UUID, Long > isBeinghurt = new HashMap<>();

	/**
	 * The result a running fortune already rolled, held until the fortune ends.
	 *
	 * PlayerFortuneEndEvent needs it on whichever path the fortune finishes:
	 * normally, or cut short by a quit, a world change or a reload.
	 */
	private static final Map < UUID, Boolean > fortuneResults = new HashMap<>();

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

	public static boolean onFileCooldown(final Player p) {
		return filecooldown.containsKey(p.getUniqueId());
	}

	static void markClearing(final Player p) {
		active.put(p.getUniqueId(), p.getName());
	}

	/** Clears the clearing flag, reporting whether it had been set. */
	static boolean unmarkClearing(final UUID id) {
		return active.remove(id) != null;
	}

	public static void markFortune(final Player p) {
		activefortune.put(p.getUniqueId(), p.getName());
	}

	/** Clears the fortune flag, reporting whether it had been set. */
	static boolean unmarkFortune(final UUID id) {
		return activefortune.remove(id) != null;
	}

	static void setFortuneResult(final Player p, final boolean lucky) {
		fortuneResults.put(p.getUniqueId(), lucky);
	}

	/** Takes the pending fortune result, or null if there is none left to fire. */
	static Boolean takeFortuneResult(final Player p) {
		return fortuneResults.remove(p.getUniqueId());
	}

	static void stashInventory(final Player p, final ItemStack[] contents) {
		inventories.put(p.getUniqueId(), contents);
	}

	static ItemStack[] peekInventory(final Player p) {
		return inventories.get(p.getUniqueId());
	}

	static void dropInventory(final Player p) {
		inventories.remove(p.getUniqueId());
	}

	static void hurt(final Player p) {
		isBeinghurt.put(p.getUniqueId(), System.currentTimeMillis());
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
		fortuneResults.remove(id);
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
