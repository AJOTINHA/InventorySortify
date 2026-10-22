package com.ajg.inventorysortify.client;

import com.ajg.inventorysortify.client.rules.KeepList;
import com.ajg.inventorysortify.client.rules.SortifyFiles;
import com.ajg.inventorysortify.client.sort.SortPlanner;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Edits the inventory filter ({@link KeepList}) in two tabs: item types that stay in the inventory wherever they are,
 * and inventory slots whose contents stay. Opened as a layer over the inventory screen, which it returns to.
 */
public final class FilterScreen extends Screen {
    private enum Tab {
        ITEMS, SLOTS
    }

    private static final int COLUMNS = 9;
    private static final int CELL = 18;
    private static final int VISIBLE_ROWS = 6;
    private static final int PADDING = 8;
    private static final int SCROLLBAR = 6;
    private static final int PANEL_WIDTH = PADDING * 2 + COLUMNS * CELL + SCROLLBAR + 2;
    private static final int TAB_HEIGHT = 16;
    private static final int BUTTON_HEIGHT = 20;
    /** Gap between the main inventory rows and the hotbar in the slot tab, like in the inventory. */
    private static final int HOTBAR_GAP = 4;

    private static final int TEXT = 0xFF404040;
    private static final int HINT = 0xFF606060;
    private static final int SLOT = 0xFF8B8B8B;
    private static final int SLOT_KEPT = 0xFF5DB04A;
    private static final int SLOT_KEPT_BORDER = 0xFF2E7D1E;
    private static final int SLOT_DARK = 0xFF373737;
    private static final int SLOT_LIGHT = 0xFFFFFFFF;
    private static final int HOVER = 0x80FFFFFF;

    /** Remembered between openings. */
    private static Tab tab = Tab.ITEMS;

    /** Built once, so items don't disappear while the screen is open when they are un-kept. */
    private List<Item> entries;
    private List<FormattedCharSequence> hint = List.of();
    private int hintLines;
    private int left;
    private int top;
    private int gridTop;
    private int panelHeight;
    private int scrollRow;

    public FilterScreen() {
        super(Component.translatable("inventorysortify.filter.title"));
    }

    @Override
    protected void init() {
        if (entries == null) {
            entries = collectEntries();
        }
        int textWidth = PANEL_WIDTH - PADDING * 2;
        hint = font.split(Component.translatable(tab == Tab.ITEMS ? "inventorysortify.filter.hint_items" : "inventorysortify.filter.hint_slots"), textWidth);
        // Room for the longer hint, so the panel doesn't change size between tabs.
        hintLines = Math.max(font.split(Component.translatable("inventorysortify.filter.hint_items"), textWidth).size(),
                font.split(Component.translatable("inventorysortify.filter.hint_slots"), textWidth).size());

        int tabsY = PADDING + 12;
        int hintY = tabsY + TAB_HEIGHT + 4;
        int gridY = hintY + hintLines * 10 + 4;
        panelHeight = gridY + VISIBLE_ROWS * CELL + 6 + BUTTON_HEIGHT + PADDING;
        left = (width - PANEL_WIDTH) / 2;
        top = (height - panelHeight) / 2;
        gridTop = top + gridY;
        scrollRow = Math.min(scrollRow, maxScroll());

        int halfWidth = (PANEL_WIDTH - PADDING * 2 - 4) / 2;
        Button itemsTab = addRenderableWidget(Button.builder(Component.translatable("inventorysortify.filter.tab_items"), button -> switchTab(Tab.ITEMS))
                .bounds(left + PADDING, top + tabsY, halfWidth, TAB_HEIGHT).build());
        Button slotsTab = addRenderableWidget(Button.builder(Component.translatable("inventorysortify.filter.tab_slots"), button -> switchTab(Tab.SLOTS))
                .bounds(left + PANEL_WIDTH - PADDING - halfWidth, top + tabsY, halfWidth, TAB_HEIGHT).build());
        // The open tab is shown pressed (inactive).
        itemsTab.active = tab != Tab.ITEMS;
        slotsTab.active = tab != Tab.SLOTS;

        int buttonY = gridTop + VISIBLE_ROWS * CELL + 6;
        addRenderableWidget(Button.builder(Component.translatable("inventorysortify.filter.clear"),
                        button -> {
                            if (tab == Tab.ITEMS) {
                                KeepList.clearItems();
                            } else {
                                KeepList.clearSlots();
                            }
                        })
                .bounds(left + PADDING, buttonY, halfWidth, BUTTON_HEIGHT).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .bounds(left + PANEL_WIDTH - PADDING - halfWidth, buttonY, halfWidth, BUTTON_HEIGHT).build());
    }

    private void switchTab(Tab newTab) {
        tab = newTab;
        rebuildWidgets();
    }

    private static List<Item> collectEntries() {
        Set<Item> items = new LinkedHashSet<>();
        Inventory inventory = inventory();
        if (inventory != null) {
            for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (!stack.isEmpty()) {
                    items.add(stack.getItem());
                }
            }
            ItemStack offhand = inventory.getItem(Inventory.SLOT_OFFHAND);
            if (!offhand.isEmpty()) {
                items.add(offhand.getItem());
            }
        }
        items.addAll(KeepList.items());
        List<Item> result = new ArrayList<>(items);
        var order = SortPlanner.comparator(SortifyFiles.tree());
        result.sort((a, b) -> order.compare(a.getDefaultInstance(), b.getDefaultInstance()));
        return result;
    }

    private static Inventory inventory() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player == null ? null : player.getInventory();
    }

    private int rows() {
        return (entries.size() + COLUMNS - 1) / COLUMNS;
    }

    private int maxScroll() {
        return Math.max(0, rows() - VISIBLE_ROWS);
    }

    // ---- item tab ----

    /** Entry index under the mouse, or -1. */
    private int entryAt(double mouseX, double mouseY) {
        int gridLeft = left + PADDING;
        if (mouseX < gridLeft || mouseY < gridTop) {
            return -1;
        }
        int column = (int) (mouseX - gridLeft) / CELL;
        int row = (int) (mouseY - gridTop) / CELL;
        if (column >= COLUMNS || row >= VISIBLE_ROWS) {
            return -1;
        }
        int index = (row + scrollRow) * COLUMNS + column;
        return index < entries.size() ? index : -1;
    }

    // ---- slot tab ----

    /** Top of the slot grid, centred in the grid area. */
    private int slotGridTop() {
        int height = 4 * CELL + HOTBAR_GAP;
        return gridTop + (VISIBLE_ROWS * CELL - height) / 2;
    }

    /** Screen y of a grid row (0–2 main inventory, 3 hotbar). */
    private int slotRowY(int row) {
        return slotGridTop() + row * CELL + (row == 3 ? HOTBAR_GAP : 0);
    }

    /** Inventory slot of a grid position: rows A–C are slots 9–35, row D (the hotbar) slots 0–8. */
    private static int inventorySlot(int row, int column) {
        return row == 3 ? column : 9 + row * COLUMNS + column;
    }

    /** Inventory slot under the mouse, or -1. */
    private int slotAt(double mouseX, double mouseY) {
        int gridLeft = left + PADDING;
        if (mouseX < gridLeft || mouseX >= gridLeft + COLUMNS * CELL) {
            return -1;
        }
        int column = (int) (mouseX - gridLeft) / CELL;
        for (int row = 0; row < 4; row++) {
            int y = slotRowY(row);
            if (mouseY >= y && mouseY < y + CELL) {
                return inventorySlot(row, column);
            }
        }
        return -1;
    }

    // ---- rendering ----

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        drawPanel(graphics);
        graphics.text(font, title, left + PADDING, top + PADDING, TEXT, false);
        int y = top + PADDING + 12 + TAB_HEIGHT + 4;
        for (FormattedCharSequence line : hint) {
            graphics.text(font, line, left + PADDING, y, HINT, false);
            y += 10;
        }

        List<Component> tooltip = tab == Tab.ITEMS ? drawItemTab(graphics, mouseX, mouseY) : drawSlotTab(graphics, mouseX, mouseY);
        super.extractRenderState(graphics, mouseX, mouseY, a);
        if (tooltip != null) {
            graphics.setTooltipForNextFrame(font, tooltip, Optional.empty(), mouseX, mouseY);
        }
    }

    private List<Component> drawItemTab(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int hovered = entryAt(mouseX, mouseY);
        for (int row = 0; row < VISIBLE_ROWS; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                int x = left + PADDING + column * CELL;
                int cellY = gridTop + row * CELL;
                int index = (row + scrollRow) * COLUMNS + column;
                Item item = index < entries.size() ? entries.get(index) : null;
                drawSlot(graphics, x, cellY, item != null && KeepList.isKept(item));
                if (item != null) {
                    graphics.item(item.getDefaultInstance(), x + 1, cellY + 1);
                    if (index == hovered) {
                        graphics.fill(x + 1, cellY + 1, x + CELL - 1, cellY + CELL - 1, HOVER);
                    }
                }
            }
        }
        if (entries.isEmpty()) {
            graphics.centeredText(font, Component.translatable("inventorysortify.filter.empty"),
                    left + PADDING + COLUMNS * CELL / 2, gridTop + VISIBLE_ROWS * CELL / 2 - 4, HINT);
        }
        drawScrollbar(graphics);

        if (hovered < 0) {
            return null;
        }
        Item item = entries.get(hovered);
        boolean kept = KeepList.isKept(item);
        return List.of(
                item.getDefaultInstance().getHoverName(),
                Component.translatable(kept ? "inventorysortify.filter.kept" : "inventorysortify.filter.not_kept")
                        .withStyle(kept ? ChatFormatting.GREEN : ChatFormatting.GRAY));
    }

    private List<Component> drawSlotTab(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        Inventory inventory = inventory();
        int hovered = slotAt(mouseX, mouseY);
        for (int row = 0; row < 4; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                int slot = inventorySlot(row, column);
                int x = left + PADDING + column * CELL;
                int cellY = slotRowY(row);
                drawSlot(graphics, x, cellY, KeepList.isSlotKept(slot));
                ItemStack stack = inventory == null ? ItemStack.EMPTY : inventory.getItem(slot);
                if (!stack.isEmpty()) {
                    graphics.item(stack, x + 1, cellY + 1);
                    graphics.itemDecorations(font, stack, x + 1, cellY + 1);
                }
                if (slot == hovered) {
                    graphics.fill(x + 1, cellY + 1, x + CELL - 1, cellY + CELL - 1, HOVER);
                }
            }
        }

        if (hovered < 0) {
            return null;
        }
        List<Component> lines = new ArrayList<>();
        ItemStack stack = inventory == null ? ItemStack.EMPTY : inventory.getItem(hovered);
        lines.add(stack.isEmpty()
                ? Component.translatable("inventorysortify.filter.empty_slot", KeepList.slotName(hovered))
                : stack.getHoverName());
        boolean kept = KeepList.isSlotKept(hovered);
        lines.add(Component.translatable(kept ? "inventorysortify.filter.slot_kept" : "inventorysortify.filter.slot_not_kept")
                .withStyle(kept ? ChatFormatting.GREEN : ChatFormatting.GRAY));
        if (!stack.isEmpty() && KeepList.isKept(stack.getItem())) {
            lines.add(Component.translatable("inventorysortify.filter.kept_by_item").withStyle(ChatFormatting.DARK_GREEN));
        }
        return lines;
    }

    private void drawPanel(GuiGraphicsExtractor graphics) {
        int right = left + PANEL_WIDTH;
        int bottom = top + panelHeight;
        graphics.fill(left, top, right, bottom, 0xFF000000);
        graphics.fill(left + 1, top + 1, right - 1, bottom - 1, 0xFFC6C6C6);
        graphics.fill(left + 1, top + 1, right - 2, top + 3, SLOT_LIGHT);
        graphics.fill(left + 1, top + 1, left + 3, bottom - 2, SLOT_LIGHT);
        graphics.fill(left + 3, bottom - 3, right - 1, bottom - 1, 0xFF555555);
        graphics.fill(right - 3, top + 3, right - 1, bottom - 1, 0xFF555555);
    }

    private static void drawSlot(GuiGraphicsExtractor graphics, int x, int y, boolean kept) {
        graphics.fill(x, y, x + CELL, y + CELL, SLOT_LIGHT);
        graphics.fill(x, y, x + CELL - 1, y + CELL - 1, SLOT_DARK);
        graphics.fill(x + 1, y + 1, x + CELL - 1, y + CELL - 1, kept ? SLOT_KEPT : SLOT);
        if (kept) {
            graphics.outline(x, y, CELL, CELL, SLOT_KEPT_BORDER);
        }
    }

    private void drawScrollbar(GuiGraphicsExtractor graphics) {
        int x = left + PADDING + COLUMNS * CELL + 2;
        int height = VISIBLE_ROWS * CELL;
        graphics.fill(x, gridTop, x + SCROLLBAR, gridTop + height, SLOT_DARK);
        int max = maxScroll();
        int thumb = max == 0 ? height : Math.max(10, height * VISIBLE_ROWS / rows());
        int thumbY = max == 0 ? gridTop : gridTop + (height - thumb) * scrollRow / max;
        graphics.fill(x, thumbY, x + SCROLLBAR, thumbY + thumb, max == 0 ? SLOT : 0xFFC6C6C6);
    }

    // ---- input ----

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            if (tab == Tab.ITEMS) {
                int index = entryAt(event.x(), event.y());
                if (index >= 0) {
                    KeepList.toggle(entries.get(index));
                    playClick();
                    return true;
                }
            } else {
                int slot = slotAt(event.x(), event.y());
                if (slot >= 0) {
                    KeepList.toggleSlot(slot);
                    playClick();
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private void playClick() {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (tab == Tab.ITEMS && scrollY != 0) {
            scrollRow = Math.clamp(scrollRow - (int) Math.signum(scrollY), 0, maxScroll());
            return true;
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
