package com.zachduda.animatedinventory;

import java.util.Collections;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class GUI implements Listener {

    static final String TITLE = "§8Are you §lsure§r§8?";

    private static final Main plugin = Main.getPlugin(Main.class);

    private static final int SIZE = 27;
    private static final int YES_SLOT = 12;
    private static final int NO_SLOT = 14;

    /**
     * Marks the confirm menu as ours.
     *
     * Clicks used to be matched on the window title alone, so any inventory a
     * player could name - a renamed shulker box, for instance - would be treated
     * as the confirm prompt and could trigger a real clear.
     */
    private static final class ConfirmHolder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static ItemStack button(Material material, String name, String lore) {
        final ItemStack item = new ItemStack(material);
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(Collections.singletonList(lore));
            item.setItemMeta(meta);
        }
        return item;
    }

    static void confirmGUI(Player p) {
        final ConfirmHolder holder = new ConfirmHolder();
        final Inventory gui = Bukkit.getServer().createInventory(holder, SIZE, TITLE);
        holder.inventory = gui;

        // The filler used to have its display name set on a detached meta that was
        // never written back, so every pane showed its vanilla item name.
        final ItemStack filler = new ItemStack(Material.WHITE_STAINED_GLASS_PANE);
        final ItemMeta fillerMeta = filler.getItemMeta();
        if (fillerMeta != null) {
            fillerMeta.setDisplayName("§f ");
            filler.setItemMeta(fillerMeta);
        }

        for (int slot = 0; slot < SIZE; slot++) {
            gui.setItem(slot, filler);
        }

        gui.setItem(YES_SLOT, button(Material.LIME_CONCRETE, "§a§lYES", "§fClear my inventory."));
        gui.setItem(NO_SLOT, button(Material.RED_CONCRETE, "§c§lNO", "§fKeep my inventory."));

        p.openInventory(gui);
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_XYLOPHONE, 2.0F, 2.0F);
    }

    @EventHandler
    public void inventoryClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof ConfirmHolder)) {
            return;
        }

        e.setCancelled(true);

        if (!(e.getWhoClicked() instanceof Player p)) {
            return;
        }

        final ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) {
            return;
        }

        if (e.getSlot() == YES_SLOT && clicked.getType() == Material.LIME_CONCRETE) {
            p.closeInventory();
            // Re-check: the config could have been reloaded, or a cooldown could
            // have started, while the prompt sat open.
            if (plugin.canClear(p)) {
                Clear.go(p);
            }
            return;
        }

        if (e.getSlot() == NO_SLOT && clicked.getType() == Material.RED_CONCRETE) {
            p.closeInventory();
            plugin.pop(p);
            Msgs.sendBar(p, "&c&lClear Canceled. &fYour inventory won't be cleared.");
        }
    }
}
