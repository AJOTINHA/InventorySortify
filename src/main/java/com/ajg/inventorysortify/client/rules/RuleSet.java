package com.ajg.inventorysortify.client.rules;

import com.ajg.inventorysortify.client.tree.ItemTree;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * One named set of rules for the player inventory, from {@code rules.txt}. The inventory is a 9×4 grid: rows A–C are
 * the main inventory (top to bottom) and row D is the hotbar; columns are 1–9. Lines look like:
 * <pre>
 * D1 sword          a slot
 * D pickaxe         a whole row (left to right; "Dr" right to left)
 * 9 food            a whole column (bottom to top; "9r" top to bottom)
 * A1-C3 blocks      a rectangle, row by row ("v" for column by column, "r" reversed)
 * D LOCKED          sorting never moves items out of these slots
 * D9 FROZEN         sorting never touches these slots at all
 * AUTOREFILL tool   only auto-refill these items ("AUTOREFILL nothing" turns it off)
 * </pre>
 * A keyword is an item tree keyword, an item id ({@code minecraft:torch}) or an item tag ({@code #c:ingots}).
 */
public final class RuleSet {
    public static final int ROWS = 4;
    public static final int COLUMNS = 9;
    public static final int SIZE = ROWS * COLUMNS;

    private static final Pattern SLOT_CONSTRAINT = Pattern.compile("(?i)[a-d][1-9]|[a-d]r?|[1-9]r?|[a-d][1-9]-[a-d][1-9][rv]{0,2}");

    /** Rule types; a more precise constraint wins over a looser one. */
    private enum Type {
        RECTANGLE, ROW, COLUMN, SLOT
    }

    public record Rule(int[] slots, String keyword, Predicate<Item> matcher, int priority) {
    }

    private final String name;
    private final List<Rule> rules = new ArrayList<>();
    private final boolean[] locked = new boolean[SIZE];
    private final boolean[] frozen = new boolean[SIZE];
    private final List<Predicate<Item>> autoRefill = new ArrayList<>();
    private boolean autoRefillDisabled;

    RuleSet(String name) {
        this.name = name;
    }

    public String name() {
        return name;
    }

    /** Rules, most important first. */
    public List<Rule> rules() {
        return rules;
    }

    public boolean[] locked() {
        return locked;
    }

    public boolean[] frozen() {
        return frozen;
    }

    public boolean allowsAutoRefill(Item item) {
        if (autoRefillDisabled) {
            return false;
        }
        if (autoRefill.isEmpty()) {
            return true;
        }
        for (Predicate<Item> matcher : autoRefill) {
            if (matcher.test(item)) {
                return true;
            }
        }
        return false;
    }

    /** Adds one line; returns an error message, or null when the line was understood. */
    String addLine(String line, ItemTree tree) {
        String[] words = line.trim().split("\\s+");
        if (words.length != 2) {
            return "expected two words";
        }
        String constraint = words[0];
        String keyword = words[1];

        if (constraint.equalsIgnoreCase("AUTOREFILL") || constraint.equalsIgnoreCase("AUTOREPLACE")) {
            if (keyword.equalsIgnoreCase("nothing")) {
                autoRefillDisabled = true;
                return null;
            }
            Predicate<Item> matcher = matcher(keyword, tree);
            if (matcher == null) {
                return "unknown keyword '" + keyword + "'";
            }
            autoRefill.add(matcher);
            return null;
        }

        if (!SLOT_CONSTRAINT.matcher(constraint).matches()) {
            return "unknown slot '" + constraint + "'";
        }
        int[] slots = slots(constraint);
        if (keyword.equalsIgnoreCase("LOCKED")) {
            for (int slot : slots) {
                locked[slot] = true;
            }
            return null;
        }
        if (keyword.equalsIgnoreCase("FROZEN")) {
            for (int slot : slots) {
                frozen[slot] = true;
            }
            return null;
        }
        Predicate<Item> matcher = matcher(keyword, tree);
        if (matcher == null) {
            return "unknown keyword '" + keyword + "'";
        }
        rules.add(new Rule(slots, keyword, matcher, priority(type(constraint), keyword, tree)));
        return null;
    }

    void finish() {
        rules.sort(Comparator.comparingInt(Rule::priority).reversed());
    }

    private static Predicate<Item> matcher(String keyword, ItemTree tree) {
        if (tree.isKeyword(keyword)) {
            return item -> tree.matches(item, keyword);
        }
        if (keyword.startsWith("#") || keyword.contains(":")) {
            return ItemTree.parseMatcher(keyword.toLowerCase(Locale.ROOT));
        }
        return null;
    }

    private static int priority(Type type, String keyword, ItemTree tree) {
        // Constraint type first, then the more specific keyword, then the one earlier in the tree.
        int specificity;
        if (tree.isKeyword(keyword)) {
            specificity = Math.min(tree.depth(keyword), 99) * 1000 + (999 - Math.min(tree.position(keyword), 999));
        } else {
            specificity = 100 * 1000; // an item id or tag is more specific than any tree keyword
        }
        return type.ordinal() * 1_000_000 + specificity;
    }

    private static Type type(String constraint) {
        String c = constraint.toLowerCase(Locale.ROOT);
        if (c.contains("-")) {
            if (c.charAt(0) == c.charAt(3)) {
                return Type.ROW;
            }
            if (c.charAt(1) == c.charAt(4)) {
                return Type.COLUMN;
            }
            return Type.RECTANGLE;
        }
        if (c.length() == 2 && Character.isLetter(c.charAt(0)) && Character.isDigit(c.charAt(1))) {
            return Type.SLOT;
        }
        return Character.isDigit(c.charAt(0)) ? Type.COLUMN : Type.ROW;
    }

    /** Grid indices for a constraint, in the order they should be filled. Index = row * 9 + column. */
    static int[] slots(String constraint) {
        String c = constraint.toLowerCase(Locale.ROOT);
        if (c.contains("-")) {
            boolean vertical = c.indexOf('v', 5) >= 0;
            boolean reverse = c.indexOf('r', 5) >= 0;
            int row1 = c.charAt(0) - 'a', col1 = c.charAt(1) - '1';
            int row2 = c.charAt(3) - 'a', col2 = c.charAt(4) - '1';
            int rowStep = row1 <= row2 ? 1 : -1, colStep = col1 <= col2 ? 1 : -1;
            int rows = Math.abs(row2 - row1) + 1, cols = Math.abs(col2 - col1) + 1;
            int[] result = new int[rows * cols];
            int k = 0;
            if (vertical) {
                for (int x = 0; x < cols; x++) {
                    for (int y = 0; y < rows; y++) {
                        result[k++] = index(row1 + y * rowStep, col1 + x * colStep);
                    }
                }
            } else {
                for (int y = 0; y < rows; y++) {
                    for (int x = 0; x < cols; x++) {
                        result[k++] = index(row1 + y * rowStep, col1 + x * colStep);
                    }
                }
            }
            return reverse ? reversed(result) : result;
        }
        char first = c.charAt(0);
        if (c.length() == 2 && Character.isDigit(c.charAt(1))) {
            return new int[]{index(first - 'a', c.charAt(1) - '1')};
        }
        boolean reverse = c.endsWith("r");
        if (Character.isLetter(first)) {
            int[] result = new int[COLUMNS];
            for (int x = 0; x < COLUMNS; x++) {
                result[x] = index(first - 'a', x);
            }
            return reverse ? reversed(result) : result;
        }
        // Columns fill from the bottom (the hotbar) up, like the classic rules.
        int[] result = new int[ROWS];
        for (int y = 0; y < ROWS; y++) {
            result[y] = index(ROWS - 1 - y, first - '1');
        }
        return reverse ? reversed(result) : result;
    }

    private static int index(int row, int column) {
        return row * COLUMNS + column;
    }

    private static int[] reversed(int[] data) {
        int[] result = new int[data.length];
        for (int i = 0; i < data.length; i++) {
            result[i] = data[data.length - 1 - i];
        }
        return result;
    }
}
