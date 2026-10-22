package com.ajg.inventorysortify.client;

import com.ajg.inventorysortify.InventorySortify;
import com.ajg.inventorysortify.client.rules.KeepList;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Draws a small star in the corner of inventory slots that the inventory filter keeps, in screens and on the hotbar. */
public final class FilterMarks {
    /** 5×5 "*" sprite, drawn as a single quad. */
    private static final Identifier STAR = Identifier.fromNamespaceAndPath(InventorySortify.MOD_ID, "filter_star");
    private static final int SIZE = 5;
    public static final Identifier HOTBAR_LAYER = Identifier.fromNamespaceAndPath(InventorySortify.MOD_ID, "filter_stars");

    private FilterMarks() {
    }

    private static boolean kept(int inventorySlot, ItemStack stack) {
        return KeepList.isSlotKept(inventorySlot) || (!stack.isEmpty() && KeepList.isKept(stack.getItem()));
    }

    public static void draw(AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics) {
        boolean any = false;
        for (Slot slot : screen.getMenu().slots) {
            if (!slot.isActive() || !(slot.container instanceof Inventory) || slot.getContainerSlot() >= Inventory.INVENTORY_SIZE) {
                continue;
            }
            if (!kept(slot.getContainerSlot(), slot.getItem())) {
                continue;
            }
            if (!any) {
                graphics.nextStratum(); // above the slot items
                any = true;
            }
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, STAR, screen.getLeftPos() + slot.x, screen.getTopPos() + slot.y, SIZE, SIZE);
        }
    }

    /** HUD layer drawn right above the vanilla hotbar; slot positions match the vanilla hotbar layout. */
    public static void drawHotbar(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || player.isSpectator() || minecraft.gui.hud.isHidden() || !ClientConfig.FILTER_MARKS.getAsBoolean()) {
            return;
        }
        Inventory inventory = player.getInventory();
        int center = graphics.guiWidth() / 2;
        int y = graphics.guiHeight() - 16 - 3;
        boolean any = false;
        for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
            if (!kept(slot, inventory.getItem(slot))) {
                continue;
            }
            if (!any) {
                graphics.nextStratum(); // above the hotbar items
                any = true;
            }
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, STAR, center - 90 + slot * 20 + 2, y, SIZE, SIZE);
        }
    }
}
