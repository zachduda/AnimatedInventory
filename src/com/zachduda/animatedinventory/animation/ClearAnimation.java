package com.zachduda.animatedinventory.animation;

import java.util.List;
import java.util.Map;

/**
 * One clear animation, parsed once from Animations/&lt;name&gt;.yml.
 *
 * @param name    lower-case file name without ".yml", ie: "explosion"
 * @param enabled whether it can be picked when an inventory is cleared
 * @param weight  how often it's picked compared to the other enabled animations
 * @param items   the items frames draw with, keyed by their one-character symbol
 * @param frames  played in order, each {@link Frame#delay()} ticks after the one before
 * @param finish  runs after the last frame and clears the inventory
 */
public record ClearAnimation(String name, boolean enabled, int weight, Map<Character, AnimationItem> items,
                             List<Frame> frames, Frame finish) {

    /** Ticks from the start of the animation until the inventory is cleared. */
    public int length() {
        int ticks = finish.delay();
        for (Frame frame : frames) {
            ticks += frame.delay();
        }
        return ticks;
    }
}
