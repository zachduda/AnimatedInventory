package com.zachduda.animatedinventory.animation;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

/**
 * Particles a frame shows around the player. Everyone nearby can see them.
 *
 * @param name   the name as written in the file, for messages
 * @param height blocks above the player's feet
 * @param data   the extra data the particle needs (dust options, a color, block data or an
 *               item), or null when it needs none
 */
public record AnimationParticle(String name, Particle particle, int count, double offsetX, double offsetY,
                                double offsetZ, double speed, double height, Object data) {

    public void spawn(Player player) {
        final Location at = player.getLocation().add(0, height, 0);
        if (data == null) {
            player.getWorld().spawnParticle(particle, at, count, offsetX, offsetY, offsetZ, speed);
        } else {
            player.getWorld().spawnParticle(particle, at, count, offsetX, offsetY, offsetZ, speed, data);
        }
    }

    @Override
    public String toString() {
        return "the " + name + " particle";
    }
}
