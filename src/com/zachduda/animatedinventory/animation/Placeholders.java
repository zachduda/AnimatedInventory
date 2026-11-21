package com.zachduda.animatedinventory.animation;

import java.util.function.Supplier;

/**
 * Fills in the placeholders an animation's action-bar text can use: %player%, %progress-msg%
 * and %done-msg%.
 *
 * This runs on frames that play every tick or two, so text with no placeholders is returned
 * as it is, and each message is only looked up when the text asks for it.
 */
public final class Placeholders {

    public static final String PLAYER = "%player%";
    public static final String PROGRESS_MSG = "%progress-msg%";
    public static final String DONE_MSG = "%done-msg%";

    private Placeholders() {
    }

    /**
     * @param progressMsg progress-msg from config.yml; may give null, which is filled in as nothing
     * @param doneMsg     done-msg from config.yml; may give null, which is filled in as nothing
     */
    public static String fill(String text, String player, Supplier<String> progressMsg, Supplier<String> doneMsg) {
        if (text == null || text.indexOf('%') < 0) {
            return text;
        }
        // The messages go in first, so a progress-msg with %player% in it gets the name too.
        String filled = text;
        if (filled.contains(PROGRESS_MSG)) {
            filled = filled.replace(PROGRESS_MSG, orEmpty(progressMsg.get()));
        }
        if (filled.contains(DONE_MSG)) {
            filled = filled.replace(DONE_MSG, orEmpty(doneMsg.get()));
        }
        return filled.replace(PLAYER, orEmpty(player));
    }

    private static String orEmpty(String text) {
        return text == null ? "" : text;
    }
}
