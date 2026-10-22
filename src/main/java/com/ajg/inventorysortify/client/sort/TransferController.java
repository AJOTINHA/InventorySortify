package com.ajg.inventorysortify.client.sort;

import com.ajg.inventorysortify.client.ClientConfig;
import com.ajg.inventorysortify.client.rules.KeepList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.HashSet;
import java.util.Set;

/**
 * Moves stacks between the player inventory (hotbar included) and the open container with vanilla shift-clicks, so it
 * works on any server. Each direction either moves everything or, per the config, only item types the other side
 * already holds.
 */
public final class TransferController {
    /** Button value of a primary (left) container click in the protocol. */
    private static final int PRIMARY_BUTTON = 0;

    private TransferController() {
    }

    /** Inventory to container. Items kept by the inventory filter ({@link KeepList}) always stay. */
    public static void toContainer(AbstractContainerMenu menu, Section inventory, Section container) {
        if (inventory == null || container == null) {
            return;
        }
        Set<Item> wanted = ClientConfig.DEPOSIT_ONLY_MATCHING.getAsBoolean() ? itemsIn(container) : null;
        move(menu, inventory, (slot, stack) -> !KeepList.keeps(slot.getContainerSlot(), stack.getItem())
                && (wanted == null || wanted.contains(stack.getItem())));
    }

    /** Container to inventory. */
    public static void toInventory(AbstractContainerMenu menu, Section container, Section inventory) {
        if (inventory == null || container == null) {
            return;
        }
        Set<Item> wanted = ClientConfig.TAKE_ONLY_MATCHING.getAsBoolean() ? itemsIn(inventory) : null;
        move(menu, container, (slot, stack) -> wanted == null || wanted.contains(stack.getItem()));
    }

    private interface SlotFilter {
        boolean test(Slot slot, ItemStack stack);
    }

    /** Item types in a section, taken before anything moves. */
    private static Set<Item> itemsIn(Section section) {
        Set<Item> items = new HashSet<>();
        for (Slot slot : section.slots()) {
            if (!slot.getItem().isEmpty()) {
                items.add(slot.getItem().getItem());
            }
        }
        return items;
    }

    private static void move(AbstractContainerMenu menu, Section from, SlotFilter filter) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.gameMode == null || !menu.getCarried().isEmpty()) {
            return;
        }
        boolean moved = false;
        for (Slot slot : from.slots()) {
            ItemStack stack = slot.getItem();
            if (stack.isEmpty() || !filter.test(slot, stack)) {
                continue;
            }
            minecraft.gameMode.handleContainerInput(menu.containerId, slot.index, PRIMARY_BUTTON, ContainerInput.QUICK_MOVE, player);
            moved = true;
        }
        if (moved && ClientConfig.SOUNDS.getAsBoolean()) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ITEM_PICKUP, 1.0F, 0.4F));
        }
    }
}
