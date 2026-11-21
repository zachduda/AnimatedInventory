package com.zachduda.animatedinventory;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

import com.zachduda.animatedinventory.animation.BukkitLookup;

/** The fortune's particles. Clear animations bring their own, from the Animations folder. */
public class Particlez implements Listener {
    private static final Main plugin = Main.getPlugin(Main.class);

    /**
     * How the "no" result is drawn.
     *
     * Particle.BARRIER was removed in 1.18 and replaced by BLOCK_MARKER carrying
     * barrier block data. The old code picked between the two on isSupported(),
     * which is true for every version from 1.13 up, so on any modern server it
     * always took the BARRIER branch, threw, and logged an error on every single
     * "no" fortune. Resolve it once here instead of guessing per call.
     */
    private static final Particle NO_PARTICLE;
    private static final Object NO_PARTICLE_DATA;

    /**
     * Looked up by name rather than written as Particle.HAPPY_VILLAGER: that constant was
     * VILLAGER_HAPPY before 1.20.5, so on those servers referencing it threw NoSuchFieldError
     * and ended every "yes" fortune with an error. resolveParticle knows both names.
     */
    private static final Particle YES_PARTICLE = BukkitLookup.resolveParticle("HAPPY_VILLAGER");

    static {
        Particle particle = BukkitLookup.resolveParticle("BLOCK_MARKER");
        Object data = null;
        if (particle != null) {
            try {
                data = Bukkit.createBlockData(Material.BARRIER);
            } catch (RuntimeException | LinkageError unavailable) {
                particle = null;
            }
        }
        if (particle == null) {
            particle = BukkitLookup.resolveParticle("BARRIER");
        }
        NO_PARTICLE = particle;
        NO_PARTICLE_DATA = data;
    }

    /** Flipped once a particle fails, so a bad version logs once, not per frame. */
    private static boolean broken = false;

    private static void pErr(String name) {
        broken = true;
        plugin.getLogger().warning("Error! Unable to display the " + name + " particle. Disabling particles...");
    }

    private static boolean enabled() {
        return Settings.useParticles && !broken;
    }

    private static void spawn(Player p, Particle particle, String name, int count,
                              double ox, double oy, double oz) {
        if (!enabled() || particle == null) {
            return;
        }
        try {
            final World world = p.getWorld();
            world.spawnParticle(particle, p.getLocation().add(0, 1, 0), count, ox, oy, oz);
        } catch (Exception | LinkageError e) {
            pErr(name);
        }
    }

    static void yesParticle(Player p) {
        spawn(p, YES_PARTICLE, "HAPPY_VILLAGER", 50, 0.5D, 0.3D, 0.5D);
    }

    static void noParticle(Player p) {
        if (!enabled() || NO_PARTICLE == null) {
            return;
        }
        try {
            if (NO_PARTICLE_DATA != null) {
                p.spawnParticle(NO_PARTICLE, p.getLocation().add(0, 1, 0), 5, 0.5D, 0.3D, 0.5D, NO_PARTICLE_DATA);
            } else {
                p.spawnParticle(NO_PARTICLE, p.getLocation().add(0, 1, 0), 5, 0.5D, 0.3D, 0.5D);
            }
        } catch (Exception | LinkageError e) {
            pErr("BARRIER");
        }
    }
}
