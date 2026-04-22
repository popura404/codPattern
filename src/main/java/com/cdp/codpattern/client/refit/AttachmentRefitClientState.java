package com.cdp.codpattern.client.refit;

import com.cdp.codpattern.compat.tacz.TaczGatewayProvider;
import com.cdp.codpattern.client.gui.screen.BackpackMenuScreen;
import com.cdp.codpattern.compat.tacz.client.CodGunRefitScreen;
import com.cdp.codpattern.core.refit.BackpackRefitSessionContext;
import com.cdp.codpattern.core.refit.AttachmentRefitInventory;
import com.cdp.codpattern.core.refit.AttachmentPresetUtil;
import com.cdp.codpattern.client.gui.screen.WeaponMenuScreen;
import com.cdp.codpattern.network.SaveAttachmentPresetPacket;
import com.cdp.codpattern.adapter.forge.network.ModNetworkChannel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class AttachmentRefitClientState {
    private static boolean pendingOpen = false;
    private static boolean activeSession = false;
    private static int bagId = -1;
    private static String slot = "";
    private static String presetPayload = "";
    private static String expectedGunId = "";
    private static String requestedGunId = "";
    private static boolean attachmentCandidatesReady = false;
    private static List<ItemStack> attachmentCandidates = List.of();
    private static Screen parentScreen = null;

    public static void prepareOpenRequest(int bagId, String slot, String requestedGunId, Screen screen) {
        AttachmentRefitClientState.bagId = bagId;
        AttachmentRefitClientState.slot = slot == null ? "" : slot;
        AttachmentRefitClientState.presetPayload = "";
        AttachmentRefitClientState.expectedGunId = "";
        AttachmentRefitClientState.requestedGunId = requestedGunId == null ? "" : requestedGunId;
        attachmentCandidatesReady = false;
        attachmentCandidates = List.of();
        pendingOpen = false;
        activeSession = false;
        BackpackRefitSessionContext.setClientActive(false);
        parentScreen = screen;
    }

    public static void onPresetSync(int bagId, String slot, String payload, String expectedGunId) {
        boolean preserveCandidates = AttachmentRefitClientState.bagId == bagId
                && AttachmentRefitClientState.slot.equals(slot)
                && attachmentCandidatesReady;
        AttachmentRefitClientState.bagId = bagId;
        AttachmentRefitClientState.slot = slot;
        AttachmentRefitClientState.presetPayload = payload == null ? "" : payload;
        AttachmentRefitClientState.expectedGunId = expectedGunId == null ? "" : expectedGunId;
        if (!preserveCandidates) {
            attachmentCandidatesReady = false;
            attachmentCandidates = List.of();
        }
        if (activeSession) {
            return;
        }
        BackpackRefitSessionContext.setClientActive(false);
        pendingOpen = true;
    }

    public static void onAttachmentCandidatesSync(int bagId, String slot, List<ItemStack> candidates) {
        if (AttachmentRefitClientState.bagId != bagId || !AttachmentRefitClientState.slot.equals(slot)) {
            return;
        }
        attachmentCandidates = snapshotCandidates(candidates);
        attachmentCandidatesReady = true;
        Minecraft mc = Minecraft.getInstance();
        if (activeSession && mc.screen instanceof CodGunRefitScreen refitScreen) {
            refitScreen.init();
        }
    }

    public static List<ItemStack> getAttachmentCandidates() {
        return snapshotCandidates(attachmentCandidates);
    }

    public static Inventory resolveRefitScreenInventory(LocalPlayer player) {
        if (player == null) {
            return null;
        }
        if (!BackpackRefitSessionContext.isBackpackRefitActive(player)) {
            return player.getInventory();
        }
        ItemStack gunStack = player.getMainHandItem();
        if (!TaczGatewayProvider.gateway().isGun(gunStack)) {
            return player.getInventory();
        }
        if (!attachmentCandidatesReady) {
            return player.getInventory();
        }
        return new AttachmentRefitInventory(
                player,
                player.getInventory().selected,
                gunStack,
                new ArrayList<>(getAttachmentCandidates()));
    }

    public static void tryOpenIfReady() {
        if (activeSession) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        if (!pendingOpen) {
            return;
        }
        if (parentScreen != null
                && mc.screen != parentScreen
                && !(mc.screen instanceof WeaponMenuScreen)
                && !(mc.screen instanceof BackpackMenuScreen)) {
            return;
        }
        ItemStack gunStack = mc.player.getMainHandItem();
        if (!TaczGatewayProvider.gateway().isGun(gunStack)) {
            return;
        }
        String targetGunId = expectedGunId.isEmpty() ? requestedGunId : expectedGunId;
        if (!targetGunId.isEmpty()) {
            Optional<String> currentGunId = TaczGatewayProvider.gateway().resolveGunId(gunStack);
            if (currentGunId.isEmpty() || !targetGunId.equals(currentGunId.get())) {
                return;
            }
        }
        CompoundTag presetTag = AttachmentPresetUtil.parsePresetString(presetPayload);
        if (!presetTag.isEmpty()) {
            AttachmentPresetUtil.applyPresetToGun(gunStack, presetTag);
            TaczGatewayProvider.gateway().postAttachmentChanged(mc.player, gunStack);
        }
        BackpackRefitSessionContext.setClientActive(true);
        mc.setScreen(new CodGunRefitScreen(parentScreen));
        pendingOpen = false;
        activeSession = true;
    }

    public static void onRefitScreenClosed() {
        if (!activeSession) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            reset();
            return;
        }
        ItemStack gunStack = mc.player.getMainHandItem();
        if (!TaczGatewayProvider.gateway().isGun(gunStack)) {
            reset();
            return;
        }
        String nbtString = gunStack.hasTag() ? gunStack.getTag().toString() : "";
        String payload = AttachmentPresetUtil.buildPresetFromGun(gunStack).toString();
        ModNetworkChannel.sendToServer(new SaveAttachmentPresetPacket(bagId, slot, payload, nbtString));
        reset();
    }

    private static void reset() {
        pendingOpen = false;
        activeSession = false;
        bagId = -1;
        slot = "";
        presetPayload = "";
        expectedGunId = "";
        requestedGunId = "";
        attachmentCandidatesReady = false;
        attachmentCandidates = List.of();
        BackpackRefitSessionContext.setClientActive(false);
        parentScreen = null;
    }

    private static List<ItemStack> snapshotCandidates(List<ItemStack> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        List<ItemStack> snapshot = new ArrayList<>(candidates.size());
        for (ItemStack candidate : candidates) {
            if (candidate == null || candidate.isEmpty()) {
                continue;
            }
            snapshot.add(candidate.copy());
        }
        return List.copyOf(snapshot);
    }
}
