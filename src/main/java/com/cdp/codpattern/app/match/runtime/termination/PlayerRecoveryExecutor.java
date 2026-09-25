package com.cdp.codpattern.app.match.runtime.termination;

import com.cdp.codpattern.adapter.forge.network.ModNetworkChannel;
import com.cdp.codpattern.app.tdm.service.WarmupMovementLockService;
import com.cdp.codpattern.compat.fpsmatch.map.RoomRespawnStateRegistry;
import com.cdp.codpattern.core.throwable.ThrowableInventoryService;
import com.cdp.codpattern.network.match.CountdownPacket;
import com.cdp.codpattern.network.match.DeathCamPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import java.util.function.BiConsumer;

/** Built-in recovery, deliberately independent of mode cleanup callbacks. */
public final class PlayerRecoveryExecutor {
    private static final Map<String, BiConsumer<ServerPlayer, String>> CUSTOM = new LinkedHashMap<>();
    private final MinecraftServer server;
    private final ForceEndCoordinator.Checkpoint checkpoint;
    public PlayerRecoveryExecutor(MinecraftServer server, ForceEndCoordinator.Checkpoint checkpoint) {
        this.server = server; this.checkpoint = checkpoint;
    }
    public static void register(String key, BiConsumer<ServerPlayer, String> action) {
        if (CUSTOM.putIfAbsent(key, Objects.requireNonNull(action)) != null)
            throw new IllegalArgumentException("Duplicate recovery executor " + key);
    }
    public void recover(PlayerRecoveryRecord record, ServerPlayer player) {
        if (record.recovered) return;
        step(record, player, "mode", () -> {
            if (!player.isAlive()) throw new IllegalStateException("Player must respawn before recovery");
            GameType mode = GameType.byName(record.restoreGameMode, GameType.ADVENTURE);
            player.setGameMode(mode);
            if (player.gameMode.getGameModeForPlayer() != mode) throw new IllegalStateException("Game mode not restored");
        });
        step(record, player, "control", () -> {
            WarmupMovementLockService.unlock(player);
            player.setCamera(player);
            player.noPhysics = record.noPhysics;
            player.setNoGravity(record.noGravity);
            player.setInvulnerable(record.invulnerable);
            player.setDeltaMovement(Vec3.ZERO);
            player.fallDistance = 0;
            if (player.getCamera() != player || player.noPhysics != record.noPhysics
                    || player.isNoGravity() != record.noGravity || player.isInvulnerable() != record.invulnerable)
                throw new IllegalStateException("Player control restoration not confirmed");
        });
        for (var attribute : record.attributes.entrySet()) step(record, player, "attribute:" + attribute.getKey(), () -> {
            var type = BuiltInRegistries.ATTRIBUTE.getOptional(new ResourceLocation(attribute.getValue().attribute())).orElseThrow();
            var instance = player.getAttribute(type);
            if (instance != null) {
                UUID modifier = UUID.fromString(attribute.getKey());
                var current = instance.getModifier(modifier);
                var expected = attribute.getValue();
                if (current != null && (!current.getName().equals(expected.name())
                        || Double.compare(current.getAmount(), expected.amount()) != 0
                        || current.getOperation().toValue() != expected.operation()))
                    throw new IllegalStateException("Attribute ownership conflict: " + modifier);
                instance.removeModifier(modifier);
                if (instance.getModifier(modifier) != null) throw new IllegalStateException("Attribute modifier remains");
            }
        });
        step(record, player, "respawn", () -> {
            RoomRespawnStateRegistry.restore(player);
            var target = record.respawnTarget;
            player.setRespawnPosition(target == null ? Level.OVERWORLD : dimension(target.dimension()),
                    target == null ? null : BlockPos.containing(target.x(), target.y(), target.z()),
                    target == null ? 0 : target.yaw(), record.respawnForced, false);
        });
        step(record, player, "inventory", () -> {
            if (record.clearInventory) {
                player.getInventory().clearContent();
                ThrowableInventoryService.clearRuntime(player, true);
                player.inventoryMenu.broadcastChanges();
                ThrowableInventoryService.sync(player);
                if (!player.getInventory().isEmpty()) throw new IllegalStateException("Round inventory remains");
            }
        });
        step(record, player, "teleport", () -> {
            if (!player.isAlive()) throw new IllegalStateException("Player must respawn before teleport");
            boolean teleported = teleport(player, record.endTarget) || teleport(player, record.returnTarget);
            if (!teleported) {
                var level = server.overworld();
                BlockPos spawn = level.getSharedSpawnPos();
                for (int dy = 0; dy <= 8 && !teleported; dy++) {
                    for (int dx = -4; dx <= 4 && !teleported; dx++) {
                        for (int dz = -4; dz <= 4 && !teleported; dz++) {
                            teleported = teleport(player, new PlayerRecoveryRecord.Target(level.dimension().location().toString(),
                                    spawn.getX() + dx + .5, spawn.getY() + dy, spawn.getZ() + dz + .5, 0, 0));
                        }
                    }
                }
            }
            if (!teleported) throw new IllegalStateException("No verified end, return or spawn destination");
        });
        step(record, player, "client", () -> {
            ModNetworkChannel.sendToPlayer(new CountdownPacket(0, false), player);
            ModNetworkChannel.sendToPlayer(new DeathCamPacket(null, "", 0, 0), player);
        });
        for (String tag : record.persistentTags) step(record, player, "tag:" + tag, () -> player.getPersistentData().remove(tag));
        for (var action : record.customActions.entrySet()) step(record, player, "custom:" + action.getKey(), () -> {
            var executor = CUSTOM.get(action.getKey());
            if (executor == null) throw new IllegalStateException("Recovery executor unavailable: " + action.getKey());
            executor.accept(player, action.getValue());
        });
        record.recovered = record.failures.isEmpty();
    }
    private void step(PlayerRecoveryRecord record, ServerPlayer player, String key, ForceEndCoordinator.SharedCleanup action) {
        if (record.completed.contains(key)) return;
        try {
            action.run();
            record.failures.remove(key);
            // Persist player state before acknowledging durable progress in the room ledger.
            PlayerRecoveryPersistence.save(player);
            record.completed.add(key);
            checkpoint.save();
        } catch (Exception | LinkageError failure) {
            record.completed.remove(key);
            record.failures.put(key, failure.toString());
        }
    }
    public static ResourceKey<Level> dimension(String id) {
        return ResourceKey.create(Registries.DIMENSION, new ResourceLocation(id));
    }
    public static ServerLevel level(MinecraftServer server, String id) { return server.getLevel(dimension(id)); }
    private boolean teleport(ServerPlayer player, PlayerRecoveryRecord.Target target) {
        if (target == null) return false;
        try {
            if (!Double.isFinite(target.x()) || !Double.isFinite(target.y()) || !Double.isFinite(target.z())) return false;
            ServerLevel level = level(server, target.dimension());
            if (level == null) return false;
            BlockPos feet = BlockPos.containing(target.x(), target.y(), target.z());
            if (!level.getWorldBorder().isWithinBounds(feet) || feet.getY() < level.getMinBuildHeight()
                    || feet.getY() + 2 >= level.getMaxBuildHeight()) return false;
            // Load only the candidate chunk and verify support, fluid, collision and hazards.
            level.getChunkAt(feet);
            if (level.getBlockState(feet.below()).getCollisionShape(level, feet.below()).isEmpty()
                    || !level.getFluidState(feet).isEmpty() || !level.getFluidState(feet.above()).isEmpty()
                    || level.getBlockState(feet).is(net.minecraft.tags.BlockTags.FIRE)
                    || level.getBlockState(feet.below()).is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)) return false;
            var box = player.getBoundingBox().move(target.x() - player.getX(), target.y() - player.getY(), target.z() - player.getZ());
            if (!level.noCollision(player, box)) return false;
            for (BlockPos position : List.of(feet.below(), feet, feet.above())) {
                var state = level.getBlockState(position);
                if (state.is(net.minecraft.tags.BlockTags.FIRE) || state.is(net.minecraft.world.level.block.Blocks.CACTUS)
                        || state.is(net.minecraft.world.level.block.Blocks.SWEET_BERRY_BUSH)
                        || state.is(net.minecraft.world.level.block.Blocks.POWDER_SNOW)
                        || state.is(net.minecraft.world.level.block.Blocks.WITHER_ROSE)
                        || state.is(net.minecraft.tags.BlockTags.CAMPFIRES)) return false;
            }
            player.stopRiding();
            player.teleportTo(level, target.x(), target.y(), target.z(), target.yaw(), target.pitch());
            return player.serverLevel() == level && player.position().distanceToSqr(target.x(), target.y(), target.z()) < .01;
        } catch (RuntimeException failure) { return false; }
    }
}
