package com.phasetranscrystal.fpsmatch.common.packet;

import com.cdp.codpattern.app.zombies.deploy.ZombiesDeployFieldSchema;
import com.cdp.codpattern.app.zombies.deploy.ZombiesDeploySnapshot;
import net.minecraft.core.BlockPos;
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
        buf.writeUtf(snapshot.workspaceStage());
        buf.writeUtf(snapshot.currentWorkflowStep());
        buf.writeUtf(snapshot.nextWorkflowStep());
        buf.writeUtf(snapshot.blockingReason());
        buf.writeUtf(snapshot.nextActionLabel());
        buf.writeBoolean(snapshot.nextActionEnabled());
        buf.writeUtf(snapshot.selectedMap());
        buf.writeUtf(snapshot.draftMapName());
        writeNullableBlockPos(buf, snapshot.mapPos1());
        writeNullableBlockPos(buf, snapshot.mapPos2());
        buf.writeVarInt(snapshot.objectTypes().size());
        for (ZombiesDeploySnapshot.ObjectTypeOption option : snapshot.objectTypes()) {
            buf.writeUtf(option.key());
            buf.writeUtf(option.labelKey());
        }
        buf.writeUtf(snapshot.selectedObjectType());
        buf.writeUtf(snapshot.capturePreset());
        buf.writeUtf(snapshot.captureSlotA());
        buf.writeUtf(snapshot.captureSlotB());
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
        buf.writeVarInt(snapshot.issueTargets().size());
        for (ZombiesDeploySnapshot.IssueTarget target : snapshot.issueTargets()) {
            buf.writeUtf(target.issueCode());
            buf.writeUtf(target.issueSubject());
            buf.writeUtf(target.workflowStep());
            buf.writeUtf(target.targetObjectType());
            buf.writeVarInt(target.targetIndex());
            buf.writeBoolean(target.mapStage());
        }
        buf.writeVarInt(snapshot.validationSummaries().size());
        for (ZombiesDeploySnapshot.ValidationSummary summary : snapshot.validationSummaries()) {
            buf.writeUtf(summary.profileKey());
            buf.writeVarInt(summary.errors());
            buf.writeVarInt(summary.warnings());
        }
        buf.writeVarInt(snapshot.objectCounts().size());
        for (ZombiesDeploySnapshot.ObjectTypeCount count : snapshot.objectCounts()) {
            buf.writeUtf(count.objectType());
            buf.writeVarInt(count.count());
            buf.writeBoolean(count.singleton());
            buf.writeBoolean(count.required());
        }
        buf.writeVarInt(snapshot.stepStatuses().size());
        for (ZombiesDeploySnapshot.StepStatus status : snapshot.stepStatuses()) {
            buf.writeUtf(status.key());
            buf.writeUtf(status.label());
            buf.writeUtf(status.detail());
            buf.writeBoolean(status.complete());
        }
        buf.writeBoolean(snapshot.dirty());
        buf.writeUtf(snapshot.nearestObjectHint());
        buf.writeBoolean(snapshot.activeMap());
        buf.writeVarInt(snapshot.revision());
        buf.writeUtf(snapshot.statusKey());
        buf.writeUtf(snapshot.statusCode());
        buf.writeUtf(snapshot.statusDetail());
    }

    public static OpenZombiesDeployToolScreenS2CPacket decode(FriendlyByteBuf buf) {
        List<String> maps = readStringList(buf);
        String workspaceStage = buf.readUtf();
        String currentWorkflowStep = buf.readUtf();
        String nextWorkflowStep = buf.readUtf();
        String blockingReason = buf.readUtf();
        String nextActionLabel = buf.readUtf();
        boolean nextActionEnabled = buf.readBoolean();
        String selectedMap = buf.readUtf();
        String draftMapName = buf.readUtf();
        BlockPos mapPos1 = readNullableBlockPos(buf);
        BlockPos mapPos2 = readNullableBlockPos(buf);
        int typeCount = buf.readVarInt();
        List<ZombiesDeploySnapshot.ObjectTypeOption> objectTypes = new ArrayList<>(typeCount);
        for (int i = 0; i < typeCount; i++) {
            objectTypes.add(new ZombiesDeploySnapshot.ObjectTypeOption(buf.readUtf(), buf.readUtf()));
        }
        String selectedObjectType = buf.readUtf();
        String capturePreset = buf.readUtf();
        String captureSlotA = buf.readUtf();
        String captureSlotB = buf.readUtf();
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
        int issueTargetCount = buf.readVarInt();
        List<ZombiesDeploySnapshot.IssueTarget> issueTargets = new ArrayList<>(issueTargetCount);
        for (int i = 0; i < issueTargetCount; i++) {
            issueTargets.add(new ZombiesDeploySnapshot.IssueTarget(
                    buf.readUtf(),
                    buf.readUtf(),
                    buf.readUtf(),
                    buf.readUtf(),
                    buf.readVarInt(),
                    buf.readBoolean()));
        }
        int validationSummaryCount = buf.readVarInt();
        List<ZombiesDeploySnapshot.ValidationSummary> validationSummaries = new ArrayList<>(validationSummaryCount);
        for (int i = 0; i < validationSummaryCount; i++) {
            validationSummaries.add(new ZombiesDeploySnapshot.ValidationSummary(
                    buf.readUtf(),
                    buf.readVarInt(),
                    buf.readVarInt()));
        }
        int objectTypeCount = buf.readVarInt();
        List<ZombiesDeploySnapshot.ObjectTypeCount> objectCounts = new ArrayList<>(objectTypeCount);
        for (int i = 0; i < objectTypeCount; i++) {
            objectCounts.add(new ZombiesDeploySnapshot.ObjectTypeCount(
                    buf.readUtf(),
                    buf.readVarInt(),
                    buf.readBoolean(),
                    buf.readBoolean()));
        }
        int stepCount = buf.readVarInt();
        List<ZombiesDeploySnapshot.StepStatus> stepStatuses = new ArrayList<>(stepCount);
        for (int i = 0; i < stepCount; i++) {
            stepStatuses.add(new ZombiesDeploySnapshot.StepStatus(
                    buf.readUtf(),
                    buf.readUtf(),
                    buf.readUtf(),
                    buf.readBoolean()));
        }
        boolean dirty = buf.readBoolean();
        String nearestObjectHint = buf.readUtf();
        ZombiesDeploySnapshot snapshot = new ZombiesDeploySnapshot(
                maps,
                workspaceStage,
                currentWorkflowStep,
                nextWorkflowStep,
                blockingReason,
                nextActionLabel,
                nextActionEnabled,
                selectedMap,
                draftMapName,
                mapPos1,
                mapPos2,
                objectTypes,
                selectedObjectType,
                capturePreset,
                captureSlotA,
                captureSlotB,
                selectedIndex,
                objects,
                fields,
                profileKey,
                profiles,
                validationLines,
                issueTargets,
                validationSummaries,
                objectCounts,
                stepStatuses,
                dirty,
                nearestObjectHint,
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
