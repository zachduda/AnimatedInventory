package com.zach_attack.inventory;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

import org.bukkit.Particle;
import org.bukkit.World;

public class Particlez implements Listener{
	static Main plugin = Main.getPlugin(Main.class);
	
	  @SuppressWarnings("unused")
	public static void explosionParticle(Player p)
	  {
		  if(plugin.getConfig().getBoolean("features.use-particles")) {
			  try {
		    for (Player online : Bukkit.getServer().getOnlinePlayers())
		    {
		      World world = p.getLocation().getWorld();
		      world.spawnParticle(Particle.EXPLOSION_LARGE, p.getLocation().add(0, 1, 0), 3, 0.4D, 0.2D, 0.4D);
		    }
			  }catch(Exception e) { plugin.getLogger().info("ERROR! Couldn't display EXPLOSION_LARGE particle.");}
		  }
	  }
	  @SuppressWarnings("unused")
	public static void colorParticle(Player p)
	  {
		  if(plugin.getConfig().getBoolean("features.use-particles")) {
			  try {
		    for (Player online : Bukkit.getServer().getOnlinePlayers())
		    {
		      World world = p.getLocation().getWorld();
		      world.spawnParticle(Particle.SPELL_MOB, p.getLocation().add(0, 1, 0), 50, 0.2D, 0.2D, 0.2D);
		    }
			  }catch(Exception e) { plugin.getLogger().info("ERROR! Couldn't display SPELL_MOB particle.");}
		  }
	  }
	  
	  @SuppressWarnings("unused")
	public static void fireballParticle(Player p)
	  {
		  if(plugin.getConfig().getBoolean("features.use-particles")) {
			  try {
		    for (Player online : Bukkit.getServer().getOnlinePlayers())
		    {
		      World world = p.getLocation().getWorld();
		      world.spawnParticle(Particle.FLAME, p.getLocation().add(0, 1, 0), 5, 0.4D, 0.2D, 0.4D, 0.1D);
		    }
			  }catch(Exception e) { plugin.getLogger().info("ERROR! Couldn't display FLAME particle.");}
		  }
	  }
	  
	  @SuppressWarnings("unused")
	public static void cloudParticle(Player p)
	  {
		  if(plugin.getConfig().getBoolean("features.use-particles")) {
			  try {
		    for (Player online : Bukkit.getServer().getOnlinePlayers())
		    {
		      World world = p.getLocation().getWorld();
		      world.spawnParticle(Particle.SPELL, p.getLocation().add(0, 1, 0), 50, 0.2D, 0.2D, 0.2D);
		    }
			  }catch(Exception e) { plugin.getLogger().info("ERROR! Couldn't display SPELL particle.");}
		  }
	  }
	  
	  @SuppressWarnings("unused")
	public static void waterParticle(Player p)
	  {
		  if(plugin.getConfig().getBoolean("features.use-particles")) {
			  try {
		    for (Player online : Bukkit.getServer().getOnlinePlayers())
		    {
		      World world = p.getLocation().getWorld();
		      world.spawnParticle(Particle.DRIP_WATER, p.getLocation().add(0, 1, 0), 20, 0.2D, 0.2D, 0.2D);
		    }
			  }catch(Exception e) { plugin.getLogger().info("ERROR! Couldn't display DRIP_WATER particle.");}
		  }
	  }
	  
	public static void yesParticle(Player p)
	  {
		  if(plugin.getConfig().getBoolean("features.use-particles")) {
			  try {
		      p.spawnParticle(Particle.VILLAGER_HAPPY, p.getLocation().add(0, 1, 0), 50, 0.5D, 0.3D, 0.5D);
	  }catch(Exception e) { plugin.getLogger().info("ERROR! Couldn't display VILLAGER_HAPPY particle.");}
  }
	  }
	  
	public static void noParticle(Player p)
	  {
		  if(plugin.getConfig().getBoolean("features.use-particles")) {
			  try {
		      p.spawnParticle(Particle.BARRIER, p.getLocation().add(0, 1, 0), 5, 0.5D, 0.3D, 0.5D);
        }catch(Exception e) { plugin.getLogger().info("ERROR! Couldn't display BARRIER particle.");}
        }
   }
}
