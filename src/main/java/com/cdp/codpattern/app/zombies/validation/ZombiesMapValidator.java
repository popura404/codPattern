package com.cdp.codpattern.app.zombies.validation;

import com.cdp.codpattern.app.zombies.map.ZombiesMapSnapshot;
import com.cdp.codpattern.app.zombies.map.ZombiesMatchSnapshot;
import com.cdp.codpattern.app.zombies.service.ZombiesErrorCode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ZombiesMapValidator {
    private static final ZombiesErrorCode MAP_MISSING_GROUP_ONE_ZOMBIE_SPAWN =
            ZombiesErrorCode.of("map.missing_group_1_zombie_spawn");
    private static final ZombiesErrorCode MAP_DYNAMIC_PLAYER_SPAWN_UNSUPPORTED =
            ZombiesErrorCode.of("map.dynamic_player_spawn_unsupported");
    private static final ZombiesErrorCode MAP_DUPLICATE_OBJECT_ID =
            ZombiesErrorCode.of("map.duplicate_object_id");

    private final ZombiesMapValidationProfile profile;

    public ZombiesMapValidator() {
        this(ZombiesMapValidationProfile.MVP1_MINIMAL);
    }

    public ZombiesMapValidator(ZombiesMapValidationProfile profile) {
        this.profile = profile == null ? ZombiesMapValidationProfile.MVP1_MINIMAL : profile;
    }

    public ZombiesMapValidationReport validate(ZombiesMatchSnapshot matchSnapshot) {
        Objects.requireNonNull(matchSnapshot, "matchSnapshot");
        return validate(matchSnapshot.mapSnapshot());
    }

    public ZombiesMapValidationReport validate(ZombiesMapSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        List<ZombiesValidationIssue> issues = validateSnapshotRules(snapshot);
        return new ZombiesMapValidationReport(snapshot.roomId(), profile.key(), issues);
    }

    public ZombiesMapValidationReport validate(
            ZombiesMapValidationContributor.ZombiesMapValidationContext context
    ) {
        Objects.requireNonNull(context, "context");
        ZombiesMapSnapshot snapshot = ZombiesMapSnapshot.fromContributorContext(context);
        List<ZombiesValidationIssue> issues = validateSnapshotRules(snapshot);
        for (ZombiesMapValidationContributor contributor : profile.contributors()) {
            List<ZombiesMapValidationContributor.ZombiesValidationIssue> contributed = contributor.validate(context);
            if (contributed == null) {
                continue;
            }
            contributed.stream()
                    .filter(Objects::nonNull)
                    .map(ZombiesValidationIssue::fromContributorIssue)
                    .forEach(issues::add);
        }
        return new ZombiesMapValidationReport(context.roomId(), profile.key(), issues);
    }

    private List<ZombiesValidationIssue> validateSnapshotRules(ZombiesMapSnapshot snapshot) {
        List<ZombiesValidationIssue> issues = new ArrayList<>();
        if (profile.requireEndTeleportPoint() && !snapshot.hasEndTeleportPoint()) {
            issues.add(ZombiesValidationIssue.error(
                    ZombiesErrorCode.MAP_MISSING_ENDTP,
                    "endtp",
                    "Zombies map requires a match end teleport point."));
        }
        if (profile.requireInitialPlayerSpawn()
                && snapshot.spawns().stream().noneMatch(ZombiesMapSnapshot.SpawnSnapshot::initialPlayerSpawn)) {
            issues.add(ZombiesValidationIssue.error(
                    ZombiesErrorCode.MAP_MISSING_INITIAL_SPAWN,
                    "spawn.INITIAL",
                    "Zombies map requires at least one INITIAL player spawn."));
        }
        if (profile.requireGroupOneZombieSpawn()
                && snapshot.spawns().stream().noneMatch(ZombiesMapValidator::validGroupOneZombieSpawn)) {
            issues.add(ZombiesValidationIssue.error(
                    MAP_MISSING_GROUP_ONE_ZOMBIE_SPAWN,
                    "zombie_spawn.group_1",
                    "Zombies map requires at least one group=1 zombie spawn with positive weight."));
        }
        if (profile.rejectDynamicPlayerSpawns()) {
            snapshot.spawns().stream()
                    .filter(ZombiesMapSnapshot.SpawnSnapshot::dynamicPlayerSpawn)
                    .map(spawn -> ZombiesValidationIssue.error(
                            MAP_DYNAMIC_PLAYER_SPAWN_UNSUPPORTED,
                            subject("spawn", spawn.objectId(), spawn.featureKey()),
                            "Zombies MVP1 maps do not support dynamic player spawn kinds."))
                    .forEach(issues::add);
        }
        if (profile.requireUniqueObjectIds()) {
            addDuplicateObjectIdIssues(snapshot, issues);
        }
        return issues;
    }

    private static boolean validGroupOneZombieSpawn(ZombiesMapSnapshot.SpawnSnapshot spawn) {
        return spawn.zombieSpawn() && spawn.group() == 1 && spawn.weight() > 0.0D;
    }

    private static void addDuplicateObjectIdIssues(
            ZombiesMapSnapshot snapshot,
            List<ZombiesValidationIssue> issues
    ) {
        Map<String, String> seenSubjects = new LinkedHashMap<>();
        for (ZombiesMapSnapshot.SpawnSnapshot spawn : snapshot.spawns()) {
            addObjectId("spawn", spawn.objectId(), spawn.featureKey(), seenSubjects, issues);
        }
        for (ZombiesMapSnapshot.BarrierSnapshot barrier : snapshot.barriers()) {
            addObjectId("barrier", barrier.objectId(), barrier.featureKey(), seenSubjects, issues);
        }
    }

    private static void addObjectId(
            String type,
            String objectId,
            String featureKey,
            Map<String, String> seenSubjects,
            List<ZombiesValidationIssue> issues
    ) {
        if (objectId == null || objectId.isBlank()) {
            return;
        }
        String subject = subject(type, objectId, featureKey);
        String previous = seenSubjects.putIfAbsent(objectId, subject);
        if (previous != null) {
            issues.add(ZombiesValidationIssue.error(
                    MAP_DUPLICATE_OBJECT_ID,
                    subject,
                    "Duplicate objectId '" + objectId + "' also used by " + previous + "."));
        }
    }

    private static String subject(String type, String objectId, String featureKey) {
        String id = Objects.requireNonNullElse(objectId, "").trim();
        if (!id.isEmpty()) {
            return type + "." + id;
        }
        String feature = Objects.requireNonNullElse(featureKey, "").trim();
        return feature.isEmpty() ? type : type + "." + feature;
    }

    public ZombiesMapValidationProfile profile() {
        return profile;
    }
}
