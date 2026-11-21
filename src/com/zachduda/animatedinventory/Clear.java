package com.zachduda.animatedinventory;

import java.io.File;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.zachduda.animatedinventory.animation.AnimationRegistry;
import com.zachduda.animatedinventory.animation.ClearAnimation;
import com.zachduda.animatedinventory.api.PlayerClearInventoryEvent;

public class Clear {
	private static final Main plugin = Main.getPlugin(Main.class);

	/** Why an /ai undoclear did or did not put an inventory back. */
	enum UndoResult {
		RESTORED, NO_FILE, ALREADY_USED, DISABLED, ERROR
	}

	/** The outcome of an undo, plus how old the backup was. */
	record UndoOutcome(UndoResult result, long secondsAgo) {
	}

	private static File cacheDir() {
		return new File(plugin.getDataFolder(), "Cache");
	}

	private static File cacheFile(UUID id) {
		return new File(cacheDir(), id + ".yml");
	}

	static void purgeCache() {
		if (!Settings.backupEnabled) {
			if (Settings.debug) {
				plugin.getLogger().info("[Debug] Purge STOPPED. (It's disabled in the config)");
			}
			return;
		}

		// Snapshot on the calling thread so the async task never touches Settings.
		final boolean debug = Settings.debug;
		final int purgeSec = Settings.backupEraseAfter;

		Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
			if (debug) {
				plugin.getLogger().info("[Debug] Attempting to start purgeCache().");
			}

			// listFiles() is null when the folder has never been created, which is
			// the normal state on a fresh install. Iterating it threw an NPE on
			// every startup that had backups switched on.
			final File[] files = cacheDir().listFiles((dir, name) -> name.endsWith(".yml"));
			if (files == null || files.length == 0) {
				if (debug) {
					plugin.getLogger().info("[Debug] No cache folder or no cache files to purge.");
				}
				return;
			}

			for (File f : files) {
				final FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);
				final long secondsAgo = ageSeconds(setcache);
				final String playername = setcache.getString("Name");

				if (debug) {
					plugin.getLogger().info("[Debug] File for " + playername + " is " + secondsAgo + " seconds old.");
				}

				if (secondsAgo > purgeSec) {
					if (!f.delete()) {
						plugin.getLogger().warning("Couldn't delete the old cache file " + f.getName());
					} else if (debug) {
						plugin.getLogger().info("[Debug] Deleted " + playername + "'s cache file because it's "
								+ secondsAgo + "s old. (Purge if past " + purgeSec + "s)");
					}
				} else if (debug) {
					plugin.getLogger().info("[Debug] Kept " + playername + "'s cache file. Life Left: "
							+ (purgeSec - secondsAgo) + " seconds");
				}
			}
		});
	}

	private static long ageSeconds(FileConfiguration setcache) {
		return TimeUnit.MILLISECONDS.toSeconds(
				Math.abs(System.currentTimeMillis() - setcache.getLong("Last-Backup")));
	}

	/**
	 * Puts a backed-up inventory back.
	 *
	 * The caller used to restore, then separately re-read the file to decide what
	 * to tell the player and to stamp the use count. That meant a failed restore
	 * still burned the player's one-time-use backup and still reported success.
	 * Everything now happens here.
	 *
	 * The backup is checked before anything changes: it used to clear() the
	 * inventory and then call setContents(), so a backup too big for the
	 * inventory wiped the player's current items before it threw. The use is
	 * stamped next, so a disk that won't save it can't hand out a one-time
	 * backup twice, and it's taken back if the restore itself fails.
	 */
	static UndoOutcome undoClear(Player p) {
		if (!Settings.backupEnabled) {
			return new UndoOutcome(UndoResult.DISABLED, 0L);
		}

		final File f = cacheFile(p.getUniqueId());
		if (!f.exists()) {
			plugin.getLogger().info("Request made for " + p.getName() + "'s inventory backup, but a file was not found.");
			return new UndoOutcome(UndoResult.NO_FILE, 0L);
		}

		final FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);
		final long secondsAgo = ageSeconds(setcache);

		if (setcache.getInt("Uses", 0) > 0 && Settings.backupOneTimeUse) {
			return new UndoOutcome(UndoResult.ALREADY_USED, secondsAgo);
		}

		final ItemStack[] backupinv = backupContents(setcache.get("Inventory"), p.getInventory().getSize());
		if (backupinv == null) {
			// A backup file can exist with no contents if an earlier save failed
			// partway, or hold something that isn't an item if it was edited by hand.
			// Reading it blind used to throw straight out of the command.
			plugin.getLogger().warning(p.getName() + "'s backup file is empty or damaged, so it wasn't restored.");
			return new UndoOutcome(UndoResult.ERROR, secondsAgo);
		}

		final int uses = setcache.getInt("Uses", 0);
		setcache.set("Uses", uses + 1);
		try {
			setcache.save(f);
		} catch (Exception e) {
			plugin.getLogger().warning("Couldn't update " + p.getName() + "'s backup file, so it wasn't restored.");
			plugin.debugError(e);
			return new UndoOutcome(UndoResult.ERROR, secondsAgo);
		}

		try {
			// setContents replaces every slot, armor and off-hand included.
			p.getInventory().setContents(backupinv);
			p.updateInventory();
		} catch (Exception e) {
			plugin.getLogger().warning("Hm. We were unable to restore " + p.getName() + "'s backup.");
			plugin.debugError(e);
			setcache.set("Uses", uses);
			try {
				setcache.save(f);
			} catch (Exception ignored) {
				// Already reporting the failure; the backup is still on disk either way.
			}
			return new UndoOutcome(UndoResult.ERROR, secondsAgo);
		}

		Cooldowns.startFileCooldown(p);
		return new UndoOutcome(UndoResult.RESTORED, secondsAgo);
	}

	/**
	 * The stored inventory, or null if it's missing, holds anything but items and empty
	 * slots, or has more slots than the inventory it's going back into.
	 */
	private static ItemStack[] backupContents(Object stored, int inventorySize) {
		if (!(stored instanceof List<?> list) || list.size() > inventorySize) {
			return null;
		}
		final ItemStack[] contents = new ItemStack[list.size()];
		for (int i = 0; i < contents.length; i++) {
			final Object slot = list.get(i);
			if (slot != null && !(slot instanceof ItemStack)) {
				return null;
			}
			contents[i] = (ItemStack) slot;
		}
		return contents;
	}

	static void backupInv(Player p) {
		if (!Settings.backupEnabled) {
			return;
		}

		if (Settings.debug) {
			plugin.getLogger().info("[Debug] Calling backupInv() event.");
		}

		final File dir = cacheDir();
		if (!dir.isDirectory() && !dir.mkdirs()) {
			plugin.getLogger().warning("Couldn't create the Cache folder, so " + p.getName()
					+ "'s inventory was not backed up.");
			return;
		}

		final File f = cacheFile(p.getUniqueId());
		// Every key is written fresh, so there's no need to read and parse the last
		// backup (a whole serialized inventory) on the main thread first.
		final FileConfiguration setcache = new YamlConfiguration();

		setcache.set("Name", p.getName());
		setcache.set("Inventory", p.getInventory().getContents());
		setcache.set("Last-Backup", System.currentTimeMillis());
		setcache.set("Uses", 0);

		try {
			setcache.save(f);
			if (Settings.debug) {
				plugin.getLogger().info("[Debug] Saved cache for: " + p.getName());
			}
		} catch (Exception fileerr) {
			plugin.getLogger().warning("Couldn't save the inventory backup for " + p.getName());
			plugin.debugError(fileerr);
		}
	}

	public static void go(Player p) {
		go(p, null);
	}

	/**
	 * Clears an inventory with an animation.
	 *
	 * @param chosen the animation to play, or null for a random enabled one. A chosen animation
	 *               plays even while it's turned off, so it can be tried out before it's enabled.
	 */
	static void go(Player p, ClearAnimation chosen) {
		// Never start on top of another animation, even when the API skips its checks.
		// Starting over a clear backed up its half-drawn hotbar in place of the real
		// inventory, and starting over a fortune cancelled it with the player's items
		// still stashed and the fortune flag left on for good.
		if (Cooldowns.isBusy(p)) {
			if (Settings.debug) {
				plugin.getLogger().info("[Debug] Didn't clear " + p.getName()
						+ "'s inventory: they're already clearing or having a fortune.");
			}
			return;
		}

		// The registry is only there once the plugin has enabled; the API can be called sooner.
		final AnimationRegistry animations = plugin.getAnimations();
		final ClearAnimation animation = chosen != null ? chosen : animations == null ? null : animations.pick();
		if (animation == null) {
			plugin.getLogger().info("No clear animations are turned on in the " + AnimationRegistry.FOLDER
					+ " folder. Aborting.");
			return;
		}

		// Fire the API event before anything is touched. It used to run after the
		// player had already been marked active and their inventory backed up, and
		// the cancel path returned without clearing that flag - which left the
		// player permanently unable to clear, teleport or move items.
		final PlayerClearInventoryEvent pce = new PlayerClearInventoryEvent(p);
		Bukkit.getPluginManager().callEvent(pce);
		if (pce.isCancelled()) {
			return;
		}

		Cooldowns.markClearing(p);
		plugin.clearMessage(p);
		backupInv(p);

		if (Settings.debug) {
			plugin.getLogger().info("[Debug] Playing the '" + animation.name() + "' clear animation for "
					+ p.getName() + (chosen != null ? " (picked by command)" : "") + ".");
		}

		try {
			MC1_20.clear(p, animation);
		} catch (Exception e) {
			Cooldowns.unmarkClearing(p.getUniqueId());
			Timeline.stop(p);
			plugin.errorMsg(p, "'" + animation.name() + "' clear animation", e);
		}
	}
}
