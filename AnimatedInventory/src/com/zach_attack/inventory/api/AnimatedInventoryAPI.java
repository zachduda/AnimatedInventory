package com.zach_attack.inventory.api;

import org.bukkit.entity.Player;
import com.zach_attack.inventory.Clear;
import com.zach_attack.inventory.Cooldowns;
import com.zach_attack.inventory.MC1_15;
import com.zach_attack.inventory.Main;

public final class AnimatedInventoryAPI {
	private final static Main plugin = Main.getPlugin(Main.class);
	
	public static boolean isPlayerClearing(Player p) {
	
		if(Cooldowns.active.containsKey(p)){
		return true;
		
		}else{
			
		return false;
		}}
	
	public static boolean isPlayerFortune(Player p) {
		 
		if(Cooldowns.activefortune.containsKey(p)){
		return true;
		
		}else{
			
		return false;
		}}
	
	public static boolean ableToClear(Player p, boolean checkpermissions) {
		    if(plugin.getConfig().getBoolean("features.clearing.enabled") &&
		    !Cooldowns.cooldown.containsKey(p) && !Cooldowns.active.containsKey(p) && !Cooldowns.activefortune.containsKey(p)
		    && !plugin.disabledclearworld.contains(p.getWorld().getName())) {
				if (p.hasPermission("animatedinv.clear") || !checkpermissions) {
		return true;
				} else {
		return false;
				}
		}else{
		return false;
		}}
	
	public static boolean abletoFortune(Player p, boolean checkpermissions) {
		 
	    if(plugin.getConfig().getBoolean("features.fortunes.enabled") &&
	    !Cooldowns.cooldown.containsKey(p) && !Cooldowns.active.containsKey(p) && !Cooldowns.activefortune.containsKey(p)
	    && !plugin.disabledfortuneworld.contains(p.getWorld().getName()) && !Cooldowns.isBeinghurt.containsKey(p)) {
			if (p.hasPermission("animatedinv.fortune") || !checkpermissions) {
	return true;
			} else {
	return false;
			}
	}else{
	return false;
	}}
	
	public static boolean isCooldownActive(Player p) {
		 
		if(Cooldowns.cooldown.containsKey(p)){
		return true;
		
		}else{
			
		return false;
		}}
	
	
	public static void doClear(Player p, boolean dochecks) {
		if(dochecks) {
		if(!Cooldowns.cooldown.containsKey(p) && !Cooldowns.active.containsKey(p) && !Cooldowns.activefortune.containsKey(p)
			    && !plugin.disabledclearworld.contains(p.getWorld().getName())) {
		      Clear.go(p);
		      Cooldowns.active.put(p, p.getName()); 
		}
	} else {
		Clear.go(p);
		 Cooldowns.active.put(p, p.getName()); 
	}}
	
	public static void doFortune(Player p, boolean dochecks) {
		if(dochecks) {
		if(!Cooldowns.cooldown.containsKey(p) && !Cooldowns.active.containsKey(p) && Cooldowns.notHurt(p) && !Cooldowns.activefortune.containsKey(p)
			    && !plugin.disabledfortuneworld.contains(p.getWorld().getName())) {
   		    	MC1_15.fortune(p);
				Cooldowns.activefortune.put(p, p.getName()); 
		}
	} else {
	    MC1_15.fortune(p);
		Cooldowns.activefortune.put(p, p.getName()); 
	}}
	
	public String getPluginVersion() {
		return plugin.getDescription().getVersion();
	}
	
}
