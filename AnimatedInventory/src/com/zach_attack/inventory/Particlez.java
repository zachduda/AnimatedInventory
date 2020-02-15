package com.zach_attack.inventory;

import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

import org.bukkit.Particle;
import org.bukkit.World;

public class Particlez implements Listener {
    private static Main plugin = Main.getPlugin(Main.class);

    private static void pErr(String name) {
    	plugin.getLogger().info("Error! Unable to display the " + name + " particle. Disabling particles...");
    }
    
    static void explosionParticle(Player p) {
        if (plugin.getConfig().getBoolean("features.use-particles")) {
            try {
                World world = p.getLocation().getWorld();
                world.spawnParticle(Particle.EXPLOSION_LARGE, p.getLocation().add(0, 1, 0), 3, 0.4D, 0.2D, 0.4D);
            } catch (Exception e) {
                pErr("EXPLOSION_LARGE");
            }
        }
    }

    static void colorParticle(Player p) {
        if (plugin.getConfig().getBoolean("features.use-particles")) {
            try {
                World world = p.getLocation().getWorld();
                world.spawnParticle(Particle.SPELL_MOB, p.getLocation().add(0, 1, 0), 50, 0.2D, 0.2D, 0.2D);
            } catch (Exception e) {
            	pErr("SPELL_MOB");
            }
        }
    }

    static void fireballParticle(Player p) {
        if (plugin.getConfig().getBoolean("features.use-particles")) {
            try {
                World world = p.getLocation().getWorld();
                world.spawnParticle(Particle.FLAME, p.getLocation().add(0, 1, 0), 3, 0.4D, 0.2D, 0.4D, 0.1D);
            } catch (Exception e) {
            	pErr("FLAME");
            }
        }
    }

    static void spellParticle(Player p) {
        if (plugin.getConfig().getBoolean("features.use-particles")) {
            try {
                World world = p.getLocation().getWorld();
                world.spawnParticle(Particle.SPELL, p.getLocation().add(0, 1, 0), 50, 0.2D, 0.2D, 0.2D);
            } catch (Exception e) {
            	pErr("SPELL");
            }
        }
    }

    static void waterParticle(Player p) {
        if (plugin.getConfig().getBoolean("features.use-particles")) {
            try {
                World world = p.getLocation().getWorld();
                world.spawnParticle(Particle.DRIP_WATER, p.getLocation().add(0, 1, 0), 20, 0.2D, 0.2D, 0.2D);
            } catch (Exception e) {
            	pErr("DRIP_WATER");
            }
        }
    }

    static void yesParticle(Player p) {
        if (plugin.getConfig().getBoolean("features.use-particles")) {
            try {
                p.spawnParticle(Particle.VILLAGER_HAPPY, p.getLocation().add(0, 1, 0), 50, 0.5D, 0.3D, 0.5D);
            } catch (Exception e) {
            	pErr("VILLAGER_HAPPY");
            }
        }
    }

    static void noParticle(Player p) {
        if (plugin.getConfig().getBoolean("features.use-particles")) {
            try {
                p.spawnParticle(Particle.BARRIER, p.getLocation().add(0, 1, 0), 5, 0.5D, 0.3D, 0.5D);
            } catch (Exception e) {
            	pErr("BARRIER");
            }
        }
    }
}