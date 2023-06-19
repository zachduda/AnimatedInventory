package com.zachduda.animatedinventory.other;

import net.md_5.bungee.api.plugin.Plugin;

public class Bungee extends Plugin { // IGNORE THIS CLASS. IT'S ONLY IF PLAYER MISPLACED .JAR
	public void onEnable() {
		System.out.println("§4§m--------------------------------------------------------------");
		System.out.println("§c§lERROR! " + this.getDescription().getName() + " is NOT a bungee plugin.");
		System.out.println("§4§m--------------------------------------------------------------");
		System.out.println("§cPlease remove the " + this.getDescription().getName() + ".jar from your Bungee plugins");
		System.out.println("§cfolder once your bungee server has stopped. Instead place");
		System.out.println("§cthe " + this.getDescription().getName() + ".jar in your Spigot server plugins folder.");
		System.out.println("§4§m--------------------------------------------------------------");
	}
	public void onDisable() {
		System.out.println("§6§m--------------------------------------------------------------");
		System.out.println("§6§lREMINDER: Please remove the " + this.getDescription().getName() + ".jar");
		System.out.println("§6§m--------------------------------------------------------------");
		System.out.println("§7Please remove the " + this.getDescription().getName() + ".jar from your Bungee plugins");
		System.out.println("§7folder once your bungee server has stopped. This is not a bungee");
		System.out.println("§7plugin, and will not function as such. Sorry!");
		System.out.println("§6§m--------------------------------------------------------------");
	}
}