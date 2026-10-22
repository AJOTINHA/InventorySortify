package com.ajg.inventorysortify.client.sort;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ShulkerBoxSlot;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.transfer.item.ResourceHandlerSlot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A sortable group of slots in an open menu: the player's 36 inventory slots, or the storage slots of one container
 * (chest, barrel, shulker box, dispenser, hopper, modded storage...).
 *
 * @param slots   menu slots in grid order (row by row)
 * @param rowSize grid width
 */
public record Section(List<Slot> slots, int rowSize, boolean playerInventory) {
    /** Containers with fewer slots aren't worth sorting (and are usually machines). */
    private static final int MIN_CONTAINER_SLOTS = 5;

    /** The section the slot belongs to, or null when that part of the menu shouldn't be sorted. */
    public static Section of(AbstractContainerMenu menu, Slot slot) {
        if (isPlayerInventory(slot)) {
            return playerInventory(menu);
        }
        return container(menu, slot.container);
    }

    /** The player's main inventory and hotbar, ordered like the rules grid: rows A–C, then the hotbar as row D. */
    public static Section playerInventory(AbstractContainerMenu menu) {
        Slot[] grid = new Slot[36];
        for (Slot slot : menu.slots) {
            if (isPlayerInventory(slot)) {
                int index = slot.getContainerSlot();
                grid[index < 9 ? 27 + index : index - 9] = slot;
            }
        }
        for (Slot slot : grid) {
            if (slot == null) {
                return null;
            }
        }
        return new Section(List.of(grid), 9, true);
    }

    /** The biggest sortable container in the menu other than the player inventory. */
    public static Section mainContainer(AbstractContainerMenu menu) {
        Section best = null;
        List<Container> seen = new ArrayList<>();
        for (Slot slot : menu.slots) {
            if (isPlayerInventory(slot) || seen.contains(slot.container)) {
                continue;
            }
            seen.add(slot.container);
            Section section = container(menu, slot.container);
            if (section != null && (best == null || section.slots.size() > best.slots.size())) {
                best = section;
            }
        }
        return best;
    }

    private static Section container(AbstractContainerMenu menu, Container container) {
        List<Slot> slots = new ArrayList<>();
        for (Slot slot : menu.slots) {
            if (slot.container == container && !isPlayerInventory(slot)) {
                if (!isStorageSlot(slot)) {
                    // Machines mix inputs, outputs and fuel in one container: leave them alone.
                    return null;
                }
                slots.add(slot);
            }
        }
        if (slots.size() < MIN_CONTAINER_SLOTS) {
            return null;
        }
        slots.sort(Comparator.<Slot>comparingInt(s -> s.y).thenComparingInt(s -> s.x));
        int firstRowY = slots.getFirst().y;
        int rowSize = 0;
        for (Slot slot : slots) {
            if (slot.y == firstRowY) {
                rowSize++;
            }
        }
        return new Section(List.copyOf(slots), Math.max(rowSize, 1), false);
    }

    private static boolean isPlayerInventory(Slot slot) {
        return slot.container instanceof Inventory && slot.getContainerSlot() < Inventory.INVENTORY_SIZE;
    }

    private static boolean isStorageSlot(Slot slot) {
        Class<?> type = slot.getClass();
        return type == Slot.class || type == ShulkerBoxSlot.class || type == ResourceHandlerSlot.class;
    }

    /** Top-left corner and size of the slot grid, relative to the screen's left/top. */
    public int[] bounds() {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (Slot slot : slots) {
            if (playerInventory && slots.indexOf(slot) >= 27) {
                continue; // the hotbar sits apart below the main inventory
            }
            minX = Math.min(minX, slot.x);
            minY = Math.min(minY, slot.y);
            maxX = Math.max(maxX, slot.x + 16);
            maxY = Math.max(maxY, slot.y + 16);
        }
        return new int[]{minX, minY, maxX - minX, maxY - minY};
    }
}
