package com.phasetranscrystal.fpsmatch.common.item.tool;

import com.mojang.blaze3d.platform.InputConstants;
import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.common.packet.ToolInteractionC2SPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.util.Objects;

@Mod.EventBusSubscriber(modid = FPSMatch.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ToolInteractionClientHandler {
    private static long lastSentGameTime = Long.MIN_VALUE;
    private static ToolInteractionAction lastSentAction;
    private static BlockPos lastSentPos;

    private ToolInteractionClientHandler() {
    }

    private static boolean isControlDown() {
        if (Screen.hasControlDown()) {
            return true;
        }
        Minecraft minecraft = Minecraft.getInstance();
        long window = minecraft.getWindow().getWindow();
        return InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_CONTROL)
                || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_CONTROL);
    }

    @SubscribeEvent
    public static void onInteractionKeyMapping(InputEvent.InteractionKeyMappingTriggered event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.screen != null) {
            return;
        }

        ItemStack stack = minecraft.player.getMainHandItem();
        if (!(stack.getItem() instanceof WorldToolItem)) {
            return;
        }

        if (event.isAttack()) {
            BlockPos target = targetedBlock(minecraft);
            if (target == null) {
                return;
            }
            sendToolInteraction(ToolInteractionAction.LEFT_CLICK_BLOCK, target);
            event.setCanceled(true);
            event.setSwingHand(true);
            return;
        }

        if (!event.isUseItem()) {
            return;
        }

        if (isControlDown()) {
            sendToolInteraction(ToolInteractionAction.CTRL_RIGHT_CLICK, null);
            event.setCanceled(true);
            event.setSwingHand(false);
            return;
        }

        BlockPos target = targetedBlock(minecraft);
        if (target == null) {
            return;
        }
        sendToolInteraction(ToolInteractionAction.RIGHT_CLICK_BLOCK, target);
        event.setCanceled(true);
        event.setSwingHand(true);
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        Player player = event.getEntity();
        Level level = player.level();
        if (!level.isClientSide || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }

        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof WorldToolItem)) {
            return;
        }

        sendToolInteraction(ToolInteractionAction.LEFT_CLICK_BLOCK, event.getPos());
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        Level level = player.level();
        if (!level.isClientSide || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }

        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof WorldToolItem)) {
            return;
        }

        ToolInteractionAction action = isControlDown()
                ? ToolInteractionAction.CTRL_RIGHT_CLICK
                : ToolInteractionAction.RIGHT_CLICK_BLOCK;
        sendToolInteraction(action, action == ToolInteractionAction.CTRL_RIGHT_CLICK ? null : event.getPos());
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        Level level = player.level();
        if (!level.isClientSide || event.getHand() != InteractionHand.MAIN_HAND || !isControlDown()) {
            return;
        }

        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof WorldToolItem)) {
            return;
        }

        sendToolInteraction(ToolInteractionAction.CTRL_RIGHT_CLICK, null);
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    @SubscribeEvent
    public static void onRightClickEmpty(PlayerInteractEvent.RightClickEmpty event) {
        if (!isControlDown()) {
            return;
        }

        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof WorldToolItem)) {
            return;
        }

        sendToolInteraction(ToolInteractionAction.CTRL_RIGHT_CLICK, null);
    }

    private static BlockPos targetedBlock(Minecraft minecraft) {
        if (minecraft.hitResult instanceof BlockHitResult hitResult
                && hitResult.getType() == HitResult.Type.BLOCK) {
            return hitResult.getBlockPos();
        }
        return null;
    }

    private static void sendToolInteraction(ToolInteractionAction action, BlockPos clickedPos) {
        Minecraft minecraft = Minecraft.getInstance();
        long gameTime = minecraft.level == null ? Long.MIN_VALUE : minecraft.level.getGameTime();
        if (gameTime == lastSentGameTime
                && action == lastSentAction
                && Objects.equals(clickedPos, lastSentPos)) {
            return;
        }
        lastSentGameTime = gameTime;
        lastSentAction = action;
        lastSentPos = clickedPos;
        FPSMatch.sendToServer(new ToolInteractionC2SPacket(action, clickedPos));
    }
}
