package com.ajg.inventorysortify.client;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ClientConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue MIDDLE_CLICK = BUILDER
            .comment("Middle-click on a slot sorts the inventory or container it belongs to (not in creative mode).")
            .define("middleClickSorting", true);

    public static final ModConfigSpec.BooleanValue SORT_BUTTONS = BUILDER
            .comment("Show the small sort buttons above inventories and chests.")
            .define("sortButtons", true);

    public static final ModConfigSpec.BooleanValue FILTER_MARKS = BUILDER
            .comment("Mark inventory slots kept by the inventory filter with a small star.")
            .define("filterMarks", true);

    public static final ModConfigSpec.BooleanValue DEPOSIT_ONLY_MATCHING = BUILDER
            .comment("Up arrow (inventory to container): when true, only move items the container already has; when false, move everything.",
                    "Items kept by the inventory filter always stay.")
            .define("depositOnlyMatching", false);

    public static final ModConfigSpec.BooleanValue TAKE_ONLY_MATCHING = BUILDER
            .comment("Down arrow (container to inventory): when true, only take items you already have in your inventory; when false, take everything.")
            .define("takeOnlyMatching", false);

    public static final ModConfigSpec.BooleanValue AUTO_REFILL = BUILDER
            .comment("Replace a broken tool or a used-up stack in your hand with the same item from your inventory.")
            .define("autoRefill", true);

    public static final ModConfigSpec.BooleanValue REFILL_BEFORE_BREAK = BUILDER
            .comment("Swap out a tool just before it breaks, so it can still be repaired. With no spare tool, it is moved to an empty slot with a warning.")
            .define("refillBeforeBreak", false);

    public static final ModConfigSpec.IntValue REFILL_DURABILITY_THRESHOLD = BUILDER
            .comment("Remaining durability at which refillBeforeBreak swaps the tool out.")
            .defineInRange("refillDurabilityThreshold", 5, 1, 100);

    public static final ModConfigSpec.BooleanValue SOUNDS = BUILDER
            .comment("Play a sound when sorting or auto-refilling.")
            .define("sounds", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private ClientConfig() {
    }
}
