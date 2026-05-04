package com.cdp.codpattern.app.zombies.service;

import com.cdp.codpattern.app.match.BuiltInGameModes;
import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.zombies.map.object.ZombiesBarrierData;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.UUID;

public final class ZombiesBarrierVisualServiceCompatTest {
    private ZombiesBarrierVisualServiceCompatTest() {
    }

    public static void main(String[] args) {
        activePreviewsOnlyIncludeUnclearedBarriersInViewerDimension();
        signatureChangesWhenBarrierClears();
        previewPrefixIsScopedByRoomAndPlayer();
    }

    private static void activePreviewsOnlyIncludeUnclearedBarriersInViewerDimension() {
        ZombiesBarrierVisualService service = new ZombiesBarrierVisualService();
        ZombiesBarrierData active = barrier("barrier_2_a", dimension("minecraft:overworld"));
        ZombiesBarrierData cleared = barrier("barrier_2_b", dimension("minecraft:overworld"));
        ZombiesBarrierData otherDimension = barrier("barrier_2_nether", dimension("minecraft:the_nether"));

        List<ZombiesBarrierVisualService.BarrierPreview> previews = service.activeBarrierPreviews(
                dimension("minecraft:overworld"),
                List.of(active, cleared, otherDimension),
                barrier -> "barrier_2_b".equals(barrier.objectId()));

        require(previews.size() == 1, "only one active same-dimension barrier should be visible");
        ZombiesBarrierVisualService.BarrierPreview preview = previews.get(0);
        require("barrier_2_a".equals(preview.objectId()), "visible preview should keep the active object id");
        require(preview.group() == 2, "visible preview should keep the barrier group");
        require(new BlockPos(5, 64, 5).equals(preview.area().pos1()), "visible preview should use areaFrom");
        require(new BlockPos(5, 66, 7).equals(preview.area().pos2()), "visible preview should use areaTo");
    }

    private static void signatureChangesWhenBarrierClears() {
        ZombiesBarrierVisualService service = new ZombiesBarrierVisualService();
        ResourceKey<Level> dimension = dimension("minecraft:overworld");
        RoomId roomId = RoomId.of(BuiltInGameModes.ZOMBIES, "visual_signature");
        List<ZombiesBarrierData> barriers = List.of(
                barrier("barrier_2_a", dimension),
                barrier("barrier_2_b", dimension));

        List<ZombiesBarrierVisualService.BarrierPreview> before = service.activeBarrierPreviews(
                dimension,
                barriers,
                ignored -> false);
        List<ZombiesBarrierVisualService.BarrierPreview> after = service.activeBarrierPreviews(
                dimension,
                barriers,
                barrier -> "barrier_2_a".equals(barrier.objectId()));

        require(before.size() == 2, "both uncleared barriers should be visible before purchase");
        require(after.size() == 1, "cleared barrier should be removed from preview candidates");
        require(!service.buildSignature(roomId, dimension, before).equals(service.buildSignature(roomId, dimension, after)),
                "preview signature should change when a barrier clears");
    }

    private static void previewPrefixIsScopedByRoomAndPlayer() {
        UUID playerA = new UUID(0L, 1L);
        UUID playerB = new UUID(0L, 2L);
        RoomId roomA = RoomId.of(BuiltInGameModes.ZOMBIES, "room_a");
        RoomId roomB = RoomId.of(BuiltInGameModes.ZOMBIES, "room_b");

        String playerAPrefix = ZombiesBarrierVisualService.previewPrefix(roomA, playerA);
        require(!playerAPrefix.equals(ZombiesBarrierVisualService.previewPrefix(roomA, playerB)),
                "preview prefix should separate players in the same room");
        require(!playerAPrefix.equals(ZombiesBarrierVisualService.previewPrefix(roomB, playerA)),
                "preview prefix should separate rooms for the same player");
        require(playerAPrefix.endsWith(playerA + ":"), "preview prefix should retain player id suffix");
    }

    private static ZombiesBarrierData barrier(String objectId, ResourceKey<Level> dimension) {
        return new ZombiesBarrierData(
                objectId,
                2,
                750,
                true,
                dimension,
                new BlockPos(5, 64, 5),
                new BlockPos(5, 66, 7),
                new BlockPos(5, 65, 5));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static ResourceKey<Level> dimension(String value) {
        try {
            Constructor<ResourceKey> constructor =
                    ResourceKey.class.getDeclaredConstructor(ResourceLocation.class, ResourceLocation.class);
            constructor.setAccessible(true);
            return (ResourceKey<Level>) constructor.newInstance(
                    resourceLocation("minecraft:dimension"),
                    resourceLocation(value));
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("failed to create test dimension key", exception);
        }
    }

    private static ResourceLocation resourceLocation(String value) {
        ResourceLocation location = ResourceLocation.tryParse(value);
        if (location == null) {
            throw new AssertionError("invalid resource location " + value);
        }
        return location;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
