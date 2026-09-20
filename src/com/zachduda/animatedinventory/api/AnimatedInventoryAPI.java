package com.zachduda.animatedinventory.api;

import org.bukkit.entity.Player;

import com.zachduda.animatedinventory.Clear;
import com.zachduda.animatedinventory.Cooldowns;
import com.zachduda.animatedinventory.MC1_20;
import com.zachduda.animatedinventory.Main;

public final class AnimatedInventoryAPI {
	private final static Main plugin = Main.getPlugin(Main.class);

	public static boolean isPlayerClearing(Player p) {
		return Cooldowns.isClearing(p);
	}

	public static boolean isPlayerFortune(Player p) {
		return Cooldowns.isFortune(p);
	}

	public static boolean ableToClear(Player p, boolean checkpermissions) {
		if (!plugin.isClearEnabled() || Cooldowns.onCooldown(p) || Cooldowns.isBusy(p)
				|| plugin.isClearDisabledIn(p.getWorld().getName())) {
			return false;
		}
		return !checkpermissions || p.hasPermission("animatedinv.clear");
	}

	public static boolean abletoFortune(Player p, boolean checkpermissions) {
		if (!plugin.isFortuneEnabled() || Cooldowns.onCooldown(p) || Cooldowns.isBusy(p)
				|| plugin.isFortuneDisabledIn(p.getWorld().getName())
				|| !Cooldowns.notHurt(p)) {
			return false;
		}
		return !checkpermissions || p.hasPermission("animatedinv.fortune");
	}

	public static boolean isCooldownActive(Player p) {
		return Cooldowns.onCooldown(p);
	}

	public static void doClear(Player p, boolean dochecks) {
		if (dochecks && !ableToClear(p, false)) {
			return;
		}
		// Clear.go() marks the player active itself; doing it here as well used to
		// leave a stale entry behind whenever go() bailed out.
		Clear.go(p);
	}

	public static void doFortune(Player p, boolean dochecks) {
		if (dochecks && !abletoFortune(p, false)) {
			return;
		}
		Cooldowns.activefortune.put(p.getUniqueId(), p.getName());
		try {
			MC1_20.fortune(p);
		} catch (Exception e) {
			Cooldowns.activefortune.remove(p.getUniqueId());
			throw e;
		}
	}

	public String getPluginVersion() {
		return plugin.getDescription().getVersion();
	}
}
