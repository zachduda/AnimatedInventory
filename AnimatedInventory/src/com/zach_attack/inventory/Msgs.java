package com.zach_attack.inventory;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.zach_attack.inventory.support.MC1_14AA;
import com.zach_attack.inventory.support.MC1_13AA;

public class Msgs {
	static Main plugin = Main.getPlugin(Main.class);
	
	public static void send(CommandSender sender, String msg) {
	    String prefix = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("messages.prefix"));
	    sender.sendMessage(prefix + " " + (ChatColor.translateAlternateColorCodes('&', msg)));	
	}
	
	public static void sendPrefix(CommandSender sender, String msg) {
	    String prefix = ChatColor.translateAlternateColorCodes('&', "&8[&b&lA&r&bnimated&f&lI&r&fnv&8]");
	    sender.sendMessage(prefix + " " + (ChatColor.translateAlternateColorCodes('&', msg)));	
	}
	
	public static void sendBar(CommandSender sender, String msg) {
		if(!(sender instanceof Player)) {
			sendPrefix(sender, msg);
			return;
		}
		
  	  try {
  		  Player p = (Player)sender;
  		  if(Bukkit.getBukkitVersion().contains("1.14")) {
  			MC1_14AA.sendActionbar(p, ChatColor.translateAlternateColorCodes('&', msg));  
  			return;
  		  } else if(Bukkit.getBukkitVersion().contains("1.13")){
  			  if(Bukkit.getBukkitVersion().contains("1.13.2")) {
  			MC1_13AA.sendActionbar(p, ChatColor.translateAlternateColorCodes('&', msg));  
  			return;
  			  } else {
  				  plugin.getLogger().warning("ERROR. Could not use Actionbar because you're not using the latest 1.13.2 version.");
  			  }
  		  }
	  } catch (Exception e) { 
		  if(plugin.getConfig().getBoolean("options.debug")) {
       plugin.getLogger().info("[Debug] Failed to use hotbar message. Using normal msg instead. Make sure you use 1.14/1.13/1.12");
		  }
       try {
       send(sender, msg);
       }catch(Exception e2) {
    	   plugin.getLogger().info("Failed to send message. Make sure your config is correct!");
    	   if(plugin.getConfig().getBoolean("options.debug")) {
    		   plugin.getLogger().info("[Debug] Check out the error below: ------------");
    		   e.printStackTrace();
    		   plugin.getLogger().info("[Debug] End of Error --------------------------");
    	   }
       }
      }
	}
}
