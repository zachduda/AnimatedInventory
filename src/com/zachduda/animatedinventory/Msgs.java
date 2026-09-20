package com.zachduda.animatedinventory;

import java.util.Objects;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class Msgs {
	private static final Main plugin = Main.getPlugin(Main.class);

	private static final String FALLBACK_PREFIX = "&8[&b&lA&r&bnimated&f&lI&r&fnv&8]";

	/** Set to false once the action bar fails, so we stop retrying per frame. */
	private static boolean actionBarWorks = true;

	private static String prefix() {
		final String configured = plugin.getConfig().getString("messages.prefix", FALLBACK_PREFIX);
		return ChatColor.translateAlternateColorCodes('&', Objects.requireNonNullElse(configured, FALLBACK_PREFIX));
	}

	static void send(CommandSender sender, String msg) {
		if (msg == null) {
			return;
		}
		sender.sendMessage(prefix() + " " + ChatColor.translateAlternateColorCodes('&', msg));
	}

	static void sendPrefix(CommandSender sender, String msg) {
		if (msg == null) {
			return;
		}
		sender.sendMessage(ChatColor.translateAlternateColorCodes('&', FALLBACK_PREFIX) + " "
				+ ChatColor.translateAlternateColorCodes('&', msg));
	}

	static void sendBar(CommandSender sender, String msg) {
		if (msg == null) {
			return;
		}

		if (!(sender instanceof Player p) || !actionBarWorks) {
			send(sender, msg);
			return;
		}

		try {
			ActionBar.send(p, ChatColor.translateAlternateColorCodes('&', msg));
		} catch (Exception e) {
			// Animations call this many times a second, so never keep retrying a
			// path that has already proven broken on this server.
			actionBarWorks = false;
			if (Settings.debug) {
				plugin.getLogger().info("[Debug] Failed to use hotbar message. Using normal msgs from now on.");
			}
			send(sender, msg);
		}
	}
}
