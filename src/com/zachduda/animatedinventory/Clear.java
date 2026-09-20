package com.zachduda.animatedinventory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

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
	 * Everything now happens here, and the use is only stamped once the items are
	 * actually back in the inventory.
	 */
	@SuppressWarnings("unchecked")
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

		final Object stored = setcache.get("Inventory");
		if (!(stored instanceof List)) {
			// A backup file can exist with no contents if an earlier save failed
			// partway. Reading it blind used to throw straight out of the command.
			plugin.getLogger().warning(p.getName() + "'s backup file has no inventory in it.");
			return new UndoOutcome(UndoResult.ERROR, secondsAgo);
		}

		final ItemStack[] backupinv = ((List < ItemStack > ) stored).toArray(new ItemStack[0]);

		try {
			p.getInventory().clear();
			p.getInventory().setContents(backupinv);
			p.updateInventory();

			setcache.set("Uses", setcache.getInt("Uses", 0) + 1);
			setcache.save(f);
		} catch (Exception e) {
			plugin.getLogger().warning("Hm. We were unable to restore " + p.getName() + "'s backup.");
			plugin.debugError(e);
			return new UndoOutcome(UndoResult.ERROR, secondsAgo);
		}

		Cooldowns.startFileCooldown(p);
		return new UndoOutcome(UndoResult.RESTORED, secondsAgo);
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
		final FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);

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
		final List < Integer > animations = new ArrayList<>(5);

		addIf(animations, 1, "Pane_Animation");
		addIf(animations, 2, "Rainbow_Animation");
		addIf(animations, 3, "Explode_Animation");
		addIf(animations, 4, "Water_Animation");
		addIf(animations, 5, "Fireball_Animation");

		if (animations.isEmpty()) {
			plugin.getLogger().info("All animations were disabled in the config.yml. Aborting.");
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

		final int pick = animations.get(ThreadLocalRandom.current().nextInt(animations.size()));

		if (Settings.debug) {
			plugin.getLogger().info("[Debug] Enabled Animations: " + animations);
			plugin.getLogger().info("[Debug] Using random animation: " + pick);
		}

		try {
			switch (pick) {
				case 1 -> MC1_20.animation1(p);
				case 2 -> MC1_20.animation2(p);
				case 3 -> MC1_20.animation3(p);
				case 4 -> MC1_20.animation4(p);
				default -> MC1_20.animation5(p);
			}
		} catch (Exception e) {
			Cooldowns.unmarkClearing(p.getUniqueId());
			Timeline.stop(p);
			plugin.errorMsg(p, pick, e);
		}
	}

	private static void addIf(List < Integer > animations, int id, String name) {
		if (plugin.getConfig().getBoolean("features.clearing.animations." + name + ".enabled")) {
			animations.add(id);
			if (Settings.debug) {
				plugin.getLogger().info("[Debug] Adding " + name + " (" + id + ") to Animations array.");
			}
		}
	}
}
