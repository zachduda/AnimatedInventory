package com.zach_attack.inventory;

import java.util.HashMap;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

import com.zach_attack.inventory.Main;

	public class Cooldowns implements Listener {

		static Main plugin = Main.getPlugin(Main.class);


		public static HashMap<Player, String> cooldown = new HashMap<Player, String>();
		public static HashMap<Player, String> filecooldown = new HashMap<Player, String>();
		public static HashMap<Player, String> active = new HashMap<Player, String>();
		public static HashMap<Player, String> activefortune = new HashMap<Player, String>();
		public static HashMap <Player, ItemStack[]> inventories = new HashMap <Player, ItemStack[]>();
		public static HashMap <Player, Long> isBeinghurt = new HashMap <Player, Long>();
		
	   static void removeAll(final Player p) {
		   boolean debug =  plugin.getConfig().getBoolean("options.debug");
			cooldown.remove(p);
			active.remove(p);
			activefortune.remove(p);
			inventories.remove(p);
			filecooldown.remove(p);
			isBeinghurt.remove(p);
			inventories.remove(p.getPlayer());
			if(debug) {
				plugin.getLogger().info("[Debug] Called removeAll() event under Cooldowns.");
			}
		}
	   
	   static boolean notHurt(final Player p) {
		   if(!plugin.getConfig().getBoolean("features.fortunes.prevent-if-being-hurt")) {
			   return true;
		   }
		   
		   boolean debug =  plugin.getConfig().getBoolean("options.debug");
		   if(!Cooldowns.isBeinghurt.containsKey(p.getPlayer())) {
			   if(debug) {
				   plugin.getLogger().info("[Debug] Player not on isBeingHurt hashmap. notHurt() check passed.");
			   }
			   return true;
		   }
		   
		   long lastHurt = Math.abs((isBeinghurt.get(p.getPlayer())/1000) - (System.currentTimeMillis()/1000));
		   if(lastHurt >= 7) {
			   if(debug) {
				   plugin.getLogger().info("[Debug] Passed notHurt() check with lastHurt @ " + lastHurt); 
			   }
			   isBeinghurt.remove(p.getPlayer());
			   return true;
		   }
		   return false;
	   }
	   
	   
		  static void startCooldown(final Player p)
		  {

			  if(plugin.getConfig().getBoolean("options.cooldowns.enabled")) {
			  try{
		    if (plugin.getConfig().getString("options.cooldowns.time").equalsIgnoreCase("none")
		    		|| plugin.getConfig().getInt("options.cooldowns.time") == 0)
		    {
				  
			    Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable()
			    {
			      public void run()
			      {
			        cooldown.remove(p.getPlayer());
			      }
			    }, 20L);
		      return;
		    } // end of if time set to 0
			  
		    Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable()
		    {
		      public void run()
		      {
				  
				    if (!plugin.getConfig().getString("options.cooldowns.time").equalsIgnoreCase("none")
				    		&& !(plugin.getConfig().getInt("options.cooldowns.time") == 0))
				    {
		        Cooldowns.cooldown.remove(p.getPlayer());
				    }
		      }
		    }, plugin.getConfig().getInt("options.cooldowns.time") * 20);
		    
		    
			  }catch(Exception e){
				  System.out.print("[AnimatedInventory] Error! Couldn't exceute the cooldown timer properly.");
				  if(plugin.getConfig().getBoolean("options.debug")) {
					  plugin.getLogger().info("[Debug] Error is below:");
					 e.printStackTrace();
				  }
			    }
			  }
		  }
		  
		  static void startFileCooldown(final Player p)
		  {
			  try{
		    if (plugin.getConfig().getString("features.clearing.inv-backup.backup-cooldown").equalsIgnoreCase("none")
		    		|| plugin.getConfig().getInt("features.clearing.inv-backup.backup-cooldown") == 0)
		    {
		      return;
		    } // end of if time set to 0
			  
		    filecooldown.put(p.getPlayer(), p.getName());
		    
		    Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable()
		    {
		      public void run()
		      {
				  
				    if (!plugin.getConfig().getString("features.clearing.inv-backup.backup-cooldown").equalsIgnoreCase("none")
				    		&& !(plugin.getConfig().getInt("features.clearing.inv-backup.backup-cooldown") == 0))
				    {
		        filecooldown.remove(p.getPlayer());
				    }
		      }
		    }, plugin.getConfig().getInt("features.clearing.inv-backup.backup-cooldown") * 22);
		    
			  }catch(Exception e){
				  System.out.print("[AnimatedInventory] Error! Couldn't exceute the file cooldown timer properly.");
				  if(plugin.getConfig().getBoolean("options.debug")) {
					  plugin.getLogger().info("[Debug] Error is below:");
					 e.printStackTrace();
				  }
			    }
		  }
		  
		  static void removeActive(final Player p)
		  {
			  
			  try{
		    
		    Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable()
		    {
		      public void run()
		      {
		        Cooldowns.active.remove(p.getPlayer());
                
    		    if (plugin.getConfig().getString("options.cooldowns.time").equalsIgnoreCase("none")
    		    		|| plugin.getConfig().getInt("options.cooldowns.time") == 0 || !plugin.getConfig().getBoolean("options.cooldowns.enabled"))
    		    { 
                   return;
    		    }
                  Cooldowns.cooldown.put(p, p.getName());
                Cooldowns.startCooldown(p); }
    		    
		    }, 10L);
			  }catch(Exception e){
				  System.out.print("[AnimatedInventory] Error! Couldn't exceute the active timer properly.");
				  }
		  }
		  
		  public static void removeFortune(final Player p)
		  {
			  
			  try {
		        Cooldowns.activefortune.remove(p.getPlayer());
                
    		    if (plugin.getConfig().getString("options.cooldowns.time").equalsIgnoreCase("none")
    		    		|| plugin.getConfig().getInt("options.cooldowns.time") == 0 || !plugin.getConfig().getBoolean("options.cooldowns.enabled"))
    		    { 
                   return;
    		    }
                  Cooldowns.cooldown.put(p, p.getName());
                Cooldowns.startCooldown(p); 
			  }catch(Exception e){
				  System.out.print("[AnimatedInventory] Error! Couldn't exceute the active timer properly.");
				  }
		  }

}
