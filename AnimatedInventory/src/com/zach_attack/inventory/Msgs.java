package com.zach_attack.inventory;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class Msgs {
	static Main plugin = Main.getPlugin(Main.class);
	
	static void send(CommandSender sender, String msg) {
	    String prefix = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("messages.prefix"));
	    sender.sendMessage(prefix + " " + (ChatColor.translateAlternateColorCodes('&', msg)));	
	}
	
	static void sendPrefix(CommandSender sender, String msg) {
	    String prefix = ChatColor.translateAlternateColorCodes('&', "&8[&b&lA&r&bnimated&f&lI&r&fnv&8]");
	    sender.sendMessage(prefix + " " + (ChatColor.translateAlternateColorCodes('&', msg)));	
	}
	
	static void sendBar(CommandSender sender, String msg) {
		if(!(sender instanceof Player)) {
			sendPrefix(sender, msg);
			return;
		}
		
  	  try {
  		  Player p = (Player)sender;
  		  ActionBar.send(p, ChatColor.translateAlternateColorCodes('&', msg));  
  		  return;
	  } catch (Exception e) { 
		  if(plugin.getConfig().getBoolean("options.debug")) {
       plugin.getLogger().info("[Debug] Failed to use hotbar message. Using normal msg instead. Make sure you use 1.15/1.14/1.13/1.12");
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
