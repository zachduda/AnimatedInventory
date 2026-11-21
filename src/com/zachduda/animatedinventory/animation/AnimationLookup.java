package com.zachduda.animatedinventory.animation;

import org.bukkit.Material;
import org.bukkit.Particle;

/**
 * Turns the names in an animation file into things the server can use. Every method returns
 * null for a name this server doesn't have. {@link BukkitLookup} is the real one; tests use
 * their own, since most of these need a running server.
 */
public interface AnimationLookup {

    /** A material that can be held as an item. Never air. */
    Material item(String name);

    /** A sound at volume 1 and pitch 1. */
    AnimationSound sound(String name);

    Particle particle(String name);

    /** Block data for particles like BLOCK and FALLING_DUST. */
    Object blockData(String name);

    /** An item stack for the ITEM particle. */
    Object itemData(String name);
}
