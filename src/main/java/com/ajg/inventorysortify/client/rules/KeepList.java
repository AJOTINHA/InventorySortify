package com.ajg.inventorysortify.client.rules;

import com.ajg.inventorysortify.InventorySortify;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * The inventory filter: what stays in the player's inventory when moving items to a container. Two kinds of entries,
 * either one keeps an item:
 * <ul>
 *     <li>item types, which stay wherever they are in the inventory;</li>
 *     <li>inventory slots, whose contents stay whatever they are (for telling identical items apart).</li>
 * </ul>
 * Stored in {@code config/inventorysortify/keep.txt}: an item id per line, and {@code slot D1} lines using the grid of
 * {@code rules.txt} (rows A–C are the main inventory, row D the hotbar).
 */
public final class KeepList {
    private static final String FILE = "keep.txt";
    private static final String SLOT_PREFIX = "slot ";

    private static Set<Identifier> keptItems;
    /** Inventory slot numbers (0–8 hotbar, 9–35 main inventory). */
    private static Set<Integer> keptSlots;
    private static long stamp = -1;
    private static final long CHECK_INTERVAL_MS = 1000;
    private static long lastCheck;

    private KeepList() {
    }

    /** Whether the stack in the given inventory slot stays in the inventory. */
    public static boolean keeps(int inventorySlot, Item item) {
        return isKept(item) || isSlotKept(inventorySlot);
    }

    public static boolean isKept(Item item) {
        refresh();
        return keptItems.contains(BuiltInRegistries.ITEM.getKey(item));
    }

    public static boolean isSlotKept(int inventorySlot) {
        refresh();
        return keptSlots.contains(inventorySlot);
    }

    public static void toggle(Item item) {
        refresh();
        Identifier id = BuiltInRegistries.ITEM.getKey(item);
        if (!keptItems.remove(id)) {
            keptItems.add(id);
        }
        save();
    }

    public static void toggleSlot(int inventorySlot) {
        refresh();
        if (!keptSlots.remove(inventorySlot)) {
            keptSlots.add(inventorySlot);
        }
        save();
    }

    public static void clearItems() {
        refresh();
        keptItems.clear();
        save();
    }

    public static void clearSlots() {
        refresh();
        keptSlots.clear();
        save();
    }

    /** Kept items that exist in this game (ids of uninstalled mods are kept in the file but skipped here). */
    public static List<Item> items() {
        refresh();
        List<Item> result = new ArrayList<>();
        for (Identifier id : keptItems) {
            BuiltInRegistries.ITEM.getOptional(id).ifPresent(result::add);
        }
        return result;
    }

    /** Grid name ("A1".."D9") of an inventory slot. */
    public static String slotName(int inventorySlot) {
        int grid = inventorySlot < 9 ? 27 + inventorySlot : inventorySlot - 9;
        return String.valueOf((char) ('A' + grid / 9)) + (grid % 9 + 1);
    }

    /** Inventory slot of a grid name, or -1. */
    private static int parseSlot(String name) {
        String n = name.trim().toLowerCase(Locale.ROOT);
        if (n.length() != 2 || n.charAt(0) < 'a' || n.charAt(0) > 'd' || n.charAt(1) < '1' || n.charAt(1) > '9') {
            return -1;
        }
        int grid = (n.charAt(0) - 'a') * 9 + (n.charAt(1) - '1');
        return grid >= 27 ? grid - 27 : grid + 9;
    }

    private static Path file() {
        return SortifyFiles.directory().resolve(FILE);
    }

    private static void refresh() {
        // Checked every frame by the slot marks: look at the file at most once a second.
        long now = System.currentTimeMillis();
        if (keptItems != null && now - lastCheck < CHECK_INTERVAL_MS) {
            return;
        }
        lastCheck = now;
        Path path = file();
        long newStamp = stamp(path);
        if (keptItems != null && newStamp == stamp) {
            return;
        }
        stamp = newStamp;
        keptItems = new LinkedHashSet<>();
        keptSlots = new TreeSet<>();
        if (!Files.exists(path)) {
            return;
        }
        try {
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                if (trimmed.toLowerCase(Locale.ROOT).startsWith(SLOT_PREFIX)) {
                    int slot = parseSlot(trimmed.substring(SLOT_PREFIX.length()));
                    if (slot >= 0 && slot < Inventory.INVENTORY_SIZE) {
                        keptSlots.add(slot);
                    }
                    continue;
                }
                Identifier id = Identifier.tryParse(trimmed);
                if (id != null) {
                    keptItems.add(id);
                }
            }
        } catch (IOException e) {
            InventorySortify.LOGGER.error("Could not read {}", path, e);
        }
    }

    private static void save() {
        Path path = file();
        List<String> lines = new ArrayList<>();
        lines.add("# Inventory Sortify - what stays in your inventory when moving items to a container.");
        lines.add("# An item id per line keeps that item anywhere in the inventory; \"slot D1\" keeps whatever is in that slot");
        lines.add("# (rows A-C are the main inventory, row D the hotbar). Edit it in game with the filter button in your inventory.");
        for (Identifier id : keptItems) {
            lines.add(id.toString());
        }
        for (int slot : keptSlots) {
            lines.add(SLOT_PREFIX + slotName(slot));
        }
        try {
            Files.createDirectories(path.getParent());
            Files.write(path, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            InventorySortify.LOGGER.error("Could not write {}", path, e);
        }
        stamp = stamp(path);
    }

    private static long stamp(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis() ^ Files.size(path);
        } catch (IOException e) {
            return 0;
        }
    }
}
