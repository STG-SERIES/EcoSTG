package com.ecostg.paper.util;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class ItemUtil {

    private ItemUtil() {
    }

    public static ItemStack named(Material material, Component name, List<Component> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(name);
        if (lore != null) {
            meta.lore(lore);
        }
        stack.setItemMeta(meta);
        return stack;
    }

    public static ItemStack named(Material material, String miniName, List<String> miniLore) {
        List<Component> lore = new ArrayList<>();
        if (miniLore != null) {
            for (String line : miniLore) {
                lore.add(Messages.mm(line));
            }
        }
        return named(material, Messages.mm(miniName), lore);
    }

    public static ItemStack filler() {
        return named(Material.GRAY_STAINED_GLASS_PANE, "<gray> ", List.of());
    }
}
