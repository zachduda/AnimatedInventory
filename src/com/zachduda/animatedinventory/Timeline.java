package com.zachduda.animatedinventory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/**
 * A cancellable sequence of timed frames belonging to one player.
 *
 * Every animation used to queue each of its frames up front with
 * scheduleSyncDelayedTask. A fortune alone queued around forty of them, and each
 * one held a lambda capturing the Player for the full ten seconds of the
 * animation. Worse, nothing could stop them: if the player logged out, the world
 * changed, the plugin was disabled or an admin ran /ai glitched, the queued
 * frames still fired and wrote glass panes into whatever inventory the Player
 * object pointed at.
 *
 * A Timeline keeps exactly one scheduled task alive at a time, chaining to the
 * next frame as each one runs, and gives the rest of the plugin a handle it can
 * cancel. Frame timings are unchanged: a frame declared at tick N still runs N
 * ticks after the animation starts.
 */
final class Timeline {

    /** A scheduled frame that can still be called off. */
    @FunctionalInterface
    interface Task {
        void cancel();
    }

    /**
     * What a timeline needs from the server. {@link #bukkit} is the real one; the tests drive
     * a timeline tick by tick with their own.
     */
    interface Host {
        /** Runs {@code action} on the main thread after {@code delayTicks} ticks. */
        Task runLater(Runnable action, long delayTicks);

        /** The player, or null once they're offline. */
        Player onlinePlayer(UUID id);

        /** A frame threw. The timeline has already been stopped. */
        void frameFailed(Player p, String what, Throwable e);
    }

    static Host bukkit(Main plugin) {
        return new Host() {
            @Override
            public Task runLater(Runnable action, long delayTicks) {
                final BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, action, delayTicks);
                return task::cancel;
            }

            @Override
            public Player onlinePlayer(UUID id) {
                final Player p = Bukkit.getPlayer(id);
                return p != null && p.isOnline() ? p : null;
            }

            @Override
            public void frameFailed(Player p, String what, Throwable e) {
                plugin.frameError(p, what, e);
            }
        };
    }

    private static final Map<UUID, Timeline> RUNNING = new HashMap<>();

    private record Frame(long tick, Consumer<Player> action) {
    }

    private final Host host;
    private final UUID id;
    /** What's playing, for error messages. ie: "'explosion' clear animation" */
    private final String what;
    private final List<Frame> frames = new ArrayList<>();

    private Task task;
    private int next;
    private long cursor;

    Timeline(Main plugin, Player p, String what) {
        this(bukkit(plugin), p.getUniqueId(), what);
    }

    Timeline(Host host, UUID id, String what) {
        this.host = host;
        this.id = id;
        this.what = what;
    }

    /** Queues an action to run {@code tick} ticks after the timeline starts. */
    Timeline at(long tick, Consumer<Player> action) {
        frames.add(new Frame(tick, action));
        return this;
    }

    /**
     * Starts the timeline, replacing whatever was already running for this
     * player. Returns immediately; frames run on the main thread.
     */
    void start() {
        if (frames.isEmpty()) {
            return;
        }
        // A stable sort: frames on the same tick keep the order they were added in.
        frames.sort(Comparator.comparingLong(Frame::tick));
        stop(id);
        RUNNING.put(id, this);
        schedule();
    }

    private void schedule() {
        final long delay = Math.max(0L, frames.get(next).tick() - cursor);
        task = host.runLater(this::fire, delay);
    }

    private boolean isLive() {
        return RUNNING.get(id) == this;
    }

    private void fire() {
        final Player p = host.onlinePlayer(id);
        if (p == null) {
            // They left mid-animation. Main#onLeave already tidied their
            // inventory, so just drop the rest of the frames.
            release();
            return;
        }

        cursor = frames.get(next).tick();
        try {
            // A frame can cancel this timeline - /ai glitched and a forced clear both go
            // through stop(), and a listener of an event a frame fires can start a new
            // animation. Once that happens, the frames left on this tick belong to an
            // animation that's over, so they mustn't draw over its replacement.
            while (next < frames.size() && frames.get(next).tick() == cursor && isLive()) {
                frames.get(next++).action().accept(p);
            }
        } catch (Exception | LinkageError e) {
            // LinkageError too: a call this server version doesn't have would otherwise escape
            // the scheduler with this timeline still registered, leaving the player stuck.
            release();
            host.frameFailed(p, what, e);
            return;
        }

        if (next < frames.size() && isLive()) {
            schedule();
        } else {
            release();
        }
    }

    /** Forgets this timeline without touching the scheduler. */
    private void release() {
        task = null;
        RUNNING.remove(id, this);
    }

    /** Cancels the animation running for this player, if any. */
    static void stop(UUID id) {
        final Timeline running = RUNNING.remove(id);
        if (running != null && running.task != null) {
            running.task.cancel();
            running.task = null;
        }
    }

    static void stop(Player p) {
        stop(p.getUniqueId());
    }

    static boolean isRunning(UUID id) {
        return RUNNING.containsKey(id);
    }

    /** Cancels every running animation. Used on plugin disable. */
    static void stopAll() {
        for (Timeline running : new ArrayList<>(RUNNING.values())) {
            if (running.task != null) {
                running.task.cancel();
                running.task = null;
            }
        }
        RUNNING.clear();
    }
}
