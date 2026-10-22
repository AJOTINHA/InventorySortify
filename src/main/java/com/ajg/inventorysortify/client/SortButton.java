package com.ajg.inventorysortify.client;

import com.ajg.inventorysortify.client.sort.Section;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;

/** Small icon button drawn above the top-right corner of a section's slot grid. */
public final class SortButton extends Button {
    public enum Icon {
        SORT, FILTER, DEPOSIT, TAKE, SETTINGS
    }

    private static final int SIZE = 10;
    private static final int ICON_COLOR = 0xFF404040;
    private static final int ICON_COLOR_HOVER = 0xFFFFFFA0;

    private final AbstractContainerScreen<?> screen;
    private final Section section;
    private final Icon icon;
    /** 0 = rightmost button. */
    private final int position;

    public SortButton(AbstractContainerScreen<?> screen, Section section, int position, Icon icon, Component tooltip, Runnable action) {
        super(0, 0, SIZE, SIZE, tooltip, button -> action.run(), DEFAULT_NARRATION);
        this.screen = screen;
        this.section = section;
        this.icon = icon;
        this.position = position;
        setTooltip(Tooltip.create(tooltip));
        // Highlight only while the mouse is over it, not while it stays focused after a click.
        setOverrideRenderHighlightedSprite(this::isHovered);
        reposition();
    }

    @Override
    public boolean shouldTakeFocusAfterInteraction() {
        return false;
    }

    /** Follows the screen when it moves (the recipe book shifts the inventory sideways). */
    public void reposition() {
        int[] bounds = section.bounds();
        setX(screen.getLeftPos() + bounds[0] + bounds[2] - SIZE - position * (SIZE + 1));
        // 3px gap: the slot frame is drawn 1px outside the slot's item area.
        setY(screen.getTopPos() + bounds[1] - SIZE - 3);
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        extractDefaultSprite(graphics);
        int color = isHovered() ? ICON_COLOR_HOVER : ICON_COLOR;
        int x = getX() + 2;
        int y = getY() + 2;
        switch (icon) {
            case SORT -> {
                // Three lines getting shorter.
                graphics.fill(x, y, x + 6, y + 1, color);
                graphics.fill(x, y + 2, x + 4, y + 3, color);
                graphics.fill(x, y + 4, x + 2, y + 5, color);
            }
            case FILTER -> {
                // A funnel.
                graphics.fill(x, y, x + 6, y + 1, color);
                graphics.fill(x + 1, y + 1, x + 5, y + 2, color);
                graphics.fill(x + 2, y + 2, x + 4, y + 6, color);
            }
            case DEPOSIT -> {
                // An arrow pointing up, towards the container.
                graphics.fill(x + 2, y, x + 4, y + 1, color);
                graphics.fill(x + 1, y + 1, x + 5, y + 2, color);
                graphics.fill(x, y + 2, x + 6, y + 3, color);
                graphics.fill(x + 2, y + 3, x + 4, y + 6, color);
            }
            case TAKE -> {
                // An arrow pointing down, towards the inventory.
                graphics.fill(x + 2, y, x + 4, y + 3, color);
                graphics.fill(x, y + 3, x + 6, y + 4, color);
                graphics.fill(x + 1, y + 4, x + 5, y + 5, color);
                graphics.fill(x + 2, y + 5, x + 4, y + 6, color);
            }
            case SETTINGS -> {
                // A gear: a ring with four teeth.
                graphics.fill(x + 1, y, x + 2, y + 1, color);
                graphics.fill(x + 4, y, x + 5, y + 1, color);
                graphics.fill(x, y + 1, x + 6, y + 2, color);
                graphics.fill(x, y + 2, x + 2, y + 4, color);
                graphics.fill(x + 4, y + 2, x + 6, y + 4, color);
                graphics.fill(x, y + 4, x + 6, y + 5, color);
                graphics.fill(x + 1, y + 5, x + 2, y + 6, color);
                graphics.fill(x + 4, y + 5, x + 5, y + 6, color);
            }
        }
    }
}
