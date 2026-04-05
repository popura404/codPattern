package com.cdp.codpattern.core.throwable;

import com.cdp.codpattern.adapter.forge.network.ModNetworkChannel;
import com.cdp.codpattern.compat.fpsmatch.FpsMatchGatewayProvider;
import com.cdp.codpattern.config.path.ConfigPath;
import com.cdp.codpattern.config.weaponfilter.WeaponFilterConfig;
import com.cdp.codpattern.config.weaponfilter.WeaponFilterConfigRepository;
import com.cdp.codpattern.network.SyncThrowableInventoryPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ThrowableInventoryService {
    public static final int SLOT_COUNT = ThrowableInventoryState.SLOT_COUNT;
    public static final int MENU_SLOT_START = 46;
    public static final int SLOT_ONE = 0;
    public static final int SLOT_TWO = 1;

    private static final Map<UUID, ThrowableUseSession> ACTIVE_SESSIONS = new ConcurrentHashMap<>();

    private ThrowableInventoryService() {
    }

    public static Optional<ThrowableInventoryState> getState(Player player) {
        return ThrowableInventoryCapability.get(player);
    }

    public static int getActiveSlot(Player player) {
        return getState(player)
                .map(ThrowableInventoryState::getActiveSlot)
                .orElse(ThrowableInventoryState.ACTIVE_SLOT_NONE);
    }

    public static ItemStack getDisplayStack(Player player, int slotIndex) {
        if (player == null || !isValidSlot(slotIndex)) {
            return ItemStack.EMPTY;
        }
        ThrowableInventoryState state = getState(player).orElse(null);
        if (state == null) {
            return ItemStack.EMPTY;
        }
        ItemStack stored = state.getContainer().getItem(slotIndex);
        if (!stored.isEmpty()) {
            return stored;
        }
        if (state.getActiveSlot() == slotIndex) {
            ItemStack handStack = player.getMainHandItem();
            if (ThrowableItemHelper.isThrowableStack(handStack)) {
                return handStack;
            }
        }
        return ItemStack.EMPTY;
    }

    public static void applyClientSync(Player player, ItemStack[] stacks, int activeSlot) {
        if (player == null) {
            return;
        }
        getState(player).ifPresent(state -> state.applySync(stacks, activeSlot));
    }

    public static void seedThrowableSlot(ServerPlayer player, int slotIndex, ItemStack stack) {
        if (player == null || !isValidSlot(slotIndex)) {
            return;
        }
        getState(player).ifPresent(state -> {
            ItemStack value = ThrowableItemHelper.isThrowableStack(stack) ? stack.copy() : ItemStack.EMPTY;
            state.getContainer().replaceItem(slotIndex, value);
            state.markDirty();
        });
    }

    public static void clearRuntime(ServerPlayer player) {
        clearRuntime(player, false);
    }

    public static void clearRuntime(ServerPlayer player, boolean syncImmediately) {
        if (player == null) {
            return;
        }
        ACTIVE_SESSIONS.remove(player.getUUID());
        getState(player).ifPresent(state -> {
            for (int i = 0; i < SLOT_COUNT; i++) {
                state.getContainer().replaceItem(i, ItemStack.EMPTY);
            }
            state.setActiveSlot(ThrowableInventoryState.ACTIVE_SLOT_NONE);
            state.markDirty();
        });
        if (syncImmediately) {
            sync(player);
        }
    }

    public static boolean startThrowableUse(ServerPlayer player, int dedicatedSlotIndex) {
        if (player == null
                || !isValidSlot(dedicatedSlotIndex)
                || ACTIVE_SESSIONS.containsKey(player.getUUID())
                || player.isUsingItem()
                || !isRuntimeEnabled(player)) {
            return false;
        }

        ThrowableInventoryState state = getState(player).orElse(null);
        if (state == null) {
            return false;
        }

        ItemStack dedicatedStack = state.getContainer().getItem(dedicatedSlotIndex).copy();
        if (!ThrowableItemHelper.isThrowableStack(dedicatedStack)) {
            return false;
        }

        Inventory inventory = player.getInventory();
        int hotbarSlot = clampHotbarSlot(inventory.selected);
        ItemStack originalHotbarStack = inventory.getItem(hotbarSlot).copy();

        state.getContainer().replaceItem(dedicatedSlotIndex, ItemStack.EMPTY);
        inventory.setItem(hotbarSlot, dedicatedStack);

        ACTIVE_SESSIONS.put(player.getUUID(),
                new ThrowableUseSession(dedicatedSlotIndex, hotbarSlot, originalHotbarStack));
        state.setActiveSlot(dedicatedSlotIndex);
        state.markDirty();
        broadcastInventory(player);
        sync(player);
        return true;
    }

    public static void stopThrowableUse(ServerPlayer player, ThrowableStopReason reason) {
        if (player == null) {
            return;
        }
        ThrowableUseSession session = ACTIVE_SESSIONS.get(player.getUUID());
        if (session == null) {
            return;
        }

        if (!player.isUsingItem()) {
            finalizeSession(player, session);
            return;
        }

        if (reason == ThrowableStopReason.SCROLL_CANCEL || !hasReachedPrepareThreshold(player)) {
            player.stopUsingItem();
            finalizeSession(player, session);
            return;
        }

        player.releaseUsingItem();
        finalizeSession(player, session);
    }

    public static void abortSession(ServerPlayer player, boolean restoreOriginalStack) {
        if (player == null) {
            return;
        }
        ThrowableUseSession session = ACTIVE_SESSIONS.remove(player.getUUID());
        if (session == null) {
            getState(player).ifPresent(state -> {
                state.setActiveSlot(ThrowableInventoryState.ACTIVE_SLOT_NONE);
                state.markDirty();
            });
            return;
        }

        ThrowableInventoryState state = getState(player).orElse(null);
        if (state == null) {
            return;
        }

        if (restoreOriginalStack) {
            Inventory inventory = player.getInventory();
            ItemStack runtimeStack = inventory.getItem(session.hotbarSlotIndex()).copy();
            inventory.setItem(session.hotbarSlotIndex(), session.originalHotbarStack().copy());
            state.getContainer().replaceItem(session.dedicatedSlotIndex(),
                    ThrowableItemHelper.isThrowableStack(runtimeStack) ? runtimeStack : ItemStack.EMPTY);
            broadcastInventory(player);
        }

        state.setActiveSlot(ThrowableInventoryState.ACTIVE_SLOT_NONE);
        state.markDirty();
        sync(player);
    }

    public static void serverTick(ServerPlayer player) {
        if (player == null) {
            return;
        }

        ThrowableUseSession session = ACTIVE_SESSIONS.get(player.getUUID());
        if (session != null) {
            if (player.isUsingItem()) {
                session.markUsingStarted();
            } else if (session.usingStarted() || session.tickStartupGrace()) {
                finalizeSession(player, session);
            }
        }

        ThrowableInventoryState state = getState(player).orElse(null);
        if (state == null || !state.isDirty()) {
            return;
        }

        sync(player);
        state.clearDirty();
    }

    public static void sync(ServerPlayer player) {
        if (player == null) {
            return;
        }
        ThrowableInventoryState state = getState(player).orElse(null);
        if (state == null) {
            return;
        }
        ModNetworkChannel.sendToPlayer(new SyncThrowableInventoryPacket(state.copyStacks(), state.getActiveSlot()), player);
    }

    private static void finalizeSession(ServerPlayer player, ThrowableUseSession session) {
        if (!ACTIVE_SESSIONS.remove(player.getUUID(), session)) {
            return;
        }

        ThrowableInventoryState state = getState(player).orElse(null);
        if (state == null) {
            return;
        }

        Inventory inventory = player.getInventory();
        ItemStack runtimeStack = inventory.getItem(session.hotbarSlotIndex()).copy();
        inventory.setItem(session.hotbarSlotIndex(), session.originalHotbarStack().copy());
        state.getContainer().replaceItem(session.dedicatedSlotIndex(),
                ThrowableItemHelper.isThrowableStack(runtimeStack) ? runtimeStack : ItemStack.EMPTY);
        state.setActiveSlot(ThrowableInventoryState.ACTIVE_SLOT_NONE);
        state.markDirty();
        broadcastInventory(player);
        sync(player);
    }

    private static void broadcastInventory(ServerPlayer player) {
        player.inventoryMenu.broadcastChanges();
        player.inventoryMenu.slotsChanged(player.getInventory());
    }

    private static boolean hasReachedPrepareThreshold(ServerPlayer player) {
        ItemStack useItem = player.getUseItem();
        if (useItem.isEmpty()) {
            return false;
        }
        return player.getTicksUsingItem() >= ThrowableItemHelper.getPrepareTicks(useItem);
    }

    private static boolean isRuntimeEnabled(ServerPlayer player) {
        if (player.server == null || !FpsMatchGatewayProvider.gateway().isInMatch(player.getUUID())) {
            return false;
        }
        WeaponFilterConfig config = WeaponFilterConfigRepository.loadOrCreate(
                ConfigPath.SERVER_FILTER.getPath(player.server));
        return config == null || config.isThrowablesEnabled();
    }

    private static int clampHotbarSlot(int slot) {
        if (slot < 0) {
            return 0;
        }
        return Math.min(8, slot);
    }

    public static boolean isValidSlot(int slotIndex) {
        return slotIndex >= 0 && slotIndex < SLOT_COUNT;
    }
}
