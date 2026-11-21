package com.zachduda.animatedinventory.animation;

import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * A sound a frame plays or stops. It's heard by the player only.
 *
 * @param name  the name as written in the file, for messages
 * @param sound the server's sound, or null for a resource pack sound the server doesn't know
 * @param key   the namespaced key of a resource pack sound; null whenever {@code sound} is set
 */
public record AnimationSound(String name, Sound sound, String key, float volume, float pitch) {

    public AnimationSound withVolumeAndPitch(float volume, float pitch) {
        return new AnimationSound(name, sound, key, volume, pitch);
    }

    public void play(Player player) {
        if (sound != null) {
            player.playSound(player.getLocation(), sound, volume, pitch);
        } else {
            player.playSound(player.getLocation(), key, volume, pitch);
        }
    }

    public void stop(Player player) {
        if (sound != null) {
            player.stopSound(sound);
        } else {
            player.stopSound(key);
        }
    }

    @Override
    public String toString() {
        return "the " + name + " sound";
    }
}
