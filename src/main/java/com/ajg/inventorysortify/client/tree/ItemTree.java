package com.ajg.inventorysortify.client.tree;

import com.ajg.inventorysortify.InventorySortify;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

/**
 * The item tree: a hierarchy of keywords (categories) that gives every item its sorting order and lets rules name
 * whole groups of items. Loaded from an indented text file, one keyword per line, followed by what it matches:
 * <pre>
 * equipment
 *   tool
 *     pickaxe   #minecraft:pickaxes
 *     shears    minecraft:shears
 * food          &#64;food
 * </pre>
 * {@code #ns:tag} is an item tag, {@code ns:item} (or just {@code item}) an item id and {@code @name} a built-in
 * test (see {@link #builtin}). A keyword matches an item when it, or any keyword nested under it, matches it; the
 * top-level keyword matches every item. The order of an item is the position of the first line that matches it,
 * skipping lines with a more specific match nested under them (so a catch-all such as {@code @block} can sit above
 * the lines it is a fallback for); unmatched items go last.
 */
public final class ItemTree {
    /** One line of the tree file. */
    private static final class Node {
        final String name;
        final int index;
        final int depth;
        final Node parent;
        final List<Predicate<Item>> matchers = new ArrayList<>();

        Node(String name, int index, int depth, Node parent) {
            this.name = name;
            this.index = index;
            this.depth = depth;
            this.parent = parent;
        }
    }

    /** What the tree knows about one item: its order and every node (with ancestors) that matches it. */
    private record ItemInfo(int order, BitSet nodes) {
    }

    private final List<Node> nodes = new ArrayList<>();
    private final Map<String, List<Node>> byName = new HashMap<>();
    private final Map<Item, ItemInfo> cache = new HashMap<>();
    private final List<String> errors = new ArrayList<>();

    private ItemTree() {
    }

    public static ItemTree parse(List<String> lines) {
        ItemTree tree = new ItemTree();
        List<Integer> indents = new ArrayList<>();
        List<Node> stack = new ArrayList<>();
        int lineNumber = 0;
        for (String raw : lines) {
            lineNumber++;
            String line = stripComment(raw);
            if (line.isBlank()) {
                continue;
            }
            int indent = 0;
            while (indent < line.length() && Character.isWhitespace(line.charAt(indent))) {
                indent += line.charAt(indent) == '\t' ? 4 : 1;
            }
            while (!indents.isEmpty() && indents.getLast() >= indent) {
                indents.removeLast();
                stack.removeLast();
            }
            String[] words = line.trim().split("\\s+");
            Node parent = stack.isEmpty() ? null : stack.getLast();
            Node node = new Node(words[0], tree.nodes.size(), stack.size(), parent);
            for (int i = 1; i < words.length; i++) {
                Predicate<Item> matcher = parseMatcher(words[i]);
                if (matcher == null) {
                    tree.errors.add("line " + lineNumber + ": unknown matcher '" + words[i] + "'");
                } else {
                    node.matchers.add(matcher);
                }
            }
            tree.nodes.add(node);
            tree.byName.computeIfAbsent(node.name.toLowerCase(Locale.ROOT), k -> new ArrayList<>()).add(node);
            indents.add(indent);
            stack.add(node);
        }
        return tree;
    }

    private static String stripComment(String line) {
        // "#" starts a comment only at the start of a line or after whitespace followed by a space, so "#c:ingots" works.
        String trimmed = line.stripLeading();
        if (trimmed.startsWith("//") || trimmed.startsWith("# ") || trimmed.equals("#")) {
            return "";
        }
        int comment = line.indexOf(" //");
        return comment >= 0 ? line.substring(0, comment) : line;
    }

    /** Parses one matcher word: {@code #tag}, {@code @builtin} or an item id. */
    public static Predicate<Item> parseMatcher(String word) {
        if (word.startsWith("#")) {
            Identifier id = Identifier.tryParse(word.substring(1));
            if (id == null) {
                return null;
            }
            TagKey<Item> tag = TagKey.create(Registries.ITEM, id);
            return item -> BuiltInRegistries.ITEM.wrapAsHolder(item).is(tag);
        }
        if (word.startsWith("@")) {
            return builtin(word.substring(1).toLowerCase(Locale.ROOT));
        }
        Identifier id = Identifier.tryParse(word);
        if (id == null) {
            return null;
        }
        return item -> BuiltInRegistries.ITEM.getKey(item).equals(id);
    }

    private static Predicate<Item> builtin(String name) {
        return switch (name) {
            case "block" -> item -> item instanceof BlockItem;
            case "food" -> item -> item.components().has(DataComponents.FOOD);
            case "damageable" -> item -> item.components().has(DataComponents.MAX_DAMAGE);
            case "tool" -> item -> item.components().has(DataComponents.TOOL);
            case "weapon" -> item -> item.components().has(DataComponents.WEAPON);
            case "equippable" -> item -> item.components().has(DataComponents.EQUIPPABLE);
            case "potion" -> item -> item.components().has(DataComponents.POTION_CONTENTS);
            case "enchanted_book" -> item -> item.components().has(DataComponents.STORED_ENCHANTMENTS);
            case "spawn_egg" -> item -> item instanceof SpawnEggItem;
            case "unstackable" -> item -> {
                DataComponentMap components = item.components();
                return components.getOrDefault(DataComponents.MAX_STACK_SIZE, 64) == 1;
            };
            default -> null;
        };
    }

    public List<String> errors() {
        return errors;
    }

    /** Forgets cached item lookups; needed when tags change (joining a world, /reload). */
    public void clearCache() {
        cache.clear();
    }

    public boolean isKeyword(String keyword) {
        return byName.containsKey(keyword.toLowerCase(Locale.ROOT));
    }

    /** How deep the keyword sits in the tree (0 for the root); deeper keywords are more specific. */
    public int depth(String keyword) {
        List<Node> found = byName.get(keyword.toLowerCase(Locale.ROOT));
        return found == null ? 0 : found.getFirst().depth;
    }

    /** Position of the keyword in the tree file. */
    public int position(String keyword) {
        List<Node> found = byName.get(keyword.toLowerCase(Locale.ROOT));
        return found == null ? Integer.MAX_VALUE : found.getFirst().index;
    }

    public boolean matches(Item item, String keyword) {
        List<Node> found = byName.get(keyword.toLowerCase(Locale.ROOT));
        if (found == null) {
            return false;
        }
        BitSet matched = info(item).nodes();
        for (Node node : found) {
            if (node.parent == null || matched.get(node.index)) {
                return true;
            }
        }
        return false;
    }

    /** Sorting order of the item: lower comes first, {@link Integer#MAX_VALUE} when the tree doesn't know it. */
    public int order(Item item) {
        return info(item).order();
    }

    private ItemInfo info(Item item) {
        return cache.computeIfAbsent(item, this::compute);
    }

    private ItemInfo compute(Item item) {
        BitSet direct = new BitSet(nodes.size());
        BitSet matched = new BitSet(nodes.size());
        for (Node node : nodes) {
            for (Predicate<Item> matcher : node.matchers) {
                try {
                    if (matcher.test(item)) {
                        direct.set(node.index);
                        break;
                    }
                } catch (RuntimeException e) {
                    // Tags not bound yet (no world loaded); treat as no match.
                    InventorySortify.LOGGER.debug("Item tree matcher failed for {}", item, e);
                }
            }
        }
        BitSet hasMatchingDescendant = new BitSet(nodes.size());
        for (int i = direct.nextSetBit(0); i >= 0; i = direct.nextSetBit(i + 1)) {
            for (Node n = nodes.get(i); n != null; n = n.parent) {
                matched.set(n.index);
                if (n.index != i) {
                    hasMatchingDescendant.set(n.index);
                }
            }
        }
        int order = Integer.MAX_VALUE;
        for (int i = direct.nextSetBit(0); i >= 0; i = direct.nextSetBit(i + 1)) {
            if (!hasMatchingDescendant.get(i)) {
                order = i;
                break;
            }
        }
        return new ItemInfo(order, matched);
    }
}
