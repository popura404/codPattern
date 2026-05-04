package com.phasetranscrystal.fpsmatch.common.packet;

import com.cdp.codpattern.app.zombies.deploy.ZombiesDeployFieldSchema;
import com.cdp.codpattern.app.zombies.deploy.ZombiesDeploySnapshot;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class OpenZombiesDeployToolScreenS2CPacket {
    private final ZombiesDeploySnapshot snapshot;

    public OpenZombiesDeployToolScreenS2CPacket(ZombiesDeploySnapshot snapshot) {
        this.snapshot = snapshot;
    }

    public ZombiesDeploySnapshot snapshot() {
        return snapshot;
    }

    public void encode(FriendlyByteBuf buf) {
        writeStringList(buf, snapshot.availableMaps());
        buf.writeUtf(snapshot.selectedMap());
        buf.writeVarInt(snapshot.objectTypes().size());
        for (ZombiesDeploySnapshot.ObjectTypeOption option : snapshot.objectTypes()) {
            buf.writeUtf(option.key());
            buf.writeUtf(option.labelKey());
        }
        buf.writeUtf(snapshot.selectedObjectType());
        buf.writeVarInt(snapshot.selectedIndex());
        buf.writeVarInt(snapshot.objects().size());
        for (ZombiesDeploySnapshot.ObjectSummary object : snapshot.objects()) {
            buf.writeVarInt(object.index());
            buf.writeUtf(object.objectType());
            buf.writeUtf(object.objectId());
            buf.writeUtf(object.primary());
            buf.writeUtf(object.detail());
        }
        buf.writeVarInt(snapshot.fields().size());
        for (ZombiesDeploySnapshot.FieldValue field : snapshot.fields()) {
            buf.writeUtf(field.key());
            buf.writeUtf(field.labelKey());
            buf.writeEnum(field.type());
            buf.writeUtf(field.value());
            buf.writeBoolean(field.editable());
        }
        buf.writeUtf(snapshot.profileKey());
        writeStringList(buf, snapshot.availableProfiles());
        buf.writeVarInt(snapshot.validationLines().size());
        for (ZombiesDeploySnapshot.ValidationLine line : snapshot.validationLines()) {
            buf.writeUtf(line.severity());
            buf.writeUtf(line.code());
            buf.writeUtf(line.subject());
            buf.writeUtf(line.message());
        }
        buf.writeBoolean(snapshot.activeMap());
        buf.writeVarInt(snapshot.revision());
        buf.writeUtf(snapshot.statusKey());
        buf.writeUtf(snapshot.statusCode());
        buf.writeUtf(snapshot.statusDetail());
    }

    public static OpenZombiesDeployToolScreenS2CPacket decode(FriendlyByteBuf buf) {
        List<String> maps = readStringList(buf);
        String selectedMap = buf.readUtf();
        int typeCount = buf.readVarInt();
        List<ZombiesDeploySnapshot.ObjectTypeOption> objectTypes = new ArrayList<>(typeCount);
        for (int i = 0; i < typeCount; i++) {
            objectTypes.add(new ZombiesDeploySnapshot.ObjectTypeOption(buf.readUtf(), buf.readUtf()));
        }
        String selectedObjectType = buf.readUtf();
        int selectedIndex = buf.readVarInt();
        int objectCount = buf.readVarInt();
        List<ZombiesDeploySnapshot.ObjectSummary> objects = new ArrayList<>(objectCount);
        for (int i = 0; i < objectCount; i++) {
            objects.add(new ZombiesDeploySnapshot.ObjectSummary(
                    buf.readVarInt(),
                    buf.readUtf(),
                    buf.readUtf(),
                    buf.readUtf(),
                    buf.readUtf()));
        }
        int fieldCount = buf.readVarInt();
        List<ZombiesDeploySnapshot.FieldValue> fields = new ArrayList<>(fieldCount);
        for (int i = 0; i < fieldCount; i++) {
            fields.add(new ZombiesDeploySnapshot.FieldValue(
                    buf.readUtf(),
                    buf.readUtf(),
                    buf.readEnum(ZombiesDeployFieldSchema.FieldType.class),
                    buf.readUtf(),
                    buf.readBoolean()));
        }
        String profileKey = buf.readUtf();
        List<String> profiles = readStringList(buf);
        int validationCount = buf.readVarInt();
        List<ZombiesDeploySnapshot.ValidationLine> validationLines = new ArrayList<>(validationCount);
        for (int i = 0; i < validationCount; i++) {
            validationLines.add(new ZombiesDeploySnapshot.ValidationLine(
                    buf.readUtf(),
                    buf.readUtf(),
                    buf.readUtf(),
                    buf.readUtf()));
        }
        ZombiesDeploySnapshot snapshot = new ZombiesDeploySnapshot(
                maps,
                selectedMap,
                objectTypes,
                selectedObjectType,
                selectedIndex,
                objects,
                fields,
                profileKey,
                profiles,
                validationLines,
                buf.readBoolean(),
                buf.readVarInt(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readUtf());
        return new OpenZombiesDeployToolScreenS2CPacket(snapshot);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> FpsmClientPacketBridge.openZombiesDeployToolScreen(this));
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
}
