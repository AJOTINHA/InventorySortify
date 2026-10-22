package com.ajg.inventorysortify.client.rules;

import com.ajg.inventorysortify.InventorySortify;
import com.ajg.inventorysortify.client.tree.ItemTree;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The editable files in {@code config/inventorysortify/}: {@code rules.txt} and {@code itemtree.txt}. Missing files are
 * created from the defaults in the jar; edited files are reloaded the next time they are needed.
 */
public final class SortifyFiles {
    private static final String RULES = "rules.txt";
    private static final String TREE = "itemtree.txt";

    private static ItemTree tree;
    private static List<RuleSet> ruleSets = List.of();
    private static int current;
    private static long rulesStamp = -1;
    private static long treeStamp = -1;
    private static final List<Component> problems = new ArrayList<>();

    private SortifyFiles() {
    }

    public static Path directory() {
        return FMLPaths.CONFIGDIR.get().resolve(InventorySortify.MOD_ID);
    }

    public static Path rulesFile() {
        return directory().resolve(RULES);
    }

    public static Path treeFile() {
        return directory().resolve(TREE);
    }

    public static ItemTree tree() {
        refresh();
        return tree;
    }

    public static RuleSet ruleSet() {
        refresh();
        return ruleSets.get(Math.min(current, ruleSets.size() - 1));
    }

    /** Switches to the next rule set (sections in rules.txt) and returns it. */
    public static RuleSet nextRuleSet() {
        refresh();
        current = (current + 1) % ruleSets.size();
        return ruleSets.get(current);
    }

    /** Problems found by the last (re)load, then forgotten; shown to the player once. */
    public static List<Component> takeProblems() {
        List<Component> result = List.copyOf(problems);
        problems.clear();
        return result;
    }

    /** Tags changed (joined a world, /reload): item lookups must be redone. */
    public static void onTagsChanged() {
        if (tree != null) {
            tree.clearCache();
        }
    }

    private static synchronized void refresh() {
        Path treePath = treeFile();
        Path rulesPath = rulesFile();
        ensureExists(treePath, TREE);
        ensureExists(rulesPath, RULES);
        long newTreeStamp = stamp(treePath);
        long newRulesStamp = stamp(rulesPath);
        if (tree != null && newTreeStamp == treeStamp && newRulesStamp == rulesStamp) {
            return;
        }
        boolean reloading = tree != null;
        treeStamp = newTreeStamp;
        rulesStamp = newRulesStamp;
        problems.clear();

        tree = ItemTree.parse(read(treePath, TREE));
        for (String error : tree.errors()) {
            problems.add(Component.translatable("inventorysortify.message.tree_error", error));
        }
        ruleSets = parseRules(read(rulesPath, RULES), tree);
        current = Math.min(current, ruleSets.size() - 1);
        if (reloading && problems.isEmpty()) {
            problems.add(Component.translatable("inventorysortify.message.reloaded"));
        }
    }

    private static List<RuleSet> parseRules(List<String> lines, ItemTree tree) {
        List<RuleSet> result = new ArrayList<>();
        RuleSet active = new RuleSet("Default");
        boolean activeIsUnnamed = true;
        int activeLines = 0;
        int lineNumber = 0;
        for (String raw : lines) {
            lineNumber++;
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) {
                continue;
            }
            if (line.endsWith(":") && line.matches("[\\w ]+:")) {
                // A new named rule set; an empty unnamed one before the first name is dropped.
                if (!activeIsUnnamed || activeLines > 0) {
                    active.finish();
                    result.add(active);
                }
                active = new RuleSet(line.substring(0, line.length() - 1).trim());
                activeIsUnnamed = false;
                activeLines = 0;
                continue;
            }
            activeLines++;
            String error = active.addLine(line, tree);
            if (error != null) {
                problems.add(Component.translatable("inventorysortify.message.rule_error", lineNumber, line, error));
            }
        }
        active.finish();
        result.add(active);
        return result;
    }

    private static void ensureExists(Path path, String name) {
        if (Files.exists(path)) {
            return;
        }
        try (InputStream in = SortifyFiles.class.getResourceAsStream("/assets/" + InventorySortify.MOD_ID + "/defaults/" + name)) {
            Files.createDirectories(path.getParent());
            if (in != null) {
                Files.copy(in, path);
            }
        } catch (IOException e) {
            InventorySortify.LOGGER.error("Could not create {}", path, e);
        }
    }

    private static List<String> read(Path path, String name) {
        try {
            return Files.readAllLines(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            InventorySortify.LOGGER.error("Could not read {}, using the default", path, e);
            try (InputStream in = SortifyFiles.class.getResourceAsStream("/assets/" + InventorySortify.MOD_ID + "/defaults/" + name)) {
                return in == null ? List.of() : new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
            } catch (IOException e2) {
                return List.of();
            }
        }
    }

    private static long stamp(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis() ^ Files.size(path);
        } catch (IOException e) {
            return 0;
        }
    }
}
