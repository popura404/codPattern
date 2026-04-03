package com.phasetranscrystal.fpsmatch.common.packet;

import com.phasetranscrystal.fpsmatch.common.client.FpsmClientPacketHandler;
import com.phasetranscrystal.fpsmatch.common.item.MapCreatorTool;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class OpenMapCreatorToolScreenS2CPacket {
    private final List<String> availableTypes;
    private final String selectedType;
    private final String draftMapName;
    private final BlockPos pos1;
    private final BlockPos pos2;

    public OpenMapCreatorToolScreenS2CPacket(List<String> availableTypes, String selectedType, String draftMapName,
            BlockPos pos1, BlockPos pos2) {
        this.availableTypes = List.copyOf(availableTypes);
        this.selectedType = selectedType;
        this.draftMapName = draftMapName;
        this.pos1 = pos1;
        this.pos2 = pos2;
    }

    public static OpenMapCreatorToolScreenS2CPacket fromStack(ItemStack stack, List<String> availableTypes) {
        return new OpenMapCreatorToolScreenS2CPacket(
                availableTypes,
                MapCreatorTool.getSelectedType(stack),
                MapCreatorTool.getDraftMapName(stack),
                MapCreatorTool.getBlockPos(stack, MapCreatorTool.BLOCK_POS_TAG_1),
                MapCreatorTool.getBlockPos(stack, MapCreatorTool.BLOCK_POS_TAG_2)
        );
    }

    public List<String> availableTypes() {
        return availableTypes;
    }

    public String selectedType() {
        return selectedType;
    }

    public String draftMapName() {
        return draftMapName;
    }

    public BlockPos pos1() {
        return pos1;
    }

    public BlockPos pos2() {
        return pos2;
    }

    public void encode(FriendlyByteBuf buf) {
        writeStringList(buf, availableTypes);
        buf.writeUtf(selectedType);
        buf.writeUtf(draftMapName);
        writeNullableBlockPos(buf, pos1);
        writeNullableBlockPos(buf, pos2);
    }

    public static OpenMapCreatorToolScreenS2CPacket decode(FriendlyByteBuf buf) {
        return new OpenMapCreatorToolScreenS2CPacket(
                readStringList(buf),
                buf.readUtf(),
                buf.readUtf(),
                readNullableBlockPos(buf),
                readNullableBlockPos(buf)
        );
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> FpsmClientPacketHandler.handleOpenMapCreatorToolScreen(this));
        });
        ctx.get().setPacketHandled(true);
    }

    private static void writeStringList(FriendlyByteBuf buf, List<String> values) {
        buf.writeVarInt(values.size());
        for (String value : values) {
            buf.writeUtf(value);
        }
    }

    private static List<String> readStringList(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<String> values = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            values.add(buf.readUtf());
        }
        return values;
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
