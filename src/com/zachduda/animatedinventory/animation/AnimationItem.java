package com.zachduda.animatedinventory.animation;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * An item a clear animation draws on the hotbar.
 *
 * @param name display name with &amp; codes, or null for none
 * @param lore lines with &amp; codes, possibly empty
 */
public record AnimationItem(Material material, String name, List<String> lore, int amount) {

    /**
     * Shown when no name is set. It's only color codes, so selecting the slot mid-animation
     * pops up an empty label instead of the item's vanilla name.
     */
    static final String BLANK_NAME = "§a§e";

    public ItemStack build() {
        final ItemStack item = new ItemStack(material, amount);
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name == null ? BLANK_NAME : color(name));
            if (!lore.isEmpty()) {
                final List<String> lines = new ArrayList<>(lore.size());
                for (String line : lore) {
                    lines.add(color(line));
                }
                meta.setLore(lines);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private static String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
