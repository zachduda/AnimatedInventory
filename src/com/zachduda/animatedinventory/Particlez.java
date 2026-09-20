package com.zachduda.animatedinventory;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

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

    static {
        Particle particle = null;
        Object data = null;
        try {
            particle = Particle.valueOf("BLOCK_MARKER");
            data = Bukkit.createBlockData(Material.BARRIER);
        } catch (Throwable modernUnavailable) {
            try {
                particle = Particle.valueOf("BARRIER");
            } catch (Throwable legacyUnavailable) {
                particle = null;
            }
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
        if (!enabled()) {
            return;
        }
        try {
            final World world = p.getWorld();
            world.spawnParticle(particle, p.getLocation().add(0, 1, 0), count, ox, oy, oz);
        } catch (Exception e) {
            pErr(name);
        }
    }

    static void explosionParticle(Player p) {
        spawn(p, Particle.EXPLOSION, "EXPLOSION", 3, 0.4D, 0.2D, 0.4D);
    }

    static void colorParticle(Player p) {
        spawn(p, Particle.WITCH, "WITCH", 50, 0.2D, 0.2D, 0.2D);
    }

    static void waterParticle(Player p) {
        spawn(p, Particle.DRIPPING_WATER, "DRIPPING_WATER", 20, 0.2D, 0.2D, 0.2D);
    }

    static void yesParticle(Player p) {
        spawn(p, Particle.HAPPY_VILLAGER, "HAPPY_VILLAGER", 50, 0.5D, 0.3D, 0.5D);
    }

    static void fireballParticle(Player p) {
        if (!enabled()) {
            return;
        }
        try {
            p.getWorld().spawnParticle(Particle.FLAME, p.getLocation().add(0, 1, 0), 3, 0.4D, 0.2D, 0.4D, 0.1D);
        } catch (Exception e) {
            pErr("FLAME");
        }
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
        } catch (Exception e) {
            pErr("BARRIER");
        }
    }
}
