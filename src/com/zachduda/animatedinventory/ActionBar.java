package com.zachduda.animatedinventory;

import org.bukkit.entity.Player;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

public class ActionBar {

	/**
	 * TextComponent.fromLegacy only exists in recent versions of the chat library (1.20.x on).
	 * Older servers throw NoSuchMethodError for it, so they're switched to fromLegacyText the
	 * first time.
	 */
	private static boolean hasFromLegacy = true;

	@SuppressWarnings("deprecation")
	static void send(Player p, String message) {
		if (hasFromLegacy) {
			try {
				p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacy(message));
				return;
			} catch (NoSuchMethodError olderServer) {
				hasFromLegacy = false;
			}
		}
		p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(message));
	}
}
