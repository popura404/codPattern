package com.cdp.codpattern.app.zombies.service;

import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.match.runtime.player.ModePlayerSessionMarker;
import com.cdp.codpattern.core.throwable.ThrowableInventoryService;
import com.mojang.logging.LogUtils;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

import java.util.Objects;
import java.util.Optional;

/**
 * Reads and writes the lightweight player-side zombies recovery marker.
 */
public final class ZombiesPlayerRuntimeMarkerService {
    public static final String ROOT_TAG = "codpattern.zombies";
    public static final String STATE_ACTIVE_ROUND = "active_round";
    public static final String STATE_PENDING_ENDTP = "pending_endtp";

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ZombiesPlayerRuntimeMarkerService INSTANCE = new ZombiesPlayerRuntimeMarkerService();

    private static final String STATE_TAG = "state";
    private static final String ROOM_TAG = "roomId";
    private static final String TARGET_TAG = "endtp";
    private static final String DIMENSION_TAG = "dimension";
    private static final String X_TAG = "x";
    private static final String Y_TAG = "y";
    private static final String Z_TAG = "z";
    private static final String YAW_TAG = "yaw";
    private static final String PITCH_TAG = "pitch";

    public static ZombiesPlayerRuntimeMarkerService instance() {
        return INSTANCE;
    }

    public void writeActiveRoundMarker(
            ServerPlayer player,
            RoomId roomId,
            Optional<ZombiesPostGameTeleportService.TeleportTarget> endTeleport
    ) {
        writeMarker(player, roomId, STATE_ACTIVE_ROUND, endTeleport);
    }

    public void writePendingEndTeleportMarker(
            ServerPlayer player,
            ZombiesPostGameTeleportService.PendingEndTeleport pending
    ) {
        if (pending == null) {
            return;
        }
        writeMarker(player, pending.roomId(), STATE_PENDING_ENDTP, pending.endTeleport());
    }

    public Optional<PlayerMarker> readMarker(ServerPlayer player) {
        if (player == null || !player.getPersistentData().contains(ROOT_TAG)) {
            return Optional.empty();
        }
        CompoundTag root = player.getPersistentData().getCompound(ROOT_TAG);
        String roomKey = root.getString(ROOM_TAG);
        String state = root.getString(STATE_TAG);
        if (roomKey.isBlank() || state.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(PlayerMarker.fromNeutral(new ModePlayerSessionMarker<>(
                    RoomId.decode(roomKey),
                    state,
                    readTarget(root))));
        } catch (IllegalArgumentException exception) {
            LOGGER.warn("Ignoring invalid zombies player marker room id {}", roomKey);
            return Optional.empty();
        }
    }

    public void clearMarker(ServerPlayer player) {
        if (player != null) {
            player.getPersistentData().remove(ROOT_TAG);
        }
    }

    public Optional<ZombiesPostGameTeleportService.TeleportTarget> targetFromSpawnPoint(SpawnPointData point) {
        if (point == null || point.getDimension() == null || point.getPosition() == null) {
            return Optional.empty();
        }
        BlockPos pos = point.getPosition();
        return Optional.of(new ZombiesPostGameTeleportService.TeleportTarget(
                point.getDimension().location().toString(),
                pos.getX(),
                pos.getY(),
                pos.getZ(),
                point.getYaw(),
                point.getPitch()));
    }

    public Optional<SpawnPointData> toSpawnPointData(
            ZombiesPostGameTeleportService.TeleportTarget target,
            SpawnPointKind kind
    ) {
        if (target == null || !target.hasDimension()) {
            return Optional.empty();
        }
        ResourceLocation dimensionId = ResourceLocation.tryParse(target.dimensionId());
        if (dimensionId == null) {
            return Optional.empty();
        }
        return Optional.of(new SpawnPointData(
                ResourceKey.create(Registries.DIMENSION, dimensionId),
                new BlockPos(target.x(), target.y(), target.z()),
                target.yaw(),
                target.pitch(),
                kind == null ? SpawnPointKind.INITIAL : kind));
    }

    public boolean teleportToTarget(ServerPlayer player, ZombiesPostGameTeleportService.TeleportTarget target) {
        if (player == null || target == null || !target.hasDimension()) {
            return false;
        }
        ResourceLocation dimensionId = ResourceLocation.tryParse(target.dimensionId());
        if (dimensionId == null) {
            return false;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return false;
        }
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, dimensionId));
        BlockPos pos = new BlockPos(target.x(), target.y(), target.z());
        if (level == null || !Level.isInSpawnableBounds(pos)) {
            return false;
        }
        player.teleportTo(
                level,
                target.x() + 0.5D,
                target.y(),
                target.z() + 0.5D,
                target.yaw(),
                target.pitch());
        player.setDeltaMovement(player.getDeltaMovement().multiply(1.0D, 0.0D, 1.0D));
        player.setOnGround(true);
        return true;
    }

    public boolean teleportToServerFallback(ServerPlayer player) {
        if (player == null || player.getServer() == null) {
            return false;
        }
        ServerLevel level = player.getServer().overworld();
        BlockPos spawn = level.getSharedSpawnPos();
        if (!Level.isInSpawnableBounds(spawn)) {
            return false;
        }
        player.teleportTo(
                level,
                spawn.getX() + 0.5D,
                spawn.getY(),
                spawn.getZ() + 0.5D,
                level.getSharedSpawnAngle(),
                0.0F);
        player.setDeltaMovement(player.getDeltaMovement().multiply(1.0D, 0.0D, 1.0D));
        player.setOnGround(true);
        return true;
    }

    public void clearTemporaryPlayerState(ServerPlayer player, boolean clearInventory) {
        if (player == null) {
            return;
        }
        if (!player.gameMode.getGameModeForPlayer().isCreative()) {
            player.setGameMode(GameType.ADVENTURE);
        }
        if (clearInventory) {
            player.getInventory().clearContent();
        }
        ThrowableInventoryService.clearRuntime(player, true);
        player.inventoryMenu.broadcastChanges();
        player.inventoryMenu.slotsChanged(player.getInventory());
        ThrowableInventoryService.sync(player);
    }

    private void writeMarker(
            ServerPlayer player,
            RoomId roomId,
            String state,
            Optional<ZombiesPostGameTeleportService.TeleportTarget> endTeleport
    ) {
        if (player == null || roomId == null) {
            return;
        }
        CompoundTag root = new CompoundTag();
        root.putString(ROOM_TAG, roomId.encode());
        root.putString(STATE_TAG, Objects.requireNonNullElse(state, "").trim());
        if (endTeleport != null) {
            endTeleport.ifPresent(target -> root.put(TARGET_TAG, writeTarget(target)));
        }
        player.getPersistentData().put(ROOT_TAG, root);
    }

    private Optional<ZombiesPostGameTeleportService.TeleportTarget> readTarget(CompoundTag root) {
        if (root == null || !root.contains(TARGET_TAG)) {
            return Optional.empty();
        }
        CompoundTag target = root.getCompound(TARGET_TAG);
        String dimension = target.getString(DIMENSION_TAG);
        if (dimension.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new ZombiesPostGameTeleportService.TeleportTarget(
                dimension,
                target.getInt(X_TAG),
                target.getInt(Y_TAG),
                target.getInt(Z_TAG),
                target.getFloat(YAW_TAG),
                target.getFloat(PITCH_TAG)));
    }

    private CompoundTag writeTarget(ZombiesPostGameTeleportService.TeleportTarget target) {
        CompoundTag tag = new CompoundTag();
        tag.putString(DIMENSION_TAG, target.dimensionId());
        tag.putInt(X_TAG, target.x());
        tag.putInt(Y_TAG, target.y());
        tag.putInt(Z_TAG, target.z());
        tag.putFloat(YAW_TAG, target.yaw());
        tag.putFloat(PITCH_TAG, target.pitch());
        return tag;
    }

    public record PlayerMarker(
            RoomId roomId,
            String state,
            Optional<ZombiesPostGameTeleportService.TeleportTarget> endTeleport
    ) {
        public PlayerMarker {
            Objects.requireNonNull(roomId, "roomId");
            state = Objects.requireNonNullElse(state, "").trim();
            endTeleport = endTeleport == null ? Optional.empty() : endTeleport;
        }

        public boolean isActiveRound() {
            return STATE_ACTIVE_ROUND.equals(state);
        }

        public boolean isPendingEndTeleport() {
            return STATE_PENDING_ENDTP.equals(state);
        }

        public ModePlayerSessionMarker<ZombiesPostGameTeleportService.TeleportTarget> toNeutral() {
            return new ModePlayerSessionMarker<>(roomId, state, endTeleport);
        }

        public static PlayerMarker fromNeutral(
                ModePlayerSessionMarker<ZombiesPostGameTeleportService.TeleportTarget> marker
        ) {
            Objects.requireNonNull(marker, "marker");
            return new PlayerMarker(marker.roomId(), marker.state(), marker.recoveryTarget());
        }
    }
}
