package com.zachduda.animatedinventory.animation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** The settings for EFFECT, INSTANT_EFFECT, DUST_COLOR_TRANSITION and TRAIL. */
class ParticleSettingsTest {

    private static AnimationParser.Result particle(String flowMapping) {
        return AnimationParserTest.parse("frames:\n  - particles: [" + flowMapping + "]\n");
    }

    private static Object data(String flowMapping) {
        final AnimationParser.Result result = particle(flowMapping);
        assertTrue(result.ok(), () -> flowMapping + " -> " + result.errors());
        assertEquals(List.of(), result.warnings(), flowMapping);
        return result.animation().frames().get(0).particles().get(0).data();
    }

    private static void assertProblem(List<String> problems, String expected) {
        assertTrue(problems.stream().anyMatch(problem -> problem.contains(expected)),
                () -> "Expected a problem containing \"" + expected + "\" in " + problems);
    }

    //______________________________________________________________ EFFECT and INSTANT_EFFECT

    /** Both took no settings on older servers, so a file written for one of those must still load. */
    @ParameterizedTest
    @ValueSource(strings = {"EFFECT", "INSTANT_EFFECT", "SPELL", "SPELL_INSTANT"})
    void effectsNeedNoSettingsAndStayWhite(String name) {
        final Particle.Spell spell = assertInstanceOf(Particle.Spell.class, data(name));

        assertEquals(Color.WHITE, spell.getColor());
        assertEquals(1f, spell.getPower());
    }

    @Test
    void effectsTakeAColorAndPower() {
        final Particle.Spell spell = assertInstanceOf(Particle.Spell.class,
                data("{particle: INSTANT_EFFECT, color: '#FF0000', power: 2.5}"));

        assertEquals(Color.RED, spell.getColor());
        assertEquals(2.5f, spell.getPower());
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            0,     true
            10,    true
            -0.1,  false
            10.5,  false
            """)
    void powerRange(String power, boolean loads) {
        assertEquals(loads, particle("{particle: EFFECT, power: " + power + "}").ok(), power);
    }

    @Test
    void anEffectColorThatIsntAColorIsAnError() {
        assertProblem(particle("{particle: EFFECT, color: red}").errors(), "particle EFFECT: 'color' isn't a color");
    }

    //______________________________________________________________ DUST_COLOR_TRANSITION

    @Test
    void dustTransitionsGoFromOneColorToAnother() {
        final Particle.DustTransition dust = assertInstanceOf(Particle.DustTransition.class,
                data("{particle: DUST_COLOR_TRANSITION, color: '#FF0000', to-color: [0, 0, 255], size: 2}"));

        assertEquals(Color.RED, dust.getColor());
        assertEquals(Color.BLUE, dust.getToColor());
        assertEquals(2f, dust.getSize());
    }

    @Test
    void dustTransitionSizeDefaultsToOne() {
        final Particle.DustTransition dust = assertInstanceOf(Particle.DustTransition.class,
                data("{particle: DUST_COLOR_TRANSITION, color: '#FF0000', to-color: '#0000FF'}"));

        assertEquals(1f, dust.getSize());
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '"', textBlock = """
            {particle: DUST_COLOR_TRANSITION}                                       | This particle needs a 'color'
            {particle: DUST_COLOR_TRANSITION, color: '#FF0000'}                     | This particle needs a 'to-color'
            {particle: DUST_COLOR_TRANSITION, to-color: '#FF0000'}                  | This particle needs a 'color'
            {particle: DUST_COLOR_TRANSITION, color: '#FF0000', to-color: blue}     | 'to-color' isn't a color
            {particle: DUST_COLOR_TRANSITION, color: '#FF0000', to-color: '#00F', size: 1} | 'to-color' isn't a color
            {particle: DUST_COLOR_TRANSITION, color: '#FF0000', to-color: '#0000FF', size: 5} | 'size' must be a number from 0.01 to 4
            """)
    void dustTransitionMistakes(String particle, String expected) {
        final AnimationParser.Result result = particle(particle);

        assertFalse(result.ok(), particle);
        assertProblem(result.errors(), "particle DUST_COLOR_TRANSITION: " + expected);
    }

    //______________________________________________________________ TRAIL

    /** A trail ends somewhere in the world, so it's only built where the particle appears. */
    @Test
    void trailsEndWhereTheirTargetSays() {
        final RelativeData settings = assertInstanceOf(RelativeData.class,
                data("{particle: TRAIL, color: '#00FF00', target: [1, 2, -3], duration: 40}"));

        final Location appearsAt = new Location(null, 10, 64, 10);
        final Particle.Trail trail = assertInstanceOf(Particle.Trail.class, settings.at(appearsAt));

        assertEquals(new Location(null, 11, 66, 7), trail.getTarget());
        assertEquals(Color.LIME, trail.getColor());
        assertEquals(40, trail.getDuration());
        assertEquals(new Location(null, 10, 64, 10), appearsAt, "Working out a trail mustn't move the particle");
    }

    @Test
    void eachPlayGetsItsOwnTrail() {
        final RelativeData settings = (RelativeData) data("{particle: TRAIL, color: '#00FF00', target: [0, 1, 0]}");

        final Particle.Trail here = (Particle.Trail) settings.at(new Location(null, 0, 0, 0));
        final Particle.Trail there = (Particle.Trail) settings.at(new Location(null, 100, 0, 0));

        assertEquals(new Location(null, 0, 1, 0), here.getTarget());
        assertEquals(new Location(null, 100, 1, 0), there.getTarget());
    }

    @Test
    void trailDurationDefaultsToASecond() {
        final Particle.Trail trail = (Particle.Trail) ((RelativeData) data(
                "{particle: TRAIL, color: '#00FF00', target: [0, 1, 0]}")).at(new Location(null, 0, 0, 0));

        assertEquals(20, trail.getDuration());
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '"', textBlock = """
            {particle: TRAIL}                                               | This particle needs a 'color'
            {particle: TRAIL, color: '#FF0000'}                             | This particle needs a 'target'
            {particle: TRAIL, color: '#FF0000', target: 2}                  | 'target' must be three numbers
            {particle: TRAIL, color: '#FF0000', target: [0, 2]}             | 'target' must be three numbers
            {particle: TRAIL, color: '#FF0000', target: [0, 17, 0]}         | 'target' must be three numbers like [0, 2, 0], each from -16 to 16
            {particle: TRAIL, color: '#FF0000', target: [0, up, 0]}         | 'target' must be three numbers
            {particle: TRAIL, color: '#FF0000', target: [0, 2, 0], duration: 0}    | 'duration' must be a whole number from 1 to 1200
            {particle: TRAIL, color: '#FF0000', target: [0, 2, 0], duration: 1201} | 'duration' must be a whole number from 1 to 1200
            {particle: TRAIL, color: '#FF0000', target: [0, 2, 0], duration: 1.5}  | 'duration' must be a whole number
            """)
    void trailMistakes(String particle, String expected) {
        final AnimationParser.Result result = particle(particle);

        assertFalse(result.ok(), particle);
        assertProblem(result.errors(), "particle TRAIL: " + expected);
    }

    //______________________________________________________________ Options that don't apply

    @Test
    void optionsAParticleDoesntUseAreWarnedAbout() {
        final AnimationParser.Result result = AnimationParserTest.parse("""
                frames:
                  - particles:
                      - {particle: EFFECT, size: 2, target: [0, 1, 0]}
                      - {particle: TRAIL, color: '#FF0000', target: [0, 1, 0], power: 2, to-color: '#00FF00'}
                      - {particle: DUST_COLOR_TRANSITION, color: '#FF0000', to-color: '#00FF00', duration: 5}
                      - {particle: FLAME, power: 1, target: [0, 1, 0], duration: 5, to-color: '#FFFFFF'}
                      - {particle: DUST, color: '#FF0000', power: 1}
                """);

        assertTrue(result.ok(), result.errors()::toString);
        assertProblem(result.warnings(), "particle EFFECT: 'size' was ignored");
        assertProblem(result.warnings(), "particle EFFECT: 'target' was ignored");
        assertProblem(result.warnings(), "particle TRAIL: 'power' was ignored");
        assertProblem(result.warnings(), "particle TRAIL: 'to-color' was ignored");
        assertProblem(result.warnings(), "particle DUST_COLOR_TRANSITION: 'duration' was ignored");
        assertProblem(result.warnings(), "particle FLAME: 'power' was ignored");
        assertProblem(result.warnings(), "particle DUST: 'power' was ignored");
        assertEquals(10, result.warnings().size(), result.warnings()::toString);
        assertFalse(result.warnings().stream().anyMatch(warning -> warning.contains("isn't an option here")),
                result.warnings()::toString);
    }

    //______________________________________________________________ Still refused

    @ParameterizedTest
    @CsvSource(textBlock = """
            SCULK_CHARGE,  SCULK_CHARGE_POP
            SHRIEK,        SONIC_BOOM
            VIBRATION,     TRAIL
            DRAGON_BREATH, WITCH
            """)
    void refusedParticlesSuggestOneThatWorks(String refused, String alternative) {
        assertProblem(particle(refused).errors(),
                "This particle needs settings that animation files can't set, so pick a different one, like "
                        + alternative + ".");
    }

    @ParameterizedTest
    @ValueSource(strings = {"GEYSER", "GEYSER_PLUME", "GEYSER_BASE", "GEYSER_POOF"})
    void refusedParticlesWithoutALookalikeSaySo(String refused) {
        assertProblem(particle(refused).errors(),
                "This particle needs settings that animation files can't set, so pick a different one.");
    }

    /** A suggestion has to be a particle on this server that a file can actually set up. */
    @Test
    void everySuggestionIsUsable() {
        for (Map.Entry<String, String> suggestion : AnimationParser.ALTERNATIVES.entrySet()) {
            final Particle alternative = BukkitLookup.resolveParticle(suggestion.getValue());
            assertNotNull(alternative, suggestion::toString);
            assertFalse(particle(suggestion.getValue()).errors().stream()
                    .anyMatch(error -> error.contains("can't set")), suggestion::toString);
            assertTrue(particle(suggestion.getKey()).errors().stream()
                    .anyMatch(error -> error.contains("can't set")), () -> suggestion.getKey() + " is usable now");
        }
    }

    //______________________________________________________________ The API they're built from

    /**
     * The types are recognized by name and built through these constructors, so a rename in
     * a future server version would otherwise only show up as "can't set" in the console.
     */
    @Test
    void theDataTypesAreWhereTheParserLooksForThem() {
        assertEquals(ParticleData.SPELL, Particle.EFFECT.getDataType().getName());
        assertEquals(ParticleData.SPELL, Particle.INSTANT_EFFECT.getDataType().getName());
        assertEquals(ParticleData.DUST_TRANSITION, Particle.DUST_COLOR_TRANSITION.getDataType().getName());
        assertEquals(ParticleData.TRAIL, Particle.TRAIL.getDataType().getName());

        assertNotNull(ParticleData.constructor(Particle.Spell.class, Color.class, float.class));
        assertNotNull(ParticleData.constructor(Particle.DustTransition.class, Color.class, Color.class, float.class));
        assertNotNull(ParticleData.constructor(Particle.Trail.class, Location.class, Color.class, int.class));
    }

    @Test
    void aMissingConstructorIsNullNotAnError() {
        assertEquals(null, ParticleData.constructor(Particle.Trail.class, String.class));
    }
}
