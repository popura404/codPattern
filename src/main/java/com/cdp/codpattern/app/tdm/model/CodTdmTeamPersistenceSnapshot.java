package com.cdp.codpattern.app.tdm.model;

import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;

import java.util.List;

public record CodTdmTeamPersistenceSnapshot(String name, int playerLimit, List<SpawnPointData> spawnPoints) {
}
