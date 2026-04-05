package com.cdp.codpattern.network;

import com.cdp.codpattern.core.throwable.ThrowableInventoryState;
import com.cdp.codpattern.network.handler.ClientPacketBridge;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SyncThrowableInventoryPacket {
    private final ItemStack[] stacks;
    private final int activeSlot;

    public SyncThrowableInventoryPacket(ItemStack[] stacks, int activeSlot) {
        this.stacks = new ItemStack[ThrowableInventoryState.SLOT_COUNT];
        for (int i = 0; i < ThrowableInventoryState.SLOT_COUNT; i++) {
            this.stacks[i] = i < stacks.length ? stacks[i].copy() : ItemStack.EMPTY;
        }
        this.activeSlot = activeSlot;
    }

    public SyncThrowableInventoryPacket(FriendlyByteBuf buf) {
        this.stacks = new ItemStack[ThrowableInventoryState.SLOT_COUNT];
        for (int i = 0; i < ThrowableInventoryState.SLOT_COUNT; i++) {
            this.stacks[i] = buf.readItem();
        }
        this.activeSlot = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        for (ItemStack stack : stacks) {
            buf.writeItem(stack);
        }
        buf.writeInt(activeSlot);
    }

    public static SyncThrowableInventoryPacket decode(FriendlyByteBuf buf) {
        return new SyncThrowableInventoryPacket(buf);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientPacketBridge.syncThrowableInventory(stacks, activeSlot));
        ctx.get().setPacketHandled(true);
    }
}
