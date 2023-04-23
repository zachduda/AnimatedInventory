package com.zachduda.animatedinventory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.zachduda.animatedinventory.api.PlayerClearInventoryEvent;

public class Clear {
	private static Main plugin = Main.getPlugin(Main.class);

	static void purgeCache() {
		 Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
		boolean debug = plugin.getConfig().getBoolean("options.debug");
		if(debug) {
			plugin.getLogger().info("[Debug] Attempting to start purgeCache().");
		}
		
		if(!plugin.getConfig().getBoolean("features.clearing.inv-backup.enabled")) {
			if(debug) {
				plugin.getLogger().info("[Debug] Purge STOPPED. (It's disabled in the config)");
			}
			return;
		}
		
		if(debug) {
			plugin.getLogger().info("[Debug] Purge set to true in the config... Continuing...");
		}
				
		int purgeSec = plugin.getConfig().getInt("features.clearing.inv-backup.erase-after");
			File cache = new File(plugin.getDataFolder(), File.separator + "Cache");
	
			for(File cachefile:cache.listFiles())
            {
				
				File f = new File(cachefile.getPath());
				FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);
				
				long secondsAgo = Math.abs(((setcache.getLong("Last-Backup"))/1000) - (System.currentTimeMillis()/1000));
				
				String playername = setcache.getString("Name");
				
				if(debug) {
					plugin.getLogger().info("[Debug] File for " + playername + " is " + secondsAgo + " seconds old.");
				}
				
				if(secondsAgo > purgeSec) {
					f.delete();
					if(debug) {
					plugin.getLogger().info("[Debug] Deleted " + playername + "'s cache file because it's " + secondsAgo + "s old. (Purge if past " + purgeSec + "s" + ")");
					}
				} else {
					if(debug) {
						plugin.getLogger().info("[Debug] Kept " + playername + "'s cache file. Life Left: " + (purgeSec-secondsAgo) + " seconds");	
					}
				}
            }});
	}
	
    @SuppressWarnings("unchecked")
	static void undoClear(Player p) {
		if(!plugin.getConfig().getBoolean("features.clearing.inv-backup.enabled")) {
			return;
		}
		
		File cache = new File(plugin.getDataFolder(), File.separator + "Cache");
		File f = new File(cache, File.separator + "" + p.getUniqueId().toString() + ".yml");
		FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);
		
		if(!f.exists()) {
			plugin.getLogger().info("Request made for " + p.getName() + "'s inventory backup, but a file was not found.");
			return;
		}
		final int uses = setcache.getInt("Uses", 0);
		if(uses > 0 && plugin.getConfig().getBoolean("features.clearing.inv-backup.one-time-use")) {
			return;
		}
		Cooldowns.startFileCooldown(p);
		ItemStack[] backupinv = ((List<ItemStack>) setcache.get("Inventory")).toArray(new ItemStack[0]);
		
		p.getInventory().clear();
		p.getInventory().setContents(backupinv);
		p.updateInventory();
    }
    
	 static void backupInv(Player p) {
		boolean debug = plugin.getConfig().getBoolean("options.debug");
		
		if(debug) {
			plugin.getLogger().info("[Debug] Calling backupInv() event.");
		}
		
		if(!plugin.getConfig().getBoolean("features.clearing.inv-backup.enabled")) {
			return;
		}
		
		if(debug) {
			plugin.getLogger().info("[Debug] Passed config check. (Inventory backups are enabled)");
		}
		
			File cache = new File(plugin.getDataFolder(), File.separator + "Cache");
			File f = new File(cache, File.separator + "" + p.getUniqueId().toString() + ".yml");
			FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);
			
	        ItemStack[] inv = p.getInventory().getContents();
	        
		    if (!f.exists()) {
		    	if(debug) {
		    	plugin.getLogger().info("[Debug] Cache file for " + p.getName() + " is being created...");
		    	}
		    	try {
		    	setcache.save(f);
		    	} catch (Exception fileerr) {
		    		if(debug) {
		    		plugin.getLogger().info("Error creating log:");
		    		fileerr.printStackTrace();
		    		plugin.getLogger().info("[End of Error] -----------------------------");
		    		return;
		    	}}}
		    
			if(debug) {
				plugin.getLogger().info("[Debug] Passed original file creation check.");
			}
		    
		    if(!f.exists()) {
		    	if(debug) {
		    		plugin.getLogger().info("[Debug] Unusual Instance: UUID cache was trying to be made but wasn't.");
		    	}
		    	return;
		    }
		    
			if(debug) {
				plugin.getLogger().info("[Debug] Passed final file check.");
			}
		    
		    setcache.set("Name", p.getName());
		    setcache.set("Inventory", inv);
		    setcache.set("Last-Backup", System.currentTimeMillis());
		    try {
				setcache.save(f);
				if(debug) {
					plugin.getLogger().info("[Debug] Saved cache for: " + p.getName());
				}
			} catch (Exception fileerr) {
	    		if(debug) {
	    		plugin.getLogger().info("[Debug] Error creating log:");
	    		fileerr.printStackTrace();
	    		plugin.getLogger().info("[Debug]  [End of Error] -----------------------------");
	    		}
			}
	}

	public static void go(Player p) {
		final boolean debug = plugin.getConfig().getBoolean("options.debug");
		List<Integer> animations = new ArrayList<Integer>();
     
     Cooldowns.active.put(p, p.getName());
     plugin.clearMessage(p);
     backupInv(p);
     
// ___________ANIMATIONS______
 	
 	if(plugin.getConfig().getBoolean("features.clearing.animations.Pane_Animation.enabled")) {
   animations.add(1);
   			if(debug) {
   				plugin.getLogger().info("[Debug] Adding Pane_Animation (1) to Animations array.");
   			}
 	    }
 	if(plugin.getConfig().getBoolean("features.clearing.animations.Rainbow_Animation.enabled")) {
 	   animations.add(2);
			if(debug) {
   				plugin.getLogger().info("[Debug] Adding Rainbow_Animation (2) to Animations array.");
   			}
 	 	}
 	if(plugin.getConfig().getBoolean("features.clearing.animations.Explode_Animation.enabled")) {
  	   animations.add(3);
			if(debug) {
   				plugin.getLogger().info("[Debug] Adding Explode_Animation (3) to Animations array.");
   			}
  	 	}
 	if(plugin.getConfig().getBoolean("features.clearing.animations.Water_Animation.enabled")) {
  	   animations.add(4);
			if(debug) {
   				plugin.getLogger().info("[Debug] Adding Water_Animation (4) to Animations array.");
   			}
  	 	}
 	
 	if(plugin.getConfig().getBoolean("features.clearing.animations.Fireball_Animation.enabled")) {
   	   animations.add(5);
 			if(debug) {
    				plugin.getLogger().info("[Debug] Adding Fireball_Animation (5) to Animations array.");
    			}
   	 	}
 		
 	 if(animations.size() == 0 || animations.isEmpty()) {
 		 plugin.getLogger().info("All animations were disabled in the config.yml. Aborting.");
 		 Cooldowns.active.remove(p);
 		 animations.clear(); // most likely not needed, but it helps me sleep at night
 		 return;
 	 }
 	 
		PlayerClearInventoryEvent pce = new PlayerClearInventoryEvent(p);
		Bukkit.getPluginManager().callEvent(pce);
		if (pce.isCancelled()) {
			return;
		}
 	 
	Random random = new Random();
	Integer randomInt = animations.get(random.nextInt(animations.size()));
	
		if(debug) {
				plugin.getLogger().info("[Debug] Enabled Animations: " + animations.toString());
				plugin.getLogger().info("[Debug] Using random animation: " + randomInt);
			}
		
  	 try {
  		if(randomInt == 1) {
  		  		MC1_19.animation1(p);
  		} else
  	  		if(randomInt == 2) {
  	  		  		MC1_19.animation2(p);
  	  		} else
  	    		if(randomInt == 3) {
  	    		  		MC1_19.animation3(p);
  	    		} else
  	    	  		if(randomInt == 4) {
  	    	  		  		MC1_19.animation4(p);
  	    	  		} else 
  	    	  			if(randomInt == 5) {
	    	  		  			MC1_19.animation5(p);
  	    	  			}
  		animations.clear();
  	 }catch(Exception e) { 
  		 plugin.errorMsg(p, randomInt, e);
  	}

	} //end of go event
}