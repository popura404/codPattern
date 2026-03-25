package com.phasetranscrystal.fpsmatch.common.packet;

import com.cdp.codpattern.app.tdm.model.TdmGameTypes;
import com.mojang.datafixers.util.Function3;
import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.common.item.MapCreatorTool;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.data.AreaData;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.Optional;
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

            switch (action) {
                case SAVE_DRAFT -> saveDraft(stack);
                case CREATE -> createMap(player, stack);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    private void saveDraft(ItemStack stack) {
        MapCreatorTool.setSelectedType(stack, TdmGameTypes.canonicalize(selectedType));
        MapCreatorTool.setDraftMapName(stack, draftMapName);
        MapCreatorTool.setBlockPos(stack, MapCreatorTool.BLOCK_POS_TAG_1, pos1);
        MapCreatorTool.setBlockPos(stack, MapCreatorTool.BLOCK_POS_TAG_2, pos2);
    }

    private void createMap(ServerPlayer player, ItemStack stack) {
        String type = TdmGameTypes.canonicalize(selectedType);
        FPSMCore core = FPSMCore.getInstance();
        if (!core.checkGameType(type)) {
            player.displayClientMessage(Component.translatable("message.fpsm.map_creator_tool.invalid_type"), false);
            return;
        }

        String mapName = draftMapName.trim();
        if (mapName.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.fpsm.map_creator_tool.invalid_name"), false);
            return;
        }

        Optional<AreaData> area = createArea();
        if (area.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.fpsm.map_creator_tool.invalid_area"), false);
            return;
        }

        if (core.isRegistered(type, mapName)) {
            player.displayClientMessage(Component.translatable("message.fpsm.map_creator_tool.duplicate_map", mapName), false);
            return;
        }

        Function3<ServerLevel, String, AreaData, BaseMap> factory = core.getPreBuildGame(type);
        if (factory == null) {
            player.displayClientMessage(Component.translatable("message.fpsm.map_creator_tool.invalid_type"), false);
            return;
        }

        BaseMap newMap = factory.apply(player.serverLevel(), mapName, area.get());
        core.registerMap(type, newMap);

        MapCreatorTool.setSelectedType(stack, type);
        MapCreatorTool.setBlockPos(stack, MapCreatorTool.BLOCK_POS_TAG_1, pos1);
        MapCreatorTool.setBlockPos(stack, MapCreatorTool.BLOCK_POS_TAG_2, pos2);
        MapCreatorTool.setDraftMapName(stack, "");

        player.displayClientMessage(Component.translatable("commands.fpsm.create.success", mapName), false);
        FPSMatch.sendToPlayer(player, OpenMapCreatorToolScreenS2CPacket.fromStack(stack, core.getGameTypes()));
    }

    private Optional<AreaData> createArea() {
        if (pos1 == null || pos2 == null) {
            return Optional.empty();
        }
        return Optional.of(new AreaData(pos1, pos2));
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
