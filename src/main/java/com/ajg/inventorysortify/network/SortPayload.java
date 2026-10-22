package com.ajg.inventorysortify.network;

import com.ajg.inventorysortify.InventorySortify;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Client → server: "rearrange these menu slots into this layout". The client computes the layout (it owns the sorting
 * rules); the server only checks that the layout holds exactly the items already in those slots, then applies it using
 * its own copies of the stacks, so a modified client cannot create or change items.
 */
public record SortPayload(int containerId, List<Integer> slots, List<ItemStack> layout) implements CustomPacketPayload {
    private static final int MAX_SLOTS = 512;

    public static final Type<SortPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(InventorySortify.MOD_ID, "sort"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SortPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SortPayload::containerId,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(MAX_SLOTS)), SortPayload::slots,
            ItemStack.OPTIONAL_UNTRUSTED_STREAM_CODEC.apply(ByteBufCodecs.list(MAX_SLOTS)), SortPayload::layout,
            SortPayload::new);

    @Override
    public Type<SortPayload> type() {
        return TYPE;
    }

    public static void handle(SortPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        AbstractContainerMenu menu = player.containerMenu;
        if (menu.containerId != payload.containerId() || !menu.stillValid(player)) {
            return;
        }
        if (!apply(player, menu, payload.slots(), payload.layout())) {
            // Whatever the client believed, put its view back in line with the server.
            menu.sendAllDataToRemote();
        }
    }

    private static boolean apply(ServerPlayer player, AbstractContainerMenu menu, List<Integer> slotIds, List<ItemStack> layout) {
        if (slotIds.size() != layout.size()) {
            return false;
        }
        List<Slot> slots = new ArrayList<>(slotIds.size());
        Set<Integer> seen = new HashSet<>();
        for (int id : slotIds) {
            if (id < 0 || id >= menu.slots.size() || !seen.add(id)) {
                return false;
            }
            slots.add(menu.slots.get(id));
        }

        // Pool of the items really in those slots, merged by item + components.
        List<ItemStack> pool = new ArrayList<>();
        for (Slot slot : slots) {
            ItemStack stack = slot.getItem();
            if (!stack.isEmpty()) {
                add(pool, stack);
            }
        }

        ItemStack[] result = new ItemStack[slots.size()];
        for (int i = 0; i < slots.size(); i++) {
            Slot slot = slots.get(i);
            ItemStack wanted = layout.get(i);
            ItemStack current = slot.getItem();
            if (wanted.isEmpty()) {
                result[i] = ItemStack.EMPTY;
            } else {
                ItemStack source = find(pool, wanted);
                if (source == null || source.getCount() < wanted.getCount()) {
                    return false;
                }
                ItemStack placed = source.copyWithCount(wanted.getCount());
                source.shrink(wanted.getCount());
                if (placed.getCount() > slot.getMaxStackSize(placed)) {
                    return false;
                }
                boolean unchanged = ItemStack.isSameItemSameComponents(current, placed) && current.getCount() == placed.getCount();
                if (!unchanged && !slot.mayPlace(placed)) {
                    return false;
                }
                result[i] = placed;
            }
            boolean changes = !(ItemStack.isSameItemSameComponents(current, result[i]) && current.getCount() == result[i].getCount());
            if (changes && !current.isEmpty() && !slot.mayPickup(player)) {
                return false;
            }
        }
        for (ItemStack left : pool) {
            if (!left.isEmpty()) {
                return false;
            }
        }

        for (int i = 0; i < slots.size(); i++) {
            Slot slot = slots.get(i);
            ItemStack current = slot.getItem();
            if (!(ItemStack.isSameItemSameComponents(current, result[i]) && current.getCount() == result[i].getCount())) {
                slot.set(result[i]);
            }
        }
        menu.broadcastChanges();
        return true;
    }

    private static void add(List<ItemStack> pool, ItemStack stack) {
        ItemStack existing = find(pool, stack);
        if (existing != null) {
            existing.grow(stack.getCount());
        } else {
            // Counts here may go past the max stack size; the pool is only a tally.
            pool.add(stack.copy());
        }
    }

    private static ItemStack find(List<ItemStack> pool, ItemStack stack) {
        for (ItemStack candidate : pool) {
            if (ItemStack.isSameItemSameComponents(candidate, stack)) {
                return candidate;
            }
        }
        return null;
    }
}
