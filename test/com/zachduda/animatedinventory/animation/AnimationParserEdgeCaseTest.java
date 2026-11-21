package com.zachduda.animatedinventory.animation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The mistakes and odd-but-valid files admins write by hand. Every one of these has to come
 * back as a clear message, or load as written - never as an exception.
 */
class AnimationParserEdgeCaseTest {

    private static AnimationParser.Result parse(String yaml) {
        return AnimationParserTest.parse(yaml);
    }

    private static ClearAnimation ok(String yaml) {
        final AnimationParser.Result result = parse(yaml);
        assertTrue(result.ok(), () -> "Expected no errors, got: " + result.errors());
        return result.animation();
    }

    private static void assertProblem(List<String> problems, String expected) {
        assertTrue(problems.stream().anyMatch(problem -> problem.contains(expected)),
                () -> "Expected a problem containing \"" + expected + "\" in " + problems);
    }

    /** One frame, written as a YAML flow mapping. */
    private static AnimationParser.Result frame(String flowMapping) {
        return parse("frames:\n  - " + flowMapping + "\n");
    }

    //______________________________________________________________ Symbols

    /** Y, N, 0 and 1 are the ones YAML could read as something else. */
    @Test
    void everyLetterAndDigitCanBeASymbol() {
        final String symbols = IntStream.concat(IntStream.rangeClosed('a', 'z'),
                        IntStream.concat(IntStream.rangeClosed('A', 'Z'), IntStream.rangeClosed('0', '9')))
                .mapToObj(c -> String.valueOf((char) c)).collect(Collectors.joining());

        final StringBuilder yaml = new StringBuilder("items:\n");
        for (char symbol : symbols.toCharArray()) {
            yaml.append("  ").append(symbol).append(": STONE\n");
        }
        yaml.append("frames:\n  - hotbar: 'YyNn01Zz9'\n");

        final ClearAnimation animation = ok(yaml.toString());
        assertEquals(symbols.length(), animation.items().size());
        for (char symbol : symbols.toCharArray()) {
            assertEquals(Material.STONE, animation.items().get(symbol).material(), "symbol " + symbol);
        }
    }

    @Test
    void symbolsAreCaseSensitive() {
        final ClearAnimation animation = ok("""
                items:
                  W: WHITE_STAINED_GLASS_PANE
                  w: BLACK_STAINED_GLASS_PANE
                frames:
                  - hotbar: 'Ww.......'
                """);

        assertEquals(Material.WHITE_STAINED_GLASS_PANE, animation.items().get('W').material());
        assertEquals(Material.BLACK_STAINED_GLASS_PANE, animation.items().get('w').material());
    }

    /** . and _ already mean "keep" and "empty", and a '.' key is a path to Bukkit. */
    @ParameterizedTest
    @ValueSource(strings = {"'_'", "'#'", "'-'", "'*'", "'.'", "' '", "~", "'10'"})
    void punctuationAndLongKeysCantBeSymbols(String key) {
        final AnimationParser.Result result = parse("items:\n  " + key + ": STONE\nframes:\n  - delay: 1\n");

        assertFalse(result.ok(), key);
        assertProblem(result.errors(), "can't be a symbol");
    }

    //______________________________________________________________ Hotbars

    @Test
    void aHotbarOfOnlyKeepsAndEmptiesNeedsNoItems() {
        assertEquals("._._._._.", ok("frames:\n  - hotbar: '._._._._.'\n").frames().get(0).hotbar());
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '"', textBlock = """
            'WWWWWWWW'      | must be exactly 9 symbols, one per slot, but 'WWWWWWWW' has 8
            'WWWWWWWWWW'    | has 10
            ''              | has 0
            111111111       | must be 9 symbols in quotes
            [W, W, W]       | must be 9 symbols in quotes
            'W\tWWWWWWW'    | isn't one of the symbols in 'items'
            """)
    void hotbarMistakesSayWhatsWrong(String hotbar, String expected) {
        final AnimationParser.Result result = parse("items:\n  W: STONE\n  '1': STONE\nframes:\n  - hotbar: "
                + hotbar.replace("\\t", "\t") + "\n");

        assertFalse(result.ok());
        assertProblem(result.errors(), "frame 1: 'hotbar' ");
        assertProblem(result.errors(), expected);
    }

    //______________________________________________________________ Numbers

    /**
     * Each value stands for a different YAML type: not-a-number, infinity, a number too big
     * for a long, a boolean, a quoted number and a list. None of them may get past as a number.
     */
    @ParameterizedTest
    @ValueSource(strings = {".nan", ".inf", "-.inf", "99999999999999999999999", "true", "'2'", "[2]"})
    void valuesThatArentUsableNumbersAreErrors(String value) {
        final AnimationParser.Result result = parse("weight: " + value + "\nframes:\n  - delay: " + value
                + "\n    sounds: [{sound: ENTITY_CHICKEN_EGG, volume: " + value + "}]\n"
                + "    particles: [{particle: FLAME, speed: " + value + "}]\n");

        assertFalse(result.ok(), value);
        assertProblem(result.errors(), "'weight' must be a whole number from 1 to 1000");
        assertProblem(result.errors(), "frame 1: 'delay' must be a whole number from 0 to 1200");
        assertProblem(result.errors(), "'volume' must be a number from 0 to 10");
        assertProblem(result.errors(), "'speed' must be a number from 0 to 100");
    }

    @Test
    void wholeNumbersCanHaveADecimalPoint() {
        final Frame frame = ok("frames:\n  - {delay: 2.0, hold: 4.0, particles: [{particle: FLAME, count: 3.0}]}\n")
                .frames().get(0);

        assertEquals(2, frame.delay());
        assertEquals(4, frame.hold());
        assertEquals(3, frame.particles().get(0).count());
    }

    /** Each option at both ends of its range, then just past each end. */
    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '"', textBlock = """
            true  | {delay: 0}
            true  | {delay: 1200}
            false | {delay: 1201}
            false | {delay: -1}
            true  | {hold: 0}
            true  | {hold: 8}
            false | {hold: 9}
            false | {hold: -1}
            true  | {sounds: [{sound: ENTITY_CHICKEN_EGG, volume: 0, pitch: 0}]}
            true  | {sounds: [{sound: ENTITY_CHICKEN_EGG, volume: 10, pitch: 2}]}
            false | {sounds: [{sound: ENTITY_CHICKEN_EGG, volume: 10.01}]}
            false | {sounds: [{sound: ENTITY_CHICKEN_EGG, volume: -0.01}]}
            false | {sounds: [{sound: ENTITY_CHICKEN_EGG, pitch: 2.01}]}
            true  | {particles: [{particle: FLAME, count: 0, speed: 0, height: -16, offset: -16}]}
            true  | {particles: [{particle: FLAME, count: 1000, speed: 100, height: 16, offset: [16, -16, 0]}]}
            false | {particles: [{particle: FLAME, count: 1001}]}
            false | {particles: [{particle: FLAME, count: -1}]}
            false | {particles: [{particle: FLAME, speed: -1}]}
            false | {particles: [{particle: FLAME, height: 16.5}]}
            false | {particles: [{particle: FLAME, offset: 16.5}]}
            false | {particles: [{particle: FLAME, offset: [0, 0, 17]}]}
            true  | {particles: [{particle: DUST, color: '#FF0000', size: 0.01}]}
            true  | {particles: [{particle: DUST, color: '#FF0000', size: 4}]}
            false | {particles: [{particle: DUST, color: '#FF0000', size: 0}]}
            false | {particles: [{particle: DUST, color: '#FF0000', size: 4.5}]}
            """)
    void rangesIncludeBothEnds(boolean loads, String frame) {
        final AnimationParser.Result result = frame(frame);

        assertEquals(loads, result.ok(), () -> frame + " -> " + result.errors());
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '"', textBlock = """
            true  | weight: 1
            true  | weight: 1000
            false | weight: 0
            false | weight: 1001
            """)
    void weightRange(boolean loads, String line) {
        assertEquals(loads, parse(line + "\nframes:\n  - delay: 1\n").ok(), line);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '"', textBlock = """
            true  | 1
            true  | 64
            false | 0
            false | 65
            false | 1.5
            """)
    void itemAmountRange(boolean loads, String amount) {
        final AnimationParser.Result result = parse("items:\n  W:\n    material: STONE\n    amount: " + amount
                + "\nframes:\n  - hotbar: 'W........'\n");

        assertEquals(loads, result.ok(), () -> amount + " -> " + result.errors());
    }

    @Test
    void lengthAddsUpEveryFrameAndTheFinish() {
        final String hundredTwelveTimes = "frames:\n" + "  - delay: 100\n".repeat(12);

        assertEquals(1200, ok(hundredTwelveTimes).length());
        assertProblem(parse(hundredTwelveTimes + "finish:\n  delay: 1\n").errors(), "It runs for 1201 ticks");
    }

    @Test
    void anAnimationCanBeZeroTicksLong() {
        assertEquals(0, ok("frames:\n  - hotbar: '_________'\n").length());
    }

    //______________________________________________________________ Wrong shapes

    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '"', textBlock = """
            items: [W, X]\\nframes: [{delay: 1}]                   | 'items' must list each symbol
            frames: {delay: 1}                                    | 'frames' must be a list
            frames: [{delay: 1}]\\nfinish: 4                       | 'finish' must be a set of options
            frames: [{delay: 1}]\\nfinish: [{delay: 4}]            | 'finish' must be a set of options
            frames: [~]                                           | frame 1: Must be a set of options
            frames: [[delay, 1]]                                  | frame 1: Must be a set of options
            items: {W: 5}\\nframes: [{delay: 1}]                   | item W: Must be a material name
            items: {W: {name: x}}\\nframes: [{delay: 1}]           | item W: Missing its 'material'
            items: {W: {material: 5}}\\nframes: [{delay: 1}]       | item W: 'material' must be a material name
            items: {W: {material: [STONE]}}\\nframes: [{delay: 1}] | item W: 'material' must be a material name
            items: {W: {material: STONE, lore: {a: b}}}\\nframes: [{delay: 1}] | 'lore' must be a line of text
            items: {W: {material: STONE, name: [a]}}\\nframes: [{delay: 1}]    | 'name' must be text
            frames: [{action-bar: [a]}]                           | 'action-bar' must be text
            frames: [{sounds: [{volume: 1}]}]                     | needs a name
            frames: [{sounds: [5]}]                               | needs a name
            frames: [{particles: [{count: 1}]}]                   | needs a name
            frames: [{particles: [[FLAME]]}]                      | needs a name
            frames: [{delay: 1}]\\nfinish: {delay: -1}             | finish: 'delay' must be a whole number
            enabled: 1\\nframes: [{delay: 1}]                      | 'enabled' must be true or false
            """)
    void wrongShapesAreReported(String yaml, String expected) {
        final AnimationParser.Result result = parse(yaml.replace("\\n", "\n"));

        assertFalse(result.ok(), yaml);
        assertProblem(result.errors(), expected);
    }

    @Test
    void anEmptyFileSaysFramesAreMissing() {
        assertProblem(parse("").errors(), "'frames' is missing");
        assertProblem(parse("# Only a comment\n").errors(), "'frames' is missing");
    }

    /** YAML aliases can make a list hold itself. Walking one must stop, not overflow the stack. */
    @Test
    void listsThatHoldThemselvesAreReported() {
        final AnimationParser.Result result = parse("""
                items:
                  W:
                    material: STONE
                    lore: &lore ['&7Line', *lore]
                frames:
                  - sounds: &sounds [ENTITY_CHICKEN_EGG, *sounds]
                    particles: &particles [FLAME, *particles]
                """);

        assertFalse(result.ok());
        assertProblem(result.errors(), "item W: 'lore' must be a line of text");
        assertProblem(result.errors(), "frame 1: Each of its 'sounds' needs a name");
        assertProblem(result.errors(), "frame 1: Each of its 'particles' needs a name");
    }

    //______________________________________________________________ Text

    @Test
    void loreWrittenAsABlockIsSplitIntoLines() {
        final AnimationItem item = ok("""
                items:
                  W:
                    material: STONE
                    lore: |
                      &7First
                      &7Second
                frames:
                  - hotbar: 'W........'
                """).items().get('W');

        assertEquals(List.of("&7First", "&7Second"), item.lore());
    }

    @Test
    void loreLinesWithBreaksInAListAreSplitToo() {
        final AnimationItem item = ok("""
                items:
                  X:
                    material: STONE
                    lore: ["&7One\\n&7Two", '&7Three', 4]
                frames:
                  - hotbar: 'X........'
                """).items().get('X');

        assertEquals(List.of("&7One", "&7Two", "&7Three", "4"), item.lore());
    }

    @Test
    void scalarsAreAcceptedAsText() {
        final ClearAnimation animation = ok("""
                items:
                  W: {material: STONE, name: 42, lore: true}
                frames:
                  - hotbar: 'W........'
                    action-bar: 7
                """);

        assertEquals("42", animation.items().get('W').name());
        assertEquals(List.of("true"), animation.items().get('W').lore());
        assertEquals("7", animation.frames().get(0).actionBar());
    }

    //______________________________________________________________ Sounds

    @ParameterizedTest
    @ValueSource(strings = {"ENTITY_CHICKEN_EGG", "entity.chicken.egg", "Entity.Chicken.Egg",
            "minecraft:entity.chicken.egg", "MINECRAFT:ENTITY_CHICKEN_EGG", "entity-chicken-egg",
            "'  ENTITY_CHICKEN_EGG  '"})
    void soundNamesWorkInEveryForm(String name) {
        assertEquals(1, ok("frames:\n  - sounds: [" + name + "]\n").frames().get(0).sounds().size(), name);
    }

    @Test
    void resourcePackSoundsAreLowerCasedKeys() {
        final AnimationSound sound = ok("frames:\n  - sounds: [MyPack:Clear.Whoosh]\n").frames().get(0).sounds().get(0);

        assertEquals("mypack:clear.whoosh", sound.key());
        assertNull(sound.sound());
        assertEquals("MyPack:Clear.Whoosh", sound.name(), "Messages show the name as written");
    }

    @ParameterizedTest
    @ValueSource(strings = {"minecraft:not.a.sound", "'my pack:whoosh'", "':'", "'pack:'", "':whoosh'", "''"})
    void soundsThatArentAnywhereAreErrors(String name) {
        final AnimationParser.Result result = parse("frames:\n  - sounds: [" + name + "]\n");

        assertFalse(result.ok(), name);
        assertProblem(result.errors(), "isn't a sound on this server");
    }

    @Test
    void oneSoundOrParticleNeedsNoList() {
        final Frame frame = ok("""
                frames:
                  - sounds: ENTITY_CHICKEN_EGG
                    stop-sounds: ENTITY_MINECART_INSIDE
                    particles: {particle: FLAME, count: 2}
                """).frames().get(0);

        assertEquals(1, frame.sounds().size());
        assertEquals(1, frame.stopSounds().size());
        assertEquals(2, frame.particles().get(0).count());
    }

    @Test
    void stopSoundsOnlyTakeAName() {
        final AnimationParser.Result result = parse("""
                frames:
                  - stop-sounds: [{sound: ENTITY_MINECART_INSIDE, volume: 2}]
                """);

        assertTrue(result.ok(), result.errors()::toString);
        assertProblem(result.warnings(), "frame 1, sound ENTITY_MINECART_INSIDE: 'volume' isn't an option here");
    }

    //______________________________________________________________ Particles

    @ParameterizedTest
    @ValueSource(strings = {"DRIPPING_WATER", "dripping_water", "minecraft:dripping_water", "'Dripping Water'",
            "dripping-water", "DRIP_WATER", "drip.water"})
    void particleNamesWorkInEveryForm(String name) {
        assertEquals(Particle.DRIPPING_WATER,
                ok("frames:\n  - particles: [" + name + "]\n").frames().get(0).particles().get(0).particle(), name);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '"', textBlock = """
            '#FF0000'     | 255 | 0   | 0
            '#ff0000'     | 255 | 0   | 0
            'FF0000'      | 255 | 0   | 0
            ' #00FF00 '   | 0   | 255 | 0
            [0, 0, 255]   | 0   | 0   | 255
            [0.0, 128, 0] | 0   | 128 | 0
            """)
    void colorsInEveryForm(String color, int red, int green, int blue) {
        final Particle.DustOptions dust = assertInstanceOf(Particle.DustOptions.class,
                ok("frames:\n  - particles: [{particle: DUST, color: " + color + "}]\n")
                        .frames().get(0).particles().get(0).data());

        assertEquals(Color.fromRGB(red, green, blue), dust.getColor(), color);
    }

    @ParameterizedTest
    @ValueSource(strings = {"'#FFF'", "'#GGGGGG'", "'#FF00000'", "red", "16711680", "[256, 0, 0]", "[-1, 0, 0]",
            "[1.5, 0, 0]", "[0, 0]", "[0, 0, 0, 0]", "{r: 255}"})
    void colorsThatArentColorsAreErrors(String color) {
        final AnimationParser.Result result = frame("{particles: [{particle: DUST, color: " + color + "}]}");

        assertFalse(result.ok(), color);
        assertProblem(result.errors(), "'color' isn't a color");
    }

    /** Without quotes, YAML reads #FF0000 as a comment, which has to be explained. */
    @Test
    void anUnquotedHexColorIsExplained() {
        final AnimationParser.Result result = parse("frames:\n  - particles:\n      - particle: DUST\n"
                + "        color: #FF0000\n");

        assertProblem(result.errors(), "This particle needs a 'color'");
        assertProblem(result.errors(), "everything after a # is ignored");
    }

    @Test
    void colorParticlesTakeAColor() {
        final Object data = ok("frames:\n  - particles: [{particle: ENTITY_EFFECT, color: [255, 0, 0]}]\n")
                .frames().get(0).particles().get(0).data();

        assertEquals(Color.RED, data);
    }

    @Test
    void dataOptionsAParticleDoesntUseAreWarnedAboutForEveryKindOfParticle() {
        final AnimationParser.Result result = parse("""
                frames:
                  - particles:
                      - {particle: BLOCK, block: STONE, color: '#FF0000'}
                      - {particle: DUST, color: '#FF0000', size: 2, block: STONE, item: DIAMOND}
                      - {particle: ENTITY_EFFECT, color: '#FF0000', size: 2}
                      - {particle: ITEM, item: DIAMOND, block: STONE}
                """);

        assertTrue(result.ok(), result.errors()::toString);
        assertProblem(result.warnings(), "particle BLOCK: 'color' was ignored");
        assertProblem(result.warnings(), "particle DUST: 'block' was ignored");
        assertProblem(result.warnings(), "particle DUST: 'item' was ignored");
        assertProblem(result.warnings(), "particle ENTITY_EFFECT: 'size' was ignored");
        assertProblem(result.warnings(), "particle ITEM: 'block' was ignored");
        assertEquals(5, result.warnings().size(), result.warnings()::toString);
    }

    @ParameterizedTest
    @ValueSource(strings = {"BLOCK", "FALLING_DUST", "BLOCK_MARKER"})
    void blockParticlesNeedABlock(String particle) {
        assertProblem(frame("{particles: [" + particle + "]}").errors(), "This particle needs a block");
        assertProblem(frame("{particles: [{particle: " + particle + ", block: NOT_A_BLOCK}]}").errors(),
                "'NOT_A_BLOCK' isn't a block on this server");
        assertTrue(frame("{particles: [{particle: " + particle + ", block: STONE}]}").ok(), particle);
    }

    /** Particles whose settings a file has no way to write are refused by name, not with a crash. */
    @ParameterizedTest
    @ValueSource(strings = {"VIBRATION", "SHRIEK", "SCULK_CHARGE", "DRAGON_BREATH", "GEYSER", "GEYSER_BASE"})
    void particlesWhoseDataCantBeWrittenAreRefused(String particle) {
        assertProblem(frame("{particles: [" + particle + "]}").errors(), "needs settings that animation files can't set");
    }

    //______________________________________________________________ The result

    @Test
    void aLoadedAnimationCantBeChangedWhileItPlays() {
        final ClearAnimation animation = ok("""
                items:
                  W: STONE
                frames:
                  - hotbar: 'W........'
                    sounds: [ENTITY_CHICKEN_EGG]
                    particles: [FLAME]
                """);

        final Frame frame = animation.frames().get(0);
        assertThrows(UnsupportedOperationException.class, () -> animation.frames().clear());
        assertThrows(UnsupportedOperationException.class, () -> animation.items().clear());
        assertThrows(UnsupportedOperationException.class, () -> frame.sounds().clear());
        assertThrows(UnsupportedOperationException.class, () -> frame.particles().clear());
        assertThrows(UnsupportedOperationException.class, () -> animation.items().get('W').lore().add("x"));
    }

    @Test
    void problemListsCantBeChanged() {
        final AnimationParser.Result result = parse("frames: 3");

        assertThrows(UnsupportedOperationException.class, () -> result.errors().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.warnings().add("x"));
        assertNull(result.animation());
    }

    @Test
    void aFrameWithNothingInItIsAPause() {
        final Frame frame = ok("frames:\n  - {}\n").frames().get(0);

        assertEquals(0, frame.delay());
        assertNull(frame.hotbar());
        assertEquals(Frame.NO_HOLD, frame.hold());
        assertTrue(frame.sounds().isEmpty() && frame.stopSounds().isEmpty() && frame.particles().isEmpty());
        assertNull(frame.actionBar());
    }
}
