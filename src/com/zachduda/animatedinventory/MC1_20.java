package com.zachduda.animatedinventory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import com.zachduda.animatedinventory.animation.AnimationParticle;
import com.zachduda.animatedinventory.animation.AnimationSound;
import com.zachduda.animatedinventory.animation.ClearAnimation;
import com.zachduda.animatedinventory.animation.Frame;
import com.zachduda.animatedinventory.animation.Placeholders;
import com.zachduda.animatedinventory.api.PlayerFortuneEndEvent;
import com.zachduda.animatedinventory.api.PlayerFortuneEvent;

public class MC1_20 implements Listener {
    private static final Main plugin = Main.getPlugin(Main.class);

    /** Hotbar width. Every animation plays across these nine slots. */
    private static final int HOTBAR = Frame.HOTBAR_SIZE;

    /** Sounds and particles that threw while playing, so they're skipped until the next reload. */
    private static final Set<Object> BROKEN_EFFECTS = new HashSet<>();

    /** The ticks the fortune steps on while the hotbar spins. */
    private static final long[] SPIN_TICKS = {
            6, 15, 18, 20, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37,
            38, 39, 40, 42, 44, 47, 50, 53, 57, 61, 65, 69, 74, 79, 84, 89, 96, 102, 110
    };

    private static ItemStack named(Material material, String displayName) {
        final ItemStack item = new ItemStack(material);
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(displayName);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack fortuneItem(String path, Material material) {
        return named(material, ChatColor.translateAlternateColorCodes('&',
                Objects.requireNonNull(plugin.getConfig().getString(path))));
    }

    static void putTokenItem(Player p) {
        // Only claim the slot when it is already empty. The marker exists so that
        // a crash or a hard reload can still be spotted afterwards, which is not
        // worth overwriting an item for - Cooldowns covers every path where the
        // plugin gets to shut the animation down itself, and Clear.backupInv has
        // already run by this point either way.
        final ItemStack current = p.getInventory().getItem(Settings.TOKEN_SLOT);
        if (current == null || current.getType() == Material.AIR) {
            p.getInventory().setItem(Settings.TOKEN_SLOT, Settings.token);
        }
    }

    static boolean hasTokenItem(Player p) {
        // This used to build the token item but never write the display name back
        // onto the stack, so it matched any plain item of that material - which
        // meant emergencyRemove() wiped the inventory of anyone merely carrying a
        // minecart. Settings.token is the fully-built stack.
        return Settings.token != null && p.getInventory().contains(Settings.token);
    }

    static void emergencyRemove() {
        if (Bukkit.getOnlinePlayers().isEmpty()) {
            return;
        }

        try {
            final ItemStack one = fortuneItem("features.fortunes.yes-block.name", Material.LIME_CONCRETE);
            final ItemStack two = fortuneItem("features.fortunes.no-block.name", Material.RED_CONCRETE);

            for (Player online : Bukkit.getServer().getOnlinePlayers()) {
                Timeline.stop(online);

                if (Cooldowns.isFortune(online)
                        || (online.getInventory().contains(one) && online.getInventory().contains(two))) {
                    plugin.getLogger().info("Attempted to restore " + online.getName()
                            + "'s inventory. (They were in the middle of a fortune)");
                    Cooldowns.unmarkFortune(online.getUniqueId());
                    Msgs.sendPrefix(online, "§c§lSorry! §fThe plugin was reloaded, we have ended your fortune.");
                    plugin.bass(online);
                    if (Cooldowns.peekInventory(online) != null) {
                        try {
                            // loadInv replaces every slot, so there's nothing to clear first.
                            plugin.loadInv(online);
                        } catch (Exception e) {
                            plugin.getLogger().warning("ERROR! Couldn't restore " + online.getName()
                                    + "'s inventory on plugin disable!");
                        }
                    } else {
                        // Spotted by its items after a restart, so the real hotbar is gone with the
                        // old memory. A fortune only ever draws on the hotbar: this used to clear the
                        // whole inventory, wiping the 27 slots and armor it never touched.
                        for (int slot = 0; slot < HOTBAR; slot++) {
                            online.getInventory().setItem(slot, null);
                        }
                    }
                    plugin.deleteInv(online);
                    fireFortuneEnd(online);
                }

                if (Cooldowns.isClearing(online) || hasTokenItem(online)) {
                    online.getInventory().clear();
                    Msgs.sendPrefix(online, "§c§lSorry! §fThe plugin was reloaded, we force cleared your inventory.");
                    plugin.bass(online);
                    Cooldowns.unmarkClearing(online.getUniqueId());
                    plugin.getLogger().info("Attempted to clear " + online.getName()
                            + "'s inventory. (They were in the middle of clearing.)");
                }
            }
        } catch (Exception err) {
            plugin.getLogger().warning("Couldn't search player inventories on shutdown. Did you change the .jar?");
            plugin.debugError(err);
        }
    }

    /**
     * Fires PlayerFortuneEndEvent once, if this player still has a result pending.
     *
     * It used to be fired the instant the fortune started - ten seconds before the
     * player saw the result, and before loadInv() put their real inventory back,
     * so anything a listener handed them was wiped moments later. It now fires
     * once the inventory is restored, on whichever path the fortune ends: normally,
     * or cut short by a quit, a world change, a reload or a frame error.
     */
    static void fireFortuneEnd(Player p) {
        final Boolean lucky = Cooldowns.takeFortuneResult(p);
        if (lucky == null) {
            return;
        }

        if (Settings.notifyConsole) {
            plugin.getLogger().info(p.getName() + " got '" + (lucky ? "YES" : "NO") + "' on their fortune.");
        }

        Bukkit.getPluginManager().callEvent(new PlayerFortuneEndEvent(p, lucky));
    }

    /**
     * Plays a fortune, unless the player is already in an animation or a listener cancels it.
     *
     * The player is marked as having a fortune here, after PlayerFortuneEvent. Every caller used
     * to mark them first, so a cancelled event left them flagged with nothing running: unable to
     * move items, drop or teleport, and with their real inventory wiped on the next plugin reload
     * (emergencyRemove found the flag but no stashed inventory to give back).
     */
    public static void fortune(final Player p) {
        // A second animation on top of this one would stash its half-drawn hotbar as the
        // "real" inventory. The API can get here with its checks turned off.
        if (Cooldowns.isBusy(p)) {
            return;
        }

        final PlayerFortuneEvent pfe = new PlayerFortuneEvent(p);
        Bukkit.getPluginManager().callEvent(pfe);
        if (pfe.isCancelled()) {
            return;
        }

        // Everything that reads the config comes first, so a problem with it can't leave the
        // hotbar half-drawn.
        final ItemStack wgp = named(Material.WHITE_STAINED_GLASS_PANE, "§7§l§m---");
        final ItemStack one = fortuneItem("features.fortunes.yes-block.name", Material.LIME_CONCRETE);
        final ItemStack two = fortuneItem("features.fortunes.no-block.name", Material.RED_CONCRETE);

        // good-luck is the percent chance of a yes: 0 never, 100 always. The roll is 0 to 99,
        // so this was "<=", which made 50 a 51% chance and 0 a 1% chance.
        final int roll = ThreadLocalRandom.current().nextInt(100);
        final boolean lucky = roll < Settings.goodLuck;

        final String spinMessage = plugin.getConfig().getString("features.fortunes.spin-message", "Spinning...");
        final String spin = "§f§l§o" + spinMessage;
        final String spinAlt = "§7§l§o" + spinMessage;
        final String result = plugin.getConfig().getString(
                lucky ? "features.fortunes.result.-yes" : "features.fortunes.result.-no");
        final String doneMsg = ChatColor.translateAlternateColorCodes('&',
                Objects.requireNonNull(plugin.getConfig().getString("features.fortunes.done-msg", "&a&lDone!")));

        if (Settings.debug) {
            plugin.getLogger().info("[Debug] Final Int Pick: " + roll + " (0 to 99)");
            plugin.getLogger().info("[Debug] For Yes the # must be less than " + Settings.goodLuck
                    + ". Otherwise, it's no.");
        }

        Cooldowns.markFortune(p);
        plugin.saveInv(p);

        plugin.pop(p);
        for (int slot = 0; slot < HOTBAR; slot++) {
            p.getInventory().setItem(slot, wgp);
        }
        p.getInventory().setItem(2, one);
        p.getInventory().setItem(6, two);

        final Timeline timeline = new Timeline(plugin, p, "fortune animation");

        // The hotbar walks one slot per step and the message alternates shade, so
        // the whole spin comes off the tick table rather than forty copied blocks.
        for (int i = 0; i < SPIN_TICKS.length; i++) {
            final int slot = i % HOTBAR;
            final String msg = (i % 2 == 0) ? spinAlt : spin;
            timeline.at(SPIN_TICKS[i], player -> {
                plugin.tick(player);
                Msgs.sendBar(player, msg);
                player.getInventory().setHeldItemSlot(slot);
            });
        }

        Cooldowns.setFortuneResult(p, lucky);

        final long finishTick;
        if (lucky) {
            timeline.at(123L, player -> {
                Msgs.sendBar(player, result);
                player.getInventory().setHeldItemSlot(2);
                Particlez.yesParticle(player);
            });
            timeline.at(135L, player -> Msgs.sendBar(player, result));
            finishTick = 185L;
        } else {
            // A "no" keeps spinning past the yes block and lands on slot 6.
            final long[] extraTicks = {115L, 122L, 128L};
            for (int i = 0; i < extraTicks.length; i++) {
                final int slot = 3 + i;
                final String msg = (i % 2 == 0) ? spin : spinAlt;
                timeline.at(extraTicks[i], player -> {
                    plugin.tick(player);
                    player.getInventory().setHeldItemSlot(slot);
                    Msgs.sendBar(player, msg);
                });
            }
            timeline.at(136L, player -> {
                plugin.tick(player);
                player.getInventory().setHeldItemSlot(6);
                Msgs.sendBar(player, result);
                Particlez.noParticle(player);
            });
            timeline.at(150L, player -> Msgs.sendBar(player, result));
            finishTick = 200L;
        }

        timeline.at(finishTick, player -> {
            plugin.clearingsound(player);
            player.getInventory().setHeldItemSlot(0);
            plugin.burp(player);
            try {
                plugin.loadInv(player);
            } catch (Exception e) {
                plugin.getLogger().warning("ERROR! Couldn't load back " + player.getName()
                        + "'s inventory after a fortune.");
                plugin.debugError(e);
            }
            plugin.deleteInv(player);
            Cooldowns.removeFortune(player);
            fireFortuneEnd(player);
            Msgs.sendBar(player, doneMsg);
        });

        timeline.start();
    }

    /**
     * Plays a clear animation from the Animations folder, then wipes the inventory.
     *
     * The items are built before anything in the inventory changes, so a problem building
     * one can't leave the hotbar half-drawn.
     */
    static void clear(Player p, ClearAnimation animation) {
        final Map<Character, ItemStack> items = new HashMap<>();
        animation.items().forEach((symbol, item) -> items.put(symbol, item.build()));

        final Timeline timeline = new Timeline(plugin, p, "'" + animation.name() + "' clear animation");
        long tick = 0;
        for (Frame frame : animation.frames()) {
            tick += frame.delay();
            timeline.at(tick, player -> playFrame(player, animation, frame, items));
        }

        final Frame finish = animation.finish();
        timeline.at(tick + finish.delay(), player -> {
            stopSounds(player, animation, finish);
            playEffects(player, animation, finish);
            plugin.finishClear(player, finish.actionBar() == null ? null : fill(finish.actionBar(), player));
        });
        timeline.start();

        // After start(), which is the only call here that can still fail (the scheduler refuses
        // a plugin that's shutting down). The first frame can't run before next tick, so this
        // is no later than it was, and a failed start no longer leaves the marker behind.
        putTokenItem(p);
    }

    private static void playFrame(Player p, ClearAnimation animation, Frame frame, Map<Character, ItemStack> items) {
        stopSounds(p, animation, frame);

        final String hotbar = frame.hotbar();
        if (hotbar != null) {
            final PlayerInventory inventory = p.getInventory();
            for (int slot = 0; slot < HOTBAR; slot++) {
                final char symbol = hotbar.charAt(slot);
                if (symbol != Frame.KEEP) {
                    inventory.setItem(slot, symbol == Frame.EMPTY ? null : items.get(symbol));
                }
            }
        }

        if (frame.hold() != Frame.NO_HOLD) {
            hold(p, frame.hold());
        }

        playEffects(p, animation, frame);

        if (frame.actionBar() != null) {
            Msgs.sendBar(p, fill(frame.actionBar(), p));
        }
    }

    /** Runs first in a frame, so a frame can cut off a sound and start a new one together. */
    private static void stopSounds(Player p, ClearAnimation animation, Frame frame) {
        for (AnimationSound sound : frame.stopSounds()) {
            safely(animation, sound, () -> sound.stop(p));
        }
    }

    private static void playEffects(Player p, ClearAnimation animation, Frame frame) {
        for (AnimationSound sound : frame.sounds()) {
            safely(animation, sound, () -> sound.play(p));
        }
        if (Settings.useParticles) {
            for (AnimationParticle particle : frame.particles()) {
                safely(animation, particle, () -> particle.spawn(p));
            }
        }
    }

    /** Only reads the config when the text has a placeholder for it; frames call this every tick or two. */
    private static String fill(String text, Player p) {
        return Placeholders.fill(text, p.getName(),
                () -> plugin.getConfig().getString("features.clearing.progress-msg"),
                () -> plugin.getConfig().getString("features.clearing.done-msg"));
    }

    /**
     * Runs one sound or particle, dropping it for good if it throws.
     *
     * The files are checked when they load, but a server can still refuse something at play
     * time. One broken effect shouldn't end the clear, or spam the console every frame.
     */
    private static void safely(ClearAnimation animation, Object effect, Runnable action) {
        // Almost always empty, and hashing a particle record isn't free, so skip it then.
        if (!BROKEN_EFFECTS.isEmpty() && BROKEN_EFFECTS.contains(effect)) {
            return;
        }
        try {
            action.run();
        } catch (RuntimeException | LinkageError e) {
            BROKEN_EFFECTS.add(effect);
            plugin.getLogger().warning("Couldn't play " + effect + " in Animations/" + animation.name()
                    + ".yml, so it's been turned off until the next /ai reload: " + e);
            plugin.debugError(e);
        }
    }

    /** Called on reload, so a fixed animation file gets its effects back. */
    static void forgetBrokenEffects() {
        BROKEN_EFFECTS.clear();
    }

    private static void hold(Player p, int slot) {
        if (Settings.slotSwitching) {
            p.getInventory().setHeldItemSlot(slot);
        }
    }
}
