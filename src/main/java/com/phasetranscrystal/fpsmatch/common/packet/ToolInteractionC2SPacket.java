package com.phasetranscrystal.fpsmatch.common.packet;

import com.phasetranscrystal.fpsmatch.common.item.tool.ToolInteractionAction;
import com.phasetranscrystal.fpsmatch.common.item.tool.ToolAccessHelper;
import com.phasetranscrystal.fpsmatch.common.item.tool.ToolInteractionHit;
import com.phasetranscrystal.fpsmatch.common.item.tool.WorldToolItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ToolInteractionC2SPacket {
    private final ToolInteractionAction action;
    private final BlockPos clickedPos;
    private final Direction clickedFace;

    public ToolInteractionC2SPacket(ToolInteractionAction action, BlockPos clickedPos) {
        this(action, clickedPos, Direction.UP);
    }

    public ToolInteractionC2SPacket(ToolInteractionAction action, BlockPos clickedPos, Direction clickedFace) {
        this.action = action;
        this.clickedPos = clickedPos;
        this.clickedFace = clickedFace;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(action);
        buf.writeBoolean(clickedPos != null);
        if (clickedPos != null) {
            buf.writeBlockPos(clickedPos);
            buf.writeEnum(clickedFace == null ? Direction.UP : clickedFace);
        }
    }

    public static ToolInteractionC2SPacket decode(FriendlyByteBuf buf) {
        ToolInteractionAction action = buf.readEnum(ToolInteractionAction.class);
        BlockPos pos = null;
        Direction face = null;
        if (buf.readBoolean()) {
            pos = buf.readBlockPos();
            face = buf.readEnum(Direction.class);
        }
        return new ToolInteractionC2SPacket(action, pos, face);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) {
                return;
            }

            ItemStack stack = player.getMainHandItem();
            if (stack.getItem() instanceof WorldToolItem worldToolItem) {
                if (!ToolAccessHelper.ensureAdminAccess(player)) {
                    return;
                }
                ToolInteractionHit hit = ToolInteractionHit.fromClicked(clickedPos, clickedFace);
                worldToolItem.handleWorldInteraction(player, stack, action, hit);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
