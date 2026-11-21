package com.zachduda.animatedinventory.animation;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import org.bukkit.Color;
import org.bukkit.Location;

/**
 * Builds the settings of the particles that only newer servers have: EFFECT and INSTANT_EFFECT
 * (Particle.Spell), DUST_COLOR_TRANSITION (Particle.DustTransition) and TRAIL (Particle.Trail).
 *
 * The plugin runs on servers back to 1.13, where some of these classes don't exist, and TRAIL's
 * constructor has changed shape between versions. Writing Particle.Spell.class anywhere would
 * throw on those servers the moment it ran, so the types are recognized by name and built
 * through their constructors instead. A server whose version is missing one gets a message
 * from the parser, not a crash.
 */
final class ParticleData {

    static final String SPELL = "org.bukkit.Particle$Spell";
    static final String DUST_TRANSITION = "org.bukkit.Particle$DustTransition";
    static final String TRAIL = "org.bukkit.Particle$Trail";

    private ParticleData() {
    }

    /** The type's public constructor taking these, or null when this server's version has none. */
    static Constructor<?> constructor(Class<?> type, Class<?>... parameters) {
        try {
            return type.getConstructor(parameters);
        } catch (NoSuchMethodException | SecurityException missing) {
            return null;
        }
    }

    /** Calls the constructor, rethrowing whatever it refused the values with as IllegalArgumentException. */
    static Object create(Constructor<?> constructor, Object... arguments) {
        try {
            return constructor.newInstance(arguments);
        } catch (InvocationTargetException refused) {
            final Throwable cause = refused.getCause();
            throw new IllegalArgumentException(cause == null ? refused.toString() : cause.getMessage(), cause);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * A trail's settings. Its end point is a place in the world, so the trail itself can only be
     * built each time it plays, from where the particle appears.
     *
     * @param x blocks from where the particle appears to where the trail ends, and so on for y and z
     */
    record Trail(Constructor<?> constructor, Color color, double x, double y, double z, int duration)
            implements RelativeData {

        @Override
        public Object at(Location where) {
            return create(constructor, where.clone().add(x, y, z), color, duration);
        }
    }
}
