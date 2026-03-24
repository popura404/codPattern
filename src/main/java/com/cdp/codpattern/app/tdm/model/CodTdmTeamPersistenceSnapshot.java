package com.cdp.codpattern.app.tdm.model;

import com.phasetranscrystal.fpsmatch.core.data.TeamSpawnProfile;

public record CodTdmTeamPersistenceSnapshot(String name, int playerLimit, TeamSpawnProfile spawnProfile) {
}
