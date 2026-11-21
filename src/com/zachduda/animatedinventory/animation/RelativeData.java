package com.zachduda.animatedinventory.animation;

import org.bukkit.Location;

/** Particle settings that depend on where the particle appears, like the end point of a trail. */
interface RelativeData {

    /** The settings for a particle appearing at {@code where}. Doesn't change {@code where}. */
    Object at(Location where);
}
