package com.phasetranscrystal.fpsmatch.common.packet;

import com.cdp.codpattern.app.zombies.deploy.ZombiesDeployDraft;
import com.cdp.codpattern.app.zombies.deploy.ZombiesDeployServiceResult;
import com.cdp.codpattern.app.zombies.deploy.ZombiesDeploySnapshot;
import com.cdp.codpattern.app.zombies.deploy.ZombiesDeployToolService;
import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.common.item.ZombiesDeployTool;
import com.phasetranscrystal.fpsmatch.common.item.tool.ToolAccessHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public class ZombiesDeployToolActionC2SPacket {
    public enum Action {
        REFRESH,
        SAVE_SELECTIONS,
        SELECT_MAP,
        SELECT_OBJECT_TYPE,
        SELECT_OBJECT,
        SET_FIELD,
        ADD_OBJECT,
        UPDATE_OBJECT,
        DUPLICATE_OBJECT,
        DELETE_OBJECT,
        CLEAR_OBJECT_TYPE,
        VALIDATE_MAP,
        SELECT_WORKSPACE_STAGE,
        SELECT_WORKFLOW_STEP,
        GO_NEXT_STEP,
        SAVE_AND_VALIDATE_MVP1,
        JUMP_TO_ISSUE,
        JUMP_TO_ISSUE_TARGET,
        CREATE_MAP,
        UPDATE_MAP_AREA,
        SELECT_CAPTURE_PRESET
    }

    private final Action action;
    private final ZombiesDeployDraft draft;
    private final String fieldKey;
    private final String fieldValue;
    private final String issueWorkflowStep;
    private final String issueObjectType;
    private final int issueTargetIndex;
    private final boolean issueMapStage;

    public ZombiesDeployToolActionC2SPacket(Action action, ZombiesDeployDraft draft) {
        this(action, draft, "", "", "", "", -1, false);
    }

    public ZombiesDeployToolActionC2SPacket(Action action, ZombiesDeployDraft draft, String fieldKey, String fieldValue) {
        this(action, draft, fieldKey, fieldValue, "", "", -1, false);
    }

    public ZombiesDeployToolActionC2SPacket(
            Action action,
            ZombiesDeployDraft draft,
            String fieldKey,
            String fieldValue,
            String issueWorkflowStep,
            String issueObjectType,
            int issueTargetIndex,
            boolean issueMapStage
    ) {
        this.action = action == null ? Action.REFRESH : action;
        this.draft = draft == null ? ZombiesDeployDraft.empty() : draft;
        this.fieldKey = fieldKey == null ? "" : fieldKey;
        this.fieldValue = fieldValue == null ? "" : fieldValue;
        this.issueWorkflowStep = issueWorkflowStep == null ? "" : issueWorkflowStep;
        this.issueObjectType = issueObjectType == null ? "" : issueObjectType;
        this.issueTargetIndex = Math.max(-1, issueTargetIndex);
        this.issueMapStage = issueMapStage;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(action);
        buf.writeUtf(draft.workspaceStage());
        buf.writeUtf(draft.workflowStep());
        buf.writeUtf(draft.selectedMap());
        buf.writeUtf(draft.draftMapName());
        writeNullableBlockPos(buf, draft.mapPos1());
        writeNullableBlockPos(buf, draft.mapPos2());
        buf.writeUtf(draft.objectType());
        buf.writeUtf(draft.capturePreset());
        buf.writeVarInt(draft.selectedIndex());
        buf.writeUtf(draft.validationView());
        buf.writeVarInt(draft.fields().size());
        draft.fields().forEach((key, value) -> {
            buf.writeUtf(key);
            buf.writeUtf(value);
        });
        buf.writeUtf(fieldKey);
        buf.writeUtf(fieldValue);
        buf.writeUtf(issueWorkflowStep);
        buf.writeUtf(issueObjectType);
        buf.writeVarInt(issueTargetIndex);
        buf.writeBoolean(issueMapStage);
    }

    public static ZombiesDeployToolActionC2SPacket decode(FriendlyByteBuf buf) {
        Action action = buf.readEnum(Action.class);
        String workspaceStage = buf.readUtf();
        String workflowStep = buf.readUtf();
        String selectedMap = buf.readUtf();
        String draftMapName = buf.readUtf();
        BlockPos mapPos1 = readNullableBlockPos(buf);
        BlockPos mapPos2 = readNullableBlockPos(buf);
        String objectType = buf.readUtf();
        String capturePreset = buf.readUtf();
        int selectedIndex = buf.readVarInt();
        String validationView = buf.readUtf();
        int fieldCount = buf.readVarInt();
        Map<String, String> fields = new LinkedHashMap<>();
        for (int i = 0; i < fieldCount; i++) {
            fields.put(buf.readUtf(), buf.readUtf());
        }
        return new ZombiesDeployToolActionC2SPacket(
                action,
                new ZombiesDeployDraft(
                        workspaceStage,
                        workflowStep,
                        selectedMap,
                        draftMapName,
                        mapPos1,
                        mapPos2,
                        objectType,
                        capturePreset,
                        selectedIndex,
                        validationView,
                        fields),
                buf.readUtf(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readVarInt(),
                buf.readBoolean());
    }

    public static void sendScreen(ServerPlayer player, ItemStack stack, ZombiesDeployDraft request) {
        if (player == null || stack == null || !(stack.getItem() instanceof ZombiesDeployTool)) {
            return;
        }
        if (!ToolAccessHelper.ensureAdminAccess(player)) {
            return;
        }
        ZombiesDeployServiceResult<ZombiesDeploySnapshot> result = ZombiesDeployToolService.instance().snapshot(
                player,
                stack,
                request,
                "message.codpattern.zombies.deploy.opened",
                "ok",
                "");
        result.value().ifPresent(snapshot -> FPSMatch.sendToPlayer(player, new OpenZombiesDeployToolScreenS2CPacket(snapshot)));
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) {
                return;
            }
            ItemStack stack = player.getMainHandItem();
            if (!(stack.getItem() instanceof ZombiesDeployTool)) {
                return;
            }
            if (!ToolAccessHelper.ensureAdminAccess(player)) {
                return;
            }
            ZombiesDeployServiceResult<ZombiesDeploySnapshot> result = dispatch(player, stack);
            result.value().ifPresent(snapshot -> FPSMatch.sendToPlayer(player, new OpenZombiesDeployToolScreenS2CPacket(snapshot)));
            if (!result.messageKey().isBlank()) {
                List<String> args = result.arguments();
                player.displayClientMessage(Component.translatable(result.messageKey(), args.toArray()), false);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    private ZombiesDeployServiceResult<ZombiesDeploySnapshot> dispatch(ServerPlayer player, ItemStack stack) {
        ZombiesDeployToolService service = ZombiesDeployToolService.instance();
        return switch (action) {
            case REFRESH, SELECT_MAP, SELECT_OBJECT_TYPE, SELECT_OBJECT -> service.snapshot(
                    player,
                    stack,
                    draft,
                    "message.codpattern.zombies.deploy.refreshed",
                    "ok",
                    "");
            case SAVE_SELECTIONS -> service.saveSelections(player, stack, draft);
            case SET_FIELD -> service.setField(player, stack, draft, fieldKey, fieldValue);
            case ADD_OBJECT -> service.addObject(player, stack, draft);
            case UPDATE_OBJECT -> service.updateObject(player, stack, draft);
            case DUPLICATE_OBJECT -> service.duplicateObject(player, stack, draft);
            case DELETE_OBJECT -> service.deleteObject(player, stack, draft);
            case CLEAR_OBJECT_TYPE -> service.clearObjectType(player, stack, draft);
            case VALIDATE_MAP -> service.validateMap(player, stack, draft);
            case SELECT_WORKSPACE_STAGE -> service.selectWorkspaceStage(player, stack, draft);
            case SELECT_WORKFLOW_STEP -> service.selectWorkflowStep(player, stack, draft);
            case GO_NEXT_STEP -> service.goNextStep(player, stack, draft);
            case SAVE_AND_VALIDATE_MVP1 -> service.saveAndValidateMvp1(player, stack, draft);
            case JUMP_TO_ISSUE -> service.jumpToIssue(player, stack, draft, fieldKey, fieldValue);
            case JUMP_TO_ISSUE_TARGET -> service.jumpToIssueTarget(
                    player,
                    stack,
                    draft,
                    fieldKey,
                    fieldValue,
                    issueWorkflowStep,
                    issueObjectType,
                    issueTargetIndex,
                    issueMapStage);
            case CREATE_MAP -> service.createMap(player, stack, draft);
            case UPDATE_MAP_AREA -> service.updateMapArea(player, stack, draft);
            case SELECT_CAPTURE_PRESET -> service.selectCapturePreset(player, stack, draft);
        };
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
