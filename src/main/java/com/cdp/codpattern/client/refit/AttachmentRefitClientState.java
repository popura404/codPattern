package com.cdp.codpattern.client.refit;

import com.cdp.codpattern.adapter.forge.network.ModNetworkChannel;
import com.cdp.codpattern.client.gui.screen.BackpackMenuScreen;
import com.cdp.codpattern.client.gui.screen.WeaponMenuScreen;
import com.cdp.codpattern.compat.tacz.TaczGatewayProvider;
import com.cdp.codpattern.compat.tacz.client.CodGunRefitScreen;
import com.cdp.codpattern.core.refit.AttachmentRefitInventory;
import com.cdp.codpattern.core.refit.AttachmentCandidateTransfer;
import com.cdp.codpattern.core.refit.BackpackRefitSessionContext;
import com.cdp.codpattern.core.refit.RefitAction;
import com.cdp.codpattern.network.RefitActionPacket;
import com.cdp.codpattern.network.SyncAttachmentCandidatesPacket;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Client lifecycle and the one-request-at-a-time queue for a server-owned refit session. */
public final class AttachmentRefitClientState {
    private static boolean pendingOpen;
    private static boolean activeSession;
    private static int bagId = -1;
    private static String slot = "";
    private static int gunSlot = -1;
    private static boolean attachmentCandidatesReady;
    private static List<ItemStack> attachmentCandidates = List.of();
    private static Screen parentScreen;
    private static final RefitOperationQueue<RefitAction> operations = new RefitOperationQueue<>();
    private static final AttachmentCandidateTransfer candidateTransfer = new AttachmentCandidateTransfer();

    private AttachmentRefitClientState() {
    }

    public static boolean prepareOpenRequest(int bagId, String slot, String requestedGunId, Screen screen) {
        if (operations.sessionId() != null) {
            showMessage(Component.translatable("message.codpattern.refit.pending"));
            return false;
        }
        reset();
        operations.begin(UUID.randomUUID());
        AttachmentRefitClientState.bagId = bagId;
        AttachmentRefitClientState.slot = slot == null ? "" : slot;
        parentScreen = screen;
        return true;
    }

    public static UUID getSessionId() {
        return operations.sessionId();
    }

    public static void onPresetSync(UUID incomingSessionId, int bagId, String slot, String payload,
            String expectedGunId, ItemStack workingGun, int gunSlot) {
        if (!matches(incomingSessionId, bagId, slot) || operations.isClosing()) {
            return;
        }
        if (expectedGunId == null
                || TaczGatewayProvider.gateway().resolveRefitGunId(workingGun).filter(expectedGunId::equals).isEmpty()
                || !applyWorkingGun(workingGun, gunSlot)) {
            cancelSession();
            return;
        }
        if (!activeSession) {
            pendingOpen = true;
        }
    }

    public static void onAttachmentCandidatesSync(UUID incomingSessionId, int bagId, String slot,
            List<ItemStack> candidates) {
        if (!matches(incomingSessionId, bagId, slot)) {
            return;
        }
        attachmentCandidates = snapshotCandidates(candidates);
        attachmentCandidatesReady = true;
        refreshScreen();
    }

    public static void onAttachmentCandidateChunk(UUID incomingSessionId, int bagId, String slot,
            int offset, int totalBytes, boolean complete, byte[] bytes) {
        if (!matches(incomingSessionId, bagId, slot)) {
            return;
        }
        try {
            byte[] snapshot = candidateTransfer.accept(offset, totalBytes, complete, bytes);
            if (snapshot != null) {
                onAttachmentCandidatesSync(incomingSessionId, bagId, slot,
                        SyncAttachmentCandidatesPacket.readSnapshot(snapshot));
            }
        } catch (RuntimeException failure) {
            LogUtils.getLogger().error("Could not assemble attachment candidate snapshot", failure);
            showMessage(Component.translatable("message.codpattern.refit.request_failed"));
            cancelSession();
        }
    }

    public static List<ItemStack> getAttachmentCandidates() {
        return snapshotCandidates(attachmentCandidates);
    }

    public static Inventory resolveRefitScreenInventory(LocalPlayer player) {
        if (player == null) {
            return null;
        }
        if (!BackpackRefitSessionContext.isBackpackRefitActive(player) || !attachmentCandidatesReady
                || gunSlot < 0 || gunSlot >= 9) {
            return player.getInventory();
        }
        ItemStack gunStack = player.getInventory().getItem(gunSlot);
        if (!TaczGatewayProvider.gateway().isGun(gunStack)) {
            return player.getInventory();
        }
        return new AttachmentRefitInventory(player, gunSlot, gunStack, attachmentCandidates);
    }

    public static void tryOpenIfReady() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            reset();
            return;
        }
        if (activeSession || !pendingOpen || operations.isClosing() || !attachmentCandidatesReady) {
            return;
        }
        if (parentScreen != null && mc.screen != parentScreen
                && !(mc.screen instanceof WeaponMenuScreen) && !(mc.screen instanceof BackpackMenuScreen)) {
            cancelSession();
            return;
        }
        if (gunSlot < 0 || gunSlot >= 9
                || !TaczGatewayProvider.gateway().isGun(mc.player.getInventory().getItem(gunSlot))) {
            cancelSession();
            return;
        }
        mc.player.getInventory().selected = gunSlot;
        pendingOpen = false;
        activeSession = true;
        BackpackRefitSessionContext.setClientActive(true);
        mc.setScreen(new CodGunRefitScreen(parentScreen));
    }

    /** Only messages from this screen belong to the CDP preview, even while a save is pending. */
    public static boolean acceptsTaczOperations() {
        return operations.sessionId() != null && activeSession && Minecraft.getInstance().screen instanceof CodGunRefitScreen;
    }

    public static void enqueueOperation(RefitAction action) {
        if (operations.sessionId() == null || !activeSession || operations.isClosing() || action == null) {
            return;
        }
        operations.enqueue(action);
        sendNextAction();
    }

    public static void onLaserColorEdited(int targetSlot, AttachmentType type, int color) {
        if (!acceptsTaczOperations() || operations.isClosing() || targetSlot != gunSlot || type == null) return;
        RefitAction edit = type == AttachmentType.NONE
                ? RefitAction.laser(gunSlot, Map.of(), true, color)
                : RefitAction.laser(gunSlot, Map.of(type.name(), color), false, 0);
        try {
            operations.enqueueReplacingTail(edit, RefitLaserPreview::sameTarget);
            ItemStack gun = Minecraft.getInstance().player.getInventory().getItem(gunSlot);
            RefitLaserPreview.applyPending(gun, operations.snapshot());
            sendNextAction();
        } catch (RuntimeException failure) {
            onOperationTranslationFailed(failure);
        }
    }

    public static void onRefitScreenClosed() {
        if (!activeSession || operations.sessionId() == null || operations.isClosing()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            reset();
            return;
        }
        // Slider deltas are already queued in UI order. A close-time whole-gun color
        // snapshot could belong to a gun state preceding an unacknowledged installation.
        operations.close(List.of(RefitAction.save()));
        BackpackRefitSessionContext.setClientActive(false);
        sendNextAction();
    }

    public static void onOperationResult(UUID incomingSessionId, boolean success, boolean finished,
            Component message, ItemStack workingGun, int gunSlot) {
        if (!operations.matches(incomingSessionId)) {
            return;
        }
        RefitAction completed = operations.inFlight();
        operations.acknowledge(incomingSessionId);
        if (!success && message != null && !message.getString().isBlank()) {
            showMessage(message);
        }
        if (finished) {
            Screen returnTo = parentScreen;
            reset();
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof CodGunRefitScreen) {
                mc.setScreen(returnTo);
            }
            // The server already restored the real inventory. Never overwrite it with
            // the preview gun contained in a terminal response.
            return;
        }
        if (!applyWorkingGun(workingGun, gunSlot)) {
            cancelSession();
            return;
        }
        // Rebuilding sliders on every color ACK would interrupt an active drag.
        if (!success || completed == null || completed.kind() != RefitAction.Kind.LASER) refreshScreen();
        sendNextAction();
    }

    public static void onRequestSendFailed(RuntimeException failure) {
        LogUtils.getLogger().error("Could not send attachment refit request", failure);
        showMessage(Component.translatable("message.codpattern.refit.request_failed"));
        reset();
    }

    public static void onOperationTranslationFailed(RuntimeException failure) {
        LogUtils.getLogger().error("Could not translate attachment refit operation", failure);
        showMessage(Component.translatable("message.codpattern.refit.request_failed"));
        cancelSession();
    }

    public static void onDisconnected() {
        reset();
    }

    private static void cancelSession() {
        if (operations.sessionId() == null) {
            return;
        }
        pendingOpen = false;
        BackpackRefitSessionContext.setClientActive(false);
        operations.cancel(RefitAction.cancel());
        sendNextAction();
    }

    private static void sendNextAction() {
        if (operations.sessionId() == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) {
            reset();
            return;
        }
        RefitAction action = operations.next();
        if (action == null) {
            return;
        }
        try {
            ModNetworkChannel.sendToServer(new RefitActionPacket(operations.sessionId(), action));
        } catch (RuntimeException failure) {
            LogUtils.getLogger().error("Could not send attachment refit operation", failure);
            showMessage(Component.translatable("message.codpattern.refit.request_failed"));
            operations.acknowledge(operations.sessionId());
            if (action.kind() != RefitAction.Kind.CANCEL) {
                cancelSession();
            }
            // If even cancellation could not be transmitted, keep this session guarded
            // until disconnect/server cleanup; do not route further clicks to real inventory.
        }
    }

    private static boolean applyWorkingGun(ItemStack workingGun, int targetSlot) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || targetSlot < 0 || targetSlot >= 9
                || workingGun == null || !TaczGatewayProvider.gateway().isGun(workingGun)) {
            return false;
        }
        gunSlot = targetSlot;
        ItemStack authoritativeGun = workingGun.copy();
        RefitLaserPreview.applyPending(authoritativeGun, operations.snapshot());
        mc.player.getInventory().setItem(targetSlot, authoritativeGun);
        mc.player.getInventory().selected = targetSlot;
        TaczGatewayProvider.gateway().postAttachmentChanged(mc.player, authoritativeGun);
        return true;
    }

    private static boolean matches(UUID incomingSessionId, int incomingBagId, String incomingSlot) {
        return operations.matches(incomingSessionId)
                && bagId == incomingBagId && slot.equals(incomingSlot);
    }

    private static void refreshScreen() {
        Minecraft mc = Minecraft.getInstance();
        if (activeSession && !operations.isClosing() && mc.screen instanceof CodGunRefitScreen refitScreen) {
            refitScreen.setFocused(null);
            refitScreen.setDragging(false);
            refitScreen.init();
        }
    }

    private static void showMessage(Component message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendSystemMessage(message);
        }
    }

    private static void reset() {
        operations.reset();
        candidateTransfer.reset();
        pendingOpen = false;
        activeSession = false;
        bagId = -1;
        slot = "";
        gunSlot = -1;
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
            snapshot.add(candidate == null ? ItemStack.EMPTY : candidate.copy());
        }
        return List.copyOf(snapshot);
    }
}
