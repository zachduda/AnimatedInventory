package com.zach_attack.inventory;

import java.util.ArrayList;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class GUI implements Listener {
	static String title = "§8Are you sure?";
    static Main plugin = Main.getPlugin(Main.class);
    
    static void confirmGUI(Player p) {
            Inventory gui = Bukkit.getServer().createInventory(p, 27, title);
                ItemStack mangos = new ItemStack(Material.LIME_CONCRETE);
                ItemMeta mangosm = mangos.getItemMeta();
                ArrayList < String > lore = new ArrayList < String > ();

                lore.add("§fClear my inventory.");
                mangosm.setLore(lore);
                mangosm.setDisplayName("§r§a§lYES");
                mangos.setItemMeta(mangosm);

                gui.setItem(12, mangos);
                lore.clear();

                ItemStack streakitem = new ItemStack(Material.RED_CONCRETE);

                ItemMeta streakm = streakitem.getItemMeta();
                ArrayList <String> slore = new ArrayList < String > ();

                slore.add("§fKeep my inventory.");

                streakm.setLore(slore);
                streakm.setDisplayName("§r§c§lNO");

                streakitem.setItemMeta(streakm);
            gui.setItem(14, streakitem);
            slore.clear();

            ItemStack blank0 = new ItemStack(Material.WHITE_STAINED_GLASS_PANE);
            ItemMeta b0m = blank0.getItemMeta();
            b0m.setDisplayName("§f ");

            for (int slot = 0; slot < gui.getSize(); slot++) {
                if (gui.getItem(slot) == null) {
                    gui.setItem(slot, blank0);
                }
            }
            p.openInventory(gui);
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_XYLOPHONE, 2.0F, 2.0F);

    }
    
    @EventHandler
    private void inventoryClick(InventoryClickEvent e) {

        Player p = (Player) e.getWhoClicked();

        if (e.getView().getTitle().equalsIgnoreCase(title)) {

            e.setCancelled(true);
            if ((e.getCurrentItem() == null) || (e.getCurrentItem().getType().equals(Material.AIR))) {
                return;
            }
            String item = e.getCurrentItem().getItemMeta().getDisplayName();

            if (e.getSlot() == 12) {
                if (item.equalsIgnoreCase("§r§a§lYES")) {
                       p.closeInventory();
                       Clear.go(p);
                    return;
                }
            }

            if (e.getSlot() == 14) {
                if (item.equalsIgnoreCase("§r§c§lNO")) {
                    p.closeInventory();
                    plugin.pop(p);
                    Msgs.sendBar(p, "&c&lClear Canceled. &fYour inventory won't be cleared.");
                    return;
                }
            }
        }
    }
}