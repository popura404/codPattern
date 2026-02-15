package com.cdp.codpattern.compat.fpsmatch.map;

import com.cdp.codpattern.app.tdm.model.TdmGamePhase;

import java.util.HashMap;
import java.util.Map;

final class CodTdmMatchRuntimeState {
    private TdmGamePhase phase = TdmGamePhase.WAITING;
    private int phaseTimer = 0;
    private int gameTimeTicks = 0;
    private final Map<String, Integer> teamScores = new HashMap<>();
    private long playingStartEpochMillis = 0L;
    private boolean resultExported = false;

    TdmGamePhase phase() {
        return phase;
    }

    int phaseTimer() {
        return phaseTimer;
    }

    int gameTimeTicks() {
        return gameTimeTicks;
    }

    Map<String, Integer> teamScores() {
        return teamScores;
    }

    void setPhase(TdmGamePhase phase) {
        this.phase = phase;
    }

    void setPhaseTimer(int phaseTimer) {
        this.phaseTimer = phaseTimer;
    }

    void setGameTimeTicks(int gameTimeTicks) {
        this.gameTimeTicks = gameTimeTicks;
    }

    void putTeamScore(String teamName, int score) {
        teamScores.put(teamName, score);
    }

    Map<String, Integer> teamScoresSnapshot() {
        return new HashMap<>(teamScores);
    }

    long playingStartEpochMillis() {
        return playingStartEpochMillis;
    }

    void markPlayingStarted(long epochMillis) {
        this.playingStartEpochMillis = Math.max(0L, epochMillis);
        this.resultExported = false;
    }

    boolean isResultExported() {
        return resultExported;
    }

    void markResultExported() {
        this.resultExported = true;
    }

    void resetCoreState() {
        phase = TdmGamePhase.WAITING;
        phaseTimer = 0;
        gameTimeTicks = 0;
        teamScores.clear();
        playingStartEpochMillis = 0L;
        resultExported = false;
    }
}
