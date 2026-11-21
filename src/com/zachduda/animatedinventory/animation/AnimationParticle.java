package com.zachduda.animatedinventory.animation;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

/**
 * Particles a frame shows around the player. Everyone nearby can see them.
 *
 * @param name   the name as written in the file, for messages
 * @param height blocks above the player's feet
 * @param data   the extra data the particle needs (dust options, a color, block data, an item,
 *               a spell or a dust transition), settings to work it out from where the particle
 *               appears (a trail), or null when it needs none
 */
public record AnimationParticle(String name, Particle particle, int count, double offsetX, double offsetY,
                                double offsetZ, double speed, double height, Object data) {

    public void spawn(Player player) {
        final Location at = player.getLocation().add(0, height, 0);
        final Object settings = data instanceof RelativeData relative ? relative.at(at) : data;
        if (settings == null) {
            player.getWorld().spawnParticle(particle, at, count, offsetX, offsetY, offsetZ, speed);
        } else {
            player.getWorld().spawnParticle(particle, at, count, offsetX, offsetY, offsetZ, speed, settings);
        }
    }

    @Override
    public String toString() {
        return "the " + name + " particle";
    }
}
