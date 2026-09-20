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

    private static final Map<UUID, Timeline> RUNNING = new HashMap<>();

    private record Frame(long tick, Consumer<Player> action) {
    }

    private final Main plugin;
    private final UUID id;
    private final List<Frame> frames = new ArrayList<>();

    private BukkitTask task;
    private int next;
    private long cursor;

    Timeline(Main plugin, Player p) {
        this.plugin = plugin;
        this.id = p.getUniqueId();
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
        frames.sort(Comparator.comparingLong(Frame::tick));
        stop(id);
        RUNNING.put(id, this);
        schedule();
    }

    private void schedule() {
        final long delay = Math.max(0L, frames.get(next).tick() - cursor);
        task = Bukkit.getScheduler().runTaskLater(plugin, this::fire, delay);
    }

    private void fire() {
        final Player p = Bukkit.getPlayer(id);
        if (p == null || !p.isOnline()) {
            // They left mid-animation. Main#onLeave already tidied their
            // inventory, so just drop the rest of the frames.
            release();
            return;
        }

        cursor = frames.get(next).tick();
        try {
            while (next < frames.size() && frames.get(next).tick() == cursor) {
                frames.get(next++).action().accept(p);
            }
        } catch (Exception e) {
            release();
            plugin.frameError(p, e);
            return;
        }

        // A frame can cancel this timeline - /ai glitched and a forced clear both
        // go through stop() - so only chain on if we are still the live one.
        if (next < frames.size() && RUNNING.get(id) == this) {
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
