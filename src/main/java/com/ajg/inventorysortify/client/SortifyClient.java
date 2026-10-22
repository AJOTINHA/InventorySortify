package com.ajg.inventorysortify.client;

import com.ajg.inventorysortify.InventorySortify;
import com.ajg.inventorysortify.client.rules.RuleSet;
import com.ajg.inventorysortify.client.rules.SortifyFiles;
import com.ajg.inventorysortify.client.sort.Section;
import com.ajg.inventorysortify.client.sort.SortController;
import com.ajg.inventorysortify.client.sort.SortMode;
import com.ajg.inventorysortify.client.sort.TransferController;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.Slot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.TagsUpdatedEvent;

@Mod(value = InventorySortify.MOD_ID, dist = Dist.CLIENT)
public class SortifyClient {
    private static final KeyMapping.Category CATEGORY = new KeyMapping.Category(Identifier.fromNamespaceAndPath(InventorySortify.MOD_ID, "main"));
    private static final KeyMapping SORT_KEY = new KeyMapping("key.inventorysortify.sort", KeyConflictContext.GUI,
            InputConstants.Type.KEYBOARD, InputConstants.KEY_R, CATEGORY);
    private static final KeyMapping NEXT_RULES_KEY = new KeyMapping("key.inventorysortify.next_rules", KeyConflictContext.UNIVERSAL,
            InputConstants.Type.KEYBOARD, InputConstants.UNKNOWN.getValue(), CATEGORY);

    private static ModContainer modContainer;

    public SortifyClient(IEventBus modBus, ModContainer container) {
        modContainer = container;
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        modBus.addListener(SortifyClient::registerKeys);
        modBus.addListener((RegisterGuiLayersEvent event) -> event.registerAbove(VanillaGuiLayers.HOTBAR, FilterMarks.HOTBAR_LAYER, FilterMarks::drawHotbar));

        NeoForge.EVENT_BUS.addListener(SortifyClient::onScreenInit);
        NeoForge.EVENT_BUS.addListener(SortifyClient::onScreenRender);
        NeoForge.EVENT_BUS.addListener(SortifyClient::onScreenForeground);
        NeoForge.EVENT_BUS.addListener(SortifyClient::onKeyPressed);
        NeoForge.EVENT_BUS.addListener(SortifyClient::onMouseClicked);
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> onClientTick());
        NeoForge.EVENT_BUS.addListener((TagsUpdatedEvent event) -> SortifyFiles.onTagsChanged());
    }

    private static void registerKeys(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        event.register(SORT_KEY);
        event.register(NEXT_RULES_KEY);
    }

    /** Container screens we can sort in; the creative inventory works differently and is left alone. */
    private static AbstractContainerScreen<?> sortableScreen(Screen screen) {
        if (screen instanceof AbstractContainerScreen<?> containerScreen && !(screen instanceof CreativeModeInventoryScreen)) {
            return containerScreen;
        }
        return null;
    }

    private static void onScreenInit(ScreenEvent.Init.Post event) {
        AbstractContainerScreen<?> screen = sortableScreen(event.getScreen());
        if (screen == null || !ClientConfig.SORT_BUTTONS.getAsBoolean()) {
            return;
        }
        Section container = Section.mainContainer(screen.getMenu());
        if (container != null) {
            event.addListener(new SortButton(screen, container, 0, SortButton.Icon.SORT, Component.translatable("inventorysortify.button.sort"),
                    () -> SortController.sort(screen.getMenu(), container, SortMode.DEFAULT)));
        }
        Section inventory = Section.playerInventory(screen.getMenu());
        if (inventory != null) {
            event.addListener(new SortButton(screen, inventory, 0, SortButton.Icon.SORT, Component.translatable("inventorysortify.button.inventory"),
                    () -> SortController.sort(screen.getMenu(), inventory, SortMode.DEFAULT)));
            if (container != null) {
                // What each arrow moves is chosen in the config: everything, or only items the other side already has.
                String deposit = ClientConfig.DEPOSIT_ONLY_MATCHING.getAsBoolean() ? "deposit_matching" : "deposit_all";
                event.addListener(new SortButton(screen, inventory, 1, SortButton.Icon.DEPOSIT, Component.translatable("inventorysortify.button." + deposit),
                        () -> TransferController.toContainer(screen.getMenu(), inventory, container)));
                String take = ClientConfig.TAKE_ONLY_MATCHING.getAsBoolean() ? "take_matching" : "take_all";
                event.addListener(new SortButton(screen, container, 1, SortButton.Icon.TAKE, Component.translatable("inventorysortify.button." + take),
                        () -> TransferController.toInventory(screen.getMenu(), container, inventory)));
            }
            if (screen instanceof InventoryScreen) {
                event.addListener(new SortButton(screen, inventory, 1, SortButton.Icon.FILTER, Component.translatable("inventorysortify.button.filter"),
                        () -> Minecraft.getInstance().gui.pushScreenLayer(new FilterScreen())));
                // The config screen goes back to its parent (the inventory) when closed.
                event.addListener(new SortButton(screen, inventory, 2, SortButton.Icon.SETTINGS, Component.translatable("inventorysortify.button.settings"),
                        () -> Minecraft.getInstance().gui.setScreen(new ConfigurationScreen(modContainer, screen))));
            }
        }
    }

    private static void onScreenRender(ScreenEvent.Render.Pre event) {
        for (GuiEventListener child : event.getScreen().children()) {
            if (child instanceof SortButton button) {
                button.reposition();
            }
        }
    }

    /** Fired after the slots are drawn and before the carried item and tooltip. */
    private static void onScreenForeground(ScreenEvent.Render.Foreground event) {
        AbstractContainerScreen<?> screen = sortableScreen(event.getScreen());
        if (screen != null && ClientConfig.FILTER_MARKS.getAsBoolean()) {
            FilterMarks.draw(screen, event.getGuiGraphics());
        }
    }

    private static void onKeyPressed(ScreenEvent.KeyPressed.Post event) {
        AbstractContainerScreen<?> screen = sortableScreen(event.getScreen());
        if (screen == null || !SORT_KEY.isActiveAndMatches(InputConstants.getKey(event.getKeyEvent()))) {
            return;
        }
        sortAt(screen, screen.getHoveredSlot());
        event.setCanceled(true);
    }

    private static void onMouseClicked(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getButton() != InputConstants.MOUSE_BUTTON_MIDDLE || !ClientConfig.MIDDLE_CLICK.getAsBoolean()) {
            return;
        }
        AbstractContainerScreen<?> screen = sortableScreen(event.getScreen());
        Minecraft minecraft = Minecraft.getInstance();
        if (screen == null || minecraft.player == null || minecraft.player.hasInfiniteMaterials()) {
            return; // in creative, middle click copies items
        }
        Slot slot = screen.getHoveredSlot();
        if (slot != null && Section.of(screen.getMenu(), slot) != null) {
            sortAt(screen, slot);
            event.setCanceled(true);
        }
    }

    /** Sorts the section under the mouse; without one, the container (or the inventory if there is none). */
    private static void sortAt(AbstractContainerScreen<?> screen, Slot slot) {
        Section section = slot != null ? Section.of(screen.getMenu(), slot) : null;
        if (section == null) {
            section = Section.mainContainer(screen.getMenu());
        }
        if (section == null) {
            section = Section.playerInventory(screen.getMenu());
        }
        SortController.sort(screen.getMenu(), section, SortMode.DEFAULT);
    }

    private static void onClientTick() {
        AutoRefill.tick();
        Minecraft minecraft = Minecraft.getInstance();
        while (NEXT_RULES_KEY.consumeClick()) {
            RuleSet ruleSet = SortifyFiles.nextRuleSet();
            if (minecraft.player != null) {
                minecraft.player.sendOverlayMessage(Component.translatable("inventorysortify.message.rule_set", ruleSet.name()));
            }
        }
    }
}
