package com.zachduda.animatedinventory.animation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

class PlaceholdersTest {

    /** Stands in for a config read that mustn't happen. */
    private static final Supplier<String> UNREAD = () -> {
        throw new AssertionError("Read the config for text that doesn't need it");
    };

    @Test
    void textWithoutPlaceholdersIsLeftAloneWithoutReadingTheConfig() {
        final String text = "&a&lPoof!";
        assertSame(text, Placeholders.fill(text, "Steve", UNREAD, UNREAD));
    }

    @Test
    void onlyTheMessagesTheTextUsesAreRead() {
        assertEquals("Bye Steve", Placeholders.fill("Bye %player%", "Steve", UNREAD, UNREAD));
        assertEquals("Clearing...", Placeholders.fill("%progress-msg%", "Steve", () -> "Clearing...", UNREAD));
        assertEquals("Done!", Placeholders.fill("%done-msg%", "Steve", UNREAD, () -> "Done!"));
    }

    @Test
    void everyPlaceholderIsFilledEveryTimeItAppears() {
        assertEquals("Steve: Clearing... / Done! / Steve",
                Placeholders.fill("%player%: %progress-msg% / %done-msg% / %player%", "Steve",
                        () -> "Clearing...", () -> "Done!"));
    }

    @Test
    void aMessageCanUseThePlayersName() {
        assertEquals("Clearing Steve's inventory",
                Placeholders.fill("%progress-msg%", "Steve", () -> "Clearing %player%'s inventory", UNREAD));
    }

    @Test
    void missingMessagesAreFilledInAsNothing() {
        assertEquals("[] []", Placeholders.fill("[%progress-msg%] [%done-msg%]", "Steve", () -> null, () -> null));
    }

    @Test
    void otherPercentSignsAndUnknownPlaceholdersStay() {
        assertEquals("100% %unknown% Steve", Placeholders.fill("100% %unknown% %player%", "Steve", UNREAD, UNREAD));
    }

    @Test
    void noTextIsNoText() {
        assertNull(Placeholders.fill(null, "Steve", UNREAD, UNREAD));
    }
}
