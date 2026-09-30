package com.ecostg.paper.economy;

import com.ecostg.paper.EcoSTGPlugin;
import com.ecostg.paper.util.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.ShulkerBox;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * Sell/AH prices from {@code worth.yml}. Defaults are inspired by
 * <a href="https://donut.auction/">donut.auction</a> market ratios (scaled ~÷1000 for EcoSTG).
 */
public final class WorthService {

    /** Bump to force a one-time rewrite of worth.yml defaults (custom edits are overwritten). */
    public static final int WORTH_VERSION = 2;

    private final EcoSTGPlugin plugin;
    private final Map<Material, Double> prices = new EnumMap<>(Material.class);
    private final NamespacedKey decoratedKey;
    private final NamespacedKey loreSizeKey;

    public WorthService(EcoSTGPlugin plugin) {
        this.plugin = plugin;
        this.decoratedKey = new NamespacedKey(plugin, "sell_worth_decorated");
        this.loreSizeKey = new NamespacedKey(plugin, "sell_worth_lore_size");
        reload();
    }

    public void reload() {
        prices.clear();
        File file = new File(plugin.getDataFolder(), "worth.yml");
        FileConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        if (!yaml.isConfigurationSection("worth")) {
            yaml.createSection("worth");
        }
        var section = yaml.getConfigurationSection("worth");
        int fileVersion = yaml.getInt("worth-version", 1);
        boolean refreshAll = fileVersion < WORTH_VERSION;
        boolean changed = refreshAll || fileVersion != WORTH_VERSION;

        if (!refreshAll) {
            for (String key : section.getKeys(false)) {
                Material material = Material.matchMaterial(key);
                if (material != null && material.isItem() && !material.isAir()) {
                    prices.put(material, Math.max(0.0, section.getDouble(key)));
                }
            }
        }

        for (Material material : Material.values()) {
            if (!isPricedMaterial(material)) {
                continue;
            }
            if (refreshAll || !prices.containsKey(material)) {
                double generated = defaultPrice(material);
                prices.put(material, generated);
                section.set(material.name(), generated);
                changed = true;
            }
        }

        if (changed) {
            yaml.set("worth-version", WORTH_VERSION);
            try {
                yaml.save(file);
                if (refreshAll) {
                    plugin.getLogger().info("Rewrote worth.yml to version " + WORTH_VERSION
                            + " (donut.auction-inspired prices).");
                } else {
                    plugin.getLogger().info("Updated worth.yml with prices for all item materials.");
                }
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Could not save worth.yml", e);
            }
        }
        plugin.getLogger().info("Loaded " + prices.size() + " worth prices.");
    }

    private static boolean isPricedMaterial(Material material) {
        if (material == null || !material.isItem() || material.isAir()) {
            return false;
        }
        try {
            if (material.isLegacy()) {
                return false;
            }
        } catch (NoSuchMethodError ignored) {
            // older API
        }
        String name = material.name();
        return !name.startsWith("LEGACY_");
    }

    /**
     * Defaults inspired by donut.auction live ratios (sampled Sep 2026), scaled ~÷1000
     * so EcoSTG stays playable with a 500 starting balance.
     */
    public static double defaultPrice(Material material) {
        if (isBlocked(material)) {
            return 0.0;
        }
        String n = material.name();

        // --- Direct donut.auction anchors (÷1000) ---
        return switch (n) {
            case "ELYTRA" -> 331_000.0;
            case "DRAGON_HEAD", "DRAGON_WALL_HEAD" -> 55_000.0;
            case "NETHERITE_BLOCK" -> 43_600.0;
            case "NETHERITE_INGOT" -> 4_900.0;
            case "NETHERITE_SCRAP" -> 1_100.0;
            case "ANCIENT_DEBRIS" -> 1_380.0;
            case "NETHERITE_SWORD" -> 6_000.0;
            case "NETHERITE_HELMET" -> 5_700.0;
            case "NETHERITE_CHESTPLATE" -> 4_800.0;
            case "NETHERITE_LEGGINGS" -> 5_300.0;
            case "NETHERITE_BOOTS" -> 5_000.0;
            case "NETHERITE_PICKAXE", "NETHERITE_AXE", "NETHERITE_SHOVEL", "NETHERITE_HOE" -> 5_200.0;
            case "PIGLIN_HEAD", "PIGLIN_WALL_HEAD" -> 730.0;
            case "ENCHANTED_GOLDEN_APPLE" -> 1_530.0;
            case "GOLDEN_APPLE" -> 40.0;
            case "TOTEM_OF_UNDYING" -> 70.0;
            case "END_CRYSTAL" -> 33.0;
            case "FIREWORK_ROCKET" -> 0.6;
            case "REDSTONE" -> 0.5;
            case "REDSTONE_BLOCK" -> 2.1;
            case "REPEATER" -> 5.2;
            case "COMPARATOR" -> 13.0;
            case "HOPPER" -> 4.0;
            case "OBSERVER" -> 2.0;
            case "SLIME_BLOCK" -> 12.0;
            case "SLIME_BALL" -> 1.3;
            case "PISTON" -> 4.3;
            case "STICKY_PISTON" -> 5.2;
            default -> estimatedPrice(n);
        };
    }

    private static double estimatedPrice(String n) {
        // Mid/high tier extrapolated from donut netherite / gapple / redstone ladder
        if (n.contains("NETHERITE")) {
            if (n.contains("BLOCK")) {
                return 43_600.0;
            }
            if (n.contains("INGOT")) {
                return 4_900.0;
            }
            return 5_000.0;
        }
        if (n.contains("DIAMOND")) {
            if (n.equals("DIAMOND")) {
                return 280.0;
            }
            if (n.contains("BLOCK")) {
                return 2_500.0;
            }
            if (n.contains("ORE")) {
                return 160.0;
            }
            return 450.0; // tools/armor
        }
        if (n.contains("EMERALD")) {
            if (n.equals("EMERALD")) {
                return 120.0;
            }
            if (n.contains("BLOCK")) {
                return 1_080.0;
            }
            return 60.0;
        }
        if (n.contains("SHULKER")) {
            return n.contains("SHELL") ? 420.0 : 900.0;
        }
        if (n.equals("NETHER_STAR") || n.equals("DRAGON_EGG") || n.equals("HEART_OF_THE_SEA")) {
            return 2_500.0;
        }
        if (n.contains("SPAWN_EGG")) {
            return 150.0;
        }
        if (n.contains("MUSIC_DISC") || n.contains("GOAT_HORN")) {
            return 80.0;
        }
        if (n.contains("HEAD") || n.contains("SKULL")) {
            return 200.0;
        }
        if (n.contains("IRON")) {
            if (n.contains("INGOT")) {
                return 12.0;
            }
            if (n.contains("BLOCK")) {
                return 108.0;
            }
            if (n.contains("ORE") || n.contains("RAW")) {
                return 7.0;
            }
            if (n.contains("NUGGET")) {
                return 1.3;
            }
            return 35.0;
        }
        if (n.contains("GOLD") && !n.contains("GOLDEN_APPLE")) {
            if (n.contains("INGOT")) {
                return 25.0;
            }
            if (n.contains("BLOCK")) {
                return 225.0;
            }
            if (n.contains("ORE") || n.contains("RAW")) {
                return 14.0;
            }
            if (n.contains("NUGGET")) {
                return 2.8;
            }
            return 40.0;
        }
        if (n.contains("COPPER")) {
            if (n.contains("INGOT")) {
                return 4.0;
            }
            if (n.contains("BLOCK")) {
                return 36.0;
            }
            if (n.contains("ORE") || n.contains("RAW")) {
                return 2.5;
            }
            return 3.0;
        }
        if (n.contains("COAL")) {
            return n.contains("BLOCK") ? 3.6 : 0.4;
        }
        if (n.contains("LAPIS")) {
            return n.contains("BLOCK") ? 8.0 : 0.9;
        }
        if (n.contains("QUARTZ")) {
            return n.contains("BLOCK") ? 4.0 : 0.5;
        }
        if (n.contains("AMETHYST")) {
            return n.contains("BLOCK") || n.contains("CLUSTER") ? 6.0 : 1.2;
        }
        if (n.equals("OBSIDIAN") || n.equals("CRYING_OBSIDIAN")) {
            return 15.0;
        }
        if (n.contains("BLAZE") || n.contains("ENDER_PEARL") || n.contains("GHAST") || n.contains("MAGMA_CREAM")) {
            return 18.0;
        }
        if (n.contains("ENDER_CHEST") || n.equals("ENCHANTING_TABLE") || n.equals("ANVIL")
                || n.equals("BEACON") || n.equals("CONDUIT")) {
            return n.equals("BEACON") ? 8_000.0 : 200.0;
        }
        if (n.endsWith("_LOG") || n.endsWith("_STEM") || n.endsWith("_HYPHAE") || n.contains("_WOOD")) {
            return 0.8;
        }
        if (n.endsWith("_PLANKS")) {
            return 0.2;
        }
        if (n.endsWith("_SAPLING") || n.endsWith("_LEAVES") || n.endsWith("_PROPAGULE")) {
            return 0.35;
        }
        if (n.contains("WOOL") || n.contains("CARPET") || n.contains("BANNER") || n.contains("BED")) {
            return 0.8;
        }
        if (n.contains("CONCRETE") || n.contains("TERRACOTTA") || n.contains("GLAZED")) {
            return 0.7;
        }
        if (n.contains("GLASS")) {
            return 0.5;
        }
        if (n.contains("SWORD") || n.contains("AXE") || n.contains("PICKAXE") || n.contains("SHOVEL")
                || n.contains("HOE") || n.contains("HELMET") || n.contains("CHESTPLATE")
                || n.contains("LEGGINGS") || n.contains("BOOTS") || n.contains("HORSE_ARMOR")) {
            if (n.contains("WOODEN") || n.contains("LEATHER")) {
                return 4.0;
            }
            if (n.contains("STONE") || n.contains("CHAINMAIL")) {
                return 12.0;
            }
            if (n.contains("IRON")) {
                return 40.0;
            }
            if (n.contains("GOLD")) {
                return 30.0;
            }
            if (n.contains("DIAMOND")) {
                return 450.0;
            }
            return 25.0;
        }
        if (n.contains("BOAT") || n.contains("MINECART") || n.contains("RAIL")) {
            return 3.0;
        }
        if (n.contains("DOOR") || n.contains("TRAPDOOR") || n.contains("FENCE") || n.contains("STAIRS")
                || n.contains("SLAB") || n.contains("WALL") || n.contains("SIGN")) {
            return 0.5;
        }
        if (n.contains("ORE")) {
            return 8.0;
        }
        if (n.equals("DIRT") || n.equals("COARSE_DIRT") || n.equals("ROOTED_DIRT") || n.equals("MUD")
                || n.equals("NETHERRACK") || n.equals("COBBLESTONE") || n.equals("COBBLED_DEEPSLATE")
                || n.equals("ANDESITE") || n.equals("DIORITE") || n.equals("GRANITE")
                || n.equals("STONE") || n.equals("DEEPSLATE") || n.equals("TUFF") || n.equals("CALCITE")
                || n.equals("SAND") || n.equals("RED_SAND") || n.equals("GRAVEL") || n.equals("CLAY_BALL")
                || n.equals("SNOWBALL") || n.equals("STICK") || n.equals("TORCH")) {
            return switch (n) {
                case "COBBLESTONE", "COBBLED_DEEPSLATE" -> 0.2;
                case "STONE", "DEEPSLATE", "ANDESITE", "DIORITE", "GRANITE", "TUFF", "CALCITE" -> 0.35;
                case "DIRT", "COARSE_DIRT", "ROOTED_DIRT", "MUD", "NETHERRACK" -> 0.15;
                case "SAND", "RED_SAND", "GRAVEL" -> 0.2;
                case "STICK", "TORCH", "SNOWBALL" -> 0.05;
                default -> 0.25;
            };
        }
        if (n.contains("COOKED_") || n.equals("BREAD") || n.equals("COOKIE") || n.equals("PUMPKIN_PIE")
                || n.equals("CAKE") || n.equals("MUSHROOM_STEW") || n.equals("RABBIT_STEW")
                || n.equals("BEETROOT_SOUP") || n.equals("SUSPICIOUS_STEW")) {
            return 2.0;
        }
        if (n.contains("SEEDS") || n.contains("WHEAT") || n.contains("CARROT") || n.contains("POTATO")
                || n.contains("BEETROOT") || n.contains("MELON") || n.contains("PUMPKIN")
                || n.contains("SUGAR") || n.contains("COCOA") || n.contains("NETHER_WART")
                || n.contains("BERRIES") || n.contains("CHORUS") || n.contains("BAMBOO")
                || n.contains("CACTUS") || n.contains("KELP") || n.contains("SEA_PICKLE")) {
            return 0.8;
        }
        if (n.contains("FISH") || n.contains("COD") || n.contains("SALMON") || n.contains("PUFFERFISH")
                || n.contains("TROPICAL")) {
            return 1.2;
        }
        if (n.contains("DYE") || n.contains("INK_SAC")) {
            return 0.6;
        }
        if (n.contains("POTION") || n.contains("TIPPED_ARROW") || n.contains("EXPERIENCE_BOTTLE")) {
            return 15.0;
        }
        if (n.contains("BOOK") || n.contains("ENCHANTED_BOOK") || n.contains("PAPER") || n.contains("MAP")) {
            return n.contains("ENCHANTED") ? 80.0 : 1.0;
        }
        if (n.contains("BUCKET")) {
            return n.equals("BUCKET") ? 10.0 : 18.0;
        }
        int parts = n.split("_").length;
        return Math.max(0.25, Math.min(40.0, parts * 1.5));
    }

    private static boolean isBlocked(Material material) {
        String n = material.name();
        return n.contains("COMMAND_BLOCK")
                || n.equals("STRUCTURE_BLOCK")
                || n.equals("STRUCTURE_VOID")
                || n.equals("JIGSAW")
                || n.equals("BARRIER")
                || n.equals("LIGHT")
                || n.equals("DEBUG_STICK")
                || n.equals("KNOWLEDGE_BOOK")
                || n.equals("BEDROCK")
                || n.equals("END_PORTAL_FRAME")
                || n.equals("SPAWNER")
                || n.equals("TRIAL_SPAWNER")
                || n.equals("VAULT")
                || n.equals("REINFORCED_DEEPSLATE")
                || n.equals("BUDDING_AMETHYST")
                || n.equals("FARMLAND")
                || n.equals("DIRT_PATH")
                || n.contains("PETRIFIED")
                || n.equals("PLAYER_HEAD")
                || n.equals("WRITTEN_BOOK");
    }

    public double unitPrice(Material material) {
        return prices.getOrDefault(material, -1.0);
    }

    public boolean isSellable(Material material) {
        return unitPrice(material) > 0.0;
    }

    public static boolean isShulkerBox(Material material) {
        return material != null && material.name().endsWith("SHULKER_BOX");
    }

    /** Contents of a shulker box item (empty array if not a shulker or empty). */
    public ItemStack[] shulkerContents(ItemStack stack) {
        if (stack == null || !isShulkerBox(stack.getType())) {
            return new ItemStack[0];
        }
        ItemMeta meta = stack.getItemMeta();
        if (!(meta instanceof BlockStateMeta bsm)) {
            return new ItemStack[0];
        }
        if (!(bsm.getBlockState() instanceof ShulkerBox box)) {
            return new ItemStack[0];
        }
        ItemStack[] contents = box.getInventory().getContents();
        return contents == null ? new ItemStack[0] : contents;
    }

    public boolean hasShulkerContents(ItemStack stack) {
        for (ItemStack inner : shulkerContents(stack)) {
            if (inner != null && !inner.getType().isAir()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Worth of an item stack. Shulker boxes include the box price plus every item inside
     * (nested shulkers are valued recursively).
     */
    public double valueOf(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return 0;
        }
        double unit = unitPrice(stack.getType());
        double total = 0;
        if (unit > 0) {
            total += unit * stack.getAmount();
        }
        if (isShulkerBox(stack.getType())) {
            // Filled shulkers do not stack; NBT contents are shared across amount==1
            total += valueOf(shulkerContents(stack));
        }
        return total;
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

    /** Adds a temporary {@code ~ $ X} lore line for the sell GUI (safe to strip later). */
    public void decorateSellLore(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return;
        }
        stripSellLore(stack);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        meta.getPersistentDataContainer().set(loreSizeKey, PersistentDataType.INTEGER, lore.size());
        double value = valueOf(stack);
        if (value > 0) {
            lore.add(Messages.mm("<white>~ <green>$</green> " + Messages.compactNumber(value)));
            if (hasShulkerContents(stack)) {
                lore.add(Messages.mm("<gray>Includes shulker contents"));
            }
        } else {
            lore.add(Messages.mm("<red>Unsellable"));
        }
        meta.lore(lore);
        meta.getPersistentDataContainer().set(decoratedKey, PersistentDataType.BYTE, (byte) 1);
        stack.setItemMeta(meta);
    }

    /**
     * Removes temporary sell-GUI worth lore / PDC so items stack with normal copies again.
     * Also cleans older contaminated stacks that lost the PDC marker but kept the lore.
     */
    public void stripSellLore(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) {
            return;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        var pdc = meta.getPersistentDataContainer();
        boolean marked = pdc.has(decoratedKey, PersistentDataType.BYTE);
        Integer originalSize = pdc.get(loreSizeKey, PersistentDataType.INTEGER);
        List<Component> lore = meta.lore() == null ? null : new ArrayList<>(meta.lore());

        if (marked && originalSize != null && lore != null) {
            if (originalSize <= 0) {
                lore = null;
            } else if (lore.size() > originalSize) {
                lore = new ArrayList<>(lore.subList(0, originalSize));
            }
        } else if (lore != null) {
            lore = stripTrailingWorthLines(lore);
        }

        pdc.remove(decoratedKey);
        pdc.remove(loreSizeKey);
        if (lore == null || lore.isEmpty()) {
            meta.lore(null);
        } else {
            meta.lore(lore);
        }
        stack.setItemMeta(meta);
    }

    private static List<Component> stripTrailingWorthLines(List<Component> lore) {
        List<Component> cleaned = new ArrayList<>(lore);
        var plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText();
        while (!cleaned.isEmpty()) {
            String line = plain.serialize(cleaned.get(cleaned.size() - 1)).trim();
            if (line.startsWith("~") || line.equalsIgnoreCase("Unsellable")
                    || line.equalsIgnoreCase("Includes shulker contents")) {
                cleaned.remove(cleaned.size() - 1);
                continue;
            }
            break;
        }
        return cleaned.isEmpty() ? null : cleaned;
    }

    public ItemStack undecoratedClone(ItemStack stack) {
        if (stack == null) {
            return null;
        }
        ItemStack clone = stack.clone();
        stripSellLore(clone);
        return clone;
    }

    /** Strips sell-worth decoration from the cursor and every inventory slot. */
    public void stripPlayerInventory(org.bukkit.entity.Player player) {
        if (player == null) {
            return;
        }
        stripSellLore(player.getItemOnCursor());
        for (ItemStack stack : player.getInventory().getContents()) {
            stripSellLore(stack);
        }
        for (ItemStack stack : player.getInventory().getArmorContents()) {
            stripSellLore(stack);
        }
        stripSellLore(player.getInventory().getItemInOffHand());
    }
}
