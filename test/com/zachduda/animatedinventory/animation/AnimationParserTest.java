package com.zachduda.animatedinventory.animation;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class AnimationParserTest {

    static AnimationParser.Result parse(String yaml) {
        final YamlConfiguration file = new YamlConfiguration();
        try {
            file.loadFromString(yaml);
        } catch (InvalidConfigurationException e) {
            throw new AssertionError("The test's YAML doesn't parse", e);
        }
        return AnimationParser.parse("test", file, new TestLookup());
    }

    private static ClearAnimation ok(String yaml) {
        final AnimationParser.Result result = parse(yaml);
        assertTrue(result.ok(), () -> "Expected no errors, got: " + result.errors());
        assertEquals(List.of(), result.warnings());
        return result.animation();
    }

    private static void assertProblem(List<String> problems, String expected) {
        assertTrue(problems.stream().anyMatch(problem -> problem.contains(expected)),
                () -> "Expected a problem containing \"" + expected + "\" in " + problems);
    }

    @Test
    void readsAMinimalAnimationWithDefaults() {
        final ClearAnimation animation = ok("""
                items:
                  W: WHITE_STAINED_GLASS_PANE
                frames:
                  - hotbar: 'W........'
                  - delay: 3
                    hotbar: 'WW_......'
                """);

        assertTrue(animation.enabled());
        assertEquals(1, animation.weight());
        assertEquals(2, animation.frames().size());
        assertEquals(0, animation.frames().get(0).delay());
        assertEquals(3, animation.frames().get(1).delay());
        assertEquals("WW_......", animation.frames().get(1).hotbar());
        assertEquals(Frame.NO_HOLD, animation.frames().get(1).hold());
        assertEquals(3, animation.length(), "The finish defaults to no delay");

        final AnimationItem pane = animation.items().get('W');
        assertEquals(Material.WHITE_STAINED_GLASS_PANE, pane.material());
        assertNull(pane.name());
        assertEquals(1, pane.amount());
    }

    @Test
    void readsFullItemSections() {
        final AnimationItem item = ok("""
                items:
                  '7':
                    material: minecraft:tnt
                    name: '&cBoom'
                    lore: ['&7One', '&7Two']
                    amount: 3
                frames:
                  - hotbar: '7........'
                """).items().get('7');

        assertEquals(Material.TNT, item.material());
        assertEquals("&cBoom", item.name());
        assertEquals(List.of("&7One", "&7Two"), item.lore());
        assertEquals(3, item.amount());
    }

    @Test
    void readsSoundsInEveryForm() {
        final Frame frame = ok("""
                frames:
                  - sounds:
                      - ENTITY_CHICKEN_EGG
                      - {sound: minecraft:entity.chicken.egg, volume: 2, pitch: 0.5}
                      - myserver:clear.whoosh
                    stop-sounds: [entity.minecart.inside]
                """).frames().get(0);

        assertEquals(3, frame.sounds().size());
        assertEquals(1f, frame.sounds().get(0).volume());
        assertEquals(1f, frame.sounds().get(0).pitch());
        assertEquals(2f, frame.sounds().get(1).volume());
        assertEquals(0.5f, frame.sounds().get(1).pitch());
        assertEquals("myserver:clear.whoosh", frame.sounds().get(2).key(), "Resource pack sounds play by key");
        assertEquals(1, frame.stopSounds().size());
    }

    @Test
    void readsParticlesInEveryForm() {
        final Frame frame = ok("""
                frames:
                  - particles:
                      - FLAME
                      - {particle: SPELL_WITCH, count: 5, offset: 0.3, speed: 1, height: 2}
                      - {particle: DUST, color: '#FF0000', size: 2}
                      - {particle: DUST, color: [0, 255, 0]}
                      - {particle: BLOCK, block: STONE}
                      - {particle: ITEM, item: DIAMOND}
                """).frames().get(0);

        final AnimationParticle flame = frame.particles().get(0);
        assertEquals(Particle.FLAME, flame.particle());
        assertEquals(1, flame.count());
        assertEquals(0, flame.speed());
        assertEquals(1, flame.height());
        assertNull(flame.data());

        final AnimationParticle witch = frame.particles().get(1);
        assertEquals(Particle.WITCH, witch.particle(), "Pre-1.20.5 names find the renamed particle");
        assertArrayEquals(new double[] {0.3, 0.3, 0.3},
                new double[] {witch.offsetX(), witch.offsetY(), witch.offsetZ()}, "One offset covers all three axes");
        assertEquals(2, witch.height());

        final Particle.DustOptions red = assertInstanceOf(Particle.DustOptions.class, frame.particles().get(2).data());
        assertEquals(Color.RED, red.getColor());
        assertEquals(2f, red.getSize());
        final Particle.DustOptions green = assertInstanceOf(Particle.DustOptions.class, frame.particles().get(3).data());
        assertEquals(Color.LIME, green.getColor());
        assertEquals(1f, green.getSize());

        assertEquals("block data for STONE", frame.particles().get(4).data());
        assertEquals("item stack of DIAMOND", frame.particles().get(5).data());
    }

    @Test
    void finishRunsAfterTheLastFrame() {
        final ClearAnimation animation = ok("""
                frames:
                  - delay: 2
                  - delay: 3
                finish:
                  delay: 4
                  sounds: [ENTITY_PLAYER_BURP]
                  action-bar: '&aGone!'
                """);

        assertEquals(9, animation.length());
        assertEquals("&aGone!", animation.finish().actionBar());
        assertEquals(1, animation.finish().sounds().size());
        assertNull(animation.finish().hotbar());
    }

    @Test
    void reportsEveryProblemInOnePass() {
        final AnimationParser.Result result = parse("""
                enabled: 'yes'
                weight: 0
                items:
                  WW: STONE
                  X: NOT_A_THING
                  A: AIR
                  S: OAK_STAIR
                frames:
                  - hotbar: 'XX'
                  - hotbar: 'Q........'
                  - hotbar: '. . . . .'
                  - hotbar: 123456789
                  - hold: 9
                  - delay: -1
                  - delay: 2.5
                  - sounds: [NOT_A_SOUND]
                  - sounds: ['ENTITY_CHICKEN_EGG 1.0 0.5']
                  - sounds: [{sound: ENTITY_CHICKEN_EGG, volume: 11, pitch: -1}]
                  - particles: [NOT_A_PARTICLE]
                  - particles: [DUST]
                  - particles: [{particle: DUST, color: red}]
                  - particles: [{particle: FLAME, count: 5000, offset: [1, 2]}]
                  - particles: [{particle: BLOCK}]
                  - just text
                """);

        assertFalse(result.ok());
        final List<String> errors = result.errors();
        assertProblem(errors, "'enabled' must be true or false");
        assertProblem(errors, "'weight' must be a whole number from 1 to 1000");
        assertProblem(errors, "'WW' can't be a symbol");
        assertProblem(errors, "item X: 'NOT_A_THING' isn't an item");
        assertProblem(errors, "item A: Can't be air");
        assertProblem(errors, "item S: 'OAK_STAIR' isn't an item");
        assertProblem(errors, "frame 2: 'hotbar' uses 'Q'");
        assertProblem(errors, "frame 3: 'hotbar' has a space in it");
        assertProblem(errors, "frame 4: 'hotbar' must be 9 symbols in quotes");
        assertProblem(errors, "frame 5: 'hold' must be a whole number from 0 to 8");
        assertProblem(errors, "frame 6: 'delay' must be a whole number");
        assertProblem(errors, "frame 7: 'delay' must be a whole number");
        assertProblem(errors, "frame 8: 'NOT_A_SOUND' isn't a sound");
        assertProblem(errors, "To set its volume or pitch, write it as");
        assertProblem(errors, "'volume' must be a number from 0 to 10");
        assertProblem(errors, "'pitch' must be a number from 0 to 2");
        assertProblem(errors, "frame 11: 'NOT_A_PARTICLE' isn't a particle");
        assertProblem(errors, "frame 12, particle DUST: This particle needs a 'color'");
        assertProblem(errors, "frame 13, particle DUST: 'color' isn't a color");
        assertProblem(errors, "'count' must be a whole number from 0 to 1000");
        assertProblem(errors, "'offset' must be one number, or three");
        assertProblem(errors, "frame 15, particle BLOCK: This particle needs a block");
        assertProblem(errors, "frame 16: Must be a set of options");
    }

    @Test
    void aSymbolWhoseItemIsBrokenIsOnlyReportedOnce() {
        final AnimationParser.Result result = parse("""
                items:
                  X: NOT_A_THING
                frames:
                  - hotbar: 'X........'
                  - hotbar: '.X.......'
                """);

        assertEquals(1, result.errors().size(), result.errors()::toString);
        assertProblem(result.errors(), "item X");
    }

    @Test
    void framesAreRequired() {
        assertProblem(parse("enabled: true").errors(), "'frames' is missing");
        assertProblem(parse("frames: []").errors(), "'frames' is empty");
        assertProblem(parse("frames: 3").errors(), "'frames' must be a list");
    }

    @Test
    void unknownOptionsAreWarningsNotErrors() {
        final AnimationParser.Result result = parse("""
                description: Just a note
                frames:
                  - sound: ENTITY_CHICKEN_EGG
                    delay: 2
                """);

        assertTrue(result.ok(), result.errors()::toString);
        assertProblem(result.warnings(), "'description' isn't an option here");
        assertProblem(result.warnings(), "frame 1: 'sound' isn't an option here");
        assertProblem(result.warnings(), "sounds");
    }

    @Test
    void theFinishCantDraw() {
        final AnimationParser.Result result = parse("""
                frames:
                  - delay: 1
                finish:
                  hotbar: 'XXXXXXXXX'
                  hold: 3
                  delay: 2
                """);

        assertTrue(result.ok(), result.errors()::toString);
        assertEquals(2, result.warnings().size(), result.warnings()::toString);
        assertProblem(result.warnings(), "finish: 'hotbar' was ignored");
        assertNull(result.animation().finish().hotbar());
        assertEquals(Frame.NO_HOLD, result.animation().finish().hold());
    }

    @Test
    void dataAParticleDoesntUseIsWarnedAbout() {
        final AnimationParser.Result result = parse("""
                frames:
                  - particles: [{particle: FLAME, color: '#FF0000'}]
                """);

        assertTrue(result.ok(), result.errors()::toString);
        assertProblem(result.warnings(), "'color' was ignored, since this particle doesn't use it");
    }

    @Test
    void animationsCantRunLongerThanAMinute() {
        assertTrue(parse("""
                frames:
                  - delay: 1200
                """).ok());

        final AnimationParser.Result tooLong = parse("""
                frames:
                  - delay: 1200
                finish:
                  delay: 1
                """);
        assertProblem(tooLong.errors(), "It runs for 1201 ticks");
    }

    @Test
    void canBeTurnedOffAndWeighted() {
        final ClearAnimation animation = ok("""
                enabled: false
                weight: 5
                frames:
                  - delay: 1
                """);

        assertFalse(animation.enabled());
        assertEquals(5, animation.weight());
    }
}
