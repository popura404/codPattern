package com.cdp.codpattern.app.zombies.deploy;

import com.phasetranscrystal.fpsmatch.common.packet.ZombiesDeployToolActionC2SPacket;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class ZombiesDeployIssueRoutingCompatTest {
    private ZombiesDeployIssueRoutingCompatTest() {
    }

    public static void main(String[] args) {
        issueTargetRecordNormalizesValues();
        jumpToIssueTargetPacketRoundTripPreservesStructuredFields();
        jumpToIssueFallbackPacketRoundTripKeepsBackwardDefaults();
    }

    private static void issueTargetRecordNormalizesValues() {
        ZombiesDeploySnapshot.IssueTarget target = new ZombiesDeploySnapshot.IssueTarget(
                " map.invalid_barrier ",
                " barrier.b-1 ",
                "barrier",
                "barrier",
                2,
                false);
        require("map.invalid_barrier".equals(target.issueCode()), "issue code should be trimmed");
        require("barrier.b-1".equals(target.issueSubject()), "issue subject should be trimmed");
        require(ZombiesDeployDraft.WORKFLOW_BARRIER.equals(target.workflowStep()), "workflow step should normalize");
        require(ZombiesDeployFieldSchema.BARRIER.equals(target.targetObjectType()), "target type should normalize");
        require(target.targetIndex() == 2, "target index should keep non-negative value");

        ZombiesDeploySnapshot.IssueTarget fallback = new ZombiesDeploySnapshot.IssueTarget(
                null,
                null,
                "unknown",
                "unknown",
                -9,
                true);
        require(fallback.issueCode().isEmpty(), "null issue code should become empty");
        require(fallback.issueSubject().isEmpty(), "null issue subject should become empty");
        require(ZombiesDeployDraft.WORKFLOW_MAP.equals(fallback.workflowStep()), "unknown workflow should fallback to map");
        require(ZombiesDeployFieldSchema.INITIAL.equals(fallback.targetObjectType()), "unknown object type should fallback to initial");
        require(fallback.targetIndex() == -1, "index should clamp to -1 minimum");
        require(fallback.mapStage(), "mapStage should keep value");
    }

    private static void jumpToIssueTargetPacketRoundTripPreservesStructuredFields() {
        ZombiesDeployDraft draft = draft();
        ZombiesDeployToolActionC2SPacket packet = new ZombiesDeployToolActionC2SPacket(
                ZombiesDeployToolActionC2SPacket.Action.JUMP_TO_ISSUE_TARGET,
                draft,
                "map.invalid_barrier",
                "barrier.b-2",
                ZombiesDeployDraft.WORKFLOW_BARRIER,
                ZombiesDeployFieldSchema.BARRIER,
                3,
                false);
        assertRoundTripStable(packet, "jump_to_issue_target packet round trip should preserve structured fields");
    }

    private static void jumpToIssueFallbackPacketRoundTripKeepsBackwardDefaults() {
        ZombiesDeployDraft draft = draft();
        ZombiesDeployToolActionC2SPacket packet = new ZombiesDeployToolActionC2SPacket(
                ZombiesDeployToolActionC2SPacket.Action.JUMP_TO_ISSUE,
                draft,
                "map.missing_power_switch",
                "power_switch");
        assertRoundTripStable(packet, "legacy jump_to_issue packet round trip should stay compatible");
    }

    private static ZombiesDeployDraft draft() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("objectId", "barrier-1");
        fields.put("areaFromX", "1");
        fields.put("areaFromY", "64");
        fields.put("areaFromZ", "1");
        return new ZombiesDeployDraft(
                ZombiesDeployDraft.STAGE_OBJECT_MARKING,
                ZombiesDeployDraft.WORKFLOW_BARRIER,
                "z_test_map",
                "",
                null,
                null,
                ZombiesDeployFieldSchema.BARRIER,
                ZombiesDeployDraft.CAPTURE_BARRIER_AREA,
                0,
                ZombiesDeployFieldSchema.PROFILE_MVP1,
                fields);
    }

    private static void assertRoundTripStable(ZombiesDeployToolActionC2SPacket packet, String message) {
        FriendlyByteBuf encoded = new FriendlyByteBuf(Unpooled.buffer());
        packet.encode(encoded);

        FriendlyByteBuf reader = new FriendlyByteBuf(encoded.copy());
        ZombiesDeployToolActionC2SPacket decoded = ZombiesDeployToolActionC2SPacket.decode(reader);
        require(packetsEquivalent(packet, decoded), message + " (semantic mismatch after decode)");

        FriendlyByteBuf reEncoded = new FriendlyByteBuf(Unpooled.buffer());
        decoded.encode(reEncoded);
        FriendlyByteBuf reReader = new FriendlyByteBuf(reEncoded.copy());
        ZombiesDeployToolActionC2SPacket decodedAgain = ZombiesDeployToolActionC2SPacket.decode(reReader);
        require(packetsEquivalent(decoded, decodedAgain), message + " (semantic mismatch after re-encode)");
    }

    private static boolean packetsEquivalent(
            ZombiesDeployToolActionC2SPacket left,
            ZombiesDeployToolActionC2SPacket right
    ) {
        return Objects.equals(packetField(left, "action"), packetField(right, "action"))
                && Objects.equals(packetField(left, "fieldKey"), packetField(right, "fieldKey"))
                && Objects.equals(packetField(left, "fieldValue"), packetField(right, "fieldValue"))
                && Objects.equals(packetField(left, "issueWorkflowStep"), packetField(right, "issueWorkflowStep"))
                && Objects.equals(packetField(left, "issueObjectType"), packetField(right, "issueObjectType"))
                && Objects.equals(packetField(left, "issueTargetIndex"), packetField(right, "issueTargetIndex"))
                && Objects.equals(packetField(left, "issueMapStage"), packetField(right, "issueMapStage"))
                && draftsEquivalent(
                (ZombiesDeployDraft) packetField(left, "draft"),
                (ZombiesDeployDraft) packetField(right, "draft"));
    }

    private static boolean draftsEquivalent(ZombiesDeployDraft left, ZombiesDeployDraft right) {
        return Objects.equals(left.workspaceStage(), right.workspaceStage())
                && Objects.equals(left.workflowStep(), right.workflowStep())
                && Objects.equals(left.selectedMap(), right.selectedMap())
                && Objects.equals(left.draftMapName(), right.draftMapName())
                && Objects.equals(left.mapPos1(), right.mapPos1())
                && Objects.equals(left.mapPos2(), right.mapPos2())
                && Objects.equals(left.objectType(), right.objectType())
                && Objects.equals(left.capturePreset(), right.capturePreset())
                && left.selectedIndex() == right.selectedIndex()
                && Objects.equals(left.validationView(), right.validationView())
                && Objects.equals(left.fields(), right.fields());
    }

    private static Object packetField(ZombiesDeployToolActionC2SPacket packet, String name) {
        try {
            Field field = ZombiesDeployToolActionC2SPacket.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(packet);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to read packet field " + name, e);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
