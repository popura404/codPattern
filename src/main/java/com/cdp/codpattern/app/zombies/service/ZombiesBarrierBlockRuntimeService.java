package com.cdp.codpattern.app.zombies.service;

import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.zombies.map.object.ZombiesBarrierData;
import com.cdp.codpattern.common.block.CodPatternBlockRegister;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.function.Predicate;

public final class ZombiesBarrierBlockRuntimeService {
    public static final int MAX_HORIZONTAL_LENGTH = 32;
    public static final int MAX_HEIGHT = 8;
    public static final int MIN_HEIGHT = 2;
    public static final int MAX_CELLS_PER_BARRIER = 256;
    public static final int MAX_CELLS_PER_ROOM = 2048;

    private static final ZombiesBarrierBlockRuntimeService INSTANCE = new ZombiesBarrierBlockRuntimeService();

    private final ConcurrentMap<CellKey, BarrierCell> cells = new ConcurrentHashMap<>();
    private final ConcurrentMap<LocationKey, Set<CellKey>> cellsByLocation = new ConcurrentHashMap<>();
    private final ConcurrentMap<RoomObjectKey, Set<CellKey>> cellsByObject = new ConcurrentHashMap<>();
    private final ConcurrentMap<RoomGroupKey, Set<CellKey>> cellsByGroup = new ConcurrentHashMap<>();

    public static ZombiesBarrierBlockRuntimeService instance() {
        return INSTANCE;
    }

    public PlacementSummary placeActiveBarriers(
            RoomId roomId,
            Collection<ZombiesBarrierData> barriers,
            Predicate<ZombiesBarrierData> barrierClearedPredicate,
            Function<ResourceKey<Level>, ServerLevel> levelResolver
    ) {
        Objects.requireNonNull(roomId, "roomId");
        clearRoom(roomId, levelResolver);
        int plannedCells = 0;
        int placedCells = 0;
        int skippedNonAir = 0;
        for (ZombiesBarrierData barrier : safeBarriers(barriers)) {
            if (barrierClearedPredicate != null && barrierClearedPredicate.test(barrier)) {
                continue;
            }
            ServerLevel level = resolveLevel(levelResolver, barrier.dimension());
            if (level == null) {
                continue;
            }
            List<BlockPos> positions = wallCells(barrier);
            plannedCells += positions.size();
            for (BlockPos pos : positions) {
                if (!level.isInWorldBounds(pos)) {
                    continue;
                }
                BlockState currentState = level.getBlockState(pos);
                if (!currentState.isAir() && !currentState.is(CodPatternBlockRegister.ZOMBIES_PLAYER_BARRIER.get())) {
                    skippedNonAir++;
                    continue;
                }
                boolean barrierAlreadyPresent = currentState.is(CodPatternBlockRegister.ZOMBIES_PLAYER_BARRIER.get());
                if (barrierAlreadyPresent
                        || level.setBlock(pos, CodPatternBlockRegister.ZOMBIES_PLAYER_BARRIER.get().defaultBlockState(), Block.UPDATE_ALL)) {
                    BarrierCell cell = BarrierCell.from(roomId, barrier, pos);
                    CellKey key = cell.key();
                    cells.put(key, cell);
                    cellsByLocation.computeIfAbsent(cell.locationKey(), ignored -> ConcurrentHashMap.newKeySet()).add(key);
                    indexCell(cell);
                    placedCells++;
                }
            }
        }
        return new PlacementSummary(roomId, plannedCells, placedCells, skippedNonAir);
    }

    public ZombiesServiceResult<PreflightSummary> validateFillTargets(
            RoomId roomId,
            Collection<ZombiesBarrierData> barriers,
            Predicate<ZombiesBarrierData> barrierClearedPredicate,
            Function<ResourceKey<Level>, ServerLevel> levelResolver
    ) {
        Objects.requireNonNull(roomId, "roomId");
        int scannedCells = 0;
        int fillableAirCells = 0;
        int occupiedMapCells = 0;
        for (ZombiesBarrierData barrier : safeBarriers(barriers)) {
            if (barrierClearedPredicate != null && barrierClearedPredicate.test(barrier)) {
                continue;
            }
            ServerLevel level = resolveLevel(levelResolver, barrier.dimension());
            if (level == null) {
                continue;
            }
            for (BlockPos pos : wallCells(barrier)) {
                if (!level.isInWorldBounds(pos)) {
                    continue;
                }
                scannedCells++;
                BlockState state = level.getBlockState(pos);
                if (state.isAir() || state.is(CodPatternBlockRegister.ZOMBIES_PLAYER_BARRIER.get())) {
                    fillableAirCells++;
                    continue;
                }
                occupiedMapCells++;
            }
        }
        return ZombiesServiceResult.success(new PreflightSummary(roomId, scannedCells, fillableAirCells, occupiedMapCells));
    }

    public CleanupSummary clearGroup(
            RoomId roomId,
            int group,
            Function<ResourceKey<Level>, ServerLevel> levelResolver
    ) {
        Objects.requireNonNull(roomId, "roomId");
        Set<CellKey> keys = new LinkedHashSet<>();
        for (Map.Entry<RoomGroupKey, Set<CellKey>> entry : cellsByGroup.entrySet()) {
            RoomGroupKey key = entry.getKey();
            if (roomId.equals(key.roomId()) && key.group() == group) {
                keys.addAll(entry.getValue());
            }
        }
        return clearKeys(roomId, keys, levelResolver);
    }

    public CleanupSummary clearObject(
            RoomId roomId,
            String objectId,
            Function<ResourceKey<Level>, ServerLevel> levelResolver
    ) {
        Objects.requireNonNull(roomId, "roomId");
        String normalizedObjectId = normalizeObjectId(objectId);
        if (normalizedObjectId.isEmpty()) {
            return CleanupSummary.empty(roomId);
        }
        Set<CellKey> keys = cellsByObject.remove(new RoomObjectKey(roomId, normalizedObjectId));
        return clearKeys(roomId, keys, levelResolver);
    }

    public CleanupSummary clearRoom(
            RoomId roomId,
            Function<ResourceKey<Level>, ServerLevel> levelResolver
    ) {
        Objects.requireNonNull(roomId, "roomId");
        Set<CellKey> keys = new LinkedHashSet<>();
        for (Map.Entry<CellKey, BarrierCell> entry : cells.entrySet()) {
            if (roomId.equals(entry.getValue().roomId())) {
                keys.add(entry.getKey());
            }
        }
        return clearKeys(roomId, keys, levelResolver);
    }

    public CleanupSummary clearAll(Function<ResourceKey<Level>, ServerLevel> levelResolver) {
        Set<CellKey> keys = new LinkedHashSet<>(cells.keySet());
        CleanupSummary summary = clearKeys(null, keys, levelResolver);
        cellsByLocation.clear();
        cellsByObject.clear();
        cellsByGroup.clear();
        return summary;
    }

    public CleanupSummary scanAndClearRoomResidue(
            RoomId roomId,
            Collection<ZombiesBarrierData> barriers,
            Function<ResourceKey<Level>, ServerLevel> levelResolver
    ) {
        Objects.requireNonNull(roomId, "roomId");
        int removed = 0;
        int scanned = 0;
        for (ZombiesBarrierData barrier : safeBarriers(barriers)) {
            ServerLevel level = resolveLevel(levelResolver, barrier.dimension());
            if (level == null) {
                continue;
            }
            for (BlockPos pos : wallCells(barrier)) {
                scanned++;
                if (isBarrierBlock(level, pos)
                        && !hasOtherRoomCellAt(roomId, barrier.dimension(), pos)
                        && level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL)) {
                    removed++;
                }
            }
        }
        clearRoom(roomId, levelResolver);
        return new CleanupSummary(roomId, scanned, removed);
    }

    public Optional<BarrierCell> cellAt(Level level, BlockPos pos) {
        if (level == null || pos == null) {
            return Optional.empty();
        }
        Set<CellKey> keys = cellsByLocation.get(new LocationKey(level.dimension(), pos.immutable()));
        if (keys == null || keys.isEmpty()) {
            return Optional.empty();
        }
        return keys.stream()
                .map(cells::get)
                .filter(Objects::nonNull)
                .findFirst();
    }

    public Optional<BarrierCell> cellAt(RoomId roomId, Level level, BlockPos pos) {
        if (roomId == null || level == null || pos == null) {
            return Optional.empty();
        }
        Set<CellKey> keys = cellsByLocation.get(new LocationKey(level.dimension(), pos.immutable()));
        if (keys == null || keys.isEmpty()) {
            return Optional.empty();
        }
        return keys.stream()
                .map(cells::get)
                .filter(Objects::nonNull)
                .filter(cell -> roomId.equals(cell.roomId()))
                .findFirst();
    }

    public boolean isActiveCell(Level level, BlockPos pos) {
        return cellAt(level, pos).isPresent();
    }

    public void onBarrierBlockRemoved(Level level, BlockPos pos) {
        if (level == null || pos == null) {
            return;
        }
        Set<CellKey> keys = cellsByLocation.get(new LocationKey(level.dimension(), pos.immutable()));
        if (keys != null) {
            List.copyOf(keys).forEach(this::removeIndex);
        }
    }

    public static List<BlockPos> wallCells(ZombiesBarrierData barrier) {
        if (barrier == null || barrier.areaFrom() == null || barrier.areaTo() == null) {
            return List.of();
        }
        BlockPos from = barrier.areaFrom();
        BlockPos to = barrier.areaTo();
        int minX = Math.min(from.getX(), to.getX());
        int maxX = Math.max(from.getX(), to.getX());
        int minY = Math.min(from.getY(), to.getY());
        int maxY = Math.max(from.getY(), to.getY());
        int minZ = Math.min(from.getZ(), to.getZ());
        int maxZ = Math.max(from.getZ(), to.getZ());
        List<BlockPos> positions = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    positions.add(new BlockPos(x, y, z));
                }
            }
        }
        return List.copyOf(positions);
    }

    public static BarrierGeometry geometry(ZombiesBarrierData barrier) {
        if (barrier == null || barrier.areaFrom() == null || barrier.areaTo() == null) {
            return BarrierGeometry.invalid(0, 0, 0);
        }
        BlockPos from = barrier.areaFrom();
        BlockPos to = barrier.areaTo();
        int horizontalLength = Math.max(Math.abs(from.getX() - to.getX()), Math.abs(from.getZ() - to.getZ())) + 1;
        int height = Math.abs(from.getY() - to.getY()) + 1;
        int cellCount = horizontalLength * height;
        boolean straightWall = from.getX() == to.getX() || from.getZ() == to.getZ();
        return new BarrierGeometry(straightWall, horizontalLength, height, cellCount);
    }

    public static ResidueScanPlan residueScanPlan(Collection<ZombiesBarrierData> barriers) {
        Map<ResourceKey<Level>, Set<BlockPos>> byDimension = new LinkedHashMap<>();
        for (ZombiesBarrierData barrier : safeBarriers(barriers)) {
            if (barrier.dimension() == null) {
                continue;
            }
            Set<BlockPos> positions = byDimension.computeIfAbsent(barrier.dimension(), ignored -> new LinkedHashSet<>());
            positions.addAll(wallCells(barrier));
        }
        Map<ResourceKey<Level>, List<BlockPos>> copy = new LinkedHashMap<>();
        byDimension.forEach((dimension, positions) -> copy.put(dimension, List.copyOf(positions)));
        return new ResidueScanPlan(copy);
    }

    private CleanupSummary clearKeys(
            RoomId requestedRoomId,
            Collection<CellKey> keys,
            Function<ResourceKey<Level>, ServerLevel> levelResolver
    ) {
        if (keys == null || keys.isEmpty()) {
            return CleanupSummary.empty(requestedRoomId);
        }
        int scanned = 0;
        int removed = 0;
        for (CellKey key : List.copyOf(keys)) {
            BarrierCell cell = cells.get(key);
            if (cell == null || requestedRoomId != null && !requestedRoomId.equals(cell.roomId())) {
                removeIndex(key);
                continue;
            }
            scanned++;
            ServerLevel level = resolveLevel(levelResolver, key.dimension());
            boolean sharedLocation = hasOtherCellAt(key);
            if (!sharedLocation && level != null && isBarrierBlock(level, key.pos())) {
                if (level.setBlock(key.pos(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL)) {
                    removed++;
                }
            }
            removeIndex(key);
        }
        return new CleanupSummary(requestedRoomId, scanned, removed);
    }

    private static ServerLevel resolveLevel(
            Function<ResourceKey<Level>, ServerLevel> levelResolver,
            ResourceKey<Level> dimension
    ) {
        return levelResolver == null || dimension == null ? null : levelResolver.apply(dimension);
    }

    private static boolean isBarrierBlock(ServerLevel level, BlockPos pos) {
        return level != null
                && pos != null
                && level.getBlockState(pos).is(CodPatternBlockRegister.ZOMBIES_PLAYER_BARRIER.get());
    }

    private void indexCell(BarrierCell cell) {
        cellsByObject.computeIfAbsent(new RoomObjectKey(cell.roomId(), cell.objectId()), ignored -> ConcurrentHashMap.newKeySet())
                .add(cell.key());
        cellsByGroup.computeIfAbsent(new RoomGroupKey(cell.roomId(), cell.dimension(), cell.group()), ignored -> ConcurrentHashMap.newKeySet())
                .add(cell.key());
    }

    private boolean hasOtherCellAt(CellKey key) {
        Set<CellKey> keys = cellsByLocation.get(new LocationKey(key.dimension(), key.pos()));
        if (keys == null || keys.isEmpty()) {
            return false;
        }
        for (CellKey other : keys) {
            if (!key.equals(other) && cells.containsKey(other)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasOtherRoomCellAt(RoomId roomId, ResourceKey<Level> dimension, BlockPos pos) {
        if (roomId == null || dimension == null || pos == null) {
            return false;
        }
        Set<CellKey> keys = cellsByLocation.get(new LocationKey(dimension, pos.immutable()));
        if (keys == null || keys.isEmpty()) {
            return false;
        }
        for (CellKey key : keys) {
            BarrierCell cell = cells.get(key);
            if (cell != null && !roomId.equals(cell.roomId())) {
                return true;
            }
        }
        return false;
    }

    private void removeIndex(CellKey key) {
        BarrierCell removed = cells.remove(key);
        if (removed == null) {
            return;
        }
        removeFromIndex(cellsByLocation, removed.locationKey(), key);
        removeFromIndex(cellsByObject, new RoomObjectKey(removed.roomId(), removed.objectId()), key);
        removeFromIndex(cellsByGroup, new RoomGroupKey(removed.roomId(), removed.dimension(), removed.group()), key);
    }

    private static <K> void removeFromIndex(ConcurrentMap<K, Set<CellKey>> index, K indexKey, CellKey cellKey) {
        Set<CellKey> keys = index.get(indexKey);
        if (keys == null) {
            return;
        }
        keys.remove(cellKey);
        if (keys.isEmpty()) {
            index.remove(indexKey, keys);
        }
    }

    private static List<ZombiesBarrierData> safeBarriers(Collection<ZombiesBarrierData> barriers) {
        if (barriers == null || barriers.isEmpty()) {
            return List.of();
        }
        return barriers.stream()
                .filter(Objects::nonNull)
                .toList();
    }

    private static String normalizeObjectId(String objectId) {
        return Objects.requireNonNullElse(objectId, "").trim();
    }

    public record BarrierGeometry(boolean straightWall, int horizontalLength, int height, int cellCount) {
        private static BarrierGeometry invalid(int horizontalLength, int height, int cellCount) {
            return new BarrierGeometry(false, horizontalLength, height, cellCount);
        }
    }

    public record BarrierCell(
            RoomId roomId,
            ResourceKey<Level> dimension,
            BlockPos pos,
            String objectId,
            int group
    ) {
        public BarrierCell {
            Objects.requireNonNull(roomId, "roomId");
            Objects.requireNonNull(dimension, "dimension");
            Objects.requireNonNull(pos, "pos");
            objectId = normalizeObjectId(objectId);
        }

        private static BarrierCell from(RoomId roomId, ZombiesBarrierData barrier, BlockPos pos) {
            return new BarrierCell(
                    roomId,
                    barrier.dimension(),
                    pos.immutable(),
                    ZombiesObjectStateStore.objectKey(barrier),
                    barrier.group());
        }

        private CellKey key() {
            return new CellKey(roomId, dimension, pos, objectId);
        }

        private LocationKey locationKey() {
            return new LocationKey(dimension, pos);
        }
    }

    public record PlacementSummary(RoomId roomId, int plannedCells, int placedCells, int skippedNonAirCells) {
    }

    public record PreflightSummary(RoomId roomId, int scannedCells, int fillableAirCells, int occupiedMapCells) {
    }

    public record CleanupSummary(RoomId roomId, int scannedCells, int removedCells) {
        private static CleanupSummary empty(RoomId roomId) {
            return new CleanupSummary(roomId, 0, 0);
        }
    }

    public record ResidueScanPlan(Map<ResourceKey<Level>, List<BlockPos>> positionsByDimension) {
        public ResidueScanPlan {
            if (positionsByDimension == null || positionsByDimension.isEmpty()) {
                positionsByDimension = Map.of();
            } else {
                Map<ResourceKey<Level>, List<BlockPos>> copy = new HashMap<>();
                positionsByDimension.forEach((dimension, positions) -> {
                    if (dimension != null && positions != null && !positions.isEmpty()) {
                        copy.put(dimension, List.copyOf(new LinkedHashSet<>(positions)));
                    }
                });
                positionsByDimension = Map.copyOf(copy);
            }
        }

        public int cellCount() {
            int total = 0;
            for (List<BlockPos> positions : positionsByDimension.values()) {
                total += positions.size();
            }
            return total;
        }
    }

    private record CellKey(RoomId roomId, ResourceKey<Level> dimension, BlockPos pos, String objectId) {
        private CellKey {
            Objects.requireNonNull(roomId, "roomId");
            Objects.requireNonNull(dimension, "dimension");
            Objects.requireNonNull(pos, "pos");
            objectId = normalizeObjectId(objectId);
        }
    }

    private record LocationKey(ResourceKey<Level> dimension, BlockPos pos) {
        private LocationKey {
            Objects.requireNonNull(dimension, "dimension");
            Objects.requireNonNull(pos, "pos");
        }
    }

    private record RoomObjectKey(RoomId roomId, String objectId) {
        private RoomObjectKey {
            Objects.requireNonNull(roomId, "roomId");
            objectId = normalizeObjectId(objectId);
        }
    }

    private record RoomGroupKey(RoomId roomId, ResourceKey<Level> dimension, int group) {
        private RoomGroupKey {
            Objects.requireNonNull(roomId, "roomId");
            Objects.requireNonNull(dimension, "dimension");
        }
    }
}
