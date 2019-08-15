
package com.zach_attack.inventory.support;

import java.util.Random;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.zach_attack.inventory.Cooldowns;
import com.zach_attack.inventory.Main;
import com.zach_attack.inventory.Msgs;
import com.zach_attack.inventory.Particlez;

public class MC1_14 implements Listener{
	static Main plugin = Main.getPlugin(Main.class);
	  
		public static void emergencyRemove() {
			try {
			  final ItemStack one = new ItemStack(Material.LIME_CONCRETE);
			    ItemMeta onem = one.getItemMeta();
			    onem.setDisplayName(ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("features.fortunes.yes-block.name")));
			    one.setItemMeta(onem);
			    
				  final ItemStack two = new ItemStack(Material.RED_CONCRETE);
				    ItemMeta twom = two.getItemMeta();
				    twom.setDisplayName(ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("features.fortunes.no-block.name")));
				    two.setItemMeta(twom);
				    
					  final ItemStack fglass = new ItemStack(Material.WHITE_STAINED_GLASS_PANE);
					    ItemMeta fglassm = fglass.getItemMeta();
					    fglassm.setDisplayName("§7§l§m---");
					    fglass.setItemMeta(fglassm);
					    
						  final ItemStack token = new ItemStack(Material.getMaterial(plugin.getConfig().getString("features.clearing.token-item")));
						    ItemMeta tokenm = token.getItemMeta();
						    tokenm.setDisplayName(ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("features.clearing.token")));
						    token.setItemMeta(tokenm);
						    
							    for (Player online : Bukkit.getServer().getOnlinePlayers())
							    {  
						  if(online.getInventory().contains(one) && online.getInventory().contains(two)) {
							  online.getInventory().clear(); 
							  plugin.getLogger().info("Attempted to restore " + online.getName() + "'s inventory. (They were in the middle of a fortune)");
							  Cooldowns.activefortune.remove(online); 
							  online.sendMessage("§c§lSorry! §fThe plugin was reloaded, we have ended your fortune.");
							  plugin.bass(online);
							  try {
							  plugin.loadInv(online);
							  } catch (Exception e) { plugin.getLogger().info("ERROR! Couldn't restore " + online.getName() + "'s inventory on plugin disable!");}
							  plugin.deleteInv(online);
						  }
						  
						  if(online.getInventory().contains(token)) {
							  online.getInventory().clear(); 
							  online.sendMessage("§c§lSorry! §fThe plugin was reloaded, we force cleared your inventory.");
							  plugin.bass(online);
							  plugin.getLogger().info("Attempted to clear " + online.getName() + "'s inventory. (They were in the middle of clearing.)");
							    }
							    }
			} catch(Exception err) {
				plugin.getLogger().info("Couldn't search player inventories on shutdown. Did you change the .jar?");
			}
					}
		
	  public static void fortune(CommandSender sender) {
	  final Player p = (Player)sender;
	  boolean debug = (plugin.getConfig().getBoolean("options.debug"));
	  int good_luck_int = plugin.getConfig().getInt("features.fortunes.result.good-luck");
	  
	  plugin.saveInv(p);
	  
      final ItemStack wgp = new ItemStack(Material.WHITE_STAINED_GLASS_PANE);
      ItemMeta wgpm = wgp.getItemMeta();
      wgpm.setDisplayName("§7§l§m---");
      wgp.setItemMeta(wgpm);
	  
      final ItemStack one = new ItemStack(Material.LIME_CONCRETE);
      ItemMeta onem = one.getItemMeta();
      onem.setDisplayName(ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("features.fortunes.yes-block.name")));
      one.setItemMeta(onem);
      
      final ItemStack two = new ItemStack(Material.RED_CONCRETE);
      ItemMeta twom = two.getItemMeta();
      twom.setDisplayName(ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("features.fortunes.no-block.name")));
      two.setItemMeta(twom);
      
      plugin.pop(p);
   	  p.getInventory().setItem(0, wgp);
   	  p.getInventory().setItem(1, wgp);
   	  p.getInventory().setItem(2, one);
   	  p.getInventory().setItem(3, wgp);
   	  p.getInventory().setItem(4, wgp);
   	  p.getInventory().setItem(5, wgp);
   	  p.getInventory().setItem(6, two);
   	  p.getInventory().setItem(7, wgp);
   	  p.getInventory().setItem(8, wgp);
   	  
      Random generator = new Random();
      int scount = 100;
	      int s = generator.nextInt(scount);
	      if(debug) {
	    	  plugin.getLogger().info("[Debug] Final Int Pick: " + s);
	    	  plugin.getLogger().info("[Debug] For Yes the # must be less than or equal to " + good_luck_int + ". Otherwise, it's no. Max Number: 100");
	      }
 	      String spinmsg = "§f§l§o" + plugin.getConfig().getString("features.fortunes.spin-message");
 	     String spinmsg2 = "§7§l§o" + plugin.getConfig().getString("features.fortunes.spin-message");
 	     
      Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
          public void run()
          {
            plugin.tick(p);
        	  Msgs.sendBar(p.getPlayer(), spinmsg2);
        	  p.getPlayer().getInventory().setHeldItemSlot(0);
          }
        }, 6L);
      Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
          public void run()
          {
            plugin.tick(p);
      	  Msgs.sendBar(p.getPlayer(), spinmsg);
        	  p.getPlayer().getInventory().setHeldItemSlot(1);
          }
        }, 15L);
      Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
          public void run()
          {
            plugin.tick(p);
      	  Msgs.sendBar(p.getPlayer(), spinmsg2);
        	  p.getPlayer().getInventory().setHeldItemSlot(2);
          }
        }, 18L);
      Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
          public void run()
          {
            plugin.tick(p);
      	  Msgs.sendBar(p.getPlayer(), spinmsg);
        	  p.getPlayer().getInventory().setHeldItemSlot(3);
          }
        }, 20L);
      Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
          public void run()
          {
            plugin.tick(p);
      	  Msgs.sendBar(p.getPlayer(), spinmsg2);
        	  p.getPlayer().getInventory().setHeldItemSlot(4);
          }
        }, 22L);
      Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
          public void run()
          {
            plugin.tick(p);
      	  Msgs.sendBar(p.getPlayer(), spinmsg);
        	  p.getPlayer().getInventory().setHeldItemSlot(5);
          }
        }, 23L);
      Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
          public void run()
          {
            plugin.tick(p);
      	  Msgs.sendBar(p.getPlayer(), spinmsg2);
        	  p.getPlayer().getInventory().setHeldItemSlot(6);
          }
        }, 24L);
      Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
          public void run()
          {
            plugin.tick(p);
      	  Msgs.sendBar(p.getPlayer(), spinmsg);
        	  p.getPlayer().getInventory().setHeldItemSlot(7);
          }
        }, 25L);
      Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
          public void run()
          {
            plugin.tick(p);
      	  Msgs.sendBar(p.getPlayer(), spinmsg2);
        	  p.getPlayer().getInventory().setHeldItemSlot(8);
          }
        }, 26L);
      Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
          public void run()
          {
            plugin.tick(p);
      	  Msgs.sendBar(p.getPlayer(), spinmsg);
        	  p.getPlayer().getInventory().setHeldItemSlot(0);
          }
        }, 27L);
      Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
          public void run()
          {
            plugin.tick(p);
      	  Msgs.sendBar(p.getPlayer(), spinmsg2);
        	  p.getPlayer().getInventory().setHeldItemSlot(1);
          }
        }, 28L);
      Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
          public void run()
          {
            plugin.tick(p);
      	  Msgs.sendBar(p.getPlayer(), spinmsg);
        	  p.getPlayer().getInventory().setHeldItemSlot(2);
          }
        }, 29L);
      Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
          public void run()
          {
            plugin.tick(p);
      	  Msgs.sendBar(p.getPlayer(), spinmsg2);
        	  p.getPlayer().getInventory().setHeldItemSlot(3);
          }
        }, 30L);
      Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
          public void run()
          {
            plugin.tick(p);
      	  Msgs.sendBar(p.getPlayer(), spinmsg);
        	  p.getPlayer().getInventory().setHeldItemSlot(4);
          }
        }, 31L);
      Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
          public void run()
          {
            plugin.tick(p);
      	  Msgs.sendBar(p.getPlayer(), spinmsg2);
        	  p.getPlayer().getInventory().setHeldItemSlot(5);
          }
        }, 32L);
      Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
          public void run()
          {
            plugin.tick(p);
      	  Msgs.sendBar(p.getPlayer(), spinmsg);
        	  p.getPlayer().getInventory().setHeldItemSlot(6);
          }
        }, 33L);
      Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
          public void run()
          {
            plugin.tick(p);
      	  Msgs.sendBar(p.getPlayer(), spinmsg2);
        	  p.getPlayer().getInventory().setHeldItemSlot(7);
          }
        }, 34L);
      Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
          public void run()
          {
            plugin.tick(p);
      	  Msgs.sendBar(p.getPlayer(), spinmsg);
        	  p.getPlayer().getInventory().setHeldItemSlot(8);
          }
        }, 35L);
      Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
          public void run()
          {
            plugin.tick(p);
      	  Msgs.sendBar(p.getPlayer(), spinmsg2);
        	  p.getPlayer().getInventory().setHeldItemSlot(0);
          }
        }, 36L);
      Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
          public void run()
          {
            plugin.tick(p);
      	  Msgs.sendBar(p.getPlayer(), spinmsg);
        	  p.getPlayer().getInventory().setHeldItemSlot(1);
          }
        }, 37L);
      Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
          public void run()
          {
            plugin.tick(p);
      	  Msgs.sendBar(p.getPlayer(), spinmsg2);
        	  p.getPlayer().getInventory().setHeldItemSlot(2);
          }
        }, 38L);
      Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
          public void run()
          {
            plugin.tick(p);
      	  Msgs.sendBar(p.getPlayer(), spinmsg);
        	  p.getPlayer().getInventory().setHeldItemSlot(3);
          }
        }, 39L);
      Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
          public void run()
          {
            plugin.tick(p);
      	  Msgs.sendBar(p.getPlayer(), spinmsg2);
        	  p.getPlayer().getInventory().setHeldItemSlot(4);
          }
        }, 40L);
      Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
          public void run()
          {
            plugin.tick(p);
      	  Msgs.sendBar(p.getPlayer(), spinmsg);
        	  p.getPlayer().getInventory().setHeldItemSlot(5);
          }
        }, 42L);
      
      Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
          public void run()
          {
            plugin.tick(p);
      	  Msgs.sendBar(p.getPlayer(), spinmsg2);
        	  p.getPlayer().getInventory().setHeldItemSlot(6);
          }
        }, 44L);
      Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
          public void run()
          {
            plugin.tick(p);
      	  Msgs.sendBar(p.getPlayer(), spinmsg);
        	  p.getPlayer().getInventory().setHeldItemSlot(7);
          }
        }, 47L);
      Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
          public void run()
          {
            plugin.tick(p);
      	  Msgs.sendBar(p.getPlayer(), spinmsg2);
        	  p.getPlayer().getInventory().setHeldItemSlot(8);
          }
        }, 50L);
      Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
          public void run()
          {
            plugin.tick(p);
      	  Msgs.sendBar(p.getPlayer(), spinmsg);
        	  p.getPlayer().getInventory().setHeldItemSlot(0);
          }
        }, 53L);
      Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
          public void run()
          {
            plugin.tick(p);
      	  Msgs.sendBar(p.getPlayer(), spinmsg2);
        	  p.getPlayer().getInventory().setHeldItemSlot(1);
          }
        }, 57L);
      Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
          public void run()
          {
            plugin.tick(p);
      	  Msgs.sendBar(p.getPlayer(), spinmsg);
        	  p.getPlayer().getInventory().setHeldItemSlot(2);
          }
        }, 61L);
      Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
          public void run()
          {
            plugin.tick(p);
      	  Msgs.sendBar(p.getPlayer(), spinmsg2);
        	  p.getPlayer().getInventory().setHeldItemSlot(3);
          }
        }, 65L);
      Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
          public void run()
          {
            plugin.tick(p);
      	  Msgs.sendBar(p.getPlayer(), spinmsg);
        	  p.getPlayer().getInventory().setHeldItemSlot(4);
          }
        }, 69L);
      Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
          public void run()
          {
            plugin.tick(p);
      	  Msgs.sendBar(p.getPlayer(), spinmsg2);
        	  p.getPlayer().getInventory().setHeldItemSlot(5);
          }
        }, 74L);
      Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
          public void run()
          {
            plugin.tick(p);
      	  Msgs.sendBar(p.getPlayer(), spinmsg);
        	  p.getPlayer().getInventory().setHeldItemSlot(6);
          }
        }, 79L);
      Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
          public void run()
          {
            plugin.tick(p);
      	  Msgs.sendBar(p.getPlayer(), spinmsg2);
        	  p.getPlayer().getInventory().setHeldItemSlot(7);
          }
        }, 84L);
      Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
          public void run()
          {
            plugin.tick(p);
      	  Msgs.sendBar(p.getPlayer(), spinmsg);
        	  p.getPlayer().getInventory().setHeldItemSlot(8);
          }
        }, 89L);
      Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
          public void run()
          {
            plugin.tick(p);
      	  Msgs.sendBar(p.getPlayer(), spinmsg2);
        	  p.getPlayer().getInventory().setHeldItemSlot(0);
          }
        }, 96L);
      Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
          public void run()
          {
            plugin.tick(p);
        	  p.getPlayer().getInventory().setHeldItemSlot(1);
          	  Msgs.sendBar(p.getPlayer(), spinmsg);
          }
        }, 102L);
      Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
          public void run()
          {
            plugin.tick(p);
        	  p.getPlayer().getInventory().setHeldItemSlot(2);
          	  Msgs.sendBar(p.getPlayer(), spinmsg2);
          }
        }, 110L);
      // FINAL FORTUNE ---------------------------------
      if(s <= good_luck_int) {
    	  if(plugin.getConfig().getBoolean("features.fortunes.result.notify-console")) {
    	  plugin.getLogger().info(p.getName() + " got 'YES' on their fortune.");
    	  }
      Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
          public void run()
          {
        	  Msgs.sendBar(p.getPlayer(), plugin.getConfig().getString("features.fortunes.result.-yes"));
        	  p.getPlayer().getInventory().setHeldItemSlot(2);
        	  Particlez.yesParticle(p);
          }
        }, 123L);
      Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
          public void run()
          {
        	  Msgs.sendBar(p.getPlayer(), plugin.getConfig().getString("features.fortunes.result.-yes"));
          }
        }, 135L);
      Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
          public void run()
          {
        	  if(p.isOnline()) {
              plugin.clearingsound(p);
          	  p.getPlayer().getInventory().setHeldItemSlot(0);
          	  plugin.burp(p);
          	  try {
          	  plugin.loadInv(p);
          	  } catch (Exception e) { plugin.getLogger().info("ERROR! Couldn't load back " + p.getName() + "'s inventory after a fortune.");}
          	  plugin.deleteInv(p);
          	  Cooldowns.removeFortune(p);
        		  Msgs.sendBar(p.getPlayer(), ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("features.clearing.done-msg")));
        	  }
          }
        }, 185L);
      } else {
    	  if(plugin.getConfig().getBoolean("features.fortunes.result.notify-console")) {
    	  plugin.getLogger().info(p.getName() + " got 'NO' on their fortune.");
    	  }
          Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
              public void run()
              {
                plugin.tick(p);
            	  p.getPlayer().getInventory().setHeldItemSlot(3);
              	  Msgs.sendBar(p.getPlayer(), spinmsg);
              }
            }, 115L);	
          Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
              public void run()
              {
                plugin.tick(p);
            	  p.getPlayer().getInventory().setHeldItemSlot(4);
              	  Msgs.sendBar(p.getPlayer(), spinmsg2);
              }
            }, 122L);
          Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
              public void run()
              {
                plugin.tick(p);
            	  p.getPlayer().getInventory().setHeldItemSlot(5);
            	  Msgs.sendBar(p.getPlayer(), spinmsg);
              }
            }, 128L);
          Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
              public void run()
              {
                plugin.tick(p);
            	  p.getPlayer().getInventory().setHeldItemSlot(6);
            	  Msgs.sendBar(p.getPlayer(), plugin.getConfig().getString("features.fortunes.result.-no"));
            	  Particlez.noParticle(p);
              }
            }, 136L);
          Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
              public void run()
              {
            	  Msgs.sendBar(p.getPlayer(), plugin.getConfig().getString("features.fortunes.result.-no"));
              }
            }, 150L);
          Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
              public void run()
              {
            	  if(p.isOnline()) {
                plugin.clearingsound(p);
            	  p.getPlayer().getInventory().setHeldItemSlot(0);
            	  plugin.burp(p);
              	  try {
                  	  plugin.loadInv(p);
                  	  } catch (Exception e) { plugin.getLogger().info("ERROR! Couldn't load back " + p.getName() + "'s inventory after a fortune.");}
            	  plugin.deleteInv(p);
            	  Cooldowns.removeFortune(p);
          		  Msgs.sendBar(p.getPlayer(), ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("features.fortunes.done-msg")));
            	  }
              }
            }, 200L);
      }
	  
	  }
	
	  //_______________________________________________________ ANIMATION 2
	  public static void animation2(CommandSender sender) {
		  final Player p = (Player)sender;
		  
	       Random generator = new Random();
		      int scount = 5; // MAX
		      int s = generator.nextInt(scount);
		  
         final ItemStack redwool = new ItemStack(Material.RED_WOOL);
         ItemMeta redwoolmeta = redwool.getItemMeta();
         redwoolmeta.setDisplayName("§a§e");
         redwool.setItemMeta(redwoolmeta);
         
         final ItemStack orangewool = new ItemStack(Material.ORANGE_WOOL);
         ItemMeta orangewoolmeta = redwool.getItemMeta();
         orangewoolmeta.setDisplayName("§a§e");
         orangewool.setItemMeta(orangewoolmeta);
         
         
         final ItemStack yellowwool = new ItemStack(Material.YELLOW_WOOL);
         ItemMeta yellowwoolmeta = redwool.getItemMeta();
         yellowwoolmeta.setDisplayName("§a§e");
         yellowwool.setItemMeta(yellowwoolmeta);
         
         final ItemStack greenwool = new ItemStack(Material.GREEN_WOOL);
         ItemMeta greenwoolmeta = redwool.getItemMeta();
         greenwoolmeta.setDisplayName("§a§e");
         greenwool.setItemMeta(greenwoolmeta);
         
         final ItemStack bluewool = new ItemStack(Material.BLUE_WOOL);
         ItemMeta bluewoolmeta = redwool.getItemMeta();
         bluewoolmeta.setDisplayName("§a§e");
         bluewool.setItemMeta(bluewoolmeta);
         
         final ItemStack purplewool = new ItemStack(Material.PURPLE_WOOL);
         ItemMeta purplewoolmeta = redwool.getItemMeta();
         purplewoolmeta.setDisplayName("§a§e");
         purplewool.setItemMeta(purplewoolmeta);
         
		  final ItemStack token = new ItemStack(Material.NAME_TAG);
		    ItemMeta tokenm = token.getItemMeta();
		    tokenm.setDisplayName(ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("features.clearing.token")));
		    token.setItemMeta(tokenm);
     	  p.getInventory().setItem(22, token);
         
         final ItemStack air = new ItemStack(Material.AIR);
         ItemMeta airmeta = air.getItemMeta();
         air.setItemMeta(airmeta);
         
         Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable()
         {
           public void run()
           {
         	  plugin.clearingsound(p);
         	  if(s == 0) {
         	  p.getInventory().setItem(0, redwool);
         	  p.getInventory().setItem(1, orangewool);
         	  p.getInventory().setItem(2, yellowwool);
         	  p.getInventory().setItem(3, greenwool);
         	  p.getInventory().setItem(4, bluewool);
         	  p.getInventory().setItem(5, purplewool);
         	  p.getInventory().setItem(6, redwool);
         	  p.getInventory().setItem(7, orangewool);
         	  p.getInventory().setItem(8, yellowwool);
         	  }
         	  if(s == 1) {
         	  p.getInventory().setItem(0, orangewool);
         	  p.getInventory().setItem(1, yellowwool);
         	  p.getInventory().setItem(2, greenwool);
         	  p.getInventory().setItem(3, bluewool);
         	  p.getInventory().setItem(4, purplewool);
         	  p.getInventory().setItem(5, redwool);
         	  p.getInventory().setItem(6, orangewool);
         	  p.getInventory().setItem(7, yellowwool);
         	  p.getInventory().setItem(8, greenwool);
         	  }
         	  if(s == 2) {
         	  p.getInventory().setItem(0, yellowwool);
         	  p.getInventory().setItem(1, greenwool);
         	  p.getInventory().setItem(2, bluewool);
         	  p.getInventory().setItem(3, purplewool);
         	  p.getInventory().setItem(4, redwool);
         	  p.getInventory().setItem(5, orangewool);
         	  p.getInventory().setItem(6, yellowwool);
         	  p.getInventory().setItem(7, greenwool);
         	  p.getInventory().setItem(8, redwool);
         	  }
         	  if(s == 3) {
         	  p.getInventory().setItem(0, greenwool);
         	  p.getInventory().setItem(1, bluewool);
         	  p.getInventory().setItem(2, purplewool);
         	  p.getInventory().setItem(3, redwool);
         	  p.getInventory().setItem(4, orangewool);
         	  p.getInventory().setItem(5, yellowwool);
         	  p.getInventory().setItem(6, greenwool);
         	  p.getInventory().setItem(7, redwool);
         	  p.getInventory().setItem(8, orangewool);
         	  }
         	  if(s == 4) {
         	  p.getInventory().setItem(0, bluewool);
         	  p.getInventory().setItem(1, purplewool);
         	  p.getInventory().setItem(2, redwool);
         	  p.getInventory().setItem(3, orangewool);
         	  p.getInventory().setItem(4, yellowwool);
         	  p.getInventory().setItem(5, greenwool);
         	  p.getInventory().setItem(6, redwool);
         	  p.getInventory().setItem(7, orangewool);
         	  p.getInventory().setItem(8, yellowwool);
         	  }
         	  if(s == 5) {
         	  p.getInventory().setItem(0, purplewool);
         	  p.getInventory().setItem(1, redwool);
         	  p.getInventory().setItem(2, orangewool);
         	  p.getInventory().setItem(3, yellowwool);
         	  p.getInventory().setItem(4, greenwool);
         	  p.getInventory().setItem(5, redwool);
         	  p.getInventory().setItem(6, orangewool);
         	  p.getInventory().setItem(7, yellowwool);
         	  p.getInventory().setItem(8, greenwool);
 		      }
           }
         }, 0);
         
         Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable()
         {
           public void run()
           {
         	  plugin.clearingsound(p);
         	  if(s == 0) {
         	  p.getInventory().setItem(0, orangewool);
         	  p.getInventory().setItem(1, yellowwool);
         	  p.getInventory().setItem(2, greenwool);
         	  p.getInventory().setItem(3, bluewool);
         	  p.getInventory().setItem(4, purplewool);
         	  p.getInventory().setItem(5, redwool);
         	  p.getInventory().setItem(6, orangewool);
         	  p.getInventory().setItem(7, yellowwool);
         	  p.getInventory().setItem(8, greenwool);
         	  }
         	  if(s == 1) {
             	  p.getInventory().setItem(0, redwool);
             	  p.getInventory().setItem(1, orangewool);
             	  p.getInventory().setItem(2, yellowwool);
             	  p.getInventory().setItem(3, greenwool);
             	  p.getInventory().setItem(4, bluewool);
             	  p.getInventory().setItem(5, purplewool);
             	  p.getInventory().setItem(6, redwool);
             	  p.getInventory().setItem(7, orangewool);
             	  p.getInventory().setItem(8, yellowwool);
         	  }
         	  if(s == 2) {
             	  p.getInventory().setItem(0, bluewool);
             	  p.getInventory().setItem(1, purplewool);
             	  p.getInventory().setItem(2, redwool);
             	  p.getInventory().setItem(3, orangewool);
             	  p.getInventory().setItem(4, yellowwool);
             	  p.getInventory().setItem(5, greenwool);
             	  p.getInventory().setItem(6, redwool);
             	  p.getInventory().setItem(7, orangewool);
             	  p.getInventory().setItem(8, yellowwool);
         	  }
         	  if(s == 3) {
         	  p.getInventory().setItem(0, greenwool);
         	  p.getInventory().setItem(1, bluewool);
         	  p.getInventory().setItem(2, purplewool);
         	  p.getInventory().setItem(3, redwool);
         	  p.getInventory().setItem(4, orangewool);
         	  p.getInventory().setItem(5, yellowwool);
         	  p.getInventory().setItem(6, greenwool);
         	  p.getInventory().setItem(7, redwool);
         	  p.getInventory().setItem(8, orangewool);
         	  }
         	  if(s == 4) {
             	  p.getInventory().setItem(0, purplewool);
             	  p.getInventory().setItem(1, redwool);
             	  p.getInventory().setItem(2, orangewool);
             	  p.getInventory().setItem(3, yellowwool);
             	  p.getInventory().setItem(4, greenwool);
             	  p.getInventory().setItem(5, redwool);
             	  p.getInventory().setItem(6, orangewool);
             	  p.getInventory().setItem(7, yellowwool);
             	  p.getInventory().setItem(8, greenwool);
             	 
         	  }
         	  if(s == 5) {
             	  p.getInventory().setItem(0, yellowwool);
             	  p.getInventory().setItem(1, greenwool);
             	  p.getInventory().setItem(2, bluewool);
             	  p.getInventory().setItem(3, purplewool);
             	  p.getInventory().setItem(4, redwool);
             	  p.getInventory().setItem(5, orangewool);
             	  p.getInventory().setItem(6, yellowwool);
             	  p.getInventory().setItem(7, greenwool);
             	  p.getInventory().setItem(8, redwool);
 		      }
           }
         }, 10);
         
         Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable()
         {
           public void run()
           {
         	  plugin.clearingsound(p);
         	  if(s == 0) {
             	  p.getInventory().setItem(0, purplewool);
             	  p.getInventory().setItem(1, redwool);
             	  p.getInventory().setItem(2, orangewool);
             	  p.getInventory().setItem(3, yellowwool);
             	  p.getInventory().setItem(4, greenwool);
             	  p.getInventory().setItem(5, redwool);
             	  p.getInventory().setItem(6, orangewool);
             	  p.getInventory().setItem(7, yellowwool);
             	  p.getInventory().setItem(8, greenwool);
         	  }
         	  if(s == 1) {
             	  p.getInventory().setItem(0, bluewool);
             	  p.getInventory().setItem(1, purplewool);
             	  p.getInventory().setItem(2, redwool);
             	  p.getInventory().setItem(3, orangewool);
             	  p.getInventory().setItem(4, yellowwool);
             	  p.getInventory().setItem(5, greenwool);
             	  p.getInventory().setItem(6, redwool);
             	  p.getInventory().setItem(7, orangewool);
             	  p.getInventory().setItem(8, yellowwool);
         	  }
         	  if(s == 2) {
             	  p.getInventory().setItem(0, greenwool);
             	  p.getInventory().setItem(1, bluewool);
             	  p.getInventory().setItem(2, purplewool);
             	  p.getInventory().setItem(3, redwool);
             	  p.getInventory().setItem(4, orangewool);
             	  p.getInventory().setItem(5, yellowwool);
             	  p.getInventory().setItem(6, greenwool);
             	  p.getInventory().setItem(7, redwool);
             	  p.getInventory().setItem(8, orangewool);
         	  }
         	  if(s == 3) {
             	  p.getInventory().setItem(0, yellowwool);
             	  p.getInventory().setItem(1, greenwool);
             	  p.getInventory().setItem(2, bluewool);
             	  p.getInventory().setItem(3, purplewool);
             	  p.getInventory().setItem(4, redwool);
             	  p.getInventory().setItem(5, orangewool);
             	  p.getInventory().setItem(6, yellowwool);
             	  p.getInventory().setItem(7, greenwool);
             	  p.getInventory().setItem(8, redwool);
         	  }
         	  if(s == 4) {
             	  p.getInventory().setItem(0, orangewool);
             	  p.getInventory().setItem(1, yellowwool);
             	  p.getInventory().setItem(2, greenwool);
             	  p.getInventory().setItem(3, bluewool);
             	  p.getInventory().setItem(4, purplewool);
             	  p.getInventory().setItem(5, redwool);
             	  p.getInventory().setItem(6, orangewool);
             	  p.getInventory().setItem(7, yellowwool);
             	  p.getInventory().setItem(8, greenwool);
         	  }
         	  if(s == 5) {
             	  p.getInventory().setItem(0, redwool);
             	  p.getInventory().setItem(1, orangewool);
             	  p.getInventory().setItem(2, yellowwool);
             	  p.getInventory().setItem(3, greenwool);
             	  p.getInventory().setItem(4, bluewool);
             	  p.getInventory().setItem(5, purplewool);
             	  p.getInventory().setItem(6, redwool);
             	  p.getInventory().setItem(7, orangewool);
             	  p.getInventory().setItem(8, yellowwool);
 		      }
           }
         }, 20);
         
         Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable()
         {
           public void run()
           {
         	  plugin.cleardone(p);
         	  Particlez.colorParticle(p);
           }
         }, 35L);
	  }
	  
	  //_______________________________________________________ ANIMATION 1
	  public static void animation1(CommandSender sender) {
		    final Player p = (Player)sender;
          
          final ItemStack air = new ItemStack(Material.AIR);
          ItemMeta airmeta = air.getItemMeta();
          air.setItemMeta(airmeta);
          
          final ItemStack glass = new ItemStack(Material.WHITE_STAINED_GLASS_PANE);
          ItemMeta glassmeta = glass.getItemMeta();
          glassmeta.setDisplayName("§a§e");
          glass.setItemMeta(glassmeta);
          
		  final ItemStack token = new ItemStack(Material.NAME_TAG);
		    ItemMeta tokenm = token.getItemMeta();
		    tokenm.setDisplayName(ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("features.clearing.token")));
		    token.setItemMeta(tokenm);
     	  p.getInventory().setItem(22, token);
          
          Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable()
          {
            public void run()
            {
           	  p.getInventory().setItem(0, glass);
           	  if(plugin.getConfig().getBoolean("features.clearing.slot-switching")) {
         	  p.getPlayer().getInventory().setHeldItemSlot(1);
           	  }
            }
          }, 0L);
          
        Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable()
    {
          public void run()
          {
         	  p.getInventory().setItem(1, glass);
         	  if(plugin.getConfig().getBoolean("features.clearing.slot-switching")) {
        	  p.getPlayer().getInventory().setHeldItemSlot(2);
         	  }
        	  plugin.clearingsound(p);
          }
        }, 1L);
        
        Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable()
    {
          public void run()
          {
         	  p.getInventory().setItem(2, glass);
         	  if(plugin.getConfig().getBoolean("features.clearing.slot-switching")) {
        	  p.getPlayer().getInventory().setHeldItemSlot(3);
         	  }
          }
        }, 2L);
        
        Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable()
    {
          public void run()
          {
         	  p.getInventory().setItem(3, glass);
         	  if(plugin.getConfig().getBoolean("features.clearing.slot-switching")) {
        	  p.getPlayer().getInventory().setHeldItemSlot(4);
         	  }
          }
        }, 3L);
        
        Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable()
    {
          public void run()
          {
         	  p.getInventory().setItem(4, glass);
         	  if(plugin.getConfig().getBoolean("features.clearing.slot-switching")) {
        	  p.getPlayer().getInventory().setHeldItemSlot(5);
         	  }
        	  plugin.clearingsound(p);
          }
        }, 4L);
        
        Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable()
    {
          public void run()
          {
         	  p.getInventory().setItem(5, glass);
         	  if(plugin.getConfig().getBoolean("features.clearing.slot-switching")) {
        	  p.getPlayer().getInventory().setHeldItemSlot(6);
         	  }
          }
        }, 5L);
        
        Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable()
    {
          public void run()
          {
         	  p.getInventory().setItem(6, glass);
         	  if(plugin.getConfig().getBoolean("features.clearing.slot-switching")) {
        	  p.getPlayer().getInventory().setHeldItemSlot(7);
         	  }
          }
        }, 6L);
        
        Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable()
    {
          public void run()
          {
         	  p.getInventory().setItem(7, glass);
         	  if(plugin.getConfig().getBoolean("features.clearing.slot-switching")) {
        	  p.getPlayer().getInventory().setHeldItemSlot(8);
         	  }
        	  plugin.clearingsound(p);
          }
        }, 7L);
        
        Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable()
    {
          public void run()
          {
         	  p.getInventory().setItem(8, glass);
         	  if(plugin.getConfig().getBoolean("features.clearing.slot-switching")) {
        	  p.getPlayer().getInventory().setHeldItemSlot(8);
         	  }
          }
        }, 8L);
        
        
        Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable()
    {
          public void run()
          {
        	  p.getInventory().setItem(8, air);
        	  if(plugin.getConfig().getBoolean("features.clearing.slot-switching")) {
        	  p.getPlayer().getInventory().setHeldItemSlot(7);
        	  }
        	  plugin.clearingsound(p);
          }
        }, 9L);
        
        Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable()
    {
          public void run()
          {
        	  p.getInventory().setItem(7, air);
        	  if(plugin.getConfig().getBoolean("features.clearing.slot-switching")) {
        	  p.getPlayer().getInventory().setHeldItemSlot(6);
        	  }
          }
        }, 10L);
        
        Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable()
    {
          public void run()
          {
        	  p.getInventory().setItem(6, air);
        	  if(plugin.getConfig().getBoolean("features.clearing.slot-switching")) {
        	  p.getPlayer().getInventory().setHeldItemSlot(5);
        	  }
          }
        }, 11L);
        
        Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable()
    {
          public void run()
          {
        	  p.getInventory().setItem(5, air);
        	  if(plugin.getConfig().getBoolean("features.clearing.slot-switching")) {
        	  p.getPlayer().getInventory().setHeldItemSlot(4);
        	  }
          }
        }, 12L);
        
        Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable()
    {
          public void run()
          {
        	  p.getInventory().setItem(4, air);
          }
        }, 13L);
        
        Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable()
    {
          public void run()
          {
        	  p.getInventory().setItem(3, air);
          }
        }, 14L);
        
        Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable()
    {
          public void run()
          {
        	  p.getInventory().setItem(2, air);
          }
        }, 15L);
        
        Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable()
    {
          public void run()
          {
        	  p.getInventory().setItem(1, air); 	  
          }
        }, 16L);
    Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable()
    {
      public void run()
      {	  
  	  p.getInventory().setItem(0, air);
  	  Particlez.cloudParticle(p);
      	plugin.cleardone(p);  
      }
    }, 17L);
}
	  
	  //_______________________________________________________ ANIMATION 4
	  public static void animation4(CommandSender sender) {
		    final Player p = (Player)sender;
          
          final ItemStack air = new ItemStack(Material.AIR);
          ItemMeta airmeta = air.getItemMeta();
          air.setItemMeta(airmeta);
          
          final ItemStack bg = new ItemStack(Material.BLUE_STAINED_GLASS_PANE);
          ItemMeta bgm = bg.getItemMeta();
          bgm.setDisplayName("§a§e");
          bg.setItemMeta(bgm);
          
          final ItemStack cg = new ItemStack(Material.CYAN_STAINED_GLASS_PANE);
          ItemMeta cgm = cg.getItemMeta();
          cgm.setDisplayName("§a§e");
          cg.setItemMeta(cgm);
          
          final ItemStack lbg = new ItemStack(Material.LIGHT_BLUE_STAINED_GLASS_PANE);
          ItemMeta lbgm = lbg.getItemMeta();
          lbgm.setDisplayName("§a§e");
          lbg.setItemMeta(lbgm);
          
          final ItemStack wb = new ItemStack(Material.WATER_BUCKET);
          ItemMeta wbm = wb.getItemMeta();
          wbm.setDisplayName("§a§e");
          wb.setItemMeta(wbm);
          
          final ItemStack b = new ItemStack(Material.BUCKET);
          ItemMeta bm = b.getItemMeta();
          bm.setDisplayName("§a§e");
          b.setItemMeta(bm);
          
		  final ItemStack token = new ItemStack(Material.NAME_TAG);
		    ItemMeta tokenm = token.getItemMeta();
		    tokenm.setDisplayName(ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("features.clearing.token")));
		    token.setItemMeta(tokenm);
     	  p.getInventory().setItem(22, token);
          
          Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable()
          {
            public void run()
            {
           	  p.getInventory().setItem(4, b);
           	 if(plugin.getConfig().getBoolean("features.clearing.slot-switching")) {
         	  p.getPlayer().getInventory().setHeldItemSlot(4);
           	 }
         	  p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_GOLD, 5.0f, 0.1f);
         	  p.playSound(p.getLocation(), Sound.ENTITY_CHICKEN_EGG, 5.0f, 0.1f);
            }
          }, 0L);
          
        Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable()
    {
          public void run()
          {
           	  p.getInventory().setItem(4, wb);
         	  p.playSound(p.getLocation(), Sound.ITEM_BUCKET_FILL, 5.0f, 1.4f);
          }
        }, 20L);
        
        Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable()
    {
          public void run()
          {
           	  p.getInventory().setItem(3, lbg);
           	  p.getInventory().setItem(5, lbg);
         	  p.playSound(p.getLocation(), Sound.BLOCK_WATER_AMBIENT, 5.0f, 1.0f);
          }
        }, 24L);
        
        Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable()
    {
          public void run()
          {
           	  p.getInventory().setItem(2, lbg);
           	  p.getInventory().setItem(6, lbg);
          }
        }, 26L);
        
        Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable()
    {
          public void run()
          {
           	  p.getInventory().setItem(1, lbg);
           	  p.getInventory().setItem(7, lbg);
          }
        }, 28L);
        
        Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable()
    {
          public void run()
          {
           	  p.getInventory().setItem(0, lbg);
           	  p.getInventory().setItem(8, lbg);
        	  p.playSound(p.getLocation(), Sound.BLOCK_WATER_AMBIENT, 5.0f, 1.0f);
          }
        }, 30L);
        
        Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable()
    {
          public void run()
          {
        	  p.getInventory().setItem(3, cg);
        	  p.getInventory().setItem(5, cg);
          }
        }, 32L);
        
        Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable()
    {
          public void run()
          {
        	  p.getInventory().setItem(2, cg);
        	  p.getInventory().setItem(6, cg);
          }
        }, 34L);
        
        Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable()
    {
          public void run()
          {
        	  p.getInventory().setItem(1, cg);
        	  p.getInventory().setItem(7, cg);
          }
        }, 36L);
        
        Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable()
    {
          public void run()
          {
        	  p.getInventory().setItem(0, cg);
        	  p.getInventory().setItem(8, cg);
        	  p.playSound(p.getLocation(), Sound.BLOCK_WATER_AMBIENT, 5.0f, 1.0f);
          }
        }, 38L);
        
        Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable()
    {
          public void run()
          {
        	  p.getInventory().setItem(3, bg);
        	  p.getInventory().setItem(5, bg);
        	  Msgs.sendBar(p.getPlayer(), plugin.getConfig().getString("features.clearing.progress-msg"));
          }
        }, 40L);
        
        Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable()
    {
          public void run()
          {
        	  p.getInventory().setItem(2, bg);
        	  p.getInventory().setItem(6, bg);
          }
        }, 42L);
        
        Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable()
    {
          public void run()
          {
        	  p.getInventory().setItem(1, bg);
        	  p.getInventory().setItem(7, bg);
          }
        }, 44L);
        
        Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable()
    {
          public void run()
          {
        	  p.getInventory().setItem(0, bg);
        	  p.getInventory().setItem(8, bg);
          }
        }, 46L);
        
        Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable()
    {
          public void run()
          {
        	  p.getInventory().setItem(4, b);
        	  p.playSound(p.getLocation(), Sound.BLOCK_WET_GRASS_PLACE, 5.0f, 0.4f);  
          }
        }, 55L);
        
        Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable()
    {
          public void run()
          {
        	  Msgs.sendBar(p.getPlayer(), plugin.getConfig().getString("features.clearing.progress-msg"));
        	  p.getInventory().setItem(3, cg);
        	  p.getInventory().setItem(5, cg); 
          }
        }, 60L);
        
        Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable()
    {
          public void run()
          {
        	  p.getInventory().setItem(2, cg);
        	  p.getInventory().setItem(6, cg);  
          }
        }, 62L);
        
        Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable()
    {
          public void run()
          {
        	  p.getInventory().setItem(1, cg);
        	  p.getInventory().setItem(7, cg);  
          }
        }, 64L);
        
        Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable()
    {
          public void run()
          {
        	  p.getInventory().setItem(1, cg);
        	  p.getInventory().setItem(7, cg);  
          }
        }, 66L);
        
        Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable()
    {
          public void run()
          {
        	  p.getInventory().setItem(0, cg);
        	  p.getInventory().setItem(8, cg);  
          }
        }, 68L);
        
        Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable()
    {
          public void run()
          {
        	  p.getInventory().setItem(3, lbg);
        	  p.getInventory().setItem(5, lbg);  
          }
        }, 70L);
        
        Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable()
    {
          public void run()
          {
        	  p.getInventory().setItem(2, lbg);
        	  p.getInventory().setItem(6, lbg);  
          }
        }, 72L);
        
        Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable()
        {
              public void run()
              {
            	  p.getInventory().setItem(1, lbg);
            	  p.getInventory().setItem(7, lbg);  
              }
            }, 74L);
        
        Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable()
        {
              public void run()
              {
            	  p.getInventory().setItem(0, lbg);
            	  p.getInventory().setItem(8, lbg);  
              }
            }, 76L);
        
        Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable()
        {
              public void run()
              {
            	  p.getInventory().setItem(3, air);
            	  p.getInventory().setItem(5, air);  
              }
            }, 77L);
        
        Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable()
        {
              public void run()
              {
            	  p.getInventory().setItem(2, air);
            	  p.getInventory().setItem(6, air);  
              }
            }, 78L);
        
        Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable()
        {
              public void run()
              {
            	  p.getInventory().setItem(1, air);
            	  p.getInventory().setItem(7, air);  
              }
            }, 79L);
        
        Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable()
        {
              public void run()
              {
            	  p.getInventory().setItem(0, air);
            	  p.getInventory().setItem(8, air);  
              }
            }, 80L);
        
        
    Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable()
    {
      public void run()
      {	  
  	  Particlez.waterParticle(p);
      	plugin.cleardone(p);  
      }
    }, 85L);
}

	  public static void animation3(CommandSender sender) {
		    final Player p = (Player)sender;
		    
	          final ItemStack gsg = new ItemStack(Material.GUNPOWDER);
	          ItemMeta gsgm = gsg.getItemMeta();
	          gsgm.setDisplayName("§a§e");
	          gsg.setItemMeta(gsgm);
	          
        final ItemStack tntblock = new ItemStack(Material.TNT);
        ItemMeta tntblockm = tntblock.getItemMeta();
        tntblockm.setDisplayName("§a§e");
        tntblock.setItemMeta(tntblockm);
        
        final ItemStack air = new ItemStack(Material.AIR);
        ItemMeta airmeta = air.getItemMeta();
        air.setItemMeta(airmeta);
        
            final ItemStack tnt = new ItemStack(Material.TNT_MINECART);
            ItemMeta tntm = tnt.getItemMeta();
            tntm.setDisplayName("§a§e");
            tnt.setItemMeta(tntm);
            
            final ItemStack glass = new ItemStack(Material.ORANGE_STAINED_GLASS_PANE);
            ItemMeta glassm = glass.getItemMeta();
            glassm.setDisplayName("§a§e");
            glass.setItemMeta(glassm);
            
			  final ItemStack token = new ItemStack(Material.NAME_TAG);
			    ItemMeta tokenm = token.getItemMeta();
			    tokenm.setDisplayName(ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("features.clearing.token")));
			    token.setItemMeta(tokenm);
	       	  p.getInventory().setItem(22, token);
	       	  
        p.getInventory().setItem(0, tnt);
        
      Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable()
      {
        public void run()
        {
          p.getInventory().setItem(1, tnt);
          p.getInventory().setItem(0, gsg);
      	  plugin.tntmovesound(p);
        }
      }, 4L);
      
      Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable()
      {
        public void run()
        {
                p.getInventory().setItem(2, tnt);
                p.getInventory().setItem(1, gsg);
        }
      }, 8L);
      
      Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable()
      {
        public void run()
        {
          p.getInventory().setItem(3, tnt);
          p.getInventory().setItem(2, gsg);
          p.getInventory().setItem(0, air);
        }
      }, 12L);
      
      Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable()
      {
        public void run()
        {         	  
          p.getInventory().setItem(4, tnt);
          p.getInventory().setItem(3, gsg);
          p.getInventory().setItem(1, air);
        }
      }, 16L);
      
      Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable()
      {
        public void run()
        {         	  
          p.getInventory().setItem(2, air);
        }
      }, 20L);
      
      Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable()
      {
        public void run()
        {
        	 p.getInventory().setItem(3, air);
      	  p.getInventory().setItem(4, tntblock);
      	  plugin.tntmovesoundstop(p);
      	  plugin.tntplacesound(p);
      	  Msgs.sendBar(p.getPlayer(), plugin.getConfig().getString("features.clearing.progress-msg"));
        }
      }, 25L);
      
      Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable()
      {
        public void run()
        {
      	  p.getInventory().setItem(3, glass);
      	  p.getInventory().setItem(5, glass);
      	  plugin.boomsound(p);
      	  Particlez.explosionParticle(p);
        }
      }, 35L);
      
      Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable()
      {
        public void run()
        {
      	  p.getInventory().setItem(2, glass);
      	  p.getInventory().setItem(6, glass);
        }
      }, 36L);
      
      
      Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable()
      {
        public void run()
        {
      	  p.getInventory().setItem(1, glass);
      	  p.getInventory().setItem(7, glass); 
        }
      }, 37L);
      
      Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable()
      {
        public void run()
        {
      	  p.getInventory().setItem(0, glass);
      	  p.getInventory().setItem(8, glass);
        }
      }, 38L);
      
  Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable()
  {
    public void run()
    {	  
    	plugin.cleardone(p);  
    }
  }, 45L);
}
	  
	  public static void animation5(CommandSender sender) {
		    final Player p = (Player)sender;

      final ItemStack dblock = new ItemStack(Material.DISPENSER);
      ItemMeta dblockm = dblock.getItemMeta();
      dblockm.setDisplayName("§a§e");
      dblock.setItemMeta(dblockm);
      
      final ItemStack fc = new ItemStack(Material.FIRE_CHARGE);
      ItemMeta fcm = dblock.getItemMeta();
      fcm.setDisplayName("§a§e");
      fc.setItemMeta(fcm);
      
      final ItemStack air = new ItemStack(Material.AIR);
      ItemMeta airmeta = air.getItemMeta();
      air.setItemMeta(airmeta);
      
	  final ItemStack token = new ItemStack(Material.NAME_TAG);
	    ItemMeta tokenm = token.getItemMeta();
	    tokenm.setDisplayName(ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("features.clearing.token")));
	    token.setItemMeta(tokenm);
 	  p.getInventory().setItem(22, token);
 	  
      p.getInventory().setItem(0, dblock);
      plugin.despsound(p);
      
      Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable()
      {
        public void run()
        {
            plugin.fireballshootsound(p);
            p.getInventory().setItem(1, fc);
            Particlez.fireballParticle(p);
        }
      }, 15L);
      
      Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable()
      {
        public void run()
        {
        	p.getInventory().setItem(1, air);
            p.getInventory().setItem(2, fc);
            Particlez.fireballParticle(p);
        }
      }, 17L);
      
      Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable()
      {
        public void run()
        {
        	p.getInventory().setItem(2, air);
            p.getInventory().setItem(3, fc);
            Particlez.fireballParticle(p);
        }
      }, 19L);
      
      
      Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable()
      {
        public void run()
        {
        	p.getInventory().setItem(3, air);
            p.getInventory().setItem(4, fc);
            Particlez.fireballParticle(p);
        }
      }, 21L);
      
      Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable()
      {
        public void run()
        {
        	p.getInventory().setItem(4, air);
            p.getInventory().setItem(5, fc);
            Particlez.fireballParticle(p);
        }
      }, 23L);
      
      Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable()
      {
        public void run()
        {
        	p.getInventory().setItem(5, air);
            p.getInventory().setItem(6, fc);
            Particlez.fireballParticle(p);
            Msgs.sendBar(p.getPlayer(), plugin.getConfig().getString("features.clearing.progress-msg"));
        }
      }, 25L);
      
      Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable()
      {
        public void run()
        {
        	p.getInventory().setItem(6, air);
            p.getInventory().setItem(7, fc);
            Particlez.fireballParticle(p);
        }
      }, 27L);
      
      Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable()
      {
        public void run()
        {
        	p.getInventory().setItem(7, air);
            p.getInventory().setItem(8, fc);
            Particlez.fireballParticle(p);
        }
      }, 29L);
      
      
      Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable()
      {
        public void run()
        {
        	p.getInventory().setItem(8, air);
        }
      }, 31L);
      
    
Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable()
{
  public void run()
  {	  
  	plugin.cleardone(p);  
  }
}, 36L);
}
	  
}
