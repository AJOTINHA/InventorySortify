package com.ajg.inventorysortify.client.sort;

import com.ajg.inventorysortify.client.rules.RuleSet;
import com.ajg.inventorysortify.client.tree.ItemTree;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Works out the sorted layout of one section (the player inventory or a container). Pure computation on copies of the
 * stacks; {@link SortController} sends the result to the server.
 */
public final class SortPlanner {
    private SortPlanner() {
    }

    /**
     * @param current  the stacks of the section in grid order (row by row)
     * @param rowSize  grid width
     * @param rules    the inventory rules, only for the 36-slot player inventory (else null)
     * @return the new layout, or null when the items would not fit (should not happen)
     */
    public static ItemStack[] plan(ItemStack[] current, int rowSize, SortMode mode, RuleSet rules, ItemTree tree) {
        int size = current.length;
        Comparator<ItemStack> order = comparator(tree);
        boolean useRules = rules != null && size == RuleSet.SIZE;
        boolean[] locked = useRules ? rules.locked() : new boolean[size];
        boolean[] frozen = useRules ? rules.frozen() : new boolean[size];

        ItemStack[] result = new ItemStack[size];
        ItemStack[] lockedContent = new ItemStack[size];
        List<ItemStack> pool = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            if (frozen[i]) {
                result[i] = current[i].copy();
            } else if (locked[i]) {
                lockedContent[i] = current[i].copy();
            } else if (!current[i].isEmpty()) {
                pool.add(current[i].copy());
            }
        }

        // Top up the stacks sitting in locked slots before anything else, so they don't leave a partial stack behind.
        for (int i = 0; i < size; i++) {
            ItemStack kept = lockedContent[i];
            if (kept == null || kept.isEmpty()) {
                continue;
            }
            for (ItemStack other : pool) {
                int space = kept.getMaxStackSize() - kept.getCount();
                if (space <= 0) {
                    break;
                }
                if (ItemStack.isSameItemSameComponents(kept, other)) {
                    int moved = Math.min(space, other.getCount());
                    kept.grow(moved);
                    other.shrink(moved);
                }
            }
        }
        pool = merge(pool, order);

        if (useRules) {
            applyRules(rules, result, lockedContent, pool, order);
        }
        for (int i = 0; i < size; i++) {
            if (result[i] == null && locked[i]) {
                result[i] = lockedContent[i] == null ? ItemStack.EMPTY : lockedContent[i];
            }
        }

        pool.sort(order);
        if (!fill(result, pool, rowSize, mode, tree)) {
            return null;
        }
        for (int i = 0; i < size; i++) {
            if (result[i] == null) {
                result[i] = ItemStack.EMPTY;
            }
        }
        return result;
    }

    private static void applyRules(RuleSet rules, ItemStack[] result, ItemStack[] lockedContent, List<ItemStack> pool, Comparator<ItemStack> order) {
        for (RuleSet.Rule rule : rules.rules()) {
            for (int slot : rule.slots()) {
                if (result[slot] != null) {
                    continue;
                }
                ItemStack here = lockedContent[slot];
                if (here != null && !here.isEmpty() && rule.matcher().test(here.getItem())) {
                    result[slot] = here;
                    lockedContent[slot] = null;
                    continue;
                }

                // Best candidate: from the free pool or from another locked slot that isn't placed yet.
                ItemStack best = null;
                int bestLocked = -1;
                for (ItemStack candidate : pool) {
                    if (rule.matcher().test(candidate.getItem()) && (best == null || order.compare(candidate, best) < 0)) {
                        best = candidate;
                    }
                }
                for (int j = 0; j < lockedContent.length; j++) {
                    ItemStack candidate = lockedContent[j];
                    if (j != slot && result[j] == null && candidate != null && !candidate.isEmpty()
                            && rule.matcher().test(candidate.getItem()) && (best == null || order.compare(candidate, best) < 0)) {
                        best = candidate;
                        bestLocked = j;
                    }
                }
                if (best == null) {
                    break;
                }

                if (bestLocked >= 0) {
                    // Swap: what was here goes to the locked slot the item came from.
                    lockedContent[bestLocked] = null;
                    result[bestLocked] = here == null ? ItemStack.EMPTY : here;
                } else {
                    pool.remove(best);
                    if (here != null && !here.isEmpty()) {
                        pool.add(here);
                    }
                }
                lockedContent[slot] = null;
                result[slot] = best;
            }
        }
    }

    /** Places the pool into the free slots of the result in the order the mode asks for. */
    private static boolean fill(ItemStack[] result, List<ItemStack> pool, int rowSize, SortMode mode, ItemTree tree) {
        int size = result.length;
        int[] sequence = new int[size];
        int lineLength;
        boolean byColumn = mode == SortMode.COLUMNS && rowSize > 0 && size % rowSize == 0;
        if (byColumn) {
            int height = size / rowSize;
            for (int i = 0; i < size; i++) {
                sequence[i] = (i % height) * rowSize + i / height;
            }
            lineLength = height;
        } else {
            for (int i = 0; i < size; i++) {
                sequence[i] = i;
            }
            lineLength = rowSize;
        }
        boolean lines = (mode == SortMode.ROWS || byColumn) && lineLength > 0;

        int pos = 0;
        int remaining = pool.size();
        ItemStack previous = null;
        for (ItemStack stack : pool) {
            boolean newGroup = previous == null || previous.getItem() != stack.getItem();
            if (lines && newGroup && pos % lineLength != 0) {
                // Start each kind of item on a new line, as long as everything left still fits after the jump.
                int nextLine = (pos / lineLength + 1) * lineLength;
                if (freeFrom(result, sequence, nextLine) >= remaining) {
                    pos = nextLine;
                }
            }
            while (pos < size && result[sequence[pos]] != null) {
                pos++;
            }
            if (pos >= size) {
                return false;
            }
            result[sequence[pos]] = stack;
            pos++;
            remaining--;
            previous = stack;
        }
        return true;
    }

    private static int freeFrom(ItemStack[] result, int[] sequence, int from) {
        int free = 0;
        for (int i = from; i < sequence.length; i++) {
            if (result[sequence[i]] == null) {
                free++;
            }
        }
        return free;
    }

    /** Merges identical stacks into as few full stacks as possible. */
    static List<ItemStack> merge(List<ItemStack> stacks, Comparator<ItemStack> order) {
        List<ItemStack> totals = new ArrayList<>();
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) {
                continue;
            }
            ItemStack total = null;
            for (ItemStack t : totals) {
                if (ItemStack.isSameItemSameComponents(t, stack)) {
                    total = t;
                    break;
                }
            }
            if (total == null) {
                totals.add(stack.copy());
            } else {
                total.grow(stack.getCount());
            }
        }
        List<ItemStack> result = new ArrayList<>();
        for (ItemStack total : totals) {
            int max = Math.max(1, total.getMaxStackSize());
            int count = total.getCount();
            while (count > 0) {
                int part = Math.min(max, count);
                result.add(total.copyWithCount(part));
                count -= part;
            }
        }
        result.sort(order);
        return result;
    }

    /** Item tree order, then registry order, then names, enchantments, damage and stack size. */
    public static Comparator<ItemStack> comparator(ItemTree tree) {
        return (a, b) -> {
            int c = Integer.compare(tree.order(a.getItem()), tree.order(b.getItem()));
            if (c != 0) {
                return c;
            }
            c = Integer.compare(BuiltInRegistries.ITEM.getId(a.getItem()), BuiltInRegistries.ITEM.getId(b.getItem()));
            if (c != 0) {
                return c;
            }
            boolean aNamed = a.has(DataComponents.CUSTOM_NAME);
            boolean bNamed = b.has(DataComponents.CUSTOM_NAME);
            if (aNamed != bNamed) {
                return aNamed ? 1 : -1;
            }
            if (aNamed) {
                c = a.getHoverName().getString().compareTo(b.getHoverName().getString());
                if (c != 0) {
                    return c;
                }
            }
            c = Integer.compare(enchantmentLevels(b), enchantmentLevels(a));
            if (c != 0) {
                return c;
            }
            c = Integer.compare(a.getDamageValue(), b.getDamageValue());
            if (c != 0) {
                return c;
            }
            if (!ItemStack.isSameItemSameComponents(a, b)) {
                // Keep variants (potions, dyed items...) grouped in a stable order.
                c = a.getComponentsPatch().toString().compareTo(b.getComponentsPatch().toString());
                if (c != 0) {
                    return c;
                }
            }
            return Integer.compare(b.getCount(), a.getCount());
        };
    }

    private static int enchantmentLevels(ItemStack stack) {
        int total = 0;
        for (ItemEnchantments enchantments : List.of(
                stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY),
                stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY))) {
            for (Object2IntMap.Entry<Holder<Enchantment>> entry : enchantments.entrySet()) {
                total += entry.getIntValue();
            }
        }
        return total;
    }
}
