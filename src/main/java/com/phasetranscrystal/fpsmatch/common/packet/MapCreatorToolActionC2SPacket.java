package com.phasetranscrystal.fpsmatch.common.packet;

import com.cdp.codpattern.app.match.GameModeRegistry;
import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.common.item.MapCreatorTool;
import com.phasetranscrystal.fpsmatch.common.item.tool.ToolAccessHelper;
import com.phasetranscrystal.fpsmatch.common.service.MapCreationService;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class MapCreatorToolActionC2SPacket {
    public enum Action {
        SAVE_DRAFT,
        CREATE
    }

    private final Action action;
    private final String selectedType;
    private final String draftMapName;
    private final BlockPos pos1;
    private final BlockPos pos2;

    public MapCreatorToolActionC2SPacket(Action action, String selectedType, String draftMapName, BlockPos pos1,
            BlockPos pos2) {
        this.action = action;
        this.selectedType = selectedType;
        this.draftMapName = draftMapName;
        this.pos1 = pos1;
        this.pos2 = pos2;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(action);
        buf.writeUtf(selectedType);
        buf.writeUtf(draftMapName);
        writeNullableBlockPos(buf, pos1);
        writeNullableBlockPos(buf, pos2);
    }

    public static MapCreatorToolActionC2SPacket decode(FriendlyByteBuf buf) {
        return new MapCreatorToolActionC2SPacket(
                buf.readEnum(Action.class),
                buf.readUtf(),
                buf.readUtf(),
                readNullableBlockPos(buf),
                readNullableBlockPos(buf)
        );
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) {
                return;
            }

            ItemStack stack = player.getMainHandItem();
            if (!(stack.getItem() instanceof MapCreatorTool)) {
                return;
            }
            if (!ToolAccessHelper.ensureAdminAccess(player)) {
                return;
            }

            switch (action) {
                case SAVE_DRAFT -> saveDraft(stack);
                case CREATE -> createMap(player, stack);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    private void saveDraft(ItemStack stack) {
        MapCreatorTool.setSelectedType(stack, GameModeRegistry.canonicalize(selectedType));
        MapCreatorTool.setDraftMapName(stack, draftMapName);
        MapCreatorTool.setBlockPos(stack, MapCreatorTool.BLOCK_POS_TAG_1, pos1);
        MapCreatorTool.setBlockPos(stack, MapCreatorTool.BLOCK_POS_TAG_2, pos2);
    }

    private void createMap(ServerPlayer player, ItemStack stack) {
        FPSMCore core = FPSMCore.getInstance();
        MapCreationService.Result result = MapCreationService.instance().createMap(
                player,
                new MapCreationService.CreateRequest(selectedType, draftMapName, pos1, pos2));
        if (!result.success()) {
            player.displayClientMessage(Component.translatable(result.messageKey(), result.arguments().toArray()), false);
            return;
        }

        MapCreatorTool.setSelectedType(stack, result.type());
        MapCreatorTool.setBlockPos(stack, MapCreatorTool.BLOCK_POS_TAG_1, pos1);
        MapCreatorTool.setBlockPos(stack, MapCreatorTool.BLOCK_POS_TAG_2, pos2);
        MapCreatorTool.setDraftMapName(stack, "");

        player.displayClientMessage(Component.translatable(result.messageKey(), result.arguments().toArray()), false);
        FPSMatch.sendToPlayer(player, OpenMapCreatorToolScreenS2CPacket.fromStack(stack, core.getGameTypes()));
    }

    private static void writeNullableBlockPos(FriendlyByteBuf buf, BlockPos pos) {
        buf.writeBoolean(pos != null);
        if (pos != null) {
            buf.writeBlockPos(pos);
        }
    }

    private static BlockPos readNullableBlockPos(FriendlyByteBuf buf) {
        return buf.readBoolean() ? buf.readBlockPos() : null;
    }
}
