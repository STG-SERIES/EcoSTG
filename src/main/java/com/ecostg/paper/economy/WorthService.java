package com.ecostg.paper.economy;

import com.ecostg.paper.EcoSTGPlugin;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.EnumMap;
import java.util.Map;

public final class WorthService {

    private final Map<Material, Double> prices = new EnumMap<>(Material.class);

    public WorthService(EcoSTGPlugin plugin) {
        File file = new File(plugin.getDataFolder(), "worth.yml");
        FileConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        var section = yaml.getConfigurationSection("worth");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                Material material = Material.matchMaterial(key);
                if (material != null && material.isItem()) {
                    prices.put(material, section.getDouble(key));
                }
            }
        }
        plugin.getLogger().info("Loaded " + prices.size() + " worth prices.");
    }

    public double unitPrice(Material material) {
        return prices.getOrDefault(material, -1.0);
    }

    public boolean isSellable(Material material) {
        return unitPrice(material) >= 0;
    }

    public double valueOf(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return 0;
        }
        double unit = unitPrice(stack.getType());
        if (unit < 0) {
            return 0;
        }
        return unit * stack.getAmount();
    }

    public double valueOf(ItemStack[] stacks) {
        double total = 0;
        if (stacks == null) {
            return 0;
        }
        for (ItemStack stack : stacks) {
            total += valueOf(stack);
        }
        return total;
    }
}
