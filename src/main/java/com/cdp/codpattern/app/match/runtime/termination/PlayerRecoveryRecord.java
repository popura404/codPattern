package com.cdp.codpattern.app.match.runtime.termination;

import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;

/** Serializable recovery evidence; basic operations are implemented by the main mod. */
public final class PlayerRecoveryRecord {
    public UUID player;
    public String room;
    public UUID generation;
    public boolean armed;
    public boolean recovered;
    public boolean recoveryPending;
    public boolean noPhysics;
    public boolean noGravity;
    public boolean invulnerable;
    public Set<String> persistentTags = new LinkedHashSet<>();
    public String restoreGameMode = GameType.ADVENTURE.getName();
    public boolean clearInventory;
    public Target returnTarget;
    public Target endTarget;
    public Target respawnTarget;
    public boolean respawnForced;
    public Map<String, AttributeUndo> attributes = new LinkedHashMap<>();
    public Map<String, String> customActions = new LinkedHashMap<>();
    public Set<String> completed = new LinkedHashSet<>();
    public Map<String, String> failures = new LinkedHashMap<>();

    public static PlayerRecoveryRecord capture(ServerPlayer player, String room, UUID generation) {
        PlayerRecoveryRecord record = new PlayerRecoveryRecord();
        record.player = player.getUUID();
        record.restoreGameMode = player.gameMode.getGameModeForPlayer().getName();
        record.noPhysics = player.noPhysics;
        record.noGravity = player.isNoGravity();
        record.invulnerable = player.isInvulnerable();
        record.room = room;
        record.generation = generation;
        record.returnTarget = Target.capture(player);
        record.respawnForced = player.isRespawnForced();
        if (player.getRespawnPosition() != null) {
            var pos = player.getRespawnPosition();
            record.respawnTarget = new Target(player.getRespawnDimension().location().toString(),
                    pos.getX(), pos.getY(), pos.getZ(), player.getRespawnAngle(), 0);
        }
        return record;
    }

    public record AttributeUndo(String attribute, String name, double amount, int operation) { }

    public record Target(String dimension, double x, double y, double z, float yaw, float pitch) {
        public static Target capture(ServerPlayer player) {
            return new Target(player.serverLevel().dimension().location().toString(), player.getX(),
                    player.getY(), player.getZ(), player.getYRot(), player.getXRot());
        }
        public static Target of(SpawnPointData point) {
            return point == null ? null : new Target(point.getDimension().location().toString(),
                    point.getX() + .5, point.getY(), point.getZ() + .5, point.getYaw(), point.getPitch());
        }
    }
}
