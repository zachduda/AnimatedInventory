package com.zachduda.animatedinventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Drives timelines through a fake scheduler, a tick at a time, to check what every clear and
 * fortune animation relies on: frames run on their ticks, in order, and stop for good when the
 * animation is stopped, replaced, fails, or the player leaves.
 */
class TimelineTest {

    private static final UUID STEVE = UUID.randomUUID();
    private static final UUID ALEX = UUID.randomUUID();

    /** A scheduler that only moves when the test says so. */
    private static final class FakeHost implements Timeline.Host {

        private final class Scheduled implements Timeline.Task {
            final long at;
            final Runnable action;
            boolean cancelled;

            Scheduled(long at, Runnable action) {
                this.at = at;
                this.action = action;
            }

            @Override
            public void cancel() {
                cancelled = true;
            }
        }

        final List<Scheduled> queue = new ArrayList<>();
        final Map<UUID, Player> online = new HashMap<>();
        final List<String> failures = new ArrayList<>();
        final List<String> ran = new ArrayList<>();
        long now;
        int mostPending;

        FakeHost() {
            online.put(STEVE, player(STEVE));
            online.put(ALEX, player(ALEX));
        }

        @Override
        public Timeline.Task runLater(Runnable action, long delayTicks) {
            final Scheduled task = new Scheduled(now + delayTicks, action);
            queue.add(task);
            mostPending = Math.max(mostPending, pending());
            return task;
        }

        @Override
        public Player onlinePlayer(UUID id) {
            return online.get(id);
        }

        @Override
        public void frameFailed(Player p, String what, Throwable e) {
            failures.add(what + ": " + e.getClass().getSimpleName());
        }

        int pending() {
            return (int) queue.stream().filter(task -> !task.cancelled).count();
        }

        /** Runs everything due up to and including {@code tick}. */
        void runUntil(long tick) {
            while (true) {
                final Scheduled next = queue.stream().filter(task -> !task.cancelled && task.at <= tick)
                        .min(Comparator.comparingLong(task -> task.at)).orElse(null);
                if (next == null) {
                    now = Math.max(now, tick);
                    return;
                }
                queue.remove(next);
                now = next.at;
                next.action.run();
            }
        }

        void runAll() {
            runUntil(Long.MAX_VALUE / 2);
        }

        /** An action that records its name and the tick it ran on. */
        java.util.function.Consumer<Player> record(String name) {
            return player -> ran.add(name + "@" + now);
        }
    }

    private static Player player(UUID id) {
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[] {Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> id;
                    case "hashCode" -> id.hashCode();
                    case "equals" -> proxy == args[0];
                    case "toString" -> "Player " + id;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private final FakeHost host = new FakeHost();

    @AfterEach
    void stopEverything() {
        Timeline.stopAll();
    }

    @Test
    void framesRunOnTheirTicksInTheOrderTheyWereAdded() {
        new Timeline(host, STEVE, "test")
                .at(10, host.record("last"))
                .at(0, host.record("first"))
                .at(3, host.record("third"))
                .at(3, host.record("fourth"))
                .at(0, host.record("second"))
                .start();

        host.runAll();

        assertEquals(List.of("first@0", "second@0", "third@3", "fourth@3", "last@10"), host.ran);
        assertFalse(Timeline.isRunning(STEVE), "A finished timeline lets go of the player");
    }

    /** The point of a Timeline: one queued task per animation, not one per frame. */
    @Test
    void onlyOneTaskIsQueuedAtATime() {
        final Timeline timeline = new Timeline(host, STEVE, "test");
        for (int tick = 0; tick < 40; tick++) {
            timeline.at(tick * 3L, host.record("frame" + tick));
        }
        timeline.start();

        host.runAll();

        assertEquals(1, host.mostPending);
        assertEquals(40, host.ran.size());
    }

    @Test
    void anEmptyTimelineChangesNothing() {
        new Timeline(host, STEVE, "first").at(5, host.record("first")).start();

        new Timeline(host, STEVE, "empty").start();
        host.runAll();

        assertEquals(List.of("first@5"), host.ran, "Starting an empty timeline mustn't stop the one playing");
    }

    @Test
    void startingAnotherTimelineForThePlayerReplacesTheFirst() {
        new Timeline(host, STEVE, "old").at(0, host.record("old")).at(5, host.record("old-late")).start();
        host.runUntil(2);

        new Timeline(host, STEVE, "new").at(1, host.record("new")).start();
        host.runAll();

        assertEquals(List.of("old@0", "new@3"), host.ran);
    }

    @Test
    void playersHaveTheirOwnTimelines() {
        new Timeline(host, STEVE, "steve").at(2, host.record("steve")).start();
        new Timeline(host, ALEX, "alex").at(1, host.record("alex")).start();

        host.runAll();

        assertEquals(List.of("alex@1", "steve@2"), host.ran);
    }

    @Test
    void stoppingCancelsWhatsQueued() {
        new Timeline(host, STEVE, "test").at(0, host.record("first")).at(5, host.record("second")).start();
        host.runUntil(0);

        Timeline.stop(STEVE);
        host.runAll();

        assertEquals(List.of("first@0"), host.ran);
        assertEquals(0, host.pending());
        assertFalse(Timeline.isRunning(STEVE));
    }

    /** ie: a frame whose action ends the animation, like a forced clear. */
    @Test
    void aFrameThatStopsItsTimelineEndsItThere() {
        new Timeline(host, STEVE, "test")
                .at(0, player -> Timeline.stop(STEVE))
                .at(0, host.record("same tick"))
                .at(5, host.record("later"))
                .start();

        host.runAll();

        assertEquals(List.of(), host.ran);
        assertFalse(Timeline.isRunning(STEVE));
    }

    /**
     * ie: a PlayerFortuneEndEvent listener that starts a clear. The rest of the old animation's
     * tick used to run anyway and draw over the new one's first frame.
     */
    @Test
    void aFrameThatStartsANewAnimationHandsOverStraightAway() {
        new Timeline(host, STEVE, "old")
                .at(0, player -> new Timeline(host, STEVE, "new").at(1, host.record("new")).start())
                .at(0, host.record("old same tick"))
                .at(4, host.record("old later"))
                .start();

        host.runAll();

        assertEquals(List.of("new@1"), host.ran);
        assertFalse(Timeline.isRunning(STEVE));
    }

    @Test
    void aFrameThatThrowsStopsTheTimelineAndIsReportedOnce() {
        new Timeline(host, STEVE, "'test' clear animation")
                .at(0, host.record("first"))
                .at(2, player -> {
                    throw new IllegalStateException("broken");
                })
                .at(2, host.record("same tick"))
                .at(4, host.record("later"))
                .start();

        host.runAll();

        assertEquals(List.of("first@0"), host.ran);
        assertEquals(List.of("'test' clear animation: IllegalStateException"), host.failures);
        assertFalse(Timeline.isRunning(STEVE));
    }

    /** A method this server version doesn't have throws an Error, not an Exception. */
    @Test
    void aMissingMethodIsHandledLikeAnyOtherFailure() {
        new Timeline(host, STEVE, "fortune animation")
                .at(0, player -> {
                    throw new NoSuchMethodError("TextComponent.fromLegacy");
                })
                .at(1, host.record("later"))
                .start();

        host.runAll();

        assertEquals(List.of("fortune animation: NoSuchMethodError"), host.failures);
        assertEquals(List.of(), host.ran);
        assertFalse(Timeline.isRunning(STEVE));
    }

    @Test
    void framesStopWhenThePlayerLeaves() {
        new Timeline(host, STEVE, "test").at(0, host.record("first")).at(5, host.record("second")).start();
        host.runUntil(0);

        host.online.remove(STEVE);
        host.runAll();

        assertEquals(List.of("first@0"), host.ran);
        assertFalse(Timeline.isRunning(STEVE));
        assertTrue(host.failures.isEmpty());
    }

    @Test
    void framesGetThePlayerTheyBelongTo() {
        final List<Player> seen = new ArrayList<>();
        new Timeline(host, STEVE, "test").at(0, seen::add).start();

        host.runAll();

        assertSame(host.online.get(STEVE), seen.get(0));
    }

    @Test
    void stopAllCancelsEveryone() {
        new Timeline(host, STEVE, "steve").at(3, host.record("steve")).start();
        new Timeline(host, ALEX, "alex").at(3, host.record("alex")).start();

        Timeline.stopAll();
        host.runAll();

        assertEquals(List.of(), host.ran);
        assertFalse(Timeline.isRunning(STEVE));
        assertFalse(Timeline.isRunning(ALEX));
    }

    @Test
    void aLongPauseIsOneWait() {
        new Timeline(host, STEVE, "test").at(0, host.record("start")).at(1200, host.record("a minute on")).start();

        host.runUntil(1199);
        assertEquals(List.of("start@0"), host.ran);
        assertEquals(1, host.pending());

        host.runAll();
        assertEquals(List.of("start@0", "a minute on@1200"), host.ran);
    }
}
