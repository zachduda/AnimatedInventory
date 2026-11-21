package com.zachduda.animatedinventory.animation;

import java.util.List;

/**
 * One step of a clear animation. The finish is a frame too, minus the hotbar and hold.
 *
 * @param delay      ticks to wait after the previous frame (or after the start, for the first)
 * @param hotbar     nine symbols, one per hotbar slot: an item symbol, {@link #KEEP} or
 *                   {@link #EMPTY}. Null when the frame leaves the hotbar alone.
 * @param hold       the hotbar slot to select, or {@link #NO_HOLD}
 * @param stopSounds sounds to stop before anything else happens
 * @param actionBar  raw action bar text, still holding its placeholders and &amp; codes. May be null.
 */
public record Frame(int delay, String hotbar, int hold, List<AnimationSound> sounds,
                    List<AnimationSound> stopSounds, List<AnimationParticle> particles, String actionBar) {

    public static final int HOTBAR_SIZE = 9;

    /** Leaves a slot as it is, so the player's own item stays until something replaces it. */
    public static final char KEEP = '.';

    /** Empties a slot. */
    public static final char EMPTY = '_';

    public static final int NO_HOLD = -1;
}
