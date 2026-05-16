package com.cdp.codpattern.app.zombies.service;

import com.cdp.codpattern.app.zombies.map.object.ZombiesBarrierData;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.lang.reflect.Constructor;
import java.util.List;

public final class ZombiesBarrierBlockRuntimeServiceCompatTest {
    private ZombiesBarrierBlockRuntimeServiceCompatTest() {
    }

    public static void main(String[] args) {
        straightWallGeometryCountsCells();
        diagonalBarrierGeometryIsInvalid();
        residuePlanDeduplicatesPositionsByDimension();
    }

    private static void straightWallGeometryCountsCells() {
        ZombiesBarrierData barrier = barrier("barrier-straight", new BlockPos(6, 1, 6), new BlockPos(6, 4, 10));

        ZombiesBarrierBlockRuntimeService.BarrierGeometry geometry =
                ZombiesBarrierBlockRuntimeService.geometry(barrier);
        List<BlockPos> cells = ZombiesBarrierBlockRuntimeService.wallCells(barrier);

        require(geometry.straightWall(), "shared-X barrier should be a valid straight wall");
        require(geometry.horizontalLength() == 5, "expected horizontal length 5, got " + geometry.horizontalLength());
        require(geometry.height() == 4, "expected height 4, got " + geometry.height());
        require(geometry.cellCount() == 20, "expected cell count 20, got " + geometry.cellCount());
        require(cells.size() == 20, "wallCells should generate the same number of cells as geometry");
        require(cells.contains(new BlockPos(6, 1, 6)), "wallCells should include the lower endpoint");
        require(cells.contains(new BlockPos(6, 4, 10)), "wallCells should include the upper endpoint");
    }

    private static void diagonalBarrierGeometryIsInvalid() {
        ZombiesBarrierData barrier = barrier("barrier-diagonal", new BlockPos(6, 1, 6), new BlockPos(9, 2, 10));

        ZombiesBarrierBlockRuntimeService.BarrierGeometry geometry =
                ZombiesBarrierBlockRuntimeService.geometry(barrier);

        require(!geometry.straightWall(), "barrier with both X and Z changing should be invalid");
        require(geometry.horizontalLength() == 5, "diagonal horizontal span should still be measurable");
        require(geometry.cellCount() == 10, "cell count should use horizontal span times height");
    }

    private static void residuePlanDeduplicatesPositionsByDimension() {
        ZombiesBarrierData first = barrier("barrier-a", new BlockPos(6, 1, 6), new BlockPos(6, 2, 6));
        ZombiesBarrierData duplicate = barrier("barrier-b", new BlockPos(6, 1, 6), new BlockPos(6, 2, 6));

        ZombiesBarrierBlockRuntimeService.ResidueScanPlan plan =
                ZombiesBarrierBlockRuntimeService.residueScanPlan(List.of(first, duplicate));

        require(plan.positionsByDimension().size() == 1, "single dimension should produce one residue scan bucket");
        require(plan.cellCount() == 2, "overlapping barrier residue scan cells should be deduplicated");
    }

    private static ZombiesBarrierData barrier(String objectId, BlockPos from, BlockPos to) {
        return new ZombiesBarrierData(
                objectId,
                1,
                0,
                true,
                dimension(),
                from,
                to,
                from);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ResourceKey<Level> dimension() {
        try {
            Class<?> resourceKeyClass = Class.forName("net.minecraft.resources.ResourceKey");
            Constructor<ResourceLocation> resourceLocation = ResourceLocation.class.getDeclaredConstructor(String.class, String.class);
            resourceLocation.setAccessible(true);
            Object registry = resourceKeyClass.getMethod("createRegistryKey", ResourceLocation.class)
                    .invoke(null, resourceLocation.newInstance("minecraft", "dimension"));
            return (ResourceKey<Level>) resourceKeyClass.getMethod("create", ResourceKey.class, ResourceLocation.class)
                    .invoke(null, registry, resourceLocation.newInstance("minecraft", "overworld"));
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to create test dimension key", exception);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
