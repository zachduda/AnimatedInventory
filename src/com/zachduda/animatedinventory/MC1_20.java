package com.zachduda.animatedinventory;

import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.zachduda.animatedinventory.api.PlayerFortuneEndEvent;
import com.zachduda.animatedinventory.api.PlayerFortuneEvent;

public class MC1_20 implements Listener {
    private static final Main plugin = Main.getPlugin(Main.class);

    /** Shared empty stack. Inventory#setItem copies, so one instance is enough. */
    private static final ItemStack AIR = new ItemStack(Material.AIR);

    /** Hotbar width. Every animation plays across these nine slots. */
    private static final int HOTBAR = 9;

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

    private static ItemStack blank(Material material) {
        return named(material, "§a§e");
    }

    private static ItemStack fortuneItem(String path, Material material) {
        return named(material, ChatColor.translateAlternateColorCodes('&',
                Objects.requireNonNull(plugin.getConfig().getString(path))));
    }

    static void putTokenItem(Player p) {
        p.getInventory().setItem(22, Settings.token);
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
                    online.getInventory().clear();
                    plugin.getLogger().info("Attempted to restore " + online.getName()
                            + "'s inventory. (They were in the middle of a fortune)");
                    Cooldowns.activefortune.remove(online.getUniqueId());
                    Msgs.sendPrefix(online, "§c§lSorry! §fThe plugin was reloaded, we have ended your fortune.");
                    plugin.bass(online);
                    try {
                        plugin.loadInv(online);
                    } catch (Exception e) {
                        plugin.getLogger().warning("ERROR! Couldn't restore " + online.getName()
                                + "'s inventory on plugin disable!");
                    }
                    plugin.deleteInv(online);
                }

                if (Cooldowns.isClearing(online) || hasTokenItem(online)) {
                    online.getInventory().clear();
                    Msgs.sendPrefix(online, "§c§lSorry! §fThe plugin was reloaded, we force cleared your inventory.");
                    plugin.bass(online);
                    Cooldowns.active.remove(online.getUniqueId());
                    plugin.getLogger().info("Attempted to clear " + online.getName()
                            + "'s inventory. (They were in the middle of clearing.)");
                }
            }
        } catch (Exception err) {
            plugin.getLogger().warning("Couldn't search player inventories on shutdown. Did you change the .jar?");
            plugin.debugError(err);
        }
    }

    public static void fortune(final Player p) {
        final PlayerFortuneEvent pfe = new PlayerFortuneEvent(p);
        Bukkit.getPluginManager().callEvent(pfe);
        if (pfe.isCancelled()) {
            return;
        }

        plugin.saveInv(p);

        final ItemStack wgp = named(Material.WHITE_STAINED_GLASS_PANE, "§7§l§m---");
        final ItemStack one = fortuneItem("features.fortunes.yes-block.name", Material.LIME_CONCRETE);
        final ItemStack two = fortuneItem("features.fortunes.no-block.name", Material.RED_CONCRETE);

        plugin.pop(p);
        for (int slot = 0; slot < HOTBAR; slot++) {
            p.getInventory().setItem(slot, wgp);
        }
        p.getInventory().setItem(2, one);
        p.getInventory().setItem(6, two);

        final int roll = ThreadLocalRandom.current().nextInt(100);
        final boolean lucky = roll <= Settings.goodLuck;

        if (Settings.debug) {
            plugin.getLogger().info("[Debug] Final Int Pick: " + roll);
            plugin.getLogger().info("[Debug] For Yes the # must be less than or equal to "
                    + Settings.goodLuck + ". Otherwise, it's no. Max Number: 100");
        }

        final String spin = "§f§l§o" + plugin.getConfig().getString("features.fortunes.spin-message");
        final String spinAlt = "§7§l§o" + plugin.getConfig().getString("features.fortunes.spin-message");
        final String result = plugin.getConfig().getString(
                lucky ? "features.fortunes.result.-yes" : "features.fortunes.result.-no");
        final String doneMsg = ChatColor.translateAlternateColorCodes('&',
                Objects.requireNonNull(plugin.getConfig().getString("features.fortunes.done-msg", "&a&lDone!")));

        final Timeline timeline = new Timeline(plugin, p);

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

        final PlayerFortuneEndEvent pfee = new PlayerFortuneEndEvent(p, lucky);
        Bukkit.getPluginManager().callEvent(pfee);

        if (Settings.notifyConsole) {
            plugin.getLogger().info(p.getName() + " got '" + (lucky ? "YES" : "NO") + "' on their fortune.");
        }

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
            Msgs.sendBar(player, doneMsg);
        });

        timeline.start();
    }

    //_______________________________________________________ ANIMATION 1 (Panes)
    public static void animation1(Player p) {
        final ItemStack glass = blank(Material.WHITE_STAINED_GLASS_PANE);

        putTokenItem(p);

        final Timeline timeline = new Timeline(plugin, p);

        // Panes sweep in left to right, then back out right to left.
        for (int i = 0; i < HOTBAR; i++) {
            final int slot = i;
            timeline.at(i, player -> {
                player.getInventory().setItem(slot, glass);
                hold(player, Math.min(slot + 1, HOTBAR - 1));
                if (slot == 1 || slot == 4 || slot == 7) {
                    plugin.clearingsound(player);
                }
            });
        }

        for (int i = 0; i < HOTBAR; i++) {
            final int slot = (HOTBAR - 1) - i;
            final long tick = HOTBAR + i;
            timeline.at(tick, player -> {
                player.getInventory().setItem(slot, AIR);
                if (slot >= 5) {
                    hold(player, slot - 1);
                }
                if (slot == 8) {
                    plugin.clearingsound(player);
                }
            });
        }

        timeline.at(17L, Particlez::colorParticle);
        finishOn(timeline, 17L);
        timeline.start();
    }

    //_______________________________________________________ ANIMATION 2 (Rainbow)
    public static void animation2(Player p) {
        final ItemStack red = blank(Material.RED_WOOL);
        final ItemStack orange = blank(Material.ORANGE_WOOL);
        final ItemStack yellow = blank(Material.YELLOW_WOOL);
        final ItemStack green = blank(Material.GREEN_WOOL);
        final ItemStack blue = blank(Material.BLUE_WOOL);
        final ItemStack purple = blank(Material.PURPLE_WOOL);

        // Each frame is the previous one scrolled right by a slot.
        final ItemStack[][] frames = {
                {red, orange, yellow, green, blue, purple, red, orange, yellow},
                {yellow, red, orange, yellow, green, blue, purple, red, orange},
                {orange, yellow, red, orange, yellow, green, blue, purple, red}
        };

        putTokenItem(p);

        final Timeline timeline = new Timeline(plugin, p);
        for (int i = 0; i < frames.length; i++) {
            final ItemStack[] frame = frames[i];
            timeline.at(i * 10L, player -> {
                plugin.clearingsound(player);
                for (int slot = 0; slot < HOTBAR; slot++) {
                    player.getInventory().setItem(slot, frame[slot]);
                }
            });
        }

        timeline.at(35L, Particlez::colorParticle);
        finishOn(timeline, 35L);
        timeline.start();
    }

    //_______________________________________________________ ANIMATION 3 (TNT)
    public static void animation3(Player p) {
        final ItemStack powder = blank(Material.GUNPOWDER);
        final ItemStack tntblock = blank(Material.TNT);
        final ItemStack cart = blank(Material.TNT_MINECART);
        final ItemStack glass = blank(Material.ORANGE_STAINED_GLASS_PANE);

        putTokenItem(p);
        p.getInventory().setItem(0, cart);

        final Timeline timeline = new Timeline(plugin, p);

        // The cart rolls along the hotbar leaving a gunpowder trail behind it.
        timeline.at(4L, player -> {
            player.getInventory().setItem(1, cart);
            player.getInventory().setItem(0, powder);
            plugin.tntmovesound(player);
        });
        timeline.at(8L, player -> {
            player.getInventory().setItem(2, cart);
            player.getInventory().setItem(1, powder);
        });
        timeline.at(12L, player -> {
            player.getInventory().setItem(3, cart);
            player.getInventory().setItem(2, powder);
            player.getInventory().setItem(0, AIR);
        });
        timeline.at(16L, player -> {
            player.getInventory().setItem(4, cart);
            player.getInventory().setItem(3, powder);
            player.getInventory().setItem(1, AIR);
        });
        timeline.at(20L, player -> player.getInventory().setItem(2, AIR));
        timeline.at(25L, player -> {
            player.getInventory().setItem(3, AIR);
            player.getInventory().setItem(4, tntblock);
            plugin.tntmovesoundstop(player);
            plugin.tntplacesound(player);
            Msgs.sendBar(player, plugin.getConfig().getString("features.clearing.progress-msg"));
        });

        // Then it detonates outwards from the middle.
        for (int i = 0; i < 4; i++) {
            final int left = 3 - i;
            final int right = 5 + i;
            final boolean first = i == 0;
            timeline.at(35L + i, player -> {
                player.getInventory().setItem(left, glass);
                player.getInventory().setItem(right, glass);
                if (first) {
                    plugin.boomsound(player);
                    Particlez.explosionParticle(player);
                }
            });
        }

        finishOn(timeline, 45L);
        timeline.start();
    }

    //_______________________________________________________ ANIMATION 4 (Water)
    public static void animation4(Player p) {
        final ItemStack deep = blank(Material.BLUE_STAINED_GLASS_PANE);
        final ItemStack mid = blank(Material.CYAN_STAINED_GLASS_PANE);
        final ItemStack shallow = blank(Material.LIGHT_BLUE_STAINED_GLASS_PANE);
        final ItemStack fullBucket = blank(Material.WATER_BUCKET);
        final ItemStack emptyBucket = blank(Material.BUCKET);

        putTokenItem(p);

        final Timeline timeline = new Timeline(plugin, p);

        timeline.at(0L, player -> {
            player.getInventory().setItem(4, emptyBucket);
            hold(player, 4);
            player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_GOLD, 5.0f, 0.1f);
            player.playSound(player.getLocation(), Sound.ENTITY_CHICKEN_EGG, 5.0f, 0.1f);
        });
        timeline.at(20L, player -> {
            player.getInventory().setItem(4, fullBucket);
            player.playSound(player.getLocation(), Sound.ITEM_BUCKET_FILL, 5.0f, 1.4f);
        });

        // Water floods outwards from the middle, deepening as it goes.
        flood(timeline, 24L, 2L, shallow, 24L, 30L);
        flood(timeline, 32L, 2L, mid, 38L, -1L);
        flood(timeline, 40L, 2L, deep, -1L, -1L);

        timeline.at(55L, player -> {
            player.getInventory().setItem(4, emptyBucket);
            player.playSound(player.getLocation(), Sound.BLOCK_WET_GRASS_PLACE, 5.0f, 0.4f);
        });

        // Then it drains back out the same way.
        flood(timeline, 60L, 2L, mid, -1L, -1L);
        flood(timeline, 70L, 2L, shallow, -1L, -1L);
        flood(timeline, 77L, 1L, AIR, -1L, -1L);

        timeline.at(40L, player -> Msgs.sendBar(player,
                plugin.getConfig().getString("features.clearing.progress-msg")));
        timeline.at(60L, player -> Msgs.sendBar(player,
                plugin.getConfig().getString("features.clearing.progress-msg")));
        timeline.at(85L, Particlez::waterParticle);

        finishOn(timeline, 85L);
        timeline.start();
    }

    /**
     * Fills the hotbar outwards from the middle pair, one step every
     * {@code step} ticks, optionally playing the water ambience on two of them.
     */
    private static void flood(Timeline timeline, long start, long step, ItemStack item,
                              long ambientA, long ambientB) {
        for (int i = 0; i < 4; i++) {
            final int left = 3 - i;
            final int right = 5 + i;
            final long tick = start + (i * step);
            final boolean ambient = tick == ambientA || tick == ambientB;
            timeline.at(tick, player -> {
                player.getInventory().setItem(left, item);
                player.getInventory().setItem(right, item);
                if (ambient) {
                    plugin.waterAmb(player);
                }
            });
        }
    }

    //_______________________________________________________ ANIMATION 5 (Fireball)
    public static void animation5(Player p) {
        final ItemStack dispenser = blank(Material.DISPENSER);
        final ItemStack charge = blank(Material.FIRE_CHARGE);

        putTokenItem(p);

        p.getInventory().setItem(0, dispenser);
        plugin.despsound(p);

        final Timeline timeline = new Timeline(plugin, p);

        // A single charge flies from the dispenser out along the hotbar.
        for (int slot = 1; slot < HOTBAR; slot++) {
            final int target = slot;
            final long tick = 15L + ((slot - 1) * 2L);
            timeline.at(tick, player -> {
                if (target == 1) {
                    plugin.fireballshootsound(player);
                } else {
                    player.getInventory().setItem(target - 1, AIR);
                }
                player.getInventory().setItem(target, charge);
                Particlez.fireballParticle(player);
                if (target == 6) {
                    Msgs.sendBar(player, plugin.getConfig().getString("features.clearing.progress-msg"));
                }
            });
        }

        timeline.at(31L, player -> player.getInventory().setItem(8, AIR));
        finishOn(timeline, 36L);
        timeline.start();
    }

    private static void hold(Player p, int slot) {
        if (Settings.slotSwitching) {
            p.getInventory().setHeldItemSlot(slot);
        }
    }

    /**
     * Adds the wipe-and-announce step four ticks after the last visual frame,
     * matching the delay the old cleardone() scheduled for itself.
     */
    private static void finishOn(Timeline timeline, long lastFrame) {
        timeline.at(lastFrame + 4L, plugin::finishClear);
    }
}
