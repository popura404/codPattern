package com.cdp.codpattern.compat.fpsmatch.data;

import com.cdp.codpattern.app.match.persistence.CommonModeMapData;
import com.cdp.codpattern.app.tdm.model.CodTdmTeamPersistenceSnapshot;
import com.cdp.codpattern.app.tdm.port.CodTdmActionPort;
import com.cdp.codpattern.app.tdm.port.CodTdmReadPort;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

final class CodTdmMapPersistenceSupport {
    static final int SCHEMA_VERSION = 1;

    private CodTdmMapPersistenceSupport() {
    }

    record TeamPayload(
            Map<String, CodTdmMapData.TeamData> teams,
            Optional<SpawnPointData> matchEndTeleportPoint
    ) {
        TeamPayload {
            teams = teams == null ? Map.of() : Map.copyOf(teams);
            matchEndTeleportPoint = matchEndTeleportPoint == null ? Optional.empty() : matchEndTeleportPoint;
        }
    }

    static Optional<ServerLevel> resolveLevel(CommonModeMapData commonData, Logger logger, String modeLabel) {
        if (ServerLifecycleHooks.getCurrentServer() == null) {
            logger.error("Failed to load {} map {}: server not ready", modeLabel, commonData.mapName());
            return Optional.empty();
        }
        ResourceLocation levelId = ResourceLocation.tryParse(commonData.levelName());
        if (levelId == null) {
            logger.error("Failed to load {} map {}: invalid levelName={}",
                    modeLabel,
                    commonData.mapName(),
                    commonData.levelName());
            return Optional.empty();
        }
        ResourceKey<Level> levelKey = ResourceKey.create(Registries.DIMENSION, levelId);
        ServerLevel level = ServerLifecycleHooks.getCurrentServer().getLevel(levelKey);
        if (level == null) {
            logger.error("Failed to load {} map {}: dimension {} not found",
                    modeLabel,
                    commonData.mapName(),
                    commonData.levelName());
            return Optional.empty();
        }
        return Optional.of(level);
    }

    static TeamPayload capturePayload(CodTdmReadPort readPort) {
        Map<String, CodTdmMapData.TeamData> teams = new LinkedHashMap<>();
        for (CodTdmTeamPersistenceSnapshot team : readPort.teamPersistenceSnapshots()) {
            teams.put(team.name(), CodTdmMapData.TeamData.fromSpawnProfile(
                    team.name(),
                    team.playerLimit(),
                    team.spawnProfile()));
        }
        return new TeamPayload(teams, readPort.matchEndTeleportPoint());
    }

    static void applyPayload(CodTdmActionPort actionPort, TeamPayload payload) {
        TeamPayload resolvedPayload = payload == null
                ? new TeamPayload(Map.of(), Optional.empty())
                : payload;
        for (CodTdmMapData.TeamData teamData : resolvedPayload.teams().values()) {
            actionPort.applyTeamSpawnProfile(teamData.name(), teamData.playerLimit(), teamData.toSpawnProfile());
        }
        resolvedPayload.matchEndTeleportPoint().ifPresent(actionPort::setMatchEndTeleportPoint);
    }
}
