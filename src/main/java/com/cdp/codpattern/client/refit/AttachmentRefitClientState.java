package com.cdp.codpattern.client.refit;

import com.cdp.codpattern.compat.tacz.TaczGatewayProvider;
import com.cdp.codpattern.compat.tacz.client.CodGunRefitScreen;
import com.cdp.codpattern.core.refit.AttachmentPresetUtil;
import com.cdp.codpattern.network.SaveAttachmentPresetPacket;
import com.cdp.codpattern.adapter.forge.network.ModNetworkChannel;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.util.Optional;

public class AttachmentRefitClientState {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static boolean pendingOpen = false;
    private static boolean activeSession = false;
    private static int bagId = -1;
    private static String slot = "";
    private static String presetPayload = "";
    private static String expectedGunId = "";
    private static Screen parentScreen = null;

    public static void onPresetSync(int bagId, String slot, String payload, String expectedGunId) {
        AttachmentRefitClientState.bagId = bagId;
        AttachmentRefitClientState.slot = slot;
        AttachmentRefitClientState.presetPayload = payload == null ? "" : payload;
        AttachmentRefitClientState.expectedGunId = expectedGunId == null ? "" : expectedGunId;
        pendingOpen = true;
        activeSession = false;
        LOGGER.info("Attachment preset sync received: bagId={} slot={}", bagId, slot);
    }

    public static void setParentScreen(Screen screen) {
        parentScreen = screen;
    }

    public static void tryOpenIfReady() {
        if (!pendingOpen || activeSession) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        ItemStack gunStack = mc.player.getMainHandItem();
        if (!TaczGatewayProvider.gateway().isGun(gunStack)) {
            return;
        }
        if (!expectedGunId.isEmpty()) {
            Optional<String> currentGunId = TaczGatewayProvider.gateway().resolveGunId(gunStack);
            if (currentGunId.isEmpty() || !expectedGunId.equals(currentGunId.get())) {
                return;
            }
        }
        CompoundTag presetTag = AttachmentPresetUtil.parsePresetString(presetPayload);
        if (!presetTag.isEmpty()) {
            AttachmentPresetUtil.applyPresetToGun(gunStack, presetTag);
            TaczGatewayProvider.gateway().postAttachmentChanged(mc.player, gunStack);
        }
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
        parentScreen = null;
    }
}
