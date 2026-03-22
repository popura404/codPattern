package com.phasetranscrystal.fpsmatch.common.packet;

import com.phasetranscrystal.fpsmatch.common.item.tool.ToolInteractionAction;
import com.phasetranscrystal.fpsmatch.common.item.tool.WorldToolItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ToolInteractionC2SPacket {
    private final ToolInteractionAction action;
    private final BlockPos clickedPos;

    public ToolInteractionC2SPacket(ToolInteractionAction action, BlockPos clickedPos) {
        this.action = action;
        this.clickedPos = clickedPos;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(action);
        buf.writeBoolean(clickedPos != null);
        if (clickedPos != null) {
            buf.writeBlockPos(clickedPos);
        }
    }

    public static ToolInteractionC2SPacket decode(FriendlyByteBuf buf) {
        ToolInteractionAction action = buf.readEnum(ToolInteractionAction.class);
        BlockPos pos = buf.readBoolean() ? buf.readBlockPos() : null;
        return new ToolInteractionC2SPacket(action, pos);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) {
                return;
            }

            ItemStack stack = player.getMainHandItem();
            if (stack.getItem() instanceof WorldToolItem worldToolItem) {
                worldToolItem.handleWorldInteraction(player, stack, action, clickedPos);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
