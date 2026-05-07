package com.cdp.codpattern.app.zombies.deploy;

import com.cdp.codpattern.app.zombies.map.ZombiesMapObjects;
import com.cdp.codpattern.app.zombies.map.object.ZombiesBarrierData;
import com.cdp.codpattern.app.zombies.map.object.ZombiesPowerSwitchData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ZombiesDeployToolServiceIssueTargetCompatTest {
    private ZombiesDeployToolServiceIssueTargetCompatTest() {
    }

    public static void main(String[] args) throws Throwable {
        validStructuredTargetClipsIndex();
        invalidStructuredStepFallsBackToLegacyResolver();
        mapStageStructuredTargetUsesDraftTypeWhenBlank();
    }

    private static void validStructuredTargetClipsIndex() throws Throwable {
        ZombiesMapObjects objects = objectsWithSingleBarrierAndPowerSwitch();
        ZombiesDeployDraft draft = draft(ZombiesDeployFieldSchema.BARRIER);
        Object target = resolveProvidedIssueTarget(
                ZombiesDeployDraft.WORKFLOW_BARRIER,
                ZombiesDeployFieldSchema.BARRIER,
                99,
                false,
                "map.invalid_barrier",
                "barrier.barrier-1",
                draft,
                objects);
        require(!recordBoolean(target, "mapStage"), "structured target should stay object stage");
        require(ZombiesDeployDraft.WORKFLOW_BARRIER.equals(recordString(target, "workflowStep")), "structured target should keep barrier step");
        require(ZombiesDeployFieldSchema.BARRIER.equals(recordString(target, "objectType")), "structured target should keep barrier type");
        require(recordInt(target, "selectedIndex") == 0, "structured target index should clamp to existing object");
    }

    private static void invalidStructuredStepFallsBackToLegacyResolver() throws Throwable {
        ZombiesMapObjects objects = objectsWithSingleBarrierAndPowerSwitch();
        ZombiesDeployDraft draft = draft(ZombiesDeployFieldSchema.BARRIER);
        Object target = resolveProvidedIssueTarget(
                "not_a_step",
                ZombiesDeployFieldSchema.BARRIER,
                0,
                false,
                "map.invalid_power_switch",
                "power_switch.power-main",
                draft,
                objects);
        require(!recordBoolean(target, "mapStage"), "fallback target should stay object stage");
        require(ZombiesDeployDraft.WORKFLOW_INTERACT.equals(recordString(target, "workflowStep")), "fallback target should route to interact step");
        require(ZombiesDeployFieldSchema.POWER_SWITCH.equals(recordString(target, "objectType")), "fallback target should route to power switch type");
        require(recordInt(target, "selectedIndex") == 0, "fallback target should resolve existing power switch index");
    }

    private static void mapStageStructuredTargetUsesDraftTypeWhenBlank() throws Throwable {
        ZombiesMapObjects objects = objectsWithSingleBarrierAndPowerSwitch();
        ZombiesDeployDraft draft = draft(ZombiesDeployFieldSchema.BARRIER);
        Object target = resolveProvidedIssueTarget(
                ZombiesDeployDraft.WORKFLOW_MAP,
                "",
                12,
                true,
                "map.missing_map",
                "map",
                draft,
                objects);
        require(recordBoolean(target, "mapStage"), "map-stage target should remain map stage");
        require(ZombiesDeployDraft.WORKFLOW_MAP.equals(recordString(target, "workflowStep")), "map-stage target should stay on map workflow");
        require(ZombiesDeployFieldSchema.BARRIER.equals(recordString(target, "objectType")), "blank map-stage type should fallback to draft type");
        require(recordInt(target, "selectedIndex") == 0, "map-stage index should clamp using resolved draft type objects");
    }

    private static Object resolveProvidedIssueTarget(
            String workflowStep,
            String objectType,
            int index,
            boolean mapStage,
            String issueCode,
            String issueSubject,
            ZombiesDeployDraft draft,
            ZombiesMapObjects objects
    ) throws Throwable {
        Method method = ZombiesDeployToolService.class.getDeclaredMethod(
                "resolveProvidedIssueTarget",
                String.class,
                String.class,
                int.class,
                boolean.class,
                String.class,
                String.class,
                ZombiesDeployDraft.class,
                ZombiesMapObjects.class);
        method.setAccessible(true);
        return method.invoke(
                ZombiesDeployToolService.instance(),
                workflowStep,
                objectType,
                index,
                mapStage,
                issueCode,
                issueSubject,
                draft,
                objects);
    }

    private static ZombiesMapObjects objectsWithSingleBarrierAndPowerSwitch() {
        ZombiesBarrierData barrier = new ZombiesBarrierData(
                "barrier-1",
                1,
                500,
                true,
                Level.OVERWORLD,
                new BlockPos(1, 64, 1),
                new BlockPos(2, 65, 2),
                new BlockPos(1, 64, 0));
        ZombiesPowerSwitchData powerSwitch = new ZombiesPowerSwitchData(
                "power-main",
                "codpattern:zombies_power_switch",
                1000,
                true,
                Level.OVERWORLD,
                new BlockPos(5, 64, 5),
                Optional.empty());
        return new ZombiesMapObjects(
                List.of(),
                List.of(),
                List.of(barrier),
                List.of(),
                List.of(),
                List.of(),
                Optional.of(powerSwitch),
                List.of(),
                List.of(),
                List.of(),
                List.of());
    }

    private static ZombiesDeployDraft draft(String objectType) {
        return new ZombiesDeployDraft(
                ZombiesDeployDraft.STAGE_OBJECT_MARKING,
                ZombiesDeployDraft.WORKFLOW_BARRIER,
                "z_test_map",
                "",
                null,
                null,
                objectType,
                ZombiesDeployDraft.CAPTURE_DEFAULT,
                -1,
                ZombiesDeployFieldSchema.PROFILE_MVP1,
                Map.of());
    }

    private static String recordString(Object record, String accessor) throws Throwable {
        Method method = record.getClass().getDeclaredMethod(accessor);
        method.setAccessible(true);
        return String.valueOf(method.invoke(record));
    }

    private static int recordInt(Object record, String accessor) throws Throwable {
        Method method = record.getClass().getDeclaredMethod(accessor);
        method.setAccessible(true);
        return ((Number) method.invoke(record)).intValue();
    }

    private static boolean recordBoolean(Object record, String accessor) throws Throwable {
        Method method = record.getClass().getDeclaredMethod(accessor);
        method.setAccessible(true);
        return (Boolean) method.invoke(record);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
