package com.cdp.codpattern.app.zombies.service;

import com.cdp.codpattern.app.match.model.ModePlayerValue;
import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.zombies.map.ZombiesMapSnapshot;
import com.cdp.codpattern.config.zombies.ZombiesBackpackConfig;
import com.cdp.codpattern.config.zombies.ZombiesWeaponFilterConfig;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Orchestrates the zombies startup side effects after a start vote snapshot is fixed.
 *
 * <p>The flow owns the ordering and rollback stack. Map-specific integration points such as room locking,
 * original-position capture, HUD setup, runtime transition, and zombies equipment cleanup are supplied as
 * participants so the service can stay independent from {@code ZombiesMap}.</p>
 */
public final class ZombiesStartupFlow {
    private final ZombiesStartupValidationService validationService;
    private final ZombiesStarterKitDistributor starterKitDistributor;
    private final ZombiesMapOccupancyService occupancyService;
    private final ZombiesSpawnAssignmentService spawnAssignmentService;

    public ZombiesStartupFlow(
            ZombiesStartupValidationService validationService,
            ZombiesStarterKitDistributor starterKitDistributor,
            ZombiesMapOccupancyService occupancyService,
            ZombiesSpawnAssignmentService spawnAssignmentService
    ) {
        this.validationService = Objects.requireNonNull(validationService, "validationService");
        this.starterKitDistributor = starterKitDistributor == null
                ? new ZombiesStarterKitDistributor()
                : starterKitDistributor;
        this.occupancyService = occupancyService == null
                ? ZombiesMapOccupancyService.instance()
                : occupancyService;
        this.spawnAssignmentService = spawnAssignmentService == null
                ? new ZombiesSpawnAssignmentService()
                : spawnAssignmentService;
    }

    public ZombiesServiceResult<StartupResult> start(StartupRequest request) {
        return execute(request);
    }

    public ZombiesServiceResult<StartupResult> execute(StartupRequest request) {
        Objects.requireNonNull(request, "request");
        StartupWork work = new StartupWork(request);

        ZombiesServiceResult<ZombiesStartupPreflightSnapshot> preflightResult =
                validationService.validatePreflight(request.mapSnapshot());
        if (!preflightResult.success() || preflightResult.value().isEmpty()) {
            return failureResult(work, preflightResult.code(), preflightResult.params(), preflightResult.logMessage());
        }
        work.preflightSnapshot = preflightResult.value().get();

        ZombiesServiceResult<ZombiesStarterKitDistributor.PreparedStarterKits> starterKitResult =
                starterKitDistributor.prepareStarterWeapons(
                        request.memberIds(),
                        request.backpackConfig(),
                        request.weaponFilterConfig());
        if (!starterKitResult.success() || starterKitResult.value().isEmpty()) {
            return failureResult(work, starterKitResult.code(), starterKitResult.params(), starterKitResult.logMessage());
        }
        work.starterKits = starterKitResult.value().get();

        ZombiesServiceResult<Void> preOccupancyResult = runParticipantStage(
                request,
                work,
                ParticipantStage.BEFORE_OCCUPANCY_ACQUIRE);
        if (!preOccupancyResult.success()) {
            return failWithRollback(work, preOccupancyResult);
        }

        ZombiesServiceResult<Void> occupancyResult = occupancyService.acquire(request.mapName(), request.roomId());
        if (!occupancyResult.success()) {
            return failureResult(work, occupancyResult.code(), occupancyResult.params(), occupancyResult.logMessage());
        }
        work.pushRollback(new NamedRollbackAction("release_occupancy", context -> {
            occupancyService.release(request.mapName(), request.roomId());
            return ZombiesServiceResult.ok();
        }));

        ZombiesServiceResult<Void> postOccupancyResult = runParticipantStage(
                request,
                work,
                ParticipantStage.AFTER_OCCUPANCY_ACQUIRED);
        if (!postOccupancyResult.success()) {
            return failWithRollback(work, postOccupancyResult);
        }

        ZombiesServiceResult<ZombiesSpawnAssignmentService.ZombiesSpawnAssignmentPlan> assignmentResult =
                spawnAssignmentService.assignFromInitialSpawns(request.initialSpawnPoints(), request.memberIds());
        if (!assignmentResult.success() || assignmentResult.value().isEmpty()) {
            return failWithRollback(work, assignmentResult);
        }
        work.assignmentPlan = assignmentResult.value().get();

        ZombiesServiceResult<Void> beforeTeleportResult = runParticipantStage(
                request,
                work,
                ParticipantStage.BEFORE_TELEPORT);
        if (!beforeTeleportResult.success()) {
            return failWithRollback(work, beforeTeleportResult);
        }

        ZombiesServiceResult<ZombiesSpawnAssignmentService.ZombiesSpawnTeleportSummary> teleportResult =
                executeTeleport(request, work.assignmentPlan);
        if (!teleportResult.success() || teleportResult.value().isEmpty()) {
            teleportResult.value().ifPresent(summary -> work.teleportSummary = summary);
            return failWithRollback(work, teleportResult);
        }
        work.teleportSummary = teleportResult.value().get();

        ZombiesServiceResult<Void> beforeStarterKitResult = runParticipantStage(
                request,
                work,
                ParticipantStage.BEFORE_STARTER_KIT_APPLY);
        if (!beforeStarterKitResult.success()) {
            return failWithRollback(work, beforeStarterKitResult);
        }

        ZombiesServiceResult<Void> applyStarterKitResult =
                applyStarterKits(request, work.starterKits);
        if (!applyStarterKitResult.success()) {
            return failWithRollback(work, applyStarterKitResult);
        }

        ZombiesServiceResult<Void> afterStarterKitResult = runParticipantStage(
                request,
                work,
                ParticipantStage.AFTER_STARTER_KIT_APPLIED);
        if (!afterStarterKitResult.success()) {
            return failWithRollback(work, afterStarterKitResult);
        }

        ZombiesServiceResult<Void> completeResult = runParticipantStage(
                request,
                work,
                ParticipantStage.COMPLETE_STARTUP);
        if (!completeResult.success()) {
            return failWithRollback(work, completeResult);
        }

        return ZombiesServiceResult.success(work.result(ZombiesServiceResult.ok(), ZombiesStartupRollbackReport.empty()));
    }

    private ZombiesServiceResult<ZombiesSpawnAssignmentService.ZombiesSpawnTeleportSummary> executeTeleport(
            StartupRequest request,
            ZombiesSpawnAssignmentService.ZombiesSpawnAssignmentPlan plan
    ) {
        try {
            if (request.map().isPresent()) {
                return spawnAssignmentService.executeTeleport(request.map().get(), plan);
            }
            return spawnAssignmentService.executeTeleport(request.serverLevel().orElse(null), plan);
        } catch (RuntimeException exception) {
            return ZombiesServiceResult.failure(
                    ZombiesErrorCode.STARTUP_TELEPORT_FAILED,
                    Map.of("reason", ModePlayerValue.ofString(exception.getClass().getSimpleName())),
                    "Zombies startup teleport threw " + exception.getClass().getName());
        }
    }

    private ZombiesServiceResult<Void> applyStarterKits(
            StartupRequest request,
            ZombiesStarterKitDistributor.PreparedStarterKits starterKits
    ) {
        try {
            return starterKitDistributor.applyStarterWeapons(request.serverLevel().orElse(null), starterKits);
        } catch (RuntimeException exception) {
            return ZombiesServiceResult.failure(
                    ZombiesErrorCode.STARTUP_STARTER_WEAPON_MISSING,
                    Map.of("reason", ModePlayerValue.ofString(exception.getClass().getSimpleName())),
                    "Zombies starter kit apply threw " + exception.getClass().getName());
        }
    }

    private ZombiesServiceResult<Void> runParticipantStage(
            StartupRequest request,
            StartupWork work,
            ParticipantStage stage
    ) {
        for (ZombiesStartupParticipant participant : request.participants()) {
            ZombiesServiceResult<Optional<ZombiesStartupRollbackAction>> result;
            try {
                result = participant.onStartupStage(stage, work.context());
            } catch (RuntimeException exception) {
                return ZombiesServiceResult.failure(
                        ZombiesErrorCode.STARTUP_PREFLIGHT_FAILED,
                        Map.of(
                                "stage", ModePlayerValue.ofString(stage.key()),
                                "participant", ModePlayerValue.ofString(participant.name()),
                                "reason", ModePlayerValue.ofString(exception.getClass().getSimpleName())
                        ),
                        "Zombies startup participant " + participant.name()
                                + " threw during " + stage.key() + ": " + exception.getClass().getName());
            }
            if (!result.success()) {
                return ZombiesServiceResult.failure(
                        result.code(),
                        stageParams(stage, participant, result.params()),
                        result.logMessage());
            }
            result.value()
                    .flatMap(optional -> optional)
                    .ifPresent(work::pushRollback);
        }
        return ZombiesServiceResult.ok();
    }

    private ZombiesServiceResult<StartupResult> failWithRollback(
            StartupWork work,
            ZombiesServiceResult<?> failure
    ) {
        ZombiesStartupRollbackReport rollbackReport = rollback(work, failure.code());
        return failureResult(work, failure.code(), failure.params(), failure.logMessage(), rollbackReport);
    }

    private ZombiesStartupRollbackReport rollback(StartupWork work, ZombiesErrorCode failureCode) {
        List<ZombiesStartupRollbackReport.Step> steps = new ArrayList<>();
        ZombiesStartupRollbackContext context = work.context(failureCode);
        while (!work.rollbackActions.isEmpty()) {
            ZombiesStartupRollbackAction action = work.rollbackActions.pop();
            try {
                ZombiesServiceResult<Void> result = action.rollback(context);
                steps.add(new ZombiesStartupRollbackReport.Step(
                        action.name(),
                        result.success(),
                        result.code(),
                        result.logMessage()));
            } catch (RuntimeException exception) {
                steps.add(new ZombiesStartupRollbackReport.Step(
                        action.name(),
                        false,
                        ZombiesErrorCode.of("startup.rollback_failed"),
                        exception.getClass().getName()));
            }
        }
        return new ZombiesStartupRollbackReport(steps);
    }

    private ZombiesServiceResult<StartupResult> failureResult(
            StartupWork work,
            ZombiesErrorCode code,
            Map<String, ModePlayerValue> params,
            String logMessage
    ) {
        return failureResult(work, code, params, logMessage, ZombiesStartupRollbackReport.empty());
    }

    private ZombiesServiceResult<StartupResult> failureResult(
            StartupWork work,
            ZombiesErrorCode code,
            Map<String, ModePlayerValue> params,
            String logMessage,
            ZombiesStartupRollbackReport rollbackReport
    ) {
        ZombiesServiceResult<Void> failure = ZombiesServiceResult.failure(code, params, logMessage);
        return new ZombiesServiceResult<>(
                false,
                failure.code(),
                failure.params(),
                Optional.of(work.result(failure, rollbackReport)),
                failure.logMessage());
    }

    private static Map<String, ModePlayerValue> stageParams(
            ParticipantStage stage,
            ZombiesStartupParticipant participant,
            Map<String, ModePlayerValue> original
    ) {
        Map<String, ModePlayerValue> params = new LinkedHashMap<>();
        params.put("stage", ModePlayerValue.ofString(stage.key()));
        params.put("participant", ModePlayerValue.ofString(participant.name()));
        if (original != null) {
            params.putAll(original);
        }
        return params;
    }

    public record StartupRequest(
            RoomId roomId,
            ZombiesMapSnapshot mapSnapshot,
            List<UUID> memberIds,
            List<SpawnPointData> initialSpawnPoints,
            Optional<BaseMap> map,
            Optional<ServerLevel> serverLevel,
            ZombiesBackpackConfig backpackConfig,
            ZombiesWeaponFilterConfig weaponFilterConfig,
            List<ZombiesStartupParticipant> participants
    ) {
        public StartupRequest {
            Objects.requireNonNull(roomId, "roomId");
            Objects.requireNonNull(mapSnapshot, "mapSnapshot");
            memberIds = normalizeMembers(memberIds);
            initialSpawnPoints = initialSpawnPoints == null ? List.of() : List.copyOf(initialSpawnPoints);
            map = map == null ? Optional.empty() : map;
            serverLevel = serverLevel == null ? Optional.empty() : serverLevel;
            participants = participants == null ? List.of() : List.copyOf(participants);
        }

        public static StartupRequest forMap(
                RoomId roomId,
                ZombiesMapSnapshot mapSnapshot,
                Collection<UUID> memberIds,
                List<SpawnPointData> initialSpawnPoints,
                BaseMap map,
                ZombiesBackpackConfig backpackConfig,
                ZombiesWeaponFilterConfig weaponFilterConfig,
                List<ZombiesStartupParticipant> participants
        ) {
            ServerLevel level = map == null ? null : map.getServerLevel();
            return new StartupRequest(
                    roomId,
                    mapSnapshot,
                    normalizeMembers(memberIds),
                    initialSpawnPoints,
                    Optional.ofNullable(map),
                    Optional.ofNullable(level),
                    backpackConfig,
                    weaponFilterConfig,
                    participants);
        }

        public static StartupRequest forServerLevel(
                RoomId roomId,
                ZombiesMapSnapshot mapSnapshot,
                Collection<UUID> memberIds,
                List<SpawnPointData> initialSpawnPoints,
                ServerLevel serverLevel,
                ZombiesBackpackConfig backpackConfig,
                ZombiesWeaponFilterConfig weaponFilterConfig,
                List<ZombiesStartupParticipant> participants
        ) {
            return new StartupRequest(
                    roomId,
                    mapSnapshot,
                    normalizeMembers(memberIds),
                    initialSpawnPoints,
                    Optional.empty(),
                    Optional.ofNullable(serverLevel),
                    backpackConfig,
                    weaponFilterConfig,
                    participants);
        }

        public String mapName() {
            return mapSnapshot.mapName();
        }
    }

    public record StartupResult(
            RoomId roomId,
            List<UUID> memberIds,
            Optional<ZombiesStartupPreflightSnapshot> preflightSnapshot,
            Optional<ZombiesStarterKitDistributor.PreparedStarterKits> starterKits,
            Optional<ZombiesSpawnAssignmentService.ZombiesSpawnAssignmentPlan> assignmentPlan,
            Optional<ZombiesSpawnAssignmentService.ZombiesSpawnTeleportSummary> teleportSummary,
            ZombiesServiceResult<Void> terminalResult,
            ZombiesStartupRollbackReport rollbackReport
    ) {
        public StartupResult {
            Objects.requireNonNull(roomId, "roomId");
            memberIds = memberIds == null ? List.of() : List.copyOf(memberIds);
            preflightSnapshot = preflightSnapshot == null ? Optional.empty() : preflightSnapshot;
            starterKits = starterKits == null ? Optional.empty() : starterKits;
            assignmentPlan = assignmentPlan == null ? Optional.empty() : assignmentPlan;
            teleportSummary = teleportSummary == null ? Optional.empty() : teleportSummary;
            terminalResult = terminalResult == null ? ZombiesServiceResult.ok() : terminalResult;
            rollbackReport = rollbackReport == null ? ZombiesStartupRollbackReport.empty() : rollbackReport;
        }
    }

    public enum ParticipantStage {
        BEFORE_OCCUPANCY_ACQUIRE("before_occupancy_acquire"),
        AFTER_OCCUPANCY_ACQUIRED("after_occupancy_acquired"),
        BEFORE_TELEPORT("before_teleport"),
        BEFORE_STARTER_KIT_APPLY("before_starter_kit_apply"),
        AFTER_STARTER_KIT_APPLIED("after_starter_kit_applied"),
        COMPLETE_STARTUP("complete_startup");

        private final String key;

        ParticipantStage(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }
    }

    public interface ZombiesStartupParticipant {
        default String name() {
            return getClass().getName();
        }

        default ZombiesServiceResult<Optional<ZombiesStartupRollbackAction>> onStartupStage(
                ParticipantStage stage,
                ZombiesStartupContext context
        ) {
            return ZombiesServiceResult.success(Optional.empty());
        }
    }

    public interface ZombiesStartupRollbackAction {
        String name();

        ZombiesServiceResult<Void> rollback(ZombiesStartupRollbackContext context);
    }

    public record ZombiesStartupContext(
            RoomId roomId,
            String mapName,
            List<UUID> memberIds,
            ZombiesMapSnapshot mapSnapshot,
            Optional<ZombiesStartupPreflightSnapshot> preflightSnapshot,
            Optional<ZombiesStarterKitDistributor.PreparedStarterKits> starterKits,
            Optional<ZombiesSpawnAssignmentService.ZombiesSpawnAssignmentPlan> assignmentPlan,
            Optional<ZombiesSpawnAssignmentService.ZombiesSpawnTeleportSummary> teleportSummary
    ) {
        public ZombiesStartupContext {
            Objects.requireNonNull(roomId, "roomId");
            mapName = Objects.requireNonNullElse(mapName, "").trim();
            memberIds = memberIds == null ? List.of() : List.copyOf(memberIds);
            Objects.requireNonNull(mapSnapshot, "mapSnapshot");
            preflightSnapshot = preflightSnapshot == null ? Optional.empty() : preflightSnapshot;
            starterKits = starterKits == null ? Optional.empty() : starterKits;
            assignmentPlan = assignmentPlan == null ? Optional.empty() : assignmentPlan;
            teleportSummary = teleportSummary == null ? Optional.empty() : teleportSummary;
        }
    }

    public record ZombiesStartupRollbackContext(
            RoomId roomId,
            String mapName,
            List<UUID> memberIds,
            ZombiesErrorCode failureCode,
            ZombiesStartupContext startupContext
    ) {
        public ZombiesStartupRollbackContext {
            Objects.requireNonNull(roomId, "roomId");
            mapName = Objects.requireNonNullElse(mapName, "").trim();
            memberIds = memberIds == null ? List.of() : List.copyOf(memberIds);
            failureCode = failureCode == null ? ZombiesErrorCode.STARTUP_PREFLIGHT_FAILED : failureCode;
            Objects.requireNonNull(startupContext, "startupContext");
        }
    }

    public record ZombiesStartupRollbackReport(List<Step> steps) {
        public ZombiesStartupRollbackReport {
            steps = steps == null ? List.of() : List.copyOf(steps);
        }

        public static ZombiesStartupRollbackReport empty() {
            return new ZombiesStartupRollbackReport(List.of());
        }

        public boolean success() {
            return steps.stream().allMatch(Step::success);
        }

        public record Step(
                String actionName,
                boolean success,
                ZombiesErrorCode code,
                String logMessage
        ) {
            public Step {
                actionName = Objects.requireNonNullElse(actionName, "").trim();
                code = code == null ? ZombiesErrorCode.OK : code;
                logMessage = Objects.requireNonNullElse(logMessage, "");
            }
        }
    }

    private record NamedRollbackAction(
            String name,
            RollbackDelegate delegate
    ) implements ZombiesStartupRollbackAction {
        private NamedRollbackAction {
            name = Objects.requireNonNullElse(name, "").trim();
            Objects.requireNonNull(delegate, "delegate");
        }

        @Override
        public ZombiesServiceResult<Void> rollback(ZombiesStartupRollbackContext context) {
            return delegate.rollback(context);
        }
    }

    @FunctionalInterface
    private interface RollbackDelegate {
        ZombiesServiceResult<Void> rollback(ZombiesStartupRollbackContext context);
    }

    private static final class StartupWork {
        private final StartupRequest request;
        private final Deque<ZombiesStartupRollbackAction> rollbackActions = new ArrayDeque<>();
        private ZombiesStartupPreflightSnapshot preflightSnapshot;
        private ZombiesStarterKitDistributor.PreparedStarterKits starterKits;
        private ZombiesSpawnAssignmentService.ZombiesSpawnAssignmentPlan assignmentPlan;
        private ZombiesSpawnAssignmentService.ZombiesSpawnTeleportSummary teleportSummary;

        private StartupWork(StartupRequest request) {
            this.request = request;
        }

        private void pushRollback(ZombiesStartupRollbackAction action) {
            if (action != null) {
                rollbackActions.push(action);
            }
        }

        private ZombiesStartupContext context() {
            return new ZombiesStartupContext(
                    request.roomId(),
                    request.mapName(),
                    request.memberIds(),
                    request.mapSnapshot(),
                    Optional.ofNullable(preflightSnapshot),
                    Optional.ofNullable(starterKits),
                    Optional.ofNullable(assignmentPlan),
                    Optional.ofNullable(teleportSummary));
        }

        private ZombiesStartupRollbackContext context(ZombiesErrorCode failureCode) {
            return new ZombiesStartupRollbackContext(
                    request.roomId(),
                    request.mapName(),
                    request.memberIds(),
                    failureCode,
                    context());
        }

        private StartupResult result(
                ZombiesServiceResult<Void> terminalResult,
                ZombiesStartupRollbackReport rollbackReport
        ) {
            return new StartupResult(
                    request.roomId(),
                    request.memberIds(),
                    Optional.ofNullable(preflightSnapshot),
                    Optional.ofNullable(starterKits),
                    Optional.ofNullable(assignmentPlan),
                    Optional.ofNullable(teleportSummary),
                    terminalResult,
                    rollbackReport);
        }
    }

    private static List<UUID> normalizeMembers(Collection<UUID> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<UUID> normalized = new LinkedHashSet<>();
        for (UUID memberId : memberIds) {
            if (memberId != null) {
                normalized.add(memberId);
            }
        }
        return List.copyOf(normalized);
    }
}
