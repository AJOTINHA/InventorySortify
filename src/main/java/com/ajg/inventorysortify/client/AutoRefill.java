package com.ajg.inventorysortify.client;

import com.ajg.inventorysortify.client.rules.SortifyFiles;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Refills the main hand and off hand when their stack runs out or their tool breaks, by swapping in a matching stack
 * from the inventory. Uses the vanilla "swap with hotbar slot" click, so it works on any server.
 */
public final class AutoRefill {
    /** Inventory slot index used by swap clicks for the off hand. */
    private static final int OFFHAND_BUTTON = Inventory.SLOT_OFFHAND;

    private static int lastSelected = -1;
    private static ItemStack lastMain = ItemStack.EMPTY;
    private static ItemStack lastOffhand = ItemStack.EMPTY;
    private static int cooldown;
    /** Hand slot and tool last warned about, so a tool with nowhere to go is announced only once. */
    private static int warnedSlot = -1;
    private static ItemStack warnedTool = ItemStack.EMPTY;

    private AutoRefill() {
    }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.gameMode == null) {
            lastSelected = -1;
            return;
        }
        Inventory inventory = player.getInventory();
        int selected = inventory.getSelectedSlot();
        ItemStack main = inventory.getItem(selected);
        ItemStack offhand = inventory.getItem(Inventory.SLOT_OFFHAND);

        boolean active = ClientConfig.AUTO_REFILL.getAsBoolean() && minecraft.gui.screen() == null && !player.isSpectator()
                && player.containerMenu == player.inventoryMenu && !minecraft.options.keyDrop.isDown()
                && !minecraft.options.keySwapOffhand.isDown() && !handsSwapped(main, offhand);
        if (warnedSlot >= 0 && !nearlyBroken(inventory.getItem(warnedSlot), warnedTool)) {
            warnedSlot = -1; // repaired, swapped or put away: warn again next time
        }
        if (cooldown > 0) {
            cooldown--;
        } else if (active && selected == lastSelected) {
            if (refill(minecraft, player, selected, lastMain, main) || refill(minecraft, player, OFFHAND_BUTTON, lastOffhand, offhand)) {
                cooldown = 2; // let the server's answer arrive before looking again
            }
        }
        lastSelected = selected;
        lastMain = main.copy();
        lastOffhand = offhand.copy();
    }

    private static boolean nearlyBroken(ItemStack stack, ItemStack sameAs) {
        return ItemStack.isSameItem(stack, sameAs) && stack.isDamageableItem()
                && stack.getMaxDamage() - stack.getDamageValue() <= ClientConfig.REFILL_DURABILITY_THRESHOLD.getAsInt();
    }

    /** The "swap item with off hand" key moved a hand's stack to the other hand: not a used-up stack. */
    private static boolean handsSwapped(ItemStack main, ItemStack offhand) {
        return (!lastMain.isEmpty() && ItemStack.matches(lastMain, offhand)) || (!lastOffhand.isEmpty() && ItemStack.matches(lastOffhand, main));
    }

    /** Returns whether a swap was sent for this hand slot. */
    private static boolean refill(Minecraft minecraft, LocalPlayer player, int handSlot, ItemStack before, ItemStack now) {
        if (before.isEmpty() || !SortifyFiles.ruleSet().allowsAutoRefill(before.getItem())) {
            return false;
        }
        boolean usedUp = now.isEmpty();
        boolean aboutToBreak = !usedUp && ClientConfig.REFILL_BEFORE_BREAK.getAsBoolean() && now.isDamageableItem()
                && now.getMaxDamage() - now.getDamageValue() <= ClientConfig.REFILL_DURABILITY_THRESHOLD.getAsInt()
                && ItemStack.isSameItem(before, now);
        if (!usedUp && !aboutToBreak) {
            return false;
        }

        // With refill-before-break on, never hand back a tool that is itself about to break (such as one just put away).
        boolean needsDurability = aboutToBreak || ClientConfig.REFILL_BEFORE_BREAK.getAsBoolean();
        int source = findReplacement(player.getInventory(), handSlot, before.getItem(), needsDurability);
        if (source < 0) {
            return aboutToBreak && protect(minecraft, player, handSlot, now);
        }
        warnedSlot = -1;
        minecraft.gameMode.handleContainerInput(player.inventoryMenu.containerId, menuSlot(source), handSlot, ContainerInput.SWAP, player);
        if (ClientConfig.SOUNDS.getAsBoolean()) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ITEM_PICKUP, 1.2F, 0.4F));
        }
        return true;
    }

    /**
     * A tool about to break with nothing to replace it: warn, and put it away in an empty slot so it stops being used.
     * With no empty slot, only warn, once per tool. Returns whether a click was sent.
     */
    private static boolean protect(Minecraft minecraft, LocalPlayer player, int handSlot, ItemStack tool) {
        if (warnedSlot == handSlot && ItemStack.isSameItem(warnedTool, tool)) {
            return false;
        }
        warnedSlot = handSlot;
        warnedTool = tool.copy();
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 0.7F, 1.0F));

        int empty = findEmptySlot(player.getInventory(), handSlot);
        if (empty < 0) {
            player.sendOverlayMessage(Component.translatable("inventorysortify.message.tool_warning", tool.getHoverName()).withStyle(ChatFormatting.RED));
            return false;
        }
        minecraft.gameMode.handleContainerInput(player.inventoryMenu.containerId, menuSlot(empty), handSlot, ContainerInput.SWAP, player);
        player.sendOverlayMessage(Component.translatable("inventorysortify.message.tool_protected", tool.getHoverName()).withStyle(ChatFormatting.GOLD));
        return true;
    }

    /** An empty slot of the main inventory, else of the hotbar (never the hand slot itself). */
    private static int findEmptySlot(Inventory inventory, int handSlot) {
        for (int slot = Inventory.getSelectionSize(); slot < Inventory.INVENTORY_SIZE; slot++) {
            if (inventory.getItem(slot).isEmpty()) {
                return slot;
            }
        }
        for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
            if (slot != handSlot && inventory.getItem(slot).isEmpty()) {
                return slot;
            }
        }
        return -1;
    }

    /**
     * Same item from the main inventory or hotbar. For tools, the most worn one that still has life in it (so worn
     * tools get used up first); for stacks, the smallest one.
     */
    private static int findReplacement(Inventory inventory, int handSlot, Item item, boolean needsDurability) {
        int best = -1;
        ItemStack bestStack = ItemStack.EMPTY;
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            if (slot == handSlot) {
                continue;
            }
            ItemStack candidate = inventory.getItem(slot);
            if (candidate.isEmpty() || !candidate.is(item)) {
                continue;
            }
            if (candidate.isDamageableItem()) {
                int left = candidate.getMaxDamage() - candidate.getDamageValue();
                if (needsDurability && left <= ClientConfig.REFILL_DURABILITY_THRESHOLD.getAsInt()) {
                    continue;
                }
                if (best < 0 || candidate.getDamageValue() > bestStack.getDamageValue()) {
                    best = slot;
                    bestStack = candidate;
                }
            } else if (best < 0 || candidate.getCount() < bestStack.getCount()) {
                best = slot;
                bestStack = candidate;
            }
        }
        return best;
    }

    /** Menu slot of an inventory slot in the player's own inventory menu. */
    private static int menuSlot(int inventorySlot) {
        return inventorySlot < 9 ? InventoryMenu.USE_ROW_SLOT_START + inventorySlot : inventorySlot;
    }
}
