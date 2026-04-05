package com.cdp.codpattern.event.client;

import com.cdp.codpattern.CodPattern;
import com.cdp.codpattern.adapter.forge.network.ModNetworkChannel;
import com.cdp.codpattern.client.ClientTdmState;
import com.cdp.codpattern.client.input.ThrowableKeyMappings;
import com.cdp.codpattern.config.weaponfilter.WeaponFilterClientCache;
import com.cdp.codpattern.config.weaponfilter.WeaponFilterConfig;
import com.cdp.codpattern.core.throwable.ThrowableInventoryService;
import com.cdp.codpattern.core.throwable.ThrowableItemHelper;
import com.cdp.codpattern.core.throwable.ThrowableInventoryState;
import com.cdp.codpattern.core.throwable.ThrowableStopReason;
import com.cdp.codpattern.network.StartThrowableUsePacket;
import com.cdp.codpattern.network.StopThrowableUsePacket;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = CodPattern.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ThrowableClientInputHandler {
    private static boolean slotOneWasDown;
    private static boolean slotTwoWasDown;
    private static ThrowableClientUsePrediction activePrediction;
    private static boolean syntheticUseHeld;

    private ThrowableClientInputHandler() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            reset();
            return;
        }

        if (activePrediction != null
                && ThrowableInventoryService.getActiveSlot(player) == ThrowableInventoryState.ACTIVE_SLOT_NONE
                && !player.isUsingItem()) {
            clearPrediction();
        }

        boolean slotOneDown = minecraft.screen == null && ThrowableKeyMappings.THROWABLE_SLOT_ONE.isDown();
        boolean slotTwoDown = minecraft.screen == null && ThrowableKeyMappings.THROWABLE_SLOT_TWO.isDown();

        handleKeyTransition(player, ThrowableInventoryService.SLOT_ONE, slotOneDown, slotOneWasDown);
        handleKeyTransition(player, ThrowableInventoryService.SLOT_TWO, slotTwoDown, slotTwoWasDown);
        syncUseKeyState(minecraft);

        slotOneWasDown = slotOneDown;
        slotTwoWasDown = slotTwoDown;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.screen != null || !ClientTdmState.hasRoomContext()) {
            return;
        }

        if (hasActiveThrowableInteraction(player)) {
            if (player.isUsingItem()) {
                player.stopUsingItem();
            }
            rollbackPrediction(player);
            ModNetworkChannel.sendToServer(new StopThrowableUsePacket(ThrowableStopReason.SCROLL_CANCEL));
            event.setCanceled(true);
            return;
        }

        int scrollStep = (int) Math.signum(event.getScrollDelta());
        if (scrollStep == 0) {
            return;
        }

        Inventory inventory = player.getInventory();
        int currentSlot = inventory.selected;
        int nextSlot = currentSlot;
        for (int i = 0; i < 9; i++) {
            nextSlot = Math.floorMod(nextSlot - scrollStep, 9);
            ItemStack candidate = inventory.getItem(nextSlot);
            if (!candidate.isEmpty()) {
                if (nextSlot == currentSlot) {
                    event.setCanceled(true);
                    return;
                }
                inventory.selected = nextSlot;
                if (player.connection != null) {
                    player.connection.send(new ServerboundSetCarriedItemPacket(nextSlot));
                }
                event.setCanceled(true);
                return;
            }
        }

        event.setCanceled(true);
    }

    public static void reset() {
        slotOneWasDown = false;
        slotTwoWasDown = false;
        activePrediction = null;
        syntheticUseHeld = false;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options != null) {
            minecraft.options.keyUse.setDown(resolvePhysicalUseKeyDown(minecraft));
        }
    }

    private static void handleKeyTransition(LocalPlayer player, int slotIndex, boolean isDown, boolean wasDown) {
        if (isDown == wasDown) {
            return;
        }
        if (isDown) {
            if (canStartThrowableInput(player) && startLocalPrediction(player, slotIndex)) {
                syntheticUseHeld = true;
                ModNetworkChannel.sendToServer(new StartThrowableUsePacket(slotIndex));
                beginLocalUse(player);
            }
            return;
        }
        if (player.isUsingItem()) {
            if (hasReachedPrepareThreshold(player)) {
                syntheticUseHeld = false;
                releaseLocalUse(player);
                clearPrediction();
            } else {
                syntheticUseHeld = false;
                player.stopUsingItem();
                rollbackPrediction(player);
            }
        } else {
            syntheticUseHeld = false;
            rollbackPrediction(player);
        }
        ModNetworkChannel.sendToServer(new StopThrowableUsePacket(ThrowableStopReason.KEY_UP));
    }

    private static boolean canStartThrowableInput(LocalPlayer player) {
        if (player == null
                || player.isSpectator()
                || !ClientTdmState.hasRoomContext()
                || hasActiveThrowableInteraction(player)
                || player.isUsingItem()) {
            return false;
        }
        WeaponFilterConfig config = WeaponFilterClientCache.get();
        return config == null || config.isThrowablesEnabled();
    }

    private static boolean hasActiveThrowableInteraction(LocalPlayer player) {
        return activePrediction != null
                || ThrowableInventoryService.getActiveSlot(player) != ThrowableInventoryState.ACTIVE_SLOT_NONE;
    }

    private static boolean startLocalPrediction(LocalPlayer player, int slotIndex) {
        ItemStack dedicatedStack = ThrowableInventoryService.getDisplayStack(player, slotIndex).copy();
        if (!ThrowableItemHelper.isThrowableStack(dedicatedStack)) {
            return false;
        }

        Inventory inventory = player.getInventory();
        int hotbarSlot = inventory.selected;
        ItemStack original = inventory.getItem(hotbarSlot).copy();
        activePrediction = new ThrowableClientUsePrediction(slotIndex, hotbarSlot, original);

        ThrowableInventoryService.getState(player).ifPresent(state -> {
            state.getContainer().replaceItem(slotIndex, ItemStack.EMPTY);
            state.setActiveSlot(slotIndex);
        });
        inventory.setItem(hotbarSlot, dedicatedStack);
        return true;
    }

    private static void beginLocalUse(LocalPlayer player) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gameMode == null) {
            return;
        }
        InteractionResult result = minecraft.gameMode.useItem(player, InteractionHand.MAIN_HAND);
        if (!result.consumesAction() && !player.isUsingItem()) {
            rollbackPrediction(player);
        }
    }

    private static void releaseLocalUse(LocalPlayer player) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gameMode != null) {
            minecraft.gameMode.releaseUsingItem(player);
        } else {
            player.releaseUsingItem();
        }
    }

    private static boolean hasReachedPrepareThreshold(LocalPlayer player) {
        ItemStack stack = player.getUseItem();
        if (stack.isEmpty()) {
            return false;
        }
        return player.getTicksUsingItem() >= ThrowableItemHelper.getPrepareTicks(stack);
    }

    private static void rollbackPrediction(LocalPlayer player) {
        if (activePrediction == null || player == null) {
            clearPrediction();
            return;
        }

        Inventory inventory = player.getInventory();
        ItemStack predictedThrowable = inventory.getItem(activePrediction.hotbarSlotIndex()).copy();
        inventory.setItem(activePrediction.hotbarSlotIndex(), activePrediction.originalHotbarStack().copy());
        ThrowableInventoryService.getState(player).ifPresent(state -> {
            if (ThrowableItemHelper.isThrowableStack(predictedThrowable)) {
                state.getContainer().replaceItem(activePrediction.dedicatedSlotIndex(), predictedThrowable);
            }
            state.setActiveSlot(ThrowableInventoryState.ACTIVE_SLOT_NONE);
        });
        clearPrediction();
    }

    private static void clearPrediction() {
        activePrediction = null;
    }

    private static void syncUseKeyState(Minecraft minecraft) {
        if (minecraft == null || minecraft.options == null) {
            return;
        }
        minecraft.options.keyUse.setDown(syntheticUseHeld || resolvePhysicalUseKeyDown(minecraft));
    }

    private static boolean resolvePhysicalUseKeyDown(Minecraft minecraft) {
        if (minecraft == null || minecraft.options == null) {
            return false;
        }

        KeyMapping useKey = minecraft.options.keyUse;
        InputConstants.Key key = useKey.getKey();
        long window = minecraft.getWindow().getWindow();
        return switch (key.getType()) {
            case MOUSE -> GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
            case KEYSYM, SCANCODE -> InputConstants.isKeyDown(window, key.getValue());
        };
    }
}
