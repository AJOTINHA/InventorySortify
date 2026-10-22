package com.ajg.inventorysortify.client.sort;

import com.ajg.inventorysortify.client.ClientConfig;
import com.ajg.inventorysortify.client.rules.RuleSet;
import com.ajg.inventorysortify.client.rules.SortifyFiles;
import com.ajg.inventorysortify.network.SortPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.ArrayList;
import java.util.List;

/** Sorts a section of the open menu: plans the layout and asks the server to apply it. */
public final class SortController {
    private SortController() {
    }

    public static void sort(AbstractContainerMenu menu, Section section, SortMode mode) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        ClientPacketListener connection = minecraft.getConnection();
        if (player == null || connection == null || section == null) {
            return;
        }
        if (!connection.hasChannel(SortPayload.TYPE)) {
            player.sendOverlayMessage(Component.translatable("inventorysortify.message.server_missing"));
            return;
        }
        if (!menu.getCarried().isEmpty()) {
            return;
        }

        List<Slot> slots = section.slots();
        ItemStack[] current = new ItemStack[slots.size()];
        for (int i = 0; i < current.length; i++) {
            current[i] = slots.get(i).getItem().copy();
        }
        RuleSet rules = section.playerInventory() ? SortifyFiles.ruleSet() : null;
        ItemStack[] layout = SortPlanner.plan(current, section.rowSize(), section.playerInventory() ? SortMode.DEFAULT : mode,
                rules, SortifyFiles.tree());
        for (Component problem : SortifyFiles.takeProblems()) {
            player.sendSystemMessage(Component.translatable("inventorysortify.message.prefix").append(problem));
        }
        if (layout == null || sameLayout(current, layout)) {
            return;
        }

        List<Integer> ids = new ArrayList<>(slots.size());
        for (Slot slot : slots) {
            ids.add(slot.index);
        }
        ClientPacketDistributor.sendToServer(new SortPayload(menu.containerId, ids, List.of(layout)));
        if (ClientConfig.SOUNDS.getAsBoolean()) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ITEM_PICKUP, 1.6F, 0.4F));
        }
    }

    private static boolean sameLayout(ItemStack[] a, ItemStack[] b) {
        for (int i = 0; i < a.length; i++) {
            if (!ItemStack.matches(a[i], b[i])) {
                return false;
            }
        }
        return true;
    }
}
