package com.cdp.codpattern.app.zombies.service;

import com.cdp.codpattern.app.zombies.map.object.ZombiesBarrierData;
import com.cdp.codpattern.app.zombies.model.ZombiesGamePhase;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

public final class ZombiesBarrierMovementService {
    private static final ZombiesBarrierMovementService INSTANCE = new ZombiesBarrierMovementService();
    private static final double PLAYER_HALF_WIDTH = 0.31D;
    private static final double PLAYER_HEIGHT = 1.80D;
    private static final double PUSH_EPSILON = 0.03125D;
    private static final double SLAB_EPSILON = 0.0000001D;

    private final Map<UUID, LegalPosition> lastLegalPositions = new ConcurrentHashMap<>();

    public static ZombiesBarrierMovementService instance() {
        return INSTANCE;
    }

    public EnforcementResult enforce(
            ServerPlayer player,
            ZombiesGamePhase phase,
            Collection<ZombiesBarrierData> barriers,
            Predicate<ZombiesBarrierData> barrierClearedPredicate,
            Predicate<UUID> aliveMemberPredicate
    ) {
        if (player == null) {
            return EnforcementResult.IGNORED;
        }

        UUID playerId = player.getUUID();
        boolean aliveMember = aliveMemberPredicate != null && aliveMemberPredicate.test(playerId);
        Optional<LegalPosition> previousLegal = Optional.ofNullable(lastLegalPositions.get(playerId));
        MovementDecision decision = decideMovement(
                PositionSample.fromPlayer(player),
                phase,
                barriers,
                barrierClearedPredicate,
                aliveMember,
                previousLegal);

        if (!decision.eligible()) {
            lastLegalPositions.remove(playerId);
            return EnforcementResult.IGNORED;
        }
        if (!decision.blocked()) {
            lastLegalPositions.put(playerId, PositionSample.fromPlayer(player).toLegalPosition());
            return EnforcementResult.ALLOWED;
        }

        Optional<LegalPosition> target = decision.target();
        if (target.isEmpty() || !teleport(player, target.get())) {
            return EnforcementResult.BLOCKED_NO_TARGET;
        }

        lastLegalPositions.put(playerId, target.get());
        return previousLegal.filter(target.get()::equals).isPresent()
                ? EnforcementResult.BLOCKED_RESTORED
                : EnforcementResult.BLOCKED_FALLBACK;
    }

    public MovementDecision decideMovement(
            PositionSample current,
            ZombiesGamePhase phase,
            Collection<ZombiesBarrierData> barriers,
            Predicate<ZombiesBarrierData> barrierClearedPredicate,
            boolean aliveMember,
            Optional<LegalPosition> previousLegalPosition
    ) {
        if (current == null || phase == null || !phase.isRoundRunning() || !aliveMember) {
            return MovementDecision.ignored();
        }

        List<BarrierArea> activeAreas = activeAreas(current.dimension(), barriers, barrierClearedPredicate);
        if (activeAreas.isEmpty()) {
            return MovementDecision.allowed();
        }

        Optional<LegalPosition> previous = previousLegalPosition == null ? Optional.empty() : previousLegalPosition;
        Optional<BarrierArea> blockingArea = activeAreas.stream()
                .filter(area -> area.blocks(current, previous))
                .findFirst();
        if (blockingArea.isEmpty()) {
            return MovementDecision.allowed();
        }

        Optional<LegalPosition> target = previous
                .filter(position -> current.dimension().equals(position.dimension()))
                .filter(position -> isLegal(position, activeAreas));
        if (target.isEmpty()) {
            target = fallbackOutside(current, blockingArea.get(), activeAreas);
        }
        return MovementDecision.blocked(blockingArea.get().objectId(), target);
    }

    public void clear(UUID playerId) {
        if (playerId != null) {
            lastLegalPositions.remove(playerId);
        }
    }

    public void clearAll() {
        lastLegalPositions.clear();
    }

    private static boolean teleport(ServerPlayer player, LegalPosition target) {
        ServerLevel targetLevel = player.serverLevel();
        if (!targetLevel.dimension().equals(target.dimension())) {
            MinecraftServer server = player.getServer();
            targetLevel = server == null ? null : server.getLevel(target.dimension());
        }
        if (targetLevel == null
                || !Level.isInSpawnableBounds(BlockPos.containing(target.x(), target.y(), target.z()))) {
            return false;
        }

        player.teleportTo(targetLevel, target.x(), target.y(), target.z(), target.yaw(), target.pitch());
        player.setDeltaMovement(Vec3.ZERO);
        player.setOnGround(true);
        return true;
    }

    private static List<BarrierArea> activeAreas(
            ResourceKey<Level> dimension,
            Collection<ZombiesBarrierData> barriers,
            Predicate<ZombiesBarrierData> barrierClearedPredicate
    ) {
        if (dimension == null || barriers == null || barriers.isEmpty()) {
            return List.of();
        }
        return barriers.stream()
                .filter(Objects::nonNull)
                .filter(ZombiesBarrierData::blocksPlayersOnly)
                .filter(barrier -> dimension.equals(barrier.dimension()))
                .filter(barrier -> barrierClearedPredicate == null || !barrierClearedPredicate.test(barrier))
                .map(BarrierArea::from)
                .toList();
    }

    private static Optional<LegalPosition> fallbackOutside(
            PositionSample current,
            BarrierArea blockingArea,
            List<BarrierArea> activeAreas
    ) {
        return blockingArea.fallbackCandidates(current)
                .stream()
                .filter(candidate -> isLegal(candidate, activeAreas))
                .min(Comparator.comparingDouble(candidate -> candidate.distanceToSqr(current)));
    }

    private static boolean isLegal(LegalPosition position, List<BarrierArea> activeAreas) {
        PositionSample sample = position.toPositionSample();
        for (BarrierArea area : activeAreas) {
            if (area.intersectsPlayer(sample)) {
                return false;
            }
        }
        return true;
    }

    public enum EnforcementResult {
        IGNORED,
        ALLOWED,
        BLOCKED_RESTORED,
        BLOCKED_FALLBACK,
        BLOCKED_NO_TARGET
    }

    public record PositionSample(
            ResourceKey<Level> dimension,
            double x,
            double y,
            double z,
            float yaw,
            float pitch
    ) {
        public PositionSample {
            Objects.requireNonNull(dimension, "dimension");
        }

        public static PositionSample fromPlayer(ServerPlayer player) {
            Objects.requireNonNull(player, "player");
            return new PositionSample(
                    player.level().dimension(),
                    player.getX(),
                    player.getY(),
                    player.getZ(),
                    player.getYRot(),
                    player.getXRot());
        }

        public LegalPosition toLegalPosition() {
            return new LegalPosition(dimension, x, y, z, yaw, pitch);
        }
    }

    public record LegalPosition(
            ResourceKey<Level> dimension,
            double x,
            double y,
            double z,
            float yaw,
            float pitch
    ) {
        public LegalPosition {
            Objects.requireNonNull(dimension, "dimension");
        }

        public PositionSample toPositionSample() {
            return new PositionSample(dimension, x, y, z, yaw, pitch);
        }

        private double distanceToSqr(PositionSample sample) {
            double dx = x - sample.x();
            double dy = y - sample.y();
            double dz = z - sample.z();
            return dx * dx + dy * dy + dz * dz;
        }
    }

    public record MovementDecision(
            boolean eligible,
            boolean blocked,
            Optional<LegalPosition> target,
            String barrierObjectId
    ) {
        public MovementDecision {
            target = target == null ? Optional.empty() : target;
            barrierObjectId = Objects.requireNonNullElse(barrierObjectId, "");
        }

        public static MovementDecision ignored() {
            return new MovementDecision(false, false, Optional.empty(), "");
        }

        public static MovementDecision allowed() {
            return new MovementDecision(true, false, Optional.empty(), "");
        }

        public static MovementDecision blocked(String barrierObjectId, Optional<LegalPosition> target) {
            return new MovementDecision(true, true, target, barrierObjectId);
        }
    }

    private record BarrierArea(
            String objectId,
            ResourceKey<Level> dimension,
            double minX,
            double minY,
            double minZ,
            double maxX,
            double maxY,
            double maxZ
    ) {
        private static BarrierArea from(ZombiesBarrierData barrier) {
            BlockPos from = barrier.areaFrom() == null ? BlockPos.ZERO : barrier.areaFrom();
            BlockPos to = barrier.areaTo() == null ? BlockPos.ZERO : barrier.areaTo();
            int minBlockX = Math.min(from.getX(), to.getX());
            int minBlockY = Math.min(from.getY(), to.getY());
            int minBlockZ = Math.min(from.getZ(), to.getZ());
            int maxBlockX = Math.max(from.getX(), to.getX());
            int maxBlockY = Math.max(from.getY(), to.getY());
            int maxBlockZ = Math.max(from.getZ(), to.getZ());
            return new BarrierArea(
                    ZombiesObjectStateStore.objectKey(barrier),
                    barrier.dimension(),
                    minBlockX - PLAYER_HALF_WIDTH,
                    minBlockY - PLAYER_HEIGHT,
                    minBlockZ - PLAYER_HALF_WIDTH,
                    maxBlockX + 1.0D + PLAYER_HALF_WIDTH,
                    maxBlockY + 1.0D,
                    maxBlockZ + 1.0D + PLAYER_HALF_WIDTH);
        }

        private boolean blocks(PositionSample current, Optional<LegalPosition> previousLegal) {
            if (intersectsPlayer(current)) {
                return true;
            }
            return previousLegal
                    .filter(previous -> dimension.equals(previous.dimension()))
                    .map(previous -> intersectsSegment(previous.toPositionSample(), current))
                    .orElse(false);
        }

        private boolean intersectsPlayer(PositionSample sample) {
            return sample != null
                    && dimension.equals(sample.dimension())
                    && sample.x() > minX
                    && sample.x() < maxX
                    && sample.y() > minY
                    && sample.y() < maxY
                    && sample.z() > minZ
                    && sample.z() < maxZ;
        }

        private boolean intersectsSegment(PositionSample from, PositionSample to) {
            if (from == null || to == null || !dimension.equals(from.dimension()) || !dimension.equals(to.dimension())) {
                return false;
            }
            double[] range = {0.0D, 1.0D};
            return clip(from.x(), to.x() - from.x(), minX, maxX, range)
                    && clip(from.y(), to.y() - from.y(), minY, maxY, range)
                    && clip(from.z(), to.z() - from.z(), minZ, maxZ, range);
        }

        private List<LegalPosition> fallbackCandidates(PositionSample current) {
            return List.of(
                    new LegalPosition(dimension, minX - PUSH_EPSILON, current.y(), current.z(), current.yaw(), current.pitch()),
                    new LegalPosition(dimension, maxX + PUSH_EPSILON, current.y(), current.z(), current.yaw(), current.pitch()),
                    new LegalPosition(dimension, current.x(), current.y(), minZ - PUSH_EPSILON, current.yaw(), current.pitch()),
                    new LegalPosition(dimension, current.x(), current.y(), maxZ + PUSH_EPSILON, current.yaw(), current.pitch()),
                    new LegalPosition(dimension, current.x(), maxY + PUSH_EPSILON, current.z(), current.yaw(), current.pitch()));
        }

        private static boolean clip(double start, double delta, double min, double max, double[] range) {
            if (Math.abs(delta) < SLAB_EPSILON) {
                return start > min && start < max;
            }
            double inverse = 1.0D / delta;
            double t1 = (min - start) * inverse;
            double t2 = (max - start) * inverse;
            if (t1 > t2) {
                double swap = t1;
                t1 = t2;
                t2 = swap;
            }
            range[0] = Math.max(range[0], t1);
            range[1] = Math.min(range[1], t2);
            return range[0] <= range[1] && range[1] >= 0.0D && range[0] <= 1.0D;
        }
    }
}
