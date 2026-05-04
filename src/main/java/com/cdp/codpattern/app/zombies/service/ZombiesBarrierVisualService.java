package com.cdp.codpattern.app.zombies.service;

import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.zombies.map.object.ZombiesBarrierData;
import com.cdp.codpattern.app.zombies.model.ZombiesGamePhase;
import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.common.packet.AddAreaDataS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.RemoveDebugDataByPrefixS2CPacket;
import com.phasetranscrystal.fpsmatch.core.data.AreaData;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

public final class ZombiesBarrierVisualService {
    private static final ZombiesBarrierVisualService INSTANCE = new ZombiesBarrierVisualService();
    private static final int BARRIER_COLOR = 0xFFFFD24A;
    private static final String PREFIX_ROOT = "zombies_barrier_preview:";

    private final Map<UUID, PlayerPreviewState> previewsByPlayer = new ConcurrentHashMap<>();

    public static ZombiesBarrierVisualService instance() {
        return INSTANCE;
    }

    public void syncPlayer(
            ServerPlayer player,
            RoomId roomId,
            ZombiesGamePhase phase,
            Collection<ZombiesBarrierData> barriers,
            Predicate<ZombiesBarrierData> barrierClearedPredicate
    ) {
        if (player == null) {
            return;
        }
        if (roomId == null || phase == null || !phase.isRoundRunning()) {
            clearTrackedPlayer(player);
            return;
        }

        ResourceKey<Level> viewerDimension = player.serverLevel().dimension();
        List<BarrierPreview> previews = activeBarrierPreviews(viewerDimension, barriers, barrierClearedPredicate);
        if (previews.isEmpty()) {
            clearTrackedPlayer(player);
            return;
        }

        UUID playerId = player.getUUID();
        String prefix = previewPrefix(roomId, playerId);
        String signature = buildSignature(roomId, viewerDimension, previews);
        PlayerPreviewState previous = previewsByPlayer.get(playerId);
        if (previous != null && previous.prefix().equals(prefix) && previous.signature().equals(signature)) {
            return;
        }

        FPSMatch.sendToPlayer(player, new RemoveDebugDataByPrefixS2CPacket(prefix));
        for (int index = 0; index < previews.size(); index++) {
            BarrierPreview preview = previews.get(index);
            FPSMatch.sendToPlayer(player, new AddAreaDataS2CPacket(
                    previewKey(prefix, preview, index),
                    previewName(preview),
                    BARRIER_COLOR,
                    preview.area()));
        }
        previewsByPlayer.put(playerId, new PlayerPreviewState(prefix, signature));
    }

    public void clearPlayer(ServerPlayer player) {
        clearPlayer(player, null);
    }

    private void clearTrackedPlayer(ServerPlayer player) {
        if (player == null) {
            return;
        }
        PlayerPreviewState previous = previewsByPlayer.remove(player.getUUID());
        if (previous != null && !previous.prefix().isBlank()) {
            FPSMatch.sendToPlayer(player, new RemoveDebugDataByPrefixS2CPacket(previous.prefix()));
        }
    }

    public void clearPlayer(ServerPlayer player, RoomId roomId) {
        if (player == null) {
            return;
        }
        UUID playerId = player.getUUID();
        PlayerPreviewState previous = previewsByPlayer.remove(playerId);
        if (previous != null && !previous.prefix().isBlank()) {
            FPSMatch.sendToPlayer(player, new RemoveDebugDataByPrefixS2CPacket(previous.prefix()));
        }
        if (roomId != null) {
            String prefix = previewPrefix(roomId, playerId);
            if (previous == null || !previous.prefix().equals(prefix)) {
                FPSMatch.sendToPlayer(player, new RemoveDebugDataByPrefixS2CPacket(prefix));
            }
        }
    }

    public void clearRoom(Collection<ServerPlayer> players, RoomId roomId) {
        if (players != null) {
            for (ServerPlayer player : players) {
                clearPlayer(player, roomId);
            }
        }
        if (roomId != null) {
            String roomPrefix = previewRoomPrefix(roomId);
            previewsByPlayer.entrySet().removeIf(entry -> entry.getValue().prefix().startsWith(roomPrefix));
        }
    }

    public List<BarrierPreview> activeBarrierPreviews(
            ResourceKey<Level> viewerDimension,
            Collection<ZombiesBarrierData> barriers,
            Predicate<ZombiesBarrierData> barrierClearedPredicate
    ) {
        if (viewerDimension == null || barriers == null || barriers.isEmpty()) {
            return List.of();
        }
        return barriers.stream()
                .filter(Objects::nonNull)
                .filter(barrier -> viewerDimension.equals(barrier.dimension()))
                .filter(barrier -> barrier.areaFrom() != null && barrier.areaTo() != null)
                .filter(barrier -> barrierClearedPredicate == null || !barrierClearedPredicate.test(barrier))
                .map(barrier -> new BarrierPreview(
                        Objects.requireNonNullElse(barrier.objectId(), ""),
                        barrier.group(),
                        new AreaData(barrier.areaFrom(), barrier.areaTo())))
                .toList();
    }

    public String buildSignature(RoomId roomId, ResourceKey<Level> viewerDimension, List<BarrierPreview> previews) {
        StringBuilder builder = new StringBuilder();
        builder.append(roomId == null ? "" : roomId.encode())
                .append('|')
                .append(viewerDimension == null ? "" : viewerDimension.location());
        if (previews != null) {
            for (BarrierPreview preview : previews) {
                if (preview == null) {
                    continue;
                }
                builder.append('|')
                        .append(preview.objectId())
                        .append('#')
                        .append(preview.group())
                        .append('@')
                        .append(preview.area().pos1().asLong())
                        .append(',')
                        .append(preview.area().pos2().asLong());
            }
        }
        return builder.toString();
    }

    public static String previewPrefix(RoomId roomId, UUID playerId) {
        return previewRoomPrefix(roomId) + (playerId == null ? "" : playerId) + ":";
    }

    private static String previewRoomPrefix(RoomId roomId) {
        return PREFIX_ROOT + (roomId == null ? "" : roomId.encode()) + ":";
    }

    private static String previewKey(String prefix, BarrierPreview preview, int index) {
        String objectId = preview.objectId().isBlank() ? "barrier:" + index : preview.objectId();
        return prefix + index + ":" + objectId;
    }

    private static Component previewName(BarrierPreview preview) {
        String objectId = preview.objectId().isBlank() ? "barrier" : preview.objectId();
        return Component.literal("Barrier " + preview.group() + " " + objectId);
    }

    public record BarrierPreview(String objectId, int group, AreaData area) {
        public BarrierPreview {
            objectId = Objects.requireNonNullElse(objectId, "");
            area = Objects.requireNonNull(area, "area");
        }
    }

    private record PlayerPreviewState(String prefix, String signature) {
        private PlayerPreviewState {
            prefix = Objects.requireNonNullElse(prefix, "");
            signature = Objects.requireNonNullElse(signature, "");
        }
    }
}
